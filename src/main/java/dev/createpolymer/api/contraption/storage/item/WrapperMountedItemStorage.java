package dev.createpolymer.api.contraption.storage.item;

import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.IItemHandlerModifiable;
import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import net.minecraft.world.item.ItemStack;

/**
 * Partial implementation of a MountedItemStorage that wraps an item handler.
 */
public abstract class WrapperMountedItemStorage<T extends IItemHandlerModifiable> extends MountedItemStorage {
	protected final T wrapped;

	protected WrapperMountedItemStorage(MountedItemStorageType<?> type, T wrapped) {
		super(type);
		this.wrapped = wrapped;
	}

	@Override
	public void setStackInSlot(int slot, ItemStack stack) {
		this.wrapped.setStackInSlot(slot, stack);
	}

	@Override
	public int getSlots() {
		return this.wrapped.getSlots();
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		return this.wrapped.getStackInSlot(slot);
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		return this.wrapped.insertItem(slot, stack, simulate);
	}

	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		return this.wrapped.extractItem(slot, amount, simulate);
	}

	@Override
	public int getSlotLimit(int slot) {
		return this.wrapped.getSlotLimit(slot);
	}

	@Override
	public boolean isItemValid(int slot, ItemStack stack) {
		return this.wrapped.isItemValid(slot, stack);
	}

	public static ItemStackHandler copyToItemStackHandler(IItemHandler handler) {
		ItemStackHandler copy = new ItemStackHandler(handler.getSlots());
		for (int i = 0; i < handler.getSlots(); i++) {
			copy.setStackInSlot(i, handler.getStackInSlot(i).copy());
		}
		return copy;
	}
}
