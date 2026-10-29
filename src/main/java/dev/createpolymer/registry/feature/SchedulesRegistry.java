package dev.createpolymer.registry.feature;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.api.behaviour.display.DisplaySource;
import dev.createpolymer.redstone.displayLink.source.StationSummaryDisplaySource;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.trains.display.GlobalTrainDisplayData;
import dev.createpolymer.trains.schedule.ScheduleItem;
import dev.createpolymer.trains.schedule.ScheduleItemEntityInteraction;
import dev.createpolymer.trains.station.StationBlockEntity;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;

/**
 * Create's train schedules: the schedule item with the data component it keeps the schedule in, and the departure
 * board source for display links on train stations (the instructions and conditions are in
 * {@code trains.schedule}).
 * <p>
 * The component is server-only (Polymer strips it from everything sent to players), like all components of this
 * port.
 */
public final class SchedulesRegistry {
	private SchedulesRegistry() {}

	/** Create's AllDataComponents.TRAIN_SCHEDULE */
	public static final DataComponentType<CompoundTag> TRAIN_SCHEDULE = component("train_schedule",
		DataComponentType.<CompoundTag>builder()
			.persistent(CompoundTag.CODEC)
			.build());

	public static final ScheduleItem SCHEDULE = ModItems.register("schedule", ScheduleItem::new, new Item.Properties());

	// ---- display sources (Create's AllDisplaySources) ----

	public static final StationSummaryDisplaySource STATION_SUMMARY = DisplaySource.register("station_summary",
		new StationSummaryDisplaySource());

	private static <T> DataComponentType<T> component(String id, DataComponentType<T> type) {
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, CreatePolymer.id(id), type);
		PolymerComponent.registerDataComponent(type);
		return type;
	}

	public static void init() {
		ScheduleItemEntityInteraction.init();
		StationBlockEntity.registerCapabilities();

		DisplaySource.BY_BLOCK.add(TrainsRegistry.TRACK_STATION, STATION_SUMMARY);
		DisplaySource.LEGACY_NAMES.put("track_station_source_station_summary", STATION_SUMMARY);

		// Create's GlobalRailwayManager refreshes the departure predictions every 100 ticks
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			GlobalTrainDisplayData.updateTick = server.overworld()
				.getGameTime() % 100 == 0;
			if (GlobalTrainDisplayData.updateTick)
				GlobalTrainDisplayData.refresh();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> GlobalTrainDisplayData.statusByDestination.clear());
	}
}
