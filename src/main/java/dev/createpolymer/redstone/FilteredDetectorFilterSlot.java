package dev.createpolymer.redstone;

import org.joml.Matrix4f;

import dev.createpolymer.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class FilteredDetectorFilterSlot extends ValueBoxTransform.Sided {

	private boolean hasSlotAtBottom;

	public FilteredDetectorFilterSlot(boolean hasSlotAtBottom) {
		this.hasSlotAtBottom = hasSlotAtBottom;
	}

	@Override
	protected boolean isSideActive(BlockState state, Direction direction) {
		Direction targetDirection = DirectedDirectionalBlock.getTargetDirection(state);
		if (direction == targetDirection)
			return false;
		if (targetDirection.getOpposite() == direction)
			return true;

		if (targetDirection.getAxis() != Axis.Y)
			return direction == Direction.UP || direction == Direction.DOWN && hasSlotAtBottom;
		if (targetDirection == Direction.UP)
			direction = direction.getOpposite();
		if (!hasSlotAtBottom)
			return direction == state.getValue(DirectedDirectionalBlock.FACING);

		return direction.getAxis() == state.getValue(DirectedDirectionalBlock.FACING)
			.getClockWise()
			.getAxis();
	}

	@Override
	public void rotate(LevelAccessor level, BlockPos pos, BlockState state, Matrix4f ms) {
		super.rotate(level, pos, state, ms);
		Direction facing = state.getValue(DirectedDirectionalBlock.FACING);
		if (facing.getAxis() == Axis.Y)
			return;
		if (getSide() != Direction.UP)
			return;
		ms.rotateZ((float) Math.toRadians(-AngleHelper.horizontalAngle(facing) + 180));
	}

	@Override
	protected Vec3 getSouthLocation() {
		return VecHelper.voxelSpace(8f, 8f, 15.5f);
	}

}
