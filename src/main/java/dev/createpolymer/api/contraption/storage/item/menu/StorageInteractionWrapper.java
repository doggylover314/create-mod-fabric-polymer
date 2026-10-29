package dev.createpolymer.api.contraption.storage.item.menu;

import java.util.function.Consumer;
import java.util.function.Predicate;

import dev.createpolymer.foundation.item.handler.IItemHandlerModifiable;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A vanilla {@link Container} view of an item handler (Create's ItemHandlerContainer), so vanilla menus can show it.
 */
public class StorageInteractionWrapper implements Container {
	private final IItemHandlerModifiable inv;
	private final Predicate<Player> stillValid;
	private final Consumer<Player> onClose;

	public StorageInteractionWrapper(IItemHandlerModifiable inv, Predicate<Player> stillValid, Consumer<Player> onClose) {
		this.inv = inv;
		this.stillValid = stillValid;
		this.onClose = onClose;
	}

	@Override
	public int getContainerSize() {
		return inv.getSlots();
	}

	@Override
	public boolean isEmpty() {
		for (int i = 0; i < inv.getSlots(); i++)
			if (!inv.getStackInSlot(i).isEmpty())
				return false;
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		return inv.getStackInSlot(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		return inv.extractItem(slot, count, false);
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		ItemStack stack = inv.getStackInSlot(slot);
		if (stack.isEmpty())
			return ItemStack.EMPTY;
		inv.setStackInSlot(slot, ItemStack.EMPTY);
		return stack;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		inv.setStackInSlot(slot, stack);
	}

	@Override
	public void setChanged() {
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return inv.isItemValid(slot, stack);
	}

	@Override
	public void clearContent() {
		for (int i = 0; i < inv.getSlots(); i++)
			inv.setStackInSlot(i, ItemStack.EMPTY);
	}

	@Override
	public boolean stillValid(Player player) {
		return this.stillValid.test(player);
	}

	@Override
	public int getMaxStackSize() {
		return 64;
	}

	@Override
	public void stopOpen(net.minecraft.world.entity.ContainerUser user) {
		if (user instanceof Player player)
			this.onClose.accept(player);
	}
}
