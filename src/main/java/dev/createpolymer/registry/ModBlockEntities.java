package dev.createpolymer.registry;

import java.util.Set;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.kinetics.crank.HandCrankBlockEntity;
import dev.createpolymer.kinetics.gearbox.GearboxBlockEntity;
import dev.createpolymer.kinetics.millstone.MillstoneBlockEntity;
import dev.createpolymer.kinetics.mixer.MechanicalMixerBlockEntity;
import dev.createpolymer.kinetics.motor.CreativeMotorBlockEntity;
import dev.createpolymer.kinetics.press.MechanicalPressBlockEntity;
import dev.createpolymer.kinetics.simple.SimpleKineticBlockEntity;
import dev.createpolymer.kinetics.waterwheel.WaterWheelBlockEntity;
import dev.createpolymer.processing.basin.BasinBlockEntity;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
	private ModBlockEntities() {}

	public static final BlockEntityType<SimpleKineticBlockEntity> SIMPLE_KINETIC = register("simple_kinetic",
		SimpleKineticBlockEntity::new, ModBlocks.SHAFT, ModBlocks.COGWHEEL, ModBlocks.LARGE_COGWHEEL);
	public static final BlockEntityType<GearboxBlockEntity> GEARBOX = register("gearbox", GearboxBlockEntity::new, ModBlocks.GEARBOX);
	public static final BlockEntityType<HandCrankBlockEntity> HAND_CRANK = register("hand_crank", HandCrankBlockEntity::new, ModBlocks.HAND_CRANK);
	public static final BlockEntityType<CreativeMotorBlockEntity> CREATIVE_MOTOR = register("motor", CreativeMotorBlockEntity::new, ModBlocks.CREATIVE_MOTOR);
	public static final BlockEntityType<WaterWheelBlockEntity> WATER_WHEEL = register("water_wheel", WaterWheelBlockEntity::new, ModBlocks.WATER_WHEEL);
	public static final BlockEntityType<MillstoneBlockEntity> MILLSTONE = register("millstone", MillstoneBlockEntity::new, ModBlocks.MILLSTONE);
	public static final BlockEntityType<MechanicalPressBlockEntity> MECHANICAL_PRESS = register("mechanical_press", MechanicalPressBlockEntity::new, ModBlocks.MECHANICAL_PRESS);
	public static final BlockEntityType<MechanicalMixerBlockEntity> MECHANICAL_MIXER = register("mechanical_mixer", MechanicalMixerBlockEntity::new, ModBlocks.MECHANICAL_MIXER);
	public static final BlockEntityType<BasinBlockEntity> BASIN = register("basin", BasinBlockEntity::new, ModBlocks.BASIN);

	private static <T extends BlockEntity> BlockEntityType<T> register(String name, BlockEntityType.BlockEntitySupplier<T> factory, Block... blocks) {
		BlockEntityType<T> type = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, CreatePolymer.id(name),
			new BlockEntityType<>(factory, Set.of(blocks)));
		// Keep these block entities server-side; players never receive them.
		PolymerBlockUtils.registerBlockEntity(type);
		return type;
	}

	public static void init() {
	}
}
