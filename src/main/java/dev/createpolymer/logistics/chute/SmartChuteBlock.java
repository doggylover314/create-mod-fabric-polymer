package dev.createpolymer.logistics.chute;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilterDisplay;
import dev.createpolymer.registry.feature.ChuteRegistry;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;

public class SmartChuteBlock extends AbstractChuteBlock {

	public SmartChuteBlock(Properties p_i48440_1_) {
		super(p_i48440_1_);
		registerDefaultState(defaultBlockState().setValue(POWERED, true));
	}

	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
		@Nullable Orientation orientation, boolean isMoving) {
		super.neighborChanged(state, level, pos, block, orientation, isMoving);
		if (level.isClientSide())
			return;
		if (!level.getBlockTicks()
			.willTickThisTick(pos, this))
			level.scheduleTick(pos, this, 1);
	}

	@Override
	protected void tick(BlockState state, ServerLevel worldIn, BlockPos pos, RandomSource r) {
		boolean previouslyPowered = state.getValue(POWERED);
		if (previouslyPowered != worldIn.hasNeighborSignal(pos))
			worldIn.setBlock(pos, state.cycle(POWERED), Block.UPDATE_CLIENTS);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext p_196258_1_) {
		return super.getStateForPlacement(p_196258_1_).setValue(POWERED, p_196258_1_.getLevel()
			.hasNeighborSignal(p_196258_1_.getClickedPos()));
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
		return true;
	}

	@Override
	public BlockEntityType<? extends ChuteBlockEntity> getBlockEntityType() {
		return ChuteRegistry.SMART_CHUTE_BLOCK_ENTITY;
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> p_206840_1_) {
		super.createBlockStateDefinition(p_206840_1_.add(POWERED));
	}

	@Override
	public BlockState updateChuteState(BlockState state, BlockState above, BlockGetter world, BlockPos pos) {
		return state;
	}

	// ---- Polymer ----

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/** Create's smart chute model (powered or not), the filter on its sides and the item waiting at the bottom. */
	public static class Model extends BaseModel {
		private final Part block;
		private final FilterDisplay filter = new FilterDisplay(this);
		private final ChuteRenderer item;

		public Model(BlockState state) {
			super(state);
			block = addPart(ModelItems.block("smart_chute/block"));
			showVariant(block, "smart_chute", state);
			item = new ChuteRenderer(this);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(block, "smart_chute", state);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof SmartChuteBlockEntity chute))
				return;
			filter.update(chute);
			item.render(chute, ChuteRenderer.isVisibleInSmartChute(chute));
		}
	}

}
