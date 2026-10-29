package dev.createpolymer.registry.feature;

import com.mojang.serialization.Codec;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.equipment.symmetryWand.SymmetryHandler;
import dev.createpolymer.equipment.symmetryWand.SymmetryWandItem;
import dev.createpolymer.equipment.symmetryWand.mirror.SymmetryMirror;
import dev.createpolymer.equipment.zapper.PlacementPatterns;
import dev.createpolymer.equipment.zapper.ZapperInteractionHandler;
import dev.createpolymer.equipment.zapper.terrainzapper.PlacementOptions;
import dev.createpolymer.equipment.zapper.terrainzapper.TerrainBrushes;
import dev.createpolymer.equipment.zapper.terrainzapper.TerrainTools;
import dev.createpolymer.equipment.zapper.terrainzapper.WorldshaperItem;
import dev.createpolymer.equipment.zapper.terrainzapper.WorldshaperRenderHandler;
import dev.createpolymer.registry.ModItems;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.ItemEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create's handheld worldshaper and wand of symmetry (content.equipment.zapper and content.equipment.symmetryWand).
 * Ids are Create's. Both are Polymer items that look like a vanilla item with Create's model; their settings are
 * server-only data components, and their screens are server side GUIs.
 */
public final class WandsRegistry {
	private WandsRegistry() {}

	// ---- Data components (server only) ----

	public static final DataComponentType<PlacementPatterns> PLACEMENT_PATTERN = component("placement_pattern",
		DataComponentType.<PlacementPatterns>builder()
			.persistent(PlacementPatterns.CODEC)
			.networkSynchronized(ByteBufCodecs.fromCodec(PlacementPatterns.CODEC))
			.build());

	public static final DataComponentType<TerrainBrushes> SHAPER_BRUSH = component("shaper_brush",
		DataComponentType.<TerrainBrushes>builder()
			.persistent(TerrainBrushes.CODEC)
			.networkSynchronized(ByteBufCodecs.fromCodec(TerrainBrushes.CODEC))
			.build());

	public static final DataComponentType<BlockPos> SHAPER_BRUSH_PARAMS = component("shaper_brush_params",
		DataComponentType.<BlockPos>builder()
			.persistent(BlockPos.CODEC)
			.networkSynchronized(BlockPos.STREAM_CODEC)
			.build());

	public static final DataComponentType<PlacementOptions> SHAPER_PLACEMENT_OPTIONS = component("shaper_placement_options",
		DataComponentType.<PlacementOptions>builder()
			.persistent(PlacementOptions.CODEC)
			.networkSynchronized(ByteBufCodecs.fromCodec(PlacementOptions.CODEC))
			.build());

	public static final DataComponentType<TerrainTools> SHAPER_TOOL = component("shaper_tool",
		DataComponentType.<TerrainTools>builder()
			.persistent(TerrainTools.CODEC)
			.networkSynchronized(ByteBufCodecs.fromCodec(TerrainTools.CODEC))
			.build());

	public static final DataComponentType<BlockState> SHAPER_BLOCK_USED = component("shaper_block_used",
		DataComponentType.<BlockState>builder()
			.persistent(BlockState.CODEC)
			.networkSynchronized(ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY))
			.build());

	public static final DataComponentType<CompoundTag> SHAPER_BLOCK_DATA = component("shaper_block_data",
		DataComponentType.<CompoundTag>builder()
			.persistent(CompoundTag.CODEC)
			.networkSynchronized(ByteBufCodecs.COMPOUND_TAG)
			.build());

	public static final DataComponentType<SymmetryMirror> SYMMETRY_WAND = component("symmetry_wand",
		DataComponentType.<SymmetryMirror>builder()
			.persistent(SymmetryMirror.CODEC)
			.networkSynchronized(ByteBufCodecs.fromCodec(SymmetryMirror.CODEC))
			.build());

	public static final DataComponentType<Boolean> SYMMETRY_WAND_ENABLE = component("symmetry_wand_enable",
		DataComponentType.<Boolean>builder()
			.persistent(Codec.BOOL)
			.networkSynchronized(ByteBufCodecs.BOOL)
			.build());

	private static <T> DataComponentType<T> component(String id, DataComponentType<T> type) {
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, CreatePolymer.id(id), type);
		PolymerComponent.registerDataComponent(type);
		return type;
	}

	/** Create's SHAPER_SWAP, which the potato cannon uses as well */
	public static final DataComponentType<Boolean> SHAPER_SWAP = EquipmentRegistry.SHAPER_SWAP;

	// ---- Items ----

	public static final Item WORLDSHAPER = ModItems.register("handheld_worldshaper", WorldshaperItem::new,
		new Item.Properties().rarity(Rarity.EPIC));
	public static final Item WAND_OF_SYMMETRY = ModItems.register("wand_of_symmetry", SymmetryWandItem::new,
		new Item.Properties().rarity(Rarity.UNCOMMON));

	public static void init() {
		// Create's ZapperInteractionHandler: left-clicking a block with the zapper selects it
		AttackBlockCallback.EVENT.register(ZapperInteractionHandler::leftClickingBlocksWithTheZapperSelectsTheBlock);

		// Create's SymmetryHandler: blocks placed by using a block item, blocks broken by players
		ItemEvents.USE_ON.register(SymmetryHandler::onItemUseOn);
		PlayerBlockBreakEvents.AFTER.register(SymmetryHandler::onBlockDestroyed);

		// the mirror displays and the region preview of the worldshaper
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			SymmetryHandler.tick(server);
			WorldshaperRenderHandler.tick(server);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			SymmetryHandler.remove(handler.getPlayer());
			WorldshaperRenderHandler.remove(handler.getPlayer());
		});
	}
}
