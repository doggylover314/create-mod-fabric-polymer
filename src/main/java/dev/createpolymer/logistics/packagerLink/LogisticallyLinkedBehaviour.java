package dev.createpolymer.logistics.packagerLink;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jspecify.annotations.Nullable;

import com.google.common.cache.Cache;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BehaviourType;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import dev.createpolymer.logistics.packager.IdentifiedInventory;
import dev.createpolymer.logistics.packager.InventorySummary;
import dev.createpolymer.logistics.packager.PackagerBlockEntity;
import dev.createpolymer.logistics.packager.PackagingRequest;
import dev.createpolymer.logistics.stockTicker.PackageOrderWithCrafts;
import dev.createpolymer.registry.feature.PackagesRegistry;
import dev.createpolymer.util.Lang;
import dev.createpolymer.util.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class LogisticallyLinkedBehaviour extends BlockEntityBehaviour {

	public static final BehaviourType<LogisticallyLinkedBehaviour> TYPE = new BehaviourType<>();

	public static final AtomicInteger LINK_ID_GENERATOR = new AtomicInteger();
	public int linkId; // Runtime context, not saved to disk

	public int redstonePower;
	public UUID freqId;

	private boolean addedGlobally = false;
	private boolean loadedGlobally = false;
	private boolean global = false;

	public static enum RequestType {
		RESTOCK, REDSTONE, PLAYER
	}

	private static final Cache<UUID, Cache<Integer, WeakReference<LogisticallyLinkedBehaviour>>> LINKS =
		new TickBasedCache<>(20, true);

	public LogisticallyLinkedBehaviour(SmartBlockEntity be, boolean global) {
		super(be);
		this.global = global;
		linkId = LINK_ID_GENERATOR.getAndIncrement();
		freqId = UUID.randomUUID();
	}

	public static Collection<LogisticallyLinkedBehaviour> getAllPresent(UUID freq, boolean sortByPriority) {
		Cache<Integer, WeakReference<LogisticallyLinkedBehaviour>> cache = LINKS.getIfPresent(freq);
		if (cache == null)
			return Collections.emptyList();
		Stream<LogisticallyLinkedBehaviour> stream = new LinkedList<>(cache.asMap()
			.values()).stream()
			.map(WeakReference::get)
			.filter(LogisticallyLinkedBehaviour::isValidLink);

		if (sortByPriority)
			stream = stream.sorted((e1, e2) -> Integer.compare(e1.redstonePower, e2.redstonePower));

		return stream.toList();
	}

	public static void keepAlive(LogisticallyLinkedBehaviour behaviour) {
		if (behaviour.redstonePower == 15)
			return;
		try {
			Cache<Integer, WeakReference<LogisticallyLinkedBehaviour>> cache =
				LINKS.get(behaviour.freqId, () -> new TickBasedCache<>(400, false));

			if (cache == null)
				return;

			WeakReference<LogisticallyLinkedBehaviour> reference =
				cache.get(behaviour.linkId, () -> new WeakReference<>(behaviour));
			cache.put(behaviour.linkId, reference.get() != behaviour ? new WeakReference<>(behaviour) : reference);

		} catch (ExecutionException e) {
			e.printStackTrace();
		}
	}

	public static void remove(LogisticallyLinkedBehaviour behaviour) {
		Cache<Integer, WeakReference<LogisticallyLinkedBehaviour>> cache = LINKS.getIfPresent(behaviour.freqId);
		if (cache != null)
			cache.invalidate(behaviour.linkId);
	}

	//

	@Override
	public void unload() {
		if (loadedGlobally && global && getWorld() != null)
			PackagesRegistry.LOGISTICS.linkInvalidated(freqId, getGlobalPos());
		super.unload();
		remove(this);
	}

	@Override
	public void lazyTick() {
		keepAlive(this);
	}

	@Override
	public void initialize() {
		super.initialize();
		if (getWorld().isClientSide())
			return;

		if (!loadedGlobally && global) {
			loadedGlobally = true;
			PackagesRegistry.LOGISTICS.linkLoaded(freqId, getGlobalPos());
			// Call keepAlive regardless of redstone power.
			// Otherwise, when no redstone power is present
			// keepAlive won't be called until next lazy tick.
			keepAlive(this);
		}

		if (!addedGlobally && global) {
			addedGlobally = true;
			blockEntity.setChanged();
			if (blockEntity instanceof PackagerLinkBlockEntity plbe)
				PackagesRegistry.LOGISTICS.linkAdded(freqId, getGlobalPos(), plbe.placedBy);
		}

	}

	private GlobalPos getGlobalPos() {
		return GlobalPos.of(getWorld().dimension(), getPos());
	}

	@Override
	public void destroy() {
		super.destroy();
		if (addedGlobally && global && getWorld() != null)
			PackagesRegistry.LOGISTICS.linkRemoved(freqId, getGlobalPos());
	}

	public void redstonePowerChanged(int power) {
		if (power == redstonePower)
			return;
		redstonePower = power;
		blockEntity.setChanged();

		if (power == 15)
			remove(this);
		else
			keepAlive(this);
	}

	@Nullable
	public Pair<PackagerBlockEntity, PackagingRequest> processRequest(ItemStack stack, int amount, String address,
		int linkIndex, MutableBoolean finalLink, int orderId, @Nullable PackageOrderWithCrafts context,
		@Nullable IdentifiedInventory ignoredHandler) {

		if (blockEntity instanceof PackagerLinkBlockEntity plbe)
			return plbe.processRequest(stack, amount, address, linkIndex, finalLink, orderId, context, ignoredHandler);

		return null;
	}

	public InventorySummary getSummary(@Nullable IdentifiedInventory ignoredHandler) {
		if (blockEntity instanceof PackagerLinkBlockEntity plbe)
			return plbe.fetchSummaryFromPackager(ignoredHandler);
		return InventorySummary.EMPTY;
	}

	public void deductFromAccurateSummary(ItemStackHandler packageContents) {
		InventorySummary summary = LogisticsManager.ACCURATE_SUMMARIES.getIfPresent(freqId);
		if (summary == null)
			return;
		for (int i = 0; i < packageContents.getSlots(); i++) {
			ItemStack orderedStack = packageContents.getStackInSlot(i);
			if (orderedStack.isEmpty())
				continue;
			summary.add(orderedStack, -Math.min(summary.getCountOf(orderedStack), orderedStack.getCount()));
		}
	}

	//

	public boolean mayInteract(Player player) {
		return PackagesRegistry.LOGISTICS.mayInteract(freqId, player);
	}

	public boolean mayInteractMessage(Player player) {
		boolean mayInteract = PackagesRegistry.LOGISTICS.mayInteract(freqId, player);
		if (!mayInteract)
			player.sendOverlayMessage(Lang.builder(CreatePolymer.MOD_ID)
				.translate("logistically_linked.protected")
				.style(ChatFormatting.RED)
				.component());
		return mayInteract;
	}

	public boolean mayAdministrate(Player player) {
		return PackagesRegistry.LOGISTICS.mayAdministrate(freqId, player);
	}

	public static boolean isValidLink(LogisticallyLinkedBehaviour link) {
		return link != null && !link.blockEntity.isRemoved();
	}

	@Override
	public boolean isSafeNBT() {
		return true;
	}

	@Override
	public void writeSafe(ValueOutput output) {
		output.store("Freq", UUIDUtil.CODEC, freqId);
	}

	@Override
	public void write(ValueOutput output) {
		super.write(output);
		output.store("Freq", UUIDUtil.CODEC, freqId);
		output.putInt("Power", redstonePower);
		output.putBoolean("Added", addedGlobally);
	}

	@Override
	public void read(ValueInput input) {
		super.read(input);
		input.read("Freq", UUIDUtil.CODEC).ifPresent(id -> freqId = id);
		redstonePower = input.getIntOr("Power", 0);
		addedGlobally = input.getBooleanOr("Added", false);
	}

	@Override
	public BehaviourType<?> getType() {
		return TYPE;
	}

}
