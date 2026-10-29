package dev.createpolymer.registry.feature;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.api.behaviour.interaction.ConductorBlockInteractionBehavior;
import dev.createpolymer.api.behaviour.interaction.MovingInteractionBehaviour;
import dev.createpolymer.api.behaviour.movement.MovementBehaviour;
import dev.createpolymer.api.contraption.ContraptionType;
import dev.createpolymer.contraptions.AbstractContraptionEntity;
import dev.createpolymer.contraptions.actors.trainControls.ControlsBlock;
import dev.createpolymer.contraptions.actors.trainControls.ControlsInteractionBehaviour;
import dev.createpolymer.contraptions.actors.trainControls.ControlsMovementBehaviour;
import dev.createpolymer.contraptions.actors.trainControls.ControlsServerHandler;
import dev.createpolymer.processing.casing.CasingBlock;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.trains.bogey.AbstractBogeyBlock;
import dev.createpolymer.trains.bogey.AllBogeyStyles;
import dev.createpolymer.trains.bogey.BogeySizes;
import dev.createpolymer.trains.bogey.StandardBogeyBlock;
import dev.createpolymer.trains.bogey.StandardBogeyBlockEntity;
import dev.createpolymer.trains.entity.CarriageContraption;
import dev.createpolymer.trains.entity.CarriageContraptionEntity;
import dev.createpolymer.trains.graph.EdgePointType;
import dev.createpolymer.trains.station.StationBlock;
import dev.createpolymer.trains.station.StationBlockEntity;
import dev.createpolymer.trains.track.TrackTargetingBlockItem;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Create's trains: the bogeys, train stations, train controls and the carriages that run on the track graph (see
 * {@link TrackRegistry}).
 */
public final class TrainsRegistry {
	private TrainsRegistry() {}

	// ---- contraption type and entity ----

	/** Create's AllContraptionTypes.CARRIAGE */
	public static final ContraptionType CARRIAGE_CONTRAPTION_TYPE =
		ContraptionType.register("carriage", CarriageContraption::new);

	/** Create's AllEntityTypes.CARRIAGE_CONTRAPTION */
	public static final EntityType<CarriageContraptionEntity> CARRIAGE_CONTRAPTION =
		entity("carriage_contraption", CarriageContraptionEntity::new, AbstractContraptionEntity::build);

	// ---- blocks ----

	/** Create's SharedProperties.softMetal */
	private static BlockBehaviour.Properties softMetal() {
		return BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK)
			.noOcclusion();
	}

	public static final CasingBlock RAILWAY_CASING = ModBlocks.register("railway_casing",
		p -> new CasingBlock(p, "railway_casing"), BlockBehaviour.Properties.of()
			.strength(2f, 3f)
			.mapColor(MapColor.TERRACOTTA_CYAN)
			.sound(SoundType.NETHERITE_BLOCK));

	public static final StationBlock TRACK_STATION = ModBlocks.register("track_station", StationBlock::new,
		softMetal().mapColor(MapColor.PODZOL)
			.sound(SoundType.NETHERITE_BLOCK));

	public static final StandardBogeyBlock SMALL_BOGEY = ModBlocks.register("small_bogey",
		p -> new StandardBogeyBlock(p, BogeySizes.SMALL), softMetal().mapColor(MapColor.PODZOL)
			.sound(SoundType.NETHERITE_BLOCK));
	public static final StandardBogeyBlock LARGE_BOGEY = ModBlocks.register("large_bogey",
		p -> new StandardBogeyBlock(p, BogeySizes.LARGE), softMetal().mapColor(MapColor.PODZOL)
			.sound(SoundType.NETHERITE_BLOCK));

	public static final ControlsBlock CONTROLS = ModBlocks.register("controls", ControlsBlock::new,
		softMetal().mapColor(MapColor.TERRACOTTA_BROWN)
			.sound(SoundType.NETHERITE_BLOCK));

	// ---- block entities ----

	public static final BlockEntityType<StationBlockEntity> TRACK_STATION_BLOCK_ENTITY = ModBlockEntities
		.register("track_station", (pos, state) -> new StationBlockEntity(TrainsRegistry.TRACK_STATION_BLOCK_ENTITY, pos,
			state), TRACK_STATION);
	public static final BlockEntityType<StandardBogeyBlockEntity> BOGEY_BLOCK_ENTITY = ModBlockEntities.register("bogey",
		(pos, state) -> new StandardBogeyBlockEntity(TrainsRegistry.BOGEY_BLOCK_ENTITY, pos, state), SMALL_BOGEY,
		LARGE_BOGEY);

	// ---- items ----

	public static final Item RAILWAY_CASING_ITEM = ModItems.block(RAILWAY_CASING);
	public static final Item TRACK_STATION_ITEM = ModItems.register("track_station",
		p -> new TrackTargetingBlockItem(TRACK_STATION, p, EdgePointType.STATION),
		new Item.Properties().useBlockDescriptionPrefix());
	public static final Item CONTROLS_ITEM = ModItems.block(CONTROLS);

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> entity(String name,
		EntityType.EntityFactory<T> factory, java.util.function.UnaryOperator<EntityType.Builder<?>> build) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, CreatePolymer.id(name));
		@SuppressWarnings("unchecked")
		EntityType<T> type = ((EntityType.Builder<T>) build.apply(EntityType.Builder.of(factory, MobCategory.MISC)))
			.build(key);
		Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
		PolymerEntityUtils.registerType(type);
		return type;
	}

	public static void init() {
		AbstractBogeyBlock.registerStandardBogey(BuiltInRegistries.BLOCK.getKey(SMALL_BOGEY));
		AbstractBogeyBlock.registerStandardBogey(BuiltInRegistries.BLOCK.getKey(LARGE_BOGEY));
		AllBogeyStyles.init();
		BogeySizes.init();

		MovementBehaviour.movementBehaviour(CONTROLS, new ControlsMovementBehaviour());
		MovingInteractionBehaviour.interactionBehaviour(CONTROLS, new ControlsInteractionBehaviour());
		MovingInteractionBehaviour.interactionBehaviour(BurnerRegistry.BLAZE_BURNER,
			new ConductorBlockInteractionBehavior.BlazeBurner());

		// players drive with the movement keys they hold
		ServerTickEvents.END_SERVER_TICK.register(ControlsServerHandler::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ControlsServerHandler.clear());
	}
}
