package dev.createpolymer.contraptions.piston;

import java.util.List;

import dev.createpolymer.contraptions.AbstractContraptionEntity;
import dev.createpolymer.contraptions.AssemblyException;
import dev.createpolymer.contraptions.ContraptionCollider;
import dev.createpolymer.contraptions.ControlledContraptionEntity;
import dev.createpolymer.contraptions.IControlContraption;
import dev.createpolymer.contraptions.IDisplayAssemblyExceptions;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.createpolymer.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public abstract class LinearActuatorBlockEntity extends KineticBlockEntity
	implements IControlContraption, IDisplayAssemblyExceptions {

	public float offset;
	public boolean running;
	public boolean assembleNextTick;
	public boolean needsContraption;
	public AbstractContraptionEntity movedContraption;
	protected boolean forceMove;
	protected ScrollOptionBehaviour<MovementMode> movementMode;
	protected boolean waitingForSpeedChange;
	protected AssemblyException lastException;
	protected double sequencedOffsetLimit;

	public LinearActuatorBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
		super(typeIn, pos, state);
		setLazyTickRate(3);
		forceMove = true;
		needsContraption = true;
		sequencedOffsetLimit = -1;
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		movementMode = new ScrollOptionBehaviour<>(MovementMode.class,
			Component.translatable("create.contraptions.movement_mode"), this, getMovementModeSlot());
		movementMode.withCallback(t -> waitingForSpeedChange = false);
		behaviours.add(movementMode);
	}

	@Override
	public void tick() {
		super.tick();

		if (movedContraption != null)
			if (!movedContraption.isAlive())
				movedContraption = null;

		if (isPassive())
			return;

		if (waitingForSpeedChange) {
			if (movedContraption != null) {
				movedContraption.setContraptionMotion(Vec3.ZERO);
			}
			return;
		}

		if (assembleNextTick) {
			assembleNextTick = false;
			if (running) {
				if (getSpeed() == 0)
					tryDisassemble();
				else
					sendData();
				return;
			} else {
				if (getSpeed() != 0)
					try {
						assemble();
						lastException = null;
					} catch (AssemblyException e) {
						lastException = e;
					}
				sendData();
			}
			return;
		}

		if (!running)
			return;

		boolean contraptionPresent = movedContraption != null;
		if (needsContraption && !contraptionPresent)
			return;

		float movementSpeed = getMovementSpeed();
		boolean locked = false;
		if (sequencedOffsetLimit > 0) {
			sequencedOffsetLimit = Math.max(0, sequencedOffsetLimit - Math.abs(movementSpeed));
			locked = sequencedOffsetLimit == 0;
		}
		float newOffset = offset + movementSpeed;
		if ((int) newOffset != (int) offset)
			visitNewPosition();

		if (locked) {
			forceMove = true;
			resetContraptionToOffset();
			sendData();
		}

		if (contraptionPresent) {
			if (moveAndCollideContraption()) {
				movedContraption.setContraptionMotion(Vec3.ZERO);
				offset = getGridOffset(offset);
				resetContraptionToOffset();
				collided();
				return;
			}
		}

		if (!contraptionPresent || !movedContraption.isStalled())
			offset = newOffset;

		int extensionRange = getExtensionRange();
		if (offset <= 0 || offset >= extensionRange) {
			offset = offset <= 0 ? 0 : extensionRange;
			moveAndCollideContraption();
			resetContraptionToOffset();
			tryDisassemble();
			if (waitingForSpeedChange) {
				forceMove = true;
				sendData();
			}
			return;
		}
	}

	protected boolean isPassive() {
		return false;
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		if (movedContraption != null)
			sendData();
	}

	protected int getGridOffset(float offset) {
		return Mth.clamp((int) (offset + .5f), 0, getExtensionRange());
	}

	public float getInterpolatedOffset(float partialTicks) {
		float interpolatedOffset =
			Mth.clamp(offset + (partialTicks - .5f) * getMovementSpeed(), 0, getExtensionRange());
		return interpolatedOffset;
	}

	@Override
	public void onSpeedChanged(float prevSpeed) {
		super.onSpeedChanged(prevSpeed);
		sequencedOffsetLimit = -1;

		if (isPassive())
			return;

		assembleNextTick = true;
		waitingForSpeedChange = false;

		if (movedContraption != null && Math.signum(prevSpeed) != Math.signum(getSpeed()) && prevSpeed != 0) {
			if (!movedContraption.isStalled()) {
				offset = Math.round(offset * 16) / 16;
				resetContraptionToOffset();
			}
			movedContraption.getContraption()
				.stop(level);
		}

		// A sequenced gearshift's "turn distance" instruction limits the offset in Create; wiring it up needs the
		// sequence context to travel through the network, see setSequencedOffsetLimit.
	}

	/** Limits how far (in blocks) the actuator may still move; negative for no limit. */
	public void setSequencedOffsetLimit(double limit) {
		sequencedOffsetLimit = limit;
	}

	@Override
	public void remove() {
		this.remove = true;
		if (!level.isClientSide())
			disassemble();
		super.remove();
	}

	@Override
	protected void write(ValueOutput compound) {
		compound.putBoolean("Running", running);
		compound.putBoolean("Waiting", waitingForSpeedChange);
		compound.putFloat("Offset", offset);
		if (sequencedOffsetLimit >= 0)
			compound.putDouble("SequencedOffsetLimit", sequencedOffsetLimit);
		AssemblyException.write(compound, lastException);
		super.write(compound);
	}

	@Override
	protected void read(ValueInput compound) {
		running = compound.getBooleanOr("Running", false);
		waitingForSpeedChange = compound.getBooleanOr("Waiting", false);
		offset = compound.getFloatOr("Offset", 0);
		sequencedOffsetLimit = compound.getDoubleOr("SequencedOffsetLimit", -1);
		lastException = AssemblyException.read(compound);
		super.read(compound);
	}

	@Override
	public AssemblyException getLastAssemblyException() {
		return lastException;
	}

	public abstract void disassemble();

	protected abstract void assemble() throws AssemblyException;

	protected abstract int getExtensionRange();

	protected abstract int getInitialOffset();

	protected abstract ValueBoxTransform getMovementModeSlot();

	protected abstract Vec3 toMotionVector(float speed);

	protected abstract Vec3 toPosition(float offset);

	protected void visitNewPosition() {}

	protected void tryDisassemble() {
		if (remove) {
			disassemble();
			return;
		}
		if (getMovementMode() == MovementMode.MOVE_NEVER_PLACE) {
			waitingForSpeedChange = true;
			return;
		}
		int initial = getInitialOffset();
		if ((int) (offset + .5f) != initial && getMovementMode() == MovementMode.MOVE_PLACE_RETURNED) {
			waitingForSpeedChange = true;
			return;
		}
		disassemble();
	}

	protected MovementMode getMovementMode() {
		return movementMode.get();
	}

	protected boolean moveAndCollideContraption() {
		if (movedContraption == null)
			return false;
		if (movedContraption.isStalled()) {
			movedContraption.setContraptionMotion(Vec3.ZERO);
			return false;
		}

		Vec3 motion = getMotionVector();
		movedContraption.setContraptionMotion(getMotionVector());
		movedContraption.move(motion.x, motion.y, motion.z);
		return ContraptionCollider.collideBlocks(movedContraption);
	}

	protected void collided() {
		offset = getGridOffset(offset - getMovementSpeed());
		resetContraptionToOffset();
		tryDisassemble();
	}

	protected void resetContraptionToOffset() {
		if (movedContraption == null)
			return;
		if (!movedContraption.isAlive())
			return;
		Vec3 vec = toPosition(offset);
		movedContraption.setPos(vec.x, vec.y, vec.z);
		if (getSpeed() == 0 || waitingForSpeedChange)
			movedContraption.setContraptionMotion(Vec3.ZERO);
	}

	public float getMovementSpeed() {
		float movementSpeed = Mth.clamp(convertToLinear(getSpeed()), -.49f, .49f);
		if (sequencedOffsetLimit >= 0)
			movementSpeed = (float) Mth.clamp(movementSpeed, -sequencedOffsetLimit, sequencedOffsetLimit);
		return movementSpeed;
	}

	public Vec3 getMotionVector() {
		return toMotionVector(getMovementSpeed());
	}

	@Override
	public void onStall() {
		if (!level.isClientSide()) {
			forceMove = true;
			sendData();
		}
	}

	public void onLengthBroken() {
		offset = 0;
		sendData();
	}

	@Override
	public boolean isValid() {
		return !isRemoved();
	}

	@Override
	public void attach(ControlledContraptionEntity contraption) {
		this.movedContraption = contraption;
		if (!level.isClientSide()) {
			this.running = true;
			sendData();
		}
	}

	@Override
	public boolean isAttachedTo(AbstractContraptionEntity contraption) {
		return movedContraption == contraption;
	}

	@Override
	public BlockPos getBlockPosition() {
		return worldPosition;
	}
}
