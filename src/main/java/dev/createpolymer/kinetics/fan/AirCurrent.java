package dev.createpolymer.kinetics.fan;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import dev.createpolymer.kinetics.fan.processing.FanProcessing;
import dev.createpolymer.kinetics.fan.processing.FanProcessingType;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class AirCurrent {

	/** Create's block tag create:fan_transparent: blocks the air flow passes through. */
	public static final TagKey<Block> FAN_TRANSPARENT = TagKey.create(Registries.BLOCK, CreatePolymer.id("fan_transparent"));

	public final IAirCurrentSource source;
	public AABB bounds = new AABB(0, 0, 0, 0, 0, 0);
	public List<AirCurrentSegment> segments = new ArrayList<>();
	public Direction direction;
	public boolean pushing;
	public float maxDistance;

	protected List<Pair<TransportedItemStackHandlerBehaviour, FanProcessingType>> affectedItemHandlers =
		new ArrayList<>();
	protected List<Entity> caughtEntities = new ArrayList<>();

	public AirCurrent(IAirCurrentSource source) {
		this.source = source;
	}

	public void tick() {
		if (direction == null)
			rebuild();
		Level world = source.getAirCurrentWorld();
		if (world instanceof ServerLevel serverLevel && maxDistance > 0)
			spawnAirFlowParticle(serverLevel);

		tickAffectedEntities(world);
		tickAffectedHandlers();
	}

	/**
	 * Create's AirFlowParticle drifts along the whole current on the client. Players only get vanilla particles from
	 * the server, so a few are sent per tick, spread along the current and already moving in its direction.
	 */
	protected void spawnAirFlowParticle(ServerLevel world) {
		if (world.getRandom().nextFloat() >= FanConfig.fanParticleDensity)
			return;

		float distance = world.getRandom().nextFloat() * maxDistance;
		Vec3 directionVec = Vec3.atLowerCornerOf(direction.getUnitVec3i());
		Vec3 pos = VecHelper.getCenterOf(source.getAirCurrentPos())
			.add(directionVec.scale(distance + .5f));
		pos = VecHelper.offsetRandomly(pos, world.getRandom(), .25f);

		Vec3 motion = directionVec.scale(1 / 8f);
		if (!pushing)
			motion = motion.scale(-1);
		motion = motion.scale(maxDistance - (distance - 1f))
			.scale(.5f);

		FlowParticle access = new FlowParticle(world, pos, motion);
		FanProcessingType type = getTypeAt(distance);
		if (type == null)
			world.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y, pos.z, 0, motion.x, motion.y, motion.z, 1);
		else {
			type.morphAirFlow(access, world.getRandom());
			world.sendParticles(new DustParticleOptions(access.color, 1f), pos.x, pos.y, pos.z, 0, motion.x, motion.y,
				motion.z, 1);
		}
	}

	protected void tickAffectedEntities(Level world) {
		for (Iterator<Entity> iterator = caughtEntities.iterator(); iterator.hasNext(); ) {
			Entity entity = iterator.next();
			if (!entity.isAlive() || !entity.getBoundingBox()
				.intersects(bounds) || isPlayerCreativeFlying(entity)) {
				iterator.remove();
				continue;
			}

			Vec3i flow = (pushing ? direction : direction.getOpposite()).getUnitVec3i();
			float speed = Math.abs(source.getSpeed());
			float sneakModifier = entity.isShiftKeyDown() ? 4096f : 512f;
			double entityDistance = VecHelper.alignedDistanceToFace(entity.position(), source.getAirCurrentPos(), direction);
			// entityDistanceOld should be removed eventually. Remember that entityDistanceOld cannot be 0 while entityDistance can,
			// so division by 0 must be avoided.
			double entityDistanceOld = entity.position().distanceTo(VecHelper.getCenterOf(source.getAirCurrentPos()));
			float acceleration = (float) (speed / sneakModifier / (entityDistanceOld / maxDistance));
			Vec3 previousMotion = getMotion(entity);
			float maxAcceleration = 5;

			double xIn = Mth.clamp(flow.getX() * acceleration - previousMotion.x, -maxAcceleration, maxAcceleration);
			double yIn = Mth.clamp(flow.getY() * acceleration - previousMotion.y, -maxAcceleration, maxAcceleration);
			double zIn = Mth.clamp(flow.getZ() * acceleration - previousMotion.z, -maxAcceleration, maxAcceleration);

			setMotion(entity, previousMotion.add(new Vec3(xIn, yIn, zIn).scale(1 / 8f)));
			entity.fallDistance = 0;

			if (entity instanceof ServerPlayer serverPlayer)
				serverPlayer.connection.resetFlyingTicks();

			FanProcessingType processingType = getTypeAt((float) entityDistance);

			if (processingType == null)
				continue;

			if (entity instanceof ItemEntity itemEntity) {
				processingType.spawnProcessingParticles(world, entity.position());
				if (FanProcessing.canProcess(itemEntity, processingType))
					FanProcessing.applyProcessing(itemEntity, processingType);
				continue;
			}

			if (world != null)
				processingType.affectEntity(entity, world);
		}
	}

	/**
	 * Players move on their own client and only report where they went, so a push has to start from the movement
	 * the server last saw of them, and has to be sent to them.
	 */
	public static Vec3 getMotion(Entity entity) {
		return entity instanceof ServerPlayer player ? player.getKnownMovement() : entity.getDeltaMovement();
	}

	/** Create's entity.setDeltaMovement plus hurtMarked: sends the new motion to the entity's own client. */
	public static void setMotion(Entity entity, Vec3 motion) {
		entity.setDeltaMovement(motion);
		if (entity instanceof ServerPlayer)
			entity.syncVelocity = true;
	}

	public static boolean isPlayerCreativeFlying(Entity entity) {
		if (entity instanceof Player player) {
			return player.isCreative() && player.getAbilities().flying;
		}
		return false;
	}

	public void tickAffectedHandlers() {
		for (Pair<TransportedItemStackHandlerBehaviour, FanProcessingType> pair : affectedItemHandlers) {
			TransportedItemStackHandlerBehaviour handler = pair.getKey();
			Level world = handler.getWorld();
			FanProcessingType processingType = pair.getRight();
			if (processingType == null)
				continue;

			handler.handleProcessingOnAllItems(transported -> {
				processingType.spawnProcessingParticles(world, handler.getWorldPositionOf(transported));
				return FanProcessing.applyProcessing(transported, world, processingType);
			});
		}
	}

	public void rebuild() {
		if (source.getSpeed() == 0) {
			maxDistance = 0;
			segments.clear();
			bounds = new AABB(0, 0, 0, 0, 0, 0);
			return;
		}

		direction = source.getAirflowOriginSide();
		pushing = source.getAirFlowDirection() == direction;
		maxDistance = source.getMaxDistance();

		Level world = source.getAirCurrentWorld();
		BlockPos start = source.getAirCurrentPos();
		float max = this.maxDistance;
		Direction facing = direction;
		Vec3 directionVec = Vec3.atLowerCornerOf(facing.getUnitVec3i());
		maxDistance = getFlowLimit(world, start, max, facing);

		// Determine segments with transported fluids/gases
		segments.clear();
		AirCurrentSegment currentSegment = null;
		FanProcessingType type = null;

		int limit = getLimit();
		int searchStart = pushing ? 1 : limit;
		int searchEnd = pushing ? limit : 1;
		int searchStep = pushing ? 1 : -1;
		int toOffset = pushing ? -1 : 0;

		for (int i = searchStart; i * searchStep <= searchEnd * searchStep; i += searchStep) {
			BlockPos currentPos = start.relative(direction, i);
			FanProcessingType newType = FanProcessingType.getAt(world, currentPos);
			if (newType != null) {
				type = newType;
			}
			if (currentSegment == null) {
				currentSegment = new AirCurrentSegment();
				currentSegment.startOffset = i + toOffset;
				currentSegment.type = type;
			} else if (currentSegment.type != type) {
				currentSegment.endOffset = i + toOffset;
				segments.add(currentSegment);
				currentSegment = new AirCurrentSegment();
				currentSegment.startOffset = i + toOffset;
				currentSegment.type = type;
			}
		}
		if (currentSegment != null) {
			currentSegment.endOffset = searchEnd + searchStep + toOffset;
			segments.add(currentSegment);
		}

		// Build Bounding Box
		if (maxDistance < 0.25f)
			bounds = new AABB(0, 0, 0, 0, 0, 0);
		else {
			float factor = maxDistance - 1;
			Vec3 scale = directionVec.scale(factor);
			if (factor > 0)
				bounds = new AABB(start.relative(direction)).expandTowards(scale);
			else {
				bounds = new AABB(start.relative(direction)).contract(scale.x, scale.y, scale.z)
					.move(scale);
			}
		}

		findAffectedHandlers();
	}

	public static float getFlowLimit(Level world, BlockPos start, float max, Direction facing) {
		for (int i = 0; i < max; i++) {
			BlockPos currentPos = start.relative(facing, i + 1);
			if (!world.isLoaded(currentPos)) {
				return i;
			}

			// Create also looks at the material of copycat blocks here (not ported)
			BlockState state = world.getBlockState(currentPos);
			if (shouldAlwaysPass(state)) {
				continue;
			}

			VoxelShape shape = state.getCollisionShape(world, currentPos);
			if (shape.isEmpty()) {
				continue;
			}
			if (shape == Shapes.block()) {
				return i;
			}
			double shapeDepth = findMaxDepth(shape, facing);
			if (shapeDepth == Double.POSITIVE_INFINITY) {
				continue;
			}
			return Math.min((float) (i + shapeDepth + 1 / 32d), max);
		}

		return max;
	}

	private static final double[][] DEPTH_TEST_COORDINATES = {
		{0.25, 0.25},
		{0.25, 0.75},
		{0.5, 0.5},
		{0.75, 0.25},
		{0.75, 0.75}
	};

	// Finds the maximum depth of the shape when traveling in the given direction.
	// The result is always positive.
	// If there is a hole, the result will be Double.POSITIVE_INFINITY.
	private static double findMaxDepth(VoxelShape shape, Direction direction) {
		Direction.Axis axis = direction.getAxis();
		Direction.AxisDirection axisDirection = direction.getAxisDirection();
		double maxDepth = 0;

		for (double[] coordinates : DEPTH_TEST_COORDINATES) {
			double depth;
			if (axisDirection == Direction.AxisDirection.POSITIVE) {
				double min = shape.min(axis, coordinates[0], coordinates[1]);
				if (min == Double.POSITIVE_INFINITY) {
					return Double.POSITIVE_INFINITY;
				}
				depth = min;
			} else {
				double max = shape.max(axis, coordinates[0], coordinates[1]);
				if (max == Double.NEGATIVE_INFINITY) {
					return Double.POSITIVE_INFINITY;
				}
				depth = 1 - max;
			}

			if (depth > maxDepth) {
				maxDepth = depth;
			}
		}

		return maxDepth;
	}

	private static boolean shouldAlwaysPass(BlockState state) {
		return state.is(FAN_TRANSPARENT);
	}

	private int getLimit() {
		if ((float) (int) maxDistance == maxDistance) {
			return (int) maxDistance;
		} else {
			return (int) maxDistance + 1;
		}
	}

	public void findAffectedHandlers() {
		Level world = source.getAirCurrentWorld();
		BlockPos start = source.getAirCurrentPos();
		affectedItemHandlers.clear();
		int limit = getLimit();
		for (int i = 1; i <= limit; i++) {
			FanProcessingType segmentType = getTypeAt(i - 1);
			for (int offset : Iterate.zeroAndOne) {
				BlockPos pos = start.relative(direction, i)
					.below(offset);
				TransportedItemStackHandlerBehaviour behaviour =
					BlockEntityBehaviour.get(world, pos, TransportedItemStackHandlerBehaviour.TYPE);
				if (behaviour != null) {
					FanProcessingType type = FanProcessingType.getAt(world, pos);
					if (type == null)
						type = segmentType;
					affectedItemHandlers.add(Pair.of(behaviour, type));
				}
				if (direction.getAxis()
					.isVertical())
					break;
			}
		}
	}

	public void findEntities() {
		caughtEntities.clear();
		caughtEntities = source.getAirCurrentWorld()
			.getEntities(null, bounds);
	}

	@Nullable
	public FanProcessingType getTypeAt(float offset) {
		if (offset >= 0 && offset <= maxDistance) {
			if (pushing) {
				for (AirCurrentSegment airCurrentSegment : segments) {
					if (offset <= airCurrentSegment.endOffset) {
						return airCurrentSegment.type;
					}
				}
			} else {
				for (AirCurrentSegment airCurrentSegment : segments) {
					if (offset >= airCurrentSegment.endOffset) {
						return airCurrentSegment.type;
					}
				}
			}
		}
		return null;
	}

	private static class AirCurrentSegment {
		@Nullable
		private FanProcessingType type;
		private int startOffset;
		private int endOffset;
	}

	/** Server side stand-in for the client's AirFlowParticle: collects what a processing type does to the flow. */
	private static class FlowParticle implements FanProcessingType.AirFlowParticleAccess {
		private final ServerLevel level;
		private final Vec3 pos;
		private final Vec3 motion;
		private int color = 0xEEEEEE;

		private FlowParticle(ServerLevel level, Vec3 pos, Vec3 motion) {
			this.level = level;
			this.pos = pos;
			this.motion = motion;
		}

		@Override
		public void setColor(int color) {
			this.color = color;
		}

		@Override
		public void setAlpha(float alpha) {
			// the dust particle cannot be translucent
		}

		@Override
		public void spawnExtraParticle(ParticleOptions options, float speedMultiplier) {
			level.sendParticles(options, pos.x, pos.y, pos.z, 0, motion.x * speedMultiplier, motion.y * speedMultiplier,
				motion.z * speedMultiplier, 1);
		}
	}
}
