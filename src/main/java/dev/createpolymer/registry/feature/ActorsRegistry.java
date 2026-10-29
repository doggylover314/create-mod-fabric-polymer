package dev.createpolymer.registry.feature;

import java.util.EnumMap;
import java.util.Map;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.api.behaviour.interaction.MovingInteractionBehaviour;
import dev.createpolymer.api.behaviour.movement.MovementBehaviour;
import dev.createpolymer.api.contraption.storage.fluid.MountedFluidStorageType;
import dev.createpolymer.api.contraption.storage.item.MountedItemStorageType;
import dev.createpolymer.contraptions.actors.contraptionControls.ContraptionControlsBlock;
import dev.createpolymer.contraptions.actors.contraptionControls.ContraptionControlsBlockEntity;
import dev.createpolymer.contraptions.actors.contraptionControls.ContraptionControlsMovement;
import dev.createpolymer.contraptions.actors.contraptionControls.ContraptionControlsMovingInteraction;
import dev.createpolymer.contraptions.actors.harvester.HarvesterBlock;
import dev.createpolymer.contraptions.actors.harvester.HarvesterMovementBehaviour;
import dev.createpolymer.contraptions.actors.plough.PloughBlock;
import dev.createpolymer.contraptions.actors.plough.PloughMovementBehaviour;
import dev.createpolymer.contraptions.actors.psi.PortableFluidInterfaceBlockEntity;
import dev.createpolymer.contraptions.actors.psi.PortableItemInterfaceBlockEntity;
import dev.createpolymer.contraptions.actors.psi.PortableStorageInterfaceBlock;
import dev.createpolymer.contraptions.actors.psi.PortableStorageInterfaceBlockEntity;
import dev.createpolymer.contraptions.actors.psi.PortableStorageInterfaceMovement;
import dev.createpolymer.contraptions.actors.roller.RollerBlock;
import dev.createpolymer.contraptions.actors.roller.RollerBlockEntity;
import dev.createpolymer.contraptions.actors.roller.RollerBlockItem;
import dev.createpolymer.contraptions.actors.roller.RollerMovementBehaviour;
import dev.createpolymer.contraptions.actors.seat.SeatBlock;
import dev.createpolymer.contraptions.actors.seat.SeatEntity;
import dev.createpolymer.contraptions.actors.seat.SeatInteractionBehaviour;
import dev.createpolymer.contraptions.actors.seat.SeatMovementBehaviour;
import dev.createpolymer.contraptions.behaviour.BellMovementBehaviour;
import dev.createpolymer.contraptions.behaviour.dispenser.storage.DispenserMountedStorageType;
import dev.createpolymer.equipment.toolbox.ToolboxMountedStorageType;
import dev.createpolymer.fluids.tank.storage.FluidTankMountedStorageType;
import dev.createpolymer.fluids.tank.storage.creative.CreativeFluidTankMountedStorageType;
import dev.createpolymer.kinetics.deployer.DeployerMovementBehaviour;
import dev.createpolymer.kinetics.deployer.DeployerMovingInteraction;
import dev.createpolymer.kinetics.drill.DrillMovementBehaviour;
import dev.createpolymer.kinetics.saw.SawMovementBehaviour;
import dev.createpolymer.logistics.crate.CreativeCrateMountedStorageType;
import dev.createpolymer.logistics.funnel.FunnelMovementBehaviour;
import dev.createpolymer.processing.basin.BasinMovementBehaviour;
import dev.createpolymer.processing.burner.BlazeBurnerMovementBehaviour;
import dev.createpolymer.redstone.RedstoneClientStates;
import dev.createpolymer.redstone.contact.ContactMovementBehaviour;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * The actors that contraptions carry (seats, harvester, plough, roller, portable storage interfaces, contraption
 * controls) and the movement behaviours and mounted storage types of blocks that are registered elsewhere (drill,
 * saw, deployer, funnels, blaze burner, basin, redstone contact, bells, creative crate, toolboxes, fluid tanks,
 * dispensers).
 */
public final class ActorsRegistry {
	private ActorsRegistry() {}

	// Create's CKinetics / CLogistics defaults
	public static boolean seatHostileMobs = true;
	public static boolean harvesterReplants = true;
	public static boolean harvestPartiallyGrown = false;
	public static int rollerFillDepth = 12;
	public static int psiTimeout = 20;

	// ---- tags (data files in data/create/tags) ----

	public static final TagKey<Block> NON_HARVESTABLE = TagKey.create(Registries.BLOCK, CreatePolymer.id("non_harvestable"));
	public static final TagKey<Block> SUGAR_CANE_VARIANTS = TagKey.create(Registries.BLOCK, CreatePolymer.id("sugar_cane_variants"));
	public static final TagKey<Block> PLOUGH_BLACKLIST = TagKey.create(Registries.BLOCK, CreatePolymer.id("plough_blacklist"));
	public static final TagKey<Block> PLOUGH_WHITELIST = TagKey.create(Registries.BLOCK, CreatePolymer.id("plough_whitelist"));
	public static final TagKey<EntityType<?>> IGNORE_SEAT = TagKey.create(Registries.ENTITY_TYPE, CreatePolymer.id("ignore_seat"));
	public static final TagKey<Item> CONTRAPTION_CONTROLLED = TagKey.create(Registries.ITEM, CreatePolymer.id("contraption_controlled"));

	// ---- mounted storage types (Create's AllMountedStorageTypes) ----

	public static final CreativeCrateMountedStorageType CREATIVE_CRATE_MOUNTED_STORAGE = MountedItemStorageType
		.register(CreatePolymer.id("creative_crate"), new CreativeCrateMountedStorageType());
	public static final ToolboxMountedStorageType TOOLBOX_MOUNTED_STORAGE = MountedItemStorageType
		.register(CreatePolymer.id("toolbox"), new ToolboxMountedStorageType());
	public static final DispenserMountedStorageType DISPENSER_MOUNTED_STORAGE = MountedItemStorageType
		.register(CreatePolymer.id("dispenser"), new DispenserMountedStorageType());
	public static final FluidTankMountedStorageType FLUID_TANK_MOUNTED_STORAGE = MountedFluidStorageType
		.register(CreatePolymer.id("fluid_tank"), new FluidTankMountedStorageType());
	public static final CreativeFluidTankMountedStorageType CREATIVE_FLUID_TANK_MOUNTED_STORAGE = MountedFluidStorageType
		.register(CreatePolymer.id("creative_fluid_tank"), new CreativeFluidTankMountedStorageType());

	// ---- entity ----

	/** Create's AllEntityTypes.SEAT, "create:seat" */
	public static final EntityType<SeatEntity> SEAT = entity("seat", SeatEntity::new, SeatEntity::build);

	// ---- damage ----

	public static final ResourceKey<DamageType> ROLLER_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
		CreatePolymer.id("mechanical_roller"));

	/** Create's CreateDamageSources.roller */
	public static DamageSource rollerDamage(LevelReader level) {
		return new DamageSource(level.registryAccess()
			.lookupOrThrow(Registries.DAMAGE_TYPE)
			.getOrThrow(ROLLER_DAMAGE));
	}

	// ---- blocks ----

	public static final Map<DyeColor, SeatBlock> SEATS = new EnumMap<>(DyeColor.class);

	static {
		for (DyeColor color : DyeColor.values())
			SEATS.put(color, ModBlocks.register(color.getSerializedName() + "_seat", p -> new SeatBlock(p, color),
				ModBlocks.wooden().mapColor(color).bounceRestitution(0.66f).fallDistanceReduction(0.5f)));
	}

	public static final HarvesterBlock MECHANICAL_HARVESTER = ModBlocks.register("mechanical_harvester", HarvesterBlock::new,
		ModBlocks.stone().mapColor(MapColor.METAL).forceSolidOn());
	public static final PloughBlock MECHANICAL_PLOUGH = ModBlocks.register("mechanical_plough", PloughBlock::new,
		ModBlocks.stone().mapColor(MapColor.COLOR_GRAY).forceSolidOn());
	public static final RollerBlock MECHANICAL_ROLLER = ModBlocks.register("mechanical_roller", RollerBlock::new,
		ModBlocks.stone().mapColor(MapColor.COLOR_GRAY));
	public static final PortableStorageInterfaceBlock PORTABLE_STORAGE_INTERFACE = ModBlocks.register(
		"portable_storage_interface", PortableStorageInterfaceBlock::forItems, ModBlocks.stone().mapColor(MapColor.PODZOL));
	public static final PortableStorageInterfaceBlock PORTABLE_FLUID_INTERFACE = ModBlocks.register(
		"portable_fluid_interface", PortableStorageInterfaceBlock::forFluids,
		BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK.weathering().unaffected()).noOcclusion()
			.mapColor(MapColor.TERRACOTTA_LIGHT_GRAY));
	public static final ContraptionControlsBlock CONTRAPTION_CONTROLS = ModBlocks.register("contraption_controls",
		ContraptionControlsBlock::new, ModBlocks.stone().mapColor(MapColor.PODZOL));

	// ---- block entities ----

	public static final BlockEntityType<RollerBlockEntity> MECHANICAL_ROLLER_BLOCK_ENTITY = ModBlockEntities.register(
		"mechanical_roller", (pos, state) -> new RollerBlockEntity(ActorsRegistry.MECHANICAL_ROLLER_BLOCK_ENTITY, pos, state),
		MECHANICAL_ROLLER);
	public static final BlockEntityType<PortableItemInterfaceBlockEntity> PORTABLE_STORAGE_INTERFACE_BLOCK_ENTITY =
		ModBlockEntities.register("portable_storage_interface",
			(pos, state) -> new PortableItemInterfaceBlockEntity(ActorsRegistry.PORTABLE_STORAGE_INTERFACE_BLOCK_ENTITY, pos, state),
			PORTABLE_STORAGE_INTERFACE);
	public static final BlockEntityType<PortableFluidInterfaceBlockEntity> PORTABLE_FLUID_INTERFACE_BLOCK_ENTITY =
		ModBlockEntities.register("portable_fluid_interface",
			(pos, state) -> new PortableFluidInterfaceBlockEntity(ActorsRegistry.PORTABLE_FLUID_INTERFACE_BLOCK_ENTITY, pos, state),
			PORTABLE_FLUID_INTERFACE);
	public static final BlockEntityType<ContraptionControlsBlockEntity> CONTRAPTION_CONTROLS_BLOCK_ENTITY = ModBlockEntities
		.register("contraption_controls",
			(pos, state) -> new ContraptionControlsBlockEntity(ActorsRegistry.CONTRAPTION_CONTROLS_BLOCK_ENTITY, pos, state),
			CONTRAPTION_CONTROLS);

	// ---- items ----

	public static final Map<DyeColor, Item> SEAT_ITEMS = new EnumMap<>(DyeColor.class);

	static {
		for (DyeColor color : DyeColor.values())
			SEAT_ITEMS.put(color, ModItems.block(SEATS.get(color)));
	}

	public static final Item MECHANICAL_HARVESTER_ITEM = ModItems.block(MECHANICAL_HARVESTER);
	public static final Item MECHANICAL_PLOUGH_ITEM = ModItems.block(MECHANICAL_PLOUGH);
	public static final Item MECHANICAL_ROLLER_ITEM = ModItems.register("mechanical_roller",
		p -> new RollerBlockItem(MECHANICAL_ROLLER, p), new Item.Properties().useBlockDescriptionPrefix());
	public static final Item PORTABLE_STORAGE_INTERFACE_ITEM = ModItems.block(PORTABLE_STORAGE_INTERFACE);
	public static final Item PORTABLE_FLUID_INTERFACE_ITEM = ModItems.block(PORTABLE_FLUID_INTERFACE);
	public static final Item CONTRAPTION_CONTROLS_ITEM = ModItems.block(CONTRAPTION_CONTROLS);

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> entity(String name,
		EntityType.EntityFactory<T> factory, java.util.function.UnaryOperator<EntityType.Builder<?>> build) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, CreatePolymer.id(name));
		@SuppressWarnings("unchecked")
		EntityType<T> type = ((EntityType.Builder<T>) build.apply(EntityType.Builder.of(factory, MobCategory.MISC))).build(key);
		Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
		PolymerEntityUtils.registerType(type);
		return type;
	}

	public static void init() {
		// the seat's invisible half block is one of the thin client states
		RedstoneClientStates.init();

		PortableItemInterfaceBlockEntity.registerCapabilities();
		PortableFluidInterfaceBlockEntity.registerCapabilities();

		// mounted storage associations: Create's blocks register their own types, the dispenser type is internal
		CREATIVE_CRATE_MOUNTED_STORAGE.associate(MiscRegistry.CREATIVE_CRATE);
		for (Block toolbox : EquipmentRegistry.TOOLBOXES.values())
			TOOLBOX_MOUNTED_STORAGE.associate(toolbox);
		DISPENSER_MOUNTED_STORAGE.markInternal();
		DISPENSER_MOUNTED_STORAGE.associate(Blocks.DISPENSER);
		DISPENSER_MOUNTED_STORAGE.associate(Blocks.DROPPER);
		FLUID_TANK_MOUNTED_STORAGE.associate(TanksRegistry.FLUID_TANK);
		CREATIVE_FLUID_TANK_MOUNTED_STORAGE.associate(TanksRegistry.CREATIVE_FLUID_TANK);

		// Create's AllBlocks: movementBehaviour(...) / interactionBehaviour(...)
		SeatMovementBehaviour seatMovement = new SeatMovementBehaviour();
		SeatInteractionBehaviour seatInteraction = new SeatInteractionBehaviour();
		for (SeatBlock seat : SEATS.values()) {
			MovementBehaviour.movementBehaviour(seat, seatMovement);
			MovingInteractionBehaviour.interactionBehaviour(seat, seatInteraction);
		}
		MovementBehaviour.movementBehaviour(MECHANICAL_HARVESTER, new HarvesterMovementBehaviour());
		MovementBehaviour.movementBehaviour(MECHANICAL_PLOUGH, new PloughMovementBehaviour());
		MovementBehaviour.movementBehaviour(MECHANICAL_ROLLER, new RollerMovementBehaviour());
		PortableStorageInterfaceMovement psiMovement = new PortableStorageInterfaceMovement();
		MovementBehaviour.movementBehaviour(PORTABLE_STORAGE_INTERFACE, psiMovement);
		MovementBehaviour.movementBehaviour(PORTABLE_FLUID_INTERFACE, psiMovement);
		MovementBehaviour.movementBehaviour(CONTRAPTION_CONTROLS, new ContraptionControlsMovement());
		MovingInteractionBehaviour.interactionBehaviour(CONTRAPTION_CONTROLS, new ContraptionControlsMovingInteraction());

		MovementBehaviour.movementBehaviour(CrushingRegistry.MECHANICAL_DRILL, new DrillMovementBehaviour());
		MovementBehaviour.movementBehaviour(SawRegistry.MECHANICAL_SAW, new SawMovementBehaviour());
		MovementBehaviour.movementBehaviour(DeployerRegistry.DEPLOYER, new DeployerMovementBehaviour());
		MovingInteractionBehaviour.interactionBehaviour(DeployerRegistry.DEPLOYER, new DeployerMovingInteraction());
		MovementBehaviour.movementBehaviour(RedstoneRegistry.REDSTONE_CONTACT, new ContactMovementBehaviour());
		MovementBehaviour.movementBehaviour(FunnelRegistry.ANDESITE_FUNNEL, FunnelMovementBehaviour.andesite());
		MovementBehaviour.movementBehaviour(FunnelRegistry.BRASS_FUNNEL, FunnelMovementBehaviour.brass());
		MovementBehaviour.movementBehaviour(BurnerRegistry.BLAZE_BURNER, new BlazeBurnerMovementBehaviour());
		MovementBehaviour.movementBehaviour(ModBlocks.BASIN, new BasinMovementBehaviour());
		MovementBehaviour.movementBehaviour(RedstoneRegistry.DESK_BELL, new BellMovementBehaviour());
	}
}
