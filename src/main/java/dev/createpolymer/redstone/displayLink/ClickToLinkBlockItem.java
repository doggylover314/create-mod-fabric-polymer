package dev.createpolymer.redstone.displayLink;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.createpolymer.registry.feature.DisplayRegistry;
import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Create's ClickToLinkBlockItem: right-click a block to select it, then place the block somewhere else to link it
 * to the selection. The selection is kept in the item (a server-only data component); sneaking clears it.
 */
public abstract class ClickToLinkBlockItem extends PolymerBlockItem {
	public ClickToLinkBlockItem(Block pBlock, Properties pProperties) {
		super(pBlock, pProperties, Items.TRIAL_KEY, true);
	}

	/**
	 * Create's linkableItemAlwaysPlacesWhenUsed: with the item in hand, right-clicking a block that is not a link
	 * uses the item, not the block (a chest would open, a lever would be flipped).
	 */
	public static void init() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || player.isSpectator())
				return InteractionResult.PASS;
			ItemStack usedItem = player.getItemInHand(hand);
			if (!(usedItem.getItem() instanceof ClickToLinkBlockItem blockItem))
				return InteractionResult.PASS;
			if (level.getBlockState(hit.getBlockPos())
				.is(blockItem.getBlock()))
				return InteractionResult.PASS;
			if (player.getCooldowns()
				.isOnCooldown(usedItem))
				return InteractionResult.PASS;
			return useItemAlways(player, hand, usedItem, hit);
		});
	}

	/** What the server does with the item after the block declined: ServerPlayerGameMode.useItemOn. */
	private static InteractionResult useItemAlways(Player player, InteractionHand hand, ItemStack itemStack,
		BlockHitResult hit) {
		UseOnContext context = new UseOnContext(player, hand, hit);
		InteractionResult result;
		if (player.hasInfiniteMaterials()) {
			int count = itemStack.getCount();
			result = itemStack.useOn(context);
			itemStack.setCount(count);
		} else {
			result = itemStack.useOn(context);
			if (result instanceof InteractionResult.Success success) {
				ItemStack resultItemStack = success.heldItemTransformedTo() != null ? success.heldItemTransformedTo()
					: player.getItemInHand(hand);
				if (resultItemStack != itemStack)
					player.setItemInHand(hand, resultItemStack);
			}
		}
		return result;
	}

	@Override
	public InteractionResult useOn(UseOnContext pContext) {
		ItemStack stack = pContext.getItemInHand();
		BlockPos pos = pContext.getClickedPos();
		Level level = pContext.getLevel();
		BlockState state = level.getBlockState(pos);
		Player player = pContext.getPlayer();
		String msgKey = getMessageTranslationKey();
		int maxDistance = getMaxDistanceFromSelection();

		if (player == null)
			return InteractionResult.FAIL;

		if (player.isShiftKeyDown() && stack.has(DisplayRegistry.CLICK_TO_LINK_DATA)) {
			if (level.isClientSide())
				return InteractionResult.SUCCESS;
			player.sendOverlayMessage(Component.translatable("create." + msgKey + ".clear"));
			stack.remove(DisplayRegistry.CLICK_TO_LINK_DATA);
			return InteractionResult.SUCCESS;
		}

		Identifier placedDim = level.dimension()
			.identifier();

		if (!stack.has(DisplayRegistry.CLICK_TO_LINK_DATA)) {
			if (!isValidTarget(level, pos)) {
				if (placeWhenInvalid()) {
					InteractionResult useOn = super.useOn(pContext);
					if (level.isClientSide() || useOn == InteractionResult.FAIL)
						return useOn;

					ItemStack itemInHand = player.getItemInHand(pContext.getHand());
					if (!itemInHand.isEmpty())
						stack.remove(DisplayRegistry.CLICK_TO_LINK_DATA);
					return useOn;
				}

				player.sendOverlayMessage(Component.translatable("create." + msgKey + ".invalid"));
				return InteractionResult.FAIL;
			}

			if (level.isClientSide())
				return InteractionResult.SUCCESS;

			player.sendOverlayMessage(Component.translatable("create." + msgKey + ".set"));
			stack.set(DisplayRegistry.CLICK_TO_LINK_DATA, new ClickToLinkData(pos, placedDim));
			return InteractionResult.SUCCESS;
		}

		ClickToLinkData data = stack.get(DisplayRegistry.CLICK_TO_LINK_DATA);
		BlockPos selectedPos = data.selectedPos();
		Identifier selectedDim = data.selectedDim();
		BlockPos placedPos = pos.relative(pContext.getClickedFace(), state.canBeReplaced() ? 0 : 1);

		if (maxDistance != -1 && (!selectedPos.closerThan(placedPos, maxDistance) || !selectedDim.equals(placedDim))) {
			player.sendOverlayMessage(Component.translatable("create." + msgKey + ".too_far")
				.withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}

		InteractionResult useOn = super.useOn(pContext);
		if (level.isClientSide() || useOn == InteractionResult.FAIL)
			return useOn;

		// Create hands the target to the placed block with the block entity data of the item
		if (useOn.consumesAction()) {
			BlockEntity placed = level.getBlockEntity(placedPos);
			if (placed != null && level.getBlockState(placedPos)
				.is(getBlock()))
				applyLink(placed, selectedPos, selectedDim);
		}

		ItemStack itemInHand = player.getItemInHand(pContext.getHand());
		if (!itemInHand.isEmpty())
			stack.remove(DisplayRegistry.CLICK_TO_LINK_DATA);
		player.sendOverlayMessage(Component.translatable("create." + msgKey + ".success")
			.withStyle(ChatFormatting.GREEN));
		return useOn;
	}

	/** Hands the selected position to the block that was just placed. */
	protected abstract void applyLink(BlockEntity placed, BlockPos selectedPos, Identifier selectedDim);

	public abstract int getMaxDistanceFromSelection();

	public abstract String getMessageTranslationKey();

	public boolean placeWhenInvalid() {
		return false;
	}

	public boolean isValidTarget(LevelAccessor level, BlockPos pos) {
		return true;
	}

	public record ClickToLinkData(BlockPos selectedPos, Identifier selectedDim) {
		public static final Codec<ClickToLinkData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.fieldOf("selected_pos").forGetter(ClickToLinkData::selectedPos),
			Identifier.CODEC.fieldOf("selected_dim").forGetter(ClickToLinkData::selectedDim)
		).apply(instance, ClickToLinkData::new));
	}
}
