package dev.createpolymer.worldgen;

import dev.createpolymer.CreatePolymer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Create's ore generation. The features are data (data/create/worldgen, converted from Create's by
 * tools/asset-scripts/copy_worldgen.py); this adds them to the overworld the way Create's neoforge biome modifier does.
 */
public final class CreateWorldgen {
	private CreateWorldgen() {}

	public static final ResourceKey<PlacedFeature> ZINC_ORE = ResourceKey.create(Registries.PLACED_FEATURE, CreatePolymer.id("zinc_ore"));

	public static void init() {
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES, ZINC_ORE);
	}
}
