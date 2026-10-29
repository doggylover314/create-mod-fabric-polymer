package dev.createpolymer.equipment.symmetryWand;

/**
 * Create's CEquipment.maxSymmetryWandRange, with Create's default; set from create.json (see ConfigMappings).
 */
public final class SymmetryWandConfig {
	private SymmetryWandConfig() {}

	/** The maximum distance to an active mirror for the symmetry wand to trigger. */
	public static int maxSymmetryWandRange = 50;
}
