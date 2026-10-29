package dev.createpolymer.kinetics.encased;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Create's EncasedBlock: a block made by using {@link #getCasing()} on its plain counterpart. */
public interface EncasedBlock {
	Block getCasing();

	/** The encased state that replaces {@code state} (the plain shaft or cogwheel) at {@code pos}. */
	BlockState encasedStateFor(BlockState state, Level level, BlockPos pos);
}
