package dev.createpolymer.foundation.item.handler;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A plain array-backed item handler. Override {@link #onContentsChanged(int)} to react to changes. */
public class ItemStackHandler implements IItemHandlerModifiable {
	protected NonNullList<ItemStack> stacks;

	public ItemStackHandler() {
		this(1);
	}

	public ItemStackHandler(int size) {
		stacks = NonNullList.withSize(size, ItemStack.EMPTY);
	}

	public ItemStackHandler(NonNullList<ItemStack> stacks) {
		this.stacks = stacks;
	}

	public void setSize(int size) {
		stacks = NonNullList.withSize(size, ItemStack.EMPTY);
	}

	@Override
	public void setStackInSlot(int slot, ItemStack stack) {
		validateSlotIndex(slot);
		stacks.set(slot, stack);
		onContentsChanged(slot);
	}

	@Override
	public int getSlots() {
		return stacks.size();
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		validateSlotIndex(slot);
		return stacks.get(slot);
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		if (stack.isEmpty())
			return ItemStack.EMPTY;
		if (!isItemValid(slot, stack))
			return stack;
		validateSlotIndex(slot);

		ItemStack existing = stacks.get(slot);
		int limit = getStackLimit(slot, stack);
		if (!existing.isEmpty()) {
			if (!ItemHandlerHelper.canItemStacksStack(stack, existing))
				return stack;
			limit -= existing.getCount();
		}
		if (limit <= 0)
			return stack;

		boolean reachedLimit = stack.getCount() > limit;
		if (!simulate) {
			if (existing.isEmpty())
				stacks.set(slot, reachedLimit ? stack.copyWithCount(limit) : stack.copy());
			else
				existing.grow(reachedLimit ? limit : stack.getCount());
			onContentsChanged(slot);
		}
		return reachedLimit ? stack.copyWithCount(stack.getCount() - limit) : ItemStack.EMPTY;
	}

	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		if (amount <= 0)
			return ItemStack.EMPTY;
		validateSlotIndex(slot);

		ItemStack existing = stacks.get(slot);
		if (existing.isEmpty())
			return ItemStack.EMPTY;

		int toExtract = Math.min(amount, existing.getMaxStackSize());
		if (existing.getCount() <= toExtract) {
			if (!simulate) {
				stacks.set(slot, ItemStack.EMPTY);
				onContentsChanged(slot);
				return existing;
			}
			return existing.copy();
		}
		if (!simulate) {
			stacks.set(slot, existing.copyWithCount(existing.getCount() - toExtract));
			onContentsChanged(slot);
		}
		return existing.copyWithCount(toExtract);
	}

	@Override
	public int getSlotLimit(int slot) {
		return 64;
	}

	protected int getStackLimit(int slot, ItemStack stack) {
		return Math.min(getSlotLimit(slot), stack.getMaxStackSize());
	}

	@Override
	public boolean isItemValid(int slot, ItemStack stack) {
		return true;
	}

	/** Writes the slots as a list of {Slot, Item} entries, skipping empty ones. */
	public void serialize(ValueOutput output) {
		output.putInt("Size", stacks.size());
		ValueOutput.ValueOutputList list = output.childrenList("Items");
		for (int i = 0; i < stacks.size(); i++) {
			if (stacks.get(i).isEmpty())
				continue;
			ValueOutput entry = list.addChild();
			entry.putInt("Slot", i);
			entry.store("Item", ItemStack.CODEC, stacks.get(i));
		}
	}

	public void deserialize(ValueInput input) {
		setSize(input.getIntOr("Size", stacks.size()));
		for (ValueInput entry : input.childrenListOrEmpty("Items")) {
			int slot = entry.getIntOr("Slot", -1);
			if (slot >= 0 && slot < stacks.size())
				stacks.set(slot, entry.read("Item", ItemStack.CODEC).orElse(ItemStack.EMPTY));
		}
		onLoad();
	}

	protected void validateSlotIndex(int slot) {
		if (slot < 0 || slot >= stacks.size())
			throw new RuntimeException("Slot " + slot + " not in valid range - [0," + stacks.size() + ")");
	}

	protected void onLoad() {
	}

	protected void onContentsChanged(int slot) {
	}
}
