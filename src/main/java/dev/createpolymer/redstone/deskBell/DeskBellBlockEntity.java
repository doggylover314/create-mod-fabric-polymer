package dev.createpolymer.redstone.deskBell;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class DeskBellBlockEntity extends SmartBlockEntity {

	/** Counts the dings, so the model can start its animation for each of them. */
	public int dings;

	int blockStateTimer;

	public DeskBellBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		blockStateTimer = 0;
	}

	@Override
	public void tick() {
		super.tick();

		if (blockStateTimer == 0)
			return;

		blockStateTimer--;

		if (blockStateTimer > 0)
			return;
		BlockState blockState = getBlockState();
		if (blockState.getBlock() instanceof DeskBellBlock bell && blockState.getValue(DeskBellBlock.POWERED))
			bell.unPress(blockState, level, worldPosition);
	}

	@Override
	protected void write(ValueOutput tag) {
		super.write(tag);
		tag.putInt("Timer", blockStateTimer);
	}

	@Override
	protected void read(ValueInput tag) {
		super.read(tag);
		blockStateTimer = tag.getIntOr("Timer", 0);
	}

	public void ding() {
		blockStateTimer = 20;
		dings++;
		sendData();
	}

}
