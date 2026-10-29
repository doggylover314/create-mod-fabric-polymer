package dev.createpolymer.registry.feature;

import com.mojang.serialization.Codec;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.redstone.RedstoneClientStates;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.trains.GlobalRailwayManager;
import dev.createpolymer.trains.track.AllPortalTracks;
import dev.createpolymer.trains.track.BezierTrackPointLocation;
import dev.createpolymer.trains.track.FakeTrackBlock;
import dev.createpolymer.trains.track.FakeTrackBlockEntity;
import dev.createpolymer.trains.track.TrackBlock;
import dev.createpolymer.trains.track.TrackBlockEntity;
import dev.createpolymer.trains.track.TrackBlockItem;
import dev.createpolymer.trains.track.TrackMaterial;
import dev.createpolymer.trains.track.TrackPlacement;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Create's train tracks and the graph they form: the track block and its curves, and the railway manager that keeps
 * the track graphs of the world. The trains, stations and signals that run on it register themselves later.
 */
public final class TrackRegistry {
	private TrackRegistry() {}

	/** Create's {@code Create.RAILWAYS}: the track graphs, signal groups and trains of the world. */
	public static final GlobalRailwayManager RAILWAYS = new GlobalRailwayManager();

	// Data components (Create: AllDataComponents). Server-only, players never receive them.
	public static final DataComponentType<TrackPlacement.ConnectingFrom> TRACK_CONNECTING_FROM = component(
		"track_connecting_from", DataComponentType.<TrackPlacement.ConnectingFrom>builder()
			.persistent(TrackPlacement.ConnectingFrom.CODEC)
			.build());
	public static final DataComponentType<Boolean> TRACK_EXTENDED_CURVE = component("track_extended_curve",
		DataComponentType.<Boolean>builder()
			.persistent(Codec.BOOL)
			.build());
	public static final DataComponentType<BlockPos> TRACK_TARGETING_ITEM_SELECTED_POS =
		component("track_targeting_item_selected_pos", DataComponentType.<BlockPos>builder()
			.persistent(BlockPos.CODEC)
			.build());
	public static final DataComponentType<Boolean> TRACK_TARGETING_ITEM_SELECTED_DIRECTION =
		component("track_targeting_item_selected_direction", DataComponentType.<Boolean>builder()
			.persistent(Codec.BOOL)
			.build());
	public static final DataComponentType<BezierTrackPointLocation> TRACK_TARGETING_ITEM_BEZIER =
		component("track_targeting_item_bezier", DataComponentType.<BezierTrackPointLocation>builder()
			.persistent(BezierTrackPointLocation.CODEC)
			.build());

	// Blocks. Create: SharedProperties.stone with a few changes.
	public static final TrackBlock TRACK = ModBlocks.register("track", TrackMaterial.ANDESITE::createBlock,
		ModBlocks.stone()
			.mapColor(MapColor.METAL)
			.strength(0.8F)
			.sound(SoundType.METAL)
			.noOcclusion()
			.forceSolidOn()
			.pushReaction(PushReaction.IMMOVEABLE));
	public static final FakeTrackBlock FAKE_TRACK = ModBlocks.register("fake_track", FakeTrackBlock::new,
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL)
			.noCollision()
			.noOcclusion()
			.noLootTable()
			.replaceable());

	// Block entities
	public static final BlockEntityType<TrackBlockEntity> TRACK_BLOCK_ENTITY = ModBlockEntities.register("track",
		(pos, state) -> new TrackBlockEntity(TrackRegistry.TRACK_BLOCK_ENTITY, pos, state), TRACK);
	public static final BlockEntityType<FakeTrackBlockEntity> FAKE_TRACK_BLOCK_ENTITY = ModBlockEntities
		.register("fake_track", (pos, state) -> new FakeTrackBlockEntity(TrackRegistry.FAKE_TRACK_BLOCK_ENTITY, pos, state),
			FAKE_TRACK);

	// Items
	public static final Item TRACK_ITEM = ModItems.register("track", p -> new TrackBlockItem(TRACK, p),
		new Item.Properties().useBlockDescriptionPrefix());

	/** Create's {@code AllBlockTags.TRACKS} (and {@code GIRDABLE_TRACKS}): the base mod only has the one track block. */
	public static boolean isTrack(BlockState state) {
		return state.getBlock() instanceof TrackBlock;
	}

	/** Create's {@code AllItemTags.INVALID_FOR_TRACK_PAVING}: the brackets. */
	public static boolean isInvalidForTrackPaving(ItemStack stack) {
		var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return key.getNamespace()
			.equals(CreatePolymer.MOD_ID)
			&& key.getPath()
				.endsWith("_bracket");
	}

	private static <T> DataComponentType<T> component(String id, DataComponentType<T> type) {
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, CreatePolymer.id(id), type);
		PolymerComponent.registerDataComponent(type);
		return type;
	}

	public static void init() {
		// Thin invisible plates under the track models; must be reserved while the mod initialises.
		RedstoneClientStates.init();

		AllPortalTracks.registerDefaults();

		ServerLevelEvents.LOAD.register((server, level) -> RAILWAYS.levelLoaded(level));
		ServerLevelEvents.UNLOAD.register((server, level) -> RAILWAYS.levelUnloaded(level));
		ServerTickEvents.END_SERVER_TICK.register(server -> RAILWAYS.tick(server.overworld()));
	}
}
