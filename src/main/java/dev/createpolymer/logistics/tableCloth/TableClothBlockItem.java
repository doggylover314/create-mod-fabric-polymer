package dev.createpolymer.logistics.tableCloth;

import java.util.function.Consumer;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.logistics.redstoneRequester.RedstoneRequesterBlock;
import dev.createpolymer.registry.feature.StockRegistry;
import dev.createpolymer.util.Lang;
import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

public class TableClothBlockItem extends PolymerBlockItem {

	public TableClothBlockItem(Block pBlock, Properties pProperties) {
		super(pBlock, pProperties, Items.TRIAL_KEY, true);
	}

	@Override
	public boolean isFoil(ItemStack pStack) {
		return pStack.has(StockRegistry.AUTO_REQUEST_DATA);
	}

	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext tooltipContext, TooltipDisplay display,
		Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
		super.appendHoverText(stack, tooltipContext, display, tooltipComponents, tooltipFlag);
		if (!isFoil(stack))
			return;

		tooltipComponents.accept(Lang.builder(CreatePolymer.MOD_ID)
			.translate("table_cloth.shop_configured")
			.style(ChatFormatting.GOLD)
			.component());

		RedstoneRequesterBlock.appendRequesterTooltip(stack, tooltipComponents);
	}

}
