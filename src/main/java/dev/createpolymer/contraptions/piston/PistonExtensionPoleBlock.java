package dev.createpolymer.contraptions.piston;

import dev.createpolymer.contraptions.piston.MechanicalPistonBlock.PistonState;
import dev.createpolymer.contraptions.render.ContraptionDisplay;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import static dev.createpolymer.contraptions.piston.MechanicalPistonBlock.isExtensionPole;
import static dev.createpolymer.contraptions.piston.MechanicalPistonBlock.isPistonHead;
import static dev.createpolymer.contraptions.piston.MechanicalPistonBlock.isPiston;

public class PistonExtensionPoleBlock extends WrenchableDirectionalBlock implements SimpleWaterloggedBlock, PolymerModelBlock, ContraptionDisplay {

	public PistonExtensionPoleBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP).setValue(BlockStateProperties.WATERLOGGED, false));
	}

	@Override
	public BlockState playerWillDestroy(Level worldIn, BlockPos pos, BlockState state, Player player) {
		Axis axis = state.getValue(FACING)
			.getAxis();
		Direction direction = Direction.get(AxisDirection.POSITIVE, axis);
		BlockPos pistonHead = null;
		BlockPos pistonBase = null;

		for (int modifier : new int[]{1, -1}) {
			for (int offset = modifier; modifier * offset < MechanicalPistonBlock.maxAllowedPistonPoles(); offset +=
				modifier) {
				BlockPos currentPos = pos.relative(direction, offset);
				BlockState block = worldIn.getBlockState(currentPos);

				if (isExtensionPole(block) && axis == block.getValue(FACING)
					.getAxis())
					continue;

				if (isPiston(block) && block.getValue(BlockStateProperties.FACING)
					.getAxis() == axis)
					pistonBase = currentPos;

				if (isPistonHead(block) && block.getValue(BlockStateProperties.FACING)
					.getAxis() == axis)
					pistonHead = currentPos;

				break;
			}
		}

		if (pistonHead != null && pistonBase != null && worldIn.getBlockState(pistonHead)
			.getValue(BlockStateProperties.FACING) == worldIn.getBlockState(pistonBase)
			.getValue(BlockStateProperties.FACING)) {

			final BlockPos basePos = pistonBase;
			boolean dropBlocks = player == null || !player.isCreative();
			BlockPos.betweenClosedStream(pistonBase, pistonHead)
				.filter(p -> !p.equals(pos) && !p.equals(basePos))
				.forEach(p -> worldIn.destroyBlock(p, dropBlocks));
			worldIn.setBlockAndUpdate(basePos, worldIn.getBlockState(basePos)
				.setValue(MechanicalPistonBlock.STATE, PistonState.RETRACTED));

			BlockEntity be = worldIn.getBlockEntity(basePos);
			if (be instanceof MechanicalPistonBlockEntity baseBE) {
				baseBE.offset = 0;
				baseBE.onLengthBroken();
			}
		}

		return super.playerWillDestroy(worldIn, pos, state, player);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		return MechanicalPistonBlock.FOUR_VOXEL_POLE.get(state.getValue(FACING).getAxis());
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return getShape(state, level, pos, context);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		FluidState fluidState = context.getLevel()
			.getFluidState(context.getClickedPos());
		return defaultBlockState().setValue(FACING, context.getClickedFace()
				.getOpposite())
			.setValue(BlockStateProperties.WATERLOGGED, fluidState.getType() == Fluids.WATER);
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
		return state;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	/** Create's PlacementHelper.matchesAxis: a pole pointing along {@code axis}. */
	public static boolean matchesAxis(BlockState state, Axis axis) {
		return isExtensionPole(state) && state.getValue(FACING).getAxis() == axis;
	}

	@Override
	public BlockState particleState() {
		return Blocks.OAK_PLANKS.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	@Override
	public java.util.List<Piece> getContraptionPieces(BlockState state) {
		return java.util.List.of(new Piece(ModelItems.block("piston_extension_pole"),
			Orientation.forFacing(state.getValue(FACING))));
	}

	public static class Model extends BaseModel {
		public Model(BlockState state) {
			super(state);
			addPart(ModelItems.block("piston_extension_pole"))
				.orientation(Orientation.forFacing(state.getValue(FACING))).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, BlockEntity be, long time) {
		}
	}
}
