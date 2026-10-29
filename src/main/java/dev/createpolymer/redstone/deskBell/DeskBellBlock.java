package dev.createpolymer.redstone.deskBell;

import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.logistics.chute.ProperWaterloggedBlock;
import dev.createpolymer.redstone.RedstoneClientStates;
import dev.createpolymer.redstone.WrenchableDirectionalBlock;
import dev.createpolymer.registry.feature.RedstoneRegistry;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.BlockstateModels;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DeskBellBlock extends WrenchableDirectionalBlock
	implements ProperWaterloggedBlock, IBE<DeskBellBlockEntity>, PolymerModelBlock {

	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

	/** Create's AllShapes.DESK_BELL */
	private static final Map<Direction, VoxelShape> SHAPES = ShapeUtil.forDirectional(
		Shapes.or(ShapeUtil.box(3, 0, 3, 13, 3, 13), ShapeUtil.box(4, 0, 4, 12, 9, 12)));

	public DeskBellBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP)
			.setValue(POWERED, false)
			.setValue(WATERLOGGED, false));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return withWater(defaultBlockState().setValue(FACING, context.getClickedFace()), context);
	}

	@Override
	protected BlockState updateShape(BlockState pState, LevelReader level, ScheduledTickAccess ticks, BlockPos pPos,
		Direction pDirection, BlockPos pNeighborPos, BlockState pNeighborState, RandomSource random) {
		updateWater(level, ticks, pState, pPos);
		return pState;
	}

	@Override
	protected VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		return SHAPES.get(pState.getValue(FACING));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		return SHAPES.get(pState.getValue(FACING));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(POWERED, WATERLOGGED));
	}

	@Override
	protected FluidState getFluidState(BlockState pState) {
		return fluidState(pState);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level.isClientSide())
			return InteractionResult.SUCCESS;
		playSound(level, pos);
		level.setBlock(pos, state.setValue(POWERED, true), Block.UPDATE_ALL);
		updateNeighbours(state, level, pos);
		withBlockEntityDo(level, pos, DeskBellBlockEntity::ding);
		return InteractionResult.SUCCESS;
	}

	/** Create's desk bell sound is a custom recording; a high vanilla bell is its stand-in. */
	public void playSound(LevelAccessor level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, .75f, 1.9f);
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState pState, ServerLevel pLevel, BlockPos pPos, boolean pIsMoving) {
		if (!pIsMoving && pState.getValue(POWERED))
			updateNeighbours(pState, pLevel, pPos);
	}

	@Override
	protected int getSignal(BlockState pBlockState, BlockGetter pBlockAccess, BlockPos pPos, Direction pSide) {
		return pBlockState.getValue(POWERED) ? 15 : 0;
	}

	@Override
	protected int ownSignal(BlockState state, BlockGetter level, BlockPos pos) {
		return state.getValue(POWERED) ? 15 : 0;
	}

	@Override
	protected int getDirectSignal(BlockState pBlockState, BlockGetter pBlockAccess, BlockPos pPos, Direction pSide) {
		return pBlockState.getValue(POWERED) && getConnectedDirection(pBlockState) == pSide ? 15 : 0;
	}

	@Override
	protected boolean isSignalSource(BlockState pState) {
		return true;
	}

	public void unPress(BlockState pState, Level pLevel, BlockPos pPos) {
		pLevel.setBlock(pPos, pState.setValue(POWERED, false), Block.UPDATE_ALL);
		updateNeighbours(pState, pLevel, pPos);
	}

	protected void updateNeighbours(BlockState pState, Level pLevel, BlockPos pPos) {
		pLevel.updateNeighborsAt(pPos, this);
		pLevel.updateNeighborsAt(pPos.relative(getConnectedDirection(pState).getOpposite()), this);
	}

	private Direction getConnectedDirection(BlockState pState) {
		return pState.getOptionalValue(FACING)
			.orElse(Direction.UP);
	}

	@Override
	public Class<DeskBellBlockEntity> getBlockEntityClass() {
		return DeskBellBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends DeskBellBlockEntity> getBlockEntityType() {
		return RedstoneRegistry.DESK_BELL_BLOCK_ENTITY;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	// ---- Polymer ----

	/** An invisible half block on the floor or ceiling, a flat plate on a wall. */
	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		boolean wet = state.getValue(WATERLOGGED);
		Direction facing = state.getValue(FACING);
		return switch (facing) {
			case UP -> RedstoneClientStates.slab(false, wet);
			case DOWN -> RedstoneClientStates.slab(true, wet);
			default -> RedstoneClientStates.plate(facing.getOpposite(), wet);
		};
	}

	@Override
	public BlockState particleState() {
		return Blocks.GOLD_BLOCK.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/**
	 * Create's desk bell, drawn like its renderer does: while the bell rings the plunger is pressed in and the
	 * bell wobbles. (The "powered" model has neither, they are the two parts that move.)
	 */
	public static class Model extends BaseModel {
		private static final int ANIMATION_TICKS = 20;

		private final Part block;
		private final Part plunger;
		private final Part bell;
		private int seenDings = -1;
		private long dingStart = Long.MIN_VALUE;
		private float animationOffset;
		private boolean animating;

		public Model(BlockState state) {
			super(state);
			block = addPart(ModelItems.block("desk_bell/block"));
			plunger = addPart(ItemStack.EMPTY);
			bell = addPart(ItemStack.EMPTY);
			showVariant(block, "desk_bell", state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(block, "desk_bell", state);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (be instanceof DeskBellBlockEntity bellBe) {
				if (seenDings < 0)
					seenDings = bellBe.dings;
				if (bellBe.dings != seenDings) {
					seenDings = bellBe.dings;
					dingStart = time;
					animationOffset = level.getRandom().nextFloat() * 2 * (float) Math.PI;
				}
			}

			float p = dingStart == Long.MIN_VALUE ? 0 : Math.max(0, 1 - (time - dingStart) / (float) ANIMATION_TICKS);
			boolean powered = state.getValue(POWERED);
			if (p < 0.004 && !powered) {
				if (animating) {
					animating = false;
					plunger.setItem(ItemStack.EMPTY);
					bell.setItem(ItemStack.EMPTY);
				}
				return;
			}

			BlockstateModels.Variant v = BlockstateModels.get("desk_bell", state);
			if (v == null)
				return;
			if (!animating) {
				animating = true;
				plunger.setItem(ModelItems.block("desk_bell/plunger"));
				bell.setItem(ModelItems.block("desk_bell/bell"));
			}

			float f = (float) (1 - 4 * Math.pow((Math.max(p - 0.5, 0)) - 0.5, 2));
			float f2 = (float) Math.pow(p, 1.25f);

			plunger.setMatrix(new Matrix4f()
				.rotate(v.rotation())
				.translate(0, f * -.75f / 16f, 0), 1);

			bell.setMatrix(new Matrix4f()
				.rotate(v.rotation())
				.translate(0, -1 / 16f, 0)
				.rotateX((float) Math.toRadians(f2 * 8 * Math.sin(p * Math.PI * 4 + animationOffset)))
				.rotateZ((float) Math.toRadians(f2 * 8 * Math.cos(p * Math.PI * 4 + animationOffset)))
				.translate(0, 1 / 16f, 0)
				.scale(0.995f), 1);
		}
	}

}
