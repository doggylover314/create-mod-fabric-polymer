package dev.createpolymer.equipment.toolbox;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import dev.createpolymer.foundation.item.handler.ItemHandlerHelper;
import net.minecraft.ChatFormatting;

/**
 * Create's ToolboxMenu as an sgui chest. Create lays the eight compartments out around a circle; here each row of the
 * chest holds two compartments of four slots. Clicking an empty slot of an empty compartment with an empty cursor
 * clears the compartment's item type, as in Create. The hopper button is the "return items to toolbox" key of
 * Create's radial menu (ToolboxDisposeAllPacket): it moves every item of the player that matches a compartment.
 */
public class ToolboxGui extends SimpleGui {

	private final ToolboxBlockEntity toolbox;

	public ToolboxGui(ServerPlayer player, ToolboxBlockEntity toolbox) {
		super(MenuType.GENERIC_9x4, player, true);
		this.toolbox = toolbox;
		setTitle(toolbox.getDisplayName());

		ToolboxInventory inventory = toolbox.getInventory();
		for (int compartment = 0; compartment < ToolboxInventory.COMPARTMENTS; compartment++) {
			int row = compartment / 2;
			int column = (compartment % 2) * 5;
			for (int i = 0; i < ToolboxInventory.STACKS_PER_COMPARTMENT; i++)
				setSlot(row * 9 + column + i, new ToolboxSlot(inventory,
					compartment * ToolboxInventory.STACKS_PER_COMPARTMENT + i, 0, 0));
		}

		for (int row = 0; row < 4; row++) {
			if (row == 0) {
				setSlot(4, new GuiElementBuilder(Items.HOPPER)
					.setName(Component.translatable("create.toolbox.depositBox"))
					.setCallback(this::depositAll)
					.hideTooltip());
				continue;
			}
			setSlot(row * 9 + 4, new GuiElementBuilder(Items.STAINED_GLASS_PANE.gray())
				.setName(Component.empty()
					.withStyle(ChatFormatting.RESET))
				.hideTooltip());
		}
		toolbox.startOpen(player);
	}

	private void depositAll() {
		Inventory playerInv = player.getInventory();
		toolbox.getInventory()
			.inLimitedMode(inventory -> {
				for (int i = 0; i < 36; i++) {
					ItemStack itemStack = playerInv.getItem(i);
					ItemStack remainder = ItemHandlerHelper.insertItemStacked(inventory, itemStack, false);
					if (remainder.getCount() != itemStack.getCount())
						playerInv.setItem(i, remainder);
				}
			});
	}

	@Override
	public boolean onAnyClick(int index, ClickType type, ContainerInput action) {
		int size = getVirtualSize();
		if (index >= 0 && index < size) {
			var slot = getSlotRedirectOrPlayer(index);
			ItemStack carried = player.containerMenu.getCarried();
			if (slot instanceof ToolboxSlot && action == ContainerInput.PICKUP && carried.isEmpty() && !slot.hasItem()) {
				int compartment = compartmentOf(index);
				if (compartment >= 0)
					toolbox.getInventory()
						.clearFilter(compartment);
			}
		}
		return true;
	}

	private static int compartmentOf(int guiIndex) {
		int row = guiIndex / 9;
		int column = guiIndex % 9;
		if (column == 4)
			return -1;
		return row * 2 + (column > 4 ? 1 : 0);
	}

	@Override
	public boolean stillValid() {
		return toolbox.canPlayerUse(player);
	}

	@Override
	public void onRemoved() {
		toolbox.stopOpen(player);
		toolbox.setChanged();
	}
}
