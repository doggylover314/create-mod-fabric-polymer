package dev.createpolymer.kinetics.gantry;

import dev.createpolymer.contraptions.gantry.GantryCarriageBlock;
import dev.createpolymer.contraptions.gantry.GantryCarriageBlockEntity;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.util.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class GantryShaftBlockEntity extends KineticBlockEntity {

	public GantryShaftBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
		super(typeIn, pos, state);
	}

	public void checkAttachedCarriageBlocks() {
		if (!canAssembleOn())
			return;
		for (Direction d : Iterate.directions) {
			if (d.getAxis() == getBlockState().getValue(GantryShaftBlock.FACING)
				.getAxis())
				continue;
			BlockPos offset = worldPosition.relative(d);
			BlockState pinionState = level.getBlockState(offset);
			if (!(pinionState.getBlock() instanceof GantryCarriageBlock))
				continue;
			if (pinionState.getValue(GantryCarriageBlock.FACING) != d)
				continue;
			BlockEntity blockEntity = level.getBlockEntity(offset);
			if (blockEntity instanceof GantryCarriageBlockEntity)
				((GantryCarriageBlockEntity) blockEntity).queueAssembly();
		}
	}

	@Override
	public void onSpeedChanged(float previousSpeed) {
		super.onSpeedChanged(previousSpeed);
		checkAttachedCarriageBlocks();
	}

	@Override
	public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo, BlockPos diff,
		boolean connectedViaAxes, boolean connectedViaCogs) {
		float defaultModifier =
			super.propagateRotationTo(target, stateFrom, stateTo, diff, connectedViaAxes, connectedViaCogs);

		if (connectedViaAxes)
			return defaultModifier;
		if (!stateFrom.getValue(GantryShaftBlock.POWERED))
			return defaultModifier;
		if (!(stateTo.getBlock() instanceof GantryCarriageBlock))
			return defaultModifier;

		Direction direction = Direction.getApproximateNearest(diff.getX(), diff.getY(), diff.getZ());
		if (stateTo.getValue(GantryCarriageBlock.FACING) != direction)
			return defaultModifier;
		return GantryCarriageBlockEntity.getGantryPinionModifier(stateFrom.getValue(GantryShaftBlock.FACING),
			stateTo.getValue(GantryCarriageBlock.FACING));
	}

	@Override
	public boolean isCustomConnection(KineticBlockEntity other, BlockState state, BlockState otherState) {
		if (!(otherState.getBlock() instanceof GantryCarriageBlock))
			return false;
		final BlockPos diff = other.getBlockPos()
			.subtract(worldPosition);
		Direction direction = Direction.getApproximateNearest(diff.getX(), diff.getY(), diff.getZ());
		return otherState.getValue(GantryCarriageBlock.FACING) == direction;
	}

	public boolean canAssembleOn() {
		BlockState blockState = getBlockState();
		if (!(blockState.getBlock() instanceof GantryShaftBlock))
			return false;
		if (blockState.getValue(GantryShaftBlock.POWERED))
			return false;
		float speed = getPinionMovementSpeed();

		switch (blockState.getValue(GantryShaftBlock.PART)) {
		case END:
			return speed < 0;
		case MIDDLE:
			return speed != 0;
		case START:
			return speed > 0;
		case SINGLE:
		default:
			return false;
		}
	}

	public float getPinionMovementSpeed() {
		BlockState blockState = getBlockState();
		if (!(blockState.getBlock() instanceof GantryShaftBlock))
			return 0;
		return Mth.clamp(convertToLinear(-getSpeed()), -.49f, .49f);
	}

}
