package dev.createpolymer.logistics.filter;

import java.util.ArrayList;
import java.util.List;

import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import dev.createpolymer.registry.feature.FilterRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A filter slot's content, ready to test items against: a plain item, or one of the filter items with its
 * settings read out of the stack's data components. Fluid matching is not ported.
 */
public class FilterItemStack {
	private final ItemStack filterItemStack;

	public static FilterItemStack of(ItemStack filter) {
		if (!filter.getComponentsPatch().isEmpty() && filter.getItem() instanceof FilterItem item) {
			trimFilterComponents(filter);
			return item.makeStackWrapper(filter);
		}

		return new FilterItemStack(filter);
	}

	public static FilterItemStack empty() {
		return of(ItemStack.EMPTY);
	}

	private static void trimFilterComponents(ItemStack filter) {
		filter.remove(DataComponents.ENCHANTMENTS);
		filter.remove(DataComponents.ATTRIBUTE_MODIFIERS);
	}

	public boolean isEmpty() {
		return filterItemStack.isEmpty();
	}

	public ItemStack item() {
		return filterItemStack;
	}

	public boolean isFilterItem() {
		return filterItemStack.getItem() instanceof FilterItem;
	}

	//

	public boolean test(Level world, ItemStack stack) {
		return test(world, stack, false);
	}

	public boolean test(Level world, ItemStack stack, boolean matchNBT) {
		if (isEmpty())
			return true;
		return FilterItem.testDirect(filterItemStack, stack, matchNBT);
	}

	//

	protected FilterItemStack(ItemStack filter) {
		filterItemStack = filter;
	}

	public static class ListFilterItemStack extends FilterItemStack {

		public List<FilterItemStack> containedItems;
		public boolean shouldRespectNBT;
		public boolean isBlacklist;

		public ListFilterItemStack(ItemStack filter) {
			super(filter);
			boolean hasFilterItems = filter.has(FilterRegistry.FILTER_ITEMS);

			containedItems = new ArrayList<>();
			ItemStackHandler items = ((ListFilterItem) filter.getItem()).getFilterItemHandler(filter);
			for (int i = 0; i < items.getSlots(); i++) {
				ItemStack stackInSlot = items.getStackInSlot(i);
				if (!stackInSlot.isEmpty())
					containedItems.add(FilterItemStack.of(stackInSlot));
			}

			shouldRespectNBT = hasFilterItems && filter.getOrDefault(FilterRegistry.FILTER_ITEMS_RESPECT_NBT, false);
			isBlacklist = hasFilterItems && filter.getOrDefault(FilterRegistry.FILTER_ITEMS_BLACKLIST, false);
		}

		@Override
		public boolean test(Level world, ItemStack stack, boolean matchNBT) {
			for (FilterItemStack filterItemStack : containedItems)
				if (filterItemStack.test(world, stack, shouldRespectNBT))
					return !isBlacklist;
			return isBlacklist;
		}

	}
}
