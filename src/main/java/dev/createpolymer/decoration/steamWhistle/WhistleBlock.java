package dev.createpolymer.decoration.steamWhistle;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.decoration.steamWhistle.WhistleExtenderBlock.WhistleExtenderShape;
import dev.createpolymer.fluids.tank.FluidTankBlock;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModSounds;
import dev.createpolymer.registry.feature.SteamRegistry;
import dev.createpolymer.util.Lang;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.util.VoxelShaper;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WhistleBlock extends Block implements IBE<WhistleBlockEntity>, IWrenchable, PolymerModelBlock {

	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty WALL = BooleanProperty.create("wall");
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	public static final EnumProperty<WhistleSize> SIZE = EnumProperty.create("size", WhistleSize.class);

	// Create's AllShapes.WHISTLE_*
	private static final VoxelShape WHISTLE_SMALL = ShapeUtil.box(4, 3, 4, 12, 16, 12);
	private static final VoxelShape WHISTLE_MEDIUM = ShapeUtil.box(3, 3, 3, 13, 16, 13);
	private static final VoxelShape WHISTLE_LARGE = ShapeUtil.box(2, 3, 2, 14, 16, 14);
	private static final VoxelShape WHISTLE_BASE_UP = Shapes.or(ShapeUtil.box(1, 0, 1, 15, 3, 15), ShapeUtil.box(5, 0, 5, 11, 8, 11));
	private static final VoxelShape WHISTLE_BASE_NORTH = ShapeUtil.transform(WHISTLE_BASE_UP, Direction.NORTH);
	private static final VoxelShape WHISTLE_SMALL_FLOOR = Shapes.or(WHISTLE_SMALL, WHISTLE_BASE_UP);
	private static final VoxelShape WHISTLE_MEDIUM_FLOOR = Shapes.or(WHISTLE_MEDIUM, WHISTLE_BASE_UP);
	private static final VoxelShape WHISTLE_LARGE_FLOOR = Shapes.or(WHISTLE_LARGE, WHISTLE_BASE_UP);
	private static final VoxelShaper WHISTLE_SMALL_WALL = VoxelShaper.forHorizontal(Shapes.or(WHISTLE_SMALL, WHISTLE_BASE_NORTH), Direction.SOUTH);
	private static final VoxelShaper WHISTLE_MEDIUM_WALL = VoxelShaper.forHorizontal(Shapes.or(WHISTLE_MEDIUM, WHISTLE_BASE_NORTH), Direction.SOUTH);
	private static final VoxelShaper WHISTLE_LARGE_WALL = VoxelShaper.forHorizontal(Shapes.or(WHISTLE_LARGE, WHISTLE_BASE_NORTH), Direction.SOUTH);

	public static enum WhistleSize implements StringRepresentable {

		SMALL, MEDIUM, LARGE;

		@Override
		public String getSerializedName() {
			return Lang.asId(name());
		}

	}

	public WhistleBlock(Properties p_49795_) {
		super(p_49795_);
		registerDefaultState(defaultBlockState().setValue(POWERED, false)
			.setValue(WALL, false)
			.setValue(SIZE, WhistleSize.MEDIUM)
			.setValue(FACING, Direction.NORTH));
	}

	@Override
	protected boolean canSurvive(BlockState pState, LevelReader pLevel, BlockPos pPos) {
		return FluidTankBlock.isTank(pLevel.getBlockState(pPos.relative(getAttachedDirection(pState))));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
		super.createBlockStateDefinition(pBuilder.add(FACING, POWERED, SIZE, WALL));
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext pContext) {
		Level level = pContext.getLevel();
		BlockPos clickedPos = pContext.getClickedPos();
		Direction face = pContext.getClickedFace();
		boolean wall = true;
		if (face.getAxis() == Axis.Y) {
			face = pContext.getHorizontalDirection()
				.getOpposite();
			wall = false;
		}

		BlockState state = defaultBlockState().setValue(FACING, face.getOpposite())
			.setValue(POWERED, level.hasNeighborSignal(clickedPos))
			.setValue(WALL, wall);
		if (!state.canSurvive(level, clickedPos))
			return null;
		return state;
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		if (level.isClientSide())
			return InteractionResult.SUCCESS;
		level.setBlock(pos, state.cycle(SIZE), Block.UPDATE_ALL);
		ModSounds.wrenchRotate(level, pos);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (player == null)
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		if (stack.is(SteamRegistry.STEAM_WHISTLE_ITEM)) {
			if (!level.isClientSide())
				incrementSize(level, pos);
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	public static void incrementSize(LevelAccessor pLevel, BlockPos pPos) {
		BlockState base = pLevel.getBlockState(pPos);
		if (!base.hasProperty(SIZE))
			return;
		WhistleSize size = base.getValue(SIZE);
		SoundType soundtype = base.getSoundType();
		BlockPos currentPos = pPos.above();

		for (int i = 1; i <= 6; i++) {
			BlockState blockState = pLevel.getBlockState(currentPos);
			float pVolume = (soundtype.getVolume() + 1.0F) / 2.0F;
			SoundEvent growSound = SoundEvents.NOTE_BLOCK_XYLOPHONE.value();
			SoundEvent hitSound = soundtype.getHitSound();

			if (blockState.is(SteamRegistry.STEAM_WHISTLE_EXTENSION)) {
				if (blockState.getValue(WhistleExtenderBlock.SHAPE) == WhistleExtenderShape.SINGLE) {
					pLevel.setBlock(currentPos,
						blockState.setValue(WhistleExtenderBlock.SHAPE, WhistleExtenderShape.DOUBLE), 3);
					if (soundtype != null) {
						float pPitch = (float) Math.pow(2, -(i * 2) / 12.0);
						pLevel.playSound(null, currentPos, growSound, SoundSource.BLOCKS, pVolume / 4f, pPitch);
						pLevel.playSound(null, currentPos, hitSound, SoundSource.BLOCKS, pVolume, pPitch);
					}
					return;
				}
				currentPos = currentPos.above();
				continue;
			}

			if (!blockState.canBeReplaced())
				return;

			pLevel.setBlock(currentPos, SteamRegistry.STEAM_WHISTLE_EXTENSION.defaultBlockState()
				.setValue(WhistleExtenderBlock.SIZE, size), 3);
			if (soundtype != null) {
				float pPitch = (float) Math.pow(2, -(i * 2 - 1) / 12.0);
				pLevel.playSound(null, currentPos, growSound, SoundSource.BLOCKS, pVolume / 4f, pPitch);
				pLevel.playSound(null, currentPos, hitSound, SoundSource.BLOCKS, pVolume, pPitch);
			}
			return;
		}
	}

	public static void queuePitchUpdate(LevelAccessor level, BlockPos pos) {
		BlockState blockState = level.getBlockState(pos);
		if (blockState.getBlock() instanceof WhistleBlock whistle && !level.getBlockTicks()
			.hasScheduledTick(pos, whistle))
			level.scheduleTick(pos, whistle, 1);
	}

	@Override
	protected void tick(BlockState pState, ServerLevel pLevel, BlockPos pPos, RandomSource pRandom) {
		withBlockEntityDo(pLevel, pPos, WhistleBlockEntity::updatePitch);
	}

	@Override
	protected void onPlace(BlockState pState, Level pLevel, BlockPos pPos, BlockState pOldState, boolean pIsMoving) {
		FluidTankBlock.updateBoilerState(pState, pLevel, pPos.relative(getAttachedDirection(pState)));
		if (pOldState.getBlock() != this || pOldState.getValue(SIZE) != pState.getValue(SIZE))
			queuePitchUpdate(pLevel, pPos);
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState pState, ServerLevel pLevel, BlockPos pPos, boolean pIsMoving) {
		super.affectNeighborsAfterRemoval(pState, pLevel, pPos, pIsMoving);
		FluidTankBlock.updateBoilerState(pState, pLevel, pPos.relative(getAttachedDirection(pState)));
	}

	@Override
	protected void neighborChanged(BlockState state, Level worldIn, BlockPos pos, Block blockIn,
		net.minecraft.world.level.redstone.@Nullable Orientation orientation, boolean isMoving) {
		if (worldIn.isClientSide())
			return;
		boolean previouslyPowered = state.getValue(POWERED);
		if (previouslyPowered != worldIn.hasNeighborSignal(pos))
			worldIn.setBlock(pos, state.cycle(POWERED), Block.UPDATE_CLIENTS);
	}

	@Override
	protected BlockState updateShape(BlockState pState, LevelReader level, ScheduledTickAccess ticks, BlockPos pCurrentPos,
									 Direction pFacing, BlockPos facingPos, BlockState pFacingState, RandomSource random) {
		return getAttachedDirection(pState) == pFacing && !pState.canSurvive(level, pCurrentPos)
			? Blocks.AIR.defaultBlockState()
			: pState;
	}

	@Override
	protected VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		WhistleSize size = pState.getValue(SIZE);
		if (!pState.getValue(WALL))
			return size == WhistleSize.SMALL ? WHISTLE_SMALL_FLOOR
				: size == WhistleSize.MEDIUM ? WHISTLE_MEDIUM_FLOOR : WHISTLE_LARGE_FLOOR;
		Direction direction = pState.getValue(FACING);
		return (size == WhistleSize.SMALL ? WHISTLE_SMALL_WALL
			: size == WhistleSize.MEDIUM ? WHISTLE_MEDIUM_WALL : WHISTLE_LARGE_WALL).get(direction);
	}

	/** Polymer would otherwise answer with the collision of the barrier players see. */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return getShape(state, level, pos, context);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	public static Direction getAttachedDirection(BlockState state) {
		return state.getValue(WALL) ? state.getValue(FACING) : Direction.DOWN;
	}

	@Override
	public Class<WhistleBlockEntity> getBlockEntityClass() {
		return WhistleBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends WhistleBlockEntity> getBlockEntityType() {
		return SteamRegistry.STEAM_WHISTLE_BE;
	}

	@Override
	protected BlockState rotate(BlockState pState, Rotation pRotation) {
		return pState.setValue(FACING, pRotation.rotate(pState.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState pState, Mirror pMirror) {
		return pMirror == Mirror.NONE ? pState : pState.rotate(pMirror.getRotation(pState.getValue(FACING)));
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

	/** Create's WhistleRenderer: the block, and the mouth that moves up and shakes while the whistle blows. */
	public static class Model extends BaseModel {
		private final Part block;
		private final Part mouth;

		public Model(BlockState state) {
			super(state);
			block = addPart(ModelItems.block("steam_whistle/block_medium_floor"));
			mouth = addPart(ModelItems.block("steam_whistle/medium_mouth"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(block, "steam_whistle", state);
			WhistleSize size = state.getValue(SIZE);
			mouth.setItem(ModelItems.block("steam_whistle/" + size.getSerializedName() + "_mouth"));
			mouth.orientation(Orientation.horizontalAngle(state.getValue(FACING))).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof WhistleBlockEntity whistle))
				return;
			WhistleSize size = state.getValue(SIZE);
			float offset = whistle.animation.getValue();
			if (whistle.animation.getChaseTarget() > 0 && whistle.animation.getValue() > 0.5f) {
				// sampled between two ticks: a whistle that shakes once per tick would otherwise not move at all
				float wiggleProgress = (time + .5f) / 8f;
				offset -= Math.sin(wiggleProgress * (2 * Mth.PI) * (4 - size.ordinal())) / 16f;
			}
			mouth.translation(0, offset * 4 / 16f, 0).apply(1);
		}
	}
}
