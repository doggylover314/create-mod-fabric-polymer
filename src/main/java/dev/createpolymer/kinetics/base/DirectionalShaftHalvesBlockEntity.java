package dev.createpolymer.kinetics.base;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Ported from Create's DirectionalShaftHalvesBlockEntity (MIT, The Create Team). */
public class DirectionalShaftHalvesBlockEntity extends KineticBlockEntity {

	public DirectionalShaftHalvesBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public Direction getSourceFacing() {
		BlockPos localSource = source.subtract(getBlockPos());
		return Direction.getApproximateNearest((float) localSource.getX(), (float) localSource.getY(), (float) localSource.getZ());
	}
}
