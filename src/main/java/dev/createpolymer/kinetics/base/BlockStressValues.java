package dev.createpolymer.kinetics.base;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.world.level.block.Block;

/**
 * Stress impact (SU per RPM used) and capacity (SU per RPM provided) for each block.
 * Values come from Create's AllBlocks / CStress defaults.
 */
public final class BlockStressValues {
	private static final Map<Block, Double> IMPACTS = new IdentityHashMap<>();
	private static final Map<Block, Double> CAPACITIES = new IdentityHashMap<>();
	private static final Map<Block, Integer> GENERATOR_SPEEDS = new IdentityHashMap<>();

	private BlockStressValues() {}

	public static void setImpact(Block block, double impact) {
		IMPACTS.put(block, impact);
	}

	public static void setCapacity(Block block, double capacity) {
		CAPACITIES.put(block, capacity);
	}

	public static void setGeneratorSpeed(Block block, int rpm) {
		GENERATOR_SPEEDS.put(block, rpm);
	}

	public static double getImpact(Block block) {
		return IMPACTS.getOrDefault(block, 0d);
	}

	public static double getCapacity(Block block) {
		return CAPACITIES.getOrDefault(block, 0d);
	}

	public static int getGeneratorSpeed(Block block) {
		return GENERATOR_SPEEDS.getOrDefault(block, 0);
	}
}
