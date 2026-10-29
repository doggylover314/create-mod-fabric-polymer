package dev.createpolymer.fluids.pipes;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.fluids.FluidPropagator;
import dev.createpolymer.fluids.FluidTransportBehaviour;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.foundation.block.ProperWaterloggedBlock;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilterDisplay;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.registry.feature.PipesRegistry;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.VoxelShaper;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.TickPriority;

public class SmartFluidPipeBlock extends FaceAttachedHorizontalDirectionalBlock
	implements IBE<SmartFluidPipeBlockEntity>, IAxisPipe, IWrenchable, ProperWaterloggedBlock, PolymerModelBlock {

	public SmartFluidPipeBlock(Properties p_i48339_1_) {
		super(p_i48339_1_);
		registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(FACE, FACING, WATERLOGGED);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		BlockState stateForPlacement = super.getStateForPlacement(ctx);
		Axis prefferedAxis = null;
		BlockPos pos = ctx.getClickedPos();
		Level world = ctx.getLevel();
		for (Direction side : Iterate.directions) {
			if (!prefersConnectionTo(world, pos, side))
				continue;
			if (prefferedAxis != null && prefferedAxis != side.getAxis()) {
				prefferedAxis = null;
				break;
			}
			prefferedAxis = side.getAxis();
		}

		if (prefferedAxis == Axis.Y)
			stateForPlacement = stateForPlacement.setValue(FACE, AttachFace.WALL)
				.setValue(FACING, stateForPlacement.getValue(FACING)
					.getOpposite());
		else if (prefferedAxis != null) {
			if (stateForPlacement.getValue(FACE) == AttachFace.WALL)
				stateForPlacement = stateForPlacement.setValue(FACE, AttachFace.FLOOR);
			for (Direction direction : ctx.getNearestLookingDirections()) {
				if (direction.getAxis() != prefferedAxis)
					continue;
				stateForPlacement = stateForPlacement.setValue(FACING, direction.getOpposite());
			}
		}

		return withWater(stateForPlacement, ctx);
	}

	protected boolean prefersConnectionTo(LevelReader reader, BlockPos pos, Direction facing) {
		BlockPos offset = pos.relative(facing);
		BlockState blockState = reader.getBlockState(offset);
		return FluidPipeBlock.canConnectTo(reader, offset, blockState, facing);
	}

	@Override
	protected boolean canSurvive(BlockState p_196260_1_, LevelReader p_196260_2_, BlockPos p_196260_3_) {
		return true;
	}

	@Override
	protected void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
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

	protected static Axis getPipeAxis(BlockState state) {
		return state.getValue(FACE) == AttachFace.WALL ? Axis.Y
			: state.getValue(FACING)
				.getAxis();
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter p_220053_2_, BlockPos p_220053_3_,
		CollisionContext p_220053_4_) {
		AttachFace face = state.getValue(FACE);
		VoxelShaper shape = face == AttachFace.FLOOR ? PipeShapes.SMART_FLUID_PIPE_FLOOR
			: face == AttachFace.CEILING ? PipeShapes.SMART_FLUID_PIPE_CEILING : PipeShapes.SMART_FLUID_PIPE_WALL;
		return shape.get(state.getValue(FACING));
	}

	/** Polymer would otherwise answer with the collision of the barrier players see. */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return getShape(state, level, pos, context);
	}

	@Override
	public Axis getAxis(BlockState state) {
		return getPipeAxis(state);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	protected BlockState updateShape(BlockState pState, LevelReader level, ScheduledTickAccess ticks, BlockPos pCurrentPos,
		Direction pFacing, BlockPos neighbourPos, BlockState pFacingState, RandomSource random) {
		if (pState.getValue(WATERLOGGED))
			ticks.scheduleTick(pCurrentPos, net.minecraft.world.level.material.Fluids.WATER,
				net.minecraft.world.level.material.Fluids.WATER.getTickDelay(level));
		return pState;
	}

	@Override
	protected FluidState getFluidState(BlockState pState) {
		return fluidState(pState);
	}

	@Override
	public Class<SmartFluidPipeBlockEntity> getBlockEntityClass() {
		return SmartFluidPipeBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends SmartFluidPipeBlockEntity> getBlockEntityType() {
		return PipesRegistry.SMART_FLUID_PIPE_BLOCK_ENTITY;
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.COPPER_BLOCK.weathering().unaffected().defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/** Create's smart_fluid_pipe blockstate model, the pipe attachments and the filter in its slot. */
	public static class Model extends BaseModel {
		private final Part body;
		private final PipeAttachments attachments;
		private final FilterDisplay filter = new FilterDisplay(this);
		private boolean dirty = true;

		public Model(BlockState state) {
			super(state);
			body = showVariant(addPart(ModelItems.block("smart_fluid_pipe/block")), "smart_fluid_pipe", state);
			attachments = new PipeAttachments(this);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(body, "smart_fluid_pipe", state);
			dirty = true;
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (be instanceof SmartBlockEntity sbe)
				filter.update(sbe);
			if (!dirty && time % 5 != 0)
				return;
			dirty = false;
			attachments.update(level, pos, state, BlockEntityBehaviour.get(be, FluidTransportBehaviour.TYPE));
		}
	}
}
