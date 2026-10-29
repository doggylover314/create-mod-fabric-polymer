package dev.createpolymer.logistics.tableCloth;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.logistics.BigItemStack;
import dev.createpolymer.logistics.packager.InventorySummary;
import dev.createpolymer.logistics.redstoneRequester.AutoRequestData;
import dev.createpolymer.logistics.stockTicker.StockSounds;
import dev.createpolymer.logistics.stockTicker.StockTickerBlockEntity;
import dev.createpolymer.logistics.tableCloth.ShoppingListItem.ShoppingList;
import dev.createpolymer.util.IntAttached;
import dev.createpolymer.util.Lang;
import dev.createpolymer.util.NBTHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.Clearable;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class TableClothBlockEntity extends SmartBlockEntity implements Clearable {

	public AutoRequestData requestData;
	public List<ItemStack> manuallyAddedItems;
	public UUID owner;

	public Direction facing;
	public boolean sideOccluded;
	public FilteringBehaviour priceTag;

	private List<ItemStack> renderedItemsForShop;

	public TableClothBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		manuallyAddedItems = new ArrayList<>();
		requestData = new AutoRequestData();
		owner = null;
		facing = Direction.SOUTH;
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		behaviours.add(priceTag = new TableClothFilteringBehaviour(this));
	}

	public List<ItemStack> getItemsForRender() {
		if (isShop()) {
			if (renderedItemsForShop == null)
				renderedItemsForShop = requestData.encodedRequest().stacks()
					.stream()
					.map(b -> b.stack)
					.limit(4)
					.toList();
			return renderedItemsForShop;
		}

		return manuallyAddedItems;
	}

	public void invalidateItemsForRender() {
		renderedItemsForShop = null;
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		BlockPos relativePos = worldPosition.relative(facing);
		sideOccluded = level.getBlockState(relativePos)
			.getBlock() instanceof TableClothBlock
			|| Block.isFaceFull(level.getBlockState(relativePos.below())
				.getOcclusionShape(), facing.getOpposite());
	}

	public boolean isShop() {
		return !requestData.encodedRequest()
			.isEmpty();
	}

	public InteractionResult use(Player player, BlockHitResult ray) {
		if (isShop())
			return useShop(player);

		ItemStack heldItem = player.getItemInHand(InteractionHand.MAIN_HAND);

		if (heldItem.isEmpty()) {
			if (manuallyAddedItems.isEmpty())
				return InteractionResult.SUCCESS;
			player.setItemInHand(InteractionHand.MAIN_HAND, manuallyAddedItems.remove(manuallyAddedItems.size() - 1));
			level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.5f, 1f);

			if (manuallyAddedItems.isEmpty()) {
				level.setBlock(worldPosition, getBlockState().setValue(TableClothBlock.HAS_BE, false), Block.UPDATE_ALL);
				// The block entity stays when only a block state property changes
				level.removeBlockEntity(worldPosition);
			} else
				notifyUpdate();

			return InteractionResult.SUCCESS;
		}

		if (manuallyAddedItems.size() >= 4)
			return InteractionResult.SUCCESS;

		level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.5f, 1f);
		manuallyAddedItems.add(heldItem.copyWithCount(1));
		facing = player.getDirection()
			.getOpposite();
		heldItem.shrink(1);
		if (heldItem.isEmpty())
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		notifyUpdate();
		return InteractionResult.SUCCESS;
	}

	public boolean targetsPriceTag(Player player, BlockHitResult ray) {
		return priceTag != null && priceTag.mayInteract(player) && priceTag.getSlotPositioning()
			.testHit(level, worldPosition, getBlockState(), ray.getLocation()
				.subtract(Vec3.atLowerCornerOf(worldPosition)));
	}

	private static void send(Player player, String key, @Nullable ChatFormatting style) {
		var builder = Lang.builder(CreatePolymer.MOD_ID)
			.translate(key);
		if (style != null)
			builder.style(style);
		player.sendOverlayMessage(builder.component());
	}

	public InteractionResult useShop(Player player) {
		ItemStack itemInHand = player.getItemInHand(InteractionHand.MAIN_HAND);
		ItemStack prevListItem = ItemStack.EMPTY;
		boolean addOntoList = false;

		// Remove other lists from inventory
		for (int i = 0; i < 9; i++) {
			ItemStack item = player.getInventory()
				.getItem(i);
			if (!(item.getItem() instanceof ShoppingListItem))
				continue;
			prevListItem = item;
			addOntoList = true;
			player.getInventory()
				.setItem(i, ItemStack.EMPTY);
		}

		// add onto existing list if in hand
		if (itemInHand.getItem() instanceof ShoppingListItem) {
			prevListItem = itemInHand;
			addOntoList = true;
		}

		if (!itemInHand.isEmpty() && !addOntoList) {
			send(player, "stock_keeper.shopping_list_empty_hand", null);
			StockSounds.deny(level, worldPosition, 0.5f, 1);
			return InteractionResult.SUCCESS;
		}

		if (getPaymentItem().isEmpty()) {
			send(player, "stock_keeper.no_price_set", null);
			StockSounds.deny(level, worldPosition, 0.5f, 1);
			return InteractionResult.SUCCESS;
		}

		UUID tickerID = null;
		BlockPos tickerPos = requestData.targetOffset()
			.offset(worldPosition);
		if (level.getBlockEntity(tickerPos) instanceof StockTickerBlockEntity stbe && stbe.isKeeperPresent())
			tickerID = stbe.behaviour.freqId;

		int stockLevel = getStockLevelForTrade(ShoppingListItem.getList(prevListItem));

		if (tickerID == null) {
			send(player, "stock_keeper.keeper_missing", ChatFormatting.RED);
			StockSounds.deny(level, worldPosition, 0.5f, 1);
			return InteractionResult.SUCCESS;
		}

		if (stockLevel == 0) {
			send(player, "stock_keeper.out_of_stock", ChatFormatting.RED);
			StockSounds.deny(level, worldPosition, 0.5f, 1);
			if (!prevListItem.isEmpty()) {
				if (player.getItemInHand(InteractionHand.MAIN_HAND)
					.isEmpty())
					player.setItemInHand(InteractionHand.MAIN_HAND, prevListItem);
				else
					player.getInventory()
						.placeItemBackInInventory(prevListItem, Prediction.SERVER_ONLY);
			}

			return InteractionResult.SUCCESS;
		}

		ShoppingList list = new ShoppingList(new ArrayList<>(), owner, tickerID);

		if (addOntoList) {
			ShoppingList prevList = ShoppingListItem.getList(prevListItem)
				.duplicate();
			if (owner.equals(prevList.shopOwner()) && tickerID.equals(prevList.shopNetwork()))
				list = prevList;
			else
				addOntoList = false;
		}

		if (list.getPurchases(worldPosition) >= stockLevel) {
			for (IntAttached<BlockPos> entry : list.purchases())
				if (worldPosition.equals(entry.getValue()))
					entry.setFirst(Math.min(stockLevel, entry.getFirst()));

			send(player, "stock_keeper.limited_stock", ChatFormatting.RED);
		} else {
			StockSounds.confirm2(level, worldPosition, 0.5f, 1.0f);

			ShoppingList.Mutable mutable = new ShoppingList.Mutable(list);
			mutable.addPurchases(worldPosition, 1);
			list = mutable.toImmutable();

			if (!addOntoList) {
				player.sendOverlayMessage(Lang.builder(CreatePolymer.MOD_ID)
					.translate("stock_keeper.use_list_to_add_purchases")
					.color(0xeeeeee)
					.component());
				level.playSound(null, worldPosition, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1, 1.5f);
			}
		}

		ItemStack newListItem = ShoppingListItem.saveList(new ItemStack(dev.createpolymer.registry.feature.StockRegistry.SHOPPING_LIST_ITEM),
			list, requestData.encodedTargetAddress());

		if (player.getItemInHand(InteractionHand.MAIN_HAND)
			.isEmpty())
			player.setItemInHand(InteractionHand.MAIN_HAND, newListItem);
		else
			player.getInventory()
				.placeItemBackInInventory(newListItem, Prediction.SERVER_ONLY);

		return InteractionResult.SUCCESS;
	}

	public int getStockLevelForTrade(@Nullable ShoppingList otherPurchases) {
		BlockPos tickerPos = requestData.targetOffset()
			.offset(worldPosition);
		if (!(level.getBlockEntity(tickerPos) instanceof StockTickerBlockEntity stbe))
			return 0;

		InventorySummary recentSummary = stbe.getRecentSummary();

		InventorySummary modifierSummary = new InventorySummary();
		if (otherPurchases != null)
			modifierSummary = otherPurchases.bakeEntries(level, worldPosition)
				.getFirst();

		int smallestQuotient = Integer.MAX_VALUE;
		for (BigItemStack entry : requestData.encodedRequest()
			.stacks())
			if (entry.count > 0)
				smallestQuotient = Math.min(smallestQuotient,
					(recentSummary.getCountOf(entry.stack) - modifierSummary.getCountOf(entry.stack)) / entry.count);

		return smallestQuotient;
	}

	@Override
	protected void write(ValueOutput output) {
		super.write(output);
		NBTHelper.writeItemList(output, "Items", manuallyAddedItems);
		output.putInt("Facing", facing.get2DDataValue());
		output.store("RequestData", AutoRequestData.CODEC, requestData);
		if (owner != null)
			output.store("OwnerUUID", UUIDUtil.CODEC, owner);
	}

	@Override
	protected void read(ValueInput input) {
		super.read(input);
		manuallyAddedItems = new ArrayList<>(NBTHelper.readItemList(input, "Items"));
		requestData = input.read("RequestData", AutoRequestData.CODEC)
			.orElse(new AutoRequestData());
		owner = input.read("OwnerUUID", UUIDUtil.CODEC)
			.orElse(null);
		facing = Direction.from2DDataValue(Mth.positiveModulo(input.getIntOr("Facing", 0), 4));
		invalidateItemsForRender();
	}

	@Override
	public void destroy() {
		super.destroy();
		manuallyAddedItems.forEach(stack -> Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(),
			worldPosition.getZ(), stack));
		manuallyAddedItems.clear();
	}

	public ItemStack getPaymentItem() {
		return priceTag.getFilter();
	}

	public int getPaymentAmount() {
		return priceTag.getFilter()
			.isEmpty() ? 1 : priceTag.count;
	}

	@Override
	public void clearContent() {
		manuallyAddedItems.clear();
	}
}
