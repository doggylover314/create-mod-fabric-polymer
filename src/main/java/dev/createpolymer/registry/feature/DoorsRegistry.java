package dev.createpolymer.registry.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import dev.createpolymer.api.behaviour.interaction.MovingInteractionBehaviour;
import dev.createpolymer.api.behaviour.movement.MovementBehaviour;
import dev.createpolymer.contraptions.behaviour.DoorMovingInteraction;
import dev.createpolymer.contraptions.behaviour.TrapdoorMovingInteraction;
import dev.createpolymer.decoration.TrainTrapdoorBlock;
import dev.createpolymer.decoration.bracket.BracketBlock;
import dev.createpolymer.decoration.bracket.BracketBlockItem;
import dev.createpolymer.decoration.girder.GirderBlock;
import dev.createpolymer.decoration.girder.GirderEncasedShaftBlock;
import dev.createpolymer.decoration.placard.PlacardBlock;
import dev.createpolymer.decoration.placard.PlacardBlockEntity;
import dev.createpolymer.decoration.slidingDoor.SlidingDoorBlock;
import dev.createpolymer.decoration.slidingDoor.SlidingDoorBlockEntity;
import dev.createpolymer.decoration.slidingDoor.SlidingDoorMovementBehaviour;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;

/**
 * Create's sliding doors (andesite, brass, copper, train and framed glass doors), the train and glass trapdoors, the
 * metal girder with its shaft-encased variant, the wooden and metal brackets that hold shafts, cogwheels and pipes
 * and the placard. Block ids and item ids are Create's.
 */
public final class DoorsRegistry {
	private DoorsRegistry() {}

	/** Every block this registry added, in registration order. */
	public static final List<Block> BLOCKS = new ArrayList<>();

	static {
		// invisible hitboxes of doors and placards; must be reserved while the mod initialises
		SlidingDoorBlock.requestClientStates();
		PlacardBlock.requestClientStates();
	}

	// Create's SharedProperties.softMetal / copperMetal
	private static Properties softMetal() {
		return Properties.ofFullCopy(Blocks.GOLD_BLOCK);
	}

	private static Properties copperMetal() {
		return Properties.ofFullCopy(Blocks.COPPER_BLOCK.weathering().unaffected());
	}

	private static <T extends Block> T block(String name, Function<Properties, T> factory, Properties properties) {
		T block = ModBlocks.register(name, factory, properties);
		BLOCKS.add(block);
		return block;
	}

	// ---- sliding doors ----

	// BuilderTransformers.slidingDoor: an iron door that needs the right tool
	private static Properties door(MapColor color) {
		return Properties.ofFullCopy(Blocks.IRON_DOOR)
			.requiresCorrectToolForDrops()
			.strength(3.0F, 6.0F)
			.mapColor(color)
			.noOcclusion();
	}

	public static final SlidingDoorBlock ANDESITE_DOOR = block("andesite_door", p -> SlidingDoorBlock.stone(p, true),
		door(MapColor.STONE));
	public static final SlidingDoorBlock BRASS_DOOR = block("brass_door", p -> SlidingDoorBlock.stone(p, false),
		door(MapColor.TERRACOTTA_YELLOW));
	public static final SlidingDoorBlock COPPER_DOOR = block("copper_door", p -> SlidingDoorBlock.stone(p, true),
		door(MapColor.COLOR_ORANGE));
	public static final SlidingDoorBlock TRAIN_DOOR = block("train_door", p -> SlidingDoorBlock.metal(p, false),
		door(MapColor.TERRACOTTA_CYAN));
	public static final SlidingDoorBlock FRAMED_GLASS_DOOR = block("framed_glass_door",
		p -> SlidingDoorBlock.glass(p, false), door(MapColor.NONE));

	public static final List<SlidingDoorBlock> SLIDING_DOORS =
		List.of(ANDESITE_DOOR, BRASS_DOOR, COPPER_DOOR, TRAIN_DOOR, FRAMED_GLASS_DOOR);

	// ---- trapdoors ----

	public static final TrainTrapdoorBlock TRAIN_TRAPDOOR = block("train_trapdoor",
		p -> TrainTrapdoorBlock.metal(p, "train_trapdoor"), softMetal().mapColor(MapColor.TERRACOTTA_CYAN)
			.noOcclusion());
	public static final TrainTrapdoorBlock FRAMED_GLASS_TRAPDOOR = block("framed_glass_trapdoor",
		p -> TrainTrapdoorBlock.glass(p, "framed_glass_trapdoor"), softMetal().mapColor(MapColor.NONE)
			.noOcclusion());

	// ---- girders ----

	public static final GirderBlock METAL_GIRDER = block("metal_girder", GirderBlock::new,
		softMetal().mapColor(MapColor.COLOR_GRAY)
			.sound(SoundType.NETHERITE_BLOCK));
	public static final GirderEncasedShaftBlock METAL_GIRDER_ENCASED_SHAFT = block("metal_girder_encased_shaft",
		GirderEncasedShaftBlock::new, softMetal().mapColor(MapColor.COLOR_GRAY)
			.sound(SoundType.NETHERITE_BLOCK));

	// ---- brackets ----

	public static final BracketBlock WOODEN_BRACKET = block("wooden_bracket", BracketBlock::new,
		Properties.of()
			.sound(SoundType.SCAFFOLDING));
	public static final BracketBlock METAL_BRACKET = block("metal_bracket", BracketBlock::new,
		Properties.of()
			.sound(SoundType.NETHERITE_BLOCK));

	// ---- placard ----

	public static final PlacardBlock PLACARD = block("placard", PlacardBlock::new, copperMetal().forceSolidOn());

	// ---- block entities ----

	public static final BlockEntityType<SlidingDoorBlockEntity> SLIDING_DOOR_BLOCK_ENTITY = ModBlockEntities.register(
		"sliding_door",
		(pos, state) -> new SlidingDoorBlockEntity(DoorsRegistry.SLIDING_DOOR_BLOCK_ENTITY, pos, state), ANDESITE_DOOR,
		BRASS_DOOR, COPPER_DOOR, TRAIN_DOOR, FRAMED_GLASS_DOOR);
	public static final BlockEntityType<KineticBlockEntity> GIRDER_ENCASED_SHAFT_BLOCK_ENTITY =
		ModBlockEntities.register("metal_girder_encased_shaft",
			(pos, state) -> new KineticBlockEntity(DoorsRegistry.GIRDER_ENCASED_SHAFT_BLOCK_ENTITY, pos, state),
			METAL_GIRDER_ENCASED_SHAFT);
	public static final BlockEntityType<PlacardBlockEntity> PLACARD_BLOCK_ENTITY = ModBlockEntities.register("placard",
		(pos, state) -> new PlacardBlockEntity(DoorsRegistry.PLACARD_BLOCK_ENTITY, pos, state), PLACARD);

	// ---- items ----

	public static final Item ANDESITE_DOOR_ITEM = ModItems.block(ANDESITE_DOOR);
	public static final Item BRASS_DOOR_ITEM = ModItems.block(BRASS_DOOR);
	public static final Item COPPER_DOOR_ITEM = ModItems.block(COPPER_DOOR);
	public static final Item TRAIN_DOOR_ITEM = ModItems.block(TRAIN_DOOR);
	public static final Item FRAMED_GLASS_DOOR_ITEM = ModItems.block(FRAMED_GLASS_DOOR);
	public static final Item TRAIN_TRAPDOOR_ITEM = ModItems.block(TRAIN_TRAPDOOR);
	public static final Item FRAMED_GLASS_TRAPDOOR_ITEM = ModItems.block(FRAMED_GLASS_TRAPDOOR);
	public static final Item METAL_GIRDER_ITEM = ModItems.block(METAL_GIRDER);
	public static final Item WOODEN_BRACKET_ITEM = ModItems.register("wooden_bracket",
		p -> new BracketBlockItem(WOODEN_BRACKET, p), new Item.Properties().useBlockDescriptionPrefix());
	public static final Item METAL_BRACKET_ITEM = ModItems.register("metal_bracket",
		p -> new BracketBlockItem(METAL_BRACKET, p), new Item.Properties().useBlockDescriptionPrefix());
	public static final Item PLACARD_ITEM = ModItems.block(PLACARD);

	public static void init() {
		// Create's AllMovementBehaviours / AllInteractionBehaviours for these blocks
		SlidingDoorMovementBehaviour doorMovement = new SlidingDoorMovementBehaviour();
		DoorMovingInteraction doorInteraction = new DoorMovingInteraction();
		for (SlidingDoorBlock door : SLIDING_DOORS) {
			MovementBehaviour.REGISTRY.register(door, doorMovement);
			MovingInteractionBehaviour.REGISTRY.register(door, doorInteraction);
		}
		TrapdoorMovingInteraction trapdoorInteraction = new TrapdoorMovingInteraction();
		MovingInteractionBehaviour.REGISTRY.register(TRAIN_TRAPDOOR, trapdoorInteraction);
		MovingInteractionBehaviour.REGISTRY.register(FRAMED_GLASS_TRAPDOOR, trapdoorInteraction);
	}
}
