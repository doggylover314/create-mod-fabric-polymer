package dev.createpolymer.registry.feature;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.api.contraption.ContraptionType;
import dev.createpolymer.api.contraption.storage.item.MountedItemStorageType;
import dev.createpolymer.api.contraption.storage.item.chest.ChestMountedStorageType;
import dev.createpolymer.api.contraption.storage.item.simple.SimpleMountedStorageType;
import dev.createpolymer.api.behaviour.interaction.MovingInteractionBehaviour;
import dev.createpolymer.api.behaviour.movement.MovementBehaviour;
import dev.createpolymer.api.registry.SimpleRegistry;
import dev.createpolymer.contraptions.ContraptionHandler;
import dev.createpolymer.contraptions.ControlledContraptionEntity;
import dev.createpolymer.contraptions.OrientedContraptionEntity;
import dev.createpolymer.contraptions.AbstractContraptionEntity;
import dev.createpolymer.contraptions.behaviour.BellMovementBehaviour;
import dev.createpolymer.contraptions.behaviour.CampfireMovementBehaviour;
import dev.createpolymer.contraptions.behaviour.DoorMovingInteraction;
import dev.createpolymer.contraptions.behaviour.FenceGateMovingInteraction;
import dev.createpolymer.contraptions.behaviour.LeverMovingInteraction;
import dev.createpolymer.contraptions.behaviour.TrapdoorMovingInteraction;
import dev.createpolymer.contraptions.glue.SuperGlueEntity;
import dev.createpolymer.contraptions.glue.SuperGlueItem;
import dev.createpolymer.contraptions.piston.MechanicalPistonBlock;
import dev.createpolymer.contraptions.piston.MechanicalPistonBlockEntity;
import dev.createpolymer.contraptions.piston.MechanicalPistonHeadBlock;
import dev.createpolymer.contraptions.piston.PistonContraption;
import dev.createpolymer.contraptions.piston.PistonExtensionPoleBlock;
import dev.createpolymer.impl.contraption.storage.FallbackMountedStorageType;
import dev.createpolymer.kinetics.base.BlockStressValues;
import dev.createpolymer.logistics.depot.storage.DepotMountedStorageType;
import dev.createpolymer.logistics.vault.ItemVaultMountedStorageType;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Contraption core: the contraption entities and types, mounted storage types, super glue, the mechanical piston.
 * <p>
 * To add a contraption kind (bearing, pulley, gantry, minecart, ...): add a line with
 * {@link ContraptionType#register} below, and register its blocks/behaviours in your own registry.
 */
public final class ContraptionRegistry {
	private ContraptionRegistry() {}

	// Create's CKinetics defaults
	public static int maxBlocksMoved = 2048;
	public static int maxChassisRange = 16;
	public static int maxPistonPoles = 64;
	public static int maxRopeLength = 384;
	public static int minimumWindmillSails = 8;
	public static boolean moveItemsToStorage = true;
	public static boolean stabiliseStableContraptions = false;
	public static boolean noDropWhenContraptionReplaceBlocks = false;

	// ---- contraption types (Create's AllContraptionTypes) ----

	public static final ContraptionType PISTON = ContraptionType.register("piston", PistonContraption::new);

	// ---- mounted storage types (Create's AllMountedStorageTypes) ----

	// fallback is special, it is the provider of last resort
	public static final FallbackMountedStorageType FALLBACK_MOUNTED_STORAGE = MountedItemStorageType
		.register(CreatePolymer.id("fallback"), new FallbackMountedStorageType());
	public static final ItemVaultMountedStorageType VAULT_MOUNTED_STORAGE = MountedItemStorageType
		.register(CreatePolymer.id("vault"), new ItemVaultMountedStorageType());
	public static final DepotMountedStorageType DEPOT_MOUNTED_STORAGE = MountedItemStorageType
		.register(CreatePolymer.id("depot"), new DepotMountedStorageType());
	public static final SimpleMountedStorageType.Impl SIMPLE_MOUNTED_STORAGE = MountedItemStorageType
		.register(CreatePolymer.id("simple"), new SimpleMountedStorageType.Impl());
	public static final ChestMountedStorageType CHEST_MOUNTED_STORAGE = MountedItemStorageType
		.register(CreatePolymer.id("chest"), new ChestMountedStorageType());

	// ---- entities ----

	public static final EntityType<ControlledContraptionEntity> CONTROLLED_CONTRAPTION = entity("contraption",
		ControlledContraptionEntity::new, AbstractContraptionEntity::build);
	public static final EntityType<OrientedContraptionEntity> ORIENTED_CONTRAPTION = entity("stationary_contraption",
		OrientedContraptionEntity::new, AbstractContraptionEntity::build);
	public static final EntityType<SuperGlueEntity> SUPER_GLUE = entity("super_glue", SuperGlueEntity::new,
		SuperGlueEntity::build);

	// ---- piston ----

	public static final MechanicalPistonBlock MECHANICAL_PISTON = ModBlocks.register("mechanical_piston",
		MechanicalPistonBlock::normal, ModBlocks.stone().mapColor(MapColor.PODZOL));
	public static final MechanicalPistonBlock STICKY_MECHANICAL_PISTON = ModBlocks.register("sticky_mechanical_piston",
		MechanicalPistonBlock::sticky, ModBlocks.stone().mapColor(MapColor.PODZOL));
	public static final PistonExtensionPoleBlock PISTON_EXTENSION_POLE = ModBlocks.register("piston_extension_pole",
		PistonExtensionPoleBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.PISTON_HEAD)
			.sound(SoundType.SCAFFOLDING).mapColor(MapColor.DIRT).forceSolidOn()
			.pushReaction(net.minecraft.world.level.material.PushReaction.PUSH_PULL));
	public static final MechanicalPistonHeadBlock MECHANICAL_PISTON_HEAD = ModBlocks.register("mechanical_piston_head",
		MechanicalPistonHeadBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.PISTON_HEAD).mapColor(MapColor.DIRT)
			.pushReaction(net.minecraft.world.level.material.PushReaction.PUSH_PULL));

	public static final BlockEntityType<MechanicalPistonBlockEntity> MECHANICAL_PISTON_BLOCK_ENTITY = ModBlockEntities
		.register("mechanical_piston", (pos, state) -> new MechanicalPistonBlockEntity(
			ContraptionRegistry.MECHANICAL_PISTON_BLOCK_ENTITY, pos, state), MECHANICAL_PISTON, STICKY_MECHANICAL_PISTON);

	public static final Item MECHANICAL_PISTON_ITEM = ModItems.block(MECHANICAL_PISTON);
	public static final Item STICKY_MECHANICAL_PISTON_ITEM = ModItems.block(STICKY_MECHANICAL_PISTON);
	public static final Item PISTON_EXTENSION_POLE_ITEM = ModItems.block(PISTON_EXTENSION_POLE);

	public static final Item SUPER_GLUE_ITEM = ModItems.register("super_glue", SuperGlueItem::new,
		new Item.Properties().stacksTo(1).durability(99));

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
		// Create's AllBlocks: CStress.setImpact(4.0)
		BlockStressValues.setImpact(MECHANICAL_PISTON, 4);
		BlockStressValues.setImpact(STICKY_MECHANICAL_PISTON, 4);

		// mounted storage associations (Create: block tags create:simple_mounted_storage / chest_mounted_storage and
		// the blocks' own registrations)
		SIMPLE_MOUNTED_STORAGE.associate(Blocks.BARREL);
		for (Block block : BuiltInRegistries.BLOCK) {
			var id = BuiltInRegistries.BLOCK.getKey(block);
			if (id.getNamespace().equals("minecraft") && id.getPath().endsWith("shulker_box"))
				SIMPLE_MOUNTED_STORAGE.associate(block);
		}
		CHEST_MOUNTED_STORAGE.associate(Blocks.CHEST);
		CHEST_MOUNTED_STORAGE.associate(Blocks.TRAPPED_CHEST);
		VAULT_MOUNTED_STORAGE.markFuelBlacklisted();
		VAULT_MOUNTED_STORAGE.associate(VaultRegistry.ITEM_VAULT);
		DEPOT_MOUNTED_STORAGE.associate(ModBlocks.DEPOT);

		// Create's AllMovementBehaviours / AllInteractionBehaviours
		MovementBehaviour.REGISTRY.register(Blocks.BELL, new BellMovementBehaviour());
		MovementBehaviour.REGISTRY.register(Blocks.CAMPFIRE, new CampfireMovementBehaviour());
		MovementBehaviour.REGISTRY.register(Blocks.SOUL_CAMPFIRE, new CampfireMovementBehaviour());

		MovingInteractionBehaviour.REGISTRY.register(Blocks.LEVER, new LeverMovingInteraction());
		MovingInteractionBehaviour.REGISTRY.registerProvider(SimpleRegistry.Provider.forBlockTag(BlockTags.WOODEN_DOORS, new DoorMovingInteraction()));
		MovingInteractionBehaviour.REGISTRY.registerProvider(SimpleRegistry.Provider.forBlockTag(BlockTags.WOODEN_TRAPDOORS, new TrapdoorMovingInteraction()));
		MovingInteractionBehaviour.REGISTRY.registerProvider(SimpleRegistry.Provider.forBlockTag(BlockTags.FENCE_GATES, new FenceGateMovingInteraction()));

		ContraptionHandler.init();
		SuperGlueItem.init();
	}
}
