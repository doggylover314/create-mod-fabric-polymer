package dev.createpolymer.contraptions;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.FluidState;

/**
 * Thin level view used for generating optimized collision shapes: block states come from the contraption, in local
 * coordinates. Create wraps a full Level here; shapes only need a BlockGetter.
 */
public class ContraptionWorld implements BlockGetter {
	final Level level;
	final Contraption contraption;
	private final int minY;
	private final int height;

	public ContraptionWorld(Level world, Contraption contraption) {
		this.level = world;
		this.contraption = contraption;

		// Include 1 block above/below contraption height range to avoid edge cases at chunk-section boundaries.
		minY = nextMultipleOf16(contraption.bounds.minY - 1);
		height = nextMultipleOf16(contraption.bounds.maxY + 1) - minY;
	}

	public Level getLevel() {
		return level;
	}

	// https://math.stackexchange.com/questions/291468
	private static int nextMultipleOf16(double a) {
		return (((Math.abs((int) a) - 1) | 15) + 1) * Mth.sign(a);
	}

	@Override
	public BlockState getBlockState(BlockPos pos) {
		StructureTemplate.StructureBlockInfo blockInfo = contraption.getBlocks().get(pos);

		if (blockInfo != null)
			return blockInfo.state();

		return Blocks.AIR.defaultBlockState();
	}

	@Override
	public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
		return null;
	}

	@Override
	public FluidState getFluidState(BlockPos pos) {
		return getBlockState(pos).getFluidState();
	}

	@Override
	public int getHeight() {
		return height;
	}

	@Override
	public int getMinY() {
		return minY;
	}
}
