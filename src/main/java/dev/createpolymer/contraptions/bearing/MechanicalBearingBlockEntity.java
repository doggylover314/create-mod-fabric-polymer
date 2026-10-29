package dev.createpolymer.contraptions.bearing;

import java.util.List;

import dev.createpolymer.contraptions.AbstractContraptionEntity;
import dev.createpolymer.contraptions.AssemblyException;
import dev.createpolymer.contraptions.ControlledContraptionEntity;
import dev.createpolymer.contraptions.IControlContraption.RotationMode;
import dev.createpolymer.contraptions.IDisplayAssemblyExceptions;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import dev.createpolymer.kinetics.base.GeneratingKineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class MechanicalBearingBlockEntity extends GeneratingKineticBlockEntity
	implements IBearingBlockEntity, IDisplayAssemblyExceptions {

	protected ScrollOptionBehaviour<RotationMode> movementMode;
	protected ControlledContraptionEntity movedContraption;
	protected float angle;
	protected boolean running;
	protected boolean assembleNextTick;
	protected AssemblyException lastException;
	protected double sequencedAngleLimit;

	public MechanicalBearingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		setLazyTickRate(3);
		sequencedAngleLimit = -1;
	}

	@Override
	public boolean isWoodenTop() {
		return false;
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		movementMode = new ScrollOptionBehaviour<>(RotationMode.class,
			Component.translatable("create.contraptions.movement_mode"), this, getMovementModeSlot());
		behaviours.add(movementMode);
	}

	@Override
	public void remove() {
		if (!level.isClientSide())
			disassemble();
		super.remove();
	}

	@Override
	public void write(ValueOutput compound) {
		compound.putBoolean("Running", running);
		compound.putFloat("Angle", angle);
		if (sequencedAngleLimit >= 0)
			compound.putDouble("SequencedAngleLimit", sequencedAngleLimit);
		AssemblyException.write(compound, lastException);
		super.write(compound);
	}

	@Override
	protected void read(ValueInput compound) {
		if (wasMoved) {
			super.read(compound);
			return;
		}

		running = compound.getBooleanOr("Running", false);
		angle = compound.getFloatOr("Angle", 0);
		sequencedAngleLimit = compound.getDoubleOr("SequencedAngleLimit", -1);
		lastException = AssemblyException.read(compound);
		super.read(compound);
	}

	@Override
	public float getInterpolatedAngle(float partialTicks) {
		if (movedContraption == null || movedContraption.isStalled() || !running)
			partialTicks = 0;
		float angularSpeed = getAngularSpeed();
		if (sequencedAngleLimit >= 0)
			angularSpeed = (float) Mth.clamp(angularSpeed, -sequencedAngleLimit, sequencedAngleLimit);
		return Mth.lerp(partialTicks, angle, angle + angularSpeed);
	}

	@Override
	public void onSpeedChanged(float prevSpeed) {
		super.onSpeedChanged(prevSpeed);
		assembleNextTick = true;
		sequencedAngleLimit = -1;

		if (movedContraption != null && Math.signum(prevSpeed) != Math.signum(getSpeed()) && prevSpeed != 0) {
			if (!movedContraption.isStalled()) {
				angle = Math.round(angle);
				applyRotation();
			}
			movedContraption.getContraption()
				.stop(level);
		}

		// A sequenced gearshift's "turn angle" instruction limits the angle in Create (sequencedAngleLimit); wiring
		// it up needs the sequence context to travel through the network, see setSequencedAngleLimit.
	}

	/** Limits how far (in degrees) the bearing may still turn; negative for no limit. */
	public void setSequencedAngleLimit(double limit) {
		sequencedAngleLimit = limit;
	}

	public float getAngularSpeed() {
		float speed = convertToAngular(isWindmill() ? getGeneratedSpeed() : getSpeed());
		if (getSpeed() == 0)
			speed = 0;
		return speed;
	}

	@Override
	public AssemblyException getLastAssemblyException() {
		return lastException;
	}

	protected boolean isWindmill() {
		return false;
	}

	@Override
	public BlockPos getBlockPosition() {
		return worldPosition;
	}

	public void assemble() {
		if (!(level.getBlockState(worldPosition)
			.getBlock() instanceof BearingBlock))
			return;

		Direction direction = getBlockState().getValue(BearingBlock.FACING);
		BearingContraption contraption = new BearingContraption(isWindmill(), direction);
		try {
			if (!contraption.assemble(level, worldPosition))
				return;

			lastException = null;
		} catch (AssemblyException e) {
			lastException = e;
			sendData();
			return;
		}

		contraption.removeBlocksFromWorld(level, BlockPos.ZERO);
		movedContraption = ControlledContraptionEntity.create(level, this, contraption);
		BlockPos anchor = worldPosition.relative(direction);
		movedContraption.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
		movedContraption.setRotationAxis(direction.getAxis());
		level.addFreshEntity(movedContraption);

		BearingSounds.assemble(level, worldPosition);

		running = true;
		angle = 0;
		sendData();
		updateGeneratedRotation();
	}

	public void disassemble() {
		if (!running && movedContraption == null)
			return;
		angle = 0;
		sequencedAngleLimit = -1;
		if (isWindmill())
			applyRotation();
		if (movedContraption != null) {
			movedContraption.disassemble();
			BearingSounds.disassemble(level, worldPosition);
		}

		movedContraption = null;
		running = false;
		updateGeneratedRotation();
		assembleNextTick = false;
		sendData();
	}

	@Override
	public void tick() {
		super.tick();

		if (assembleNextTick) {
			assembleNextTick = false;
			if (running) {
				boolean canDisassemble = movementMode.get() == RotationMode.ROTATE_PLACE
					|| (isNearInitialAngle() && movementMode.get() == RotationMode.ROTATE_PLACE_RETURNED);
				if (speed == 0 && (canDisassemble || movedContraption == null || movedContraption.getContraption()
					.getBlocks()
					.isEmpty())) {
					if (movedContraption != null)
						movedContraption.getContraption()
							.stop(level);
					disassemble();
					return;
				}
			} else {
				if (speed == 0 && !isWindmill())
					return;
				assemble();
			}
		}

		if (!running)
			return;

		if (!(movedContraption != null && movedContraption.isStalled())) {
			float angularSpeed = getAngularSpeed();
			if (sequencedAngleLimit >= 0) {
				angularSpeed = (float) Mth.clamp(angularSpeed, -sequencedAngleLimit, sequencedAngleLimit);
				sequencedAngleLimit = Math.max(0, sequencedAngleLimit - Math.abs(angularSpeed));
			}
			float newAngle = angle + angularSpeed;
			angle = (float) (newAngle % 360);
		}

		applyRotation();
	}

	public boolean isNearInitialAngle() {
		return Math.abs(angle) < 22.5 || Math.abs(angle) > 360 - 22.5;
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		if (movedContraption != null)
			sendData();
	}

	protected void applyRotation() {
		if (movedContraption == null)
			return;
		movedContraption.setAngle(angle);
		BlockState blockState = getBlockState();
		if (blockState.hasProperty(BlockStateProperties.FACING))
			movedContraption.setRotationAxis(blockState.getValue(BlockStateProperties.FACING)
				.getAxis());
	}

	@Override
	public void attach(ControlledContraptionEntity contraption) {
		BlockState blockState = getBlockState();
		if (!(contraption.getContraption() instanceof BearingContraption))
			return;
		if (!blockState.hasProperty(BearingBlock.FACING))
			return;

		this.movedContraption = contraption;
		setChanged();
		BlockPos anchor = worldPosition.relative(blockState.getValue(BearingBlock.FACING));
		movedContraption.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
		if (!level.isClientSide()) {
			this.running = true;
			sendData();
		}
	}

	@Override
	public void onStall() {
		if (!level.isClientSide())
			sendData();
	}

	@Override
	public boolean isValid() {
		return !isRemoved();
	}

	@Override
	public boolean isAttachedTo(AbstractContraptionEntity contraption) {
		return movedContraption == contraption;
	}

	public boolean isRunning() {
		return running;
	}

	@Override
	public void setAngle(float forcedAngle) {
		angle = forcedAngle;
	}

	public ControlledContraptionEntity getMovedContraption() {
		return movedContraption;
	}
}
