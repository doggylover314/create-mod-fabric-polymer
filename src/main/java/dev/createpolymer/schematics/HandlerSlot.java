package dev.createpolymer.schematics;

import java.util.function.Predicate;

import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A menu slot that reads and writes one slot of an {@link ItemStackHandler} directly, like NeoForge's
 * SlotItemHandler. {@code mayPlace} decides on top of the handler's own validity check what the player may put in.
 */
public class HandlerSlot extends Slot {

	private final ItemStackHandler handler;
	private final int handlerIndex;
	private final Predicate<ItemStack> mayPlace;
	private final Runnable changed;

	public HandlerSlot(ItemStackHandler handler, int index, Predicate<ItemStack> mayPlace, Runnable changed) {
		super(new SimpleContainer(1), 0, 0, 0);
		this.handler = handler;
		this.handlerIndex = index;
		this.mayPlace = mayPlace;
		this.changed = changed;
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		return !stack.isEmpty() && mayPlace.test(stack);
	}

	@Override
	public ItemStack getItem() {
		return handler.getStackInSlot(handlerIndex);
	}

	@Override
	public boolean hasItem() {
		return !getItem().isEmpty();
	}

	@Override
	public void set(ItemStack stack) {
		handler.setStackInSlot(handlerIndex, stack);
		changed.run();
	}

	@Override
	public void setByPlayer(ItemStack stack) {
		set(stack);
	}

	@Override
	public void setChanged() {
		changed.run();
	}

	@Override
	public int getMaxStackSize() {
		return handler.getSlotLimit(handlerIndex);
	}

	@Override
	public int getMaxStackSize(ItemStack stack) {
		ItemStack maxAdd = stack.copy();
		int maxInput = stack.getMaxStackSize();
		maxAdd.setCount(maxInput);

		ItemStack currentStack = handler.getStackInSlot(handlerIndex);
		ItemStack remainder = handler.insertItem(handlerIndex, maxAdd, true);
		int current = currentStack.getCount();
		int added = maxInput - remainder.getCount();
		return current + added;
	}

	@Override
	public ItemStack remove(int amount) {
		return handler.extractItem(handlerIndex, amount, false);
	}
}
