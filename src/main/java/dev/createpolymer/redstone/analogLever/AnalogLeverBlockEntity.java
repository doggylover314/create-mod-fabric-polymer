package dev.createpolymer.redstone.analogLever;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class AnalogLeverBlockEntity extends SmartBlockEntity {

	int state = 0;
	int lastChange;

	public AnalogLeverBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void write(ValueOutput compound) {
		compound.putInt("State", state);
		compound.putInt("ChangeTimer", lastChange);
		super.write(compound);
	}

	@Override
	protected void read(ValueInput compound) {
		state = compound.getIntOr("State", 0);
		lastChange = compound.getIntOr("ChangeTimer", 0);
		super.read(compound);
	}

	@Override
	public void tick() {
		super.tick();
		if (lastChange > 0) {
			lastChange--;
			if (lastChange == 0)
				updateOutput();
		}
	}

	private void updateOutput() {
		AnalogLeverBlock.updateNeighbors(getBlockState(), level, worldPosition);
	}

	public void changeState(boolean back) {
		int prevState = state;
		state += back ? -1 : 1;
		state = Mth.clamp(state, 0, 15);
		if (prevState != state)
			lastChange = 15;
		setChanged();
		sendData();
	}

	public int getState() {
		return state;
	}
}
