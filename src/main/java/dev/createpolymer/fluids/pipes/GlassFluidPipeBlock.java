package dev.createpolymer.fluids.pipes;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.decoration.bracket.BracketDisplay;
import dev.createpolymer.fluids.FluidTransportBehaviour;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.registry.feature.PipesRegistry;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;

public class GlassFluidPipeBlock extends AxisPipeBlock implements IBE<StraightPipeBlockEntity>, SimpleWaterloggedBlock, PolymerModelBlock {

	public static final BooleanProperty ALT = BooleanProperty.create("alt");

	public GlassFluidPipeBlock(Properties p_i48339_1_) {
		super(p_i48339_1_);
		registerDefaultState(defaultBlockState().setValue(ALT, false).setValue(BlockStateProperties.WATERLOGGED, false));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> p_206840_1_) {
		super.createBlockStateDefinition(p_206840_1_.add(ALT, BlockStateProperties.WATERLOGGED));
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		if (tryRemoveBracket(context))
			return InteractionResult.SUCCESS;
		BlockState newState;
		Level world = context.getLevel();
		BlockPos pos = context.getClickedPos();
		FluidTransportBehaviour.cacheFlows(world, pos);
		newState = toRegularPipe(world, pos, state).setValue(BlockStateProperties.WATERLOGGED, state.getValue(BlockStateProperties.WATERLOGGED));
		world.setBlock(pos, newState, Block.UPDATE_ALL);
		FluidTransportBehaviour.loadFlows(world, pos);
		return InteractionResult.SUCCESS;
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		FluidState ifluidstate = context.getLevel()
			.getFluidState(context.getClickedPos());
		BlockState state = super.getStateForPlacement(context);
		return state == null ? null : state.setValue(BlockStateProperties.WATERLOGGED,
			ifluidstate.getType() == Fluids.WATER);
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false)
			: Fluids.EMPTY.defaultFluidState();
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	public Class<StraightPipeBlockEntity> getBlockEntityClass() {
		return StraightPipeBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends StraightPipeBlockEntity> getBlockEntityType() {
		return PipesRegistry.GLASS_FLUID_PIPE_BLOCK_ENTITY;
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

	/**
	 * The window model along the pipe's axis plus the rims and connections. Create also draws the fluid flowing
	 * inside through a custom renderer; that is not shown here.
	 */
	public static class Model extends BaseModel {
		private final dev.createpolymer.visual.Part window;
		private final PipeAttachments attachments;
		private final BracketDisplay bracket = new BracketDisplay(this);
		private boolean dirty = true;

		public Model(BlockState state) {
			super(state);
			window = showVariant(addPart(ModelItems.block("fluid_pipe/window")), "glass_fluid_pipe", state);
			attachments = new PipeAttachments(this);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(window, "glass_fluid_pipe", state);
			dirty = true;
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!dirty && time % 5 != 0)
				return;
			dirty = false;
			bracket.update(be);
			attachments.update(level, pos, state, BlockEntityBehaviour.get(be, FluidTransportBehaviour.TYPE));
		}
	}
}
