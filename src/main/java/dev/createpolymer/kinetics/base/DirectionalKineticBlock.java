package dev.createpolymer.kinetics.base;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** Ported from Create's DirectionalKineticBlock (MIT, The Create Team). */
public abstract class DirectionalKineticBlock extends KineticBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

	public DirectionalKineticBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
		super.createBlockStateDefinition(builder);
	}

	public Direction getPreferredFacing(BlockPlaceContext context) {
		Direction preferredSide = null;
		for (Direction side : Direction.values()) {
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
		return preferredSide;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction preferred = getPreferredFacing(context);
		boolean sneaking = context.getPlayer() != null && context.getPlayer().isShiftKeyDown();
		if (preferred == null || sneaking) {
			Direction nearestLookingDirection = context.getNearestLookingDirection();
			return defaultBlockState().setValue(FACING, sneaking ? nearestLookingDirection : nearestLookingDirection.getOpposite());
		}
		return defaultBlockState().setValue(FACING, preferred.getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rot) {
		return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirrorIn) {
		return state.rotate(mirrorIn.getRotation(state.getValue(FACING)));
	}
}
