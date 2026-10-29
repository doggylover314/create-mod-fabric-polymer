package dev.createpolymer.contraptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.ImmutableMap;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.api.contraption.storage.fluid.MountedFluidStorage;
import dev.createpolymer.api.contraption.storage.fluid.MountedFluidStorageType;
import dev.createpolymer.api.contraption.storage.fluid.MountedFluidStorageWrapper;
import dev.createpolymer.api.contraption.storage.item.MountedItemStorage;
import dev.createpolymer.api.contraption.storage.item.MountedItemStorageType;
import dev.createpolymer.api.contraption.storage.item.MountedItemStorageWrapper;
import dev.createpolymer.foundation.item.handler.CombinedInvWrapper;
import dev.createpolymer.foundation.item.handler.IItemHandlerModifiable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/**
 * Ported from Create's MountedStorageManager. The client sync (SyncedMountedStorage, sync packets, interactable
 * positions) and the legacy storage formats are gone: there is no client side.
 */
public class MountedStorageManager {
	// builders used during assembly, null afterward
	// ImmutableMap.Builder is not used because it will throw with duplicate keys, not override them
	private Map<BlockPos, MountedItemStorage> itemsBuilder;
	private Map<BlockPos, MountedFluidStorage> fluidsBuilder;

	// built data structures after assembly, null before
	private ImmutableMap<BlockPos, MountedItemStorage> allItemStorages;
	// different from allItemStorages, does not contain internal ones
	protected MountedItemStorageWrapper items;
	@Nullable
	protected MountedItemStorageWrapper fuelItems;
	protected MountedFluidStorageWrapper fluids;

	private List<IItemHandlerModifiable> externalHandlers;
	protected CombinedInvWrapper allItems;

	public MountedStorageManager() {
		this.reset();
	}

	public void initialize() {
		if (this.isInitialized()) {
			// the contraption can be deserialized before its entity; both initialize the storage
			return;
		}

		this.allItemStorages = ImmutableMap.copyOf(this.itemsBuilder);

		this.items = new MountedItemStorageWrapper(subMap(this.allItemStorages, this::isExposed));

		this.allItems = this.items;
		this.itemsBuilder = null;

		ImmutableMap<BlockPos, MountedItemStorage> fuelMap = subMap(this.allItemStorages, this::canUseForFuel);
		this.fuelItems = fuelMap.isEmpty() ? null : new MountedItemStorageWrapper(fuelMap);

		ImmutableMap<BlockPos, MountedFluidStorage> fluids = ImmutableMap.copyOf(this.fluidsBuilder);
		this.fluids = new MountedFluidStorageWrapper(fluids);
		this.fluidsBuilder = null;
	}

	private boolean isExposed(MountedItemStorage storage) {
		return !storage.type.isInternal();
	}

	private boolean canUseForFuel(MountedItemStorage storage) {
		return this.isExposed(storage) && !storage.type.isFuelBlacklisted();
	}

	private boolean isInitialized() {
		return this.itemsBuilder == null;
	}

	private void assertInitialized() {
		if (!this.isInitialized()) {
			throw new IllegalStateException("MountedStorageManager is uninitialized");
		}
	}

	protected void reset() {
		this.allItemStorages = null;
		this.items = null;
		this.fuelItems = null;
		this.fluids = null;
		this.externalHandlers = new ArrayList<>();
		this.allItems = null;
		this.itemsBuilder = new HashMap<>();
		this.fluidsBuilder = new HashMap<>();
	}

	public void addBlock(Level level, BlockState state, BlockPos globalPos, BlockPos localPos, @Nullable BlockEntity be) {
		MountedItemStorageType<?> itemType = MountedItemStorageType.REGISTRY.get(state.getBlock());
		if (itemType != null) {
			MountedItemStorage storage = itemType.mount(level, state, globalPos, be);
			if (storage != null) {
				this.addStorage(storage, localPos);
			}
		}

		MountedFluidStorageType<?> fluidType = MountedFluidStorageType.REGISTRY.get(state.getBlock());
		if (fluidType != null) {
			MountedFluidStorage storage = fluidType.mount(level, state, globalPos, be);
			if (storage != null) {
				this.addStorage(storage, localPos);
			}
		}
	}

	public void unmount(Level level, StructureBlockInfo info, BlockPos globalPos, @Nullable BlockEntity be) {
		BlockPos localPos = info.pos();
		BlockState state = info.state();

		MountedItemStorage itemStorage = this.getAllItemStorages().get(localPos);
		if (itemStorage != null) {
			MountedItemStorageType<?> expectedType = MountedItemStorageType.REGISTRY.get(state.getBlock());
			if (itemStorage.type == expectedType) {
				itemStorage.unmount(level, state, globalPos, be);
			}
		}

		MountedFluidStorage fluidStorage = this.getFluids().storages.get(localPos);
		if (fluidStorage != null) {
			MountedFluidStorageType<?> expectedType = MountedFluidStorageType.REGISTRY.get(state.getBlock());
			if (fluidStorage.type == expectedType) {
				fluidStorage.unmount(level, state, globalPos, be);
			}
		}
	}

	/** Create syncs changed storages to clients here; nothing is synced, so there is nothing to do. */
	public void tick(AbstractContraptionEntity entity) {
	}

	public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket, @Nullable Contraption contraption) {
		final RegistryOps<Tag> registryOps = registries.createSerializationContext(NbtOps.INSTANCE);
		this.reset();

		try {
			for (Tag entry : nbt.getListOrEmpty("items")) {
				if (!(entry instanceof CompoundTag tag))
					continue;
				BlockPos pos = tag.read("pos", BlockPos.CODEC).orElse(BlockPos.ZERO);
				Tag data = tag.getCompoundOrEmpty("storage");
				MountedItemStorage.CODEC.decode(registryOps, data)
					.resultOrPartial(err -> CreatePolymer.LOGGER.error("Failed to deserialize mounted item storage: {}", err))
					.map(com.mojang.datafixers.util.Pair::getFirst)
					.ifPresent(storage -> this.addStorage(storage, pos));
			}

			for (Tag entry : nbt.getListOrEmpty("fluids")) {
				if (!(entry instanceof CompoundTag tag))
					continue;
				BlockPos pos = tag.read("pos", BlockPos.CODEC).orElse(BlockPos.ZERO);
				Tag data = tag.getCompoundOrEmpty("storage");
				MountedFluidStorage.CODEC.decode(registryOps, data)
					.resultOrPartial(err -> CreatePolymer.LOGGER.error("Failed to deserialize mounted fluid storage: {}", err))
					.map(com.mojang.datafixers.util.Pair::getFirst)
					.ifPresent(storage -> this.addStorage(storage, pos));
			}
		} catch (Throwable t) {
			CreatePolymer.LOGGER.error("Error deserializing mounted storage", t);
			// an exception will leave the manager in an invalid state, initialize must be called
		}

		this.initialize();
	}

	public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
		final RegistryOps<Tag> registryOps = registries.createSerializationContext(NbtOps.INSTANCE);
		ListTag items = new ListTag();
		this.getAllItemStorages().forEach((pos, storage) -> {
			MountedItemStorage.CODEC.encodeStart(registryOps, storage)
				.resultOrPartial(err -> CreatePolymer.LOGGER.error("Failed to serialize mounted item storage: {}", err))
				.ifPresent(encoded -> {
					CompoundTag tag = new CompoundTag();
					tag.store("pos", BlockPos.CODEC, pos);
					tag.put("storage", encoded);
					items.add(tag);
				});
		});
		if (!items.isEmpty()) {
			nbt.put("items", items);
		}

		ListTag fluids = new ListTag();
		this.getFluids().storages.forEach((pos, storage) -> {
			MountedFluidStorage.CODEC.encodeStart(registryOps, storage)
				.resultOrPartial(err -> CreatePolymer.LOGGER.error("Failed to serialize mounted fluid storage: {}", err))
				.ifPresent(encoded -> {
					CompoundTag tag = new CompoundTag();
					tag.store("pos", BlockPos.CODEC, pos);
					tag.put("storage", encoded);
					fluids.add(tag);
				});
		});
		if (!fluids.isEmpty()) {
			nbt.put("fluids", fluids);
		}
	}

	public void attachExternal(IItemHandlerModifiable externalStorage) {
		this.externalHandlers.add(externalStorage);
		IItemHandlerModifiable[] all = new IItemHandlerModifiable[this.externalHandlers.size() + 1];
		all[0] = this.items;
		for (int i = 0; i < this.externalHandlers.size(); i++) {
			all[i + 1] = this.externalHandlers.get(i);
		}

		this.allItems = new CombinedInvWrapper(all);
	}

	/**
	 * The primary way to access a contraption's inventory. Includes all
	 * non-internal mounted storages as well as all external storage.
	 */
	public CombinedInvWrapper getAllItems() {
		this.assertInitialized();
		return this.allItems;
	}

	/**
	 * Gets a map of all MountedItemStorages in the contraption, irrelevant of them being internal or providing fuel.
	 */
	public ImmutableMap<BlockPos, MountedItemStorage> getAllItemStorages() {
		this.assertInitialized();
		return this.allItemStorages;
	}

	/**
	 * Gets an item handler wrapping all non-internal mounted storages. This is not
	 * the whole contraption inventory as it does not include external storages.
	 * Most often, you want {@link #getAllItems()}, which does.
	 */
	public MountedItemStorageWrapper getMountedItems() {
		this.assertInitialized();
		return this.items;
	}

	/**
	 * Gets an item handler wrapping all non-internal mounted storages that provide fuel.
	 * May be null if none are present.
	 */
	@Nullable
	public MountedItemStorageWrapper getFuelItems() {
		this.assertInitialized();
		return this.fuelItems;
	}

	/**
	 * Gets a fluid handler wrapping all mounted fluid storages.
	 */
	public MountedFluidStorageWrapper getFluids() {
		this.assertInitialized();
		return this.fluids;
	}

	public boolean handlePlayerStorageInteraction(Contraption contraption, Player player, BlockPos localPos) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return false;
		}

		StructureBlockInfo info = contraption.getBlocks().get(localPos);
		if (info == null)
			return false;

		MountedStorageManager storageManager = contraption.getStorage();
		MountedItemStorage storage = storageManager.getAllItemStorages().get(localPos);

		if (storage != null) {
			return storage.handleInteraction(serverPlayer, contraption, info);
		} else {
			return false;
		}
	}

	private void addStorage(MountedItemStorage storage, BlockPos pos) {
		this.itemsBuilder.put(pos, storage);
	}

	private void addStorage(MountedFluidStorage storage, BlockPos pos) {
		this.fluidsBuilder.put(pos, storage);
	}

	private static <K, V> ImmutableMap<K, V> subMap(Map<K, V> map, Predicate<V> predicate) {
		ImmutableMap.Builder<K, V> builder = ImmutableMap.builder();
		map.forEach((key, value) -> {
			if (predicate.test(value)) {
				builder.put(key, value);
			}
		});
		return builder.build();
	}
}
