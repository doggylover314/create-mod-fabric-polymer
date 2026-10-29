package dev.createpolymer.contraptions.actors;

import java.util.List;

import dev.createpolymer.contraptions.render.ContraptionDisplay;
import dev.createpolymer.foundation.block.BlockHelper;
import dev.createpolymer.foundation.block.ProperWaterloggedBlock;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.util.VoxelShaper;
import dev.createpolymer.visual.PolymerModelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ported from Create's AttachedActorBlock (MIT, The Create Team): the base of the harvester, plough and roller. They
 * hang on the side of another block and cannot be turned with a wrench.
 */
public abstract class AttachedActorBlock extends HorizontalDirectionalBlock
	implements IWrenchable, ProperWaterloggedBlock, PolymerModelBlock, ContraptionDisplay {

	/** Create's AllShapes.HARVESTER_BASE */
	public static final VoxelShaper HARVESTER_BASE = VoxelShaper
		.forDirectional(Shapes.box(0, 2 / 16d, 0, 1, 14 / 16d, 3 / 16d), Direction.SOUTH);

	protected AttachedActorBlock(Properties p_i48377_1_) {
		super(p_i48377_1_);
		registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false));
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		return InteractionResult.FAIL;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		Direction direction = state.getValue(FACING);
		return HARVESTER_BASE.get(direction);
	}

	/** Polymer would otherwise answer with the collision of the barrier players see. */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return getShape(state, level, pos, context);
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(FACING, WATERLOGGED);
		super.createBlockStateDefinition(builder);
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader worldIn, BlockPos pos) {
		Direction direction = state.getValue(FACING);
		BlockPos offset = pos.relative(direction.getOpposite());
		return BlockHelper.hasBlockSolidSide(worldIn.getBlockState(offset), worldIn, offset, direction);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction facing;
		if (context.getClickedFace()
			.getAxis()
			.isVertical())
			facing = context.getHorizontalDirection()
				.getOpposite();
		else {
			BlockState blockState = context.getLevel()
				.getBlockState(context.getClickedPos()
					.relative(context.getClickedFace()
						.getOpposite()));
			if (blockState.getBlock() instanceof AttachedActorBlock)
				facing = blockState.getValue(FACING);
			else
				facing = context.getClickedFace();
		}
		return withWater(defaultBlockState().setValue(FACING, facing), context);
	}

	@Override
	protected BlockState updateShape(BlockState pState, LevelReader level, ScheduledTickAccess ticks, BlockPos pCurrentPos,
		Direction pDirection, BlockPos pNeighborPos, BlockState pNeighborState, RandomSource random) {
		updateWater(level, ticks, pState, pCurrentPos);
		return pState;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	protected FluidState getFluidState(BlockState pState) {
		return fluidState(pState);
	}

	@Override
	public BlockState particleState() {
		return Blocks.ANDESITE.defaultBlockState();
	}

	@Override
	public List<Piece> getContraptionPieces(BlockState state) {
		return List.of();
	}

}
