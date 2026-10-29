package dev.createpolymer.kinetics.gearbox;

import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Ported from Create's GearboxBlockEntity / DirectionalShaftHalvesBlockEntity (MIT, The Create Team). */
public class GearboxBlockEntity extends KineticBlockEntity {

	public GearboxBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.GEARBOX, pos, state);
	}

	public Direction getSourceFacing() {
		BlockPos localSource = source.subtract(getBlockPos());
		return Direction.getApproximateNearest((float) localSource.getX(), (float) localSource.getY(), (float) localSource.getZ());
	}
}
