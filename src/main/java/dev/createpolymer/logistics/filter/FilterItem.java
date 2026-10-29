package dev.createpolymer.logistics.filter;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;

import dev.createpolymer.foundation.recipe.ItemCopyingRecipe.SupportsItemCopying;
import dev.createpolymer.foundation.item.ItemHelper;
import dev.createpolymer.logistics.box.PackageItem;
import dev.createpolymer.registry.feature.PackagesRegistry;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Base of Create's filter items. Players see it as a vanilla item with Create's item model (the same Polymer
 * mechanism as every other item here); the filter's contents live in data components that are never sent to clients.
 */
public abstract class FilterItem extends SimplePolymerItem implements SupportsItemCopying {
	public static ListFilterItem regular(Properties properties) {
		return new ListFilterItem(properties);
	}

	public static AttributeFilterItem attribute(Properties properties) {
		return new AttributeFilterItem(properties);
	}

	public static PackageFilterItem address(Properties properties) {
		return new PackageFilterItem(properties);
	}

	protected FilterItem(Properties properties) {
		super(properties, Items.TRIAL_KEY, true);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getPlayer() == null)
			return InteractionResult.PASS;
		return use(context.getLevel(), context.getPlayer(), context.getHand());
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
		Consumer<Component> builder, TooltipFlag flag) {
		List<Component> makeSummary = makeSummary(stack);
		if (makeSummary.isEmpty())
			return;
		builder.accept(CommonComponents.SPACE);
		makeSummary.forEach(builder);
	}

	/** The container-contents component adds its own item list to the tooltip, which would repeat the summary. */
	@Override
	public void modifyClientTooltip(List<Component> tooltip, ItemStack stack, PacketContext context) {
		tooltip.removeIf(line -> line.getContents() instanceof TranslatableContents contents
			&& contents.getKey().startsWith("item.container."));
	}

	public abstract List<Component> makeSummary(ItemStack filter);

	@Override
	public InteractionResult use(Level world, Player player, InteractionHand hand) {
		ItemStack heldItem = player.getItemInHand(hand);

		if (!player.isShiftKeyDown() && hand == InteractionHand.MAIN_HAND) {
			if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer)
				openGui(serverPlayer, heldItem);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	/** Opens the configuration GUI for {@code filter}, which is the stack in the player's main hand. */
	protected abstract void openGui(ServerPlayer player, ItemStack filter);

	public static boolean testDirect(ItemStack filter, ItemStack stack, boolean matchNBT) {
		if (matchNBT) {
			if (PackageItem.isPackage(filter) && PackageItem.isPackage(stack))
				return doPackagesHaveSameData(filter, stack);

			return ItemStack.isSameItemSameComponents(filter, stack);
		}

		if (PackageItem.isPackage(filter) && PackageItem.isPackage(stack))
			return true;

		return ItemHelper.sameItem(filter, stack);
	}

	public static boolean doPackagesHaveSameData(@NotNull ItemStack a, @NotNull ItemStack b) {
		if (a.isEmpty())
			return false;
		if (!ItemStack.isSameItemSameComponents(a, b))
			return false;
		for (TypedDataComponent<?> component : a.getComponents()) {
			DataComponentType<?> type = component.type();
			if (type.equals(PackagesRegistry.PACKAGE_ORDER_DATA) ||
				type.equals(PackagesRegistry.PACKAGE_ORDER_CONTEXT))
				continue;
			if (!Objects.equals(a.get(type), b.get(type)))
				return false;
		}
		return true;
	}

	public abstract DataComponentType<?> getComponentType();

	public abstract FilterItemStack makeStackWrapper(ItemStack filter);

	public abstract ItemStack[] getFilterItems(ItemStack stack);
}
