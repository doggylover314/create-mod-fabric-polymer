package dev.createpolymer.kinetics.chaindrive;

import dev.createpolymer.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity of the encased chain drive. Create gives it a plain KineticBlockEntity (type "encased_shaft"); here it
 * has a block entity type of its own and ChainGearshiftBlockEntity extends it. The speed ratio between attached
 * chain drives comes from {@link ChainDriveBlock#getRotationSpeedModifier}, called by RotationPropagator.
 */
public class ChainDriveBlockEntity extends KineticBlockEntity {

	public ChainDriveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}
}
