package dev.createpolymer.contraptions.chassis;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.decoration.DecorationStates;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.MultiPolymerBlockModel;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class RadialChassisBlock extends AbstractChassisBlock {

	public static final BooleanProperty STICKY_NORTH = BooleanProperty.create("sticky_north");
	public static final BooleanProperty STICKY_SOUTH = BooleanProperty.create("sticky_south");
	public static final BooleanProperty STICKY_EAST = BooleanProperty.create("sticky_east");
	public static final BooleanProperty STICKY_WEST = BooleanProperty.create("sticky_west");

	public RadialChassisBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(STICKY_EAST, false).setValue(STICKY_SOUTH, false).setValue(STICKY_NORTH, false)
				.setValue(STICKY_WEST, false));
		requestClientStates();
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(STICKY_NORTH, STICKY_EAST, STICKY_SOUTH, STICKY_WEST);
		super.createBlockStateDefinition(builder);
	}

	/**
	 * Create's radial chassis blockstate is a multipart: the base cube and one side model for each of the four sticky
	 * properties, turned to the axis. Polymer takes the same list of parts for one block state.
	 */
	@Override
	protected @Nullable BlockState requestClientState(BlockState state) {
		Axis axis = state.getValue(AXIS);
		MultiPolymerBlockModel model = MultiPolymerBlockModel.of();
		switch (axis) {
			case X -> model.with(DecorationStates.model("radial_chassis/base", 90, 90));
			case Y -> model.with(DecorationStates.model("radial_chassis/base"));
			case Z -> model.with(DecorationStates.model("radial_chassis/base", 90, 0));
		}
		side(model, axis, state.getValue(STICKY_SOUTH), Direction.SOUTH);
		side(model, axis, state.getValue(STICKY_WEST), Direction.WEST);
		side(model, axis, state.getValue(STICKY_NORTH), Direction.NORTH);
		side(model, axis, state.getValue(STICKY_EAST), Direction.EAST);
		return DecorationStates.request(BlockModelType.FULL_BLOCK, model);
	}

	/** One side model of the multipart; the models and rotations are the ones of Create's blockstate. */
	private static void side(MultiPolymerBlockModel model, Axis axis, boolean sticky, Direction side) {
		String suffix = sticky ? "_sticky" : "";
		switch (axis) {
			case X -> {
				switch (side) {
					case SOUTH -> model.with(DecorationStates.model("radial_chassis_side_x" + suffix, 180, 0));
					case WEST -> model.with(DecorationStates.model("radial_chassis_side_x" + suffix, 90, 0));
					case NORTH -> model.with(DecorationStates.model("radial_chassis_side_x" + suffix));
					default -> model.with(DecorationStates.model("radial_chassis_side_x" + suffix, 270, 0));
				}
			}
			case Y -> {
				switch (side) {
					case SOUTH -> model.with(DecorationStates.model("radial_chassis_side_y" + suffix));
					case WEST -> model.with(DecorationStates.model("radial_chassis_side_y" + suffix, 0, 90));
					case NORTH -> model.with(DecorationStates.model("radial_chassis_side_y" + suffix, 0, 180));
					default -> model.with(DecorationStates.model("radial_chassis_side_y" + suffix, 0, 270));
				}
			}
			case Z -> {
				switch (side) {
					case SOUTH -> model.with(DecorationStates.model("radial_chassis_side_x" + suffix, 180, 90));
					case WEST -> model.with(DecorationStates.model("radial_chassis_side_z" + suffix, 0, 180));
					case NORTH -> model.with(DecorationStates.model("radial_chassis_side_x" + suffix, 0, 90));
					default -> model.with(DecorationStates.model("radial_chassis_side_z" + suffix));
				}
			}
		}
	}

	@Override
	public BooleanProperty getGlueableSide(BlockState state, Direction face) {
		Axis axis = state.getValue(AXIS);

		if (axis == Axis.X) {
			if (face == Direction.NORTH)
				return STICKY_WEST;
			if (face == Direction.SOUTH)
				return STICKY_EAST;
			if (face == Direction.UP)
				return STICKY_NORTH;
			if (face == Direction.DOWN)
				return STICKY_SOUTH;
		}

		if (axis == Axis.Y) {
			if (face == Direction.NORTH)
				return STICKY_NORTH;
			if (face == Direction.SOUTH)
				return STICKY_SOUTH;
			if (face == Direction.EAST)
				return STICKY_EAST;
			if (face == Direction.WEST)
				return STICKY_WEST;
		}

		if (axis == Axis.Z) {
			if (face == Direction.UP)
				return STICKY_NORTH;
			if (face == Direction.DOWN)
				return STICKY_SOUTH;
			if (face == Direction.EAST)
				return STICKY_EAST;
			if (face == Direction.WEST)
				return STICKY_WEST;
		}

		return null;
	}

}
