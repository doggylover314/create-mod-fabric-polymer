package dev.createpolymer.processing.burner;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Stand-in for Create's REGULAR_BLAZE_BURNER_FUELS and SUPERHEATED_BLAZE_BURNER_FUELS data maps (NeoForge data maps
 * have no Fabric counterpart): burn times by item, filled in code. The two item tags Create still honours as a
 * fallback are kept as well.
 */
public final class BlazeBurnerFuels {
	private BlazeBurnerFuels() {}

	public static final TagKey<Item> BLAZE_BURNER_FUEL_REGULAR =
		TagKey.create(Registries.ITEM, CreatePolymer.id("blaze_burner_fuel/regular"));
	public static final TagKey<Item> BLAZE_BURNER_FUEL_SPECIAL =
		TagKey.create(Registries.ITEM, CreatePolymer.id("blaze_burner_fuel/special"));

	private static final Map<Item, Integer> REGULAR = new HashMap<>();
	private static final Map<Item, Integer> SUPERHEATED = new HashMap<>();

	public static void registerRegular(Item item, int burnTime) {
		REGULAR.put(item, burnTime);
	}

	public static void registerSuperheated(Item item, int burnTime) {
		SUPERHEATED.put(item, burnTime);
	}

	/** Burn time of a superheated fuel, or null if the item is not one. */
	public static @Nullable Integer getSuperheated(ItemStack stack) {
		return SUPERHEATED.get(stack.getItem());
	}

	/** Burn time of a regular fuel registered here, or null. Furnace fuels are handled by the block entity. */
	public static @Nullable Integer getRegular(ItemStack stack) {
		return REGULAR.get(stack.getItem());
	}
}
