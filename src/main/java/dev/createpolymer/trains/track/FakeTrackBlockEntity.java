package dev.createpolymer.trains.track;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class FakeTrackBlockEntity extends BlockEntity {

	int keepAlive;

	public FakeTrackBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		keepAlive();
	}

	public void randomTick() {
		keepAlive--;
		if (keepAlive > 0)
			return;
		level.removeBlock(worldPosition, false);
	}

	public void keepAlive() {
		keepAlive = 3;
	}

}
