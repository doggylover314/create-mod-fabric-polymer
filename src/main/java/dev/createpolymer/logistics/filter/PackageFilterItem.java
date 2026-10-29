package dev.createpolymer.logistics.filter;

import java.util.Collections;
import java.util.List;

import dev.createpolymer.foundation.gui.PackageFilterGui;
import dev.createpolymer.logistics.box.PackageItem;
import dev.createpolymer.logistics.filter.FilterItemStack.PackageFilterItemStack;
import dev.createpolymer.registry.feature.PackagesRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class PackageFilterItem extends FilterItem {
	protected PackageFilterItem(Properties properties) {
		super(properties);
	}

	@Override
	public List<Component> makeSummary(ItemStack filter) {
		String address = PackageItem.getAddress(filter);
		if (address.isBlank())
			return Collections.emptyList();

		return List.of(Component.literal("-> ")
			.withStyle(ChatFormatting.GRAY)
			.append(Component.literal(address)
				.withStyle(ChatFormatting.GOLD)));
	}

	@Override
	protected void openGui(ServerPlayer player, ItemStack filter) {
		new PackageFilterGui(player, filter).open();
	}

	@Override
	public DataComponentType<?> getComponentType() {
		return PackagesRegistry.PACKAGE_ADDRESS;
	}

	@Override
	public FilterItemStack makeStackWrapper(ItemStack filter) {
		return new PackageFilterItemStack(filter);
	}

	@Override
	public ItemStack[] getFilterItems(ItemStack stack) {
		return new ItemStack[0];
	}
}
