package dev.createpolymer.logistics.redstoneRequester;

import java.util.function.Consumer;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.logistics.packagerLink.LogisticallyLinkedBlockItem;
import dev.createpolymer.registry.feature.StockRegistry;
import dev.createpolymer.util.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

public class RedstoneRequesterBlockItem extends LogisticallyLinkedBlockItem {

	public RedstoneRequesterBlockItem(Block pBlock, Properties pProperties) {
		super(pBlock, pProperties);
	}

	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext tooltipContext, TooltipDisplay display,
		Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
		if (!isTuned(stack))
			return;

		if (!stack.has(StockRegistry.AUTO_REQUEST_DATA)) {
			super.appendHoverText(stack, tooltipContext, display, tooltipComponents, tooltipFlag);
			return;
		}

		tooltipComponents.accept(Lang.builder(CreatePolymer.MOD_ID)
			.translate("logistically_linked.tooltip")
			.style(ChatFormatting.GOLD)
			.component());
		RedstoneRequesterBlock.appendRequesterTooltip(stack, tooltipComponents);
	}

}
