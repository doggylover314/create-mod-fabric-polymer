package dev.createpolymer.fluids;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.fluid.FluidStack;

/**
 * Two fluids meeting in a pipe or at an open end. Create posts PipeCollisionEvents with these results as the fallback
 * handler; here the fallback is the whole logic (the lava interactions of Create's own fluids are not ported).
 */
public class FluidReactions {

	public static void handlePipeFlowCollision(Level level, BlockPos pos, FluidStack fluid, FluidStack fluid2) {
		Fluid f1 = fluid.getFluid();
		Fluid f2 = fluid2.getFluid();

		PipeHelper.destroyBlock(level, pos, 1);

		BlockState state = getFlowCollisionResult(f1, f2);
		if (state != null)
			level.setBlockAndUpdate(pos, state);
	}

	@Nullable
	public static BlockState getFlowCollisionResult(Fluid f1, Fluid f2) {
		if (f1 == Fluids.WATER && f2 == Fluids.LAVA || f2 == Fluids.WATER && f1 == Fluids.LAVA)
			return Blocks.COBBLESTONE.defaultBlockState();
		return null;
	}

	public static void handlePipeSpillCollision(Level level, BlockPos pos, Fluid pipeFluid, FluidState worldFluid) {
		Fluid pf = PipeHelper.convertToStill(pipeFluid);
		Fluid wf = worldFluid.getType();

		BlockState state = getSpillCollisionResult(pf, wf);
		if (state != null)
			level.setBlockAndUpdate(pos, state);
	}

	@Nullable
	public static BlockState getSpillCollisionResult(Fluid pf, Fluid wf) {
		BlockState state = null;
		if (PipeHelper.isTag(pf, FluidTags.WATER) && wf == Fluids.LAVA) {
			state = Blocks.OBSIDIAN.defaultBlockState();
		} else if (pf == Fluids.WATER && wf == Fluids.FLOWING_LAVA) {
			state = Blocks.COBBLESTONE.defaultBlockState();
		} else if (pf == Fluids.LAVA && wf == Fluids.WATER) {
			state = Blocks.STONE.defaultBlockState();
		} else if (pf == Fluids.LAVA && wf == Fluids.FLOWING_WATER) {
			state = Blocks.COBBLESTONE.defaultBlockState();
		}
		return state;
	}

}
