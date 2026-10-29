package dev.createpolymer.fluids.tank;

import dev.createpolymer.api.boiler.BoilerHeater;
import dev.createpolymer.api.registry.SimpleRegistry;
import dev.createpolymer.CreatePolymer;
import dev.createpolymer.processing.burner.BlazeBurnerBlock;
import dev.createpolymer.processing.burner.BlazeBurnerBlock.HeatLevel;
import dev.createpolymer.registry.feature.BurnerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;

public class BoilerHeaters {
	/** Create's AllBlockTags.PASSIVE_BOILER_HEATERS */
	public static final TagKey<Block> PASSIVE_BOILER_HEATERS =
		TagKey.create(Registries.BLOCK, CreatePolymer.id("passive_boiler_heaters"));

	public static void registerDefaults() {
		BoilerHeater.REGISTRY.register(BurnerRegistry.BLAZE_BURNER, BoilerHeater.BLAZE_BURNER);
		BoilerHeater.REGISTRY.registerProvider(SimpleRegistry.Provider.forBlockTag(PASSIVE_BOILER_HEATERS, BoilerHeater.PASSIVE));
	}

	public static int passive(Level level, BlockPos pos, BlockState state) {
		return isNotUnheated(state) ? BoilerHeater.PASSIVE_HEAT : BoilerHeater.NO_HEAT;
	}

	public static int blazeBurner(Level level, BlockPos pos, BlockState state) {
		HeatLevel value = state.getValue(BlazeBurnerBlock.HEAT_LEVEL);
		if (value == HeatLevel.NONE) {
			return BoilerHeater.NO_HEAT;
		}
		if (value == HeatLevel.SEETHING) {
			return 2;
		}
		if (value.isAtLeast(HeatLevel.FADING)) {
			return 1;
		}
		return BoilerHeater.PASSIVE_HEAT;
	}

	/** Create's BlockHelper.isNotUnheated: unlit campfires and burners without heat are not heaters. */
	public static boolean isNotUnheated(BlockState state) {
		if (state.is(BlockTags.CAMPFIRES) && state.hasProperty(CampfireBlock.LIT))
			return state.getValue(CampfireBlock.LIT);
		if (state.hasProperty(BlazeBurnerBlock.HEAT_LEVEL))
			return state.getValue(BlazeBurnerBlock.HEAT_LEVEL) != HeatLevel.NONE;
		return true;
	}
}
