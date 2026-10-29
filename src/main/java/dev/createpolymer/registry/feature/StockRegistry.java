package dev.createpolymer.registry.feature;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.serialization.Codec;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.logistics.packagerLink.LogisticallyLinkedBlockItem;
import dev.createpolymer.logistics.redstoneRequester.AutoRequestData;
import dev.createpolymer.logistics.redstoneRequester.RedstoneRequesterBlock;
import dev.createpolymer.logistics.redstoneRequester.RedstoneRequesterBlockEntity;
import dev.createpolymer.logistics.redstoneRequester.RedstoneRequesterBlockItem;
import dev.createpolymer.logistics.stockTicker.StockTickerBlock;
import dev.createpolymer.logistics.stockTicker.StockTickerBlockEntity;
import dev.createpolymer.logistics.stockTicker.StockTickerInteractionHandler;
import dev.createpolymer.logistics.tableCloth.ShoppingListItem;
import dev.createpolymer.logistics.tableCloth.ShoppingListItem.ShoppingList;
import dev.createpolymer.logistics.tableCloth.TableClothBlock;
import dev.createpolymer.logistics.tableCloth.TableClothBlockEntity;
import dev.createpolymer.logistics.tableCloth.TableClothBlockItem;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Create's stock keeper: the stock ticker, the redstone requester, the table cloths with their shopping lists.
 */
public final class StockRegistry {
	private StockRegistry() {}

	// Data components (Create: AllDataComponents). Server-only, players never receive them.
	public static final DataComponentType<AutoRequestData> AUTO_REQUEST_DATA = component("auto_request_data",
		DataComponentType.<AutoRequestData>builder()
			.persistent(AutoRequestData.CODEC)
			.build());
	public static final DataComponentType<ShoppingList> SHOPPING_LIST = component("shopping_list",
		DataComponentType.<ShoppingList>builder()
			.persistent(ShoppingList.CODEC)
			.build());
	public static final DataComponentType<String> SHOPPING_LIST_ADDRESS = component("shopping_list_address",
		DataComponentType.<String>builder()
			.persistent(Codec.STRING)
			.build());

	// Blocks. Create: SharedProperties.softMetal with the glass sound for the ticker.
	private static BlockBehaviour.Properties softMetal() {
		return BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK);
	}

	public static final StockTickerBlock STOCK_TICKER = ModBlocks.register("stock_ticker", StockTickerBlock::new,
		softMetal().sound(SoundType.GLASS));
	public static final RedstoneRequesterBlock REDSTONE_REQUESTER = ModBlocks.register("redstone_requester",
		RedstoneRequesterBlock::new, ModBlocks.stone()
			.sound(SoundType.NETHERITE_BLOCK));

	public static final Map<DyeColor, TableClothBlock> TABLE_CLOTHS = new EnumMap<>(DyeColor.class);
	static {
		for (DyeColor colour : DyeColor.values())
			TABLE_CLOTHS.put(colour, ModBlocks.register(colour.getSerializedName() + "_table_cloth",
				p -> new TableClothBlock(p, colour),
				BlockBehaviour.Properties.ofFullCopy(Blocks.CARPET.black())
					.mapColor(colour)));
	}
	public static final TableClothBlock ANDESITE_TABLE_CLOTH = ModBlocks.register("andesite_table_cloth",
		p -> new TableClothBlock(p, "andesite"), BlockBehaviour.Properties.ofFullCopy(Blocks.ANDESITE)
			.mapColor(MapColor.STONE)
			.requiresCorrectToolForDrops());
	public static final TableClothBlock BRASS_TABLE_CLOTH = ModBlocks.register("brass_table_cloth",
		p -> new TableClothBlock(p, "brass"), softMetal()
			.mapColor(MapColor.TERRACOTTA_YELLOW)
			.requiresCorrectToolForDrops());
	public static final TableClothBlock COPPER_TABLE_CLOTH = ModBlocks.register("copper_table_cloth",
		p -> new TableClothBlock(p, "copper"), BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK.weathering().unaffected())
			.requiresCorrectToolForDrops());

	// Block entities
	public static final BlockEntityType<StockTickerBlockEntity> STOCK_TICKER_BLOCK_ENTITY = ModBlockEntities.register("stock_ticker",
		(pos, state) -> new StockTickerBlockEntity(StockRegistry.STOCK_TICKER_BLOCK_ENTITY, pos, state), STOCK_TICKER);
	public static final BlockEntityType<RedstoneRequesterBlockEntity> REDSTONE_REQUESTER_BLOCK_ENTITY = ModBlockEntities.register("redstone_requester",
		(pos, state) -> new RedstoneRequesterBlockEntity(StockRegistry.REDSTONE_REQUESTER_BLOCK_ENTITY, pos, state), REDSTONE_REQUESTER);
	public static final BlockEntityType<TableClothBlockEntity> TABLE_CLOTH_BLOCK_ENTITY = ModBlockEntities.register("table_cloth",
		(pos, state) -> new TableClothBlockEntity(StockRegistry.TABLE_CLOTH_BLOCK_ENTITY, pos, state), allTableCloths());

	private static TableClothBlock[] allTableCloths() {
		TableClothBlock[] blocks = new TableClothBlock[TABLE_CLOTHS.size() + 3];
		int i = 0;
		for (TableClothBlock block : TABLE_CLOTHS.values())
			blocks[i++] = block;
		blocks[i++] = ANDESITE_TABLE_CLOTH;
		blocks[i++] = BRASS_TABLE_CLOTH;
		blocks[i] = COPPER_TABLE_CLOTH;
		return blocks;
	}

	// Block items
	public static final Item STOCK_TICKER_ITEM = ModItems.register("stock_ticker",
		p -> new LogisticallyLinkedBlockItem(STOCK_TICKER, p), new Item.Properties().useBlockDescriptionPrefix());
	public static final Item REDSTONE_REQUESTER_ITEM = ModItems.register("redstone_requester",
		p -> new RedstoneRequesterBlockItem(REDSTONE_REQUESTER, p), new Item.Properties().useBlockDescriptionPrefix());
	public static final Map<DyeColor, Item> TABLE_CLOTH_ITEMS = new EnumMap<>(DyeColor.class);
	static {
		TABLE_CLOTHS.forEach((colour, block) -> TABLE_CLOTH_ITEMS.put(colour,
			ModItems.register(colour.getSerializedName() + "_table_cloth", p -> new TableClothBlockItem(block, p),
				new Item.Properties().useBlockDescriptionPrefix())));
	}
	public static final Item ANDESITE_TABLE_CLOTH_ITEM = ModItems.register("andesite_table_cloth",
		p -> new TableClothBlockItem(ANDESITE_TABLE_CLOTH, p), new Item.Properties().useBlockDescriptionPrefix());
	public static final Item BRASS_TABLE_CLOTH_ITEM = ModItems.register("brass_table_cloth",
		p -> new TableClothBlockItem(BRASS_TABLE_CLOTH, p), new Item.Properties().useBlockDescriptionPrefix());
	public static final Item COPPER_TABLE_CLOTH_ITEM = ModItems.register("copper_table_cloth",
		p -> new TableClothBlockItem(COPPER_TABLE_CLOTH, p), new Item.Properties().useBlockDescriptionPrefix());

	public static final ShoppingListItem SHOPPING_LIST_ITEM = ModItems.register("shopping_list", ShoppingListItem::new,
		new Item.Properties().stacksTo(1));

	private static <T> DataComponentType<T> component(String id, DataComponentType<T> type) {
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, CreatePolymer.id(id), type);
		PolymerComponent.registerDataComponent(type);
		return type;
	}

	public static void init() {
		StockTickerBlockEntity.registerCapabilities();
		StockTickerInteractionHandler.init();
	}
}
