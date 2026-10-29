package dev.createpolymer.api.schematic.nbt;

import net.minecraft.world.level.storage.ValueOutput;

public interface PartialSafeNBT {
	/**
	 * This will always be called from the logical server
	 */
	void writeSafe(ValueOutput output);
}
