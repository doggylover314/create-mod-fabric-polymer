package dev.createpolymer.foundation.item.handler;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;

public class ItemHandlerHelper {
	public static ItemStack insertItem(IItemHandler dest, ItemStack stack, boolean simulate) {
		if (dest == null || stack.isEmpty())
			return stack;
		for (int i = 0; i < dest.getSlots(); i++) {
			stack = dest.insertItem(i, stack, simulate);
			if (stack.isEmpty())
				return ItemStack.EMPTY;
		}
		return stack;
	}

	public static boolean canItemStacksStack(ItemStack a, ItemStack b) {
		if (a.isEmpty() || b.isEmpty() || !a.isStackable())
			return false;
		return ItemStack.isSameItemSameComponents(a, b);
	}

	/** Like {@link #canItemStacksStack} but also allows unstackable items. */
	public static boolean canItemStacksStackRelaxed(ItemStack a, ItemStack b) {
		if (a.isEmpty() || b.isEmpty())
			return false;
		return ItemStack.isSameItemSameComponents(a, b);
	}

	public static ItemStack copyStackWithSize(ItemStack stack, int size) {
		if (size == 0)
			return ItemStack.EMPTY;
		return stack.copyWithCount(size);
	}

	/** Fills existing matching stacks first, then empty slots. */
	public static ItemStack insertItemStacked(IItemHandler inventory, ItemStack stack, boolean simulate) {
		if (inventory == null || stack.isEmpty())
			return stack;
		if (!stack.isStackable())
			return insertItem(inventory, stack, simulate);

		int sizeInventory = inventory.getSlots();
		for (int i = 0; i < sizeInventory; i++) {
			ItemStack slot = inventory.getStackInSlot(i);
			if (canItemStacksStackRelaxed(slot, stack)) {
				stack = inventory.insertItem(i, stack, simulate);
				if (stack.isEmpty())
					break;
			}
		}
		if (!stack.isEmpty()) {
			for (int i = 0; i < sizeInventory; i++) {
				if (inventory.getStackInSlot(i).isEmpty()) {
					stack = inventory.insertItem(i, stack, simulate);
					if (stack.isEmpty())
						break;
				}
			}
		}
		return stack;
	}

	public static void giveItemToPlayer(Player player, ItemStack stack) {
		if (stack.isEmpty())
			return;
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
	}

	public static int calcRedstoneFromInventory(IItemHandler inv) {
		if (inv == null)
			return 0;
		int itemsFound = 0;
		float proportion = 0.0F;
		for (int j = 0; j < inv.getSlots(); ++j) {
			ItemStack itemstack = inv.getStackInSlot(j);
			if (!itemstack.isEmpty()) {
				proportion += (float) itemstack.getCount()
					/ (float) Math.min(inv.getSlotLimit(j), itemstack.getMaxStackSize());
				++itemsFound;
			}
		}
		proportion = proportion / (float) inv.getSlots();
		return Mth.floor(proportion * 14.0F) + (itemsFound > 0 ? 1 : 0);
	}
}
