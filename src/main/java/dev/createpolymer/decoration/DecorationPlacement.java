package dev.createpolymer.decoration;

import java.util.function.UnaryOperator;

import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

/** Places one block of the item in the player's hand at a position chosen by the block that was clicked (Create's placement helpers). */
public final class DecorationPlacement {
	private DecorationPlacement() {}

	/** Create's config placementAssistRange. */
	public static final int PLACEMENT_ASSIST_RANGE = 12;

	public static InteractionResult placeInWorld(Level level, BlockItem blockItem, Player player, InteractionHand hand,
		BlockPos newPos, UnaryOperator<BlockState> transform) {
		if (!level.getBlockState(newPos).canBeReplaced())
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (level.isClientSide())
			return InteractionResult.SUCCESS;
		if (!level.mayInteract(player, newPos))
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		ItemStack stackBefore = player.getItemInHand(hand).copy();
		BlockState state = transform.apply(blockItem.getBlock().defaultBlockState());
		if (state.hasProperty(BlockStateProperties.WATERLOGGED))
			state = state.setValue(BlockStateProperties.WATERLOGGED, level.getFluidState(newPos).getType() == Fluids.WATER);
		if (!level.setBlockAndUpdate(newPos, state))
			return InteractionResult.FAIL;

		BlockState newState = level.getBlockState(newPos);
		SoundType soundtype = newState.getSoundType();
		level.playSound(null, newPos, soundtype.getPlaceSound(), SoundSource.BLOCKS, (soundtype.getVolume() + 1.0F) / 2.0F,
			soundtype.getPitch() * 0.8F);

		player.awardStat(Stats.ITEM_USED.get(blockItem));
		newState.getBlock().setPlacedBy(level, newPos, newState, player, stackBefore);
		if (player instanceof ServerPlayer serverPlayer)
			CriteriaTriggers.PLACED_BLOCK.trigger(serverPlayer, newPos, stackBefore);

		if (!player.isCreative())
			player.getItemInHand(hand).shrink(1);
		return InteractionResult.SUCCESS;
	}
}
