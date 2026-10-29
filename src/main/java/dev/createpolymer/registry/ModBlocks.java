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
import dev.createpolymer.kinetics.waterwheel.WaterWheelBlock;
import dev.createpolymer.processing.basin.BasinBlock;
import dev.createpolymer.processing.casing.CasingBlock;
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
	private static BlockBehaviour.Properties stone() {
		return BlockBehaviour.Properties.of().strength(1.5f, 6f).sound(SoundType.STONE).noOcclusion();
	}

	private static BlockBehaviour.Properties wooden() {
		return BlockBehaviour.Properties.of().strength(2f, 3f).sound(SoundType.WOOD).noOcclusion();
	}

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
	public static final CasingBlock ANDESITE_CASING = register("andesite_casing", CasingBlock::new,
		BlockBehaviour.Properties.of().strength(2f, 3f).sound(SoundType.WOOD).mapColor(MapColor.PODZOL));

	private static <T extends Block> T register(String name, Function<BlockBehaviour.Properties, T> factory, BlockBehaviour.Properties props) {
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
	}
}
