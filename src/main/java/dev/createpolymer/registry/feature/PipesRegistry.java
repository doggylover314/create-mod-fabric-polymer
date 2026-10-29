package dev.createpolymer.registry.feature;

import dev.createpolymer.fluids.effect.OpenPipeEffectHandler;
import dev.createpolymer.fluids.hosePulley.HosePulleyBlock;
import dev.createpolymer.fluids.hosePulley.HosePulleyBlockEntity;
import dev.createpolymer.fluids.pipes.EncasedPipeBlock;
import dev.createpolymer.fluids.pipes.FluidPipeBlock;
import dev.createpolymer.fluids.pipes.FluidPipeBlockEntity;
import dev.createpolymer.fluids.pipes.GlassFluidPipeBlock;
import dev.createpolymer.fluids.pipes.SmartFluidPipeBlock;
import dev.createpolymer.fluids.pipes.SmartFluidPipeBlockEntity;
import dev.createpolymer.fluids.pipes.StraightPipeBlockEntity;
import dev.createpolymer.fluids.pipes.valve.FluidValveBlock;
import dev.createpolymer.fluids.pipes.valve.FluidValveBlockEntity;
import dev.createpolymer.fluids.pump.PumpBlock;
import dev.createpolymer.fluids.pump.PumpBlockEntity;
import dev.createpolymer.kinetics.base.BlockStressValues;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Fluid pipes (plain, encased, glass, smart), the fluid valve, the mechanical pump and the hose pulley. */
public final class PipesRegistry {
	private PipesRegistry() {}

	// Create's SharedProperties.copperMetal is a copy of the copper block
	private static BlockBehaviour.Properties copperMetal() {
		return BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK.weathering().unaffected());
	}

	public static final FluidPipeBlock FLUID_PIPE = ModBlocks.register("fluid_pipe", FluidPipeBlock::new,
		copperMetal().noOcclusion().forceSolidOff());
	public static final EncasedPipeBlock ENCASED_FLUID_PIPE = ModBlocks.register("encased_fluid_pipe",
		p -> new EncasedPipeBlock(p, EncasedPipeBlock::copperCasing),
		copperMetal().noOcclusion().mapColor(MapColor.TERRACOTTA_LIGHT_GRAY));
	public static final GlassFluidPipeBlock GLASS_FLUID_PIPE = ModBlocks.register("glass_fluid_pipe", GlassFluidPipeBlock::new,
		copperMetal().noOcclusion());
	public static final PumpBlock MECHANICAL_PUMP = ModBlocks.register("mechanical_pump", PumpBlock::new,
		copperMetal().noOcclusion().mapColor(MapColor.STONE));
	public static final SmartFluidPipeBlock SMART_FLUID_PIPE = ModBlocks.register("smart_fluid_pipe", SmartFluidPipeBlock::new,
		copperMetal().noOcclusion().mapColor(MapColor.TERRACOTTA_YELLOW));
	public static final FluidValveBlock FLUID_VALVE = ModBlocks.register("fluid_valve", FluidValveBlock::new,
		copperMetal().noOcclusion());
	public static final HosePulleyBlock HOSE_PULLEY = ModBlocks.register("hose_pulley", HosePulleyBlock::new,
		copperMetal().noOcclusion());

	public static final BlockEntityType<FluidPipeBlockEntity> FLUID_PIPE_BLOCK_ENTITY = ModBlockEntities.register("fluid_pipe",
		(pos, state) -> new FluidPipeBlockEntity(PipesRegistry.FLUID_PIPE_BLOCK_ENTITY, pos, state), FLUID_PIPE);
	public static final BlockEntityType<FluidPipeBlockEntity> ENCASED_FLUID_PIPE_BLOCK_ENTITY = ModBlockEntities.register("encased_fluid_pipe",
		(pos, state) -> new FluidPipeBlockEntity(PipesRegistry.ENCASED_FLUID_PIPE_BLOCK_ENTITY, pos, state), ENCASED_FLUID_PIPE);
	public static final BlockEntityType<StraightPipeBlockEntity> GLASS_FLUID_PIPE_BLOCK_ENTITY = ModBlockEntities.register("glass_fluid_pipe",
		(pos, state) -> new StraightPipeBlockEntity(PipesRegistry.GLASS_FLUID_PIPE_BLOCK_ENTITY, pos, state), GLASS_FLUID_PIPE);
	public static final BlockEntityType<PumpBlockEntity> MECHANICAL_PUMP_BLOCK_ENTITY = ModBlockEntities.register("mechanical_pump",
		(pos, state) -> new PumpBlockEntity(PipesRegistry.MECHANICAL_PUMP_BLOCK_ENTITY, pos, state), MECHANICAL_PUMP);
	public static final BlockEntityType<SmartFluidPipeBlockEntity> SMART_FLUID_PIPE_BLOCK_ENTITY = ModBlockEntities.register("smart_fluid_pipe",
		(pos, state) -> new SmartFluidPipeBlockEntity(PipesRegistry.SMART_FLUID_PIPE_BLOCK_ENTITY, pos, state), SMART_FLUID_PIPE);
	public static final BlockEntityType<FluidValveBlockEntity> FLUID_VALVE_BLOCK_ENTITY = ModBlockEntities.register("fluid_valve",
		(pos, state) -> new FluidValveBlockEntity(PipesRegistry.FLUID_VALVE_BLOCK_ENTITY, pos, state), FLUID_VALVE);
	public static final BlockEntityType<HosePulleyBlockEntity> HOSE_PULLEY_BLOCK_ENTITY = ModBlockEntities.register("hose_pulley",
		(pos, state) -> new HosePulleyBlockEntity(PipesRegistry.HOSE_PULLEY_BLOCK_ENTITY, pos, state), HOSE_PULLEY);

	public static final Item FLUID_PIPE_ITEM = ModItems.block(FLUID_PIPE);
	public static final Item MECHANICAL_PUMP_ITEM = ModItems.block(MECHANICAL_PUMP);
	public static final Item SMART_FLUID_PIPE_ITEM = ModItems.block(SMART_FLUID_PIPE);
	public static final Item FLUID_VALVE_ITEM = ModItems.block(FLUID_VALVE);
	public static final Item HOSE_PULLEY_ITEM = ModItems.block(HOSE_PULLEY);

	public static void init() {
		// Stress values from Create's AllBlocks
		BlockStressValues.setImpact(MECHANICAL_PUMP, 4);
		BlockStressValues.setImpact(HOSE_PULLEY, 4);

		OpenPipeEffectHandler.registerDefaults();
		HosePulleyBlockEntity.registerCapabilities();
	}
}
