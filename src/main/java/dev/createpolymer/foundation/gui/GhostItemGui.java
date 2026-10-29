package dev.createpolymer.foundation.gui;

import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Create's GhostItemMenu as an sgui chest: the first {@code ghostInventory.getSlots()} slots show a ghost item
 * each. Clicking one with an item on the cursor sets it to a single copy of that item, clicking with an empty cursor
 * clears it, shift-clicking an item in the player's inventory puts it into the first free ghost slot. Nothing is
 * ever taken from or given to the player.
 */
public abstract class GhostItemGui extends SimpleGui {

	protected final ItemStackHandler ghostInventory;

	protected GhostItemGui(MenuType<?> type, ServerPlayer player, Component title, ItemStackHandler ghostInventory) {
		super(type, player, false);
		this.ghostInventory = ghostInventory;
		setTitle(title);
		for (int i = 0; i < ghostInventory.getSlots(); i++)
			refreshGhostSlot(i);
	}

	protected abstract boolean allowRepeats();

	/** The GUI slot that shows a ghost slot. Ghost slots fill the first GUI slots, unless a GUI places them elsewhere. */
	protected int guiSlot(int ghostSlot) {
		return ghostSlot;
	}

	/** The ghost slot that a GUI slot shows, or -1 if it is not a ghost slot. */
	protected int ghostSlot(int guiSlot) {
		return guiSlot >= 0 && guiSlot < ghostInventory.getSlots() ? guiSlot : -1;
	}

	/** Called after the ghost items changed. */
	protected void onGhostChanged() {
	}

	public void clearContents() {
		for (int i = 0; i < ghostInventory.getSlots(); i++) {
			ghostInventory.setStackInSlot(i, ItemStack.EMPTY);
			refreshGhostSlot(i);
		}
		onGhostChanged();
	}

	protected void refreshGhostSlot(int slot) {
		ItemStack stack = ghostInventory.getStackInSlot(slot);
		if (stack.isEmpty())
			clearSlot(guiSlot(slot));
		else
			setSlot(guiSlot(slot), stack.copy());
	}

	/** Whether the player slot with this menu index must not be touched. */
	protected boolean isProtectedSlot(int menuIndex) {
		return false;
	}

	@Override
	public boolean onAnyClick(int index, ClickType type, ContainerInput action) {
		if (type == ClickType.MOUSE_DOUBLE_CLICK)
			return false;
		return !isProtectedSlot(index);
	}

	@Override
	public boolean onClick(int index, ClickType type, ContainerInput action, GuiElement element) {
		int ghost = ghostSlot(index);
		if (ghost < 0)
			return false;
		if (action == ContainerInput.THROW || type.isDragging || type.numKey || action == ContainerInput.PICKUP_ALL)
			return false;

		ItemStack held = player.containerMenu.getCarried();
		if (action == ContainerInput.CLONE) {
			if (player.isCreative() && held.isEmpty()) {
				ItemStack stackInSlot = ghostInventory.getStackInSlot(ghost)
					.copy();
				stackInSlot.setCount(stackInSlot.getMaxStackSize());
				player.containerMenu.setCarried(stackInSlot);
			}
			return false;
		}

		if (action == ContainerInput.QUICK_MOVE) {
			ghostInventory.setStackInSlot(ghost, ItemStack.EMPTY);
		} else {
			ItemStack insert;
			if (held.isEmpty()) {
				insert = ItemStack.EMPTY;
			} else {
				insert = held.copy();
				insert.setCount(1);
			}
			ghostInventory.setStackInSlot(ghost, insert);
		}
		refreshGhostSlot(ghost);
		onGhostChanged();
		return false;
	}

	@Override
	public ItemStack quickMove(int index) {
		if (index < getSize() || isProtectedSlot(index))
			return ItemStack.EMPTY;
		Slot slot = getSlotRedirectOrPlayer(index);
		if (slot == null || !slot.hasItem())
			return ItemStack.EMPTY;

		ItemStack stackToInsert = slot.getItem();
		for (int i = 0; i < ghostInventory.getSlots(); i++) {
			ItemStack stack = ghostInventory.getStackInSlot(i);
			if (!allowRepeats() && ItemStack.isSameItemSameComponents(stack, stackToInsert))
				break;
			if (stack.isEmpty()) {
				ItemStack copy = stackToInsert.copy();
				copy.setCount(1);
				ghostInventory.setStackInSlot(i, copy);
				refreshGhostSlot(i);
				onGhostChanged();
				break;
			}
		}
		return ItemStack.EMPTY;
	}
}
