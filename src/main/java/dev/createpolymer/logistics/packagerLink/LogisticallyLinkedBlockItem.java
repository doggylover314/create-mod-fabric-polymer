package dev.createpolymer.logistics.packagerLink;

import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.util.Lang;
import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Create's LogisticallyLinkedBlockItem: a block item that carries a network frequency in its block entity data.
 * Using it on a logistically linked block tunes it to that block's network; using it in the air clears the tuning.
 */
public class LogisticallyLinkedBlockItem extends PolymerBlockItem {

	public LogisticallyLinkedBlockItem(Block pBlock, Properties pProperties) {
		super(pBlock, pProperties, Items.TRIAL_KEY, true);
	}

	@Override
	public boolean isFoil(ItemStack pStack) {
		return isTuned(pStack);
	}

	public static boolean isTuned(ItemStack pStack) {
		return pStack.has(DataComponents.BLOCK_ENTITY_DATA);
	}

	@Nullable
	public static UUID networkFromStack(ItemStack pStack) {
		TypedEntityData<BlockEntityType<?>> data = pStack.get(DataComponents.BLOCK_ENTITY_DATA);
		if (data == null)
			return null;
		return data.getUnsafe()
			.read("Freq", UUIDUtil.CODEC)
			.orElse(null);
	}

	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext tooltipContext, TooltipDisplay display,
		Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
		super.appendHoverText(stack, tooltipContext, display, tooltipComponents, tooltipFlag);

		if (networkFromStack(stack) == null)
			return;

		tooltipComponents.accept(translate("logistically_linked.tooltip").withStyle(ChatFormatting.GOLD));
		tooltipComponents.accept(translate("logistically_linked.tooltip_clear").withStyle(ChatFormatting.GRAY));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand usedHand) {
		ItemStack stack = player.getItemInHand(usedHand);
		if (isTuned(stack)) {
			if (!level.isClientSide()) {
				level.playSound(null, player.blockPosition(), SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS,
					0.75f, 1.0f);
				player.sendOverlayMessage(translate("logistically_linked.cleared"));
				stack.remove(DataComponents.BLOCK_ENTITY_DATA);
			}
			return InteractionResult.SUCCESS;
		} else {
			return super.use(level, player, usedHand);
		}
	}

	@Override
	public InteractionResult useOn(UseOnContext pContext) {
		ItemStack stack = pContext.getItemInHand();
		BlockPos pos = pContext.getClickedPos();
		Level level = pContext.getLevel();
		Player player = pContext.getPlayer();

		if (player == null)
			return InteractionResult.FAIL;
		if (player.isShiftKeyDown())
			return super.useOn(pContext);

		LogisticallyLinkedBehaviour link = BlockEntityBehaviour.get(level, pos, LogisticallyLinkedBehaviour.TYPE);
		boolean tuned = isTuned(stack);

		if (link != null) {
			if (level.isClientSide())
				return InteractionResult.SUCCESS;
			if (!link.mayInteractMessage(player))
				return InteractionResult.SUCCESS;

			assignFrequency(stack, player, link.freqId);
			return InteractionResult.SUCCESS;
		}

		InteractionResult useOn = super.useOn(pContext);
		if (level.isClientSide() || useOn == InteractionResult.FAIL)
			return useOn;

		player.sendOverlayMessage(tuned ? translate("logistically_linked.connected")
			: translate("logistically_linked.new_network_started"));
		return useOn;
	}

	public static void assignFrequency(ItemStack stack, Player player, UUID frequency) {
		TypedEntityData<BlockEntityType<?>> data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
		CompoundTag tag = data == null ? new CompoundTag() : data.copyTagWithoutId();
		tag.store("Freq", UUIDUtil.CODEC, frequency);

		player.sendOverlayMessage(translate("logistically_linked.tuned"));

		BlockEntityType<?> type = ((IBE<?>) ((BlockItem) stack.getItem()).getBlock()).getBlockEntityType();
		stack.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(type, tag));
	}

	private static net.minecraft.network.chat.MutableComponent translate(String key) {
		return Lang.builder(CreatePolymer.MOD_ID)
			.translate(key)
			.component();
	}

}
