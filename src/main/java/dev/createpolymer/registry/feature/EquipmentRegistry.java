package dev.createpolymer.registry.feature;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.equipment.BuildersTeaItem;
import dev.createpolymer.equipment.TreeFertilizerItem;
import dev.createpolymer.equipment.armor.AllArmorMaterials;
import dev.createpolymer.equipment.armor.BacktankBlock;
import dev.createpolymer.equipment.armor.BacktankBlockEntity;
import dev.createpolymer.equipment.armor.BacktankItem;
import dev.createpolymer.equipment.armor.BacktankItem.BacktankBlockItem;
import dev.createpolymer.equipment.armor.DivingBootsItem;
import dev.createpolymer.equipment.armor.DivingHelmetItem;
import dev.createpolymer.equipment.armor.NetheriteDivingHandler;
import dev.createpolymer.equipment.extendoGrip.ExtendoGripItem;
import dev.createpolymer.equipment.potatoCannon.AllPotatoProjectileBlockHitActions;
import dev.createpolymer.equipment.potatoCannon.AllPotatoProjectileEntityHitActions;
import dev.createpolymer.equipment.potatoCannon.AllPotatoProjectileRenderModes;
import dev.createpolymer.equipment.potatoCannon.PotatoCannonItem;
import dev.createpolymer.equipment.potatoCannon.PotatoCannonProjectileType;
import dev.createpolymer.equipment.potatoCannon.PotatoProjectileBlockHitAction;
import dev.createpolymer.equipment.potatoCannon.PotatoProjectileEntity;
import dev.createpolymer.equipment.potatoCannon.PotatoProjectileEntityHitAction;
import dev.createpolymer.equipment.potatoCannon.PotatoProjectileRenderMode;
import dev.createpolymer.equipment.sandPaper.SandPaperItem;
import dev.createpolymer.equipment.sandPaper.SandPaperItemComponent;
import dev.createpolymer.equipment.sandPaper.SandPaperPolishingRecipe;
import dev.createpolymer.equipment.toolbox.ToolboxBlock;
import dev.createpolymer.equipment.toolbox.ToolboxBlockEntity;
import dev.createpolymer.equipment.toolbox.ToolboxDyeingRecipe;
import dev.createpolymer.equipment.toolbox.ToolboxInventory;
import dev.createpolymer.equipment.toolbox.UncontainableBlockItem;
import dev.createpolymer.kinetics.base.BlockStressValues;
import dev.createpolymer.processing.recipe.ProcessingRecipe;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.core.MappedRegistry;

/**
 * Create's equipment: sand paper, tree fertilizer, builder's tea, the extendo grip, the potato cannon, the backtanks
 * with the diving helmets and boots, and the toolboxes. Ids are Create's, so its recipes and loot tables apply.
 * <p>
 * Everything stored in items (air, the polished item, toolbox contents) is a server-only data component.
 */
public final class EquipmentRegistry {
	private EquipmentRegistry() {}

	// ---- Tags, enchantments, damage types and registries ----

	public static final TagKey<Item> PRESSURIZED_AIR_SOURCES = TagKey.create(Registries.ITEM, CreatePolymer.id("pressurized_air_sources"));
	public static final ResourceKey<Enchantment> CAPACITY = ResourceKey.create(Registries.ENCHANTMENT, CreatePolymer.id("capacity"));
	public static final ResourceKey<Enchantment> POTATO_RECOVERY = ResourceKey.create(Registries.ENCHANTMENT, CreatePolymer.id("potato_recovery"));
	public static final ResourceKey<DamageType> POTATO_CANNON_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, CreatePolymer.id("potato_cannon"));

	/** Create's CreateRegistries.POTATO_PROJECTILE_TYPE, a data pack registry */
	public static final ResourceKey<Registry<PotatoCannonProjectileType>> POTATO_PROJECTILE_TYPE =
		ResourceKey.createRegistryKey(CreatePolymer.id("potato_projectile_type"));

	/** Create's CreateBuiltInRegistries, as plain registries that exist only on the server */
	public static final MappedRegistry<MapCodec<? extends PotatoProjectileEntityHitAction>> POTATO_PROJECTILE_ENTITY_HIT_ACTION =
		registry("potato_projectile_entity_hit_action");
	public static final MappedRegistry<MapCodec<? extends PotatoProjectileBlockHitAction>> POTATO_PROJECTILE_BLOCK_HIT_ACTION =
		registry("potato_projectile_block_hit_action");
	public static final MappedRegistry<MapCodec<? extends PotatoProjectileRenderMode>> POTATO_PROJECTILE_RENDER_MODE =
		registry("potato_projectile_render_mode");

	private static <T> MappedRegistry<T> registry(String name) {
		return new MappedRegistry<>(ResourceKey.createRegistryKey(CreatePolymer.id(name)), Lifecycle.stable());
	}

	// ---- Data components (server only) ----

	public static final DataComponentType<Integer> BACKTANK_AIR = component("banktank_air",
		DataComponentType.<Integer>builder()
			.persistent(ExtraCodecs.NON_NEGATIVE_INT)
			.networkSynchronized(ByteBufCodecs.VAR_INT)
			.build());

	public static final DataComponentType<SandPaperItemComponent> SAND_PAPER_POLISHING = component("sand_paper_polishing",
		DataComponentType.<SandPaperItemComponent>builder()
			.persistent(SandPaperItemComponent.CODEC)
			.networkSynchronized(SandPaperItemComponent.STREAM_CODEC)
			.build());

	public static final DataComponentType<Boolean> SHAPER_SWAP = component("shaper_swap",
		DataComponentType.<Boolean>builder()
			.persistent(Codec.BOOL)
			.networkSynchronized(ByteBufCodecs.BOOL)
			.build());

	public static final DataComponentType<ToolboxInventory> TOOLBOX_INVENTORY = component("toolbox_inventory",
		DataComponentType.<ToolboxInventory>builder()
			.persistent(ToolboxInventory.CODEC)
			.networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(ToolboxInventory.CODEC))
			.build());

	public static final DataComponentType<java.util.UUID> TOOLBOX_UUID = component("toolbox_uuid",
		DataComponentType.<java.util.UUID>builder()
			.persistent(UUIDUtil.CODEC)
			.networkSynchronized(UUIDUtil.STREAM_CODEC)
			.build());

	private static <T> DataComponentType<T> component(String id, DataComponentType<T> type) {
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, CreatePolymer.id(id), type);
		PolymerComponent.registerDataComponent(type);
		return type;
	}

	// ---- Recipes ----

	public static final RecipeType<SandPaperPolishingRecipe> SANDPAPER_POLISHING = type("sandpaper_polishing");
	public static final RecipeSerializer<SandPaperPolishingRecipe> SANDPAPER_POLISHING_SERIALIZER =
		serializer("sandpaper_polishing", ProcessingRecipe.codec(SandPaperPolishingRecipe::new));
	public static final RecipeSerializer<ToolboxDyeingRecipe> TOOLBOX_DYEING_SERIALIZER =
		serializer("toolbox_dyeing", ToolboxDyeingRecipe.SERIALIZER);

	private static <T extends Recipe<?>> RecipeType<T> type(String name) {
		RecipeType<T> type = new RecipeType<>() {
			@Override
			public String toString() {
				return CreatePolymer.MOD_ID + ":" + name;
			}
		};
		Registry.register(BuiltInRegistries.RECIPE_TYPE, CreatePolymer.id(name), type);
		RegistrySyncUtils.setServerEntry(BuiltInRegistries.RECIPE_TYPE, type);
		return type;
	}

	private static <T extends Recipe<?>> RecipeSerializer<T> serializer(String name, MapCodec<T> codec) {
		return serializer(name, new RecipeSerializer<>(codec, ByteBufCodecs.fromCodecWithRegistries(codec.codec())));
	}

	private static <T extends Recipe<?>> RecipeSerializer<T> serializer(String name, RecipeSerializer<T> serializer) {
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, CreatePolymer.id(name), serializer);
		RegistrySyncUtils.setServerEntry(BuiltInRegistries.RECIPE_SERIALIZER, serializer);
		return serializer;
	}

	// ---- Entities ----

	public static final EntityType<PotatoProjectileEntity> POTATO_PROJECTILE = Registry.register(BuiltInRegistries.ENTITY_TYPE,
		CreatePolymer.id("potato_projectile"),
		PotatoProjectileEntity.build(EntityType.Builder.<PotatoProjectileEntity>of(PotatoProjectileEntity::new, MobCategory.MISC)
			.clientTrackingRange(4)
			.updateInterval(20)
			.noSave())
			.build(ResourceKey.create(Registries.ENTITY_TYPE, CreatePolymer.id("potato_projectile"))));

	// ---- Blocks ----

	public static final BacktankBlock COPPER_BACKTANK = ModBlocks.register("copper_backtank", BacktankBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK.weathering().unaffected()).noOcclusion());
	public static final BacktankBlock NETHERITE_BACKTANK = ModBlocks.register("netherite_backtank", BacktankBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.NETHERITE_BLOCK).noOcclusion());

	public static final Map<DyeColor, ToolboxBlock> TOOLBOXES = new EnumMap<>(DyeColor.class);

	static {
		for (DyeColor color : DyeColor.values())
			TOOLBOXES.put(color, ModBlocks.register(color.getSerializedName() + "_toolbox", p -> new ToolboxBlock(p, color),
				ModBlocks.wooden().sound(SoundType.WOOD).mapColor(color).forceSolidOn()));
	}

	// ---- Block entities ----

	public static final BlockEntityType<BacktankBlockEntity> BACKTANK = ModBlockEntities.register("backtank",
		(pos, state) -> new BacktankBlockEntity(EquipmentRegistry.BACKTANK, pos, state), COPPER_BACKTANK, NETHERITE_BACKTANK);

	public static final BlockEntityType<ToolboxBlockEntity> TOOLBOX = ModBlockEntities.register("toolbox",
		(pos, state) -> new ToolboxBlockEntity(EquipmentRegistry.TOOLBOX, pos, state),
		TOOLBOXES.values().toArray(Block[]::new));

	// ---- Items ----

	public static final Item SAND_PAPER = ModItems.register("sand_paper", SandPaperItem::new, new Item.Properties());
	public static final Item RED_SAND_PAPER = ModItems.register("red_sand_paper", SandPaperItem::new, new Item.Properties());
	public static final Item TREE_FERTILIZER = ModItems.register("tree_fertilizer", TreeFertilizerItem::new, new Item.Properties());
	public static final Item BUILDERS_TEA = ModItems.register("builders_tea", BuildersTeaItem::new, BuildersTeaItem.properties());
	public static final Item EXTENDO_GRIP = ModItems.register("extendo_grip", ExtendoGripItem::new,
		new Item.Properties().rarity(net.minecraft.world.item.Rarity.UNCOMMON));
	public static final Item POTATO_CANNON = ModItems.register("potato_cannon", PotatoCannonItem::new, new Item.Properties());

	// wrapped by the backtank items for block placement
	public static final BacktankBlockItem COPPER_BACKTANK_PLACEABLE = placeable("copper_backtank_placeable", COPPER_BACKTANK,
		() -> EquipmentRegistry.COPPER_BACKTANK_ITEM);
	public static final BacktankBlockItem NETHERITE_BACKTANK_PLACEABLE = placeable("netherite_backtank_placeable", NETHERITE_BACKTANK,
		() -> EquipmentRegistry.NETHERITE_BACKTANK_ITEM);

	public static final BacktankItem COPPER_BACKTANK_ITEM = ModItems.register("copper_backtank",
		p -> new BacktankItem(AllArmorMaterials.COPPER, p, () -> COPPER_BACKTANK_PLACEABLE), new Item.Properties());
	public static final BacktankItem NETHERITE_BACKTANK_ITEM = ModItems.register("netherite_backtank",
		p -> new BacktankItem(AllArmorMaterials.NETHERITE, p, () -> NETHERITE_BACKTANK_PLACEABLE), new Item.Properties().fireResistant());

	public static final DivingHelmetItem COPPER_DIVING_HELMET = ModItems.register("copper_diving_helmet",
		p -> new DivingHelmetItem(AllArmorMaterials.COPPER, p), new Item.Properties());
	public static final DivingHelmetItem NETHERITE_DIVING_HELMET = ModItems.register("netherite_diving_helmet",
		p -> new DivingHelmetItem(AllArmorMaterials.NETHERITE, p), new Item.Properties().fireResistant());
	public static final DivingBootsItem COPPER_DIVING_BOOTS = ModItems.register("copper_diving_boots",
		p -> new DivingBootsItem(AllArmorMaterials.COPPER, p), new Item.Properties());
	public static final DivingBootsItem NETHERITE_DIVING_BOOTS = ModItems.register("netherite_diving_boots",
		p -> new DivingBootsItem(AllArmorMaterials.NETHERITE, p), new Item.Properties().fireResistant());

	public static final Map<DyeColor, Item> TOOLBOX_ITEMS = new EnumMap<>(DyeColor.class);

	static {
		for (DyeColor color : DyeColor.values()) {
			ToolboxBlock block = TOOLBOXES.get(color);
			TOOLBOX_ITEMS.put(color, ModItems.register(color.getSerializedName() + "_toolbox",
				p -> new UncontainableBlockItem(block, p), new Item.Properties().useBlockDescriptionPrefix()));
		}
	}

	private static BacktankBlockItem placeable(String name, Block block, java.util.function.Supplier<Item> actual) {
		BacktankBlockItem item = ModItems.register(name, p -> new BacktankBlockItem(block, actual, p),
			new Item.Properties().useBlockDescriptionPrefix());
		// only exists to place the block, the backtank item is what players have
		ModItems.TAB_ORDER.remove(item);
		return item;
	}

	// ---- Registry access for item predicates ----

	private static @Nullable RegistryAccess registryAccess;

	/** Create's GlobalRegistryAccess: the registries of the running server */
	public static @Nullable RegistryAccess registryAccess() {
		return registryAccess;
	}

	public static int enchantmentLevel(Level level, ItemStack stack, ResourceKey<Enchantment> key) {
		Holder<Enchantment> holder = level.registryAccess()
			.lookupOrThrow(Registries.ENCHANTMENT)
			.get(key)
			.orElse(null);
		if (holder == null)
			return 0;
		return EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
	}

	public static int capacityLevel(Level level, ItemStack stack) {
		return enchantmentLevel(level, stack, CAPACITY);
	}

	public static void init() {
		// Registries that data pack content refers to
		AllPotatoProjectileEntityHitActions.init();
		AllPotatoProjectileBlockHitActions.init();
		AllPotatoProjectileRenderModes.init();
		DynamicRegistries.register(POTATO_PROJECTILE_TYPE, PotatoCannonProjectileType.CODEC);

		PolymerEntityUtils.registerType(POTATO_PROJECTILE);

		// Create's BuilderTransformers.backtank: CStress.setImpact(4.0)
		BlockStressValues.setImpact(COPPER_BACKTANK, 4);
		BlockStressValues.setImpact(NETHERITE_BACKTANK, 4);

		ToolboxBlockEntity.registerCapabilities();

		ServerLifecycleEvents.SERVER_STARTING.register(server -> registryAccess = server.registryAccess());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> registryAccess = null);
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> registryAccess = server.registryAccess());

		// Create's entity tick, breathe and equipment change event handlers
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				ExtendoGripItem.tick(player);
				DivingHelmetItem.tick(player);
				DivingBootsItem.accelerateDescentUnderwater(player);
				NetheriteDivingHandler.tick(player);
			}
		});

		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> ExtendoGripItem.consumeDurabilityOnBlockBreak(player));
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			ExtendoGripItem.damageOnAttack(player, entity);
			return InteractionResult.PASS;
		});
	}
}
