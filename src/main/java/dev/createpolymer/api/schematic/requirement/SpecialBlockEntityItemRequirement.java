package dev.createpolymer.api.schematic.requirement;

import dev.createpolymer.schematics.requirement.ItemRequirement;
import net.minecraft.world.level.block.state.BlockState;

public interface SpecialBlockEntityItemRequirement {
	ItemRequirement getRequiredItems(BlockState state);
}
