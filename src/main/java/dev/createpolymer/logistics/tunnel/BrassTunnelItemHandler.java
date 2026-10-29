package dev.createpolymer.logistics.tunnel;

import dev.createpolymer.foundation.item.ItemHelper;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.ItemStack;

public class BrassTunnelItemHandler implements IItemHandler, Clearable {

	private BrassTunnelBlockEntity blockEntity;

	public BrassTunnelItemHandler(BrassTunnelBlockEntity be) {
		this.blockEntity = be;
	}

	@Override
	public void clearContent() {
		blockEntity.stackToDistribute = ItemStack.EMPTY;
	}

	@Override
	public int getSlots() {
		return 1;
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		return blockEntity.stackToDistribute;
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		if (!blockEntity.hasDistributionBehaviour()) {
			IItemHandler beltCapability = blockEntity.getBeltCapability();
			if (beltCapability == null)
				return stack;
			return beltCapability.insertItem(slot, stack, simulate);
		}

		if (!blockEntity.canTakeItems())
			return stack;

		ItemStack remainder = ItemHelper.limitCountToMaxStackSize(stack, simulate);
		if (!simulate)
			blockEntity.setStackToDistribute(stack, null);
		return remainder;
	}

	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		IItemHandler beltCapability = blockEntity.getBeltCapability();
		if (beltCapability == null)
			return ItemStack.EMPTY;
		return beltCapability.extractItem(slot, amount, simulate);
	}

	@Override
	public int getSlotLimit(int slot) {
		return blockEntity.stackToDistribute.isEmpty() ? 64 : blockEntity.stackToDistribute.getMaxStackSize();
	}

	@Override
	public boolean isItemValid(int slot, ItemStack stack) {
		return true;
	}

}
