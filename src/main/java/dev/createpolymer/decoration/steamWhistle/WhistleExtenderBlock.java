package dev.createpolymer.decoration.steamWhistle;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.decoration.steamWhistle.WhistleBlock.WhistleSize;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.registry.ModSounds;
import dev.createpolymer.registry.feature.SteamRegistry;
import dev.createpolymer.util.Lang;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WhistleExtenderBlock extends Block implements IWrenchable, PolymerModelBlock {

	public static final EnumProperty<WhistleExtenderShape> SHAPE =
		EnumProperty.create("shape", WhistleExtenderShape.class);
	public static final EnumProperty<WhistleSize> SIZE = WhistleBlock.SIZE;

	// Create's AllShapes.WHISTLE_EXTENDER_*
	private static final VoxelShape SMALL = ShapeUtil.box(4, 0, 4, 12, 10, 12);
	private static final VoxelShape MEDIUM = ShapeUtil.box(3, 0, 3, 13, 10, 13);
	private static final VoxelShape LARGE = ShapeUtil.box(2, 0, 2, 14, 10, 14);
	private static final VoxelShape SMALL_DOUBLE = ShapeUtil.box(4, 0, 4, 12, 18, 12);
	private static final VoxelShape MEDIUM_DOUBLE = ShapeUtil.box(3, 0, 3, 13, 18, 13);
	private static final VoxelShape LARGE_DOUBLE = ShapeUtil.box(2, 0, 2, 14, 18, 14);
	private static final VoxelShape SMALL_DOUBLE_CONNECTED = ShapeUtil.box(4, 0, 4, 12, 16, 12);
	private static final VoxelShape MEDIUM_DOUBLE_CONNECTED = ShapeUtil.box(3, 0, 3, 13, 16, 13);
	private static final VoxelShape LARGE_DOUBLE_CONNECTED = ShapeUtil.box(2, 0, 2, 14, 16, 14);

	public static enum WhistleExtenderShape implements StringRepresentable {
		SINGLE, DOUBLE, DOUBLE_CONNECTED;

		@Override
		public String getSerializedName() {
			return Lang.asId(name());
		}
	}

	public WhistleExtenderBlock(Properties p_49795_) {
		super(p_49795_);
		registerDefaultState(defaultBlockState().setValue(SHAPE, WhistleExtenderShape.SINGLE)
			.setValue(SIZE, WhistleSize.MEDIUM));
	}

	@Override
	public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
		Level world = context.getLevel();
		BlockPos pos = context.getClickedPos();

		if (context.getClickLocation().y < context.getClickedPos()
			.getY() + .5f || state.getValue(SHAPE) == WhistleExtenderShape.SINGLE)
			return null;
		if (world.isClientSide())
			return InteractionResult.SUCCESS;
		world.setBlock(pos, state.setValue(SHAPE, WhistleExtenderShape.SINGLE), Block.UPDATE_ALL);
		ModSounds.wrenchRemove(world, pos);
		return InteractionResult.SUCCESS;
	}

	protected UseOnContext relocateContext(UseOnContext context, BlockPos target) {
		return new UseOnContext(context.getPlayer(), context.getHand(),
			new BlockHitResult(context.getClickLocation(), context.getClickedFace(), target, context.isInside()));
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (player == null || !stack.is(SteamRegistry.STEAM_WHISTLE_ITEM))
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		BlockPos findRoot = findRoot(level, pos);
		BlockState blockState = level.getBlockState(findRoot);
		if (blockState.getBlock() instanceof WhistleBlock whistle)
			return whistle.useItemOn(stack, blockState, level, findRoot, player, hand,
				new BlockHitResult(hitResult.getLocation(), hitResult.getDirection(), findRoot, hitResult.isInside()));
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		Level level = context.getLevel();
		BlockPos findRoot = findRoot(level, context.getClickedPos());
		BlockState blockState = level.getBlockState(findRoot);
		if (blockState.getBlock() instanceof WhistleBlock whistle)
			return whistle.onWrenched(blockState, relocateContext(context, findRoot));
		return null;
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(SteamRegistry.STEAM_WHISTLE_ITEM);
	}

	public static BlockPos findRoot(LevelAccessor pLevel, BlockPos pPos) {
		BlockPos currentPos = pPos.below();
		while (true) {
			BlockState blockState = pLevel.getBlockState(currentPos);
			if (blockState.is(SteamRegistry.STEAM_WHISTLE_EXTENSION)) {
				currentPos = currentPos.below();
				continue;
			}
			return currentPos;
		}
	}

	@Override
	protected boolean canSurvive(BlockState pState, LevelReader pLevel, BlockPos pPos) {
		BlockState below = pLevel.getBlockState(pPos.below());
		return below.is(this) && below.getValue(SHAPE) != WhistleExtenderShape.SINGLE
			|| below.is(SteamRegistry.STEAM_WHISTLE);
	}

	@Override
	protected BlockState updateShape(BlockState pState, LevelReader pLevel, ScheduledTickAccess ticks, BlockPos pCurrentPos,
									 Direction pFacing, BlockPos facingPos, BlockState pFacingState, RandomSource random) {
		if (pFacing.getAxis() != Axis.Y)
			return pState;

		if (pFacing == Direction.UP) {
			boolean connected = pState.getValue(SHAPE) == WhistleExtenderShape.DOUBLE_CONNECTED;
			boolean shouldConnect = pFacingState.is(this);
			if (!connected && shouldConnect)
				return pState.setValue(SHAPE, WhistleExtenderShape.DOUBLE_CONNECTED);
			if (connected && !shouldConnect)
				return pState.setValue(SHAPE, WhistleExtenderShape.DOUBLE);
			return pState;
		}

		return !pState.canSurvive(pLevel, pCurrentPos) ? Blocks.AIR.defaultBlockState()
			: pState.setValue(SIZE, pFacingState.getValue(SIZE));
	}

	@Override
	protected void onPlace(BlockState pState, Level pLevel, BlockPos pPos, BlockState pOldState, boolean pIsMoving) {
		if (pOldState.getBlock() != this || pOldState.getValue(SHAPE) != pState.getValue(SHAPE))
			WhistleBlock.queuePitchUpdate(pLevel, findRoot(pLevel, pPos));
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState pState, ServerLevel pLevel, BlockPos pPos, boolean pIsMoving) {
		super.affectNeighborsAfterRemoval(pState, pLevel, pPos, pIsMoving);
		if (!pLevel.getBlockState(pPos).is(this))
			WhistleBlock.queuePitchUpdate(pLevel, findRoot(pLevel, pPos));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
		super.createBlockStateDefinition(pBuilder.add(SHAPE, SIZE));
	}

	@Override
	protected VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		WhistleSize size = pState.getValue(SIZE);
		switch (pState.getValue(SHAPE)) {
		case DOUBLE:
			return size == WhistleSize.LARGE ? LARGE_DOUBLE : size == WhistleSize.MEDIUM ? MEDIUM_DOUBLE : SMALL_DOUBLE;
		case DOUBLE_CONNECTED:
			return size == WhistleSize.LARGE ? LARGE_DOUBLE_CONNECTED
				: size == WhistleSize.MEDIUM ? MEDIUM_DOUBLE_CONNECTED : SMALL_DOUBLE_CONNECTED;
		case SINGLE:
		default:
			return size == WhistleSize.LARGE ? LARGE : size == WhistleSize.MEDIUM ? MEDIUM : SMALL;
		}
	}

	/** Polymer would otherwise answer with the collision of the barrier players see. */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return getShape(state, level, pos, context);
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

	/** Create's blockstate: single, or double with a rim on top; connected doubles have no rim. */
	public static class Model extends BaseModel {
		private final Part main;
		private final Part rim;

		public Model(BlockState state) {
			super(state);
			main = addPart(ModelItems.block("steam_whistle/extension/medium_single"));
			rim = addPart(ModelItems.block("steam_whistle/extension/medium_top_rim"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			String size = state.getValue(SIZE).getSerializedName();
			WhistleExtenderShape shape = state.getValue(SHAPE);
			main.setItem(ModelItems.block("steam_whistle/extension/" + size + (shape == WhistleExtenderShape.SINGLE ? "_single" : "_double")));
			rim.setItem(ModelItems.block("steam_whistle/extension/" + size + "_top_rim"));
			rim.scale(shape == WhistleExtenderShape.DOUBLE ? 1 : .0001f).apply(0);
			main.apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
		}
	}
}
