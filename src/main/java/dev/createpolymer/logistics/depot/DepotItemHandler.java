package dev.createpolymer.logistics.depot;

import dev.createpolymer.foundation.item.handler.IItemHandlerModifiable;
import dev.createpolymer.kinetics.belt.transport.TransportedItemStack;
import net.minecraft.world.item.ItemStack;

/**
 * Modifiable so that the Fabric storage bridge applies changes immediately and rolls them back by itself: hoppers
 * check an extraction first and repeat it, which the bridge cannot replay for handlers that are not modifiable.
 */
public class DepotItemHandler implements IItemHandlerModifiable {

	private static final int MAIN_SLOT = 0;
	private DepotBehaviour behaviour;

	public DepotItemHandler(DepotBehaviour behaviour) {
		this.behaviour = behaviour;
	}

	@Override
	public int getSlots() {
		return 9;
	}

	@Override
	public void setStackInSlot(int slot, ItemStack stack) {
		if (slot != MAIN_SLOT) {
			behaviour.processingOutputBuffer.setStackInSlot(slot - 1, stack);
			return;
		}
		if (stack.isEmpty()) {
			behaviour.heldItem = null;
		} else if (behaviour.heldItem != null) {
			behaviour.heldItem.stack = stack;
		} else {
			TransportedItemStack held = new TransportedItemStack(stack);
			held.beltPosition = .5f;
			held.prevBeltPosition = .5f;
			behaviour.heldItem = held;
		}
		behaviour.blockEntity.notifyUpdate();
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		return slot == MAIN_SLOT ? behaviour.getHeldItemStack() : behaviour.processingOutputBuffer.getStackInSlot(slot - 1);
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		if (slot != MAIN_SLOT)
			return stack;
		if (!behaviour.getHeldItemStack()
			.isEmpty() && !behaviour.canMergeItems())
			return stack;
		if (!behaviour.isOutputEmpty() && !behaviour.canMergeItems())
			return stack;

		ItemStack remainder = behaviour.insert(new TransportedItemStack(stack), simulate);
		if (!simulate && remainder != stack)
			behaviour.blockEntity.notifyUpdate();
		return remainder;
	}

	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		if (slot != MAIN_SLOT)
			return behaviour.processingOutputBuffer.extractItem(slot - 1, amount, simulate);

		TransportedItemStack held = behaviour.heldItem;
		if (held == null)
			return ItemStack.EMPTY;
		ItemStack stack = held.stack.copy();
		ItemStack extracted = stack.split(amount);
		if (!simulate) {
			behaviour.heldItem.stack = stack;
			if (stack.isEmpty())
				behaviour.heldItem = null;
			behaviour.blockEntity.notifyUpdate();
		}
		return extracted;
	}

	@Override
	public int getSlotLimit(int slot) {
		return slot == MAIN_SLOT ? behaviour.maxStackSize.get() : 64;
	}

	@Override
	public boolean isItemValid(int slot, ItemStack stack) {
		return slot == MAIN_SLOT && behaviour.isItemValid(stack);
	}

}
