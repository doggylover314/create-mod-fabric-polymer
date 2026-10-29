package dev.createpolymer.foundation.gui;

import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import eu.pb4.sgui.api.ClickType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * A {@link GhostItemGui} linked to the item in a player's main hand: that item cannot be moved while the GUI is
 * open, the GUI closes when it leaves the hand, and every change is written straight into it.
 */
public abstract class HeldItemGhostItemGui extends GhostItemGui {

	protected final ItemStack contentHolder;

	protected HeldItemGhostItemGui(MenuType<?> type, ServerPlayer player, Component title, ItemStack contentHolder,
		ItemStackHandler ghostInventory) {
		super(type, player, title, ghostInventory);
		this.contentHolder = contentHolder;
	}

	protected abstract void saveData(ItemStack contentHolder);

	@Override
	protected void onGhostChanged() {
		saveData(contentHolder);
	}

	@Override
	public boolean stillValid() {
		return player.getInventory()
			.getSelectedItem() == contentHolder;
	}

	@Override
	protected boolean isProtectedSlot(int menuIndex) {
		// The menu lists the 27 inventory slots after the container slots, then the hotbar
		return menuIndex == getSize() + 27 + player.getInventory()
			.getSelectedSlot();
	}

	@Override
	public boolean onAnyClick(int index, ClickType type, ContainerInput action) {
		if (type.numKey && index >= getSize() && type.value - 1 == player.getInventory()
			.getSelectedSlot())
			return false;
		return super.onAnyClick(index, type, action);
	}
}
