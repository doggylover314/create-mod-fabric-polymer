package dev.createpolymer.kinetics.base;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.Config;
import dev.createpolymer.kinetics.KineticNetwork;
import dev.createpolymer.kinetics.RotationPropagator;
import dev.createpolymer.kinetics.TorquePropagator;
import dev.createpolymer.kinetics.base.IRotate.SpeedLevel;
import dev.createpolymer.kinetics.base.IRotate.StressImpact;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Ported from Create's KineticBlockEntity (MIT, The Create Team).
 * Client-only parts (sound, goggles rendering, Flywheel visuals) are removed.
 */
public class KineticBlockEntity extends SmartBlockEntity {

	public @Nullable Long network;
	public @Nullable BlockPos source;
	public boolean networkDirty;
	public boolean updateSpeed;
	public int preventSpeedUpdate;

	protected float speed;
	protected float capacity;
	protected float stress;
	protected boolean overStressed;
	protected boolean wasMoved;

	private int flickerTally;
	private int networkSize;
	private int validationCountdown;
	protected float lastStressApplied;
	protected float lastCapacityProvided;

	public KineticBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
		super(typeIn, pos, state);
		updateSpeed = true;
	}

	@Override
	public void initialize() {
		if (hasNetwork() && !level.isClientSide()) {
			KineticNetwork network = getOrCreateNetwork();
			if (!network.initialized)
				network.initFromTE(capacity, stress, networkSize);
			network.addSilently(this, lastCapacityProvided, lastStressApplied);
		}
		super.initialize();
	}

	@Override
	public void tick() {
		if (!level.isClientSide() && needsSpeedUpdate())
			attachKinetics();

		super.tick();

		preventSpeedUpdate = 0;

		if (level.isClientSide())
			return;

		if (validationCountdown-- <= 0) {
			validationCountdown = Config.kineticValidationFrequency;
			validateKinetics();
		}

		if (getFlickerScore() > 0)
			flickerTally = getFlickerScore() - 1;

		if (networkDirty) {
			if (hasNetwork())
				getOrCreateNetwork().updateNetwork();
			networkDirty = false;
		}
	}

	private void validateKinetics() {
		if (hasSource()) {
			if (!hasNetwork()) {
				removeSource();
				return;
			}

			if (!level.isLoaded(source))
				return;

			BlockEntity blockEntity = level.getBlockEntity(source);
			KineticBlockEntity sourceBE = blockEntity instanceof KineticBlockEntity k ? k : null;
			if (sourceBE == null || sourceBE.speed == 0) {
				removeSource();
				detachKinetics();
				return;
			}

			return;
		}

		if (speed != 0) {
			if (getGeneratedSpeed() == 0)
				speed = 0;
		}
	}

	public void updateFromNetwork(float maxStress, float currentStress, int networkSize) {
		networkDirty = false;
		this.capacity = maxStress;
		this.stress = currentStress;
		this.networkSize = networkSize;
		boolean overStressed = maxStress < currentStress && StressImpact.isEnabled();
		setChanged();

		if (overStressed != this.overStressed) {
			float prevSpeed = getSpeed();
			this.overStressed = overStressed;
			onSpeedChanged(prevSpeed);
			sendData();
		}
	}

	protected Block getStressConfigKey() {
		return getBlockState().getBlock();
	}

	public float calculateStressApplied() {
		float impact = (float) BlockStressValues.getImpact(getStressConfigKey());
		this.lastStressApplied = impact;
		return impact;
	}

	public float calculateAddedStressCapacity() {
		float capacity = (float) BlockStressValues.getCapacity(getStressConfigKey());
		this.lastCapacityProvided = capacity;
		return capacity;
	}

	public void onSpeedChanged(float previousSpeed) {
		boolean fromOrToZero = (previousSpeed == 0) != (getSpeed() == 0);
		boolean directionSwap = !fromOrToZero && Math.signum(previousSpeed) != Math.signum(getSpeed());
		if (fromOrToZero || directionSwap)
			flickerTally = getFlickerScore() + 5;
		setChanged();
	}

	@Override
	public void remove() {
		if (level != null && !level.isClientSide()) {
			if (hasNetwork())
				getOrCreateNetwork().remove(this);
			detachKinetics();
		}
		super.remove();
	}

	@Override
	protected void write(ValueOutput compound) {
		compound.putFloat("Speed", speed);

		if (needsSpeedUpdate())
			compound.putBoolean("NeedsSpeedUpdate", true);

		if (hasSource())
			compound.store("Source", BlockPos.CODEC, source);

		if (hasNetwork()) {
			ValueOutput networkTag = compound.child("Network");
			networkTag.putLong("Id", this.network);
			networkTag.putFloat("Stress", stress);
			networkTag.putFloat("Capacity", capacity);
			networkTag.putInt("Size", networkSize);

			if (lastStressApplied != 0)
				networkTag.putFloat("AddedStress", lastStressApplied);
			if (lastCapacityProvided != 0)
				networkTag.putFloat("AddedCapacity", lastCapacityProvided);
		}

		super.write(compound);
	}

	public boolean needsSpeedUpdate() {
		return updateSpeed;
	}

	@Override
	protected void read(ValueInput compound) {
		clearKineticInformation();

		// DO NOT READ kinetic information when placed after movement
		if (wasMoved) {
			super.read(compound);
			return;
		}

		speed = compound.getFloatOr("Speed", 0);

		source = compound.read("Source", BlockPos.CODEC).orElse(null);

		compound.child("Network").ifPresent(networkTag -> {
			network = networkTag.getLongOr("Id", 0);
			stress = networkTag.getFloatOr("Stress", 0);
			capacity = networkTag.getFloatOr("Capacity", 0);
			networkSize = networkTag.getIntOr("Size", 0);
			lastStressApplied = networkTag.getFloatOr("AddedStress", 0);
			lastCapacityProvided = networkTag.getFloatOr("AddedCapacity", 0);
			overStressed = capacity < stress && StressImpact.isEnabled();
		});

		super.read(compound);
	}

	public float getGeneratedSpeed() {
		return 0;
	}

	public boolean isSource() {
		return getGeneratedSpeed() != 0;
	}

	public float getSpeed() {
		if (overStressed || (level != null && level.tickRateManager().isFrozen()))
			return 0;
		return getTheoreticalSpeed();
	}

	public float getTheoreticalSpeed() {
		return speed;
	}

	public void setSpeed(float speed) {
		this.speed = speed;
	}

	public boolean hasSource() {
		return source != null;
	}

	public void setSource(BlockPos source) {
		this.source = source;
		if (level == null || level.isClientSide())
			return;

		BlockEntity blockEntity = level.getBlockEntity(source);
		if (!(blockEntity instanceof KineticBlockEntity sourceBE)) {
			removeSource();
			return;
		}

		setNetwork(sourceBE.network);
	}

	public void removeSource() {
		float prevSpeed = getSpeed();

		speed = 0;
		source = null;
		setNetwork(null);

		onSpeedChanged(prevSpeed);
	}

	public void setNetwork(@Nullable Long networkIn) {
		if (Objects.equals(network, networkIn))
			return;
		if (network != null)
			getOrCreateNetwork().remove(this);

		network = networkIn;
		setChanged();

		if (networkIn == null)
			return;

		network = networkIn;
		KineticNetwork network = getOrCreateNetwork();
		network.initialized = true;
		network.add(this);
	}

	public KineticNetwork getOrCreateNetwork() {
		return TorquePropagator.getOrCreateNetworkFor(this);
	}

	public boolean hasNetwork() {
		return network != null;
	}

	public void attachKinetics() {
		updateSpeed = false;
		RotationPropagator.handleAdded(level, worldPosition, this);
	}

	public void detachKinetics() {
		RotationPropagator.handleRemoved(level, worldPosition, this);
	}

	public boolean isSpeedRequirementFulfilled() {
		BlockState state = getBlockState();
		if (!(state.getBlock() instanceof IRotate def))
			return true;
		SpeedLevel minimumRequiredSpeedLevel = def.getMinimumRequiredSpeedLevel();
		return Math.abs(getSpeed()) >= minimumRequiredSpeedLevel.getSpeedValue();
	}

	public static void switchToBlockState(Level world, BlockPos pos, BlockState state) {
		if (world.isClientSide())
			return;

		BlockEntity blockEntity = world.getBlockEntity(pos);
		BlockState currentState = world.getBlockState(pos);
		boolean isKinetic = blockEntity instanceof KineticBlockEntity;

		if (currentState == state)
			return;
		if (blockEntity == null || !isKinetic) {
			world.setBlock(pos, state, Block.UPDATE_ALL);
			return;
		}

		KineticBlockEntity kineticBlockEntity = (KineticBlockEntity) blockEntity;
		if (state.getBlock() instanceof KineticBlock kb && !kb.areStatesKineticallyEquivalent(currentState, state)) {
			if (kineticBlockEntity.hasNetwork())
				kineticBlockEntity.getOrCreateNetwork().remove(kineticBlockEntity);
			kineticBlockEntity.detachKinetics();
			kineticBlockEntity.removeSource();
		}

		if (blockEntity instanceof GeneratingKineticBlockEntity generatingBlockEntity)
			generatingBlockEntity.reActivateSource = true;

		world.setBlock(pos, state, Block.UPDATE_ALL);
	}

	public void clearKineticInformation() {
		speed = 0;
		source = null;
		network = null;
		overStressed = false;
		stress = 0;
		capacity = 0;
		lastStressApplied = 0;
		lastCapacityProvided = 0;
	}

	public void warnOfMovement() {
		wasMoved = true;
	}

	public int getFlickerScore() {
		return flickerTally;
	}

	public static float convertToDirection(float axisSpeed, Direction d) {
		return d.getAxisDirection() == AxisDirection.POSITIVE ? axisSpeed : -axisSpeed;
	}

	public static float convertToLinear(float speed) {
		return speed / 512f;
	}

	public static float convertToAngular(float speed) {
		// rpm -> deg/tick
		return speed * 360f / 60f / 20f;
	}

	public boolean isOverStressed() {
		return overStressed;
	}

	public float getNetworkStress() {
		return stress;
	}

	public float getNetworkCapacity() {
		return capacity;
	}

	// Custom Propagation

	/**
	 * Ratio of transferred rotation from this component to another. 0 means
	 * "no custom rule"; the standard shaft/cog rules then apply.
	 */
	public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo, BlockPos diff,
									 boolean connectedViaAxes, boolean connectedViaCogs) {
		return 0;
	}

	/** Extra positions the propagator should look at. Contains the 6 neighbours by default. */
	public List<BlockPos> addPropagationLocations(IRotate block, BlockState state, List<BlockPos> neighbours) {
		if (!canPropagateDiagonally(block, state))
			return neighbours;

		Axis axis = block.getRotationAxis(state);
		BlockPos.betweenClosedStream(new BlockPos(-1, -1, -1), new BlockPos(1, 1, 1))
			.forEach(offset -> {
				if (axis.choose(offset.getX(), offset.getY(), offset.getZ()) != 0)
					return;
				if (offset.distSqr(BlockPos.ZERO) != 2)
					return;
				neighbours.add(worldPosition.offset(offset));
			});
		return neighbours;
	}

	public boolean isCustomConnection(KineticBlockEntity other, BlockState state, BlockState otherState) {
		return false;
	}

	protected boolean canPropagateDiagonally(IRotate block, BlockState state) {
		return ICogWheel.isSmallCog(state);
	}

	public int getRotationAngleOffset(Axis axis) {
		return 0;
	}
}
