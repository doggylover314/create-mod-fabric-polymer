package dev.createpolymer.registry.feature;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.api.behaviour.movement.MovementBehaviour;
import dev.createpolymer.contraptions.behaviour.BellMovementBehaviour;
import dev.createpolymer.equipment.armor.CardboardArmorHandler;
import dev.createpolymer.equipment.armor.CardboardArmorItem;
import dev.createpolymer.equipment.bell.HauntedBellBlock;
import dev.createpolymer.equipment.bell.HauntedBellBlockEntity;
import dev.createpolymer.equipment.bell.HauntedBellMovementBehaviour;
import dev.createpolymer.equipment.bell.HauntedBellPulser;
import dev.createpolymer.equipment.bell.PeculiarBellBlock;
import dev.createpolymer.equipment.bell.PeculiarBellBlockEntity;
import dev.createpolymer.equipment.tool.AllToolMaterials;
import dev.createpolymer.equipment.tool.CardboardSwordItem;
import dev.createpolymer.kinetics.gearbox.VerticalGearboxItem;
import dev.createpolymer.legacy.ChromaticCompoundItem;
import dev.createpolymer.legacy.ItemEntityUpdate;
import dev.createpolymer.legacy.RefinedRadianceItem;
import dev.createpolymer.legacy.ShadowSteelItem;
import dev.createpolymer.materials.ExperienceBlock;
import dev.createpolymer.materials.RoseQuartzBlock;
import dev.createpolymer.processing.casing.CasingBlock;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.providers.number.floats.ContextFloatProviders;
import net.minecraft.world.level.storage.loot.providers.number.floats.ResolvableFloat;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;

/**
 * Create's remaining simple blocks and items: the food (bar of chocolate, sweet roll, chocolate glazed berries,
 * honeyed apple), the cardboard sword and armour, the legacy chromatic compound, shadow steel and refined radiance,
 * the vertical gearbox, the casings of shadow steel and refined radiance, the peculiar and haunted bells, the block
 * of experience and the rose quartz blocks. Ids are Create's, so its recipes, tags and loot tables apply unchanged.
 */
public final class ExtrasRegistry {
	private ExtrasRegistry() {}

	// ---- data components (server only) ----

	/** Create's AllDataComponents.CHROMATIC_COMPOUND_COLLECTING_LIGHT: light sources eaten so far */
	public static final DataComponentType<Integer> CHROMATIC_COMPOUND_COLLECTING_LIGHT = component("chromatic_compound_collecting_light",
		DataComponentType.<Integer>builder()
			.persistent(ExtraCodecs.NON_NEGATIVE_INT)
			.networkSynchronized(ByteBufCodecs.VAR_INT)
			.build());

	private static <T> DataComponentType<T> component(String id, DataComponentType<T> type) {
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, CreatePolymer.id(id), type);
		PolymerComponent.registerDataComponent(type);
		return type;
	}

	// ---- blocks ----

	// Create's casings start from SharedProperties.stone
	private static final BlockBehaviour.Properties CASING_PROPERTIES = BlockBehaviour.Properties.of().strength(1.5f, 6f);

	public static final CasingBlock SHADOW_STEEL_CASING = ModBlocks.register("shadow_steel_casing",
		p -> new CasingBlock(p, "shadow_steel_casing"),
		CASING_PROPERTIES.sound(SoundType.WOOD).mapColor(MapColor.COLOR_BLACK));

	public static final CasingBlock REFINED_RADIANCE_CASING = ModBlocks.register("refined_radiance_casing",
		p -> new CasingBlock(p, "refined_radiance_casing"),
		CASING_PROPERTIES.sound(SoundType.WOOD).mapColor(MapColor.SNOW).lightLevel(s -> 12));

	public static final PeculiarBellBlock PECULIAR_BELL = ModBlocks.register("peculiar_bell", PeculiarBellBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK).mapColor(MapColor.GOLD).forceSolidOn().noOcclusion().sound(SoundType.ANVIL));

	public static final HauntedBellBlock HAUNTED_BELL = ModBlocks.register("haunted_bell", HauntedBellBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK).mapColor(MapColor.SAND).forceSolidOn().noOcclusion().sound(SoundType.ANVIL));

	public static final ExperienceBlock EXPERIENCE_BLOCK = ModBlocks.register("experience_block", ExperienceBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK).mapColor(MapColor.PLANT)
			.sound(new SoundType(1, .5f, SoundEvents.AMETHYST_BLOCK_BREAK, SoundEvents.AMETHYST_BLOCK_STEP,
				SoundEvents.AMETHYST_BLOCK_PLACE, SoundEvents.AMETHYST_BLOCK_HIT, SoundEvents.AMETHYST_BLOCK_FALL))
			.requiresCorrectToolForDrops()
			.lightLevel(s -> 15));

	public static final RoseQuartzBlock ROSE_QUARTZ_BLOCK = ModBlocks.register("rose_quartz_block", RoseQuartzBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_BLOCK).mapColor(MapColor.TERRACOTTA_PINK)
			.requiresCorrectToolForDrops()
			.sound(SoundType.DEEPSLATE));

	public static final MaterialsRegistry.TexturedBlock ROSE_QUARTZ_TILES = ModBlocks.register("rose_quartz_tiles",
		p -> new MaterialsRegistry.TexturedBlock(p, "palettes/rose_quartz_tiles", Blocks.DEEPSLATE),
		BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE).mapColor(MapColor.TERRACOTTA_PINK).requiresCorrectToolForDrops());

	public static final MaterialsRegistry.TexturedBlock SMALL_ROSE_QUARTZ_TILES = ModBlocks.register("small_rose_quartz_tiles",
		p -> new MaterialsRegistry.TexturedBlock(p, "palettes/small_rose_quartz_tiles", Blocks.DEEPSLATE),
		BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE).mapColor(MapColor.TERRACOTTA_PINK).requiresCorrectToolForDrops());

	// ---- block items ----

	public static final Item SHADOW_STEEL_CASING_ITEM = ModItems.block(SHADOW_STEEL_CASING);
	public static final Item REFINED_RADIANCE_CASING_ITEM = ModItems.block(REFINED_RADIANCE_CASING);
	public static final Item PECULIAR_BELL_ITEM = ModItems.block(PECULIAR_BELL);
	public static final Item HAUNTED_BELL_ITEM = ModItems.block(HAUNTED_BELL);
	public static final Item EXPERIENCE_BLOCK_ITEM = ModItems.block(EXPERIENCE_BLOCK, new Item.Properties().rarity(Rarity.UNCOMMON));
	public static final Item ROSE_QUARTZ_BLOCK_ITEM = ModItems.block(ROSE_QUARTZ_BLOCK);
	public static final Item ROSE_QUARTZ_TILES_ITEM = ModItems.block(ROSE_QUARTZ_TILES);
	public static final Item SMALL_ROSE_QUARTZ_TILES_ITEM = ModItems.block(SMALL_ROSE_QUARTZ_TILES);

	// ---- block entities ----

	public static final BlockEntityType<PeculiarBellBlockEntity> PECULIAR_BELL_BLOCK_ENTITY = ModBlockEntities.register("peculiar_bell",
		(pos, state) -> new PeculiarBellBlockEntity(ExtrasRegistry.PECULIAR_BELL_BLOCK_ENTITY, pos, state), PECULIAR_BELL);

	/** Create's haunted bell block entity has the id "cursed_bell" */
	public static final BlockEntityType<HauntedBellBlockEntity> HAUNTED_BELL_BLOCK_ENTITY = ModBlockEntities.register("cursed_bell",
		(pos, state) -> new HauntedBellBlockEntity(ExtrasRegistry.HAUNTED_BELL_BLOCK_ENTITY, pos, state), HAUNTED_BELL);

	// ---- food: Create's nutrition and saturation ----

	public static final Item BAR_OF_CHOCOLATE = food("bar_of_chocolate", 6, 0.3F);
	public static final Item SWEET_ROLL = food("sweet_roll", 6, 0.8F);
	public static final Item CHOCOLATE_GLAZED_BERRIES = food("chocolate_glazed_berries", 7, 0.8F);
	public static final Item HONEYED_APPLE = food("honeyed_apple", 8, 0.8F);

	private static Item food(String name, int nutrition, float saturation) {
		return ModItems.register(name, p -> new SimplePolymerItem(p, Items.TRIAL_KEY, true), new Item.Properties()
			.food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build()));
	}

	// ---- items ----

	/** Create's burn time for the cardboard items: 1000 ticks */
	private static CookingFuel burnTime(int ticks) {
		return new CookingFuel(new ResolvableInt.Constant(ticks),
			ResolvableFloat.fromKey(ContextFloatProviders.COOKING_DEFAULT_SPEED_MULTIPLIER));
	}

	public static final Item CARDBOARD_SWORD = ModItems.register("cardboard_sword", CardboardSwordItem::new,
		new Item.Properties().stacksTo(1).sword(AllToolMaterials.CARDBOARD, 3, 1).component(DataComponents.COOKING_FUEL, burnTime(1000)));

	public static final CardboardArmorItem CARDBOARD_HELMET = cardboardArmor("cardboard_helmet", ArmorType.HELMET);
	public static final CardboardArmorItem CARDBOARD_CHESTPLATE = cardboardArmor("cardboard_chestplate", ArmorType.CHESTPLATE);
	public static final CardboardArmorItem CARDBOARD_LEGGINGS = cardboardArmor("cardboard_leggings", ArmorType.LEGGINGS);
	public static final CardboardArmorItem CARDBOARD_BOOTS = cardboardArmor("cardboard_boots", ArmorType.BOOTS);

	private static CardboardArmorItem cardboardArmor(String name, ArmorType type) {
		return ModItems.register(name, p -> new CardboardArmorItem(type, p),
			new Item.Properties().component(DataComponents.COOKING_FUEL, burnTime(1000)));
	}

	public static final Item CHROMATIC_COMPOUND = ModItems.register("chromatic_compound", ChromaticCompoundItem::new,
		new Item.Properties().rarity(Rarity.UNCOMMON));

	public static final Item SHADOW_STEEL = ModItems.register("shadow_steel", ShadowSteelItem::new,
		new Item.Properties().rarity(Rarity.UNCOMMON));

	public static final Item REFINED_RADIANCE = ModItems.register("refined_radiance", RefinedRadianceItem::new,
		new Item.Properties().rarity(Rarity.UNCOMMON));

	public static final VerticalGearboxItem VERTICAL_GEARBOX = ModItems.register("vertical_gearbox", VerticalGearboxItem::new,
		new Item.Properties());

	public static void init() {
		// Create's item and entity events, which Fabric has as callbacks and tick hooks
		ServerTickEvents.END_SERVER_TICK.register(ItemEntityUpdate::tick);
		ServerTickEvents.END_SERVER_TICK.register(CardboardArmorHandler::tick);
		ServerTickEvents.END_SERVER_TICK.register(HauntedBellPulser::tick);
		ServerLevelEvents.UNLOAD.register((server, level) -> HauntedBellPulser.unload(level.dimension()));
		CardboardSwordItem.register();

		// Create's movement behaviours: the bells ring or pulse on a contraption
		MovementBehaviour.movementBehaviour(PECULIAR_BELL, new BellMovementBehaviour());
		MovementBehaviour.movementBehaviour(HAUNTED_BELL, new HauntedBellMovementBehaviour());
	}
}
