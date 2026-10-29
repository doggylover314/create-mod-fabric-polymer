package dev.createpolymer.fluids.pipes.valve;

import org.jspecify.annotations.Nullable;
import org.joml.Quaternionf;

import dev.createpolymer.fluids.FluidPropagator;
import dev.createpolymer.fluids.FluidTransportBehaviour;
import dev.createpolymer.fluids.pipes.FluidPipeBlock;
import dev.createpolymer.fluids.pipes.IAxisPipe;
import dev.createpolymer.fluids.pipes.PipeAttachments;
import dev.createpolymer.fluids.pipes.PipeShapes;
import dev.createpolymer.fluids.pipes.ProperWaterloggedBlock;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.kinetics.base.DirectionalAxisKineticBlock;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.registry.feature.PipesRegistry;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.TickPriority;

public class FluidValveBlock extends DirectionalAxisKineticBlock
	implements IAxisPipe, ProperWaterloggedBlock {

	public static final BooleanProperty ENABLED = BooleanProperty.create("enabled");

	public FluidValveBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(ENABLED, false)
			.setValue(WATERLOGGED, false));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter p_220053_2_, BlockPos p_220053_3_,
		CollisionContext p_220053_4_) {
		return PipeShapes.FLUID_VALVE.get(getPipeAxis(state));
	}

	/** Polymer would otherwise answer with the collision of the barrier players see. */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return getShape(state, level, pos, context);
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(ENABLED, WATERLOGGED));
	}

	@Override
	protected boolean prefersConnectionTo(LevelReader reader, BlockPos pos, Direction facing, boolean shaftAxis) {
		if (!shaftAxis) {
			BlockPos offset = pos.relative(facing);
			BlockState blockState = reader.getBlockState(offset);
			return FluidPipeBlock.canConnectTo(reader, offset, blockState, facing);
		}
		return super.prefersConnectionTo(reader, pos, facing, shaftAxis);
	}

	public static Axis getPipeAxis(BlockState state) {
		if (!(state.getBlock() instanceof FluidValveBlock))
			throw new IllegalStateException("Provided BlockState is for a different block.");
		Direction facing = state.getValue(FACING);
		boolean alongFirst = !state.getValue(AXIS_ALONG_FIRST_COORDINATE);
		for (Axis axis : Iterate.axes) {
			if (axis == facing.getAxis())
				continue;
			if (!alongFirst) {
				alongFirst = true;
				continue;
			}
			return axis;
		}
		throw new IllegalStateException("Impossible axis.");
	}

	@Override
	public Axis getAxis(BlockState state) {
		return getPipeAxis(state);
	}

	@Override
	protected boolean canSurvive(BlockState p_196260_1_, LevelReader p_196260_2_, BlockPos p_196260_3_) {
		return true;
	}

	@Override
	protected void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
		super.onPlace(state, world, pos, oldState, isMoving);
		if (world.isClientSide())
			return;
		if (state != oldState)
			world.scheduleTick(pos, this, 1, TickPriority.HIGH);
	}

	public static boolean isOpenAt(BlockState state, Direction d) {
		return d.getAxis() == getPipeAxis(state);
	}

	@Override
	protected void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource r) {
		FluidPropagator.propagateChangedPipe(world, pos, state);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
		return PipesRegistry.FLUID_VALVE_BLOCK_ENTITY;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return withWater(super.getStateForPlacement(context), context);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
		Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (state.getValue(WATERLOGGED))
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		return state;
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return fluidState(state);
	}

	// ---- Polymer ----

	@Override
	protected BlockState particleState() {
		return Blocks.COPPER_BLOCK.weathering().unaffected().defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/**
	 * The valve body of Create's blockstate, the shaft and the pointer of FluidValveRenderer, and the pipe
	 * attachments (only drains can show on a valve).
	 */
	public static class Model extends BaseModel {
		private final Part body;
		private final Part shaft;
		private final Part pointer;
		private final PipeAttachments attachments;
		private float shownPointer = -1;
		private boolean dirty = true;

		public Model(BlockState state) {
			super(state);
			body = showVariant(addPart(ModelItems.block("fluid_valve/block_horizontal_closed")), "fluid_valve", state);
			shaft = addPart(ModelItems.block("shaft"));
			pointer = addPart(ModelItems.block("fluid_valve/pointer"));
			attachments = new PipeAttachments(this);
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			if (!(state.getBlock() instanceof FluidValveBlock))
				return;
			showVariant(body, "fluid_valve", state);
			Axis axis = ((FluidValveBlock) state.getBlock()).getRotationAxis(state);
			shaft.orientation(Orientation.forAxis(axis))
				.spinAxis(axis)
				.apply(0);
			shownPointer = -1;
			dirty = true;
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(state.getBlock() instanceof FluidValveBlock valve))
				return;
			Axis shaftAxis = valve.getRotationAxis(state);
			if (be instanceof FluidValveBlockEntity valveBE) {
				shaft.spin(time, valveBE.getSpeed(),
					Orientation.cogOffset(false, shaftAxis, pos.getX(), pos.getY(), pos.getZ()));
				float value = valveBE.getPointerValue();
				if (value != shownPointer) {
					shownPointer = value;
					Direction facing = state.getValue(FACING);
					Axis pipeAxis = getPipeAxis(state);
					float pointerRotation = Mth.lerp(value, 0, -90);
					int pointerRotationOffset = 0;
					if (pipeAxis.isHorizontal() && shaftAxis == Axis.X || pipeAxis.isVertical())
						pointerRotationOffset = 90;
					Quaternionf q = new Quaternionf()
						.rotationY((float) Math.toRadians(AngleHelper.horizontalAngle(facing)))
						.rotateX((float) Math.toRadians(facing == Direction.UP ? 0 : facing == Direction.DOWN ? 180 : 90))
						.rotateY((float) Math.toRadians(pointerRotationOffset + pointerRotation));
					pointer.orientation(q)
						.apply(1);
				}
			}
			if (!dirty && time % 5 != 0)
				return;
			dirty = false;
			attachments.update(level, pos, state, BlockEntityBehaviour.get(be, FluidTransportBehaviour.TYPE));
		}
	}
}
