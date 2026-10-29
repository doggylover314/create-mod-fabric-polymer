package dev.createpolymer.logistics.stockTicker;

import java.util.ArrayList;
import java.util.List;

import dev.createpolymer.logistics.filter.FilterItem;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Create's StockKeeperCategoryMenu and StockKeeperCategoryScreen as an sgui chest. Create's screen is a list of
 * categories that are edited one by one; here each category is a filter item in a slot of the first three rows. The
 * order of the slots is the order of the categories, and the name of the category is the name of the filter item
 * (rename it in an anvil). Taking a filter out deletes the category, and gives the filter back like Create does.
 */
public class StockKeeperCategoryGui extends SimpleGui {
	private static final String PREFIX = "create.gui.stock_ticker.";

	public static final int CATEGORY_SLOTS = 27;
	private static final int INFO_BUTTON = 31;
	private static final int CONFIRM_BUTTON = 35;

	private final StockTickerBlockEntity stockTicker;

	public StockKeeperCategoryGui(ServerPlayer player, StockTickerBlockEntity stockTicker) {
		super(MenuType.GENERIC_9x4, player, false);
		this.stockTicker = stockTicker;
		setTitle(Component.translatable(PREFIX + "category_editor"));

		Container container = new CategoryContainer();
		for (int i = 0; i < CATEGORY_SLOTS; i++)
			setSlot(i, new Slot(container, i, 0, 0) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return container.canPlaceItem(getContainerSlot(), stack);
				}
			});

		for (int i = CATEGORY_SLOTS; i < 36; i++)
			setSlot(i, new GuiElementBuilder(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY)).setName(Component.empty())
				.hideTooltip());

		setSlot(INFO_BUTTON, new GuiElementBuilder(Items.BOOK)
			.setName(Component.translatable(PREFIX + "category_filter")
				.withStyle(ChatFormatting.GOLD))
			.addLoreLine(Component.translatable(PREFIX + "category_filter_tip")
				.withStyle(ChatFormatting.GRAY))
			.addLoreLine(Component.translatable(PREFIX + "category_filter_tip_1")
				.withStyle(ChatFormatting.GRAY))
			.addLoreLine(Component.translatableWithFallback(PREFIX + "category_name_tip",
				"Rename the filter in an anvil to name the category")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))
			.hideDefaultTooltip());
		setSlot(CONFIRM_BUTTON, new GuiElementBuilder(Items.DYE.pick(DyeColor.LIME))
			.setName(Component.translatableWithFallback("create.gui.confirm", "Confirm")
				.withStyle(ChatFormatting.GREEN))
			.hideDefaultTooltip()
			.setCallback(() -> close()));
	}

	@Override
	public boolean stillValid() {
		return !stockTicker.isRemoved() && player.isWithinBlockInteractionRange(stockTicker.getBlockPos(), 4);
	}

	/** The categories of the ticker as a vanilla container, written back to the ticker on every change. */
	private class CategoryContainer implements Container {
		private final List<ItemStack> stacks = new ArrayList<>();

		CategoryContainer() {
			for (ItemStack category : stockTicker.getCategories())
				if (!category.isEmpty() && stacks.size() < CATEGORY_SLOTS)
					stacks.add(category.copy());
			while (stacks.size() < CATEGORY_SLOTS)
				stacks.add(ItemStack.EMPTY);
		}

		@Override
		public int getContainerSize() {
			return CATEGORY_SLOTS;
		}

		@Override
		public boolean isEmpty() {
			return stacks.stream()
				.allMatch(ItemStack::isEmpty);
		}

		@Override
		public ItemStack getItem(int slot) {
			return stacks.get(slot);
		}

		@Override
		public ItemStack removeItem(int slot, int count) {
			ItemStack removed = stacks.get(slot)
				.split(count);
			if (!removed.isEmpty())
				setChanged();
			return removed;
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			ItemStack stack = stacks.get(slot);
			stacks.set(slot, ItemStack.EMPTY);
			return stack;
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			stacks.set(slot, stack);
			setChanged();
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}

		@Override
		public void setChanged() {
			List<ItemStack> categories = new ArrayList<>();
			for (ItemStack stack : stacks)
				if (!stack.isEmpty())
					categories.add(stack.copy());
			stockTicker.setCategories(categories);
		}

		@Override
		public boolean stillValid(Player player) {
			return !stockTicker.isRemoved();
		}

		@Override
		public boolean canPlaceItem(int slot, ItemStack stack) {
			return stack.getItem() instanceof FilterItem;
		}

		@Override
		public void clearContent() {
			for (int i = 0; i < stacks.size(); i++)
				stacks.set(i, ItemStack.EMPTY);
			setChanged();
		}
	}
}
