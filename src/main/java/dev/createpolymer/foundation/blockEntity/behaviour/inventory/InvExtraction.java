package dev.createpolymer.foundation.blockEntity.behaviour.inventory;

import java.util.function.Predicate;

import dev.createpolymer.foundation.item.handler.IItemHandler;
import net.minecraft.world.item.ItemStack;

/**
 * The pieces of Create's ItemHelper that the inventory manipulation and filtering code needs
 * ({@code ExtractionCountMode} and {@code extract}). Same logic as ItemHelper; to be merged into it.
 */
public final class InvExtraction {
	private InvExtraction() {}

	public static enum ExtractionCountMode {
		EXACTLY, UPTO
	}

	public static ItemStack extract(IItemHandler inv, Predicate<ItemStack> test, boolean simulate) {
		return extract(inv, test, ExtractionCountMode.UPTO, 64, simulate);
	}

	public static ItemStack extract(IItemHandler inv, Predicate<ItemStack> test, int exactAmount, boolean simulate) {
		return extract(inv, test, ExtractionCountMode.EXACTLY, exactAmount, simulate);
	}

	public static ItemStack extract(IItemHandler inv, Predicate<ItemStack> test, ExtractionCountMode mode, int amount,
		boolean simulate) {
		ItemStack extracting = ItemStack.EMPTY;
		boolean amountRequired = mode == ExtractionCountMode.EXACTLY;
		boolean checkHasEnoughItems = amountRequired;
		boolean hasEnoughItems = !checkHasEnoughItems;
		boolean potentialOtherMatch = false;
		int maxExtractionCount = amount;

		Extraction:
		do {
			extracting = ItemStack.EMPTY;

			for (int slot = 0; slot < inv.getSlots(); slot++) {
				ItemStack slotStack = inv.getStackInSlot(slot);
				if (slotStack.isEmpty())
					continue;
				int amountToExtractFromThisSlot =
					Math.min(maxExtractionCount - extracting.getCount(), slotStack.getMaxStackSize());
				ItemStack stack = inv.extractItem(slot, amountToExtractFromThisSlot, true);

				if (stack.isEmpty())
					continue;
				if (!test.test(stack))
					continue;
				if (!extracting.isEmpty() && !canItemStackAmountsStack(stack, extracting)) {
					potentialOtherMatch = true;
					continue;
				}

				if (extracting.isEmpty())
					extracting = stack.copy();
				else
					extracting.grow(stack.getCount());

				if (!simulate && hasEnoughItems)
					inv.extractItem(slot, stack.getCount(), false);

				if (extracting.getCount() >= maxExtractionCount) {
					if (checkHasEnoughItems) {
						hasEnoughItems = true;
						checkHasEnoughItems = false;
						continue Extraction;
					} else {
						break Extraction;
					}
				}
			}

			if (!extracting.isEmpty() && !hasEnoughItems && potentialOtherMatch) {
				ItemStack blackListed = extracting.copy();
				test = test.and(i -> !ItemStack.isSameItemSameComponents(i, blackListed));
				continue;
			}

			if (checkHasEnoughItems)
				checkHasEnoughItems = false;
			else
				break Extraction;

		} while (true);

		if (amountRequired && extracting.getCount() < amount)
			return ItemStack.EMPTY;

		return extracting;
	}

	public static boolean canItemStackAmountsStack(ItemStack a, ItemStack b) {
		return ItemStack.isSameItemSameComponents(a, b) && a.getCount() + b.getCount() <= a.getMaxStackSize();
	}
}
