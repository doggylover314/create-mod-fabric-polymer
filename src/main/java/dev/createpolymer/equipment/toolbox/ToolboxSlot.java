package dev.createpolymer.equipment.toolbox;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Ported from Create's ToolboxSlot (MIT, The Create Team): a menu slot that reads and writes one slot of the toolbox
 * inventory directly, like NeoForge's SlotItemHandler.
 */
public class ToolboxSlot extends Slot {

	private final ToolboxInventory handler;
	private final int handlerIndex;

	public ToolboxSlot(ToolboxInventory itemHandler, int index, int xPosition, int yPosition) {
		super(new SimpleContainer(1), 0, xPosition, yPosition);
		this.handler = itemHandler;
		this.handlerIndex = index;
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		return !stack.isEmpty() && handler.isItemValid(handlerIndex, stack);
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
	}

	@Override
	public void setChanged() {
		handler.onContentsChanged(handlerIndex);
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
