package dev.createpolymer.logistics.packagePort;

import dev.createpolymer.foundation.item.handler.IItemHandlerModifiable;
import dev.createpolymer.logistics.box.PackageItem;
import net.minecraft.world.item.ItemStack;

/** What hoppers and other automation see of a package port: only packages that do not belong to the port. */
public class PackagePortAutomationInventoryWrapper implements IItemHandlerModifiable {
	private final IItemHandlerModifiable wrapped;
	private final PackagePortBlockEntity ppbe;

	public PackagePortAutomationInventoryWrapper(IItemHandlerModifiable wrapped, PackagePortBlockEntity ppbe) {
		this.wrapped = wrapped;
		this.ppbe = ppbe;
	}

	@Override
	public int getSlots() {
		return wrapped.getSlots();
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		return wrapped.getStackInSlot(slot);
	}

	@Override
	public void setStackInSlot(int slot, ItemStack stack) {
		wrapped.setStackInSlot(slot, stack);
	}

	@Override
	public int getSlotLimit(int slot) {
		return wrapped.getSlotLimit(slot);
	}

	@Override
	public boolean isItemValid(int slot, ItemStack stack) {
		return wrapped.isItemValid(slot, stack);
	}

	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		ItemStack preview = wrapped.extractItem(slot, 64, true);

		if (!PackageItem.isPackage(preview))
			return ItemStack.EMPTY;

		String filterString = ppbe.getFilterString();
		if (filterString == null || !PackageItem.matchAddress(preview, filterString))
			return ItemStack.EMPTY;

		return simulate ? preview : wrapped.extractItem(slot, amount, false);
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		if (!PackageItem.isPackage(stack))
			return stack;
		String filterString = ppbe.getFilterString();
		if (filterString != null && PackageItem.matchAddress(stack, filterString))
			return stack;
		return wrapped.insertItem(slot, stack, simulate);
	}
}
