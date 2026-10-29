package dev.createpolymer.contraptions.piston;

import static dev.createpolymer.contraptions.piston.MechanicalPistonBlock.isExtensionPole;
import static dev.createpolymer.contraptions.piston.MechanicalPistonBlock.isPistonHead;

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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MechanicalPistonHeadBlock extends WrenchableDirectionalBlock implements SimpleWaterloggedBlock, PolymerModelBlock, ContraptionDisplay {

	public static final EnumProperty<PistonType> TYPE = BlockStateProperties.PISTON_TYPE;

	public MechanicalPistonHeadBlock(Properties p_i48415_1_) {
		super(p_i48415_1_);
		registerDefaultState(super.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(TYPE, BlockStateProperties.WATERLOGGED);
		super.createBlockStateDefinition(builder);
	}

	@Override
	public BlockState playerWillDestroy(Level worldIn, BlockPos pos, BlockState state, Player player) {
		Direction direction = state.getValue(FACING);
		BlockPos pistonHead = pos;
		BlockPos pistonBase = null;

		for (int offset = 1; offset < MechanicalPistonBlock.maxAllowedPistonPoles(); offset++) {
			BlockPos currentPos = pos.relative(direction.getOpposite(), offset);
			BlockState block = worldIn.getBlockState(currentPos);

			if (isExtensionPole(block) && direction.getAxis() == block.getValue(BlockStateProperties.FACING)
				.getAxis())
				continue;

			if (MechanicalPistonBlock.isPiston(block) && block.getValue(BlockStateProperties.FACING) == direction)
				pistonBase = currentPos;

			break;
		}

		if (pistonHead != null && pistonBase != null) {
			final BlockPos basePos = pistonBase;
			boolean dropBlocks = player == null || !player.isCreative();
			BlockPos.betweenClosedStream(pistonBase, pistonHead)
				.filter(p -> !p.equals(pos) && !p.equals(basePos))
				.forEach(p -> worldIn.destroyBlock(p, dropBlocks));
			worldIn.setBlockAndUpdate(basePos, worldIn.getBlockState(basePos)
				.setValue(MechanicalPistonBlock.STATE, PistonState.RETRACTED));
		}

		return super.playerWillDestroy(worldIn, pos, state, player);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		return MechanicalPistonBlock.PISTON_HEAD_SHAPE.get(state.getValue(FACING));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return getShape(state, level, pos, context);
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState();
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
		Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (state.getValue(BlockStateProperties.WATERLOGGED))
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		return state;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
		return super.getStateForPlacement(context).setValue(BlockStateProperties.WATERLOGGED, fluidState.getType() == Fluids.WATER);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
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
		return java.util.List.of(new Piece(ModelItems.block(modelPath(state).substring("block/".length())),
			Orientation.forFacing(state.getValue(FACING))));
	}

	public static String modelPath(BlockState state) {
		return "block/mechanical_piston/" + state.getValue(TYPE).getSerializedName() + "/head";
	}

	public static class Model extends BaseModel {
		private final Part head;

		public Model(BlockState state) {
			super(state);
			head = addPart(ModelItems.block(modelPath(state).substring("block/".length())));
			head.orientation(Orientation.forFacing(state.getValue(FACING))).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.entity.BlockEntity be, long time) {
		}
	}
}
