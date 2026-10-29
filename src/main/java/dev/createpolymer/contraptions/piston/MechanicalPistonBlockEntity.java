package dev.createpolymer.contraptions.piston;

import dev.createpolymer.contraptions.AssemblyException;
import dev.createpolymer.contraptions.ContraptionCollider;
import dev.createpolymer.contraptions.ControlledContraptionEntity;
import dev.createpolymer.contraptions.DirectionalExtenderScrollOptionSlot;
import dev.createpolymer.contraptions.piston.MechanicalPistonBlock.PistonState;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.createpolymer.kinetics.base.IRotate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class MechanicalPistonBlockEntity extends LinearActuatorBlockEntity {
	protected int extensionLength;

	public MechanicalPistonBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	protected void read(ValueInput compound) {
		extensionLength = compound.getIntOr("ExtensionLength", 0);
		super.read(compound);
	}

	@Override
	protected void write(ValueOutput tag) {
		tag.putInt("ExtensionLength", extensionLength);
		super.write(tag);
	}

	@Override
	public void assemble() throws AssemblyException {
		if (!(level.getBlockState(worldPosition)
			.getBlock() instanceof MechanicalPistonBlock))
			return;

		Direction direction = getBlockState().getValue(BlockStateProperties.FACING);

		// Collect Construct
		PistonContraption contraption = new PistonContraption(direction, getMovementSpeed() < 0);
		if (!contraption.assemble(level, worldPosition))
			return;

		Direction positive = Direction.get(AxisDirection.POSITIVE, direction.getAxis());
		Direction movementDirection =
			getSpeed() > 0 ^ direction.getAxis() != Axis.Z ? positive : positive.getOpposite();

		BlockPos anchor = contraption.anchor.relative(direction, contraption.initialExtensionProgress);
		if (ContraptionCollider.isCollidingWithWorld(level, contraption, anchor.relative(movementDirection),
			movementDirection))
			return;

		// Check if not at limit already
		extensionLength = contraption.extensionLength;
		float resultingOffset = contraption.initialExtensionProgress + Math.signum(getMovementSpeed()) * .5f;
		if (resultingOffset <= 0 || resultingOffset >= extensionLength) {
			return;
		}

		// Run
		running = true;
		offset = contraption.initialExtensionProgress;
		sendData();

		BlockPos startPos = BlockPos.ZERO.relative(direction, contraption.initialExtensionProgress);
		contraption.removeBlocksFromWorld(level, startPos);
		movedContraption = ControlledContraptionEntity.create(getLevel(), this, contraption);
		resetContraptionToOffset();
		forceMove = true;
		level.addFreshEntity(movedContraption);

		level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, .5f, 1.2f);
	}

	@Override
	public void disassemble() {
		if (!running && movedContraption == null)
			return;
		if (!remove)
			getLevel().setBlock(worldPosition, getBlockState().setValue(MechanicalPistonBlock.STATE, PistonState.EXTENDED),
				3 | 16);
		if (movedContraption != null) {
			resetContraptionToOffset();
			movedContraption.disassemble();
			level.playSound(null, worldPosition, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, .5f, 1.2f);
		}
		running = false;
		movedContraption = null;
		sendData();

		if (remove)
			((MechanicalPistonBlock) getBlockState().getBlock()).playerWillDestroy(level, worldPosition,
				getBlockState(), null);
	}

	@Override
	protected void collided() {
		super.collided();
		if (!running && getMovementSpeed() > 0)
			assembleNextTick = true;
	}

	@Override
	public float getMovementSpeed() {
		float movementSpeed = Mth.clamp(convertToLinear(getSpeed()), -.49f, .49f);
		Direction pistonDirection = getBlockState().getValue(BlockStateProperties.FACING);
		int movementModifier = pistonDirection.getAxisDirection()
			.getStep() * (pistonDirection.getAxis() == Axis.Z ? -1 : 1);
		movementSpeed = movementSpeed * -movementModifier;

		int extensionRange = getExtensionRange();
		movementSpeed = Mth.clamp(movementSpeed, 0 - offset, extensionRange - offset);
		if (sequencedOffsetLimit >= 0)
			movementSpeed = (float) Mth.clamp(movementSpeed, -sequencedOffsetLimit, sequencedOffsetLimit);
		return movementSpeed;
	}

	@Override
	protected int getExtensionRange() {
		return extensionLength;
	}

	@Override
	protected void visitNewPosition() {
	}

	@Override
	protected Vec3 toMotionVector(float speed) {
		Direction pistonDirection = getBlockState().getValue(BlockStateProperties.FACING);
		return Vec3.atLowerCornerOf(pistonDirection.getUnitVec3i())
			.scale(speed);
	}

	@Override
	protected Vec3 toPosition(float offset) {
		Vec3 position = Vec3.atLowerCornerOf(getBlockState().getValue(BlockStateProperties.FACING)
				.getUnitVec3i())
			.scale(offset);
		return position.add(Vec3.atLowerCornerOf(movedContraption.getContraption().anchor));
	}

	@Override
	protected ValueBoxTransform getMovementModeSlot() {
		return new DirectionalExtenderScrollOptionSlot((state, d) -> {
			Axis axis = d.getAxis();
			Axis extensionAxis = state.getValue(MechanicalPistonBlock.FACING)
				.getAxis();
			Axis shaftAxis = ((IRotate) state.getBlock()).getRotationAxis(state);
			return extensionAxis != axis && shaftAxis != axis;
		});
	}

	@Override
	protected int getInitialOffset() {
		return movedContraption == null ? 0
			: ((PistonContraption) movedContraption.getContraption()).initialExtensionProgress;
	}
}
