package dev.createpolymer.kinetics.base;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** Ported from Create's HorizontalAxisKineticBlock (MIT, The Create Team). */
public abstract class HorizontalAxisKineticBlock extends KineticBlock {
	public static final EnumProperty<Axis> HORIZONTAL_AXIS = BlockStateProperties.HORIZONTAL_AXIS;

	public HorizontalAxisKineticBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HORIZONTAL_AXIS);
		super.createBlockStateDefinition(builder);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Axis preferredAxis = getPreferredHorizontalAxis(context);
		if (preferredAxis != null)
			return this.defaultBlockState().setValue(HORIZONTAL_AXIS, preferredAxis);
		return this.defaultBlockState().setValue(HORIZONTAL_AXIS, context.getHorizontalDirection().getClockWise().getAxis());
	}

	public static Axis getPreferredHorizontalAxis(BlockPlaceContext context) {
		Direction preferredSide = null;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockState blockState = context.getLevel().getBlockState(context.getClickedPos().relative(side));
			if (blockState.getBlock() instanceof IRotate rotate) {
				if (rotate.hasShaftTowards(context.getLevel(), context.getClickedPos().relative(side), blockState, side.getOpposite()))
					if (preferredSide != null && preferredSide.getAxis() != side.getAxis()) {
						preferredSide = null;
						break;
					} else {
						preferredSide = side;
					}
			}
		}
		return preferredSide == null ? null : preferredSide.getAxis();
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return state.getValue(HORIZONTAL_AXIS);
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face.getAxis() == state.getValue(HORIZONTAL_AXIS);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rot) {
		Axis axis = state.getValue(HORIZONTAL_AXIS);
		return state.setValue(HORIZONTAL_AXIS, rot.rotate(Direction.get(AxisDirection.POSITIVE, axis)).getAxis());
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirrorIn) {
		return state;
	}
}
