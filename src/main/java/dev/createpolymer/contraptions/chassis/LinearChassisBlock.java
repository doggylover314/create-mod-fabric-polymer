package dev.createpolymer.contraptions.chassis;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.decoration.DecorationStates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class LinearChassisBlock extends AbstractChassisBlock {

	public static final BooleanProperty STICKY_TOP = BooleanProperty.create("sticky_top");
	public static final BooleanProperty STICKY_BOTTOM = BooleanProperty.create("sticky_bottom");

	/** Name of Create's models for this block: "linear_chassis" or "secondary_linear_chassis". */
	private final String model;

	public LinearChassisBlock(Properties properties, String model) {
		super(properties);
		this.model = model;
		registerDefaultState(defaultBlockState().setValue(STICKY_TOP, false)
			.setValue(STICKY_BOTTOM, false));
		requestClientStates();
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(STICKY_TOP, STICKY_BOTTOM);
		super.createBlockStateDefinition(builder);
	}

	/** Create's linear chassis blockstate: the model with the sticky ends, turned to the axis. */
	@Override
	protected @Nullable BlockState requestClientState(BlockState state) {
		boolean top = state.getValue(STICKY_TOP);
		boolean bottom = state.getValue(STICKY_BOTTOM);
		String name = model + (top && bottom ? "_top_bottom" : top ? "_top" : bottom ? "_bottom" : "");
		return switch (state.getValue(AXIS)) {
			case X -> DecorationStates.fullBlock(DecorationStates.model(name, 90, 90));
			case Y -> DecorationStates.fullBlock(DecorationStates.model(name));
			case Z -> DecorationStates.fullBlock(DecorationStates.model(name, 90, 180));
		};
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos placedOnPos = context.getClickedPos()
			.relative(context.getClickedFace()
				.getOpposite());
		BlockState blockState = context.getLevel()
			.getBlockState(placedOnPos);

		if (context.getPlayer() == null || !context.getPlayer()
			.isShiftKeyDown()) {
			if (isChassis(blockState))
				return defaultBlockState().setValue(AXIS, blockState.getValue(AXIS));
			return defaultBlockState().setValue(AXIS, context.getNearestLookingDirection()
				.getAxis());
		}
		return super.getStateForPlacement(context);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
		Direction side, BlockPos neighbourPos, BlockState other, RandomSource random) {
		BooleanProperty property = getGlueableSide(state, side);
		if (property == null || !sameKind(state, other) || state.getValue(AXIS) != other.getValue(AXIS))
			return state;
		return state.setValue(property, false);
	}

	@Override
	public BooleanProperty getGlueableSide(BlockState state, Direction face) {
		if (face.getAxis() != state.getValue(AXIS))
			return null;
		return face.getAxisDirection() == AxisDirection.POSITIVE ? STICKY_TOP : STICKY_BOTTOM;
	}

	@Override
	protected boolean glueAllowedOnSide(BlockGetter world, BlockPos pos, BlockState state, Direction side) {
		BlockState other = world.getBlockState(pos.relative(side));
		return !sameKind(other, state) || state.getValue(AXIS) != other.getValue(AXIS);
	}

	public static boolean isChassis(BlockState state) {
		return state.getBlock() instanceof LinearChassisBlock;
	}

	public static boolean sameKind(BlockState state1, BlockState state2) {
		return state1.getBlock() == state2.getBlock();
	}

}
