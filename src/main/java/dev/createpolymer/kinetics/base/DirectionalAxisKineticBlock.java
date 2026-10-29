package dev.createpolymer.kinetics.base;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Ported from Create's DirectionalAxisKineticBlock (MIT, The Create Team). */
public abstract class DirectionalAxisKineticBlock extends DirectionalKineticBlock {
	public static final BooleanProperty AXIS_ALONG_FIRST_COORDINATE = BooleanProperty.create("axis_along_first");

	public DirectionalAxisKineticBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AXIS_ALONG_FIRST_COORDINATE);
		super.createBlockStateDefinition(builder);
	}

	protected Direction getFacingForPlacement(BlockPlaceContext context) {
		Direction facing = context.getNearestLookingDirection().getOpposite();
		if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown())
			facing = facing.getOpposite();
		return facing;
	}

	protected boolean getAxisAlignmentForPlacement(BlockPlaceContext context) {
		return context.getHorizontalDirection().getAxis() == Axis.X;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction facing = getFacingForPlacement(context);
		BlockPos pos = context.getClickedPos();
		Level world = context.getLevel();
		boolean alongFirst = false;
		Axis faceAxis = facing.getAxis();

		if (faceAxis.isHorizontal()) {
			alongFirst = faceAxis == Axis.Z;
			Direction positivePerpendicular = faceAxis == Axis.X ? Direction.SOUTH : Direction.EAST;

			boolean shaftAbove = prefersConnectionTo(world, pos, Direction.UP, true);
			boolean shaftBelow = prefersConnectionTo(world, pos, Direction.DOWN, true);
			boolean preferLeft = prefersConnectionTo(world, pos, positivePerpendicular, false);
			boolean preferRight = prefersConnectionTo(world, pos, positivePerpendicular.getOpposite(), false);

			if (shaftAbove || shaftBelow || preferLeft || preferRight)
				alongFirst = faceAxis == Axis.X;
		}

		if (faceAxis.isVertical()) {
			alongFirst = getAxisAlignmentForPlacement(context);
			Direction preferredSide = null;
			for (Direction side : Direction.Plane.HORIZONTAL) {
				if (!prefersConnectionTo(world, pos, side, true) && !prefersConnectionTo(world, pos, side.getClockWise(), false))
					continue;
				if (preferredSide != null && preferredSide.getAxis() != side.getAxis()) {
					preferredSide = null;
					break;
				}
				preferredSide = side;
			}
			if (preferredSide != null)
				alongFirst = preferredSide.getAxis() == Axis.X;
		}

		return defaultBlockState().setValue(FACING, facing).setValue(AXIS_ALONG_FIRST_COORDINATE, alongFirst);
	}

	protected boolean prefersConnectionTo(LevelReader reader, BlockPos pos, Direction facing, boolean shaftAxis) {
		if (!shaftAxis)
			return false;
		BlockPos neighbourPos = pos.relative(facing);
		BlockState blockState = reader.getBlockState(neighbourPos);
		return blockState.getBlock() instanceof IRotate rotate && rotate.hasShaftTowards(reader, neighbourPos, blockState, facing.getOpposite());
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		Axis pistonAxis = state.getValue(FACING).getAxis();
		boolean alongFirst = state.getValue(AXIS_ALONG_FIRST_COORDINATE);
		return switch (pistonAxis) {
			case X -> alongFirst ? Axis.Y : Axis.Z;
			case Y -> alongFirst ? Axis.X : Axis.Z;
			case Z -> alongFirst ? Axis.X : Axis.Y;
		};
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rot) {
		if (rot.ordinal() % 2 == 1)
			state = state.cycle(AXIS_ALONG_FIRST_COORDINATE);
		return super.rotate(state, rot);
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face.getAxis() == getRotationAxis(state);
	}
}
