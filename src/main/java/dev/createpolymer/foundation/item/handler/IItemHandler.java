package dev.createpolymer.foundation.item.handler;

import net.minecraft.world.item.ItemStack;

/**
 * A slotted item inventory with simulated insertion and extraction. Same contract as NeoForge's interface of the
 * same name, so Create's logistics code can use it unchanged. Bridged to Fabric's transfer API by
 * {@link ItemHandlerLookup}.
 */
public interface IItemHandler {
	int getSlots();

	/** The returned stack must not be modified by the caller. */
	ItemStack getStackInSlot(int slot);

	/** @return the remainder that could not be inserted (empty if everything fit). */
	ItemStack insertItem(int slot, ItemStack stack, boolean simulate);

	/** @return the extracted stack (empty if nothing could be extracted). */
	ItemStack extractItem(int slot, int amount, boolean simulate);

	int getSlotLimit(int slot);

	boolean isItemValid(int slot, ItemStack stack);
}
