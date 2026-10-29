package dev.createpolymer.decoration.copycat;

import dev.createpolymer.foundation.block.ProperWaterloggedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.material.FluidState;

public abstract class WaterloggedCopycatBlock extends CopycatBlock implements ProperWaterloggedBlock {

	public WaterloggedCopycatBlock(Properties pProperties) {
		super(pProperties);
		registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
		super.createBlockStateDefinition(pBuilder.add(WATERLOGGED));
	}

	@Override
	protected FluidState getFluidState(BlockState pState) {
		return fluidState(pState);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext pContext) {
		return withWater(super.getStateForPlacement(pContext), pContext);
	}

	@Override
	protected BlockState updateShape(BlockState pState, LevelReader pLevel, ScheduledTickAccess ticks, BlockPos pCurrentPos,
		Direction pDirection, BlockPos pNeighborPos, BlockState pNeighborState, RandomSource random) {
		updateWater(pLevel, ticks, pState, pCurrentPos);
		return pState;
	}

}
