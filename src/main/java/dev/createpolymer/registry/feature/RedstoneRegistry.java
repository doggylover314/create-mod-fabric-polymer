package dev.createpolymer.registry.feature;

import java.util.EnumMap;
import java.util.Map;

import dev.createpolymer.redstone.ItemUseOverrides;
import dev.createpolymer.redstone.RedstoneClientStates;
import dev.createpolymer.redstone.RoseQuartzLampBlock;
import dev.createpolymer.redstone.analogLever.AnalogLeverBlock;
import dev.createpolymer.redstone.analogLever.AnalogLeverBlockEntity;
import dev.createpolymer.redstone.contact.RedstoneContactBlock;
import dev.createpolymer.redstone.deskBell.DeskBellBlock;
import dev.createpolymer.redstone.deskBell.DeskBellBlockEntity;
import dev.createpolymer.redstone.diodes.BrassDiodeBlock;
import dev.createpolymer.redstone.diodes.PoweredLatchBlock;
import dev.createpolymer.redstone.diodes.PulseExtenderBlockEntity;
import dev.createpolymer.redstone.diodes.PulseRepeaterBlockEntity;
import dev.createpolymer.redstone.diodes.PulseTimerBlockEntity;
import dev.createpolymer.redstone.diodes.ToggleLatchBlock;
import dev.createpolymer.redstone.link.LinkHandler;
import dev.createpolymer.redstone.link.RedstoneLinkBlock;
import dev.createpolymer.redstone.link.RedstoneLinkBlockEntity;
import dev.createpolymer.redstone.link.RedstoneLinkNetworkHandler;
import dev.createpolymer.redstone.nixieTube.NixieTubeBlock;
import dev.createpolymer.redstone.nixieTube.NixieTubeBlockEntity;
import dev.createpolymer.redstone.smartObserver.SmartObserverBlock;
import dev.createpolymer.redstone.smartObserver.SmartObserverBlockEntity;
import dev.createpolymer.redstone.thresholdSwitch.ThresholdSwitchBlock;
import dev.createpolymer.redstone.thresholdSwitch.ThresholdSwitchBlockEntity;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Create's redstone blocks: rose quartz lamp, analog lever, redstone contact, desk bell, the pulse repeater, extender
 * and timer, powered latch and powered toggle latch, redstone link, nixie tubes, smart observer and threshold
 * switch (ids "content_observer" and "stockpile_switch", as in Create). Not ported: display link, controller rail,
 * linked controller and the redstone requester.
 */
public final class RedstoneRegistry {
	private RedstoneRegistry() {}

	// ---- blocks ----

	// Create's SharedProperties.softMetal is a gold block
	private static BlockBehaviour.Properties softMetal() {
		return BlockBehaviour.Properties.of().strength(3f, 6f).sound(SoundType.METAL).noOcclusion();
	}

	public static final RoseQuartzLampBlock ROSE_QUARTZ_LAMP = ModBlocks.register("rose_quartz_lamp", RoseQuartzLampBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.REDSTONE_LAMP)
			.mapColor(MapColor.TERRACOTTA_PINK)
			.lightLevel(s -> s.getValue(RoseQuartzLampBlock.POWERING) ? 15 : 0)
			.isRedstoneConductor((state, level, pos) -> false));

	public static final AnalogLeverBlock ANALOG_LEVER = ModBlocks.register("analog_lever", AnalogLeverBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.LEVER));

	public static final RedstoneContactBlock REDSTONE_CONTACT = ModBlocks.register("redstone_contact", RedstoneContactBlock::new,
		ModBlocks.stone().mapColor(MapColor.COLOR_GRAY)
			.isRedstoneConductor((state, level, pos) -> false));

	public static final DeskBellBlock DESK_BELL = ModBlocks.register("desk_bell", DeskBellBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.SAND));

	public static final BrassDiodeBlock PULSE_REPEATER = ModBlocks.register("pulse_repeater", BrassDiodeBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.REPEATER));
	public static final BrassDiodeBlock PULSE_EXTENDER = ModBlocks.register("pulse_extender", BrassDiodeBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.REPEATER));
	public static final BrassDiodeBlock PULSE_TIMER = ModBlocks.register("pulse_timer", BrassDiodeBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.REPEATER));
	public static final PoweredLatchBlock POWERED_LATCH = ModBlocks.register("powered_latch", PoweredLatchBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.REPEATER));
	public static final ToggleLatchBlock POWERED_TOGGLE_LATCH = ModBlocks.register("powered_toggle_latch", ToggleLatchBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.REPEATER));

	public static final RedstoneLinkBlock REDSTONE_LINK = ModBlocks.register("redstone_link", RedstoneLinkBlock::new,
		ModBlocks.wooden().mapColor(MapColor.TERRACOTTA_BROWN).forceSolidOn());

	public static final NixieTubeBlock ORANGE_NIXIE_TUBE = registerNixieTube(DyeColor.ORANGE, "nixie_tube");
	/** Create's NIXIE_TUBES: one block per dye colour, the orange one is "nixie_tube" and the only one with an item. */
	public static final Map<DyeColor, NixieTubeBlock> NIXIE_TUBES = new EnumMap<>(DyeColor.class);
	static {
		for (DyeColor color : DyeColor.values())
			NIXIE_TUBES.put(color, color == DyeColor.ORANGE ? ORANGE_NIXIE_TUBE
				: registerNixieTube(color, color.getSerializedName() + "_nixie_tube"));
	}

	private static NixieTubeBlock registerNixieTube(DyeColor color, String id) {
		return ModBlocks.register(id, p -> new NixieTubeBlock(p, color),
			softMetal().lightLevel(s -> 5)
				.mapColor(color.getMapColor())
				.forceSolidOn());
	}

	public static final SmartObserverBlock SMART_OBSERVER = ModBlocks.register("content_observer", SmartObserverBlock::new,
		ModBlocks.stone().mapColor(MapColor.TERRACOTTA_BROWN)
			.isRedstoneConductor((state, level, pos) -> false));

	public static final ThresholdSwitchBlock THRESHOLD_SWITCH = ModBlocks.register("stockpile_switch", ThresholdSwitchBlock::new,
		ModBlocks.stone().mapColor(MapColor.TERRACOTTA_BROWN)
			.isRedstoneConductor((state, level, pos) -> false));

	// ---- block entities ----

	public static final BlockEntityType<AnalogLeverBlockEntity> ANALOG_LEVER_BLOCK_ENTITY = ModBlockEntities.register("analog_lever",
		(pos, state) -> new AnalogLeverBlockEntity(RedstoneRegistry.ANALOG_LEVER_BLOCK_ENTITY, pos, state), ANALOG_LEVER);

	public static final BlockEntityType<DeskBellBlockEntity> DESK_BELL_BLOCK_ENTITY = ModBlockEntities.register("desk_bell",
		(pos, state) -> new DeskBellBlockEntity(RedstoneRegistry.DESK_BELL_BLOCK_ENTITY, pos, state), DESK_BELL);

	public static final BlockEntityType<PulseRepeaterBlockEntity> PULSE_REPEATER_BLOCK_ENTITY = ModBlockEntities.register("pulse_repeater",
		(pos, state) -> new PulseRepeaterBlockEntity(RedstoneRegistry.PULSE_REPEATER_BLOCK_ENTITY, pos, state), PULSE_REPEATER);
	public static final BlockEntityType<PulseExtenderBlockEntity> PULSE_EXTENDER_BLOCK_ENTITY = ModBlockEntities.register("pulse_extender",
		(pos, state) -> new PulseExtenderBlockEntity(RedstoneRegistry.PULSE_EXTENDER_BLOCK_ENTITY, pos, state), PULSE_EXTENDER);
	public static final BlockEntityType<PulseTimerBlockEntity> PULSE_TIMER_BLOCK_ENTITY = ModBlockEntities.register("pulse_timer",
		(pos, state) -> new PulseTimerBlockEntity(RedstoneRegistry.PULSE_TIMER_BLOCK_ENTITY, pos, state), PULSE_TIMER);

	public static final BlockEntityType<RedstoneLinkBlockEntity> REDSTONE_LINK_BLOCK_ENTITY = ModBlockEntities.register("redstone_link",
		(pos, state) -> new RedstoneLinkBlockEntity(RedstoneRegistry.REDSTONE_LINK_BLOCK_ENTITY, pos, state), REDSTONE_LINK);

	public static final BlockEntityType<NixieTubeBlockEntity> NIXIE_TUBE_BLOCK_ENTITY = ModBlockEntities.register("nixie_tube",
		(pos, state) -> new NixieTubeBlockEntity(RedstoneRegistry.NIXIE_TUBE_BLOCK_ENTITY, pos, state),
		NIXIE_TUBES.values().toArray(new Block[0]));

	public static final BlockEntityType<SmartObserverBlockEntity> SMART_OBSERVER_BLOCK_ENTITY = ModBlockEntities.register("content_observer",
		(pos, state) -> new SmartObserverBlockEntity(RedstoneRegistry.SMART_OBSERVER_BLOCK_ENTITY, pos, state), SMART_OBSERVER);

	public static final BlockEntityType<ThresholdSwitchBlockEntity> THRESHOLD_SWITCH_BLOCK_ENTITY = ModBlockEntities.register("stockpile_switch",
		(pos, state) -> new ThresholdSwitchBlockEntity(RedstoneRegistry.THRESHOLD_SWITCH_BLOCK_ENTITY, pos, state), THRESHOLD_SWITCH);

	// ---- items ----

	/** The part of the redstone link's recipe (Create's AllItems.TRANSMITTER). */
	public static final Item TRANSMITTER = ModItems.simple("transmitter");

	public static final Item ROSE_QUARTZ_LAMP_ITEM = ModItems.block(ROSE_QUARTZ_LAMP);
	public static final Item ANALOG_LEVER_ITEM = ModItems.block(ANALOG_LEVER);
	public static final Item REDSTONE_CONTACT_ITEM = ModItems.block(REDSTONE_CONTACT);
	public static final Item DESK_BELL_ITEM = ModItems.block(DESK_BELL);
	public static final Item PULSE_REPEATER_ITEM = ModItems.block(PULSE_REPEATER);
	public static final Item PULSE_EXTENDER_ITEM = ModItems.block(PULSE_EXTENDER);
	public static final Item PULSE_TIMER_ITEM = ModItems.block(PULSE_TIMER);
	public static final Item POWERED_LATCH_ITEM = ModItems.block(POWERED_LATCH);
	public static final Item POWERED_TOGGLE_LATCH_ITEM = ModItems.block(POWERED_TOGGLE_LATCH);
	public static final Item REDSTONE_LINK_ITEM = ModItems.block(REDSTONE_LINK);
	public static final Item ORANGE_NIXIE_TUBE_ITEM = ModItems.block(ORANGE_NIXIE_TUBE);
	public static final Item SMART_OBSERVER_ITEM = ModItems.block(SMART_OBSERVER);
	public static final Item THRESHOLD_SWITCH_ITEM = ModItems.block(THRESHOLD_SWITCH);

	public static void init() {
		RedstoneClientStates.init();
		LinkHandler.init();
		ItemUseOverrides.addBlock(ANALOG_LEVER);
		ServerLevelEvents.LOAD.register((server, level) -> RedstoneLinkNetworkHandler.INSTANCE.onLoadWorld(level));
		ServerLevelEvents.UNLOAD.register((server, level) -> RedstoneLinkNetworkHandler.INSTANCE.onUnloadWorld(level));
	}
}
