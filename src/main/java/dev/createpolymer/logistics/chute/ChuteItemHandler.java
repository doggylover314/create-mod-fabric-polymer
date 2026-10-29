package dev.createpolymer.logistics.chute;

import dev.createpolymer.foundation.item.ItemHelper;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

public class ChuteItemHandler implements IItemHandler {

	private ChuteBlockEntity blockEntity;

	public ChuteItemHandler(ChuteBlockEntity be) {
		this.blockEntity = be;
	}

	@Override
	public int getSlots() {
		return 1;
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		return blockEntity.item;
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		if (!blockEntity.canAcceptItem(stack))
			return stack;
		ItemStack remainder = ItemHelper.limitCountToMaxStackSize(stack, simulate);
		if (!simulate)
			blockEntity.setItem(stack);
		return remainder;
	}

	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		ItemStack remainder = blockEntity.item.copy();
		ItemStack split = remainder.split(amount);
		if (!simulate)
			blockEntity.setItem(remainder);
		return split;
	}

	@Override
	public int getSlotLimit(int slot) {
		return getStackInSlot(slot).getOrDefault(DataComponents.MAX_STACK_SIZE, 64);
	}

	@Override
	public boolean isItemValid(int slot, ItemStack stack) {
		return true;
	}

}
