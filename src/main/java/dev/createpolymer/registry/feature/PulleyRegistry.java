package dev.createpolymer.registry.feature;

import java.util.function.UnaryOperator;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.api.contraption.BlockMovementChecks;
import dev.createpolymer.api.contraption.BlockMovementChecks.CheckResult;
import dev.createpolymer.api.contraption.ContraptionType;
import dev.createpolymer.contraptions.AbstractContraptionEntity;
import dev.createpolymer.contraptions.elevator.ElevatorContactBlock;
import dev.createpolymer.contraptions.elevator.ElevatorContactBlockEntity;
import dev.createpolymer.contraptions.elevator.ElevatorContraption;
import dev.createpolymer.contraptions.elevator.ElevatorPulleyBlock;
import dev.createpolymer.contraptions.elevator.ElevatorPulleyBlockEntity;
import dev.createpolymer.contraptions.gantry.GantryCarriageBlock;
import dev.createpolymer.contraptions.gantry.GantryCarriageBlockEntity;
import dev.createpolymer.contraptions.gantry.GantryContraption;
import dev.createpolymer.contraptions.gantry.GantryContraptionEntity;
import dev.createpolymer.contraptions.pulley.PulleyBlock;
import dev.createpolymer.contraptions.pulley.PulleyBlockEntity;
import dev.createpolymer.contraptions.pulley.PulleyContraption;
import dev.createpolymer.kinetics.base.BlockStressValues;
import dev.createpolymer.kinetics.gantry.GantryShaftBlock;
import dev.createpolymer.kinetics.gantry.GantryShaftBlockEntity;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Rope pulleys (with their rope and magnet blocks), gantries (shaft and carriage) and elevators (pulley and contact),
 * and the three contraption kinds they move.
 */
public final class PulleyRegistry {
	private PulleyRegistry() {}

	// ---- contraption types (Create's AllContraptionTypes) ----

	public static final ContraptionType PULLEY = ContraptionType.register("pulley", PulleyContraption::new);
	public static final ContraptionType GANTRY = ContraptionType.register("gantry", GantryContraption::new);
	public static final ContraptionType ELEVATOR = ContraptionType.register("elevator", ElevatorContraption::new);

	// ---- entities ----

	public static final EntityType<GantryContraptionEntity> GANTRY_CONTRAPTION = entity("gantry_contraption",
		GantryContraptionEntity::new, AbstractContraptionEntity::build);

	// ---- blocks ----

	/** Create's SharedProperties.softMetal: a gold block. */
	private static BlockBehaviour.Properties softMetal() {
		return BlockBehaviour.Properties.of()
			.strength(3f, 6f)
			.sound(SoundType.METAL);
	}

	public static final PulleyBlock ROPE_PULLEY = ModBlocks.register("rope_pulley", PulleyBlock::new,
		ModBlocks.stone().mapColor(MapColor.PODZOL));
	public static final PulleyBlock.RopeBlock ROPE = ModBlocks.register("rope", PulleyBlock.RopeBlock::new,
		BlockBehaviour.Properties.of()
			.sound(SoundType.WOOL)
			.mapColor(MapColor.COLOR_BROWN)
			.noOcclusion()
			.noLootTable()
			.pushReaction(PushReaction.IMMOVEABLE));
	public static final PulleyBlock.MagnetBlock PULLEY_MAGNET = ModBlocks.register("pulley_magnet",
		PulleyBlock.MagnetBlock::new, ModBlocks.stone()
			.noLootTable()
			.pushReaction(PushReaction.IMMOVEABLE));

	public static final GantryShaftBlock GANTRY_SHAFT = ModBlocks.register("gantry_shaft", GantryShaftBlock::new,
		ModBlocks.stone()
			.mapColor(MapColor.NETHER)
			.forceSolidOn());
	public static final GantryCarriageBlock GANTRY_CARRIAGE = ModBlocks.register("gantry_carriage",
		GantryCarriageBlock::new, ModBlocks.stone()
			.mapColor(MapColor.PODZOL));

	public static final ElevatorPulleyBlock ELEVATOR_PULLEY = ModBlocks.register("elevator_pulley",
		ElevatorPulleyBlock::new, softMetal().mapColor(MapColor.TERRACOTTA_BROWN)
			.noOcclusion());
	public static final ElevatorContactBlock ELEVATOR_CONTACT = ModBlocks.register("elevator_contact",
		ElevatorContactBlock::new, softMetal().mapColor(MapColor.TERRACOTTA_YELLOW)
			.lightLevel(ElevatorContactBlock::getLight)
			.isRedstoneConductor((state, level, pos) -> false));

	// ---- block entities ----

	public static final BlockEntityType<PulleyBlockEntity> ROPE_PULLEY_BLOCK_ENTITY = ModBlockEntities
		.register("rope_pulley", (pos, state) -> new PulleyBlockEntity(PulleyRegistry.ROPE_PULLEY_BLOCK_ENTITY, pos, state),
			ROPE_PULLEY);
	public static final BlockEntityType<ElevatorPulleyBlockEntity> ELEVATOR_PULLEY_BLOCK_ENTITY = ModBlockEntities
		.register("elevator_pulley",
			(pos, state) -> new ElevatorPulleyBlockEntity(PulleyRegistry.ELEVATOR_PULLEY_BLOCK_ENTITY, pos, state),
			ELEVATOR_PULLEY);
	public static final BlockEntityType<ElevatorContactBlockEntity> ELEVATOR_CONTACT_BLOCK_ENTITY = ModBlockEntities
		.register("elevator_contact",
			(pos, state) -> new ElevatorContactBlockEntity(PulleyRegistry.ELEVATOR_CONTACT_BLOCK_ENTITY, pos, state),
			ELEVATOR_CONTACT);
	public static final BlockEntityType<GantryShaftBlockEntity> GANTRY_SHAFT_BLOCK_ENTITY = ModBlockEntities
		.register("gantry_shaft",
			(pos, state) -> new GantryShaftBlockEntity(PulleyRegistry.GANTRY_SHAFT_BLOCK_ENTITY, pos, state),
			GANTRY_SHAFT);
	public static final BlockEntityType<GantryCarriageBlockEntity> GANTRY_PINION_BLOCK_ENTITY = ModBlockEntities
		.register("gantry_pinion",
			(pos, state) -> new GantryCarriageBlockEntity(PulleyRegistry.GANTRY_PINION_BLOCK_ENTITY, pos, state),
			GANTRY_CARRIAGE);

	// ---- items ----

	public static final Item ROPE_PULLEY_ITEM = ModItems.block(ROPE_PULLEY);
	public static final Item GANTRY_SHAFT_ITEM = ModItems.block(GANTRY_SHAFT);
	public static final Item GANTRY_CARRIAGE_ITEM = ModItems.block(GANTRY_CARRIAGE);
	public static final Item ELEVATOR_PULLEY_ITEM = ModItems.block(ELEVATOR_PULLEY);
	public static final Item ELEVATOR_CONTACT_ITEM = ModItems.block(ELEVATOR_CONTACT);

	private static <T extends Entity> EntityType<T> entity(String name, EntityType.EntityFactory<T> factory,
		UnaryOperator<EntityType.Builder<?>> build) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, CreatePolymer.id(name));
		@SuppressWarnings("unchecked")
		EntityType<T> type = ((EntityType.Builder<T>) build.apply(EntityType.Builder.of(factory, MobCategory.MISC)))
			.build(key);
		Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
		PolymerEntityUtils.registerType(type);
		return type;
	}

	/** Create's AllSoundEvents.CONTRAPTION_ASSEMBLE, a mix of vanilla sounds. */
	public static void playContraptionAssemble(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.BLOCKS, .5f, .5f);
		level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, .045f, .74f);
	}

	/** Create's AllSoundEvents.CONTRAPTION_DISASSEMBLE */
	public static void playContraptionDisassemble(Level level, BlockPos pos, float volume, float pitch) {
		level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, .35f * volume, .75f * pitch);
	}

	public static void init() {
		// Create's AllBlocks: CStress.setImpact(4.0); the gantry shaft has no impact
		BlockStressValues.setImpact(ROPE_PULLEY, 4);
		BlockStressValues.setImpact(ELEVATOR_PULLEY, 4);

		// Create's BlockMovementChecksImpl: a pulley that moves a contraption cannot be moved itself, and nothing is
		// supported by the underside of a pulley
		BlockMovementChecks.registerMovementAllowedCheck((state, world, pos) -> {
			if (state.getBlock() instanceof PulleyBlock && world.getBlockEntity(pos) instanceof PulleyBlockEntity pulley)
				return CheckResult.of(!pulley.running);
			return CheckResult.PASS;
		});
		BlockMovementChecks.registerNotSupportiveCheck((state, facing) -> {
			if (state.is(ROPE_PULLEY))
				return CheckResult.of(facing == Direction.DOWN);
			return CheckResult.PASS;
		});
	}
}
