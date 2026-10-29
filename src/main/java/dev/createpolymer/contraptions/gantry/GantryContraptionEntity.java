package dev.createpolymer.contraptions.gantry;

import dev.createpolymer.contraptions.AbstractContraptionEntity;
import dev.createpolymer.contraptions.Contraption;
import dev.createpolymer.contraptions.ContraptionCollider;
import dev.createpolymer.contraptions.StructureTransform;
import dev.createpolymer.kinetics.gantry.GantryShaftBlock;
import dev.createpolymer.kinetics.gantry.GantryShaftBlockEntity;
import dev.createpolymer.registry.feature.PulleyRegistry;
import dev.createpolymer.util.NBTHelper;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class GantryContraptionEntity extends AbstractContraptionEntity {

	Direction movementAxis;
	double axisMotion;

	public double sequencedOffsetLimit;

	public GantryContraptionEntity(EntityType<?> entityTypeIn, Level worldIn) {
		super(entityTypeIn, worldIn);
		sequencedOffsetLimit = -1;
	}

	public static GantryContraptionEntity create(Level world, Contraption contraption, Direction movementAxis) {
		GantryContraptionEntity entity = new GantryContraptionEntity(PulleyRegistry.GANTRY_CONTRAPTION, world);
		entity.setContraption(contraption);
		entity.movementAxis = movementAxis;
		return entity;
	}

	public void limitMovement(double maxOffset) {
		sequencedOffsetLimit = maxOffset;
	}

	@Override
	protected void tickContraption() {
		if (!(contraption instanceof GantryContraption))
			return;

		double prevAxisMotion = axisMotion;

		checkPinionShaft();
		tickActors();
		Vec3 movementVec = getDeltaMovement();

		if (ContraptionCollider.collideBlocks(this)) {
			disassemble();
			return;
		}

		if (!isStalled() && tickCount > 2) {
			if (sequencedOffsetLimit >= 0)
				movementVec = VecHelper.clampComponentWise(movementVec, (float) sequencedOffsetLimit);
			move(movementVec.x, movementVec.y, movementVec.z);
			if (sequencedOffsetLimit > 0)
				sequencedOffsetLimit = Math.max(0, sequencedOffsetLimit - movementVec.length());
		}

		if (Math.signum(prevAxisMotion) != Math.signum(axisMotion) && prevAxisMotion != 0)
			contraption.stop(level());
	}

	@Override
	public void disassemble() {
		sequencedOffsetLimit = -1;
		super.disassemble();
	}

	protected void checkPinionShaft() {
		Vec3 movementVec;
		Direction facing = ((GantryContraption) contraption).getFacing();
		Vec3 currentPosition = getAnchorVec().add(.5, .5, .5);
		BlockPos gantryShaftPos = BlockPos.containing(currentPosition).relative(facing.getOpposite());

		BlockEntity be = level().getBlockEntity(gantryShaftPos);
		if (!(be instanceof GantryShaftBlockEntity gantryShaftBlockEntity) || !(be.getBlockState().getBlock() instanceof GantryShaftBlock)) {
			setContraptionMotion(Vec3.ZERO);
			disassemble();
			return;
		}

		BlockState blockState = be.getBlockState();
		Direction direction = blockState.getValue(GantryShaftBlock.FACING);

		float pinionMovementSpeed = gantryShaftBlockEntity.getPinionMovementSpeed();
		if (blockState.getValue(GantryShaftBlock.POWERED) || pinionMovementSpeed == 0) {
			setContraptionMotion(Vec3.ZERO);
			disassemble();
			return;
		}

		if (sequencedOffsetLimit >= 0)
			pinionMovementSpeed = (float) Mth.clamp(pinionMovementSpeed, -sequencedOffsetLimit, sequencedOffsetLimit);
		movementVec = Vec3.atLowerCornerOf(direction.getUnitVec3i())
			.scale(pinionMovementSpeed);

		Vec3 nextPosition = currentPosition.add(movementVec);
		double currentCoord = direction.getAxis()
			.choose(currentPosition.x, currentPosition.y, currentPosition.z);
		double nextCoord = direction.getAxis()
			.choose(nextPosition.x, nextPosition.y, nextPosition.z);

		if ((Mth.floor(currentCoord) + .5 < nextCoord != (pinionMovementSpeed * direction.getAxisDirection()
			.getStep() < 0)))
			if (!gantryShaftBlockEntity.canAssembleOn()) {
				setContraptionMotion(Vec3.ZERO);
				disassemble();
				return;
			}

		axisMotion = pinionMovementSpeed;
		setContraptionMotion(movementVec);
	}

	@Override
	protected void writeAdditional(CompoundTag compound, HolderLookup.Provider registries, boolean spawnPacket) {
		NBTHelper.writeEnum(compound, "GantryAxis", movementAxis);
		if (sequencedOffsetLimit >= 0)
			compound.putDouble("SequencedOffsetLimit", sequencedOffsetLimit);
		super.writeAdditional(compound, registries, spawnPacket);
	}

	@Override
	protected void readAdditional(CompoundTag compound, boolean spawnData) {
		movementAxis = NBTHelper.readEnum(compound, "GantryAxis", Direction.class);
		sequencedOffsetLimit =
			compound.contains("SequencedOffsetLimit") ? compound.getDoubleOr("SequencedOffsetLimit", -1) : -1;
		super.readAdditional(compound, spawnData);
	}

	@Override
	public Vec3 applyRotation(Vec3 localPos, float partialTicks) {
		return localPos;
	}

	@Override
	public Vec3 reverseRotation(Vec3 localPos, float partialTicks) {
		return localPos;
	}

	@Override
	protected StructureTransform makeStructureTransform() {
		return new StructureTransform(BlockPos.containing(getAnchorVec().add(.5, .5, .5)), 0, 0, 0);
	}

	@Override
	protected float getStalledAngle() {
		return 0;
	}

	@Override
	public void teleportTo(double p_70634_1_, double p_70634_3_, double p_70634_5_) {
	}

	@Override
	public ContraptionRotationState getRotationState() {
		return ContraptionRotationState.NONE;
	}

	public double getAxisCoord() {
		Vec3 anchorVec = getAnchorVec();
		return movementAxis.getAxis()
			.choose(anchorVec.x, anchorVec.y, anchorVec.z);
	}

}
