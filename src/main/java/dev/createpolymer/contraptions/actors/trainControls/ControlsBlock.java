package dev.createpolymer.contraptions.actors.trainControls;

import dev.createpolymer.contraptions.ContraptionWorld;
import dev.createpolymer.foundation.block.ProperWaterloggedBlock;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.util.VoxelShaper;
import dev.createpolymer.visual.PolymerModelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The base of Create's controls blocks (MIT, The Create Team). Only the contraption controls use it here: the train
 * controls and everything about steering trains are not ported.
 */
public abstract class ControlsBlock extends HorizontalDirectionalBlock implements IWrenchable, ProperWaterloggedBlock, PolymerModelBlock {

	public static final BooleanProperty OPEN = BooleanProperty.create("open");
	public static final BooleanProperty VIRTUAL = BooleanProperty.create("virtual");

	/** Create's AllShapes.CONTROLS and CONTROLS_COLLISION */
	public static final VoxelShaper CONTROLS = VoxelShaper.forHorizontal(
		Shapes.or(Shapes.box(0, 0, 6 / 16d, 1, 1, 1), Shapes.box(0, 0, 4 / 16d, 1, 2 / 16d, 1)), Direction.NORTH);
	public static final VoxelShaper CONTROLS_COLLISION =
		VoxelShaper.forHorizontal(Shapes.box(0, 0, 6 / 16d, 1, 1, 1), Direction.NORTH);

	public ControlsBlock(Properties p_54120_) {
		super(p_54120_);
		registerDefaultState(defaultBlockState().setValue(OPEN, false)
			.setValue(WATERLOGGED, false)
			.setValue(VIRTUAL, false));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
		super.createBlockStateDefinition(pBuilder.add(FACING, OPEN, WATERLOGGED, VIRTUAL));
	}

	@Override
	protected BlockState updateShape(BlockState pState, LevelReader level, ScheduledTickAccess ticks, BlockPos pCurrentPos,
		Direction pDirection, BlockPos pNeighborPos, BlockState pNeighborState, RandomSource random) {
		updateWater(level, ticks, pState, pCurrentPos);
		return pState.setValue(OPEN, level instanceof ContraptionWorld);
	}

	@Override
	protected FluidState getFluidState(BlockState pState) {
		return fluidState(pState);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext pContext) {
		BlockState state = withWater(super.getStateForPlacement(pContext), pContext);
		Direction horizontalDirection = pContext.getHorizontalDirection();
		Player player = pContext.getPlayer();

		state = state.setValue(FACING, horizontalDirection.getOpposite());
		if (player != null && player.isShiftKeyDown())
			state = state.setValue(FACING, horizontalDirection);

		return state;
	}

	@Override
	protected VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		return CONTROLS.get(pState.getValue(FACING));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos,
		CollisionContext pContext) {
		return CONTROLS_COLLISION.get(pState.getValue(FACING));
	}

	@Override
	public BlockState particleState() {
		return Blocks.ANDESITE.defaultBlockState();
	}

}
