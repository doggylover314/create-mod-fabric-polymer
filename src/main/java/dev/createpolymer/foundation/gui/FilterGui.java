package dev.createpolymer.foundation.gui;

import java.util.ArrayList;
import java.util.List;

import dev.createpolymer.logistics.filter.ListFilterItem;
import dev.createpolymer.registry.feature.FilterRegistry;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Create's FilterMenu / FilterScreen for the list filter: 18 ghost slots plus the allow/deny list and
 * respect/ignore data toggles and a reset button. The data is saved into the filter item on every change.
 */
public class FilterGui extends HeldItemGhostItemGui {
	private static final String PREFIX = "create.gui.filter.";

	private static final int MODE_BUTTON = 20;
	private static final int DATA_BUTTON = 22;
	private static final int RESET_BUTTON = 24;

	private boolean respectNBT;
	private boolean blacklist;

	public FilterGui(ServerPlayer player, ItemStack filterItem) {
		super(MenuType.GENERIC_9x3, player, filterItem.getItemName(), filterItem,
			((ListFilterItem) filterItem.getItem()).getFilterItemHandler(filterItem));
		respectNBT = filterItem.getOrDefault(FilterRegistry.FILTER_ITEMS_RESPECT_NBT, false);
		blacklist = filterItem.getOrDefault(FilterRegistry.FILTER_ITEMS_BLACKLIST, false);
		refreshButtons();
	}

	@Override
	protected boolean allowRepeats() {
		return false;
	}

	@Override
	protected void saveData(ItemStack filterItem) {
		boolean empty = true;
		for (int i = 0; i < ghostInventory.getSlots() && empty; i++)
			empty = ghostInventory.getStackInSlot(i)
				.isEmpty();
		if (empty) {
			filterItem.remove(FilterRegistry.FILTER_ITEMS);
		} else {
			List<ItemStack> stacks = new ArrayList<>(ghostInventory.getSlots());
			for (int i = 0; i < ghostInventory.getSlots(); i++)
				stacks.add(ghostInventory.getStackInSlot(i));
			filterItem.set(FilterRegistry.FILTER_ITEMS,
				ItemContainerContents.fromItems(stacks));
		}
		filterItem.set(FilterRegistry.FILTER_ITEMS_RESPECT_NBT, respectNBT);
		filterItem.set(FilterRegistry.FILTER_ITEMS_BLACKLIST, blacklist);
	}

	private void refreshButtons() {
		setSlot(MODE_BUTTON, new GuiElementBuilder(blacklist ? GuiText.dye(DyeColor.RED) : GuiText.dye(DyeColor.LIME))
			.setName(Component.translatable(PREFIX + (blacklist ? "deny_list" : "allow_list")).withStyle(ChatFormatting.GOLD))
			.addLoreLine(Component.translatable(PREFIX + (blacklist ? "deny_list" : "allow_list") + ".description")
				.withStyle(ChatFormatting.GRAY))
			.addLoreLine(GuiText.text("create.gui.filter_gui.click_to_toggle", "Click to switch")
				.withStyle(ChatFormatting.DARK_GRAY))
			.hideDefaultTooltip()
			.setCallback(() -> {
				blacklist = !blacklist;
				refreshButtons();
				onGhostChanged();
			}));

		setSlot(DATA_BUTTON, new GuiElementBuilder(respectNBT ? Items.NAME_TAG : Items.PAPER)
			.setName(Component.translatable(PREFIX + (respectNBT ? "respect_data" : "ignore_data")).withStyle(ChatFormatting.GOLD))
			.addLoreLine(Component.translatable(PREFIX + (respectNBT ? "respect_data" : "ignore_data") + ".description")
				.withStyle(ChatFormatting.GRAY))
			.addLoreLine(GuiText.text("create.gui.filter_gui.click_to_toggle", "Click to switch")
				.withStyle(ChatFormatting.DARK_GRAY))
			.hideDefaultTooltip()
			.setCallback(() -> {
				respectNBT = !respectNBT;
				refreshButtons();
				onGhostChanged();
			}));

		setSlot(RESET_BUTTON, new GuiElementBuilder(Items.BARRIER)
			.setName(GuiText.text("create.gui.filter_gui.reset", "Reset")
				.withStyle(ChatFormatting.RED))
			.hideDefaultTooltip()
			.setCallback(this::clearContents));
	}
}
