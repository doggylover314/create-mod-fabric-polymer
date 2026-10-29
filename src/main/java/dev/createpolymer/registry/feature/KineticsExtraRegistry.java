package dev.createpolymer.registry.feature;

import java.util.EnumMap;
import java.util.Map;

import dev.createpolymer.kinetics.base.BlockStressValues;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.chaindrive.ChainDriveBlockEntity;
import dev.createpolymer.kinetics.chaindrive.ChainGearshiftBlock;
import dev.createpolymer.kinetics.chaindrive.ChainGearshiftBlockEntity;
import dev.createpolymer.kinetics.clock.CuckooClockBlock;
import dev.createpolymer.kinetics.clock.CuckooClockBlockEntity;
import dev.createpolymer.kinetics.crank.ValveHandleBlock;
import dev.createpolymer.kinetics.crank.ValveHandleBlockEntity;
import dev.createpolymer.kinetics.encased.EncasedCogwheelBlock;
import dev.createpolymer.kinetics.encased.EncasedShaftBlock;
import dev.createpolymer.kinetics.encased.EncasingRegistry;
import dev.createpolymer.kinetics.flywheel.FlywheelBlock;
import dev.createpolymer.kinetics.flywheel.FlywheelBlockEntity;
import dev.createpolymer.kinetics.simple.SimpleKineticBlockEntity;
import dev.createpolymer.kinetics.transmission.sequencer.SequencedGearshiftBlock;
import dev.createpolymer.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import dev.createpolymer.kinetics.turntable.TurntableBlock;
import dev.createpolymer.kinetics.turntable.TurntableBlockEntity;
import dev.createpolymer.kinetics.turntable.TurntableHandler;
import dev.createpolymer.processing.casing.CasingApplication;
import dev.createpolymer.processing.casing.CasingBlock;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Turntable, flywheel, cuckoo clocks, brass and copper casing with the brass encased shafts and cogwheels,
 * valve handles, adjustable chain gearshift and sequenced gearshift.
 */
public final class KineticsExtraRegistry {
	private KineticsExtraRegistry() {}

	// Create's SharedProperties.softMetal / copperMetal (gold and copper block strength and sound)
	private static BlockBehaviour.Properties softMetal() {
		return BlockBehaviour.Properties.of().strength(3f, 6f).sound(SoundType.METAL).noOcclusion();
	}

	private static BlockBehaviour.Properties copperMetal() {
		return BlockBehaviour.Properties.of().strength(3f, 6f).sound(SoundType.COPPER).noOcclusion();
	}

	private static BlockBehaviour.Properties casing(SoundType sound, MapColor color) {
		return BlockBehaviour.Properties.of().strength(1.5f, 6f).sound(sound).mapColor(color);
	}

	// ---- blocks ----

	public static final TurntableBlock TURNTABLE = ModBlocks.register("turntable", TurntableBlock::new,
		ModBlocks.wooden().mapColor(MapColor.PODZOL));
	public static final FlywheelBlock FLYWHEEL = ModBlocks.register("flywheel", FlywheelBlock::new,
		softMetal().mapColor(MapColor.TERRACOTTA_YELLOW));
	public static final CuckooClockBlock CUCKOO_CLOCK = ModBlocks.register("cuckoo_clock", CuckooClockBlock::regular,
		ModBlocks.wooden().mapColor(MapColor.TERRACOTTA_YELLOW));
	public static final CuckooClockBlock MYSTERIOUS_CUCKOO_CLOCK = ModBlocks.register("mysterious_cuckoo_clock", CuckooClockBlock::mysterious,
		ModBlocks.wooden().mapColor(MapColor.TERRACOTTA_YELLOW));

	public static final CasingBlock BRASS_CASING = ModBlocks.register("brass_casing", p -> new CasingBlock(p, "brass_casing"),
		casing(SoundType.WOOD, MapColor.TERRACOTTA_BROWN));
	public static final CasingBlock COPPER_CASING = ModBlocks.register("copper_casing", p -> new CasingBlock(p, "copper_casing"),
		casing(SoundType.COPPER, MapColor.TERRACOTTA_LIGHT_GRAY));

	public static final EncasedShaftBlock BRASS_ENCASED_SHAFT = ModBlocks.register("brass_encased_shaft",
		p -> new EncasedShaftBlock(p, () -> KineticsExtraRegistry.BRASS_CASING, "brass_encased_shaft",
			() -> KineticsExtraRegistry.BRASS_ENCASED_SHAFT_BE),
		ModBlocks.wooden().mapColor(MapColor.TERRACOTTA_BROWN));
	public static final EncasedCogwheelBlock BRASS_ENCASED_COGWHEEL = ModBlocks.register("brass_encased_cogwheel",
		p -> new EncasedCogwheelBlock(p, false, () -> KineticsExtraRegistry.BRASS_CASING, "brass_encased_cogwheel",
			() -> KineticsExtraRegistry.BRASS_ENCASED_COGWHEEL_BE),
		ModBlocks.wooden().mapColor(MapColor.TERRACOTTA_BROWN));
	public static final EncasedCogwheelBlock BRASS_ENCASED_LARGE_COGWHEEL = ModBlocks.register("brass_encased_large_cogwheel",
		p -> new EncasedCogwheelBlock(p, true, () -> KineticsExtraRegistry.BRASS_CASING, "brass_encased_large_cogwheel",
			() -> KineticsExtraRegistry.BRASS_ENCASED_LARGE_COGWHEEL_BE),
		ModBlocks.wooden().mapColor(MapColor.TERRACOTTA_BROWN));

	public static final ChainGearshiftBlock ADJUSTABLE_CHAIN_GEARSHIFT = ModBlocks.register("adjustable_chain_gearshift", ChainGearshiftBlock::new,
		ModBlocks.stone().mapColor(MapColor.NETHER));
	public static final SequencedGearshiftBlock SEQUENCED_GEARSHIFT = ModBlocks.register("sequenced_gearshift", SequencedGearshiftBlock::new,
		ModBlocks.stone().mapColor(MapColor.TERRACOTTA_BROWN));

	public static final ValveHandleBlock COPPER_VALVE_HANDLE = ModBlocks.register("copper_valve_handle", ValveHandleBlock::copper,
		copperMetal());
	public static final Map<DyeColor, ValveHandleBlock> DYED_VALVE_HANDLES = new EnumMap<>(DyeColor.class);
	static {
		for (DyeColor color : DyeColor.values())
			DYED_VALVE_HANDLES.put(color, ModBlocks.register(color.getSerializedName() + "_valve_handle",
				p -> ValveHandleBlock.dyed(p, color), copperMetal().mapColor(color.getMapColor())));
	}

	// ---- block entities ----

	public static final BlockEntityType<TurntableBlockEntity> TURNTABLE_BE = ModBlockEntities.register("turntable",
		(pos, state) -> new TurntableBlockEntity(KineticsExtraRegistry.TURNTABLE_BE, pos, state), TURNTABLE);
	public static final BlockEntityType<FlywheelBlockEntity> FLYWHEEL_BE = ModBlockEntities.register("flywheel",
		(pos, state) -> new FlywheelBlockEntity(KineticsExtraRegistry.FLYWHEEL_BE, pos, state), FLYWHEEL);
	public static final BlockEntityType<CuckooClockBlockEntity> CUCKOO_CLOCK_BE = ModBlockEntities.register("cuckoo_clock",
		(pos, state) -> new CuckooClockBlockEntity(KineticsExtraRegistry.CUCKOO_CLOCK_BE, pos, state), CUCKOO_CLOCK, MYSTERIOUS_CUCKOO_CLOCK);

	// Create shares one block entity type per kind between the andesite and brass encased blocks. These types are
	// separate because ModBlockEntities' valid block sets are fixed.
	public static final BlockEntityType<KineticBlockEntity> BRASS_ENCASED_SHAFT_BE = ModBlockEntities.register("brass_encased_shaft",
		(pos, state) -> new KineticBlockEntity(KineticsExtraRegistry.BRASS_ENCASED_SHAFT_BE, pos, state), BRASS_ENCASED_SHAFT);
	public static final BlockEntityType<SimpleKineticBlockEntity> BRASS_ENCASED_COGWHEEL_BE = ModBlockEntities.register("brass_encased_cogwheel",
		(pos, state) -> new SimpleKineticBlockEntity(KineticsExtraRegistry.BRASS_ENCASED_COGWHEEL_BE, pos, state), BRASS_ENCASED_COGWHEEL);
	public static final BlockEntityType<SimpleKineticBlockEntity> BRASS_ENCASED_LARGE_COGWHEEL_BE = ModBlockEntities.register("brass_encased_large_cogwheel",
		(pos, state) -> new SimpleKineticBlockEntity(KineticsExtraRegistry.BRASS_ENCASED_LARGE_COGWHEEL_BE, pos, state), BRASS_ENCASED_LARGE_COGWHEEL);

	/** The encased chain drive's block entity (ModBlockEntities.ENCASED_SHAFT is shared with the encased shaft). */
	public static final BlockEntityType<ChainDriveBlockEntity> ENCASED_CHAIN_DRIVE_BE = ModBlockEntities.register("encased_chain_drive",
		(pos, state) -> new ChainDriveBlockEntity(KineticsExtraRegistry.ENCASED_CHAIN_DRIVE_BE, pos, state), ModBlocks.ENCASED_CHAIN_DRIVE);
	public static final BlockEntityType<ChainGearshiftBlockEntity> ADJUSTABLE_CHAIN_GEARSHIFT_BE = ModBlockEntities.register("adjustable_chain_gearshift",
		(pos, state) -> new ChainGearshiftBlockEntity(KineticsExtraRegistry.ADJUSTABLE_CHAIN_GEARSHIFT_BE, pos, state), ADJUSTABLE_CHAIN_GEARSHIFT);
	public static final BlockEntityType<SequencedGearshiftBlockEntity> SEQUENCED_GEARSHIFT_BE = ModBlockEntities.register("sequenced_gearshift",
		(pos, state) -> new SequencedGearshiftBlockEntity(KineticsExtraRegistry.SEQUENCED_GEARSHIFT_BE, pos, state), SEQUENCED_GEARSHIFT);
	public static final BlockEntityType<ValveHandleBlockEntity> VALVE_HANDLE_BE = ModBlockEntities.register("valve_handle",
		(pos, state) -> new ValveHandleBlockEntity(KineticsExtraRegistry.VALVE_HANDLE_BE, pos, state), valveHandles());

	private static Block[] valveHandles() {
		Block[] blocks = new Block[DYED_VALVE_HANDLES.size() + 1];
		blocks[0] = COPPER_VALVE_HANDLE;
		int i = 1;
		for (ValveHandleBlock handle : DYED_VALVE_HANDLES.values())
			blocks[i++] = handle;
		return blocks;
	}

	// ---- items ----

	public static final Item TURNTABLE_ITEM = ModItems.block(TURNTABLE);
	public static final Item FLYWHEEL_ITEM = ModItems.block(FLYWHEEL);
	public static final Item CUCKOO_CLOCK_ITEM = ModItems.block(CUCKOO_CLOCK);
	public static final Item MYSTERIOUS_CUCKOO_CLOCK_ITEM = ModItems.block(MYSTERIOUS_CUCKOO_CLOCK);
	public static final Item BRASS_CASING_ITEM = ModItems.block(BRASS_CASING);
	public static final Item COPPER_CASING_ITEM = ModItems.block(COPPER_CASING);
	public static final Item ADJUSTABLE_CHAIN_GEARSHIFT_ITEM = ModItems.block(ADJUSTABLE_CHAIN_GEARSHIFT);
	public static final Item SEQUENCED_GEARSHIFT_ITEM = ModItems.block(SEQUENCED_GEARSHIFT);
	public static final Item COPPER_VALVE_HANDLE_ITEM = ModItems.block(COPPER_VALVE_HANDLE);
	public static final Map<DyeColor, Item> DYED_VALVE_HANDLE_ITEMS = new EnumMap<>(DyeColor.class);
	static {
		for (DyeColor color : DyeColor.values())
			DYED_VALVE_HANDLE_ITEMS.put(color, ModItems.block(DYED_VALVE_HANDLES.get(color)));
	}

	public static void init() {
		// Stress values from Create's AllBlocks: turntable 4, cuckoo clock 1, valve handles provide 8 per RPM at 32 RPM;
		// the flywheel, the chain gearshift and the sequenced gearshift have no impact.
		BlockStressValues.setImpact(TURNTABLE, 4);
		BlockStressValues.setImpact(CUCKOO_CLOCK, 1);
		BlockStressValues.setImpact(MYSTERIOUS_CUCKOO_CLOCK, 1);
		BlockStressValues.setCapacity(COPPER_VALVE_HANDLE, 8);
		BlockStressValues.setGeneratorSpeed(COPPER_VALVE_HANDLE, 32);
		DYED_VALVE_HANDLES.values().forEach(handle -> {
			BlockStressValues.setCapacity(handle, 8);
			BlockStressValues.setGeneratorSpeed(handle, 32);
		});

		TurntableBlock.requestClientState();

		EncasingRegistry.addVariant(ModBlocks.SHAFT, BRASS_ENCASED_SHAFT);
		EncasingRegistry.addVariant(ModBlocks.COGWHEEL, BRASS_ENCASED_COGWHEEL);
		EncasingRegistry.addVariant(ModBlocks.LARGE_COGWHEEL, BRASS_ENCASED_LARGE_COGWHEEL);
		EncasingRegistry.registerEvents();

		// Create's item application recipes (brass ingots only exist once another feature adds them to the tag)
		CasingApplication.add("c:ingots/brass", () -> BRASS_CASING);
		CasingApplication.add("c:ingots/copper", () -> COPPER_CASING);
		CasingApplication.registerEvents();

		ValveHandleBlock.registerEvents();
		ServerTickEvents.END_SERVER_TICK.register(TurntableHandler::tick);
	}
}
