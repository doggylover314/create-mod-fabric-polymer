package dev.createpolymer.registry.feature;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.api.behaviour.display.DisplaySource;
import dev.createpolymer.api.behaviour.display.DisplayTarget;
import dev.createpolymer.kinetics.base.BlockStressValues;
import dev.createpolymer.redstone.displayLink.ClickToLinkBlockItem;
import dev.createpolymer.redstone.displayLink.ClickToLinkBlockItem.ClickToLinkData;
import dev.createpolymer.redstone.displayLink.DisplayLinkBlock;
import dev.createpolymer.redstone.displayLink.DisplayLinkBlockEntity;
import dev.createpolymer.redstone.displayLink.DisplayLinkBlockItem;
import dev.createpolymer.redstone.displayLink.source.AccumulatedItemCountDisplaySource;
import dev.createpolymer.redstone.displayLink.source.BoilerDisplaySource;
import dev.createpolymer.redstone.displayLink.source.DeathCounterDisplaySource;
import dev.createpolymer.redstone.displayLink.source.EnchantPowerDisplaySource;
import dev.createpolymer.redstone.displayLink.source.FillLevelDisplaySource;
import dev.createpolymer.redstone.displayLink.source.FluidAmountDisplaySource;
import dev.createpolymer.redstone.displayLink.source.FluidListDisplaySource;
import dev.createpolymer.redstone.displayLink.source.ItemCountDisplaySource;
import dev.createpolymer.redstone.displayLink.source.ItemListDisplaySource;
import dev.createpolymer.redstone.displayLink.source.ItemNameDisplaySource;
import dev.createpolymer.redstone.displayLink.source.ItemThroughputDisplaySource;
import dev.createpolymer.redstone.displayLink.source.KineticSpeedDisplaySource;
import dev.createpolymer.redstone.displayLink.source.KineticStressDisplaySource;
import dev.createpolymer.redstone.displayLink.source.NixieTubeDisplaySource;
import dev.createpolymer.redstone.displayLink.source.RedstonePowerDisplaySource;
import dev.createpolymer.redstone.displayLink.source.ScoreboardDisplaySource;
import dev.createpolymer.redstone.displayLink.source.StopWatchDisplaySource;
import dev.createpolymer.redstone.displayLink.source.TimeOfDayDisplaySource;
import dev.createpolymer.redstone.displayLink.target.DisplayBoardTarget;
import dev.createpolymer.redstone.displayLink.target.LecternDisplayTarget;
import dev.createpolymer.redstone.displayLink.target.NixieTubeDisplayTarget;
import dev.createpolymer.redstone.displayLink.target.SignDisplayTarget;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.trains.display.FlapDisplayBlock;
import dev.createpolymer.trains.display.FlapDisplayBlockEntity;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.minecraft.core.Registry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Create's display link and the display board, and the display sources and targets (Create's AllDisplaySources and
 * AllDisplayTargets). The station summary and observed train name sources are registered by SchedulesRegistry and
 * SignalsRegistry. Not ported yet: the train status, elevator floor, factory gauge, package address, seat and
 * computer sources.
 * <p>
 * Other features add their own sources and targets with {@link DisplaySource#register},
 * {@link DisplaySource#BY_BLOCK} and {@link DisplayTarget#BY_BLOCK}.
 */
public final class DisplayRegistry {
	private DisplayRegistry() {}

	// Create's SharedProperties.softMetal
	private static BlockBehaviour.Properties softMetal() {
		return BlockBehaviour.Properties.of().strength(3f, 6f).sound(SoundType.METAL).noOcclusion();
	}

	/** The block a display link item remembers to link to, server-only (Create: AllDataComponents.CLICK_TO_LINK_DATA). */
	public static final DataComponentType<ClickToLinkData> CLICK_TO_LINK_DATA = component("click_to_link_data",
		DataComponentType.<ClickToLinkData>builder()
			.persistent(ClickToLinkData.CODEC)
			.networkSynchronized(StreamCodec.composite(
				BlockPos.STREAM_CODEC, ClickToLinkData::selectedPos,
				Identifier.STREAM_CODEC, ClickToLinkData::selectedDim,
				ClickToLinkData::new))
			.build());

	// ---- blocks ----

	public static final DisplayLinkBlock DISPLAY_LINK = ModBlocks.register("display_link", DisplayLinkBlock::new,
		softMetal().mapColor(MapColor.TERRACOTTA_BROWN));

	public static final FlapDisplayBlock DISPLAY_BOARD = ModBlocks.register("display_board", FlapDisplayBlock::new,
		softMetal().mapColor(MapColor.COLOR_GRAY));

	// ---- block entities ----

	public static final BlockEntityType<DisplayLinkBlockEntity> DISPLAY_LINK_BLOCK_ENTITY = ModBlockEntities.register("display_link",
		(pos, state) -> new DisplayLinkBlockEntity(DisplayRegistry.DISPLAY_LINK_BLOCK_ENTITY, pos, state), DISPLAY_LINK);

	public static final BlockEntityType<FlapDisplayBlockEntity> FLAP_DISPLAY_BLOCK_ENTITY = ModBlockEntities.register("flap_display",
		(pos, state) -> new FlapDisplayBlockEntity(DisplayRegistry.FLAP_DISPLAY_BLOCK_ENTITY, pos, state), DISPLAY_BOARD);

	// ---- items ----

	public static final Item DISPLAY_LINK_ITEM = ModItems.register("display_link",
		p -> new DisplayLinkBlockItem(DISPLAY_LINK, p), new Item.Properties().useBlockDescriptionPrefix());
	public static final Item DISPLAY_BOARD_ITEM = ModItems.block(DISPLAY_BOARD);

	// ---- display targets (Create's AllDisplayTargets) ----

	public static final SignDisplayTarget SIGN = DisplayTarget.register("sign", new SignDisplayTarget());
	public static final LecternDisplayTarget LECTERN = DisplayTarget.register("lectern", new LecternDisplayTarget());
	public static final DisplayBoardTarget DISPLAY_BOARD_TARGET = DisplayTarget.register("display_board", new DisplayBoardTarget());
	public static final NixieTubeDisplayTarget NIXIE_TUBE_TARGET = DisplayTarget.register("nixie_tube", new NixieTubeDisplayTarget());

	// ---- display sources (Create's AllDisplaySources) ----

	public static final DeathCounterDisplaySource DEATH_COUNT = DisplaySource.register("death_count", new DeathCounterDisplaySource());
	public static final ScoreboardDisplaySource SCOREBOARD = DisplaySource.register("scoreboard", new ScoreboardDisplaySource());
	public static final EnchantPowerDisplaySource ENCHANT_POWER = DisplaySource.register("enchant_power", new EnchantPowerDisplaySource());
	public static final RedstonePowerDisplaySource REDSTONE_POWER = DisplaySource.register("redstone_power", new RedstonePowerDisplaySource());

	public static final NixieTubeDisplaySource NIXIE_TUBE = DisplaySource.register("nixie_tube", new NixieTubeDisplaySource());
	public static final ItemNameDisplaySource ITEM_NAMES = DisplaySource.register("item_names", new ItemNameDisplaySource());
	public static final BoilerDisplaySource BOILER = DisplaySource.register("boiler", new BoilerDisplaySource());
	public static final FillLevelDisplaySource FILL_LEVEL = DisplaySource.register("fill_level", new FillLevelDisplaySource());

	public static final TimeOfDayDisplaySource TIME_OF_DAY = DisplaySource.register("time_of_day", new TimeOfDayDisplaySource());
	public static final StopWatchDisplaySource STOPWATCH = DisplaySource.register("stopwatch", new StopWatchDisplaySource());

	public static final KineticSpeedDisplaySource KINETIC_SPEED = DisplaySource.register("kinetic_speed", new KineticSpeedDisplaySource());
	public static final KineticStressDisplaySource KINETIC_STRESS = DisplaySource.register("kinetic_stress", new KineticStressDisplaySource());

	public static final AccumulatedItemCountDisplaySource ACCUMULATE_ITEMS = DisplaySource.register("accumulate_items", new AccumulatedItemCountDisplaySource());
	public static final ItemThroughputDisplaySource ITEM_THROUGHPUT = DisplaySource.register("item_throughput", new ItemThroughputDisplaySource());

	public static final ItemCountDisplaySource COUNT_ITEMS = DisplaySource.register("count_items", new ItemCountDisplaySource());
	public static final ItemListDisplaySource LIST_ITEMS = DisplaySource.register("list_items", new ItemListDisplaySource());
	public static final FluidAmountDisplaySource COUNT_FLUIDS = DisplaySource.register("count_fluids", new FluidAmountDisplaySource());
	public static final FluidListDisplaySource LIST_FLUIDS = DisplaySource.register("list_fluids", new FluidListDisplaySource());

	private static <T> DataComponentType<T> component(String id, DataComponentType<T> type) {
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, CreatePolymer.id(id), type);
		PolymerComponent.registerDataComponent(type);
		return type;
	}

	public static void init() {
		// Create: CStress.setNoImpact() on the display board
		BlockStressValues.setImpact(DISPLAY_BOARD, 0);

		// Which blocks the sources read from: Registrate's displaySource transformers and AllDisplaySources.associate
		DisplaySource.BY_BLOCK.add(Blocks.RESPAWN_ANCHOR, DEATH_COUNT);
		DisplaySource.BY_BLOCK_ENTITY.add(BlockEntityTypes.COMMAND_BLOCK, SCOREBOARD);
		DisplaySource.BY_BLOCK_ENTITY.add(BlockEntityTypes.ENCHANTING_TABLE, ENCHANT_POWER);
		DisplaySource.BY_BLOCK.add(Blocks.TARGET, REDSTONE_POWER);

		DisplaySource.BY_BLOCK_ENTITY.add(RedstoneRegistry.NIXIE_TUBE_BLOCK_ENTITY, NIXIE_TUBE);
		DisplaySource.BY_BLOCK.add(BeltRegistry.BELT, ITEM_NAMES);
		DisplaySource.BY_BLOCK.add(ModBlocks.DEPOT, ITEM_NAMES);
		DisplaySource.BY_BLOCK.add(EjectorRegistry.WEIGHTED_EJECTOR, ITEM_NAMES);
		DisplaySource.BY_BLOCK.add(TanksRegistry.FLUID_TANK, BOILER);
		DisplaySource.BY_BLOCK.add(RedstoneRegistry.THRESHOLD_SWITCH, FILL_LEVEL);
		DisplaySource.BY_BLOCK.add(KineticsExtraRegistry.CUCKOO_CLOCK, TIME_OF_DAY);
		DisplaySource.BY_BLOCK.add(KineticsExtraRegistry.CUCKOO_CLOCK, STOPWATCH);
		DisplaySource.BY_BLOCK.add(ModBlocks.SPEEDOMETER, KINETIC_SPEED);
		DisplaySource.BY_BLOCK.add(ModBlocks.STRESSOMETER, KINETIC_STRESS);
		for (var tunnel : new net.minecraft.world.level.block.Block[] { BeltRegistry.ANDESITE_TUNNEL, BeltRegistry.BRASS_TUNNEL }) {
			DisplaySource.BY_BLOCK.add(tunnel, ACCUMULATE_ITEMS);
			DisplaySource.BY_BLOCK.add(tunnel, ITEM_THROUGHPUT);
		}
		DisplaySource.BY_BLOCK.add(RedstoneRegistry.SMART_OBSERVER, COUNT_ITEMS);
		DisplaySource.BY_BLOCK.add(RedstoneRegistry.SMART_OBSERVER, LIST_ITEMS);
		DisplaySource.BY_BLOCK.add(RedstoneRegistry.SMART_OBSERVER, COUNT_FLUIDS);
		DisplaySource.BY_BLOCK.add(RedstoneRegistry.SMART_OBSERVER, LIST_FLUIDS);

		// Targets: AllDisplayTargets' associate and the displayTarget calls in AllBlocks and AllBlockEntityTypes
		DisplayTarget.BY_BLOCK_ENTITY.register(BlockEntityTypes.SIGN, SIGN);
		DisplayTarget.BY_BLOCK_ENTITY.register(BlockEntityTypes.LECTERN, LECTERN);
		DisplayTarget.BY_BLOCK.register(DISPLAY_BOARD, DISPLAY_BOARD_TARGET);
		DisplayTarget.BY_BLOCK_ENTITY.register(RedstoneRegistry.NIXIE_TUBE_BLOCK_ENTITY, NIXIE_TUBE_TARGET);

		// Ids of Create versions before the sources and targets had their own registries
		DisplaySource.LEGACY_NAMES.put("death_count_display_source", DEATH_COUNT);
		DisplaySource.LEGACY_NAMES.put("scoreboard_display_source", SCOREBOARD);
		DisplaySource.LEGACY_NAMES.put("enchant_power_display_source", ENCHANT_POWER);
		DisplaySource.LEGACY_NAMES.put("redstone_power_display_source", REDSTONE_POWER);
		DisplaySource.LEGACY_NAMES.put("nixie_tube_source", NIXIE_TUBE);
		DisplaySource.LEGACY_NAMES.put("belt_source_combine_item_names", ITEM_NAMES);
		DisplaySource.LEGACY_NAMES.put("cuckoo_clock_source_time_of_day", TIME_OF_DAY);
		DisplaySource.LEGACY_NAMES.put("cuckoo_clock_source_stop_watch", STOPWATCH);
		DisplaySource.LEGACY_NAMES.put("speedometer_source_kinetic_speed", KINETIC_SPEED);
		DisplaySource.LEGACY_NAMES.put("stressometer_source_kinetic_stress", KINETIC_STRESS);
		DisplaySource.LEGACY_NAMES.put("fluid_tank_source_boiler_status", BOILER);
		DisplaySource.LEGACY_NAMES.put("andesite_tunnel_source_accumulate_items", ACCUMULATE_ITEMS);
		DisplaySource.LEGACY_NAMES.put("andesite_tunnel_source_item_throughput", ITEM_THROUGHPUT);
		DisplaySource.LEGACY_NAMES.put("brass_tunnel_source_accumulate_items", ACCUMULATE_ITEMS);
		DisplaySource.LEGACY_NAMES.put("brass_tunnel_source_item_throughput", ITEM_THROUGHPUT);
		DisplaySource.LEGACY_NAMES.put("content_observer_source_count_items", COUNT_ITEMS);
		DisplaySource.LEGACY_NAMES.put("content_observer_source_list_items", LIST_ITEMS);
		DisplaySource.LEGACY_NAMES.put("content_observer_source_count_fluids", COUNT_FLUIDS);
		DisplaySource.LEGACY_NAMES.put("content_observer_source_list_fluids", LIST_FLUIDS);
		DisplaySource.LEGACY_NAMES.put("stockpile_switch_source_fill_level", FILL_LEVEL);
		DisplaySource.LEGACY_NAMES.put("depot_source_combine_item_names", ITEM_NAMES);
		DisplaySource.LEGACY_NAMES.put("weighted_ejector_source_combine_item_names", ITEM_NAMES);
		DisplayTarget.LEGACY_NAMES.put("sign_display_target", SIGN);
		DisplayTarget.LEGACY_NAMES.put("lectern_display_target", LECTERN);
		DisplayTarget.LEGACY_NAMES.put("display_board_target", DISPLAY_BOARD_TARGET);
		DisplayTarget.LEGACY_NAMES.put("nixie_tube_target", NIXIE_TUBE_TARGET);

		ClickToLinkBlockItem.init();
	}
}
