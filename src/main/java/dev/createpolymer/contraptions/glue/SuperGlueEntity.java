package dev.createpolymer.contraptions.glue;

import dev.createpolymer.contraptions.bearing.BearingBlock;
import dev.createpolymer.contraptions.chassis.AbstractChassisBlock;
import dev.createpolymer.contraptions.chassis.StickerBlock;
import dev.createpolymer.contraptions.gantry.GantryCarriageBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

import dev.createpolymer.api.contraption.BlockMovementChecks;
import dev.createpolymer.contraptions.piston.MechanicalPistonBlock;
import dev.createpolymer.registry.feature.ContraptionRegistry;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.VecHelper;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A glued area. Players cannot see it: it is a server-only entity that vanilla clients are never told about (a
 * marker); the selection shows its outline with particles.
 */
public class SuperGlueEntity extends Entity implements PolymerEntity {

	public static AABB span(BlockPos startPos, BlockPos endPos) {
		return new AABB(Vec3.atLowerCornerOf(startPos), Vec3.atLowerCornerOf(endPos)).expandTowards(1, 1, 1);
	}

	public static boolean isGlued(LevelAccessor level, BlockPos blockPos, Direction direction,
		Set<SuperGlueEntity> cached) {
		BlockPos targetPos = blockPos.relative(direction);
		if (cached != null)
			for (SuperGlueEntity glueEntity : cached)
				if (glueEntity.contains(blockPos) && glueEntity.contains(targetPos))
					return true;
		for (SuperGlueEntity glueEntity : level.getEntitiesOfClass(SuperGlueEntity.class,
			span(blockPos, targetPos).inflate(16))) {
			if (!glueEntity.contains(blockPos) || !glueEntity.contains(targetPos))
				continue;
			if (cached != null)
				cached.add(glueEntity);
			return true;
		}
		return false;
	}

	public static List<SuperGlueEntity> collectCropped(Level level, AABB bb) {
		List<SuperGlueEntity> glue = new ArrayList<>();
		for (SuperGlueEntity glueEntity : level.getEntitiesOfClass(SuperGlueEntity.class, bb)) {
			AABB glueBox = glueEntity.getBoundingBox();
			AABB intersect = bb.intersect(glueBox);
			if (intersect.getXsize() * intersect.getYsize() * intersect.getZsize() == 0)
				continue;
			if (Mth.equal(intersect.getSize(), 1))
				continue;
			glue.add(new SuperGlueEntity(level, intersect));
		}
		return glue;
	}

	public SuperGlueEntity(EntityType<?> type, Level world) {
		super(type, world);
	}

	public SuperGlueEntity(Level world, AABB boundingBox) {
		this(ContraptionRegistry.SUPER_GLUE, world);
		setBoundingBox(boundingBox);
		resetPositionToBB();
	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext context) {
		return EntityTypes.MARKER;
	}

	public void resetPositionToBB() {
		AABB bb = getBoundingBox();
		setPosRaw(bb.getCenter().x, bb.minY, bb.getCenter().z);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {}

	public static boolean isValidFace(Level world, BlockPos pos, Direction direction) {
		BlockState state = world.getBlockState(pos);
		if (BlockMovementChecks.isBlockAttachedTowards(state, world, pos, direction))
			return true;
		if (!BlockMovementChecks.isMovementNecessary(state, world, pos))
			return false;
		if (BlockMovementChecks.isNotSupportive(state, direction))
			return false;
		return true;
	}

	public static boolean isSideSticky(Level world, BlockPos pos, Direction direction) {
		BlockState state = world.getBlockState(pos);
		if (MechanicalPistonBlock.isStickyPiston(state))
			return state.getValue(MechanicalPistonBlock.FACING) == direction;

		Block block = state.getBlock();
		if (block instanceof StickerBlock)
			return state.getValue(BlockStateProperties.FACING) == direction;
		if (block instanceof GantryCarriageBlock)
			return state.getValue(BlockStateProperties.FACING) == direction;
		if (block instanceof BearingBlock)
			return state.getValue(BlockStateProperties.FACING) == direction;
		if (block instanceof AbstractChassisBlock chassis) {
			BooleanProperty glueableSide = chassis.getGlueableSide(state, direction);
			if (glueableSide == null)
				return false;
			return state.getValue(glueableSide);
		}

		if (state.getBlock() == Blocks.SLIME_BLOCK)
			return true;
		if (state.getBlock() == Blocks.HONEY_BLOCK)
			return true;

		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public void tick() {
		xRotO = getXRot();
		yRotO = getYRot();
		xo = getX();
		yo = getY();
		zo = getZ();

		if (getBoundingBox().getXsize() == 0)
			discard();
	}

	@Override
	public void setPos(double x, double y, double z) {
		AABB bb = getBoundingBox();
		setPosRaw(x, y, z);
		Vec3 center = bb.getCenter();
		setBoundingBox(bb.move(-center.x, -bb.minY, -center.z)
			.move(x, y, z));
	}

	@Override
	public void move(MoverType typeIn, Vec3 pos) {
		if (!level().isClientSide() && isAlive() && pos.lengthSqr() > 0.0D)
			discard();
	}

	@Override
	public void push(double x, double y, double z) {
		if (!level().isClientSide() && isAlive() && x * x + y * y + z * z > 0.0D)
			discard();
	}

	@Override
	public @NotNull EntityDimensions getDimensions(@NotNull Pose pose) {
		return super.getDimensions(pose).withEyeHeight(0.0F);
	}

	@Override
	public void push(Entity entityIn) {
		super.push(entityIn);
	}

	@Override
	public void addAdditionalSaveData(ValueOutput output) {
		Vec3 position = position();
		CompoundTag compound = new CompoundTag();
		writeBoundingBox(compound, getBoundingBox().move(position.scale(-1)));
		output.store("Glue", CompoundTag.CODEC, compound);
	}

	@Override
	public void readAdditionalSaveData(ValueInput input) {
		Vec3 position = position();
		input.read("Glue", CompoundTag.CODEC)
			.ifPresent(compound -> setBoundingBox(readBoundingBox(compound).move(position)));
	}

	public static void writeBoundingBox(CompoundTag compound, AABB bb) {
		compound.put("From", VecHelper.writeNBT(new Vec3(bb.minX, bb.minY, bb.minZ)));
		compound.put("To", VecHelper.writeNBT(new Vec3(bb.maxX, bb.maxY, bb.maxZ)));
	}

	public static AABB readBoundingBox(CompoundTag compound) {
		Vec3 from = VecHelper.readNBT(compound.getListOrEmpty("From"));
		Vec3 to = VecHelper.readNBT(compound.getListOrEmpty("To"));
		return new AABB(from, to);
	}

	@Override
	protected boolean repositionEntityAfterLoad() {
		return false;
	}

	@Override
	public float rotate(Rotation transformRotation) {
		AABB bb = getBoundingBox().move(position().scale(-1));
		if (transformRotation == Rotation.CLOCKWISE_90 || transformRotation == Rotation.COUNTERCLOCKWISE_90)
			setBoundingBox(new AABB(bb.minZ, bb.minY, bb.minX, bb.maxZ, bb.maxY, bb.maxX).move(position()));
		return super.rotate(transformRotation);
	}

	@Override
	public float mirror(Mirror transformMirror) {
		return super.mirror(transformMirror);
	}

	@Override
	public void thunderHit(ServerLevel world, LightningBolt lightningBolt) {}

	@Override
	public void refreshDimensions() {}

	public static EntityType.Builder<?> build(EntityType.Builder<?> builder) {
		@SuppressWarnings("unchecked")
		EntityType.Builder<SuperGlueEntity> entityBuilder = (EntityType.Builder<SuperGlueEntity>) builder;
		return entityBuilder.sized(0.5f, 0.5f).clientTrackingRange(10).noUpdateInterval();
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	public boolean contains(BlockPos pos) {
		return getBoundingBox().contains(Vec3.atCenterOf(pos));
	}

	@Override
	public PushReaction getPistonPushReaction() {
		return PushReaction.IGNORE_ENTITY;
	}

	/** Outlines the glued area with slime particles, to every player nearby. */
	public void spawnParticles() {
		spawnOutlineParticles(level(), getBoundingBox());
	}

	public static void spawnOutlineParticles(Level level, AABB bb) {
		Vec3 origin = new Vec3(bb.minX, bb.minY, bb.minZ);
		Vec3 extents = new Vec3(bb.getXsize(), bb.getYsize(), bb.getZsize());

		if (!(level instanceof ServerLevel slevel))
			return;

		for (Axis axis : Iterate.axes) {
			AxisDirection positive = AxisDirection.POSITIVE;
			double max = axis.choose(extents.x, extents.y, extents.z);
			Vec3 normal = Vec3.atLowerCornerOf(Direction.fromAxisAndDirection(axis, positive)
				.getUnitVec3i());
			for (Axis axis2 : Iterate.axes) {
				if (axis2 == axis)
					continue;
				double max2 = axis2.choose(extents.x, extents.y, extents.z);
				Vec3 normal2 = Vec3.atLowerCornerOf(Direction.fromAxisAndDirection(axis2, positive)
					.getUnitVec3i());
				for (Axis axis3 : Iterate.axes) {
					if (axis3 == axis2 || axis3 == axis)
						continue;
					double max3 = axis3.choose(extents.x, extents.y, extents.z);
					Vec3 normal3 = Vec3.atLowerCornerOf(Direction.fromAxisAndDirection(axis3, positive)
						.getUnitVec3i());

					for (int i = 0; i <= max * 2; i++) {
						for (int o1 : Iterate.zeroAndOne) {
							for (int o2 : Iterate.zeroAndOne) {
								Vec3 v = origin.add(normal.scale(i / 2f))
									.add(normal2.scale(max2 * o1))
									.add(normal3.scale(max3 * o2));

								slevel.sendParticles(ParticleTypes.ITEM_SLIME, v.x, v.y, v.z, 1, 0, 0, 0, 0);

							}
						}
					}
					break;
				}
				break;
			}
		}
	}
}
