package dev.createpolymer.util;

import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A fixed list of item slots with simulate-able insert/extract, like NeoForge's ItemStackHandler. */
public class ItemSlots {
	private final NonNullList<ItemStack> stacks;
	private final int maxStackSize;
	private final Runnable onChanged;

	public ItemSlots(int size, int maxStackSize, Runnable onChanged) {
		this.stacks = NonNullList.withSize(size, ItemStack.EMPTY);
		this.maxStackSize = maxStackSize;
		this.onChanged = onChanged;
	}

	public int size() {
		return stacks.size();
	}

	public ItemStack get(int slot) {
		return stacks.get(slot);
	}

	public void set(int slot, ItemStack stack) {
		stacks.set(slot, stack);
		onChanged.run();
	}

	public NonNullList<ItemStack> stacks() {
		return stacks;
	}

	public int getSlotLimit(ItemStack stack) {
		return Math.min(maxStackSize, stack.getMaxStackSize());
	}

	public boolean isEmpty() {
		for (ItemStack s : stacks)
			if (!s.isEmpty())
				return false;
		return true;
	}

	/** Inserts into one slot. Returns what did not fit. */
	public ItemStack insert(int slot, ItemStack stack, boolean simulate) {
		if (stack.isEmpty())
			return ItemStack.EMPTY;
		ItemStack existing = stacks.get(slot);
		int limit = getSlotLimit(stack);
		if (!existing.isEmpty()) {
			if (!ItemStack.isSameItemSameComponents(stack, existing))
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
			onChanged.run();
		}
		return reachedLimit ? stack.copyWithCount(stack.getCount() - limit) : ItemStack.EMPTY;
	}

	/** Inserts into any slot, filling matching stacks first. Returns what did not fit. */
	public ItemStack insertStacked(ItemStack stack, boolean simulate) {
		if (stack.isEmpty())
			return ItemStack.EMPTY;
		if (simulate) {
			ItemSlots copy = copy();
			return copy.insertStacked(stack, false);
		}
		ItemStack remaining = stack.copy();
		for (int i = 0; i < size() && !remaining.isEmpty(); i++)
			if (!stacks.get(i).isEmpty())
				remaining = insert(i, remaining, false);
		for (int i = 0; i < size() && !remaining.isEmpty(); i++)
			if (stacks.get(i).isEmpty())
				remaining = insert(i, remaining, false);
		return remaining;
	}

	public ItemStack extract(int slot, int amount, boolean simulate) {
		ItemStack existing = stacks.get(slot);
		if (existing.isEmpty() || amount <= 0)
			return ItemStack.EMPTY;
		int toExtract = Math.min(amount, existing.getCount());
		ItemStack result = existing.copyWithCount(toExtract);
		if (!simulate) {
			if (toExtract == existing.getCount())
				stacks.set(slot, ItemStack.EMPTY);
			else
				existing.shrink(toExtract);
			onChanged.run();
		}
		return result;
	}

	public ItemSlots copy() {
		ItemSlots copy = new ItemSlots(size(), maxStackSize, () -> {});
		for (int i = 0; i < size(); i++)
			copy.stacks.set(i, stacks.get(i).copy());
		return copy;
	}

	public void clear() {
		for (int i = 0; i < size(); i++)
			stacks.set(i, ItemStack.EMPTY);
	}

	public void save(ValueOutput output) {
		ContainerHelper.saveAllItems(output, stacks);
	}

	public void load(ValueInput input) {
		clear();
		ContainerHelper.loadAllItems(input, stacks);
	}
}
