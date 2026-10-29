package dev.createpolymer.kinetics.chaindrive;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.base.RotatedPillarKineticBlock;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Ported from Create's ChainDriveBlock (MIT, The Create Team): encased chain drives placed
 * side by side link up and pass rotation 1:1 to each other.
 */
public class ChainDriveBlock extends RotatedPillarKineticBlock {
	public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
	public static final BooleanProperty CONNECTED_ALONG_FIRST_COORDINATE = BooleanProperty.create("axis_along_first");

	public ChainDriveBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(PART, Part.NONE).setValue(CONNECTED_ALONG_FIRST_COORDINATE, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(PART, CONNECTED_ALONG_FIRST_COORDINATE));
	}

	@Override
	public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
		return ModBlockEntities.ENCASED_SHAFT;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Axis placedAxis = context.getNearestLookingDirection().getAxis();
		Axis axis = context.getPlayer() != null && context.getPlayer().isShiftKeyDown() ? placedAxis : getPreferredAxis(context);
		if (axis == null)
			axis = placedAxis;
		BlockState state = defaultBlockState().setValue(AXIS, axis);
		for (Direction facing : Direction.values()) {
			if (facing.getAxis() == axis)
				continue;
			BlockPos pos = context.getClickedPos();
			BlockPos offset = pos.relative(facing);
			state = connect(state, facing, context.getLevel().getBlockState(offset));
		}
		return state;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
									 Direction face, BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
		return connect(state, face, neighbour);
	}

	/** Create's ChainDriveBlock.updateShape. */
	public static BlockState connect(BlockState stateIn, Direction face, BlockState neighbour) {
		Part part = stateIn.getValue(PART);
		Axis axis = stateIn.getValue(AXIS);
		boolean connectionAlongFirst = stateIn.getValue(CONNECTED_ALONG_FIRST_COORDINATE);
		Axis connectionAxis = connectionAlongFirst ? (axis == Axis.X ? Axis.Y : Axis.X) : (axis == Axis.Z ? Axis.Y : Axis.Z);

		Axis faceAxis = face.getAxis();
		boolean facingAlongFirst = axis == Axis.X ? faceAxis.isVertical() : faceAxis == Axis.X;
		boolean positive = face.getAxisDirection() == AxisDirection.POSITIVE;

		if (axis == faceAxis)
			return stateIn;

		if (!(neighbour.getBlock() instanceof ChainDriveBlock)) {
			if (facingAlongFirst != connectionAlongFirst || part == Part.NONE)
				return stateIn;
			if (part == Part.MIDDLE)
				return stateIn.setValue(PART, positive ? Part.END : Part.START);
			if ((part == Part.START) == positive)
				return stateIn.setValue(PART, Part.NONE);
			return stateIn;
		}

		Part otherPart = neighbour.getValue(PART);
		Axis otherAxis = neighbour.getValue(AXIS);
		boolean otherConnection = neighbour.getValue(CONNECTED_ALONG_FIRST_COORDINATE);
		Axis otherConnectionAxis = otherConnection ? (otherAxis == Axis.X ? Axis.Y : Axis.X) : (otherAxis == Axis.Z ? Axis.Y : Axis.Z);

		if (neighbour.getValue(AXIS) == faceAxis)
			return stateIn;
		if (otherPart != Part.NONE && otherConnectionAxis != faceAxis)
			return stateIn;

		if (part == Part.NONE) {
			part = positive ? Part.START : Part.END;
			connectionAlongFirst = axis == Axis.X ? faceAxis.isVertical() : faceAxis == Axis.X;
		} else if (connectionAxis != faceAxis) {
			return stateIn;
		}

		if ((part == Part.START) != positive)
			part = Part.MIDDLE;

		return stateIn.setValue(PART, part).setValue(CONNECTED_ALONG_FIRST_COORDINATE, connectionAlongFirst);
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face.getAxis() == state.getValue(AXIS);
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return state.getValue(AXIS);
	}

	public static boolean areBlocksConnected(BlockState state, BlockState other, Direction facing) {
		Part part = state.getValue(PART);
		Axis connectionAxis = getConnectionAxis(state);
		Axis otherConnectionAxis = getConnectionAxis(other);

		if (otherConnectionAxis != connectionAxis)
			return false;
		if (facing.getAxis() != connectionAxis)
			return false;
		if (facing.getAxisDirection() == AxisDirection.POSITIVE && (part == Part.MIDDLE || part == Part.START))
			return true;
		return facing.getAxisDirection() == AxisDirection.NEGATIVE && (part == Part.MIDDLE || part == Part.END);
	}

	protected static Axis getConnectionAxis(BlockState state) {
		Axis axis = state.getValue(AXIS);
		boolean connectionAlongFirst = state.getValue(CONNECTED_ALONG_FIRST_COORDINATE);
		return connectionAlongFirst ? (axis == Axis.X ? Axis.Y : Axis.X) : (axis == Axis.Z ? Axis.Y : Axis.Z);
	}

	public enum Part implements StringRepresentable {
		START, MIDDLE, END, NONE;

		@Override
		public String getSerializedName() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	@Override
	protected BlockState particleState() {
		return Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	public static class Model extends BaseModel {
		private final dev.createpolymer.visual.Part casing;
		private final dev.createpolymer.visual.Part shaft;

		public Model(BlockState state) {
			super(state);
			casing = addPart(ModelItems.block("shaft"));
			shaft = addPart(ModelItems.block("shaft"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(casing, "encased_chain_drive", state);
			Axis axis = state.getValue(AXIS);
			shaft.orientation(Orientation.forAxis(axis)).spinAxis(axis).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (be instanceof KineticBlockEntity kbe) {
				Axis axis = state.getValue(AXIS);
				shaft.spin(time, kbe.getSpeed(), Orientation.cogOffset(false, axis, pos.getX(), pos.getY(), pos.getZ()));
			}
		}
	}
}
