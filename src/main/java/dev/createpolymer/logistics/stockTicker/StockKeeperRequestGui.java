package dev.createpolymer.logistics.stockTicker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.logistics.BigItemStack;
import dev.createpolymer.logistics.packager.InventorySummary;
import dev.createpolymer.logistics.packagerLink.LogisticsNetwork;
import dev.createpolymer.registry.feature.PackagesRegistry;
import dev.createpolymer.util.Lang;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Create's StockKeeperRequestScreen as an sgui chest. The first four rows list the stock of the network, category by
 * category (clicking a category header hides or shows it, like in Create), and the next row holds the order of up to
 * nine item types; the last row has the controls: search, pages, address, lock, clear and send.
 * <ul>
 * <li>Stock item: left click adds 1 to the order (shift: a stack, middle click: 10), right click takes 1 away
 * (shift: a stack).</li>
 * <li>Order item: any click takes away 1 (shift: a stack, middle click: 10).</li>
 * </ul>
 * Searching with a leading {@code @} matches the mod, with {@code #} the tags of the item, like in Create.
 * <p>
 * With a redstone requester or a table cloth in hand, sending programs that item instead of making a request.
 */
public class StockKeeperRequestGui extends SimpleGui {
	private static final String PREFIX = "create.gui.stock_keeper.";

	private static final int COLS = 9;
	private static final int ITEM_SLOTS = 36;
	private static final int ORDER_START = 36;
	private static final int SEARCH_BUTTON = 45;
	private static final int PREVIOUS_PAGE = 46;
	private static final int PAGE_INDICATOR = 47;
	private static final int NEXT_PAGE = 48;
	private static final int ADDRESS_BUTTON = 49;
	private static final int LOCK_BUTTON = 51;
	private static final int CLEAR_BUTTON = 52;
	private static final int SEND_BUTTON = 53;
	private static final int REFRESH_TICKS = 15;

	/** What survives opening the anvil for text input. */
	public static class State {
		public final List<BigItemStack> itemsToOrder = new ArrayList<>();
		public String address;
		public String search = "";
		public int page;
		public final Set<Integer> hiddenCategories = new HashSet<>();
	}

	private sealed interface Entry permits Header, Item, Filler {}

	private record Header(int category, ItemStack filter, String name, boolean hidden) implements Entry {}

	private record Item(BigItemStack stack) implements Entry {}

	private record Filler() implements Entry {}

	private final StockTickerBlockEntity stockTicker;
	private final State state;
	private final boolean isAdmin;
	private final boolean encodeRequester;
	private boolean isLocked;

	private InventorySummary summary = new InventorySummary();
	private List<BigItemStack> lastSnapshot;
	private List<Entry> entries = new ArrayList<>();
	private int ticks;
	private int sinceRefresh;

	public StockKeeperRequestGui(ServerPlayer player, StockTickerBlockEntity stockTicker) {
		this(player, stockTicker, null);
	}

	private StockKeeperRequestGui(ServerPlayer player, StockTickerBlockEntity stockTicker, @Nullable State state) {
		super(MenuType.GENERIC_9x6, player, false);
		this.stockTicker = stockTicker;
		if (state == null) {
			state = new State();
			state.address = stockTicker.getPreviouslyUsedAddress();
			state.hiddenCategories.addAll(stockTicker.getHiddenCategories(player.getUUID()));
		}
		this.state = state;
		ItemStack itemToProgram = player.getMainHandItem();
		encodeRequester = dev.createpolymer.logistics.redstoneRequester.RedstoneRequesterBlock.isRequester(itemToProgram)
			|| dev.createpolymer.logistics.redstoneRequester.RedstoneRequesterBlock.isTableCloth(itemToProgram);
		isAdmin = stockTicker.behaviour.mayAdministrate(player)
			&& PackagesRegistry.LOGISTICS.isLockable(stockTicker.behaviour.freqId);
		isLocked = PackagesRegistry.LOGISTICS.isLocked(stockTicker.behaviour.freqId);

		setTitle(Component.translatable(PREFIX + "title"));
		setLockPlayerInventory(true);
		refreshStock(true);
		sinceRefresh = 0;
	}

	// ---- stock ----

	private void refreshStock(boolean force) {
		summary = stockTicker.getRecentSummary();
		List<BigItemStack> snapshot = new ArrayList<>();
		for (BigItemStack entry : summary.getStacksByCount())
			snapshot.add(new BigItemStack(entry.stack, entry.count));
		if (!force && snapshot.equals(lastSnapshot))
			return;
		lastSnapshot = snapshot;
		revalidateOrders();
		rebuildEntries();
		redraw();
	}

	private void rebuildEntries() {
		List<List<BigItemStack>> sorted = stockTicker.sortIntoCategories(summary);
		List<ItemStack> categories = stockTicker.getCategories();

		String valueWithPrefix = state.search;
		boolean modSearch = false;
		boolean tagSearch = false;
		if ((modSearch = valueWithPrefix.startsWith("@")) || (tagSearch = valueWithPrefix.startsWith("#")))
			valueWithPrefix = valueWithPrefix.substring(1);
		final String value = valueWithPrefix.toLowerCase(Locale.ROOT);
		boolean filtering = !state.search.isBlank();

		List<List<BigItemStack>> displayed = new ArrayList<>();
		boolean anyItemsInCategory = false;
		for (int categoryIndex = 0; categoryIndex < sorted.size(); categoryIndex++) {
			List<BigItemStack> inCategory = new ArrayList<>();
			for (BigItemStack entry : sorted.get(categoryIndex)) {
				if (!filtering || matches(entry.stack, value, modSearch, tagSearch))
					inCategory.add(entry);
			}
			displayed.add(inCategory);
			if (!inCategory.isEmpty() && categoryIndex < sorted.size() - 1)
				anyItemsInCategory = true;
		}

		entries = new ArrayList<>();
		for (int categoryIndex = 0; categoryIndex < displayed.size(); categoryIndex++) {
			List<BigItemStack> inCategory = displayed.get(categoryIndex);
			if (inCategory.isEmpty())
				continue;
			int beCategory = categoryIndex < categories.size() ? categoryIndex : -1;
			boolean hidden = state.hiddenCategories.contains(beCategory);

			if (anyItemsInCategory) {
				ItemStack filter = beCategory == -1 ? ItemStack.EMPTY : categories.get(beCategory);
				String name = filter.isEmpty() ? Lang.builder("create")
					.translate("gui.stock_keeper.unsorted_category")
					.string()
					: filter.getHoverName()
						.getString();
				if (beCategory != -1 && filter.isEmpty())
					name = "";
				entries.add(new Header(beCategory, filter, name, hidden));
				padRow();
				if (hidden)
					continue;
			}

			for (BigItemStack entry : inCategory)
				entries.add(new Item(entry));
			padRow();
		}
		while (!entries.isEmpty() && entries.get(entries.size() - 1) instanceof Filler)
			entries.remove(entries.size() - 1);
	}

	private void padRow() {
		while (entries.size() % COLS != 0)
			entries.add(new Filler());
	}

	private static boolean matches(ItemStack stack, String value, boolean modSearch, boolean tagSearch) {
		if (modSearch)
			return BuiltInRegistries.ITEM.getKey(stack.getItem())
				.getNamespace()
				.contains(value);
		if (tagSearch)
			return stack.typeHolder()
				.tags()
				.anyMatch(key -> key.location()
					.toString()
					.contains(value));
		return stack.getHoverName()
			.getString()
			.toLowerCase(Locale.ROOT)
			.contains(value)
			|| BuiltInRegistries.ITEM.getKey(stack.getItem())
				.getPath()
				.contains(value);
	}

	private void revalidateOrders() {
		for (BigItemStack entry : new ArrayList<>(state.itemsToOrder)) {
			entry.count = Math.min(summary.getCountOf(entry.stack), entry.count);
			if (entry.count <= 0)
				state.itemsToOrder.remove(entry);
		}
	}

	@Nullable
	private BigItemStack getOrderForItem(ItemStack stack) {
		for (BigItemStack entry : state.itemsToOrder)
			if (ItemStack.isSameItemSameComponents(stack, entry.stack))
				return entry;
		return null;
	}

	// ---- drawing ----

	private int pages() {
		return Math.max(1, Mth.ceil(entries.size() / (float) ITEM_SLOTS));
	}

	private void redraw() {
		state.page = Mth.clamp(state.page, 0, pages() - 1);
		drawItems();
		drawOrders();
		drawControls();
	}

	private void drawItems() {
		int first = state.page * ITEM_SLOTS;
		for (int i = 0; i < ITEM_SLOTS; i++) {
			int index = first + i;
			if (index >= entries.size()) {
				if (entries.isEmpty() && i == 13)
					setSlot(i, new GuiElementBuilder(Items.BARRIER).setName(troubleshootingMessage())
						.hideDefaultTooltip());
				else
					clearSlot(i);
				continue;
			}
			Entry entry = entries.get(index);
			if (entry instanceof Header header)
				setSlot(i, headerButton(header));
			else if (entry instanceof Item item)
				setSlot(i, stockButton(item.stack()));
			else
				setSlot(i, new GuiElementBuilder(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY)).setName(Component.empty())
					.hideTooltip());
		}
	}

	private GuiElementBuilder headerButton(Header header) {
		GuiElementBuilder builder = header.filter()
			.isEmpty() ? new GuiElementBuilder(Items.BOOKSHELF) : new GuiElementBuilder(header.filter()
				.copyWithCount(1));
		MutableComponent name = Component.literal(header.name());
		return builder.setName(name.withStyle(header.hidden() ? ChatFormatting.DARK_GRAY : ChatFormatting.GOLD))
			.addLoreLine(Component.translatableWithFallback(PREFIX + "toggle_category",
				header.hidden() ? "Hidden. Click to show" : "Click to hide")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))
			.hideDefaultTooltip()
			.setCallback(() -> toggleCategory(header));
	}

	private GuiElementBuilder stockButton(BigItemStack entry) {
		BigItemStack order = getOrderForItem(entry.stack);
		int ordered = order == null ? 0 : order.count;
		int left = entry.isInfinite() ? entry.count : Math.max(0, entry.count - ordered);
		GuiElementBuilder builder = new GuiElementBuilder(entry.stack.copyWithCount(1))
			.setCount(Mth.clamp(left, 1, 99))
			.addLoreLine(Component.translatableWithFallback(PREFIX + "in_stock", "In stock: %s", amount(entry.count))
				.withStyle(ChatFormatting.GRAY));
		if (ordered > 0)
			builder.addLoreLine(Component.translatableWithFallback(PREFIX + "ordered", "Ordered: %s", amount(ordered))
				.withStyle(ChatFormatting.GREEN));
		builder.addLoreLine(Component.translatableWithFallback(PREFIX + "order_hint",
			"Left click: add, right click: remove, shift: a stack, middle click: 10")
			.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		return builder.setCallback((ClickType type) -> clickStock(entry, type));
	}

	private static String amount(int count) {
		if (count >= BigItemStack.INF)
			return "+";
		return Lang.formatNumber(count);
	}

	private void drawOrders() {
		for (int i = 0; i < COLS; i++) {
			int slot = ORDER_START + i;
			if (i >= state.itemsToOrder.size()) {
				setSlot(slot, new GuiElementBuilder(Items.STAINED_GLASS_PANE.pick(DyeColor.LIGHT_GRAY)).setName(Component.empty())
					.hideTooltip());
				continue;
			}
			BigItemStack order = state.itemsToOrder.get(i);
			int index = i;
			setSlot(slot, new GuiElementBuilder(order.stack.copyWithCount(1))
				.setCount(Mth.clamp(order.count, 1, 99))
				.addLoreLine(Component.translatableWithFallback(PREFIX + "ordered", "Ordered: %s", amount(order.count))
					.withStyle(ChatFormatting.GREEN))
				.addLoreLine(Component.translatableWithFallback(PREFIX + "order_remove_hint",
					"Click to remove, shift: a stack, middle click: 10")
					.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))
				.setCallback((ClickType type) -> clickOrder(index, type)));
		}
	}

	private void drawControls() {
		boolean searching = !state.search.isBlank();
		setSlot(SEARCH_BUTTON, new GuiElementBuilder(Items.SPYGLASS)
			.setName(searching ? Component.literal(state.search)
				.withStyle(ChatFormatting.WHITE)
				: Component.translatable(PREFIX + "search_items")
					.withStyle(ChatFormatting.GOLD))
			.addLoreLine(Component.translatableWithFallback(PREFIX + "search_hint",
				"Left click to type, right click to clear")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))
			.hideDefaultTooltip()
			.setCallback((ClickType type) -> {
				if (type.isRight) {
					state.search = "";
					state.page = 0;
					rebuildEntries();
					redraw();
				} else
					editSearch();
			}));

		boolean hasPrevious = state.page > 0;
		boolean hasNext = state.page < pages() - 1;
		setSlot(PREVIOUS_PAGE, new GuiElementBuilder(hasPrevious ? Items.ARROW : Items.DYE.pick(DyeColor.GRAY))
			.setName(Component.translatableWithFallback(PREFIX + "previous_page", "Previous page")
				.withStyle(hasPrevious ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY))
			.hideDefaultTooltip()
			.setCallback(() -> changePage(-1)));
		setSlot(PAGE_INDICATOR, new GuiElementBuilder(Items.PAPER)
			.setName(Component.translatableWithFallback(PREFIX + "page", "Page %s of %s", state.page + 1, pages())
				.withStyle(ChatFormatting.WHITE))
			.hideDefaultTooltip());
		setSlot(NEXT_PAGE, new GuiElementBuilder(hasNext ? Items.ARROW : Items.DYE.pick(DyeColor.GRAY))
			.setName(Component.translatableWithFallback(PREFIX + "next_page", "Next page")
				.withStyle(hasNext ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY))
			.hideDefaultTooltip()
			.setCallback(() -> changePage(1)));

		boolean named = state.address != null && !state.address.isBlank();
		setSlot(ADDRESS_BUTTON, new GuiElementBuilder(Items.NAME_TAG)
			.setName(named ? Component.literal(state.address)
				.withStyle(ChatFormatting.WHITE)
				: Component.translatable(PREFIX + "package_address")
					.withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC))
			.addLoreLine(Component.translatable("create.gui.factory_panel.restocker_address")
				.withStyle(ChatFormatting.GRAY))
			.addLoreLine(Component.translatableWithFallback("create.gui.package_port.click_to_edit",
				"Click to change the address")
				.withStyle(ChatFormatting.DARK_GRAY))
			.hideDefaultTooltip()
			.setCallback(this::editAddress));

		if (isAdmin)
			setSlot(LOCK_BUTTON, new GuiElementBuilder(isLocked ? Items.IRON_DOOR : Items.OAK_DOOR)
				.setName(Component.translatable(PREFIX + (isLocked ? "network_locked" : "network_open"))
					.withStyle(isLocked ? ChatFormatting.RED : ChatFormatting.GREEN))
				.addLoreLine(Component.translatable(PREFIX + "network_lock_tip")
					.withStyle(ChatFormatting.GRAY))
				.addLoreLine(Component.translatable(PREFIX + "network_lock_tip_1")
					.withStyle(ChatFormatting.GRAY))
				.addLoreLine(Component.translatable(PREFIX + "network_lock_tip_2")
					.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))
				.hideDefaultTooltip()
				.setCallback(this::toggleLock));
		else
			clearSlot(LOCK_BUTTON);

		setSlot(CLEAR_BUTTON, new GuiElementBuilder(Items.BARRIER)
			.setName(Component.translatableWithFallback(PREFIX + "clear_order", "Clear order")
				.withStyle(ChatFormatting.RED))
			.hideDefaultTooltip()
			.setCallback(() -> {
				state.itemsToOrder.clear();
				redraw();
			}));

		setSlot(SEND_BUTTON, new GuiElementBuilder(Items.DYE.pick(state.itemsToOrder.isEmpty() ? DyeColor.GRAY : DyeColor.LIME))
			.setName(Component.translatable(PREFIX + (encodeRequester ? "configure" : "send"))
				.withStyle(state.itemsToOrder.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.GREEN))
			.hideDefaultTooltip()
			.setCallback(this::sendIt));
	}

	private Component troubleshootingMessage() {
		String key;
		if (stockTicker.activeLinks == 0)
			key = "no_packagers_linked";
		else if (summary.isEmpty())
			key = "inventories_empty";
		else
			key = "no_search_results";
		return Component.translatable(PREFIX + key)
			.withStyle(ChatFormatting.GRAY);
	}

	// ---- actions ----

	/** Sets the search text, like typing it into the search box. */
	public void search(String text) {
		state.search = text;
		state.page = 0;
		rebuildEntries();
		redraw();
	}

	private void changePage(int by) {
		int page = Mth.clamp(state.page + by, 0, pages() - 1);
		if (page == state.page)
			return;
		state.page = page;
		redraw();
		uiSound(SoundEvents.BOOK_PAGE_TURN, 1, 1);
	}

	private void toggleCategory(Header header) {
		if (!header.hidden()) {
			state.hiddenCategories.add(header.category());
			uiSound(SoundEvents.ITEM_FRAME_ROTATE_ITEM, 1f, 1.5f);
		} else {
			state.hiddenCategories.remove(header.category());
			uiSound(SoundEvents.ITEM_FRAME_ROTATE_ITEM, 1f, 0.675f);
		}
		stockTicker.setHiddenCategories(player.getUUID(), new ArrayList<>(state.hiddenCategories));
		rebuildEntries();
		redraw();
	}

	private void toggleLock() {
		if (!isAdmin || !stockTicker.behaviour.mayAdministrate(player))
			return;
		isLocked = !isLocked;
		LogisticsNetwork network = PackagesRegistry.LOGISTICS.logisticsNetworks.get(stockTicker.behaviour.freqId);
		if (network != null) {
			network.locked = isLocked;
			PackagesRegistry.LOGISTICS.markDirty();
		}
		uiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1, 1);
		drawControls();
	}

	private void clickStock(BigItemStack entry, ClickType type) {
		if (!type.isLeft && !type.isRight && !type.isMiddle)
			return;
		ItemStack itemStack = entry.stack;
		int transfer = type.shift ? itemStack.getMaxStackSize() : type.isMiddle ? 10 : 1;

		BigItemStack existingOrder = getOrderForItem(itemStack);
		if (existingOrder == null) {
			if (state.itemsToOrder.size() >= COLS || type.isRight)
				return;
			state.itemsToOrder.add(existingOrder = new BigItemStack(itemStack.copyWithCount(1), 0));
			uiSound(SoundEvents.WOOL_STEP, 0.75f, 1.2f);
			uiSound(SoundEvents.BAMBOO_WOOD_STEP, 0.75f, 0.8f);
		}

		int current = existingOrder.count;
		if (type.isRight) {
			existingOrder.count = current - transfer;
			if (existingOrder.count <= 0) {
				state.itemsToOrder.remove(existingOrder);
				uiSound(SoundEvents.WOOL_STEP, 0.75f, 1.8f);
				uiSound(SoundEvents.BAMBOO_WOOD_STEP, 0.75f, 1.8f);
			}
		} else {
			existingOrder.count = current + Math.min(transfer, entry.count - current);
			if (existingOrder.count <= 0)
				state.itemsToOrder.remove(existingOrder);
		}
		redraw();
	}

	private void clickOrder(int index, ClickType type) {
		if (index >= state.itemsToOrder.size())
			return;
		if (!type.isLeft && !type.isRight && !type.isMiddle)
			return;
		BigItemStack order = state.itemsToOrder.get(index);
		int transfer = type.shift ? order.stack.getMaxStackSize() : type.isMiddle ? 10 : 1;
		order.count -= transfer;
		if (order.count <= 0) {
			state.itemsToOrder.remove(order);
			uiSound(SoundEvents.WOOL_STEP, 0.75f, 1.8f);
			uiSound(SoundEvents.BAMBOO_WOOD_STEP, 0.75f, 1.8f);
		}
		redraw();
	}

	/** Create's sendIt: the crafting entries of the order are not supported (they come from the recipe viewer). */
	private void sendIt() {
		revalidateOrders();
		if (state.itemsToOrder.isEmpty())
			return;

		PackageOrderWithCrafts order = PackageOrderWithCrafts.simple(BigItemStack.duplicateWrappers(state.itemsToOrder));
		PackageOrderRequest.apply(player, stockTicker, order, state.address == null ? "" : state.address,
			encodeRequester);

		state.itemsToOrder.clear();
		sinceRefresh = REFRESH_TICKS - 5;
		if (!encodeRequester) {
			player.sendOverlayMessage(Component.translatable(PREFIX + "request_sent")
				.withStyle(ChatFormatting.GOLD));
			redraw();
		}
		uiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1, 1);
	}

	private void uiSound(net.minecraft.sounds.SoundEvent event, float volume, float pitch) {
		player.level()
			.playSound(null, player.blockPosition(), event, SoundSource.PLAYERS, volume, pitch);
	}

	// ---- text input ----

	private void editAddress() {
		StockTickerBlockEntity stockTicker = this.stockTicker;
		State state = this.state;
		ServerPlayer player = this.player;
		AnvilInputGui anvil = new AnvilInputGui(player, false) {
			@Override
			public void onInput(String input) {
				state.address = input.length() > 25 ? input.substring(0, 25) : input;
				stockTicker.setPreviouslyUsedAddress(state.address);
			}
		};
		anvil.setTitle(Component.translatable(PREFIX + "package_address"));
		anvil.setDefaultInputValue(state.address == null ? "" : state.address);
		anvil.setSlot(2, confirmButton(() -> new StockKeeperRequestGui(player, stockTicker, state).open()));
		anvil.open();
	}

	private void editSearch() {
		StockTickerBlockEntity stockTicker = this.stockTicker;
		State state = this.state;
		ServerPlayer player = this.player;
		AnvilInputGui anvil = new AnvilInputGui(player, false) {
			@Override
			public void onInput(String input) {
				state.search = input.length() > 50 ? input.substring(0, 50) : input;
			}
		};
		anvil.setTitle(Component.translatable(PREFIX + "search_items"));
		anvil.setDefaultInputValue(state.search);
		anvil.setSlot(2, confirmButton(() -> {
			state.page = 0;
			new StockKeeperRequestGui(player, stockTicker, state).open();
		}));
		anvil.open();
	}

	private static GuiElementBuilder confirmButton(Runnable action) {
		return new GuiElementBuilder(Items.NAME_TAG)
			.setName(Component.translatableWithFallback("create.gui.package_port.confirm", "Confirm")
				.withStyle(ChatFormatting.GREEN))
			.setCallback(action);
	}

	// ---- gui ----

	@Override
	public void onTick() {
		ticks++;
		if (ticks % 10 == 0 && !stockTicker.isRemoved() && !stockTicker.isKeeperPresent()) {
			close();
			return;
		}
		if (++sinceRefresh >= REFRESH_TICKS) {
			sinceRefresh = 0;
			refreshStock(false);
		}
	}

	@Override
	public boolean stillValid() {
		return !stockTicker.isRemoved() && stockTicker.canPlayerUse(player);
	}

	/** Create's screen sends an empty request when it closes: it keeps the address for the next visit. */
	@Override
	public void onRemoved() {
		stockTicker.setPreviouslyUsedAddress(state.address == null ? "" : state.address);
		stockTicker.setHiddenCategories(player.getUUID(), new ArrayList<>(state.hiddenCategories));
	}
}
