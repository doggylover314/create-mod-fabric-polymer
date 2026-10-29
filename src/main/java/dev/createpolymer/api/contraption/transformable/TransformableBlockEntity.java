package dev.createpolymer.api.contraption.transformable;

import dev.createpolymer.contraptions.StructureTransform;

import net.minecraft.world.level.block.entity.BlockEntity;

public interface TransformableBlockEntity {
	void transform(BlockEntity blockEntity, StructureTransform transform);
}
