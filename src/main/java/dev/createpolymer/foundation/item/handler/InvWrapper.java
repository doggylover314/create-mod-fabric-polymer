package dev.createpolymer.foundation.item.handler;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Exposes a vanilla {@link Container} as an item handler. */
public class InvWrapper implements IItemHandlerModifiable {
	private final Container inv;

	public InvWrapper(Container inv) {
		this.inv = inv;
	}

	public Container getInv() {
		return inv;
	}

	@Override
	public int getSlots() {
		return inv.getContainerSize();
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		return inv.getItem(slot);
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		if (stack.isEmpty())
			return ItemStack.EMPTY;
		if (!inv.canPlaceItem(slot, stack))
			return stack;

		ItemStack existing = inv.getItem(slot);
		int limit = Math.min(getSlotLimit(slot), stack.getMaxStackSize());
		if (!existing.isEmpty()) {
			if (!ItemHandlerHelper.canItemStacksStack(stack, existing))
				return stack;
			limit -= existing.getCount();
		}
		if (limit <= 0)
			return stack;

		int moved = Math.min(limit, stack.getCount());
		if (!simulate) {
			if (existing.isEmpty())
				inv.setItem(slot, stack.copyWithCount(moved));
			else {
				ItemStack grown = existing.copy();
				grown.grow(moved);
				inv.setItem(slot, grown);
			}
			inv.setChanged();
		}
		return moved == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - moved);
	}

	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		if (amount <= 0)
			return ItemStack.EMPTY;
		ItemStack existing = inv.getItem(slot);
		if (existing.isEmpty())
			return ItemStack.EMPTY;
		int moved = Math.min(amount, existing.getCount());
		ItemStack result = existing.copyWithCount(moved);
		if (!simulate) {
			inv.setItem(slot, moved == existing.getCount() ? ItemStack.EMPTY
				: existing.copyWithCount(existing.getCount() - moved));
			inv.setChanged();
		}
		return result;
	}

	@Override
	public void setStackInSlot(int slot, ItemStack stack) {
		inv.setItem(slot, stack);
	}

	@Override
	public int getSlotLimit(int slot) {
		return inv.getMaxStackSize();
	}

	@Override
	public boolean isItemValid(int slot, ItemStack stack) {
		return inv.canPlaceItem(slot, stack);
	}
}
