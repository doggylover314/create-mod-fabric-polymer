package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import java.util.List;
import java.util.UUID;

import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.logistics.BigItemStack;
import dev.createpolymer.logistics.box.PackageItem;
import dev.createpolymer.logistics.packager.PackagerBlock;
import dev.createpolymer.logistics.packager.PackagerBlockEntity;
import dev.createpolymer.logistics.packagerLink.PackagerLinkBlock;
import dev.createpolymer.logistics.packagerLink.PackagerLinkBlockEntity;
import dev.createpolymer.logistics.redstoneRequester.AutoRequestData;
import dev.createpolymer.logistics.redstoneRequester.RedstoneRequesterBlock;
import dev.createpolymer.logistics.redstoneRequester.RedstoneRequesterBlockEntity;
import dev.createpolymer.logistics.redstoneRequester.RedstoneRequesterGui;
import dev.createpolymer.logistics.stockTicker.PackageOrderWithCrafts;
import dev.createpolymer.logistics.stockTicker.StockKeeperCategoryGui;
import dev.createpolymer.logistics.stockTicker.StockKeeperRequestGui;
import dev.createpolymer.logistics.stockTicker.StockTickerBlockEntity;
import dev.createpolymer.logistics.stockTicker.StockTickerInteractionHandler;
import dev.createpolymer.logistics.tableCloth.ShoppingListItem;
import dev.createpolymer.logistics.tableCloth.ShoppingListItem.ShoppingList;
import dev.createpolymer.logistics.tableCloth.TableClothBlock;
import dev.createpolymer.logistics.tableCloth.TableClothBlockEntity;
import dev.createpolymer.processing.burner.BlazeBurnerBlock;
import dev.createpolymer.processing.burner.BlazeBurnerBlock.HeatLevel;
import dev.createpolymer.registry.feature.BurnerRegistry;
import dev.createpolymer.registry.feature.FilterRegistry;
import dev.createpolymer.registry.feature.PackagesRegistry;
import dev.createpolymer.registry.feature.StockRegistry;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.gui.SlotBasedGui;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.AttachFace;

public class StockGameTests {

	private static final BlockPos CHEST = new BlockPos(1, 1, 2);
	private static final BlockPos PACKAGER = new BlockPos(2, 1, 2);
	private static final BlockPos TICKER = new BlockPos(5, 1, 2);
	private static final BlockPos KEEPER = new BlockPos(5, 1, 3);

	private static int countIn(IItemHandler handler, Item item) {
		int count = 0;
		for (int i = 0; i < handler.getSlots(); i++)
			if (handler.getStackInSlot(i).is(item))
				count += handler.getStackInSlot(i).getCount();
		return count;
	}

	private static ChestBlockEntity chest(GameTestHelper helper) {
		return helper.getBlockEntity(CHEST, ChestBlockEntity.class);
	}

	@SuppressWarnings("removal")
	private static ServerPlayer connectedPlayer(GameTestHelper helper) {
		return helper.makeMockServerPlayerInLevel();
	}

	private static void leave(GameTestHelper helper, ServerPlayer player) {
		player.connection.disconnect(Component.literal("test over"));
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	private static ItemStack at(SlotBasedGui gui, int slot) {
		var element = gui.getGuiElement(slot);
		return element == null ? ItemStack.EMPTY : element.getItemStack();
	}

	private static void click(SlotBasedGui gui, int slot, ClickType type) {
		gui.click(slot, type, ContainerInput.PICKUP);
	}

	/** A chest with iron ingots and a packager with a stock link on it, and a stock ticker with a blaze burner as keeper. */
	private static void setup(GameTestHelper helper, boolean keeper) {
		floor(helper);
		helper.setBlock(CHEST, Blocks.CHEST);
		helper.setBlock(PACKAGER, PackagesRegistry.PACKAGER.defaultBlockState().setValue(PackagerBlock.FACING, Direction.EAST));
		helper.setBlock(PACKAGER.above(), PackagesRegistry.STOCK_LINK.defaultBlockState().setValue(PackagerLinkBlock.FACE, AttachFace.FLOOR));
		helper.setBlock(TICKER, StockRegistry.STOCK_TICKER.defaultBlockState());
		if (keeper)
			helper.setBlock(KEEPER, BurnerRegistry.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, HeatLevel.KINDLED));
	}

	/** Fills the chest and tunes the ticker to the network of the link. Call one tick after {@link #setup}. */
	private static UUID stock(GameTestHelper helper, ItemStack... stacks) {
		for (int i = 0; i < stacks.length; i++)
			chest(helper).setItem(i, stacks[i]);
		UUID freq = helper.getBlockEntity(PACKAGER.above(), PackagerLinkBlockEntity.class).behaviour.freqId;
		helper.getBlockEntity(TICKER, StockTickerBlockEntity.class).behaviour.freqId = freq;
		return freq;
	}

	private static void assertPackage(GameTestHelper helper, String address, Item item, int count) {
		PackagerBlockEntity packager = helper.getBlockEntity(PACKAGER, PackagerBlockEntity.class);
		ItemStack box = packager.heldBox;
		helper.assertTrue(PackageItem.isPackage(box), "the packager holds no package");
		helper.assertTrue(PackageItem.getAddress(box).equals(address), "the package address is '" + PackageItem.getAddress(box) + "'");
		int inBox = countIn(PackageItem.getContents(box), item);
		helper.assertTrue(inBox == count, "the package holds " + inBox + " instead of " + count);
	}

	@GameTest(maxTicks = 100)
	public void stockTickerNeedsAKeeper(GameTestHelper helper) {
		setup(helper, false);
		helper.runAfterDelay(2, () -> {
			StockTickerBlockEntity ticker = helper.getBlockEntity(TICKER, StockTickerBlockEntity.class);
			helper.assertTrue(!ticker.isKeeperPresent(), "a lonely ticker has a keeper");
			helper.setBlock(KEEPER, BurnerRegistry.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, HeatLevel.KINDLED));
			helper.assertTrue(ticker.isKeeperPresent(), "a blaze burner next to the ticker is no keeper");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void stockKeeperGuiOrdersItemsFromTheNetwork(GameTestHelper helper) {
		setup(helper, true);
		ServerPlayer[] player = new ServerPlayer[1];
		helper.runAfterDelay(1, () -> {
			stock(helper, new ItemStack(Items.IRON_INGOT, 10));
			helper.getBlockEntity(TICKER, StockTickerBlockEntity.class).setPreviouslyUsedAddress("Shop");
		});
		helper.runAfterDelay(10, () -> {
			StockTickerBlockEntity ticker = helper.getBlockEntity(TICKER, StockTickerBlockEntity.class);
			player[0] = connectedPlayer(helper);
			StockKeeperRequestGui gui = new StockKeeperRequestGui(player[0], ticker);
			gui.open();
			helper.assertTrue(at(gui, 0).is(Items.IRON_INGOT), "the stock is not listed");
			for (int i = 0; i < 4; i++)
				click(gui, 0, ClickType.MOUSE_LEFT);
			helper.assertTrue(at(gui, 36).is(Items.IRON_INGOT), "the order row is empty");
			click(gui, 53, ClickType.MOUSE_LEFT);
			assertPackage(helper, "Shop", Items.IRON_INGOT, 4);
			helper.assertTrue(countIn(ItemHandlerLookup.get(helper.getLevel(), helper.absolutePos(CHEST), null), Items.IRON_INGOT) == 6,
				"the chest was not drained by 4");
			gui.close();
			leave(helper, player[0]);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void stockKeeperGuiCannotOrderMoreThanInStock(GameTestHelper helper) {
		setup(helper, true);
		helper.runAfterDelay(1, () -> stock(helper, new ItemStack(Items.IRON_INGOT, 10)));
		helper.runAfterDelay(10, () -> {
			StockTickerBlockEntity ticker = helper.getBlockEntity(TICKER, StockTickerBlockEntity.class);
			ServerPlayer player = connectedPlayer(helper);
			StockKeeperRequestGui gui = new StockKeeperRequestGui(player, ticker);
			gui.open();
			click(gui, 0, ClickType.MOUSE_LEFT_SHIFT);
			click(gui, 0, ClickType.MOUSE_LEFT_SHIFT);
			click(gui, 53, ClickType.MOUSE_LEFT);
			assertPackage(helper, "", Items.IRON_INGOT, 10);
			gui.close();
			leave(helper, player);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void stockKeeperGuiSortsItemsIntoCategories(GameTestHelper helper) {
		setup(helper, true);
		helper.runAfterDelay(1, () -> stock(helper, new ItemStack(Items.IRON_INGOT, 10), new ItemStack(Items.COAL, 5)));
		helper.runAfterDelay(10, () -> {
			StockTickerBlockEntity ticker = helper.getBlockEntity(TICKER, StockTickerBlockEntity.class);
			ServerPlayer player = connectedPlayer(helper);

			// A category is a filter item in the category GUI
			ItemStack filter = new ItemStack(FilterRegistry.FILTER);
			filter.set(FilterRegistry.FILTER_ITEMS, ItemContainerContents.fromItems(List.of(new ItemStack(Items.IRON_INGOT))));
			filter.set(DataComponents.CUSTOM_NAME, Component.literal("Metals"));
			StockKeeperCategoryGui categoryGui = new StockKeeperCategoryGui(player, ticker);
			categoryGui.open();
			categoryGui.getCustomSlot(0).set(filter);
			helper.assertTrue(ticker.getCategories().size() == 1, "the category was not saved");
			helper.assertTrue(!categoryGui.getCustomSlot(1).mayPlace(new ItemStack(Items.STONE)), "a stone is no category filter");
			categoryGui.close();

			StockKeeperRequestGui gui = new StockKeeperRequestGui(player, ticker);
			gui.open();
			helper.assertTrue(at(gui, 0).is(FilterRegistry.FILTER), "no header for the category");
			helper.assertTrue(at(gui, 9).is(Items.IRON_INGOT), "iron is not in the category");
			helper.assertTrue(at(gui, 18).is(Items.BOOKSHELF), "no header for the unsorted items");
			helper.assertTrue(at(gui, 27).is(Items.COAL), "coal is not unsorted");

			// Clicking a header hides the category
			click(gui, 0, ClickType.MOUSE_LEFT);
			helper.assertTrue(ticker.getHiddenCategories(player.getUUID()).contains(0), "the category was not hidden");
			helper.assertTrue(at(gui, 9).isEmpty() || !at(gui, 9).is(Items.IRON_INGOT), "hidden items are listed");
			gui.close();
			leave(helper, player);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void stockKeeperGuiSearches(GameTestHelper helper) {
		setup(helper, true);
		helper.runAfterDelay(1, () -> stock(helper, new ItemStack(Items.IRON_INGOT, 10), new ItemStack(Items.COAL, 5)));
		helper.runAfterDelay(10, () -> {
			StockTickerBlockEntity ticker = helper.getBlockEntity(TICKER, StockTickerBlockEntity.class);
			ServerPlayer player = connectedPlayer(helper);
			StockKeeperRequestGui gui = new StockKeeperRequestGui(player, ticker);
			gui.open();
			helper.assertTrue(at(gui, 1).is(Items.COAL), "both items are listed");
			gui.search("coal");
			helper.assertTrue(at(gui, 0).is(Items.COAL) && at(gui, 1).isEmpty(), "the search did not narrow the list");
			gui.search("@minecraft");
			helper.assertTrue(at(gui, 1).is(Items.COAL), "searching for a mod did not list its items");
			gui.search("@create");
			helper.assertTrue(at(gui, 0).isEmpty(), "searching for another mod listed items");
			gui.close();
			leave(helper, player);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void networkOwnerCanLockTheStockKeeper(GameTestHelper helper) {
		setup(helper, true);
		helper.runAfterDelay(1, () -> stock(helper, new ItemStack(Items.IRON_INGOT, 10)));
		helper.runAfterDelay(10, () -> {
			StockTickerBlockEntity ticker = helper.getBlockEntity(TICKER, StockTickerBlockEntity.class);
			ServerPlayer player = connectedPlayer(helper);
			StockKeeperRequestGui gui = new StockKeeperRequestGui(player, ticker);
			gui.open();
			click(gui, 51, ClickType.MOUSE_LEFT);
			helper.assertTrue(PackagesRegistry.LOGISTICS.isLocked(ticker.behaviour.freqId), "the network is not locked");
			click(gui, 51, ClickType.MOUSE_LEFT);
			helper.assertTrue(!PackagesRegistry.LOGISTICS.isLocked(ticker.behaviour.freqId), "the network is still locked");
			gui.close();
			leave(helper, player);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void redstoneRequesterSendsItsOrderOnAPulse(GameTestHelper helper) {
		setup(helper, false);
		BlockPos requesterPos = new BlockPos(5, 1, 5);
		helper.setBlock(requesterPos, StockRegistry.REDSTONE_REQUESTER.defaultBlockState());
		helper.runAfterDelay(1, () -> {
			UUID freq = stock(helper, new ItemStack(Items.IRON_INGOT, 10));
			RedstoneRequesterBlockEntity requester = helper.getBlockEntity(requesterPos, RedstoneRequesterBlockEntity.class);
			requester.behaviour.freqId = freq;
			requester.encodedRequest = PackageOrderWithCrafts.simple(List.of(new BigItemStack(new ItemStack(Items.IRON_INGOT), 3)));
			requester.encodedTargetAdress = "Factory";
		});
		helper.runAfterDelay(10, () -> helper.setBlock(requesterPos.east(), Blocks.REDSTONE_BLOCK));
		helper.succeedWhen(() -> {
			assertPackage(helper, "Factory", Items.IRON_INGOT, 3);
			RedstoneRequesterBlockEntity requester = helper.getBlockEntity(requesterPos, RedstoneRequesterBlockEntity.class);
			helper.assertTrue(requester.lastRequestSucceeded, "the requester does not report success");
			helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(requesterPos)).getValue(RedstoneRequesterBlock.POWERED),
				"the requester is not powered");
		});
	}

	@GameTest(maxTicks = 100)
	public void redstoneRequesterNeedsEverythingUnlessPartialOrdersAreAllowed(GameTestHelper helper) {
		setup(helper, false);
		BlockPos requesterPos = new BlockPos(5, 1, 5);
		helper.setBlock(requesterPos, StockRegistry.REDSTONE_REQUESTER.defaultBlockState());
		helper.runAfterDelay(1, () -> {
			UUID freq = stock(helper, new ItemStack(Items.IRON_INGOT, 2));
			RedstoneRequesterBlockEntity requester = helper.getBlockEntity(requesterPos, RedstoneRequesterBlockEntity.class);
			requester.behaviour.freqId = freq;
			requester.encodedRequest = PackageOrderWithCrafts.simple(List.of(new BigItemStack(new ItemStack(Items.IRON_INGOT), 3)));
		});
		helper.runAfterDelay(10, () -> {
			RedstoneRequesterBlockEntity requester = helper.getBlockEntity(requesterPos, RedstoneRequesterBlockEntity.class);
			requester.triggerRequest();
			helper.assertTrue(!requester.lastRequestSucceeded, "an order that is not in stock was sent");
			helper.assertTrue(helper.getBlockEntity(PACKAGER, PackagerBlockEntity.class).heldBox.isEmpty(), "a package was made");
			requester.allowPartialRequests = true;
			requester.triggerRequest();
			helper.assertTrue(requester.lastRequestSucceeded, "the partial order was not sent");
			assertPackage(helper, "", Items.IRON_INGOT, 2);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void redstoneRequesterGuiStoresItsOrder(GameTestHelper helper) {
		floor(helper);
		BlockPos requesterPos = new BlockPos(3, 1, 3);
		helper.setBlock(requesterPos, StockRegistry.REDSTONE_REQUESTER.defaultBlockState());
		helper.runAfterDelay(2, () -> {
			RedstoneRequesterBlockEntity requester = helper.getBlockEntity(requesterPos, RedstoneRequesterBlockEntity.class);
			ServerPlayer player = connectedPlayer(helper);
			RedstoneRequesterGui gui = new RedstoneRequesterGui(player, requester);
			gui.open();
			player.containerMenu.setCarried(new ItemStack(Items.IRON_INGOT, 5));
			click(gui, 0, ClickType.MOUSE_LEFT);
			click(gui, 9, ClickType.MOUSE_LEFT_SHIFT);
			click(gui, 9, ClickType.MOUSE_LEFT);
			helper.assertTrue(requester.encodedRequest.stacks().size() == 1, "the item was not stored");
			BigItemStack entry = requester.encodedRequest.stacks().get(0);
			helper.assertTrue(entry.stack.is(Items.IRON_INGOT) && entry.count == 12, "the order is " + entry);
			click(gui, 21, ClickType.MOUSE_LEFT);
			helper.assertTrue(requester.allowPartialRequests, "partial orders were not enabled");
			// taking the item out of the slot resets the amount
			player.containerMenu.setCarried(ItemStack.EMPTY);
			click(gui, 0, ClickType.MOUSE_LEFT);
			helper.assertTrue(requester.encodedRequest.isEmpty(), "the item is still ordered");
			gui.close();
			leave(helper, player);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void stockKeeperProgramsARedstoneRequester(GameTestHelper helper) {
		setup(helper, true);
		helper.runAfterDelay(1, () -> stock(helper, new ItemStack(Items.IRON_INGOT, 10)));
		helper.runAfterDelay(10, () -> {
			StockTickerBlockEntity ticker = helper.getBlockEntity(TICKER, StockTickerBlockEntity.class);
			ServerPlayer player = connectedPlayer(helper);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(StockRegistry.REDSTONE_REQUESTER_ITEM));
			StockKeeperRequestGui gui = new StockKeeperRequestGui(player, ticker);
			gui.open();
			for (int i = 0; i < 5; i++)
				click(gui, 0, ClickType.MOUSE_LEFT);
			click(gui, 53, ClickType.MOUSE_LEFT);

			ItemStack requesterItem = player.getMainHandItem();
			AutoRequestData data = requesterItem.get(StockRegistry.AUTO_REQUEST_DATA);
			helper.assertTrue(data != null, "the requester was not programmed");
			helper.assertTrue(data.encodedRequest().stacks().size() == 1 && data.encodedRequest().stacks().get(0).count == 5,
				"the request is " + data.encodedRequest().stacks());
			helper.assertTrue(data.targetOffset().equals(helper.absolutePos(TICKER)), "the keeper is at " + data.targetOffset());
			helper.assertTrue(PackagesRegistry.LOGISTICS != null && requesterItem.has(DataComponents.BLOCK_ENTITY_DATA), "the requester is not tuned");
			helper.assertTrue(helper.getBlockEntity(PACKAGER, PackagerBlockEntity.class).heldBox.isEmpty(), "programming the requester made a package");

			// Placing it passes the order on to the block
			BlockPos placed = new BlockPos(3, 1, 5);
			helper.setBlock(placed, StockRegistry.REDSTONE_REQUESTER.defaultBlockState());
			BlockPos absolute = helper.absolutePos(placed);
			StockRegistry.REDSTONE_REQUESTER.setPlacedBy(helper.getLevel(), absolute, helper.getLevel().getBlockState(absolute), player, requesterItem);
			RedstoneRequesterBlockEntity requester = helper.getBlockEntity(placed, RedstoneRequesterBlockEntity.class);
			helper.assertTrue(requester.encodedRequest.stacks().size() == 1 && requester.encodedRequest.stacks().get(0).count == 5,
				"the placed requester has no order");
			gui.close();
			leave(helper, player);
			helper.succeed();
		});
	}

	@GameTest
	public void tableClothHoldsItemsOnDisplay(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		TableClothBlock cloth = StockRegistry.TABLE_CLOTHS.get(DyeColor.RED);
		helper.setBlock(pos, cloth.defaultBlockState());
		ServerPlayer player = connectedPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE, 2));
		helper.useBlock(pos, player);
		helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(pos)).getValue(TableClothBlock.HAS_BE), "the cloth has no block entity");
		TableClothBlockEntity be = helper.getBlockEntity(pos, TableClothBlockEntity.class);
		helper.assertTrue(be.manuallyAddedItems.size() == 1 && be.manuallyAddedItems.get(0).is(Items.APPLE), "the apple was not put on the cloth");
		helper.assertTrue(player.getMainHandItem().getCount() == 1, "the apple was not taken from the hand");

		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.useBlock(pos, player);
		helper.assertTrue(player.getMainHandItem().is(Items.APPLE), "the apple was not taken back");
		helper.assertTrue(!helper.getLevel().getBlockState(helper.absolutePos(pos)).getValue(TableClothBlock.HAS_BE), "the cloth still has a block entity");
		helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(pos)) == null, "the block entity was not removed");
		leave(helper, player);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void tableClothSellsItemsForAShoppingList(GameTestHelper helper) {
		setup(helper, true);
		BlockPos clothPos = new BlockPos(3, 1, 5);
		helper.runAfterDelay(1, () -> stock(helper, new ItemStack(Items.IRON_INGOT, 10)));
		helper.runAfterDelay(10, () -> {
			StockTickerBlockEntity ticker = helper.getBlockEntity(TICKER, StockTickerBlockEntity.class);
			BlockPos tickerPos = helper.absolutePos(TICKER);

			// The owner programs a cloth: 2 iron per purchase
			ServerPlayer owner = connectedPlayer(helper);
			ItemStack clothItem = new ItemStack(StockRegistry.TABLE_CLOTH_ITEMS.get(DyeColor.BLUE));
			new AutoRequestData(PackageOrderWithCrafts.simple(List.of(new BigItemStack(new ItemStack(Items.IRON_INGOT), 2))), "Shop",
				tickerPos, helper.getLevel().dimension().identifier().toString(), false).writeToItem(BlockPos.ZERO, clothItem);
			helper.assertTrue(clothItem.has(StockRegistry.AUTO_REQUEST_DATA), "the cloth item was not programmed");

			TableClothBlock cloth = StockRegistry.TABLE_CLOTHS.get(DyeColor.BLUE);
			helper.setBlock(clothPos, cloth.defaultBlockState());
			BlockPos absolute = helper.absolutePos(clothPos);
			cloth.setPlacedBy(helper.getLevel(), absolute, helper.getLevel().getBlockState(absolute), owner, clothItem);
			TableClothBlockEntity be = helper.getBlockEntity(clothPos, TableClothBlockEntity.class);
			helper.assertTrue(be.isShop() && owner.getUUID().equals(be.owner), "the cloth is no shop");
			helper.assertTrue(be.requestData.targetOffset().equals(tickerPos.subtract(absolute)), "the keeper offset is " + be.requestData.targetOffset());
			be.priceTag.setFilter(new ItemStack(Items.GOLD_INGOT));
			be.priceTag.count = 3;
			helper.assertTrue(be.getPaymentAmount() == 3, "the price is " + be.getPaymentAmount());
			helper.assertTrue(be.getStockLevelForTrade(null) == 5, "the stock level is " + be.getStockLevelForTrade(null));

			// A customer takes a shopping list from the cloth
			ServerPlayer buyer = connectedPlayer(helper);
			buyer.getInventory().setItem(20, new ItemStack(Items.GOLD_INGOT, 10));
			helper.useBlock(clothPos, buyer);
			ItemStack list = buyer.getMainHandItem();
			helper.assertTrue(list.getItem() instanceof ShoppingListItem, "no shopping list: " + list);
			ShoppingList shoppingList = ShoppingListItem.getList(list);
			helper.assertTrue(shoppingList.getPurchases(absolute) == 1 && ticker.behaviour.freqId.equals(shoppingList.shopNetwork()),
				"the list is wrong");
			helper.assertTrue(ShoppingListItem.getAddress(list).equals("Shop"), "the list address is " + ShoppingListItem.getAddress(list));

			// Using the cloth again adds to the list
			helper.useBlock(clothPos, buyer);
			helper.assertTrue(ShoppingListItem.getList(buyer.getMainHandItem()).getPurchases(absolute) == 2, "the second purchase was not added");

			// Handing the list to the keeper pays and orders
			helper.assertTrue(StockTickerInteractionHandler.interactWithLogisticsManagerAt(buyer, helper.getLevel(), tickerPos), "the keeper does not talk");
			helper.assertTrue(buyer.getMainHandItem().isEmpty(), "the list was not handed in");
			helper.assertTrue(countIn(ticker.getReceivedPaymentsHandler(), Items.GOLD_INGOT) == 6,
				"the ticker got " + countIn(ticker.getReceivedPaymentsHandler(), Items.GOLD_INGOT) + " gold");
			int gold = 0;
			for (ItemStack stack : buyer.getInventory().getNonEquipmentItems())
				if (stack.is(Items.GOLD_INGOT))
					gold += stack.getCount();
			helper.assertTrue(gold == 4, "the buyer has " + gold + " gold left");
			assertPackage(helper, "Shop", Items.IRON_INGOT, 4);
			leave(helper, owner);
			leave(helper, buyer);
			helper.succeed();
		});
	}

	private static int shownModels(GameTestHelper helper, BlockPos pos, String part) {
		BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(pos));
		helper.assertTrue(attachment != null, "the cloth has no model");
		int shown = 0;
		for (VirtualElement element : attachment.holder().getElements())
			if (element instanceof ItemDisplayElement display && !display.getItem().isEmpty()) {
				var model = display.getItem().get(DataComponents.ITEM_MODEL);
				if (model != null && model.getPath().contains(part))
					shown++;
			}
		return shown;
	}

	@GameTest(maxTicks = 60)
	public void tableClothModelDrapesAreHiddenWhereNeighboursTouch(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		TableClothBlock cloth = StockRegistry.TABLE_CLOTHS.get(DyeColor.GREEN);
		helper.setBlock(pos, cloth.defaultBlockState());
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(shownModels(helper, pos, "green_flap_") == 4, "a lone cloth shows " + shownModels(helper, pos, "green_flap_") + " drapes");
			helper.assertTrue(shownModels(helper, pos, "green_corner_") == 4, "a lone cloth shows " + shownModels(helper, pos, "green_corner_") + " corners");
			helper.setBlock(pos.east(), cloth.defaultBlockState());
			helper.setBlock(pos.north(), Blocks.STONE);
		});
		helper.runAfterDelay(25, () -> {
			helper.assertTrue(shownModels(helper, pos, "green_flap_") == 2, "the cloth shows " + shownModels(helper, pos, "green_flap_") + " drapes");
			// only the corner between the south and west drapes is left
			helper.assertTrue(shownModels(helper, pos, "green_corner_sw") == 1 && shownModels(helper, pos, "green_corner_") == 1,
				"the cloth shows " + shownModels(helper, pos, "green_corner_") + " corners");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void tableClothModelShowsItsItems(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		helper.setBlock(pos, StockRegistry.TABLE_CLOTHS.get(DyeColor.RED).defaultBlockState());
		ServerPlayer player = connectedPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE, 2));
		helper.useBlock(pos, player);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 2));
		helper.useBlock(pos, player);
		leave(helper, player);
		helper.runAfterDelay(8, () -> {
			BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(pos));
			boolean apple = false, stone = false;
			for (VirtualElement element : attachment.holder().getElements())
				if (element instanceof ItemDisplayElement display) {
					apple |= display.getItem().is(Items.APPLE);
					stone |= display.getItem().is(Items.STONE);
				}
			helper.assertTrue(apple && stone, "the items are not displayed: apple " + apple + ", stone " + stone);
			helper.succeed();
		});
	}
}
