package dev.createpolymer.logistics.filter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.createpolymer.foundation.gui.FilterGui;
import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import dev.createpolymer.logistics.filter.FilterItemStack.ListFilterItemStack;
import dev.createpolymer.registry.feature.FilterRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

public class ListFilterItem extends FilterItem {
	public static final int SLOTS = 18;

	public ListFilterItem(Properties properties) {
		super(properties);
	}

	@Override
	public List<Component> makeSummary(ItemStack filter) {
		List<Component> list = new ArrayList<>();

		ItemStackHandler filterItems = getFilterItemHandler(filter);
		boolean blacklist = filter.getOrDefault(FilterRegistry.FILTER_ITEMS_BLACKLIST, false);

		list.add((blacklist ? Component.translatable("create.gui.filter.deny_list")
			: Component.translatable("create.gui.filter.allow_list")).withStyle(ChatFormatting.GOLD));
		int count = 0;
		for (int i = 0; i < filterItems.getSlots(); i++) {
			if (count > 3) {
				list.add(Component.literal("- ...")
					.withStyle(ChatFormatting.DARK_GRAY));
				break;
			}

			ItemStack filterStack = filterItems.getStackInSlot(i);
			if (filterStack.isEmpty())
				continue;
			list.add(Component.literal("- ")
				.append(filterStack.getHoverName())
				.withStyle(ChatFormatting.GRAY));
			count++;
		}

		if (count == 0)
			return Collections.emptyList();

		return list;
	}

	@Override
	protected void openGui(ServerPlayer player, ItemStack filter) {
		new FilterGui(player, filter).open();
	}

	@Override
	public DataComponentType<?> getComponentType() {
		return FilterRegistry.FILTER_ITEMS;
	}

	@Override
	public FilterItemStack makeStackWrapper(ItemStack filter) {
		return new ListFilterItemStack(filter);
	}

	public ItemStackHandler getFilterItemHandler(ItemStack stack) {
		ItemStackHandler newInv = new ItemStackHandler(SLOTS);
		ItemContainerContents contents = stack.getOrDefault(FilterRegistry.FILTER_ITEMS, ItemContainerContents.EMPTY);
		List<ItemStack> itemStacks = contents.itemCopies().toList();
		for (int i = 0; i < itemStacks.size() && i < SLOTS; i++)
			newInv.setStackInSlot(i, itemStacks.get(i));
		return newInv;
	}

	@Override
	public ItemStack[] getFilterItems(ItemStack stack) {
		if (stack.getOrDefault(FilterRegistry.FILTER_ITEMS_BLACKLIST, false))
			return new ItemStack[0];
		ItemStackHandler handler = getFilterItemHandler(stack);
		List<ItemStack> stacks = new ArrayList<>();
		for (int i = 0; i < handler.getSlots(); i++)
			if (!handler.getStackInSlot(i).isEmpty())
				stacks.add(handler.getStackInSlot(i));
		return stacks.toArray(ItemStack[]::new);
	}
}
