package dev.createpolymer.kinetics.crank;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity.SequenceContext;
import dev.createpolymer.kinetics.transmission.sequencer.SequencerInstructions;
import dev.createpolymer.registry.feature.KineticsExtraRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Ported from Create's ValveHandleBlockEntity (MIT, The Create Team).
 * Create's scroll value behaviour (the angle) is the plain {@link #angleInput} field here.
 */
public class ValveHandleBlockEntity extends HandCrankBlockEntity {
	/** The ScrollValueBehaviour's range. */
	public static final int MIN_ANGLE = -180;
	public static final int MAX_ANGLE = 180;
	public static final int DEFAULT_ANGLE = 45;

	/** Angle turned per use in degrees; negative values turn the other way. */
	public int angleInput = DEFAULT_ANGLE;
	public int cooldown;

	protected int startAngle;
	protected int targetAngle;
	protected int totalUseTicks;

	/** In Create this is a field of KineticBlockEntity, copied along the network by setSource. */
	public @Nullable SequenceContext sequenceContext;

	public ValveHandleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public int getAngleInput() {
		return angleInput;
	}

	public void setAngleInput(int value) {
		angleInput = Mth.clamp(value, MIN_ANGLE, MAX_ANGLE);
		setChanged();
	}

	@Override
	protected boolean clockwise() {
		return angleInput < 0 ^ backwards;
	}

	@Override
	protected net.minecraft.world.level.block.Block getStressConfigKey() {
		return KineticsExtraRegistry.COPPER_VALVE_HANDLE;
	}

	@Override
	protected void write(ValueOutput compound) {
		super.write(compound);
		compound.putInt("ScrollValue", angleInput);
		compound.putInt("TotalUseTicks", totalUseTicks);
		compound.putInt("StartAngle", startAngle);
		compound.putInt("TargetAngle", targetAngle);
		if (sequenceContext != null)
			compound.store("Sequence", SequenceContext.CODEC, sequenceContext);
	}

	@Override
	protected void read(ValueInput compound) {
		super.read(compound);
		angleInput = compound.getIntOr("ScrollValue", DEFAULT_ANGLE);
		totalUseTicks = compound.getIntOr("TotalUseTicks", 0);
		startAngle = compound.getIntOr("StartAngle", 0);
		targetAngle = compound.getIntOr("TargetAngle", 0);
		sequenceContext = compound.read("Sequence", SequenceContext.CODEC).orElse(null);
	}

	@Override
	public void tick() {
		super.tick();
		if (inUse == 0) {
			sequenceContext = null;
			if (cooldown > 0)
				cooldown--;
		}
	}

	/** True while an other generator drives the handle: it then spins like any kinetic block. */
	public boolean isDrivenByNetwork() {
		return inUse == 0 && source != null && getSpeed() != 0;
	}

	/** Angle of the handle in degrees around the positive axis of its facing, for when it is not driven by a network. */
	public float getIndependentAngle(float partialTicks) {
		int step = getBlockState().getOptionalValue(ValveHandleBlock.FACING)
			.orElse(Direction.SOUTH)
			.getAxisDirection()
			.getStep();

		return (inUse > 0 && totalUseTicks > 0
			? Mth.lerp(Math.min(totalUseTicks, totalUseTicks - inUse + partialTicks) / (float) totalUseTicks,
			startAngle, targetAngle)
			: targetAngle) * (backwards ? -1 : 1) * step;
	}

	public boolean showValue() {
		return inUse == 0;
	}

	public boolean activate(boolean sneak) {
		if (getTheoreticalSpeed() != 0)
			return false;
		if (inUse > 0 || cooldown > 0)
			return false;
		if (level.isClientSide())
			return true;

		// Always overshoot, target will stop early
		int value = angleInput;
		int target = Math.abs(value);
		int rotationSpeed = KineticsExtraRegistry.COPPER_VALVE_HANDLE.getRotationSpeed();
		double degreesPerTick = KineticBlockEntity.convertToAngular(rotationSpeed);
		inUse = (int) Math.ceil(target / degreesPerTick) + 2;

		// Create reads its client-side independent angle here, which is always 0 on the server
		float independentAngle = 0;
		startAngle = (int) ((independentAngle) % 90 + 360) % 90;
		targetAngle = Math.round((startAngle + (target > 135 ? 180 : 90) * Mth.sign(value)) / 90f) * 90;
		totalUseTicks = inUse;
		backwards = sneak;

		sequenceContext = SequenceContext.fromGearshift(SequencerInstructions.TURN_ANGLE, rotationSpeed, target);
		updateGeneratedRotation();
		cooldown = 4;

		return true;
	}

}
