package dev.createpolymer.registry.feature;

import dev.createpolymer.processing.burner.BlazeBurnerBlock;
import dev.createpolymer.processing.burner.BlazeBurnerBlockEntity;
import dev.createpolymer.processing.burner.BlazeBurnerBlockItem;
import dev.createpolymer.processing.burner.BlazeBurnerFuels;
import dev.createpolymer.processing.burner.LitBlazeBurnerBlock;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.providers.number.floats.ContextFloatProviders;
import net.minecraft.world.level.storage.loot.providers.number.floats.ResolvableFloat;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;

/** Blaze burner, lit blaze burner, their items and the blaze cakes. */
public final class BurnerRegistry {
	private BurnerRegistry() {}

	// Create's SharedProperties.softMetal is a copy of the gold block
	public static final BlazeBurnerBlock BLAZE_BURNER = ModBlocks.register("blaze_burner", BlazeBurnerBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK).mapColor(MapColor.COLOR_GRAY)
			.lightLevel(BlazeBurnerBlock::getLight));
	public static final LitBlazeBurnerBlock LIT_BLAZE_BURNER = ModBlocks.register("lit_blaze_burner", LitBlazeBurnerBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK).mapColor(MapColor.COLOR_LIGHT_GRAY)
			.lightLevel(LitBlazeBurnerBlock::getLight));

	public static final BlockEntityType<BlazeBurnerBlockEntity> HEATER = ModBlockEntities.register("blaze_heater",
		(pos, state) -> new BlazeBurnerBlockEntity(BurnerRegistry.HEATER, pos, state), BLAZE_BURNER);

	public static final Item BLAZE_CAKE_BASE = ModItems.simple("blaze_cake_base");
	public static final Item BLAZE_CAKE = ModItems.register("blaze_cake",
		p -> new SimplePolymerItem(p, Items.TRIAL_KEY, true), new Item.Properties().component(DataComponents.COOKING_FUEL, fuel(6400)));
	public static final Item CREATIVE_BLAZE_CAKE = ModItems.register("creative_blaze_cake",
		p -> new SimplePolymerItem(p, Items.TRIAL_KEY, true),
		new Item.Properties().rarity(Rarity.EPIC).component(DataComponents.COOKING_FUEL, fuel(Integer.MAX_VALUE)));
	public static final BlazeBurnerBlockItem EMPTY_BLAZE_BURNER = ModItems.register("empty_blaze_burner",
		BlazeBurnerBlockItem::empty, new Item.Properties());
	public static final BlazeBurnerBlockItem BLAZE_BURNER_ITEM = ModItems.register("blaze_burner",
		p -> BlazeBurnerBlockItem.withBlaze(BLAZE_BURNER, p), new Item.Properties().useBlockDescriptionPrefix());

	private static CookingFuel fuel(int burnTime) {
		return new CookingFuel(new ResolvableInt.Constant(burnTime),
			ResolvableFloat.fromKey(ContextFloatProviders.COOKING_DEFAULT_SPEED_MULTIPLIER));
	}

	public static void init() {
		// Create's data map for superheated fuels: the blaze cake burns for 3200 ticks at seething heat
		BlazeBurnerFuels.registerSuperheated(BLAZE_CAKE, 3200);
	}
}
