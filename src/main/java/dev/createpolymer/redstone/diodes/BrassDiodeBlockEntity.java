package dev.createpolymer.redstone.diodes;

import static dev.createpolymer.redstone.diodes.BrassDiodeBlock.POWERING;

import java.util.List;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public abstract class BrassDiodeBlockEntity extends SmartBlockEntity {

	protected int state;
	ScrollValueBehaviour maxState;

	public BrassDiodeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		maxState = new BrassDiodeScrollValueBehaviour(Component.translatable("create.logistics.redstone_interval"), this,
			new BrassDiodeScrollSlot()).between(2, 60 * 20 * 60);
		maxState.withFormatter(this::format);
		maxState.withCallback(this::onMaxDelayChanged);
		maxState.setValue(defaultValue());
		behaviours.add(maxState);
	}

	protected int defaultValue() {
		return 2;
	}

	public float getProgress() {
		int max = Math.max(2, maxState.getValue());
		return Mth.clamp(state, 0, max) / (float) max;
	}

	public boolean isIdle() {
		return state == 0;
	}

	@Override
	public void tick() {
		super.tick();
		boolean powered = getBlockState().getValue(DiodeBlock.POWERED);
		boolean powering = getBlockState().getValue(POWERING);
		boolean atMax = state >= maxState.getValue();
		boolean atMin = state <= 0;
		updateState(powered, powering, atMax, atMin);
	}

	protected abstract void updateState(boolean powered, boolean powering, boolean atMax, boolean atMin);

	private void onMaxDelayChanged(int newMax) {
		state = Mth.clamp(state, 0, newMax);
		sendData();
	}

	@Override
	protected void read(ValueInput compound) {
		state = compound.getIntOr("State", 0);
		super.read(compound);
	}

	@Override
	public void write(ValueOutput compound) {
		compound.putInt("State", state);
		super.write(compound);
	}

	private String format(int value) {
		if (value < 60)
			return value + "t";
		if (value < 20 * 60)
			return (value / 20) + "s";
		return (value / 20 / 60) + "m";
	}

	public int getMaxDelay() {
		return maxState.getValue();
	}

}
