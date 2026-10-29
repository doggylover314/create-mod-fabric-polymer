package dev.createpolymer.logistics.stockTicker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.api.equipment.goggles.IHaveGoggleInformation;
import dev.createpolymer.foundation.item.ItemHelper;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import dev.createpolymer.logistics.BigItemStack;
import dev.createpolymer.logistics.filter.FilterItem;
import dev.createpolymer.logistics.packager.IdentifiedInventory;
import dev.createpolymer.logistics.packager.InventorySummary;
import dev.createpolymer.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import dev.createpolymer.processing.burner.BlazeBurnerBlockEntity;
import dev.createpolymer.registry.feature.StockRegistry;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.Lang;
import dev.createpolymer.util.NBTHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * Create's StockTickerBlockEntity. The client side snapshot of the stock (Create's player-interface feature) is not
 * needed: {@link StockKeeperRequestGui} asks {@link #getRecentSummary()} itself.
 */
public class StockTickerBlockEntity extends StockCheckingBlockEntity implements IHaveGoggleInformation, Clearable {

	/** Create's entity type id of the seat the stock keeper sits on. */
	public static final Identifier SEAT_ID = CreatePolymer.id("seat");

	private static final Codec<Map<UUID, List<Integer>>> HIDDEN_CATEGORIES_CODEC =
		Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT.listOf());

	// Player-interface Feature
	protected String previouslyUsedAddress;
	protected int activeLinks;
	protected List<ItemStack> categories;
	protected Map<UUID, List<Integer>> hiddenCategoriesByPlayer;

	// Shop feature
	protected PaymentInventory receivedPayments;

	public StockTickerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		previouslyUsedAddress = "";
		receivedPayments = new PaymentInventory();
		categories = new ArrayList<>();
		hiddenCategoriesByPlayer = new HashMap<>();
	}

	/** Create's SmartInventory(27, this, 64, false). */
	public class PaymentInventory extends ItemStackHandler {
		public PaymentInventory() {
			super(27);
		}

		@Override
		protected void onContentsChanged(int slot) {
			notifyUpdate();
		}

		public boolean isEmpty() {
			for (int i = 0; i < getSlots(); i++)
				if (!getStackInSlot(i).isEmpty())
					return false;
			return true;
		}

		public void clearContent() {
			for (int i = 0; i < getSlots(); i++)
				setStackInSlot(i, ItemStack.EMPTY);
		}
	}

	public static void registerCapabilities() {
		ItemHandlerLookup.registerBlockEntity(StockRegistry.STOCK_TICKER_BLOCK_ENTITY, (be, side) -> be.receivedPayments);
	}

	public IItemHandler getReceivedPaymentsHandler() {
		return receivedPayments;
	}

	public PaymentInventory getReceivedPayments() {
		return receivedPayments;
	}

	public String getPreviouslyUsedAddress() {
		return previouslyUsedAddress;
	}

	public void setPreviouslyUsedAddress(String address) {
		previouslyUsedAddress = address;
		setChanged();
	}

	public List<ItemStack> getCategories() {
		return categories;
	}

	public void setCategories(List<ItemStack> categories) {
		this.categories = categories;
		notifyUpdate();
	}

	public List<Integer> getHiddenCategories(UUID player) {
		return hiddenCategoriesByPlayer.getOrDefault(player, List.of());
	}

	public void setHiddenCategories(UUID player, List<Integer> indices) {
		if (indices.isEmpty()) {
			hiddenCategoriesByPlayer.remove(player);
		} else {
			hiddenCategoriesByPlayer.put(player, indices);
			notifyUpdate();
		}
	}

	@Override
	public boolean broadcastPackageRequest(RequestType type, PackageOrderWithCrafts order, @Nullable IdentifiedInventory ignoredHandler, String address) {
		boolean result = super.broadcastPackageRequest(type, order, ignoredHandler, address);
		previouslyUsedAddress = address;
		notifyUpdate();
		return result;
	}

	@Override
	public InventorySummary getRecentSummary() {
		InventorySummary recentSummary = super.getRecentSummary();
		int contributingLinks = recentSummary.contributingLinks;
		if (activeLinks != contributingLinks && !isRemoved()) {
			activeLinks = contributingLinks;
			sendData();
		}
		return recentSummary;
	}

	/** The entries of the stock sorted into the categories of this ticker; the last list holds the unsorted items. */
	public List<List<BigItemStack>> sortIntoCategories(InventorySummary summary) {
		List<BigItemStack> remaining = new ArrayList<>(summary.getStacksByCount());
		List<List<BigItemStack>> sorted = new ArrayList<>();

		for (ItemStack filter : categories) {
			List<BigItemStack> inCategory = new ArrayList<>();
			if (!filter.isEmpty()) {
				dev.createpolymer.logistics.filter.FilterItemStack filterItemStack =
					dev.createpolymer.logistics.filter.FilterItemStack.of(filter);
				for (var iterator = remaining.iterator(); iterator.hasNext();) {
					BigItemStack bigStack = iterator.next();
					if (!filterItemStack.test(level, bigStack.stack))
						continue;
					inCategory.add(bigStack);
					iterator.remove();
				}
			}
			sorted.add(inCategory);
		}

		sorted.add(remaining);
		return sorted;
	}

	@Override
	protected void write(ValueOutput output) {
		super.write(output);
		output.putString("PreviousAddress", previouslyUsedAddress);
		receivedPayments.serialize(output.child("ReceivedPayments"));
		NBTHelper.writeItemList(output, "Categories", categories);
		output.store("HiddenCategories", HIDDEN_CATEGORIES_CODEC, hiddenCategoriesByPlayer);
	}

	@Override
	protected void read(ValueInput input) {
		super.read(input);
		previouslyUsedAddress = input.getStringOr("PreviousAddress", "");
		receivedPayments.deserialize(input.childOrEmpty("ReceivedPayments"));
		categories = new ArrayList<>(NBTHelper.readItemList(input, "Categories"));
		categories.removeIf(stack -> !stack.isEmpty() && !(stack.getItem() instanceof FilterItem));
		hiddenCategoriesByPlayer.clear();
		input.read("HiddenCategories", HIDDEN_CATEGORIES_CODEC)
			.ifPresent(map -> map.forEach((id, indices) -> hiddenCategoriesByPlayer.put(id, new ArrayList<>(indices))));
	}

	/** Create's check: a mob sitting on a seat next to the ticker, or a blaze burner next to it. */
	public boolean isKeeperPresent() {
		for (int yOffset : Iterate.zeroAndOne) {
			for (Direction side : Iterate.horizontalDirections) {
				BlockPos seatPos = worldPosition.below(yOffset)
					.relative(side);
				for (Entity seatEntity : level.getEntitiesOfClass(Entity.class, new AABB(seatPos),
					StockTickerBlockEntity::isSeat))
					if (seatEntity.isVehicle())
						return true;
				if (yOffset == 0 && level.getBlockEntity(seatPos) instanceof BlazeBurnerBlockEntity)
					return true;
			}
		}
		return false;
	}

	/** Whether the entity is one of Create's seats. */
	public static boolean isSeat(Entity entity) {
		return SEAT_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
	}

	/** The mob that sits next to the ticker, if any. */
	@Nullable
	public LivingEntity findKeeper() {
		for (int yOffset : Iterate.zeroAndOne) {
			for (Direction side : Iterate.horizontalDirections) {
				BlockPos seatPos = worldPosition.below(yOffset)
					.relative(side);
				for (Entity seatEntity : level.getEntitiesOfClass(Entity.class, new AABB(seatPos),
					StockTickerBlockEntity::isSeat))
					if (!seatEntity.getPassengers()
						.isEmpty() && seatEntity.getPassengers()
							.get(0) instanceof LivingEntity keeper)
						return keeper;
			}
		}
		return null;
	}

	/** Create's addToTooltip, for the goggles. The goggle line has no player to check administration rights against. */
	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		if (receivedPayments.isEmpty())
			return false;

		Lang.builder(CreatePolymer.MOD_ID)
			.translate("stock_ticker.contains_payments")
			.style(ChatFormatting.WHITE)
			.forGoggles(tooltip);

		InventorySummary summary = new InventorySummary();
		for (int i = 0; i < receivedPayments.getSlots(); i++)
			summary.add(receivedPayments.getStackInSlot(i));
		for (BigItemStack entry : summary.getStacksByCount())
			Lang.builder(CreatePolymer.MOD_ID)
				.text(entry.stack.getHoverName()
					.getString() + " x" + entry.count)
				.style(ChatFormatting.GREEN)
				.forGoggles(tooltip);

		Lang.builder(CreatePolymer.MOD_ID)
			.translate("stock_ticker.click_to_retrieve")
			.style(ChatFormatting.GRAY)
			.forGoggles(tooltip);
		return true;
	}

	@Override
	public void clearContent() {
		categories.clear();
		receivedPayments.clearContent();
	}

	@Override
	public void destroy() {
		ItemHelper.dropContents(level, worldPosition, receivedPayments);
		for (ItemStack filter : categories)
			if (!filter.isEmpty() && filter.getItem() instanceof FilterItem)
				Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
					filter);
		super.destroy();
	}

	/** Create's WiFiEffectPacket on the ticker: sound and a burst of wifi. */
	public void playEffect() {
		StockSounds.stockLink(level, worldPosition, 1.0f, 1.0f);
		if (level instanceof ServerLevel serverLevel)
			serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
				worldPosition.getX() + .5, worldPosition.getY() + 1, worldPosition.getZ() + .5, 6, .2, .2, .2, .05);
	}
}
