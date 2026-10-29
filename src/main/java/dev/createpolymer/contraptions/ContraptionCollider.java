package dev.createpolymer.contraptions;

import static net.minecraft.world.entity.Entity.collideBoundingBox;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import dev.createpolymer.api.behaviour.interaction.MovingInteractionBehaviour;
import dev.createpolymer.api.behaviour.movement.MovementBehaviour;
import dev.createpolymer.contraptions.AbstractContraptionEntity.ContraptionRotationState;
import dev.createpolymer.foundation.collision.CollisionList;
import dev.createpolymer.foundation.collision.CollisionList.Populate;
import dev.createpolymer.foundation.collision.ContinuousOBBCollider;
import dev.createpolymer.foundation.collision.Matrix3d;
import dev.createpolymer.foundation.collision.OrientedBB;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes.DoubleLineConsumer;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ported from Create's ContraptionCollider, server side only. Mobs, items and other entities are pushed and carried
 * exactly like Create does. Vanilla player clients run their own physics, so server players are never moved by the
 * collision: {@link ContraptionPlayerSupport} keeps them standing on the contraption instead.
 */
public class ContraptionCollider {

	/**
	 * Implemented by movement behaviours that break the block in front of them (Create checks for
	 * BlockBreakingMovementBehaviour and HarvesterMovementBehaviour here): a contraption is only stopped by a block
	 * such an actor cannot break.
	 */
	public interface BlockBreaker {
		boolean canBreakAt(Level world, BlockPos pos, BlockState state);
	}

	static void collideEntities(AbstractContraptionEntity contraptionEntity) {
		Level world = contraptionEntity.level();
		Contraption contraption = contraptionEntity.getContraption();
		AABB bounds = contraptionEntity.getBoundingBox();

		if (contraption == null)
			return;
		if (bounds == null)
			return;
		if (!contraptionEntity.collisionEnabled())
			return;

		Vec3 contraptionPosition = contraptionEntity.position();
		Vec3 contraptionMotion = contraptionPosition.subtract(contraptionEntity.getPrevPositionVec());
		Vec3 anchorVec = contraptionEntity.getAnchorVec();
		ContraptionRotationState rotation = null;

		CollisionList denseViableColliders = new CollisionList();

		List<Entity> entitiesWithinAABB = world.getEntitiesOfClass(Entity.class, bounds.inflate(2)
			.expandTowards(0, 32, 0), contraptionEntity::canCollideWith);
		for (Entity entity : entitiesWithinAABB) {
			if (!entity.isAlive() || world.tickRateManager().isEntityFrozen(entity))
				continue;

			boolean isPlayer = entity instanceof Player;

			entity.getSelfAndPassengers()
				.forEach(e -> {
					if (e instanceof ServerPlayer sp)
						sp.connection.resetFlyingTicks();
				});

			// Init matrix
			if (rotation == null)
				rotation = contraptionEntity.getRotationState();
			Matrix3d rotationMatrix = rotation.asMatrix();

			// Transform entity position and motion to local space
			Vec3 entityPosition = entity.position();
			AABB entityBounds = entity.getBoundingBox();
			Vec3 motion = entity.getDeltaMovement();
			float yawOffset = rotation.getYawOffset();
			Vec3 position = getWorldToLocalTranslation(entity, anchorVec, rotationMatrix, yawOffset);

			// Players stand where the client puts them, a little off the server's view of the surface
			if (isPlayer)
				entityBounds = entityBounds.expandTowards(0, -ContraptionPlayerSupport.SUPPORT_REACH, 0);

			motion = motion.subtract(contraptionMotion);
			motion = rotationMatrix.transform(motion);

			// Prepare entity bounds
			AABB localBB = entityBounds.move(position)
				.inflate(1.0E-7D);

			OrientedBB obb = new OrientedBB(localBB);
			obb.setRotation(rotationMatrix);

			// Use simplified bbs when present
			CollisionList collidableBBs = contraption.getSimplifiedEntityColliders();

			if (collidableBBs == null) {
				// Else find 'nearby' individual block shapes to collide with
				collidableBBs = new CollisionList();

				getPotentiallyCollidedShapes(world, contraption, localBB.expandTowards(motion), new Populate(collidableBBs));
			}

			var collisionResult = ContinuousOBBCollider.collideMany(collidableBBs, denseViableColliders, obb, motion, entity.maxUpStep(), !rotation.hasVerticalRotation());

			// Resolve collision
			Vec3 entityMotion = entity.getDeltaMovement();
			Vec3 entityMotionNoTemporal = entityMotion;
			Vec3 collisionNormal = collisionResult.normal;
			Vec3 collisionLocation = collisionResult.location;
			Vec3 totalResponse = collisionResult.collisionResponse;
			boolean surfaceCollision = collisionResult.surfaceCollision;
			boolean hardCollision = !totalResponse.equals(Vec3.ZERO);
			boolean temporalCollision = collisionResult.temporalResponse != 1;
			Vec3 motionResponse = !temporalCollision ? motion
				: motion.normalize()
					.scale(motion.length() * collisionResult.temporalResponse);

			motionResponse = rotationMatrix.transformTransposed(motionResponse)
				.add(contraptionMotion);
			totalResponse = rotationMatrix.transformTransposed(totalResponse);
			totalResponse = VecHelper.rotate(totalResponse, yawOffset, Axis.Y);
			collisionNormal = rotationMatrix.transformTransposed(collisionNormal);
			collisionNormal = VecHelper.rotate(collisionNormal, yawOffset, Axis.Y);
			collisionNormal = collisionNormal.normalize();
			collisionLocation = rotationMatrix.transformTransposed(collisionLocation);
			collisionLocation = VecHelper.rotate(collisionLocation, yawOffset, Axis.Y);

			double bounce = 0;
			double slide = 0;

			if (!collisionLocation.equals(Vec3.ZERO)) {
				collisionLocation = collisionLocation.add(entity.position()
					.add(entity.getBoundingBox()
						.getCenter())
					.scale(.5f));
				if (temporalCollision)
					collisionLocation = collisionLocation.add(0, motionResponse.y, 0);

				BlockPos pos = BlockPos.containing(contraptionEntity.toLocalVector(entity.position(), 0));
				if (contraption.getBlocks()
					.containsKey(pos)) {
					BlockState blockState = contraption.getBlocks()
						.get(pos).state();
					if (blockState.is(BlockTags.CLIMBABLE)) {
						surfaceCollision = true;
						totalResponse = totalResponse.add(0, .1f, 0);
					}
				}

				pos = BlockPos.containing(contraptionEntity.toLocalVector(collisionLocation, 0));
				if (contraption.getBlocks()
					.containsKey(pos)) {
					BlockState blockState = contraption.getBlocks()
						.get(pos).state();

					MovingInteractionBehaviour movingInteractionBehaviour = contraption.getInteractors().get(pos);
					if (movingInteractionBehaviour != null)
						movingInteractionBehaviour.handleEntityCollision(entity, pos, contraptionEntity);

					bounce = getBounceMultiplier(blockState.getBlock());
					slide = Math.max(0, blockState.getBlock().getFriction() - .6f);
				}
			}

			if (isPlayer) {
				// Players only report whether they are supported; the client moves them.
				if (surfaceCollision && entity instanceof ServerPlayer player) {
					contraptionEntity.registerColliding(entity);
					ContraptionPlayerSupport.supportPlayer(player, contraptionEntity);
				}
				continue;
			}

			boolean hasNormal = !collisionNormal.equals(Vec3.ZERO);
			boolean anyCollision = hardCollision || temporalCollision;

			if (bounce > 0 && hasNormal && anyCollision
				&& bounceEntity(entity, collisionNormal, contraptionEntity, bounce)) {
				entity.level().playSound(null, entity.getX(),
					entity.getY(), entity.getZ(), SoundEvents.SLIME_BLOCK_FALL, SoundSource.BLOCKS, .5f, 1);
				continue;
			}

			if (temporalCollision) {
				double idealVerticalMotion = motionResponse.y;
				if (idealVerticalMotion != entityMotion.y) {
					entity.setDeltaMovement(entityMotion.multiply(1, 0, 1)
						.add(0, idealVerticalMotion, 0));
					entityMotion = entity.getDeltaMovement();
				}
			}

			if (hardCollision) {
				double motionX = entityMotion.x();
				double motionY = entityMotion.y();
				double motionZ = entityMotion.z();
				double intersectX = totalResponse.x();
				double intersectY = totalResponse.y();
				double intersectZ = totalResponse.z();

				double horizonalEpsilon = 1 / 128f;
				if (motionX != 0 && Math.abs(intersectX) > horizonalEpsilon && motionX > 0 == intersectX < 0)
					entityMotion = entityMotion.multiply(0, 1, 1);
				if (motionY != 0 && intersectY != 0 && motionY > 0 == intersectY < 0)
					entityMotion = entityMotion.multiply(1, 0, 1)
						.add(0, contraptionMotion.y, 0);
				if (motionZ != 0 && Math.abs(intersectZ) > horizonalEpsilon && motionZ > 0 == intersectZ < 0)
					entityMotion = entityMotion.multiply(1, 1, 0);

			}

			if (bounce == 0 && slide > 0 && hasNormal && anyCollision && rotation.hasVerticalRotation()) {
				double slideFactor = collisionNormal.multiply(1, 0, 1)
					.length() * 1.25f;
				Vec3 motionIn = entityMotionNoTemporal.multiply(0, .9, 0)
					.add(0, -.01f, 0);
				Vec3 slideNormal = collisionNormal.cross(motionIn.cross(collisionNormal))
					.normalize();
				Vec3 newMotion = entityMotion.multiply(.85, 0, .85)
					.add(slideNormal.scale((.2f + slide) * motionIn.length() * slideFactor)
						.add(0, -.1f - collisionNormal.y * .125f, 0));
				entity.setDeltaMovement(newMotion);
				entityMotion = entity.getDeltaMovement();
			}

			if (!hardCollision && !surfaceCollision)
				continue;

			Vec3 allowedMovement = collide(totalResponse, entity);
			entity.setPos(entityPosition.x + allowedMovement.x, entityPosition.y + allowedMovement.y,
				entityPosition.z + allowedMovement.z);
			entityPosition = entity.position();

			entity.needsSync = true;
			Vec3 contactPointMotion = Vec3.ZERO;

			if (surfaceCollision) {
				contraptionEntity.registerColliding(entity);
				entity.fallDistance = 0;
				boolean canWalk = bounce != 0 || slide == 0;
				if (canWalk || !rotation.hasVerticalRotation()) {
					if (canWalk)
						entity.setOnGround(true);
					if (entity instanceof ItemEntity ie && ie.getItem().is(net.minecraft.world.item.Items.DIAMOND) && ie.getItem().getCount() == 1)
						entityMotion = entityMotion.multiply(.5f, 1, .5f);
				}
				contactPointMotion = contraptionEntity.getContactPointMotion(entityPosition);
				allowedMovement = collide(contactPointMotion, entity);
				entity.setPos(entityPosition.x + allowedMovement.x, entityPosition.y,
					entityPosition.z + allowedMovement.z);
			}

			entity.setDeltaMovement(entityMotion);
		}

	}

	static boolean bounceEntity(Entity entity, Vec3 normal, AbstractContraptionEntity contraption, double factor) {
		if (factor == 0)
			return false;
		if (entity.isSuppressingBounce())
			return false;

		Vec3 contactPointMotion = contraption.getContactPointMotion(entity.position());
		Vec3 motion = entity.getDeltaMovement()
			.subtract(contactPointMotion);
		Vec3 deltav = normal.scale(factor * 2 * motion.dot(normal));
		if (deltav.dot(deltav) < 0.1f)
			return false;
		entity.setDeltaMovement(entity.getDeltaMovement()
			.subtract(deltav));
		return true;
	}

	/** Create's BlockHelper.getBounceMultiplier */
	private static double getBounceMultiplier(Block block) {
		if (block instanceof SlimeBlock)
			return 0.8D;
		if (block instanceof BedBlock)
			return 0.66D;
		return 0.0D;
	}

	public static Vec3 getWorldToLocalTranslation(Entity entity, Vec3 anchorVec, Matrix3d rotationMatrix,
		float yawOffset) {
		Vec3 entityPosition = entity.position();
		Vec3 centerY = new Vec3(0, entity.getBoundingBox()
			.getYsize() / 2, 0);
		Vec3 position = entityPosition;
		position = position.add(centerY);
		position = worldToLocalPos(position, anchorVec, rotationMatrix, yawOffset);
		position = position.subtract(centerY);
		position = position.subtract(entityPosition);
		return position;
	}

	public static Vec3 worldToLocalPos(Vec3 worldPos, AbstractContraptionEntity contraptionEntity) {
		return worldToLocalPos(worldPos, contraptionEntity.getAnchorVec(),
			contraptionEntity.getRotationState());
	}

	public static Vec3 worldToLocalPos(Vec3 worldPos, Vec3 anchorVec, ContraptionRotationState rotation) {
		return worldToLocalPos(worldPos, anchorVec, rotation.asMatrix(), rotation.getYawOffset());
	}

	public static Vec3 worldToLocalPos(Vec3 worldPos, Vec3 anchorVec, Matrix3d rotationMatrix, float yawOffset) {
		Vec3 localPos = worldPos;
		localPos = localPos.subtract(anchorVec);
		localPos = localPos.subtract(VecHelper.CENTER_OF_ORIGIN);
		localPos = VecHelper.rotate(localPos, -yawOffset, Axis.Y);
		localPos = rotationMatrix.transform(localPos);
		localPos = localPos.add(VecHelper.CENTER_OF_ORIGIN);
		return localPos;
	}

	/** Like Entity#collide, without stepping up. */
	static Vec3 collide(Vec3 movement, Entity e) {
		AABB aabb = e.getBoundingBox();
		List<VoxelShape> list = e.level().getEntityCollisions(e, aabb.expandTowards(movement));
		return movement.lengthSqr() == 0.0D ? movement : collideBoundingBox(e, movement, aabb, e.level(), list);
	}

	private static void getPotentiallyCollidedShapes(Level world, Contraption contraption, AABB localBB, DoubleLineConsumer out) {
		double height = localBB.getYsize();
		double width = localBB.getXsize();
		double horizontalFactor = (height > width && width != 0) ? height / width : 1;
		double verticalFactor = (width > height && height != 0) ? width / height : 1;
		AABB blockScanBB = localBB.inflate(0.5f);
		blockScanBB = blockScanBB.inflate(horizontalFactor, verticalFactor, horizontalFactor);

		BlockPos min = BlockPos.containing(blockScanBB.minX, blockScanBB.minY, blockScanBB.minZ);
		BlockPos max = BlockPos.containing(blockScanBB.maxX, blockScanBB.maxY, blockScanBB.maxZ);

		for (BlockPos p : BlockPos.betweenClosed(min, max)) {
			if (contraption.blocks.containsKey(p) && !contraption.isHiddenInPortal(p)) {
				StructureBlockInfo info = contraption.getBlocks().get(p);

				BlockState blockState = info.state();
				BlockPos pos = info.pos();

				VoxelShape collisionShape = blockState.getCollisionShape(world, p)
						.move(pos.getX(), pos.getY(), pos.getZ());

				if (!collisionShape.isEmpty()) {
					collisionShape.forAllBoxes(out);
				}
			}
		}
	}

	public static boolean collideBlocks(AbstractContraptionEntity contraptionEntity) {
		if (!contraptionEntity.supportsTerrainCollision())
			return false;

		Level world = contraptionEntity.level();
		Vec3 motion = contraptionEntity.getDeltaMovement();
		TranslatingContraption contraption = (TranslatingContraption) contraptionEntity.getContraption();
		AABB bounds = contraptionEntity.getBoundingBox();
		Vec3 position = contraptionEntity.position();
		BlockPos gridPos = BlockPos.containing(position);

		if (contraption == null)
			return false;
		if (bounds == null)
			return false;
		if (motion.equals(Vec3.ZERO))
			return false;

		Direction movementDirection = Direction.getApproximateNearest(motion.x, motion.y, motion.z);

		// Blocks in the world
		if (movementDirection.getAxisDirection() == AxisDirection.POSITIVE)
			gridPos = gridPos.relative(movementDirection);
		if (isCollidingWithWorld(world, contraption, gridPos, movementDirection))
			return true;

		// Other moving Contraptions
		for (ControlledContraptionEntity otherContraptionEntity : world.getEntitiesOfClass(
			ControlledContraptionEntity.class, bounds.inflate(1), e -> !e.equals(contraptionEntity))) {

			if (!otherContraptionEntity.supportsTerrainCollision())
				continue;

			Vec3 otherMotion = otherContraptionEntity.getDeltaMovement();
			TranslatingContraption otherContraption = (TranslatingContraption) otherContraptionEntity.getContraption();
			AABB otherBounds = otherContraptionEntity.getBoundingBox();
			Vec3 otherPosition = otherContraptionEntity.position();

			if (otherContraption == null)
				return false;
			if (otherBounds == null)
				return false;

			if (!bounds.move(motion)
				.intersects(otherBounds.move(otherMotion)))
				continue;

			for (BlockPos colliderPos : contraption.getOrCreateColliders(world, movementDirection)) {
				colliderPos = colliderPos.offset(gridPos)
					.subtract(BlockPos.containing(otherPosition));
				if (!otherContraption.getBlocks()
					.containsKey(colliderPos))
					continue;
				return true;
			}
		}

		return false;
	}

	public static boolean isCollidingWithWorld(Level world, TranslatingContraption contraption, BlockPos anchor,
		Direction movementDirection) {
		for (BlockPos pos : contraption.getOrCreateColliders(world, movementDirection)) {
			BlockPos colliderPos = pos.offset(anchor);

			if (!world.isLoaded(colliderPos))
				return true;

			BlockState collidedState = world.getBlockState(colliderPos);
			StructureBlockInfo blockInfo = contraption.getBlocks()
				.get(pos);
			boolean emptyCollider = collidedState.getCollisionShape(world, pos)
				.isEmpty();

			if (collidedState.getBlock() instanceof CocoaBlock)
				continue;

			MovementBehaviour movementBehaviour = MovementBehaviour.REGISTRY.get(blockInfo.state());
			if (movementBehaviour instanceof BlockBreaker breaker) {
				if (!breaker.canBreakAt(world, colliderPos, collidedState) && !emptyCollider)
					return true;
				continue;
			}

			if (!collidedState.canBeReplaced() && !emptyCollider) {
				return true;
			}

		}
		return false;
	}

}
