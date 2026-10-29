package dev.createpolymer.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.item.AndesiteAlloyItem;
import dev.createpolymer.item.GogglesItem;
import dev.createpolymer.item.WrenchItem;
import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import eu.pb4.polymer.core.api.item.PolymerCreativeModeTabUtils;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;

public final class ModItems {
	private ModItems() {}

	public static final List<Item> TAB_ORDER = new ArrayList<>();

	// Tools
	public static final WrenchItem WRENCH = register("wrench", WrenchItem::new, new Item.Properties().stacksTo(1));
	public static final GogglesItem GOGGLES = register("goggles", GogglesItem::new, new Item.Properties().stacksTo(1).equippable(EquipmentSlot.HEAD));

	// Kinetic blocks
	public static final Item SHAFT = block(ModBlocks.SHAFT);
	public static final Item COGWHEEL = block(ModBlocks.COGWHEEL);
	public static final Item LARGE_COGWHEEL = block(ModBlocks.LARGE_COGWHEEL);
	public static final Item GEARBOX = block(ModBlocks.GEARBOX);
	public static final Item HAND_CRANK = block(ModBlocks.HAND_CRANK);
	public static final Item WATER_WHEEL = block(ModBlocks.WATER_WHEEL);
	public static final Item CREATIVE_MOTOR = block(ModBlocks.CREATIVE_MOTOR, new Item.Properties().rarity(Rarity.EPIC));
	public static final Item MILLSTONE = block(ModBlocks.MILLSTONE);
	public static final Item MECHANICAL_PRESS = block(ModBlocks.MECHANICAL_PRESS);
	public static final Item MECHANICAL_MIXER = block(ModBlocks.MECHANICAL_MIXER);
	public static final Item BASIN = block(ModBlocks.BASIN);
	public static final Item ANDESITE_CASING = block(ModBlocks.ANDESITE_CASING);

	// Materials
	public static final Item ANDESITE_ALLOY = register("andesite_alloy", AndesiteAlloyItem::new, new Item.Properties());
	public static final Item WHISK = simple("whisk");
	public static final Item IRON_SHEET = simple("iron_sheet");
	public static final Item GOLDEN_SHEET = simple("golden_sheet");
	public static final Item COPPER_SHEET = simple("copper_sheet");
	public static final Item WHEAT_FLOUR = simple("wheat_flour");

	private static Item simple(String name) {
		return register(name, p -> new SimplePolymerItem(p, Items.TRIAL_KEY, true), new Item.Properties());
	}

	private static Item block(Block block) {
		return block(block, new Item.Properties());
	}

	private static Item block(Block block, Item.Properties props) {
		String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
		return register(name, p -> new PolymerBlockItem(block, p, Items.TRIAL_KEY, true), props.useBlockDescriptionPrefix());
	}

	private static <T extends Item> T register(String name, Function<Item.Properties, T> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, CreatePolymer.id(name));
		T item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
		TAB_ORDER.add(item);
		return item;
	}

	public static void init() {
		PolymerCreativeModeTabUtils.registerPolymerCreativeModeTab(CreatePolymer.id("base"), PolymerCreativeModeTabUtils.builder()
			.title(Component.translatable("itemGroup.create.base"))
			.icon(() -> new ItemStack(COGWHEEL))
			.displayItems((params, output) -> TAB_ORDER.forEach(output::accept))
			.build());
	}
}
