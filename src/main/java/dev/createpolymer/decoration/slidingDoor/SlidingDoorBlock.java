package dev.createpolymer.decoration.slidingDoor;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.contraptions.ContraptionWorld;
import dev.createpolymer.contraptions.render.ContraptionDisplay;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.registry.feature.DoorsRegistry;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.BlockstateModels;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ported from Create's SlidingDoorBlock. Players see no door at all: the panels are display entities that slide (or
 * fold) like SlidingDoorRenderer moves them, and the block they see is only there for its hitbox: the thin slab of a
 * closed door, nothing while the door is open or still moving.
 */
public class SlidingDoorBlock extends DoorBlock
	implements IWrenchable, IBE<SlidingDoorBlockEntity>, PolymerTexturedBlock, BlockWithElementHolder, ContraptionDisplay {
	public static final Supplier<BlockSetType> TRAIN_SET_TYPE =
		() -> new BlockSetType("create:train", true, true, true,
			BlockSetType.PressurePlateSensitivity.EVERYTHING, SoundType.NETHERITE_BLOCK, SoundEvents.IRON_DOOR_CLOSE,
			SoundEvents.IRON_DOOR_OPEN, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundEvents.IRON_TRAPDOOR_OPEN,
			SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,
			SoundEvents.STONE_BUTTON_CLICK_OFF, SoundEvents.STONE_BUTTON_CLICK_ON);

	public static final Supplier<BlockSetType> GLASS_SET_TYPE =
		() -> new BlockSetType("create:glass", true, true, true,
			BlockSetType.PressurePlateSensitivity.EVERYTHING, SoundType.GLASS, SoundEvents.IRON_DOOR_CLOSE,
			SoundEvents.IRON_DOOR_OPEN, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundEvents.IRON_TRAPDOOR_OPEN,
			SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,
			SoundEvents.STONE_BUTTON_CLICK_OFF, SoundEvents.STONE_BUTTON_CLICK_ON);

	public static final Supplier<BlockSetType> STONE_SET_TYPE =
		() -> new BlockSetType("create:stone", true, true, true,
			BlockSetType.PressurePlateSensitivity.EVERYTHING, SoundType.STONE, SoundEvents.IRON_DOOR_CLOSE,
			SoundEvents.IRON_DOOR_OPEN, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundEvents.IRON_TRAPDOOR_OPEN,
			SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,
			SoundEvents.STONE_BUTTON_CLICK_OFF, SoundEvents.STONE_BUTTON_CLICK_ON);

	/** True while the door stands closed and its panels rest: then the hitbox is the closed door's. */
	public static final BooleanProperty VISIBLE = BooleanProperty.create("visible");

	// The slab of a closed vanilla door per facing, invisible: what players bump into while the door is shut.
	private static final Map<Direction, BlockState> CLIENT_CLOSED = new EnumMap<>(Direction.class);

	private final boolean folds;

	public static SlidingDoorBlock metal(Properties properties, boolean folds) {
		return new SlidingDoorBlock(properties, TRAIN_SET_TYPE.get(), folds);
	}

	public static SlidingDoorBlock glass(Properties properties, boolean folds) {
		return new SlidingDoorBlock(properties, GLASS_SET_TYPE.get(), folds);
	}

	public static SlidingDoorBlock stone(Properties properties, boolean folds) {
		return new SlidingDoorBlock(properties, STONE_SET_TYPE.get(), folds);
	}

	public SlidingDoorBlock(Properties properties, BlockSetType type, boolean folds) {
		super(type, properties);
		this.folds = folds;
		registerDefaultState(defaultBlockState().setValue(VISIBLE, true));
	}

	/** Reserves the client states; must run while the mod initialises. */
	public static void requestClientStates() {
		for (Direction d : Iterate.horizontalDirections)
			CLIENT_CLOSED.put(d, PolymerBlockResourceUtils.requestEmpty(BlockModelType.getDoor(d)));
	}

	public boolean isFoldingDoor() {
		return folds;
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
		super.createBlockStateDefinition(pBuilder.add(VISIBLE));
	}

	@Override
	protected VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		if (!pState.getValue(OPEN) && (pState.getValue(VISIBLE) || pLevel instanceof ContraptionWorld))
			return super.getShape(pState, pLevel, pPos, pContext);

		Direction direction = pState.getValue(FACING);
		boolean hinge = pState.getValue(HINGE) == DoorHingeSide.RIGHT;
		return SlidingDoorShapes.get(direction, hinge, isFoldingDoor());
	}

	/** Polymer would otherwise answer with the collision of the block players see. */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
		CollisionContext context) {
		return getShape(state, level, pos, context);
	}

	@Override
	protected boolean canSurvive(BlockState pState, LevelReader pLevel, BlockPos pPos) {
		return pState.getValue(HALF) == DoubleBlockHalf.LOWER || pLevel.getBlockState(pPos.below())
			.is(this);
	}

	@Override
	protected VoxelShape getInteractionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos) {
		return getShape(pState, pLevel, pPos, CollisionContext.empty());
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext pContext) {
		BlockState stateForPlacement = super.getStateForPlacement(pContext);
		if (stateForPlacement != null && stateForPlacement.getValue(OPEN))
			return stateForPlacement.setValue(OPEN, false)
				.setValue(POWERED, false);
		return stateForPlacement;
	}

	@Override
	protected void onPlace(BlockState pState, Level pLevel, BlockPos pPos, BlockState pOldState, boolean pIsMoving) {
		if (!pOldState.is(this))
			deferUpdate(pLevel, pPos);
	}

	@Override
	protected BlockState updateShape(BlockState pState, LevelReader pLevel, ScheduledTickAccess ticks,
		BlockPos pCurrentPos, Direction pFacing, BlockPos pFacingPos, BlockState pFacingState, RandomSource random) {
		BlockState blockState =
			super.updateShape(pState, pLevel, ticks, pCurrentPos, pFacing, pFacingPos, pFacingState, random);
		if (blockState.isAir())
			return blockState;
		DoubleBlockHalf doubleblockhalf = blockState.getValue(HALF);
		if (pFacing.getAxis() == Direction.Axis.Y
			&& doubleblockhalf == DoubleBlockHalf.LOWER == (pFacing == Direction.UP)) {
			return pFacingState.is(this) && pFacingState.getValue(HALF) != doubleblockhalf
				? blockState.setValue(VISIBLE, pFacingState.getValue(VISIBLE))
				: Blocks.AIR.defaultBlockState();
		}
		return blockState;
	}

	@Override
	public void setOpen(@Nullable Entity entity, Level level, BlockState state, BlockPos pos, boolean open) {
		if (!state.is(this))
			return;
		if (state.getValue(OPEN) == open)
			return;
		BlockState changedState = state.setValue(OPEN, open);
		if (open)
			changedState = changedState.setValue(VISIBLE, false);
		level.setBlock(pos, changedState, UPDATE_CLIENTS | UPDATE_IMMEDIATE);

		DoorHingeSide hinge = changedState.getValue(HINGE);
		Direction facing = changedState.getValue(FACING);
		BlockPos otherPos =
			pos.relative(hinge == DoorHingeSide.LEFT ? facing.getClockWise() : facing.getCounterClockWise());
		BlockState otherDoor = level.getBlockState(otherPos);
		if (isDoubleDoor(changedState, hinge, facing, otherDoor))
			setOpen(entity, level, otherDoor, otherPos, open);

		this.playSound(entity, level, pos, open);
		level.gameEvent(entity, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
	}

	@Override
	protected void neighborChanged(BlockState pState, Level pLevel, BlockPos pPos, Block pBlock,
		@Nullable Orientation orientation, boolean pIsMoving) {
		boolean lower = pState.getValue(HALF) == DoubleBlockHalf.LOWER;
		boolean isPowered = isDoorPowered(pLevel, pPos, pState);
		if (defaultBlockState().is(pBlock))
			return;
		if (isPowered == pState.getValue(POWERED))
			return;

		SlidingDoorBlockEntity be = getBlockEntity(pLevel, lower ? pPos : pPos.below());
		if (be != null && be.deferUpdate)
			return;

		BlockState changedState = pState.setValue(POWERED, isPowered)
			.setValue(OPEN, isPowered);
		if (isPowered)
			changedState = changedState.setValue(VISIBLE, false);

		if (isPowered != pState.getValue(OPEN)) {
			this.playSound(null, pLevel, pPos, isPowered);
			pLevel.gameEvent(null, isPowered ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pPos);

			DoorHingeSide hinge = changedState.getValue(HINGE);
			Direction facing = changedState.getValue(FACING);
			BlockPos otherPos =
				pPos.relative(hinge == DoorHingeSide.LEFT ? facing.getClockWise() : facing.getCounterClockWise());
			BlockState otherDoor = pLevel.getBlockState(otherPos);

			if (isDoubleDoor(changedState, hinge, facing, otherDoor)) {
				otherDoor = otherDoor.setValue(POWERED, isPowered)
					.setValue(OPEN, isPowered);
				if (isPowered)
					otherDoor = otherDoor.setValue(VISIBLE, false);
				pLevel.setBlock(otherPos, otherDoor, Block.UPDATE_CLIENTS);
			}
		}

		pLevel.setBlock(pPos, changedState, Block.UPDATE_CLIENTS);
	}

	public static boolean isDoorPowered(Level pLevel, BlockPos pPos, BlockState state) {
		boolean lower = state.getValue(HALF) == DoubleBlockHalf.LOWER;
		DoorHingeSide hinge = state.getValue(HINGE);
		Direction facing = state.getValue(FACING);
		BlockPos otherPos =
			pPos.relative(hinge == DoorHingeSide.LEFT ? facing.getClockWise() : facing.getCounterClockWise());
		BlockState otherDoor = pLevel.getBlockState(otherPos);

		if (isDoubleDoor(state.cycle(OPEN), hinge, facing, otherDoor) && (pLevel.hasNeighborSignal(otherPos)
			|| pLevel.hasNeighborSignal(otherPos.relative(lower ? Direction.UP : Direction.DOWN))))
			return true;

		return pLevel.hasNeighborSignal(pPos)
			|| pLevel.hasNeighborSignal(pPos.relative(lower ? Direction.UP : Direction.DOWN));
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
		BlockHitResult hitResult) {
		state = state.cycle(OPEN);
		boolean isOpen = state.getValue(OPEN);
		if (isOpen)
			state = state.setValue(VISIBLE, false);
		level.setBlock(pos, state, UPDATE_CLIENTS | UPDATE_IMMEDIATE);
		level.gameEvent(player, isOpen(state) ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);

		DoorHingeSide hinge = state.getValue(HINGE);
		Direction facing = state.getValue(FACING);
		BlockPos otherPos =
			pos.relative(hinge == DoorHingeSide.LEFT ? facing.getClockWise() : facing.getCounterClockWise());
		BlockState otherDoor = level.getBlockState(otherPos);
		if (isDoubleDoor(state, hinge, facing, otherDoor))
			useWithoutItem(otherDoor, level, otherPos, player, hitResult);
		else if (isOpen) {
			this.playSound(player, level, pos, true);
			level.gameEvent(player, GameEvent.BLOCK_OPEN, pos);
		}

		return InteractionResult.SUCCESS;
	}

	public void deferUpdate(Level level, BlockPos pos) {
		withBlockEntityDo(level, pos, sdte -> sdte.deferUpdate = true);
	}

	public static boolean isDoubleDoor(BlockState pState, DoorHingeSide hinge, Direction facing, BlockState otherDoor) {
		return otherDoor.getBlock() == pState.getBlock() && otherDoor.getValue(HINGE) != hinge
			&& otherDoor.getValue(FACING) == facing && otherDoor.getValue(OPEN) != pState.getValue(OPEN)
			&& otherDoor.getValue(HALF) == pState.getValue(HALF);
	}

	private void playSound(@Nullable Entity pSource, Level pLevel, BlockPos pPos, boolean pIsOpening) {
		pLevel.playSound(pSource, pPos, pIsOpening ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE,
			SoundSource.BLOCKS, 1.0F, pLevel.getRandom()
				.nextFloat() * 0.1F + 0.9F);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		if (state.getValue(HALF) == DoubleBlockHalf.UPPER)
			return null;
		return IBE.super.newBlockEntity(pos, state);
	}

	@Override
	public Class<SlidingDoorBlockEntity> getBlockEntityClass() {
		return SlidingDoorBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends SlidingDoorBlockEntity> getBlockEntityType() {
		return DoorsRegistry.SLIDING_DOOR_BLOCK_ENTITY;
	}

	// ---- Polymer ----

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		if (!state.getValue(OPEN) && state.getValue(VISIBLE)) {
			BlockState closed = CLIENT_CLOSED.get(state.getValue(FACING));
			if (closed != null)
				return closed;
		}
		return Blocks.AIR.defaultBlockState();
	}

	@Override
	public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
		return Blocks.IRON_BLOCK.defaultBlockState();
	}

	@Override
	public boolean tickElementHolder(ServerLevel world, BlockPos pos, BlockState initialBlockState) {
		return true;
	}

	/** The lower half draws both panels. */
	@Override
	public @Nullable ElementHolder createElementHolder(ServerLevel world, BlockPos pos,
		BlockState initialBlockState) {
		if (initialBlockState.getValue(HALF) == DoubleBlockHalf.UPPER)
			return null;
		return new Model(initialBlockState);
	}

	/** The blockstate file (and name of the model folder) of this door: "andesite_door", ... */
	public String blockstateFile() {
		return BuiltInRegistries.BLOCK.getKey(this)
			.getPath();
	}

	/**
	 * On a contraption the panels cannot slide: a closed door is the model of its half, an open one is gone (the
	 * panel is in the wall).
	 */
	@Override
	public List<Piece> getContraptionPieces(BlockState state) {
		if (state.getValue(OPEN))
			return List.of();
		BlockstateModels.Variant variant = BlockstateModels.get(blockstateFile(), state);
		if (variant == null)
			return List.of();
		return List.of(new Piece(variant.stack(), variant.rotation()));
	}

	/**
	 * SlidingDoorRenderer: both halves of a plain door slide sideways (the first third of the way they also move
	 * 1/32 out of the door frame), the two panels of a folding door swing open on hinges. The animation value comes
	 * from the block entity.
	 */
	public static class Model extends BaseModel {
		private final String file;
		private final Part[] parts;
		private float shownValue = -1;
		private boolean dirty = true;

		public Model(BlockState state) {
			super(state);
			SlidingDoorBlock block = (SlidingDoorBlock) state.getBlock();
			file = block.blockstateFile();
			parts = new Part[2];
			for (int i = 0; i < parts.length; i++)
				parts[i] = addPart(net.minecraft.world.item.ItemStack.EMPTY);
			onStateChanged(state);
		}

		private boolean folds() {
			return ((SlidingDoorBlock) state.getBlock()).isFoldingDoor();
		}

		@Override
		protected void onStateChanged(BlockState state) {
			if (folds()) {
				parts[0].setItem(ModelItems.block(file + "/fold_left"));
				parts[1].setItem(ModelItems.block(file + "/fold_right"));
			} else {
				BlockState closed = state.setValue(OPEN, false);
				for (DoubleBlockHalf half : DoubleBlockHalf.values()) {
					Part part = parts[half == DoubleBlockHalf.LOWER ? 0 : 1];
					showVariant(part, file, closed.setValue(HALF, half));
				}
			}
			dirty = true;
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			float value = be instanceof SlidingDoorBlockEntity sdbe ? sdbe.getAnimationValue()
				: SlidingDoorBlockEntity.isOpen(state) ? 1 : 0;
			if (value == shownValue && !dirty)
				return;
			int interpolation = dirty ? 0 : 1;
			dirty = false;
			shownValue = value;
			if (folds())
				placeFolding(value, interpolation);
			else
				placeSliding(value, interpolation);
		}

		private void placeSliding(float value, int interpolation) {
			Direction facing = state.getValue(FACING);
			Direction movementDirection = facing.getClockWise();
			if (state.getValue(HINGE) == DoorHingeSide.LEFT)
				movementDirection = movementDirection.getOpposite();

			float value2 = Mth.clamp(value * 10, 0, 1);
			float slide = value * value * 13 / 16f;
			float out = value2 * 1 / 32f;
			float x = movementDirection.getStepX() * slide + facing.getStepX() * out;
			float z = movementDirection.getStepZ() * slide + facing.getStepZ() * out;

			parts[0].translation(x, 0, z)
				.apply(interpolation);
			parts[1].translation(x, 1 - 1 / 512f, z)
				.apply(interpolation);
		}

		private void placeFolding(float value, int interpolation) {
			Direction facing = state.getValue(FACING);
			float value2 = Mth.clamp(value * 10, 0, 1);
			boolean flip = state.getValue(HINGE) == DoorHingeSide.RIGHT;
			float f = flip ? -1 : 1;

			for (boolean left : Iterate.trueAndFalse) {
				// Create's chain of PoseStack calls on the partial, in block space (corner at the origin)
				Matrix4f m = new Matrix4f();
				m.translate(0, -1 / 512f, 0);
				m.translate(facing.getStepX() * value2 / 32f, 0, facing.getStepZ() * value2 / 32f);
				m.translate(.5f, .5f, .5f)
					.rotateY(AngleHelper.rad(AngleHelper.horizontalAngle(facing.getClockWise())))
					.translate(-.5f, -.5f, -.5f);

				if (flip)
					m.translate(0, 0, 1);
				m.rotateY(AngleHelper.rad(91 * f * value * value));

				if (!left) {
					m.translate(0, 0, f / 2f);
					m.rotateY(AngleHelper.rad(-181 * f * value * value));
				}

				if (flip)
					m.translate(0, 0, -1 / 2f);

				// to block-centred display coordinates
				Matrix4f centered = new Matrix4f().translate(-.5f, -.5f, -.5f)
					.mul(m)
					.translate(.5f, .5f, .5f);
				parts[left ^ flip ? 0 : 1].setMatrix(centered, interpolation);
			}
		}
	}

}
