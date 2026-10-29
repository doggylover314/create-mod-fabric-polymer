package dev.createpolymer.redstone;

import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Create's {@code IWrenchable} defaults for the blocks of this package: the wrench turns the block to the state
 * {@link #getRotatedBlockState} picks. (The shared {@link IWrenchable} only lets a block override what the wrench does.)
 */
public interface RedstoneWrenchable extends IWrenchable {

	@Override
	default InteractionResult onWrenched(BlockState state, UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState rotated = getRotatedBlockState(state, context.getClickedFace());
		if (!rotated.canSurvive(level, pos))
			return InteractionResult.PASS;

		KineticBlockEntity.switchToBlockState(level, pos, updateAfterWrenched(rotated, context));

		if (level.getBlockState(pos) != state)
			ModSounds.wrenchRotate(level, pos);

		return InteractionResult.SUCCESS;
	}

	default BlockState updateAfterWrenched(BlockState newState, UseOnContext context) {
		return Block.updateFromNeighbourShapes(newState, context.getLevel(), context.getClickedPos());
	}

	/** The part of Create's IWrenchable.getRotatedBlockState that applies to horizontally facing blocks. */
	default BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
		if (targetedFace.getAxis() == Direction.Axis.Y && originalState.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
			return originalState.setValue(BlockStateProperties.HORIZONTAL_FACING,
				originalState.getValue(BlockStateProperties.HORIZONTAL_FACING).getClockWise(targetedFace.getAxis()));
		return originalState;
	}
}
