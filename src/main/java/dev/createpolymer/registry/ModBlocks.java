package dev.createpolymer.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.kinetics.base.BlockStressValues;
import dev.createpolymer.kinetics.crank.HandCrankBlock;
import dev.createpolymer.kinetics.gearbox.GearboxBlock;
import dev.createpolymer.kinetics.millstone.MillstoneBlock;
import dev.createpolymer.kinetics.mixer.MechanicalMixerBlock;
import dev.createpolymer.kinetics.motor.CreativeMotorBlock;
import dev.createpolymer.kinetics.press.MechanicalPressBlock;
import dev.createpolymer.kinetics.simple.CogWheelBlock;
import dev.createpolymer.kinetics.simple.ShaftBlock;
import dev.createpolymer.kinetics.waterwheel.LargeWaterWheelBlock;
import dev.createpolymer.kinetics.waterwheel.WaterWheelBlock;
import dev.createpolymer.kinetics.waterwheel.WaterWheelStructuralBlock;
import dev.createpolymer.processing.basin.BasinBlock;
import dev.createpolymer.processing.casing.CasingBlock;
import dev.createpolymer.kinetics.chaindrive.ChainDriveBlock;
import dev.createpolymer.logistics.depot.DepotBlock;
import dev.createpolymer.kinetics.gauge.GaugeBlock;
import dev.createpolymer.kinetics.encased.EncasedCogwheelBlock;
import dev.createpolymer.kinetics.encased.EncasedShaftBlock;
import dev.createpolymer.kinetics.transmission.GearshiftBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {
	private ModBlocks() {}

	public static final List<Block> ALL = new ArrayList<>();

	// Create's SharedProperties.stone / wooden
	public static BlockBehaviour.Properties stone() {
		return BlockBehaviour.Properties.of().strength(1.5f, 6f).sound(SoundType.STONE).noOcclusion();
	}

	public static BlockBehaviour.Properties wooden() {
		return BlockBehaviour.Properties.of().strength(2f, 3f).sound(SoundType.WOOD).noOcclusion();
	}

	public static final CasingBlock ANDESITE_CASING = register("andesite_casing", CasingBlock::new,
		BlockBehaviour.Properties.of().strength(2f, 3f).sound(SoundType.WOOD).mapColor(MapColor.PODZOL));

	public static final ShaftBlock SHAFT = register("shaft", ShaftBlock::new,
		stone().mapColor(MapColor.METAL).forceSolidOff());
	public static final CogWheelBlock COGWHEEL = register("cogwheel", CogWheelBlock::small,
		stone().sound(SoundType.WOOD).mapColor(MapColor.DIRT));
	public static final CogWheelBlock LARGE_COGWHEEL = register("large_cogwheel", CogWheelBlock::large,
		stone().sound(SoundType.WOOD).mapColor(MapColor.DIRT));
	public static final GearboxBlock GEARBOX = register("gearbox", GearboxBlock::new,
		stone().mapColor(MapColor.PODZOL).pushReaction(PushReaction.PUSH));
	public static final CreativeMotorBlock CREATIVE_MOTOR = register("creative_motor", CreativeMotorBlock::new,
		stone().mapColor(MapColor.COLOR_PURPLE).strength(-1, 3600000));
	public static final WaterWheelBlock WATER_WHEEL = register("water_wheel", WaterWheelBlock::new,
		wooden().mapColor(MapColor.DIRT));
	public static final HandCrankBlock HAND_CRANK = register("hand_crank", HandCrankBlock::new,
		wooden().mapColor(MapColor.PODZOL));
	public static final MillstoneBlock MILLSTONE = register("millstone", MillstoneBlock::new,
		stone().mapColor(MapColor.METAL));
	public static final MechanicalPressBlock MECHANICAL_PRESS = register("mechanical_press", MechanicalPressBlock::new,
		stone().mapColor(MapColor.PODZOL));
	public static final MechanicalMixerBlock MECHANICAL_MIXER = register("mechanical_mixer", MechanicalMixerBlock::new,
		stone().mapColor(MapColor.STONE));
	public static final BasinBlock BASIN = register("basin", BasinBlock::new,
		stone().mapColor(MapColor.COLOR_GRAY).sound(SoundType.NETHERITE_BLOCK));
	public static final EncasedShaftBlock ANDESITE_ENCASED_SHAFT = register("andesite_encased_shaft",
		p -> new EncasedShaftBlock(p, () -> ModBlocks.ANDESITE_CASING, "andesite_encased_shaft"), wooden().mapColor(MapColor.PODZOL));
	public static final EncasedCogwheelBlock ANDESITE_ENCASED_COGWHEEL = register("andesite_encased_cogwheel",
		p -> new EncasedCogwheelBlock(p, false, () -> ModBlocks.ANDESITE_CASING, "andesite_encased_cogwheel"), wooden().mapColor(MapColor.PODZOL));
	public static final EncasedCogwheelBlock ANDESITE_ENCASED_LARGE_COGWHEEL = register("andesite_encased_large_cogwheel",
		p -> new EncasedCogwheelBlock(p, true, () -> ModBlocks.ANDESITE_CASING, "andesite_encased_large_cogwheel"), wooden().mapColor(MapColor.PODZOL));
	public static final GearshiftBlock GEARSHIFT = register("gearshift", p -> new GearshiftBlock(p, false),
		wooden().mapColor(MapColor.PODZOL));
	public static final GearshiftBlock CLUTCH = register("clutch", p -> new GearshiftBlock(p, true),
		wooden().mapColor(MapColor.PODZOL));

	public static final ChainDriveBlock ENCASED_CHAIN_DRIVE = register("encased_chain_drive", ChainDriveBlock::new,
		stone().mapColor(MapColor.PODZOL));

	public static final GaugeBlock SPEEDOMETER = register("speedometer", p -> new GaugeBlock(p, false),
		stone().mapColor(MapColor.PODZOL));
	public static final GaugeBlock STRESSOMETER = register("stressometer", p -> new GaugeBlock(p, true),
		stone().mapColor(MapColor.PODZOL));

	public static final LargeWaterWheelBlock LARGE_WATER_WHEEL = register("large_water_wheel", LargeWaterWheelBlock::new,
		wooden().mapColor(MapColor.DIRT));
	public static final WaterWheelStructuralBlock WATER_WHEEL_STRUCTURAL = register("water_wheel_structure", WaterWheelStructuralBlock::new,
		wooden().mapColor(MapColor.DIRT).noLootTable());

	public static final DepotBlock DEPOT = register("depot", DepotBlock::new,
		stone().mapColor(MapColor.COLOR_GRAY).sound(SoundType.NETHERITE_BLOCK));

	public static <T extends Block> T register(String name, Function<BlockBehaviour.Properties, T> factory, BlockBehaviour.Properties props) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, CreatePolymer.id(name));
		T block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(props.setId(key)));
		ALL.add(block);
		return block;
	}

	public static void init() {
		// Stress values from Create's AllBlocks (impact = SU per RPM consumed, capacity = SU per RPM provided).
		BlockStressValues.setImpact(MILLSTONE, 4);
		BlockStressValues.setImpact(MECHANICAL_PRESS, 8);
		BlockStressValues.setImpact(MECHANICAL_MIXER, 4);
		BlockStressValues.setCapacity(HAND_CRANK, 8);
		BlockStressValues.setGeneratorSpeed(HAND_CRANK, 32);
		BlockStressValues.setCapacity(WATER_WHEEL, 32);
		BlockStressValues.setCapacity(CREATIVE_MOTOR, 16384);
		BlockStressValues.setCapacity(LARGE_WATER_WHEEL, 128);
	}
}
