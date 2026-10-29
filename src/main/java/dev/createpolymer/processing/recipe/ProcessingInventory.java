package dev.createpolymer.processing.recipe;

import java.util.function.Consumer;

import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Ported from Create's ProcessingInventory (MIT, The Create Team). */
public class ProcessingInventory extends ItemStackHandler {
	public float remainingTime;
	public float recipeDuration;
	public boolean appliedRecipe;
	public Consumer<ItemStack> callback;
	private boolean limit;

	public ProcessingInventory(Consumer<ItemStack> callback) {
		super(32);
		this.callback = callback;
	}

	public ProcessingInventory withSlotLimit(boolean limit) {
		this.limit = limit;
		return this;
	}

	@Override
	public int getSlotLimit(int slot) {
		return !limit ? super.getSlotLimit(slot) : 1;
	}

	public void clear() {
		for (int i = 0; i < getSlots(); i++)
			setStackInSlot(i, ItemStack.EMPTY);
		remainingTime = 0;
		recipeDuration = 0;
		appliedRecipe = false;
	}

	public boolean isEmpty() {
		for (int i = 0; i < getSlots(); i++)
			if (!getStackInSlot(i).isEmpty())
				return false;
		return true;
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		ItemStack insertItem = super.insertItem(slot, stack, simulate);
		if (slot == 0 && !(insertItem.getCount() == stack.getCount() && ItemStack.isSameItem(insertItem, stack)))
			callback.accept(getStackInSlot(slot));
		return insertItem;
	}

	@Override
	public void serialize(ValueOutput output) {
		super.serialize(output);
		output.putFloat("ProcessingTime", remainingTime);
		output.putFloat("RecipeTime", recipeDuration);
		output.putBoolean("AppliedRecipe", appliedRecipe);
	}

	@Override
	public void deserialize(ValueInput input) {
		remainingTime = input.getFloatOr("ProcessingTime", 0);
		recipeDuration = input.getFloatOr("RecipeTime", 0);
		appliedRecipe = input.getBooleanOr("AppliedRecipe", false);
		super.deserialize(input);
		if (isEmpty())
			appliedRecipe = false;
	}

	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean isItemValid(int slot, ItemStack stack) {
		return slot == 0 && isEmpty();
	}

}
