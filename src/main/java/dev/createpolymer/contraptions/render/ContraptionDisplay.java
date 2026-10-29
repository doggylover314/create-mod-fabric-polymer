package dev.createpolymer.contraptions.render;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Implemented by blocks that know how to look on a contraption. Vanilla blocks are shown with block displays and
 * everything else falls back to the block's item; a block implementing this shows the exact pieces instead
 * (the model items of {@code ModelItems}, block-centred, with their orientation).
 */
public interface ContraptionDisplay {

	/** One block-centred model piece, moved by {@code translation} inside the block before it is turned. */
	record Piece(ItemStack stack, Quaternionf orientation, Vector3f translation) {
		public Piece(ItemStack stack) {
			this(stack, new Quaternionf(), new Vector3f());
		}

		public Piece(ItemStack stack, Quaternionf orientation) {
			this(stack, orientation, new Vector3f());
		}
	}

	/**
	 * One block display piece, such as the material of a copycat. {@code matrix} places the unit cube of the block
	 * display (which starts at its corner) relative to the centre of the block, like CopycatModel's matrices do.
	 */
	record BlockPiece(BlockState state, Matrix4f matrix) {}

	/** The pieces that draw {@code state} when it is part of a contraption. Moving parts stand still. */
	List<Piece> getContraptionPieces(BlockState state);

	/**
	 * Same as {@link #getContraptionPieces(BlockState)} for blocks whose look depends on their block entity, which a
	 * contraption keeps as the saved data {@code blockEntityData} (null for a block without one).
	 */
	default List<Piece> getContraptionPieces(BlockState state, @Nullable CompoundTag blockEntityData) {
		return getContraptionPieces(state);
	}

	/** Block display pieces that are drawn in addition to {@link #getContraptionPieces(BlockState, CompoundTag)}. */
	default List<BlockPiece> getContraptionBlockPieces(BlockState state, @Nullable CompoundTag blockEntityData) {
		return List.of();
	}
}
