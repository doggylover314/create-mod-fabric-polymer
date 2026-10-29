package dev.createpolymer.logistics.filter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.createpolymer.foundation.gui.AttributeFilterGui;
import dev.createpolymer.logistics.filter.FilterItemStack.AttributeFilterItemStack;
import dev.createpolymer.logistics.item.filter.attribute.ItemAttribute;
import dev.createpolymer.logistics.item.filter.attribute.ItemAttribute.ItemAttributeEntry;
import dev.createpolymer.logistics.item.filter.attribute.attributes.InTagAttribute;
import dev.createpolymer.registry.feature.ToolsRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class AttributeFilterItem extends FilterItem {
	protected AttributeFilterItem(Properties properties) {
		super(properties);
	}

	@Override
	public List<Component> makeSummary(ItemStack filter) {
		List<Component> list = new ArrayList<>();

		AttributeFilterWhitelistMode whitelistMode = filter.get(ToolsRegistry.ATTRIBUTE_FILTER_WHITELIST_MODE);
		list.add((whitelistMode == AttributeFilterWhitelistMode.WHITELIST_CONJ
			? Component.translatable("create.gui.attribute_filter.allow_list_conjunctive")
			: whitelistMode == AttributeFilterWhitelistMode.WHITELIST_DISJ
			? Component.translatable("create.gui.attribute_filter.allow_list_disjunctive")
			: Component.translatable("create.gui.attribute_filter.deny_list")).withStyle(ChatFormatting.GOLD));

		int count = 0;
		List<ItemAttribute.ItemAttributeEntry> attributes = filter.getOrDefault(ToolsRegistry.ATTRIBUTE_FILTER_MATCHED_ATTRIBUTES, List.of());
		for (ItemAttributeEntry attributeEntry : attributes) {
			ItemAttribute attribute = attributeEntry.attribute();
			if (attribute == null)
				continue;
			boolean inverted = attributeEntry.inverted();
			if (count > 3) {
				list.add(Component.literal("- ...")
					.withStyle(ChatFormatting.DARK_GRAY));
				break;
			}
			list.add(Component.literal("- ")
				.append(attribute.format(inverted)));
			count++;
		}

		if (count == 0)
			return Collections.emptyList();

		return list;
	}

	@Override
	protected void openGui(ServerPlayer player, ItemStack filter) {
		new AttributeFilterGui(player, filter).open();
	}

	@Override
	public DataComponentType<?> getComponentType() {
		return ToolsRegistry.ATTRIBUTE_FILTER_MATCHED_ATTRIBUTES;
	}

	@Override
	public FilterItemStack makeStackWrapper(ItemStack filter) {
		return new AttributeFilterItemStack(filter);
	}

	@Override
	public ItemStack[] getFilterItems(ItemStack stack) {
		AttributeFilterWhitelistMode whitelistMode = stack.get(ToolsRegistry.ATTRIBUTE_FILTER_WHITELIST_MODE);
		List<ItemAttribute.ItemAttributeEntry> attributes = stack.getOrDefault(ToolsRegistry.ATTRIBUTE_FILTER_MATCHED_ATTRIBUTES, List.of());

		if (whitelistMode == AttributeFilterWhitelistMode.WHITELIST_DISJ && attributes.size() == 1) {
			ItemAttribute attribute = attributes.getFirst().attribute();
			if (attribute instanceof InTagAttribute(TagKey<Item> tag)) {
				List<ItemStack> stacks = new ArrayList<>();
				for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
					stacks.add(new ItemStack(holder.value()));
				}
				return stacks.toArray(ItemStack[]::new);
			}
		}
		return new ItemStack[0];
	}
}
