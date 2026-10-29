package dev.createpolymer.fluids.pipes;

import dev.createpolymer.foundation.fluid.FluidStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

public class VanillaFluidTargets {

	public static boolean canProvideFluidWithoutCapability(BlockState state) {
		if (state.is(Blocks.CAULDRON))
			return true;
		if (state.is(Blocks.LAVA_CAULDRON))
			return true;
		if (state.is(Blocks.WATER_CAULDRON))
			return true;
		return false;
	}

	public static FluidStack drainBlock(Level level, BlockPos pos, BlockState state, boolean simulate) {
		if (state.is(Blocks.LAVA_CAULDRON)) {
			if (!simulate)
				level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), Block.UPDATE_ALL);
			return new FluidStack(Fluids.LAVA, 1000);
		}

		if (state.is(Blocks.WATER_CAULDRON) && state.getBlock() instanceof LayeredCauldronBlock lcb) {
			if (!lcb.isFull(state))
				return FluidStack.EMPTY;
			if (!simulate)
				level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), Block.UPDATE_ALL);
			return new FluidStack(Fluids.WATER, 1000);
		}

		return FluidStack.EMPTY;
	}

}
