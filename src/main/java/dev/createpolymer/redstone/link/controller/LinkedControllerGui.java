package dev.createpolymer.redstone.link.controller;

import dev.createpolymer.foundation.gui.HeldItemGhostItemGui;
import dev.createpolymer.foundation.item.ItemHelper;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import dev.createpolymer.registry.feature.ToolsRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Create's LinkedControllerMenu / LinkedControllerScreen as an sgui chest: six columns, one per button, with the
 * two frequency items of the button below its label. The frequencies are saved into the controller on every change.
 */
public class LinkedControllerGui extends HeldItemGhostItemGui {
	private static final String[] KEYS = { "key.forward", "key.back", "key.left", "key.right", "key.jump", "key.sneak" };

	public LinkedControllerGui(ServerPlayer player, ItemStack controller) {
		super(MenuType.GENERIC_9x3, player, controller.getItemName(), controller, LinkedControllerItem.getFrequencyItems(controller));
		for (int column = 0; column < 9; column++) {
			if (column < KEYS.length)
				setSlot(column, new GuiElementBuilder(Items.PAPER)
					.setName(Component.translatable(KEYS[column])
						.withStyle(ChatFormatting.GOLD))
					.hideDefaultTooltip());
			else
				setSlot(column, filler());
			if (column >= KEYS.length) {
				setSlot(9 + column, filler());
				setSlot(18 + column, filler());
			}
		}
	}

	private static GuiElementBuilder filler() {
		return new GuiElementBuilder(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY)).setName(Component.empty())
			.hideTooltip();
	}

	/** Button column, then the frequency's row: two rows of six below the labels. */
	@Override
	protected int guiSlot(int ghostSlot) {
		return 9 + ghostSlot % 2 * 9 + ghostSlot / 2;
	}

	@Override
	protected int ghostSlot(int guiSlot) {
		int row = guiSlot / 9;
		int column = guiSlot % 9;
		if (guiSlot < 0 || row < 1 || row > 2 || column >= KEYS.length)
			return -1;
		return column * 2 + row - 1;
	}

	@Override
	protected boolean allowRepeats() {
		return true;
	}

	@Override
	protected void saveData(ItemStack contentHolder) {
		contentHolder.set(ToolsRegistry.LINKED_CONTROLLER_ITEMS, ItemHelper.containerContentsFromHandler(ghostInventory));
	}
}
