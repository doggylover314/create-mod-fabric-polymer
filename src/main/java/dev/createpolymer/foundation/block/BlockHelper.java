package dev.createpolymer.foundation.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create's BlockHelper. Only hasBlockSolidSide lives here; destroyBlock and its variants are in
 * {@link dev.createpolymer.fluids.PipeHelper}.
 */
public final class BlockHelper {
	private BlockHelper() {}

	public static boolean hasBlockSolidSide(BlockState state, BlockGetter blockGetter, BlockPos pos, Direction dir) {
		return !state.is(BlockTags.LEAVES)
			&& Block.isFaceFull(state.getCollisionShape(blockGetter, pos), dir);
	}
}
