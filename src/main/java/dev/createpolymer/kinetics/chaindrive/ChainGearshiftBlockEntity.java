package dev.createpolymer.kinetics.chaindrive;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Ported from Create's ChainGearshiftBlockEntity (MIT, The Create Team). */
public class ChainGearshiftBlockEntity extends ChainDriveBlockEntity {

	int signal;
	boolean signalChanged;

	public ChainGearshiftBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		signal = 0;
		setLazyTickRate(40);
	}

	@Override
	protected void write(ValueOutput compound) {
		compound.putInt("Signal", signal);
		super.write(compound);
	}

	@Override
	protected void read(ValueInput compound) {
		signal = compound.getIntOr("Signal", 0);
		super.read(compound);
	}

	public float getModifier() {
		return getModifierForSignal(signal);
	}

	public void neighbourChanged() {
		if (!hasLevel())
			return;
		int power = level.getBestNeighborSignal(worldPosition);
		if (power != signal)
			signalChanged = true;
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		neighbourChanged();
	}

	@Override
	public void tick() {
		super.tick();
		if (level.isClientSide())
			return;
		if (signalChanged) {
			signalChanged = false;
			analogSignalChanged(level.getBestNeighborSignal(worldPosition));
		}
	}

	protected void analogSignalChanged(int newSignal) {
		detachKinetics();
		removeSource();
		signal = newSignal;
		attachKinetics();
	}

	protected float getModifierForSignal(int newPower) {
		if (newPower == 0)
			return 1;
		return 1 + ((newPower + 1) / 16f);
	}

}
