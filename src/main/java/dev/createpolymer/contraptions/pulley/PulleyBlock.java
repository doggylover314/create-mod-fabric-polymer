package dev.createpolymer.contraptions.pulley;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.contraptions.render.ContraptionDisplay;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.registry.ModSounds;
import dev.createpolymer.kinetics.base.HorizontalAxisKineticBlock;
import dev.createpolymer.registry.feature.PulleyRegistry;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.BlockstateModels;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PulleyBlock extends HorizontalAxisKineticBlock
	implements IBE<PulleyBlockEntity>, IWrenchable, ContraptionDisplay {

	public PulleyBlock(Properties properties) {
		super(properties);
	}

	private static void onRopeBroken(Level world, BlockPos pulleyPos) {
		BlockEntity be = world.getBlockEntity(pulleyPos);
		if (be instanceof PulleyBlockEntity pulley) {
			pulley.initialOffset = 0;
			pulley.onLengthBroken();
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		BlockState below = level.getBlockState(pos.below());
		if (below.getBlock() instanceof RopeBlockBase)
			level.destroyBlock(pos.below(), true);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (!player.mayBuild())
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (player.isShiftKeyDown())
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (stack.isEmpty()) {
			withBlockEntityDo(level, pos, be -> be.assembleNextTick = true);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	/** Clicking the top or bottom turns the pulley around the vertical axis. */
	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		if (context.getClickedFace()
			.getAxis() != Axis.Y)
			return InteractionResult.SUCCESS;
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState rotated = state.rotate(Rotation.CLOCKWISE_90);
		if (!rotated.canSurvive(level, pos))
			return InteractionResult.SUCCESS;
		KineticBlockEntity.switchToBlockState(level, pos, Block.updateFromNeighbourShapes(rotated, level, pos));
		ModSounds.wrenchRotate(level, pos);
		return InteractionResult.SUCCESS;
	}

	@Override
	public Class<PulleyBlockEntity> getBlockEntityClass() {
		return PulleyBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends PulleyBlockEntity> getBlockEntityType() {
		return PulleyRegistry.ROPE_PULLEY_BLOCK_ENTITY;
	}

	// ---- Polymer ----

	@Override
	protected BlockState particleState() {
		return Blocks.OAK_PLANKS.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new PulleyModel(state, PulleyModel.ROPE);
	}

	@Override
	public List<Piece> getContraptionPieces(BlockState state) {
		Axis shaftAxis = state.getValue(HORIZONTAL_AXIS);
		List<Piece> pieces = new java.util.ArrayList<>();
		BlockstateModels.Variant body = BlockstateModels.get("rope_pulley", state);
		if (body != null)
			pieces.add(new Piece(body.stack(), body.rotation()));
		pieces.add(new Piece(ModelItems.block("shaft"), Orientation.forAxis(shaftAxis)));
		pieces.add(new Piece(ModelItems.block("rope_pulley/rope_coil"),
			Orientation.horizontalAngle(Direction.get(Direction.AxisDirection.POSITIVE, shaftAxis))));
		return pieces;
	}

	private static abstract class RopeBlockBase extends Block
		implements SimpleWaterloggedBlock, PolymerModelBlock, ContraptionDisplay {

		public RopeBlockBase(Properties properties) {
			super(properties);
			registerDefaultState(super.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false));
		}

		@Override
		protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
			return false;
		}

		@Override
		protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
			return new ItemStack(PulleyRegistry.ROPE_PULLEY_ITEM);
		}

		@Override
		protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
			boolean movedByPiston) {
			super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
			if (movedByPiston)
				return;
			onRopeBroken(level, pos.above());
			BlockState above = level.getBlockState(pos.above());
			BlockState below = level.getBlockState(pos.below());
			if (above.getBlock() instanceof RopeBlockBase)
				level.destroyBlock(pos.above(), true);
			if (below.getBlock() instanceof RopeBlockBase)
				level.destroyBlock(pos.below(), true);
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
		public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
			FluidState fluidState = context.getLevel()
				.getFluidState(context.getClickedPos());
			return super.getStateForPlacement(context)
				.setValue(BlockStateProperties.WATERLOGGED, fluidState.getType() == Fluids.WATER);
		}

		@Override
		protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
			CollisionContext context) {
			return getShape(state, level, pos, context);
		}

		@Override
		public BlockState particleState() {
			return Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.BROWN).defaultBlockState();
		}

		protected abstract String modelPath();

		@Override
		public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
			return new StaticModel(state, modelPath());
		}

		@Override
		public List<Piece> getContraptionPieces(BlockState state) {
			return List.of(new Piece(ModelItems.block(modelPath())));
		}
	}

	/** A model that never changes. */
	private static class StaticModel extends BaseModel {
		StaticModel(BlockState state, String model) {
			super(state);
			addPart(ModelItems.block(model)).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
		}
	}

	/** Create's AllShapes.PULLEY_MAGNET */
	private static final VoxelShape MAGNET_SHAPE = Shapes.or(
		Shapes.box(3 / 16d, -3 / 16d, 3 / 16d, 13 / 16d, 2 / 16d, 13 / 16d),
		Shapes.box(6 / 16d, 0, 6 / 16d, 10 / 16d, 1, 10 / 16d));
	private static final VoxelShape ROPE_SHAPE = Shapes.box(6 / 16d, 0, 6 / 16d, 10 / 16d, 1, 10 / 16d);

	public static class MagnetBlock extends RopeBlockBase {

		public MagnetBlock(Properties properties) {
			super(properties);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
			return MAGNET_SHAPE;
		}

		@Override
		protected String modelPath() {
			return "rope_pulley/pulley_magnet";
		}
	}

	public static class RopeBlock extends RopeBlockBase {

		public RopeBlock(Properties properties) {
			super(properties);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
			return ROPE_SHAPE;
		}

		@Override
		protected String modelPath() {
			return "rope_pulley/rope";
		}
	}

}
