package dev.createpolymer.registry.feature;

import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.api.behaviour.display.DisplaySource;
import dev.createpolymer.trains.graph.EdgePointType;
import dev.createpolymer.trains.observer.ObservedTrainNameSource;
import dev.createpolymer.trains.observer.TrackObserverBlock;
import dev.createpolymer.trains.observer.TrackObserverBlockEntity;
import dev.createpolymer.trains.signal.SignalBlock;
import dev.createpolymer.trains.signal.SignalBlockEntity;
import dev.createpolymer.trains.track.TrackTargetingBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Create's train signals and track observers: blocks that sit next to a track and put a signal or an observer on
 * the track graph (see {@link TrackRegistry}).
 */
public final class SignalsRegistry {
	private SignalsRegistry() {}

	/** Create's SharedProperties.softMetal */
	private static BlockBehaviour.Properties softMetal() {
		return BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK)
			.noOcclusion();
	}

	// ---- blocks ----

	public static final SignalBlock TRACK_SIGNAL = ModBlocks.register("track_signal", SignalBlock::new,
		softMetal().mapColor(MapColor.PODZOL)
			.sound(SoundType.NETHERITE_BLOCK));
	public static final TrackObserverBlock TRACK_OBSERVER = ModBlocks.register("track_observer",
		TrackObserverBlock::new, softMetal().mapColor(MapColor.PODZOL)
			.sound(SoundType.NETHERITE_BLOCK));

	// ---- block entities ----

	public static final BlockEntityType<SignalBlockEntity> TRACK_SIGNAL_BLOCK_ENTITY = ModBlockEntities
		.register("track_signal", (pos, state) -> new SignalBlockEntity(SignalsRegistry.TRACK_SIGNAL_BLOCK_ENTITY, pos,
			state), TRACK_SIGNAL);
	public static final BlockEntityType<TrackObserverBlockEntity> TRACK_OBSERVER_BLOCK_ENTITY = ModBlockEntities
		.register("track_observer", (pos, state) -> new TrackObserverBlockEntity(
			SignalsRegistry.TRACK_OBSERVER_BLOCK_ENTITY, pos, state), TRACK_OBSERVER);

	// ---- items ----

	public static final Item TRACK_SIGNAL_ITEM = ModItems.register("track_signal",
		p -> new TrackTargetingBlockItem(TRACK_SIGNAL, p, EdgePointType.SIGNAL),
		new Item.Properties().useBlockDescriptionPrefix());
	public static final Item TRACK_OBSERVER_ITEM = ModItems.register("track_observer",
		p -> new TrackTargetingBlockItem(TRACK_OBSERVER, p, EdgePointType.OBSERVER),
		new Item.Properties().useBlockDescriptionPrefix());

	// ---- display sources (Create's AllDisplaySources) ----

	public static final ObservedTrainNameSource OBSERVED_TRAIN_NAME = DisplaySource.register("observed_train_name",
		new ObservedTrainNameSource());

	public static void init() {
		DisplaySource.BY_BLOCK.add(TRACK_OBSERVER, OBSERVED_TRAIN_NAME);
		DisplaySource.LEGACY_NAMES.put("track_observer_source_observed_train_name", OBSERVED_TRAIN_NAME);
	}
}
