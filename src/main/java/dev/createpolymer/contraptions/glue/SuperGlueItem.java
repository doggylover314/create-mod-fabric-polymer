package dev.createpolymer.contraptions.glue;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Create selects the glued area on the client (an outlined box between two clicked blocks) and sends the corners.
 * Without a client mod the selection is made with the item itself: right-click the first block, right-click the
 * second one and the area between them is glued (the outline is shown with particles). Sneak-clicking discards a
 * pending selection; with none pending, it removes the glue boxes around the clicked block.
 */
public class SuperGlueItem extends SimplePolymerItem {

	/** First selected corner per player. */
	private static final Map<UUID, BlockPos> SELECTIONS = new HashMap<>();

	public SuperGlueItem(Properties properties) {
		super(properties, Items.TRIAL_KEY, true);
	}

	/** Create's glueItemAlwaysPlacesWhenUsed: the glue acts on the clicked block instead of the block's own use. */
	public static void init() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (!(stack.getItem() instanceof SuperGlueItem item) || player.isSpectator())
				return InteractionResult.PASS;
			return item.useOn(new UseOnContext(player, hand, hit));
		});
	}

	@Override
	public boolean canDestroyBlock(ItemStack itemStack, BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.LivingEntity entity) {
		return false;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getPlayer() instanceof ServerPlayer player) || !(context.getLevel() instanceof ServerLevel level))
			return InteractionResult.SUCCESS;
		BlockPos pos = context.getClickedPos();
		BlockPos first = SELECTIONS.get(player.getUUID());

		if (player.isShiftKeyDown()) {
			if (first != null) {
				SELECTIONS.remove(player.getUUID());
				player.sendOverlayMessage(Component.translatable("create.super_glue.abort"));
			} else {
				boolean removed = false;
				for (SuperGlueEntity glue : level.getEntitiesOfClass(SuperGlueEntity.class, new AABB(pos))) {
					if (!glue.contains(pos))
						continue;
					glue.discard();
					removed = true;
				}
				if (removed)
					level.playSound(null, pos, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.BLOCKS, .5f, .75f);
			}
			return InteractionResult.SUCCESS;
		}

		if (first == null) {
			SELECTIONS.put(player.getUUID(), pos);
			SuperGlueEntity.spawnOutlineParticles(level, SuperGlueEntity.span(pos, pos));
			player.sendOverlayMessage(Component.translatable("create.super_glue.first_pos"));
			return InteractionResult.SUCCESS;
		}

		SELECTIONS.remove(player.getUUID());
		if (!first.closerThan(pos, 24)) {
			player.sendOverlayMessage(Component.translatable("create.super_glue.too_far"));
			return InteractionResult.SUCCESS;
		}
		Set<BlockPos> group = SuperGlueSelectionHelper.searchGlueGroup(level, first, pos, false);
		if (group == null || !group.contains(pos)) {
			player.sendOverlayMessage(Component.translatable("create.super_glue.cannot_reach"));
			return InteractionResult.SUCCESS;
		}
		if (!SuperGlueSelectionHelper.collectGlueFromInventory(player, 1, true)) {
			player.sendOverlayMessage(Component.translatable("create.super_glue.not_enough"));
			return InteractionResult.SUCCESS;
		}

		AABB bb = SuperGlueEntity.span(first, pos);
		SuperGlueSelectionHelper.collectGlueFromInventory(player, 1, false);
		SuperGlueEntity entity = new SuperGlueEntity(level, bb);
		level.addFreshEntity(entity);
		entity.spawnParticles();
		level.playSound(null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, .5f, .75f);
		player.sendOverlayMessage(Component.translatable("create.super_glue.success"));
		return InteractionResult.SUCCESS;
	}

}
