package dev.createpolymer.kinetics.gauge;

import dev.createpolymer.Config;
import dev.createpolymer.kinetics.base.IRotate.StressImpact;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Ported from Create's GaugeBlockEntity, SpeedGaugeBlockEntity and StressGaugeBlockEntity (MIT, The Create Team). */
public class GaugeBlockEntity extends KineticBlockEntity {
	public final boolean stressGauge;
	public float dialTarget;
	public float dialState;
	public float prevDialState;

	public GaugeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, boolean stressGauge) {
		super(type, pos, state);
		this.stressGauge = stressGauge;
	}

	@Override
	protected void write(ValueOutput compound) {
		compound.putFloat("Value", dialTarget);
		super.write(compound);
	}

	@Override
	protected void read(ValueInput compound) {
		dialTarget = compound.getFloatOr("Value", 0);
		super.read(compound);
	}

	@Override
	public void tick() {
		super.tick();
		prevDialState = dialState;
		dialState += (dialTarget - dialState) * .125f;
		if (dialState > 1 && level.getRandom().nextFloat() < 1 / 2f)
			dialState -= (dialState - 1) * level.getRandom().nextFloat();
	}

	@Override
	public void onSpeedChanged(float prevSpeed) {
		super.onSpeedChanged(prevSpeed);
		if (!stressGauge) {
			dialTarget = getSpeedDialTarget(getSpeed());
		} else if (getSpeed() == 0) {
			dialTarget = 0;
		} else if (hasNetwork()) {
			updateStressDial(capacity, stress);
		}
		updateComparators();
		setChanged();
	}

	@Override
	public void updateFromNetwork(float maxStress, float currentStress, int networkSize) {
		super.updateFromNetwork(maxStress, currentStress, networkSize);
		if (stressGauge) {
			updateStressDial(maxStress, currentStress);
			updateComparators();
		}
	}

	private void updateStressDial(float maxStress, float currentStress) {
		if (!StressImpact.isEnabled())
			dialTarget = 0;
		else if (isOverStressed())
			dialTarget = 1.125f;
		else if (maxStress == 0)
			dialTarget = 0;
		else
			dialTarget = currentStress / maxStress;
	}

	private void updateComparators() {
		if (level != null && !level.isClientSide())
			level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
	}

	public static float getSpeedDialTarget(float speed) {
		speed = Math.abs(speed);
		float medium = Config.mediumSpeed;
		float fast = Config.fastSpeed;
		float max = Config.maxRotationSpeed;
		if (speed == 0)
			return 0;
		if (speed < medium)
			return Mth.lerp(speed / medium, 0, .45f);
		if (speed < fast)
			return Mth.lerp((speed - medium) / (fast - medium), .45f, .75f);
		return Mth.lerp((speed - fast) / (max - fast), .75f, 1.125f);
	}
}
