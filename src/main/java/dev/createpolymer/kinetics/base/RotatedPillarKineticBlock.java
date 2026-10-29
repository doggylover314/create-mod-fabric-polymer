package dev.createpolymer.kinetics.base;

import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** Ported from Create's RotatedPillarKineticBlock (MIT, The Create Team). */
public abstract class RotatedPillarKineticBlock extends KineticBlock {
	public static final EnumProperty<Axis> AXIS = BlockStateProperties.AXIS;

	public RotatedPillarKineticBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.defaultBlockState().setValue(AXIS, Axis.Y));
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rot) {
		return switch (rot) {
			case COUNTERCLOCKWISE_90, CLOCKWISE_90 -> switch (state.getValue(AXIS)) {
				case X -> state.setValue(AXIS, Axis.Z);
				case Z -> state.setValue(AXIS, Axis.X);
				default -> state;
			};
			default -> state;
		};
	}

	public static Axis getPreferredAxis(BlockPlaceContext context) {
		Axis preferredAxis = null;
		for (Direction side : Direction.values()) {
			BlockState blockState = context.getLevel().getBlockState(context.getClickedPos().relative(side));
			if (blockState.getBlock() instanceof IRotate rotate) {
				if (rotate.hasShaftTowards(context.getLevel(), context.getClickedPos().relative(side), blockState, side.getOpposite()))
					if (preferredAxis != null && preferredAxis != side.getAxis()) {
						preferredAxis = null;
						break;
					} else {
						preferredAxis = side.getAxis();
					}
			}
		}
		return preferredAxis;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AXIS);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Axis preferredAxis = getPreferredAxis(context);
		boolean sneaking = context.getPlayer() != null && context.getPlayer().isShiftKeyDown();
		if (preferredAxis != null && !sneaking)
			return this.defaultBlockState().setValue(AXIS, preferredAxis);
		return this.defaultBlockState().setValue(AXIS, preferredAxis != null && sneaking
			? context.getClickedFace().getAxis()
			: context.getNearestLookingDirection().getAxis());
	}
}
