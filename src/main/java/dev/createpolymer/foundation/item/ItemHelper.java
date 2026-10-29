package dev.createpolymer.foundation.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.Nullable;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.IItemHandlerModifiable;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import dev.createpolymer.logistics.box.PackageEntity;
import dev.createpolymer.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ItemHelper {

	public static boolean sameItem(ItemStack stack, ItemStack otherStack) {
		return !otherStack.isEmpty() && stack.is(otherStack.getItem());
	}

	public static Predicate<ItemStack> sameItemPredicate(ItemStack stack) {
		return s -> sameItem(stack, s);
	}

	public static void dropContents(Level world, BlockPos pos, IItemHandler inv) {
		for (int slot = 0; slot < inv.getSlots(); slot++)
			Containers.dropItemStack(world, pos.getX(), pos.getY(), pos.getZ(), inv.getStackInSlot(slot));
	}

	public static List<ItemStack> multipliedOutput(ItemStack in, ItemStack out) {
		List<ItemStack> stacks = new ArrayList<>();
		ItemStack result = out.copy();
		result.setCount(in.getCount() * out.getCount());

		while (result.getCount() > result.getMaxStackSize()) {
			stacks.add(result.split(result.getMaxStackSize()));
		}

		stacks.add(result);
		return stacks;
	}

	public static void addToList(ItemStack stack, List<ItemStack> stacks) {
		for (ItemStack s : stacks) {
			if (!ItemStack.isSameItemSameComponents(stack, s))
				continue;
			int transferred = Math.min(s.getMaxStackSize() - s.getCount(), stack.getCount());
			s.grow(transferred);
			stack.shrink(transferred);
		}
		if (stack.getCount() > 0)
			stacks.add(stack);
	}

	public static boolean isSameInventory(IItemHandler h1, IItemHandler h2) {
		if (h1 == null || h2 == null)
			return false;
		if (h1.getSlots() != h2.getSlots())
			return false;
		for (int slot = 0; slot < h1.getSlots(); slot++) {
			if (h1.getStackInSlot(slot) != h2.getStackInSlot(slot))
				return false;
		}
		return true;
	}

	public static <T extends IBE<? extends BlockEntity>> int calcRedstoneFromBlockEntity(T ibe, Level level, BlockPos pos) {
		return ibe.getBlockEntityOptional(level, pos)
			.map(be -> ItemHandlerLookup.get(level, pos, null))
			.map(ItemHelper::calcRedstoneFromInventory)
			.orElse(0);
	}

	public static int calcRedstoneFromInventory(@Nullable IItemHandler inv) {
		if (inv == null)
			return 0;
		int i = 0;
		float f = 0.0F;
		int totalSlots = inv.getSlots();

		for (int j = 0; j < inv.getSlots(); ++j) {
			int slotLimit = inv.getSlotLimit(j);
			if (slotLimit == 0) {
				totalSlots--;
				continue;
			}
			ItemStack itemstack = inv.getStackInSlot(j);
			if (!itemstack.isEmpty()) {
				f += (float) itemstack.getCount() / (float) Math.min(slotLimit, itemstack.getMaxStackSize());
				++i;
			}
		}

		if (totalSlots == 0)
			return 0;

		f = f / totalSlots;
		return Mth.floor(f * 14.0F) + (i > 0 ? 1 : 0);
	}

	public static List<Pair<Ingredient, MutableInt>> condenseIngredients(List<Ingredient> recipeIngredients) {
		List<Pair<Ingredient, MutableInt>> actualIngredients = new ArrayList<>();
		Ingredients:
		for (Ingredient igd : recipeIngredients) {
			for (Pair<Ingredient, MutableInt> pair : actualIngredients) {
				if (!matchIngredients(pair.getFirst(), igd))
					continue;
				pair.getSecond()
					.increment();
				continue Ingredients;
			}
			actualIngredients.add(Pair.of(igd, new MutableInt(1)));
		}
		return actualIngredients;
	}

	/** 26.3 ingredients are item sets, so two ingredients match when they accept the same items. */
	public static boolean matchIngredients(Ingredient i1, Ingredient i2) {
		if (i1 == i2)
			return true;
		List<Holder<Item>> items1 = i1.items().toList();
		List<Holder<Item>> items2 = i2.items().toList();
		return items1.equals(items2);
	}

	public static boolean matchAllIngredients(List<Ingredient> ingredients) {
		if (ingredients.size() <= 1)
			return true;
		Ingredient firstIngredient = ingredients.get(0);
		for (int i = 1; i < ingredients.size(); i++)
			if (!matchIngredients(firstIngredient, ingredients.get(i)))
				return false;
		return true;
	}

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

	public static ItemStack extract(IItemHandler inv, Predicate<ItemStack> test,
									Function<ItemStack, Integer> amountFunction, boolean simulate) {
		ItemStack extracting = ItemStack.EMPTY;
		int maxExtractionCount = 64;

		for (int slot = 0; slot < inv.getSlots(); slot++) {
			if (extracting.isEmpty()) {
				ItemStack stackInSlot = inv.getStackInSlot(slot);
				if (stackInSlot.isEmpty() || !test.test(stackInSlot))
					continue;
				int maxExtractionCountForItem = amountFunction.apply(stackInSlot);
				if (maxExtractionCountForItem == 0)
					continue;
				maxExtractionCount = Math.min(maxExtractionCount, maxExtractionCountForItem);
			}

			ItemStack stack = inv.extractItem(slot, maxExtractionCount - extracting.getCount(), true);

			if (!test.test(stack))
				continue;
			if (!extracting.isEmpty() && !canItemStackAmountsStack(stack, extracting))
				continue;

			if (extracting.isEmpty())
				extracting = stack.copy();
			else
				extracting.grow(stack.getCount());

			if (!simulate)
				inv.extractItem(slot, stack.getCount(), false);
			if (extracting.getCount() >= maxExtractionCount)
				break;
		}

		return extracting;
	}

	public static boolean canItemStackAmountsStack(ItemStack a, ItemStack b) {
		return ItemStack.isSameItemSameComponents(a, b) && a.getCount() + b.getCount() <= a.getMaxStackSize();
	}

	public static ItemStack findFirstMatch(IItemHandler inv, Predicate<ItemStack> test) {
		int slot = findFirstMatchingSlotIndex(inv, test);
		if (slot == -1)
			return ItemStack.EMPTY;
		else
			return inv.getStackInSlot(slot);
	}

	public static int findFirstMatchingSlotIndex(IItemHandler inv, Predicate<ItemStack> test) {
		for (int slot = 0; slot < inv.getSlots(); slot++) {
			ItemStack toTest = inv.getStackInSlot(slot);
			if (test.test(toTest))
				return slot;
		}
		return -1;
	}

	public static ItemStack fromItemEntity(Entity entityIn) {
		if (!entityIn.isAlive())
			return ItemStack.EMPTY;
		if (entityIn instanceof PackageEntity packageEntity) {
			return packageEntity.getBox();
		}
		return entityIn instanceof ItemEntity itemEntity ? itemEntity.getItem() : ItemStack.EMPTY;
	}

	public static void fillItemStackHandler(ItemContainerContents contents, ItemStackHandler inv) {
		List<ItemStack> itemStacks = contents.itemCopies().toList();

		for (int i = 0; i < itemStacks.size(); i++) {
			inv.setStackInSlot(i, itemStacks.get(i));
		}
	}

	public static ItemContainerContents containerContentsFromHandler(ItemStackHandler handler) {
		List<ItemStack> stacks = new ArrayList<>(handler.getSlots());
		for (int i = 0; i < handler.getSlots(); i++)
			stacks.add(handler.getStackInSlot(i));
		return ItemContainerContents.fromItems(stacks);
	}

	public static ItemStack limitCountToMaxStackSize(ItemStack stack, boolean simulate) {
		int count = stack.getCount();
		int max = stack.getMaxStackSize();
		if (count <= max)
			return ItemStack.EMPTY;
		ItemStack remainder = stack.copyWithCount(count - max);
		if (!simulate)
			stack.setCount(max);
		return remainder;
	}

	public static void copyContents(IItemHandler from, IItemHandlerModifiable to) {
		if (from.getSlots() != to.getSlots()) {
			throw new IllegalArgumentException("Slot count mismatch");
		}

		for (int slot = to.getSlots() - 1; slot >= 0; slot--) {
			to.setStackInSlot(slot, ItemStack.EMPTY);
		}

		for (int i = 0; i < from.getSlots(); i++) {
			to.setStackInSlot(i, from.getStackInSlot(i).copy());
		}
	}

	public static List<ItemStack> getNonEmptyStacks(ItemStackHandler handler) {
		List<ItemStack> stacks = new ArrayList<>();
		for (int i = 0; i < handler.getSlots(); i++) {
			ItemStack stack = handler.getStackInSlot(i);
			if (!stack.isEmpty()) {
				stacks.add(stack);
			}
		}
		return stacks;
	}
}
