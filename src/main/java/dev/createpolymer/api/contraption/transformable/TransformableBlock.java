package dev.createpolymer.api.contraption.transformable;

import dev.createpolymer.contraptions.StructureTransform;

import net.minecraft.world.level.block.state.BlockState;

@FunctionalInterface
public interface TransformableBlock {
	BlockState transform(BlockState state, StructureTransform transform);
}
