package dev.createpolymer.registry.feature;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.api.packager.InventoryIdentifier;
import dev.createpolymer.api.packager.unpacking.UnpackingHandler;
import dev.createpolymer.logistics.box.PackageEntity;
import dev.createpolymer.logistics.box.PackageItem;
import dev.createpolymer.logistics.box.PackageItem.PackageOrderData;
import dev.createpolymer.logistics.box.PackageStyles;
import dev.createpolymer.logistics.box.PackageStyles.PackageStyle;
import dev.createpolymer.logistics.packager.AllInventoryIdentifiers;
import dev.createpolymer.logistics.packager.AllUnpackingHandlers;
import dev.createpolymer.logistics.packager.PackagerBlock;
import dev.createpolymer.logistics.packager.PackagerBlockEntity;
import dev.createpolymer.logistics.packager.repackager.RepackagerBlock;
import dev.createpolymer.logistics.packager.repackager.RepackagerBlockEntity;
import dev.createpolymer.logistics.packagePort.AllPackagePortTargetTypes;
import dev.createpolymer.logistics.packagePort.PackagePortItem;
import dev.createpolymer.logistics.packagePort.PackagePortTarget;
import dev.createpolymer.logistics.packagePort.frogport.FrogportBlock;
import dev.createpolymer.logistics.packagePort.frogport.FrogportBlockEntity;
import dev.createpolymer.logistics.packagePort.postbox.PostboxBlock;
import dev.createpolymer.logistics.packagePort.postbox.PostboxBlockEntity;
import dev.createpolymer.logistics.packagerLink.GlobalLogisticsManager;
import dev.createpolymer.logistics.packagerLink.LogisticallyLinkedBlockItem;
import dev.createpolymer.logistics.packagerLink.PackagerLinkBlock;
import dev.createpolymer.logistics.packagerLink.PackagerLinkBlockEntity;
import dev.createpolymer.logistics.packagerLink.TickBasedCache;
import dev.createpolymer.logistics.stockTicker.PackageOrderWithCrafts;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import eu.pb4.polymer.resourcepack.extras.api.ResourcePackExtras;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;

/**
 * Create's package logistics core: packages (items and entities), the packager and repackager, the stock link and its
 * logistics networks, the frogport and the postboxes.
 */
public final class PackagesRegistry {
	private PackagesRegistry() {}

	/** Create's CLogistics.packagePortRange: maximum distance in blocks a package port can be placed at from its target. */
	public static int packagePortRange = 5;

	/** Create's {@code Create.LOGISTICS}: the logistics networks of the world. */
	public static final GlobalLogisticsManager LOGISTICS = new GlobalLogisticsManager();

	// Data components (Create: AllDataComponents). Server-only, players never receive them.
	public static final DataComponentType<String> PACKAGE_ADDRESS = component("package_address",
		DataComponentType.<String>builder()
			.persistent(Codec.STRING)
			.build());
	public static final DataComponentType<ItemContainerContents> PACKAGE_CONTENTS = component("package_contents",
		DataComponentType.<ItemContainerContents>builder()
			.persistent(ItemContainerContents.CODEC)
			.build());
	public static final DataComponentType<PackageOrderData> PACKAGE_ORDER_DATA = component("package_order_data",
		DataComponentType.<PackageOrderData>builder()
			.persistent(PackageOrderData.CODEC)
			.build());
	public static final DataComponentType<PackageOrderWithCrafts> PACKAGE_ORDER_CONTEXT = component("package_order_context",
		DataComponentType.<PackageOrderWithCrafts>builder()
			.persistent(PackageOrderWithCrafts.CODEC)
			.build());
	/** The target picked for a package port item (Create keeps this in a client-side field). */
	public static final DataComponentType<PackagePortTarget> PACKAGE_PORT_TARGET = component("package_port_target",
		DataComponentType.<PackagePortTarget>builder()
			.persistent(PackagePortTarget.CODEC)
			.build());

	// Entity
	public static final EntityType<PackageEntity> PACKAGE = entity("package",
		EntityType.Builder.<PackageEntity>of(PackageEntity::new, MobCategory.MISC)
			.sized(1, 1)
			.clientTrackingRange(10)
			.updateInterval(3)
			.noLootTable());

	// Package items, one per style
	public static final List<PackageItem> PACKAGES = new ArrayList<>();
	static {
		for (PackageStyle style : PackageStyles.STYLES) {
			PackageItem item = ModItems.register(style.getItemId().getPath(), p -> new PackageItem(p, style),
				PackageItem.properties(style));
			PACKAGES.add(item);
			// the rare ones are not meant for the creative inventory
			if (style.rare())
				ModItems.TAB_ORDER.remove(item);
		}
	}

	// Blocks. Create: SharedProperties.softMetal with noOcclusion, terracotta blue and netherite sound.
	private static BlockBehaviour.Properties softMetal() {
		return BlockBehaviour.Properties.of()
			.strength(3f, 6f)
			.sound(SoundType.NETHERITE_BLOCK)
			.mapColor(MapColor.TERRACOTTA_BLUE)
			.noOcclusion();
	}

	public static final PackagerBlock PACKAGER = ModBlocks.register("packager", PackagerBlock::new,
		softMetal().isRedstoneConductor(($1, $2, $3) -> false));
	public static final RepackagerBlock REPACKAGER = ModBlocks.register("repackager", RepackagerBlock::new,
		softMetal().isRedstoneConductor(($1, $2, $3) -> false));
	public static final FrogportBlock PACKAGE_FROGPORT = ModBlocks.register("package_frogport", FrogportBlock::new,
		softMetal());
	public static final PackagerLinkBlock STOCK_LINK = ModBlocks.register("stock_link", PackagerLinkBlock::new,
		softMetal().noOcclusion());

	public static final Map<DyeColor, PostboxBlock> PACKAGE_POSTBOXES = new EnumMap<>(DyeColor.class);
	static {
		for (DyeColor colour : DyeColor.values())
			PACKAGE_POSTBOXES.put(colour, ModBlocks.register(colour.getSerializedName() + "_postbox",
				p -> new PostboxBlock(p, colour), ModBlocks.wooden().mapColor(colour)));
	}

	// Block entities
	public static final BlockEntityType<PackagerBlockEntity> PACKAGER_BLOCK_ENTITY = ModBlockEntities.register("packager",
		(pos, state) -> new PackagerBlockEntity(PackagesRegistry.PACKAGER_BLOCK_ENTITY, pos, state), PACKAGER);
	public static final BlockEntityType<RepackagerBlockEntity> REPACKAGER_BLOCK_ENTITY = ModBlockEntities.register("repackager",
		(pos, state) -> new RepackagerBlockEntity(PackagesRegistry.REPACKAGER_BLOCK_ENTITY, pos, state), REPACKAGER);
	public static final BlockEntityType<FrogportBlockEntity> PACKAGE_FROGPORT_BLOCK_ENTITY = ModBlockEntities.register("package_frogport",
		(pos, state) -> new FrogportBlockEntity(PackagesRegistry.PACKAGE_FROGPORT_BLOCK_ENTITY, pos, state), PACKAGE_FROGPORT);
	public static final BlockEntityType<PostboxBlockEntity> PACKAGE_POSTBOX_BLOCK_ENTITY = ModBlockEntities.register("package_postbox",
		(pos, state) -> new PostboxBlockEntity(PackagesRegistry.PACKAGE_POSTBOX_BLOCK_ENTITY, pos, state),
		PACKAGE_POSTBOXES.values().toArray(new PostboxBlock[0]));
	public static final BlockEntityType<PackagerLinkBlockEntity> PACKAGER_LINK_BLOCK_ENTITY = ModBlockEntities.register("packager_link",
		(pos, state) -> new PackagerLinkBlockEntity(PackagesRegistry.PACKAGER_LINK_BLOCK_ENTITY, pos, state), STOCK_LINK);

	// Block items
	public static final Item PACKAGER_ITEM = ModItems.block(PACKAGER);
	public static final Item REPACKAGER_ITEM = ModItems.block(REPACKAGER);
	public static final Item PACKAGE_FROGPORT_ITEM = ModItems.register("package_frogport",
		p -> new PackagePortItem(PACKAGE_FROGPORT, p), new Item.Properties().useBlockDescriptionPrefix());
	public static final Item STOCK_LINK_ITEM = ModItems.register("stock_link",
		p -> new LogisticallyLinkedBlockItem(STOCK_LINK, p), new Item.Properties().useBlockDescriptionPrefix());
	public static final Map<DyeColor, Item> PACKAGE_POSTBOX_ITEMS = new EnumMap<>(DyeColor.class);
	static {
		PACKAGE_POSTBOXES.forEach((colour, block) -> PACKAGE_POSTBOX_ITEMS.put(colour,
			ModItems.register(colour.getSerializedName() + "_postbox", p -> new PackagePortItem(block, p),
				new Item.Properties().useBlockDescriptionPrefix())));
	}

	private static <T> DataComponentType<T> component(String id, DataComponentType<T> type) {
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, CreatePolymer.id(id), type);
		PolymerComponent.registerDataComponent(type);
		return type;
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> entity(String id,
		EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, CreatePolymer.id(id));
		EntityType<T> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
		PolymerEntityUtils.registerType(type);
		return type;
	}

	/** Dropped packages become package entities (Create: PackageItem.createEntity); items that need converting wait here. */
	private static final List<ItemEntity> DROPPED_PACKAGES = new ArrayList<>();

	public static void init() {
		AllPackagePortTargetTypes.init();
		AllInventoryIdentifiers.registerDefaults();
		AllUnpackingHandlers.registerDefaults();

		PackagerBlockEntity.registerCapabilities();
		RepackagerBlockEntity.registerCapabilities();
		FrogportBlockEntity.registerCapabilities();
		PostboxBlockEntity.registerCapabilities();

		// The rigging models hang packages from frog tongues and chains: they are shown through bridged item models
		ResourcePackExtras.forDefault().addBridgedModelsFolder(CreatePolymer.id("item/package"));

		ServerLevelEvents.LOAD.register((server, level) -> LOGISTICS.levelLoaded(level));
		ServerLevelEvents.UNLOAD.register((server, level) -> LOGISTICS.levelUnloaded(level));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			TickBasedCache.tick();
			LOGISTICS.tick(server.overworld());
			convertDroppedPackages();
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity itemEntity && PackageItem.isPackage(itemEntity.getItem())
				&& itemEntity.getAge() < 5999) // 5999 is the age of the fake items commands create
				DROPPED_PACKAGES.add(itemEntity);
		});
	}

	private static void convertDroppedPackages() {
		if (DROPPED_PACKAGES.isEmpty())
			return;
		List<ItemEntity> items = new ArrayList<>(DROPPED_PACKAGES);
		DROPPED_PACKAGES.clear();
		for (ItemEntity itemEntity : items) {
			if (itemEntity.isRemoved() || !(itemEntity.level() instanceof ServerLevel level)
				|| !PackageItem.isPackage(itemEntity.getItem()))
				continue;
			PackageEntity packageEntity = PackageEntity.fromDroppedItem(level, itemEntity, itemEntity.getItem());
			itemEntity.discard();
			level.addFreshEntity(packageEntity);
		}
	}
}
