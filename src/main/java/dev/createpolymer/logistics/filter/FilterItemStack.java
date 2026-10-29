package dev.createpolymer.logistics.filter;

import java.util.ArrayList;
import java.util.List;

import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import dev.createpolymer.fluids.transfer.GenericItemEmptying;
import dev.createpolymer.logistics.box.PackageItem;
import dev.createpolymer.logistics.item.filter.attribute.ItemAttribute;
import dev.createpolymer.registry.feature.FilterRegistry;
import dev.createpolymer.registry.feature.ToolsRegistry;
import dev.createpolymer.util.Pair;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A filter slot's content, ready to test items against: a plain item, or one of the filter items with its
 * settings read out of the stack's data components. A filter can also be tested against fluids: a plain item
 * stands for the fluid it holds.
 */
public class FilterItemStack {
	private final ItemStack filterItemStack;
	private boolean fluidExtracted;
	private FluidStack filterFluidStack;

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

	public FluidStack fluid(Level level) {
		resolveFluid(level);
		return filterFluidStack;
	}

	public boolean isFilterItem() {
		return filterItemStack.getItem() instanceof FilterItem;
	}

	//

	public boolean test(Level world, ItemStack stack) {
		return test(world, stack, false);
	}

	public boolean test(Level world, FluidStack stack) {
		return test(world, stack, true);
	}

	public boolean test(Level world, ItemStack stack, boolean matchNBT) {
		if (isEmpty())
			return true;
		return FilterItem.testDirect(filterItemStack, stack, matchNBT);
	}

	public boolean test(Level world, FluidStack stack, boolean matchNBT) {
		if (isEmpty())
			return true;
		if (stack.isEmpty())
			return false;

		resolveFluid(world);

		if (filterFluidStack.isEmpty())
			return false;
		if (!matchNBT)
			return filterFluidStack.getFluid()
				.isSame(stack.getFluid());
		return FluidStack.isSameFluidSameComponents(filterFluidStack, stack);
	}

	//

	private void resolveFluid(Level world) {
		if (!fluidExtracted) {
			fluidExtracted = true;
			if (GenericItemEmptying.canItemBeEmptied(world, filterItemStack))
				filterFluidStack = GenericItemEmptying.emptyItem(world, filterItemStack, true)
					.getFirst();
		}
	}

	protected FilterItemStack(ItemStack filter) {
		filterItemStack = filter;
		filterFluidStack = FluidStack.EMPTY;
		fluidExtracted = false;
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

		@Override
		public boolean test(Level world, FluidStack stack, boolean matchNBT) {
			for (FilterItemStack filterItemStack : containedItems)
				if (filterItemStack.test(world, stack, shouldRespectNBT))
					return !isBlacklist;
			return isBlacklist;
		}

	}

	public static class AttributeFilterItemStack extends FilterItemStack {
		public AttributeFilterWhitelistMode whitelistMode;
		public List<Pair<ItemAttribute, Boolean>> attributeTests;

		public AttributeFilterItemStack(ItemStack filter) {
			super(filter);
			boolean defaults = !filter.has(ToolsRegistry.ATTRIBUTE_FILTER_MATCHED_ATTRIBUTES);

			attributeTests = new ArrayList<>();
			whitelistMode = filter.getOrDefault(ToolsRegistry.ATTRIBUTE_FILTER_WHITELIST_MODE, AttributeFilterWhitelistMode.WHITELIST_DISJ);

			List<ItemAttribute.ItemAttributeEntry> attributes = defaults ? new ArrayList<>()
				: filter.get(ToolsRegistry.ATTRIBUTE_FILTER_MATCHED_ATTRIBUTES);
			//noinspection DataFlowIssue
			for (ItemAttribute.ItemAttributeEntry attributeEntry : attributes) {
				ItemAttribute attribute = attributeEntry.attribute();
				if (attribute != null)
					attributeTests.add(Pair.of(attribute, attributeEntry.inverted()));
			}
		}

		@Override
		public boolean test(Level world, FluidStack stack, boolean matchNBT) {
			return false;
		}

		@Override
		public boolean test(Level world, ItemStack stack, boolean matchNBT) {
			for (Pair<ItemAttribute, Boolean> test : attributeTests) {
				ItemAttribute attribute = test.getFirst();
				boolean inverted = test.getSecond();
				boolean matches = attribute.appliesTo(stack, world) != inverted;

				if (matches) {
					switch (whitelistMode) {
						case BLACKLIST -> {
							return false;
						}
						case WHITELIST_CONJ -> {
							continue;
						}
						case WHITELIST_DISJ -> {
							return true;
						}
					}
				} else {
					switch (whitelistMode) {
						case BLACKLIST, WHITELIST_DISJ -> {
							continue;
						}
						case WHITELIST_CONJ -> {
							return false;
						}
					}
				}
			}

			return switch (whitelistMode) {
				case BLACKLIST, WHITELIST_CONJ -> true;
				case WHITELIST_DISJ -> false;
			};

		}

	}

	public static class PackageFilterItemStack extends FilterItemStack {

		public String filterString;

		public PackageFilterItemStack(ItemStack filter) {
			super(filter);
			filterString = PackageItem.getAddress(filter);
		}

		@Override
		public boolean test(Level world, ItemStack stack, boolean matchNBT) {
			return PackageItem.isPackage(stack) && PackageItem.matchAddress(stack, filterString);
		}

		@Override
		public boolean test(Level world, FluidStack stack, boolean matchNBT) {
			return false;
		}

	}
}
