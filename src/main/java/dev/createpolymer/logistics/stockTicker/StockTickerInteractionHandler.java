package dev.createpolymer.logistics.stockTicker;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.logistics.BigItemStack;
import dev.createpolymer.logistics.packager.InventorySummary;
import dev.createpolymer.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import dev.createpolymer.logistics.tableCloth.ShoppingListItem;
import dev.createpolymer.logistics.tableCloth.ShoppingListItem.ShoppingList;
import dev.createpolymer.foundation.item.handler.ItemHandlerHelper;
import dev.createpolymer.processing.burner.BlazeBurnerBlock;
import dev.createpolymer.processing.burner.BlazeBurnerBlockEntity;
import dev.createpolymer.registry.feature.PackagesRegistry;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.StockRegistry;
import dev.createpolymer.util.Couple;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.Lang;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create's StockTickerInteractionHandler: clicking the mob that sits next to a stock ticker (or a blaze burner next to
 * one) talks to the stock keeper, either with a shopping list in hand or to open the request screen.
 */
public class StockTickerInteractionHandler {

	public static void init() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (hand != InteractionHand.MAIN_HAND)
				return InteractionResult.PASS;
			return interactWithLogisticsManager(player, level, entity);
		});

		// Create's BlazeBurnerBlock.useItemOn does this for a burner that is a stock keeper
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || player.isShiftKeyDown())
				return InteractionResult.PASS;
			BlockPos pos = hitResult.getBlockPos();
			BlockState state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof BlazeBurnerBlock))
				return InteractionResult.PASS;
			if (!(level.getBlockEntity(pos) instanceof BlazeBurnerBlockEntity burner))
				return InteractionResult.PASS;
			if (player.getMainHandItem()
				.is(ModItems.GOGGLES))
				return InteractionResult.PASS;
			StockTickerBlockEntity stockTicker = getStockTicker(level, pos);
			if (stockTicker == null)
				return InteractionResult.PASS;
			interactWithLogisticsManagerAt(player, level, stockTicker.getBlockPos());
			return InteractionResult.SUCCESS;
		});
	}

	/** Create's BlazeBurnerBlockEntity.getStockTicker. */
	@Nullable
	public static StockTickerBlockEntity getStockTicker(Level level, BlockPos pos) {
		for (Direction direction : Iterate.horizontalDirections) {
			if (!level.isLoaded(pos))
				return null;
			BlockState blockState = level.getBlockState(pos.relative(direction));
			if (!(blockState.getBlock() instanceof StockTickerBlock))
				continue;
			if (level.getBlockEntity(pos.relative(direction)) instanceof StockTickerBlockEntity stbe)
				return stbe;
		}
		return null;
	}

	public static InteractionResult interactWithLogisticsManager(Player player, Level level, Entity entity) {
		if (player == null || entity == null)
			return InteractionResult.PASS;
		if (player.isSpectator())
			return InteractionResult.PASS;

		BlockPos targetPos = getStockTickerPosition(entity);
		if (targetPos == null)
			return InteractionResult.PASS;

		if (interactWithLogisticsManagerAt(player, level, targetPos))
			return InteractionResult.SUCCESS;
		return InteractionResult.PASS;
	}

	public static boolean interactWithLogisticsManagerAt(Player player, Level level, BlockPos targetPos) {
		ItemStack mainHandItem = player.getMainHandItem();

		if (mainHandItem.getItem() instanceof ShoppingListItem) {
			interactWithShop(player, level, targetPos, mainHandItem);
			return true;
		}

		if (level.isClientSide())
			return true;
		if (!(level.getBlockEntity(targetPos) instanceof StockTickerBlockEntity stbe))
			return false;

		if (!stbe.behaviour.mayInteract(player)) {
			player.sendOverlayMessage(Lang.builder(CreatePolymer.MOD_ID)
				.translate("stock_keeper.locked")
				.style(ChatFormatting.RED)
				.component());
			return true;
		}

		if (player instanceof ServerPlayer sp)
			new StockKeeperRequestGui(sp, stbe).open();

		return true;
	}

	private static void deny(Level level, Player player) {
		StockSounds.deny(level, player.blockPosition(), 1, 1);
	}

	private static void status(Player player, String key) {
		player.sendOverlayMessage(Lang.builder(CreatePolymer.MOD_ID)
			.translate(key)
			.style(ChatFormatting.RED)
			.component());
	}

	private static void interactWithShop(Player player, Level level, BlockPos targetPos, ItemStack mainHandItem) {
		if (level.isClientSide())
			return;
		if (!(level.getBlockEntity(targetPos) instanceof StockTickerBlockEntity tickerBE))
			return;

		ShoppingList list = ShoppingListItem.getList(mainHandItem);
		if (list == null)
			return;

		if (!tickerBE.behaviour.freqId.equals(list.shopNetwork())) {
			deny(level, player);
			status(player, "stock_keeper.wrong_network");
			return;
		}

		Couple<InventorySummary> bakeEntries = list.bakeEntries(level, null);
		InventorySummary paymentEntries = bakeEntries.getSecond();
		InventorySummary orderEntries = bakeEntries.getFirst();
		PackageOrder order = new PackageOrder(orderEntries.getStacksByCount());

		// Must be up-to-date
		tickerBE.getAccurateSummary();

		// Check stock levels
		InventorySummary recentSummary = tickerBE.getRecentSummary();
		for (BigItemStack entry : order.stacks()) {
			if (recentSummary.getCountOf(entry.stack) >= entry.count)
				continue;

			deny(level, player);
			status(player, "stock_keeper.stock_level_too_low");
			return;
		}

		// Check space in stock ticker
		int occupiedSlots = 0;
		for (BigItemStack entry : paymentEntries.getStacksByCount())
			occupiedSlots += Mth.ceil(entry.count / (float) entry.stack.getMaxStackSize());
		for (int i = 0; i < tickerBE.getReceivedPayments()
			.getSlots(); i++)
			if (tickerBE.getReceivedPayments()
				.getStackInSlot(i)
				.isEmpty())
				occupiedSlots--;

		if (occupiedSlots > 0) {
			deny(level, player);
			status(player, "stock_keeper.cash_register_full");
			return;
		}

		// Transfer payment to stock ticker
		for (boolean simulate : Iterate.trueAndFalse) {
			InventorySummary tally = paymentEntries.copy();
			List<ItemStack> toTransfer = new ArrayList<>();

			for (int i = 0; i < player.getInventory()
				.getNonEquipmentItems()
				.size(); i++) {
				ItemStack item = player.getInventory()
					.getItem(i);
				if (item.isEmpty())
					continue;
				int countOf = tally.getCountOf(item);
				if (countOf == 0)
					continue;
				int toRemove = Math.min(item.getCount(), countOf);
				tally.add(item, -toRemove);

				if (simulate)
					continue;

				int newStackSize = item.getCount() - toRemove;
				player.getInventory()
					.setItem(i, newStackSize == 0 ? ItemStack.EMPTY : item.copyWithCount(newStackSize));
				toTransfer.add(item.copyWithCount(toRemove));
			}

			if (simulate && tally.getTotalCount() != 0) {
				deny(level, player);
				status(player, "stock_keeper.too_broke");
				return;
			}

			if (simulate)
				continue;

			toTransfer.forEach(s -> ItemHandlerHelper.insertItemStacked(tickerBE.getReceivedPayments(), s, false));
		}

		tickerBE.broadcastPackageRequest(RequestType.PLAYER, order, null, ShoppingListItem.getAddress(mainHandItem));
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		if (!order.isEmpty())
			StockSounds.stockTickerTrade(level, tickerBE.getBlockPos());
	}

	@Nullable
	public static BlockPos getStockTickerPosition(Entity entity) {
		Entity rootVehicle = entity.getRootVehicle();
		if (!StockTickerBlockEntity.isSeat(rootVehicle))
			return null;
		if (!(entity instanceof LivingEntity))
			return null;
		if (entity.getType() == PackagesRegistry.PACKAGE)
			return null;

		BlockPos pos = entity.blockPosition();
		int stations = 0;
		BlockPos targetPos = null;

		for (Direction d : Iterate.horizontalDirections) {
			for (int y : Iterate.zeroAndOne) {
				BlockPos workstationPos = pos.relative(d)
					.above(y);
				if (!(entity.level()
					.getBlockState(workstationPos)
					.getBlock() instanceof StockTickerBlock))
					continue;
				targetPos = workstationPos;
				stations++;
			}
		}

		if (stations != 1)
			return null;
		return targetPos;
	}

}
