package dev.createpolymer.contraptions.chassis;

import dev.createpolymer.contraptions.glue.SuperGlueEntity;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ported from Create's StickerBlockEntity (MIT, The Create Team). The head's animation, sounds and particles that
 * Create runs on the client are done by {@link StickerBlock} and its display model.
 */
public class StickerBlockEntity extends SmartBlockEntity {

	public StickerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public boolean isBlockStateExtended() {
		BlockState blockState = getBlockState();
		boolean extended = blockState.getBlock() instanceof StickerBlock && blockState.getValue(StickerBlock.EXTENDED);
		return extended;
	}

	public boolean isAttachedToBlock() {
		BlockState blockState = getBlockState();
		if (!(blockState.getBlock() instanceof StickerBlock))
			return false;
		Direction direction = blockState.getValue(StickerBlock.FACING);
		return SuperGlueEntity.isValidFace(level, worldPosition.relative(direction), direction.getOpposite());
	}

}
