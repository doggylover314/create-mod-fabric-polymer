package dev.createpolymer.redstone;

import java.util.HashSet;
import java.util.Set;

import dev.createpolymer.registry.ModItems;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Create's ItemUseOverrides: for the registered blocks, right-clicking always uses the block, whatever the player
 * holds and whether or not they sneak (the analog lever is lowered by sneaking, with an item in hand as well).
 */
public final class ItemUseOverrides {
	private static final Set<Block> OVERRIDES = new HashSet<>();
	private static boolean registered;

	private ItemUseOverrides() {}

	public static void addBlock(Block block) {
		OVERRIDES.add(block);
		if (registered)
			return;
		registered = true;
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || player.isSpectator())
				return InteractionResult.PASS;
			if (player.getItemInHand(hand).is(ModItems.WRENCH))
				return InteractionResult.PASS;

			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			if (!OVERRIDES.contains(state.getBlock()))
				return InteractionResult.PASS;

			BlockHitResult blockTrace = new BlockHitResult(Vec3.atCenterOf(pos), hit.getDirection(), pos, true);
			InteractionResult result = state.useWithoutItem(level, player, blockTrace);
			return result.consumesAction() ? result : InteractionResult.PASS;
		});
	}
}
