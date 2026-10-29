package dev.createpolymer.contraptions.render;

import java.util.List;

import org.joml.Quaternionf;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Implemented by blocks that know how to look on a contraption. Vanilla blocks are shown with block displays and
 * everything else falls back to the block's item; a block implementing this shows the exact pieces instead
 * (the model items of {@code ModelItems}, block-centred, with their orientation).
 */
public interface ContraptionDisplay {

	/** One block-centred model piece. */
	record Piece(ItemStack stack, Quaternionf orientation) {
		public Piece(ItemStack stack) {
			this(stack, new Quaternionf());
		}
	}

	/** The pieces that draw {@code state} when it is part of a contraption. Moving parts stand still. */
	List<Piece> getContraptionPieces(BlockState state);
}
