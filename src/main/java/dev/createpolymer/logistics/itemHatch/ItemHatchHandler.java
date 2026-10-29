package dev.createpolymer.logistics.itemHatch;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Create's ItemHatchHandler: a sneaking player right-clicking a hatch with a block in hand would place that block
 * instead of using the hatch. Here the hatch's interaction is run directly for that case.
 */
public final class ItemHatchHandler {
	private ItemHatchHandler() {}

	public static void registerEvents() {
		UseBlockCallback.EVENT.register(ItemHatchHandler::onBlockActivated);
	}

	public static InteractionResult onBlockActivated(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide() || !player.isShiftKeyDown())
			return InteractionResult.PASS;
		BlockState state = level.getBlockState(hit.getBlockPos());
		if (!(state.getBlock() instanceof ItemHatchBlock))
			return InteractionResult.PASS;

		InteractionResult result = state.useItemOn(player.getItemInHand(hand), level, player, hand, hit);
		if (result instanceof InteractionResult.TryEmptyHandInteraction)
			result = state.useWithoutItem(level, player, hit);
		return result;
	}
}
