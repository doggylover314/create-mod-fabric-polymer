package dev.createpolymer.kinetics.chaindrive;

import dev.createpolymer.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity of the encased chain drive. Create's RotationPropagator asks
 * {@link ChainDriveBlock#getRotationSpeedModifier} for attached chain drives, so that an adjustable
 * chain gearshift scales the speed in both directions. This port's propagator only has the per-block-entity
 * hook, so every chain drive answers it for the connections it starts.
 */
public class ChainDriveBlockEntity extends KineticBlockEntity {

	public ChainDriveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo, BlockPos diff,
									 boolean connectedViaAxes, boolean connectedViaCogs) {
		if (connectedViaAxes)
			return 0;
		if (!(stateFrom.getBlock() instanceof ChainDriveBlock) || !(stateTo.getBlock() instanceof ChainDriveBlock))
			return 0;
		Direction direction = Direction.getApproximateNearest((float) diff.getX(), (float) diff.getY(), (float) diff.getZ());
		if (!ChainDriveBlock.areBlocksConnected(stateFrom, stateTo, direction))
			return 0;
		return ChainDriveBlock.getRotationSpeedModifier(this, target);
	}
}
