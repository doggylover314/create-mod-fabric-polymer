package dev.createpolymer.logistics.chute;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.foundation.block.ProperWaterloggedBlock;
import dev.createpolymer.logistics.funnel.FunnelBlock;
import dev.createpolymer.registry.feature.ChuteRegistry;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.Lang;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
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
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;

public class ChuteBlock extends AbstractChuteBlock implements ProperWaterloggedBlock {

	public static final Property<Shape> SHAPE = EnumProperty.create("shape", Shape.class);
	public static final Property<Direction> FACING = BlockStateProperties.FACING_HOPPER;

	/** Create's AllBlocks.INDUSTRIAL_IRON_BLOCK; not ported yet, so it is looked up by id. */
	private static final Identifier INDUSTRIAL_IRON_BLOCK = CreatePolymer.id("industrial_iron_block");

	public ChuteBlock(Properties p_i48440_1_) {
		super(p_i48440_1_);
		registerDefaultState(defaultBlockState().setValue(SHAPE, Shape.NORMAL)
			.setValue(FACING, Direction.DOWN)
			.setValue(WATERLOGGED, false));
	}

	public enum Shape implements StringRepresentable {
		INTERSECTION, WINDOW, NORMAL, ENCASED;

		@Override
		public String getSerializedName() {
			return Lang.asId(name());
		}
	}

	@Override
	public Direction getFacing(BlockState state) {
		return state.getValue(FACING);
	}

	@Override
	public boolean isOpen(BlockState state) {
		return state.getValue(FACING) == Direction.DOWN || state.getValue(SHAPE) == Shape.INTERSECTION;
	}

	@Override
	public boolean isTransparent(BlockState state) {
		return state.getValue(SHAPE) == Shape.WINDOW;
	}

	@Override
	protected FluidState getFluidState(BlockState pState) {
		return fluidState(pState);
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		Shape shape = state.getValue(SHAPE);
		boolean down = state.getValue(FACING) == Direction.DOWN;
		if (shape == Shape.INTERSECTION)
			return InteractionResult.PASS;
		Level level = context.getLevel();
		if (level.isClientSide())
			return InteractionResult.SUCCESS;
		if (shape == Shape.ENCASED) {
			level.setBlockAndUpdate(context.getClickedPos(), state.setValue(SHAPE, Shape.NORMAL));
			level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, context.getClickedPos(),
				Block.getId(BuiltInRegistries.BLOCK.getOptional(INDUSTRIAL_IRON_BLOCK)
					.orElse(Blocks.IRON_BLOCK)
					.defaultBlockState()));
			return InteractionResult.SUCCESS;
		}
		if (down)
			level.setBlockAndUpdate(context.getClickedPos(),
				state.setValue(SHAPE, shape != Shape.NORMAL ? Shape.NORMAL : Shape.WINDOW));
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		Shape shape = state.getValue(SHAPE);
		if (!isIndustrialIron(stack))
			return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
		if (shape == Shape.INTERSECTION || shape == Shape.ENCASED)
			return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
		if (player == null || level.isClientSide())
			return InteractionResult.SUCCESS;

		level.setBlockAndUpdate(pos, state.setValue(SHAPE, Shape.ENCASED));
		level.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_HIT, SoundSource.BLOCKS, 0.5f, 1.05f);
		return InteractionResult.SUCCESS;
	}

	private static boolean isIndustrialIron(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem())
			.equals(INDUSTRIAL_IRON_BLOCK);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		BlockState state = withWater(super.getStateForPlacement(ctx), ctx);
		Direction face = ctx.getClickedFace();
		if (face.getAxis()
			.isHorizontal() && !ctx.isSecondaryUseActive()) {
			Level world = ctx.getLevel();
			BlockPos pos = ctx.getClickedPos();
			return updateChuteState(state.setValue(FACING, face), world.getBlockState(pos.above()), world, pos);
		}
		return state;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
		Direction direction, BlockPos neighbourPos, BlockState above, RandomSource random) {
		updateWater(level, ticks, state, pos);
		return super.updateShape(state, level, ticks, pos, direction, neighbourPos, above, random);
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> p_206840_1_) {
		super.createBlockStateDefinition(p_206840_1_.add(SHAPE, FACING, WATERLOGGED));
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
		BlockState above = world.getBlockState(pos.above());
		return !isChute(above) || getChuteFacing(above) == Direction.DOWN;
	}

	@Override
	public BlockState updateChuteState(BlockState state, BlockState above, BlockGetter world, BlockPos pos) {
		if (!(state.getBlock() instanceof ChuteBlock))
			return state;

		Map<Direction, Boolean> connections = new HashMap<>();
		int amtConnections = 0;
		Direction facing = state.getValue(FACING);
		boolean vertical = facing == Direction.DOWN;

		if (!vertical) {
			BlockState target = world.getBlockState(pos.below()
				.relative(facing.getOpposite()));
			if (!isChute(target))
				return state.setValue(FACING, Direction.DOWN)
					.setValue(SHAPE, Shape.NORMAL);
		}

		for (Direction direction : Iterate.horizontalDirections) {
			BlockState diagonalInputChute = world.getBlockState(pos.above()
				.relative(direction));
			boolean value =
				diagonalInputChute.getBlock() instanceof ChuteBlock && diagonalInputChute.getValue(FACING) == direction;
			connections.put(direction, value);
			if (value)
				amtConnections++;
		}

		boolean noConnections = amtConnections == 0;
		if (vertical)
			return state.setValue(SHAPE,
				noConnections ? state.getValue(SHAPE) == Shape.INTERSECTION ? Shape.NORMAL : state.getValue(SHAPE)
					: Shape.INTERSECTION);
		if (noConnections)
			return state.setValue(SHAPE, Shape.INTERSECTION);
		if (connections.get(Direction.NORTH) && connections.get(Direction.SOUTH))
			return state.setValue(SHAPE, Shape.INTERSECTION);
		if (connections.get(Direction.EAST) && connections.get(Direction.WEST))
			return state.setValue(SHAPE, Shape.INTERSECTION);
		if (amtConnections == 1 && connections.get(facing)
			&& !(above.getBlock() instanceof FunnelBlock && FunnelBlock.getFunnelFacing(above) == Direction.DOWN))
			return state.setValue(SHAPE, state.getValue(SHAPE) == Shape.ENCASED ? Shape.ENCASED : Shape.NORMAL);
		return state.setValue(SHAPE, Shape.INTERSECTION);
	}

	@Override
	protected BlockState rotate(BlockState pState, Rotation pRot) {
		return pState.setValue(FACING, pRot.rotate(pState.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState pState, Mirror pMirror) {
		return pState.rotate(pMirror.getRotation(pState.getValue(FACING)));
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	public BlockEntityType<? extends ChuteBlockEntity> getBlockEntityType() {
		return ChuteRegistry.CHUTE_BLOCK_ENTITY;
	}

	// ---- Polymer ----

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/** Create's chute models for the block state, plus the item inside (see {@link ChuteRenderer}). */
	public static class Model extends BaseModel {
		private final Part block;
		private final ChuteRenderer item;

		public Model(BlockState state) {
			super(state);
			block = addPart(ModelItems.block("chute/block"));
			showVariant(block, "chute", state);
			item = new ChuteRenderer(this);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(block, "chute", state);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof ChuteBlockEntity chute))
				return;
			item.render(chute, ChuteRenderer.isVisibleInChute(chute, state));
		}
	}

}
