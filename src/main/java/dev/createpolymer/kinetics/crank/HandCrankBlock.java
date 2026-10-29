package dev.createpolymer.kinetics.crank;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.Config;
import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.simple.AbstractShaftBlock;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.util.ShapeUtil;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Ported from Create's HandCrankBlock (MIT, The Create Team). */
public class HandCrankBlock extends DirectionalKineticBlock implements SimpleWaterloggedBlock {
	public static final Map<Direction, VoxelShape> SHAPE = ShapeUtil.forDirectional(
		Shapes.or(ShapeUtil.box(5, 0, 5, 11, 6, 11), ShapeUtil.box(1, 3, 1, 15, 8, 15)));

	public HandCrankBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false));
	}

	@Override
	public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
		return ModBlockEntities.HAND_CRANK;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		return SHAPE.get(state.getValue(FACING));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(BlockStateProperties.WATERLOGGED));
	}

	public int getRotationSpeed() {
		return 32;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		return crank(level, pos, player);
	}

	@Override
	protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos,
										  Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
		if (stack.getItem() instanceof net.minecraft.world.item.BlockItem)
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		return crank(level, pos, player);
	}

	private InteractionResult crank(Level level, BlockPos pos, Player player) {
		if (player.isSpectator())
			return InteractionResult.PASS;
		if (level.getBlockEntity(pos) instanceof HandCrankBlockEntity be)
			be.turn(player.isShiftKeyDown());
		player.causeFoodExhaustion(getRotationSpeed() * Config.crankHungerMultiplier);
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction preferred = getPreferredFacing(context);
		BlockState defaultBlockState = AbstractShaftBlock.withWater(defaultBlockState(), context);
		if (preferred == null || (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()))
			return defaultBlockState.setValue(FACING, context.getClickedFace());
		return defaultBlockState.setValue(FACING, preferred.getOpposite());
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader worldIn, BlockPos pos) {
		Direction facing = state.getValue(FACING).getOpposite();
		BlockPos neighbourPos = pos.relative(facing);
		BlockState neighbour = worldIn.getBlockState(neighbourPos);
		return !neighbour.getCollisionShape(worldIn, neighbourPos).isEmpty();
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
									 Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (state.getValue(BlockStateProperties.WATERLOGGED))
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		if (directionToNeighbour == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos))
			return state.getValue(BlockStateProperties.WATERLOGGED) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
		return state;
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face == state.getValue(FACING).getOpposite();
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return state.getValue(FACING).getAxis();
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	protected BlockState particleState() {
		return Blocks.SPRUCE_PLANKS.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	public static class Model extends BaseModel {
		private final Part base;
		private final Part handle;

		public Model(BlockState state) {
			super(state);
			base = addPart(ModelItems.block("hand_crank/block"));
			handle = addPart(ModelItems.block("hand_crank/handle"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			Direction facing = state.getValue(FACING);
			for (Part p : new Part[] { base, handle })
				p.orientation(Orientation.forFacing(facing)).spinAxis(facing.getAxis()).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof KineticBlockEntity kbe))
				return;
			Axis axis = state.getValue(FACING).getAxis();
			float offset = Orientation.cogOffset(false, axis, pos.getX(), pos.getY(), pos.getZ());
			base.spin(time, kbe.getSpeed(), offset);
			handle.spin(time, kbe.getSpeed(), offset);
		}
	}
}
