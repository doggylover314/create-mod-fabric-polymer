package dev.createpolymer.fluids.pump;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.fluids.FluidPropagator;
import dev.createpolymer.fluids.FluidTransportBehaviour;
import dev.createpolymer.fluids.pipes.FluidPipeBlock;
import dev.createpolymer.fluids.pipes.PipeAttachments;
import dev.createpolymer.fluids.pipes.PipeShapes;
import dev.createpolymer.foundation.block.ProperWaterloggedBlock;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.base.ICogWheel;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.registry.ModSounds;
import dev.createpolymer.registry.feature.PipesRegistry;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.TickPriority;

public class PumpBlock extends DirectionalKineticBlock
	implements SimpleWaterloggedBlock, ICogWheel, IWrenchable {

	public PumpBlock(Properties p_i48415_1_) {
		super(p_i48415_1_);
		registerDefaultState(super.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false));
	}

	/** Create's getRotatedBlockState: the wrench turns the pump around. */
	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState rotated = state.setValue(FACING, state.getValue(FACING)
			.getOpposite());
		if (!rotated.canSurvive(level, pos))
			return InteractionResult.SUCCESS;
		KineticBlockEntity.switchToBlockState(level, pos, Block.updateFromNeighbourShapes(rotated, level, pos));
		ModSounds.wrenchRotate(level, pos);
		return InteractionResult.SUCCESS;
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return state.getValue(FACING)
			.getAxis();
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter p_220053_2_, BlockPos p_220053_3_,
		CollisionContext p_220053_4_) {
		return PipeShapes.PUMP.get(state.getValue(FACING));
	}

	/** Polymer would otherwise answer with the collision of the barrier players see. */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return getShape(state, level, pos, context);
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false)
			: Fluids.EMPTY.defaultFluidState();
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(BlockStateProperties.WATERLOGGED);
		super.createBlockStateDefinition(builder);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
		Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (state.getValue(BlockStateProperties.WATERLOGGED))
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		Direction changed = FluidPropagator.validateNeighbourChange(state, direction, neighbourState);
		if (changed != null && isOpenAt(state, changed))
			ticks.scheduleTick(pos, this, 1, TickPriority.HIGH);
		return state;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState toPlace = super.getStateForPlacement(context);
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();

		boolean isShiftKeyDown = context.getPlayer() != null && context.getPlayer().isShiftKeyDown();
		toPlace = ProperWaterloggedBlock.withWater(level, toPlace, pos);

		Direction nearestLookingDirection = context.getNearestLookingDirection();
		Direction targetDirection = isShiftKeyDown ? nearestLookingDirection : nearestLookingDirection.getOpposite();
		Direction bestConnectedDirection = null;
		double bestDistance = Double.MAX_VALUE;

		for (Direction d : Iterate.directions) {
			BlockPos adjPos = pos.relative(d);
			BlockState adjState = level.getBlockState(adjPos);
			if (!FluidPipeBlock.canConnectTo(level, adjPos, adjState, d))
				continue;
			double distance = Vec3.atLowerCornerOf(d.getUnitVec3i())
				.distanceTo(Vec3.atLowerCornerOf(targetDirection.getUnitVec3i()));
			if (distance > bestDistance)
				continue;
			bestDistance = distance;
			bestConnectedDirection = d;
		}

		if (bestConnectedDirection != null && bestConnectedDirection.getAxis() != targetDirection.getAxis() && !isShiftKeyDown)
			return toPlace.setValue(FACING, bestConnectedDirection);

		return toPlace;
	}

	public static boolean isPump(BlockState state) {
		return state.getBlock() instanceof PumpBlock;
	}

	@Override
	protected void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
		super.onPlace(state, world, pos, oldState, isMoving);
		if (world.isClientSide())
			return;
		if (state != oldState)
			world.scheduleTick(pos, this, 1, TickPriority.HIGH);

		if (isPump(state) && isPump(oldState) && state.getValue(FACING) == oldState.getValue(FACING)
			.getOpposite()) {
			BlockEntity blockEntity = world.getBlockEntity(pos);
			if (!(blockEntity instanceof PumpBlockEntity pump))
				return;
			pump.pressureUpdate = true;
		}
	}

	public static boolean isOpenAt(BlockState state, Direction d) {
		return d.getAxis() == state.getValue(FACING)
			.getAxis();
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
		return PipesRegistry.MECHANICAL_PUMP_BLOCK_ENTITY;
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

	/** Create's block model, the spinning cog of PumpRenderer and the pipe attachments. */
	public static class Model extends BaseModel {
		private final Part body;
		private final Part cog;
		private final PipeAttachments attachments;
		private boolean dirty = true;

		public Model(BlockState state) {
			super(state);
			body = showVariant(addPart(ModelItems.block("mechanical_pump/block")), "mechanical_pump", state);
			cog = addPart(ModelItems.block("mechanical_pump/cog"));
			attachments = new PipeAttachments(this);
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			if (!isPump(state))
				return;
			showVariant(body, "mechanical_pump", state);
			Direction facing = state.getValue(FACING);
			cog.orientation(Orientation.fromSouth(facing))
				.spinAxis(facing.getAxis())
				.apply(0);
			dirty = true;
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!isPump(state))
				return;
			if (be instanceof PumpBlockEntity pump) {
				Axis axis = state.getValue(FACING)
					.getAxis();
				cog.spin(time, pump.getSpeed(), Orientation.cogOffset(false, axis, pos.getX(), pos.getY(), pos.getZ()));
			}
			if (!dirty && time % 5 != 0)
				return;
			dirty = false;
			attachments.update(level, pos, state, BlockEntityBehaviour.get(be, FluidTransportBehaviour.TYPE));
		}
	}
}
