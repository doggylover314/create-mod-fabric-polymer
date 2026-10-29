package dev.createpolymer.contraptions;

import static dev.createpolymer.util.AngleHelper.angleLerp;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.registry.feature.ContraptionRegistry;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.NBTHelper;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

/**
 * Ex: Minecarts, Couplings <br>
 * Oriented Contraption Entities can rotate freely around two axes
 * simultaneously.
 * <p>
 * Minecart controllers (couplings, furnace cart fuel, picking carts up) and the stabilized-bearing variant are
 * not ported yet: the contraption hooks {@link Contraption#getStabilizedFacing()},
 * {@link Contraption#isRotationLocked()}, {@link Contraption#pausesActorsWhileRotating()} and
 * {@link Contraption#addExtraInventories(Entity)} stand in for Create's instanceof checks on those kinds.
 */
public class OrientedContraptionEntity extends AbstractContraptionEntity {

	protected Vec3 motionBeforeStall;
	protected boolean forceAngle;
	private boolean attachedExtraInventories;
	private boolean manuallyPlaced;

	/** Create's synched COUPLING and INITIAL_ORIENTATION data; nothing is synced to clients. */
	private UUID couplingId;
	private Direction initialOrientation = Direction.UP;

	public float prevYaw;
	public float yaw;
	public float targetYaw;

	public float prevPitch;
	public float pitch;

	public int nonDamageTicks;

	public OrientedContraptionEntity(EntityType<?> type, Level world) {
		super(type, world);
		motionBeforeStall = Vec3.ZERO;
		attachedExtraInventories = false;
		nonDamageTicks = 10;
	}

	public static OrientedContraptionEntity create(Level world, Contraption contraption, Direction initialOrientation) {
		OrientedContraptionEntity entity =
			new OrientedContraptionEntity(ContraptionRegistry.ORIENTED_CONTRAPTION, world);
		entity.setContraption(contraption);
		entity.setInitialOrientation(initialOrientation);
		entity.startAtInitialYaw();
		return entity;
	}

	public static OrientedContraptionEntity createAtYaw(Level world, Contraption contraption,
														Direction initialOrientation, float initialYaw) {
		OrientedContraptionEntity entity = create(world, contraption, initialOrientation);
		entity.startAtYaw(initialYaw);
		entity.manuallyPlaced = true;
		return entity;
	}

	public void setInitialOrientation(Direction direction) {
		initialOrientation = direction;
		if (isInitialOrientationPresent() && !manuallyPlaced)
			startAtInitialYaw();
	}

	public Direction getInitialOrientation() {
		return initialOrientation;
	}

	@Override
	public float getYawOffset() {
		return getInitialYaw();
	}

	public float getInitialYaw() {
		return (isInitialOrientationPresent() ? initialOrientation : Direction.SOUTH).toYRot();
	}

	@Override
	public ContraptionRotationState getRotationState() {
		return rotationStateFor(yaw, pitch);
	}

	@Override
	public ContraptionRotationState getPredictedRotationState(int ticks) {
		float yawDelta = AngleHelper.getShortestAngleDiff(prevYaw, yaw);
		return rotationStateFor(yaw + yawDelta * ticks, pitch + (pitch - prevPitch) * ticks);
	}

	private ContraptionRotationState rotationStateFor(float yaw, float pitch) {
		ContraptionRotationState crs = new ContraptionRotationState();

		float yawOffset = getYawOffset();
		crs.zRotation = pitch;
		crs.yRotation = -yaw + yawOffset;

		if (pitch != 0 && yaw != 0) {
			crs.secondYRotation = -yaw;
			crs.yRotation = yawOffset;
		}

		return crs;
	}

	@Override
	public void stopRiding() {
		if (!level().isClientSide() && isAlive())
			disassemble();
		super.stopRiding();
	}

	@Override
	protected void readAdditional(CompoundTag compound, boolean spawnPacket) {
		super.readAdditional(compound, spawnPacket);

		if (compound.contains("InitialOrientation"))
			initialOrientation = NBTHelper.readEnum(compound, "InitialOrientation", Direction.class);

		yaw = compound.getFloatOr("Yaw", 0);
		pitch = compound.getFloatOr("Pitch", 0);
		manuallyPlaced = compound.getBooleanOr("Placed", false);

		if (compound.contains("ForceYaw"))
			startAtYaw(compound.getFloatOr("ForceYaw", 0));

		ListTag vecNBT = compound.getListOrEmpty("CachedMotion");
		if (!vecNBT.isEmpty()) {
			motionBeforeStall = VecHelper.readNBT(vecNBT);
			if (!motionBeforeStall.equals(Vec3.ZERO))
				targetYaw = prevYaw = yaw += yawFromVector(motionBeforeStall);
			setDeltaMovement(Vec3.ZERO);
		}

		setCouplingId(compound.read("OnCoupling", net.minecraft.core.UUIDUtil.CODEC).orElse(null));
	}

	@Override
	protected void writeAdditional(CompoundTag compound, HolderLookup.Provider registries, boolean spawnPacket) {
		super.writeAdditional(compound, registries, spawnPacket);

		if (motionBeforeStall != null) {
			ListTag list = new ListTag();
			list.add(DoubleTag.valueOf(motionBeforeStall.x));
			list.add(DoubleTag.valueOf(motionBeforeStall.y));
			list.add(DoubleTag.valueOf(motionBeforeStall.z));
			compound.put("CachedMotion", list);
		}

		Direction optional = initialOrientation;
		if (optional.getAxis()
			.isHorizontal())
			NBTHelper.writeEnum(compound, "InitialOrientation", optional);
		if (forceAngle) {
			compound.putFloat("ForceYaw", yaw);
			forceAngle = false;
		}

		compound.putBoolean("Placed", manuallyPlaced);
		compound.putFloat("Yaw", yaw);
		compound.putFloat("Pitch", pitch);

		if (getCouplingId() != null)
			compound.store("OnCoupling", net.minecraft.core.UUIDUtil.CODEC, getCouplingId());
	}

	public boolean isInitialOrientationPresent() {
		return initialOrientation
			.getAxis()
			.isHorizontal();
	}

	public void startAtInitialYaw() {
		startAtYaw(getInitialYaw());
	}

	public void startAtYaw(float yaw) {
		targetYaw = this.yaw = prevYaw = yaw;
		forceAngle = true;
	}

	@Override
	public Vec3 applyRotation(Vec3 localPos, float partialTicks) {
		localPos = VecHelper.rotate(localPos, getInitialYaw(), Axis.Y);
		localPos = VecHelper.rotate(localPos, getViewXRot(partialTicks), Axis.Z);
		localPos = VecHelper.rotate(localPos, getViewYRot(partialTicks), Axis.Y);
		return localPos;
	}

	@Override
	public Vec3 reverseRotation(Vec3 localPos, float partialTicks) {
		localPos = VecHelper.rotate(localPos, -getViewYRot(partialTicks), Axis.Y);
		localPos = VecHelper.rotate(localPos, -getViewXRot(partialTicks), Axis.Z);
		localPos = VecHelper.rotate(localPos, -getInitialYaw(), Axis.Y);
		return localPos;
	}

	public float getViewYRot(float partialTicks) {
		return -(partialTicks == 1.0F ? yaw : angleLerp(partialTicks, prevYaw, yaw));
	}

	public float getViewXRot(float partialTicks) {
		return partialTicks == 1.0F ? pitch : angleLerp(partialTicks, prevPitch, pitch);
	}

	@Override
	protected void tickContraption() {
		if (nonDamageTicks > 0)
			nonDamageTicks--;
		Entity e = getVehicle();
		if (e == null)
			return;

		boolean rotationLock = contraption.isRotationLocked();
		boolean pauseWhileRotating = contraption.pausesActorsWhileRotating();
		boolean wasStalled = isStalled();

		Entity riding = e;
		while (riding.getVehicle() != null && contraption.getStabilizedFacing() == null)
			riding = riding.getVehicle();

		boolean isOnCoupling = false;
		UUID couplingId = getCouplingId();
		isOnCoupling = couplingId != null && riding instanceof AbstractMinecart;

		if (!attachedExtraInventories) {
			attachInventoriesFromRidingCarts(riding, isOnCoupling, couplingId);
			attachedExtraInventories = true;
		}

		boolean rotating = updateOrientation(rotationLock, wasStalled, riding, isOnCoupling);
		if (!rotating || !pauseWhileRotating)
			tickActors();
		boolean isStalled = isStalled();

		// vanilla carts only: Create's minecart controller (couplings) takes over this part
		if (isStalled) {
			if (!wasStalled)
				motionBeforeStall = riding.getDeltaMovement();
			riding.setDeltaMovement(0, 0, 0);
		}
		if (wasStalled && !isStalled) {
			riding.setDeltaMovement(motionBeforeStall);
			motionBeforeStall = Vec3.ZERO;
		}
	}

	protected boolean updateOrientation(boolean rotationLock, boolean wasStalled, Entity riding, boolean isOnCoupling) {
		if (isOnCoupling) {
			// coupled carts orient the contraption along the coupling: needs the minecart controllers
			return false;
		}

		Direction stabilizedFacing = contraption.getStabilizedFacing();
		if (stabilizedFacing != null) {
			if (!(riding instanceof OrientedContraptionEntity parent))
				return false;
			if (stabilizedFacing.getAxis()
				.isVertical())
				return false;
			prevYaw = yaw;
			yaw = AngleHelper.wrapAngle180(getInitialYaw() - parent.getInitialYaw()) - parent.getViewYRot(1);
			return false;
		}

		prevYaw = yaw;
		if (wasStalled)
			return false;

		boolean rotating = false;
		Vec3 movementVector = riding.getDeltaMovement();
		Vec3 locationDiff = riding.position()
			.subtract(riding.xo, riding.yo, riding.zo);
		if (!(riding instanceof AbstractMinecart))
			movementVector = locationDiff;
		Vec3 motion = movementVector.normalize();

		if (!rotationLock) {
			if (riding instanceof AbstractMinecart minecartEntity) {
				BlockPos railPosition = minecartEntity.blockPosition();
				BlockState blockState = level().getBlockState(railPosition);
				if (!(blockState.getBlock() instanceof BaseRailBlock))
					blockState = level().getBlockState(railPosition = railPosition.below());
				if (blockState.getBlock() instanceof BaseRailBlock abstractRailBlock) {
					RailShape railDirection = blockState.getValue(abstractRailBlock.getShapeProperty());
					Vec3 railVec = getRailVec(railDirection);
					if (railVec != null)
						motion = VecHelper.project(motion, railVec);
				}
			}

			if (motion.length() > 0) {
				targetYaw = yawFromVector(motion);
				if (targetYaw < 0)
					targetYaw += 360;
				if (yaw < 0)
					yaw += 360;
			}

			prevYaw = yaw;
			float maxApproachSpeed = (float) (motion.length() * 12f / (Math.max(1, getBoundingBox().getXsize() / 6f)));
			float yawHint = AngleHelper.getShortestAngleDiff(yaw, yawFromVector(locationDiff));
			float approach = AngleHelper.getShortestAngleDiff(yaw, targetYaw, yawHint);
			approach = Mth.clamp(approach, -maxApproachSpeed, maxApproachSpeed);
			yaw += approach;
			if (Math.abs(AngleHelper.getShortestAngleDiff(yaw, targetYaw)) < 1f)
				yaw = targetYaw;
			else
				rotating = true;
		}
		return rotating;
	}

	/** Create's MinecartSim2020.getRailVec for straight rails; curves do not constrain the direction. */
	@Nullable
	private static Vec3 getRailVec(RailShape shape) {
		return switch (shape) {
			case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> new Vec3(0, 0, 1);
			case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> new Vec3(1, 0, 0);
			default -> null;
		};
	}

	@Nullable
	public UUID getCouplingId() {
		return couplingId;
	}

	public void setCouplingId(@Nullable UUID id) {
		couplingId = id;
	}

	protected void attachInventoriesFromRidingCarts(Entity riding, boolean isOnCoupling, UUID couplingId) {
		if (!isOnCoupling) {
			contraption.addExtraInventories(riding);
			return;
		}
		// coupled carts: both carts' inventories, needs the minecart controllers
		contraption.addExtraInventories(riding);
	}

	@Override
	public Vec3 getVehicleAttachmentPoint(Entity entity) {
		return entity instanceof AbstractContraptionEntity ? Vec3.ZERO : new Vec3(0, 0.19, 0);
	}

	@Override
	public Vec3 getAnchorVec() {
		Vec3 anchorVec = super.getAnchorVec();
		return anchorVec.subtract(.5, 0, .5);
	}

	@Override
	public Vec3 getPrevAnchorVec() {
		Vec3 prevAnchorVec = super.getPrevAnchorVec();
		return prevAnchorVec.subtract(.5, 0, .5);
	}

	@Override
	protected StructureTransform makeStructureTransform() {
		BlockPos offset = BlockPos.containing(getAnchorVec().add(.5, .5, .5));
		return new StructureTransform(offset, 0, -yaw + getInitialYaw(), 0);
	}

	@Override
	protected float getStalledAngle() {
		return yaw;
	}
}
