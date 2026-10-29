package dev.createpolymer.api.schematic.requirement;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.schematics.requirement.ItemRequirement;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public interface SpecialBlockItemRequirement {
	ItemRequirement getRequiredItems(BlockState state, @Nullable BlockEntity blockEntity);
}
