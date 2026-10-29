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

	/** A copy of every impact set so far. */
	public static Map<Block, Double> impacts() {
		return new IdentityHashMap<>(IMPACTS);
	}

	/** A copy of every capacity set so far. */
	public static Map<Block, Double> capacities() {
		return new IdentityHashMap<>(CAPACITIES);
	}

	/** Replaces all impacts and capacities, used by the config to restore the registered values before applying overrides. */
	public static void replaceAll(Map<Block, Double> impacts, Map<Block, Double> capacities) {
		IMPACTS.clear();
		IMPACTS.putAll(impacts);
		CAPACITIES.clear();
		CAPACITIES.putAll(capacities);
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
