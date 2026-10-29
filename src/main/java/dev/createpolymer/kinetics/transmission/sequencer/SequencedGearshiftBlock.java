package dev.createpolymer.kinetics.transmission.sequencer;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.item.WrenchItem;
import dev.createpolymer.kinetics.base.HorizontalAxisKineticBlock;
import dev.createpolymer.kinetics.base.KineticBlock;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.base.RotatedPillarKineticBlock;
import dev.createpolymer.kinetics.transmission.SplitShaftBlockEntity;
import dev.createpolymer.registry.ModSounds;
import dev.createpolymer.registry.feature.KineticsExtraRegistry;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Ported from Create's SequencedGearshiftBlock (MIT, The Create Team).
 * Right-click with an empty hand opens the instruction editor ({@link SequencedGearshiftGui}).
 */
public class SequencedGearshiftBlock extends HorizontalAxisKineticBlock implements IBE<SequencedGearshiftBlockEntity>, IWrenchable {

	public static final BooleanProperty VERTICAL = BooleanProperty.create("vertical");
	public static final IntegerProperty STATE = IntegerProperty.create("state", 0, 5);

	public SequencedGearshiftBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(VERTICAL, false).setValue(STATE, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(STATE, VERTICAL));
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
								   net.minecraft.world.level.redstone.@Nullable Orientation orientation, boolean isMoving) {
		if (level.isClientSide())
			return;
		if (!level.getBlockTicks()
			.willTickThisTick(pos, this))
			level.scheduleTick(pos, this, 1);
	}

	@Override
	protected void tick(BlockState state, ServerLevel worldIn, BlockPos pos, RandomSource r) {
		boolean previouslyPowered = state.getValue(STATE) != 0;
		boolean isPowered = worldIn.hasNeighborSignal(pos);
		withBlockEntityDo(worldIn, pos, sgte -> sgte.onRedstoneUpdate(isPowered, previouslyPowered));
	}

	@Override
	protected boolean areStatesKineticallyEquivalent(BlockState oldState, BlockState newState) {
		return false;
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		if (state.getValue(VERTICAL))
			return face.getAxis()
				.isVertical();
		return super.hasShaftTowards(world, pos, state, face);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
										  InteractionHand hand, BlockHitResult hitResult) {
		if (stack.getItem() instanceof WrenchItem)
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (stack.getItem() instanceof BlockItem blockItem) {
			if (blockItem.getBlock() instanceof KineticBlock && hasShaftTowards(level, pos, state, hitResult.getDirection()))
				return InteractionResult.TRY_WITH_EMPTY_HAND;
		}

		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer)
			withBlockEntityDo(level, pos, be -> SequencedGearshiftGui.open(serverPlayer, be));
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Axis preferredAxis = RotatedPillarKineticBlock.getPreferredAxis(context);
		if (preferredAxis != null && (context.getPlayer() == null || !context.getPlayer()
			.isShiftKeyDown()))
			return withAxis(preferredAxis, context);
		return withAxis(context.getNearestLookingDirection()
			.getAxis(), context);
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		BlockState newState = state;
		Direction face = context.getClickedFace();

		if (face.getAxis() != Axis.Y) {
			if (newState.getValue(HORIZONTAL_AXIS) != face.getAxis())
				newState = newState.cycle(VERTICAL);
		} else {
			// Create's IWrenchable.getRotatedBlockState for HORIZONTAL_AXIS
			newState = newState.setValue(HORIZONTAL_AXIS, newState.getValue(HORIZONTAL_AXIS) == Axis.X ? Axis.Z : Axis.X);
		}

		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		if (!newState.canSurvive(level, pos))
			return InteractionResult.PASS;
		KineticBlockEntity.switchToBlockState(level, pos, Block.updateFromNeighbourShapes(newState, level, pos));
		if (level.getBlockState(pos) != state)
			ModSounds.wrenchRotate(level, pos);
		return InteractionResult.SUCCESS;
	}

	private BlockState withAxis(Axis axis, BlockPlaceContext context) {
		BlockState state = defaultBlockState().setValue(VERTICAL, axis.isVertical());
		if (axis.isVertical())
			return state.setValue(HORIZONTAL_AXIS, context.getHorizontalDirection()
				.getAxis());
		return state.setValue(HORIZONTAL_AXIS, axis);
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		if (state.getValue(VERTICAL))
			return Axis.Y;
		return super.getRotationAxis(state);
	}

	@Override
	public Class<SequencedGearshiftBlockEntity> getBlockEntityClass() {
		return SequencedGearshiftBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends SequencedGearshiftBlockEntity> getBlockEntityType() {
		return KineticsExtraRegistry.SEQUENCED_GEARSHIFT_BE;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos, Direction direction) {
		return state.getValue(STATE)
			.intValue();
	}

	@Override
	protected BlockState particleState() {
		return Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/** Casing from Create's blockstate file plus the two shaft halves of its SplitShaftVisual. */
	public static class Model extends BaseModel {
		private final Part casing;
		private final Part positive;
		private final Part negative;

		public Model(BlockState state) {
			super(state);
			casing = addPart(ModelItems.block("shaft"));
			positive = addPart(ModelItems.block("shaft_half"));
			negative = addPart(ModelItems.block("shaft_half"));
			onStateChanged(state);
		}

		private static Axis axisOf(BlockState state) {
			return state.getValue(VERTICAL) ? Axis.Y : state.getValue(HORIZONTAL_AXIS);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(casing, "sequenced_gearshift", state);
			Axis axis = axisOf(state);
			Direction pos = Direction.get(AxisDirection.POSITIVE, axis);
			positive.orientation(Orientation.fromSouth(pos)).spinAxis(axis).apply(0);
			negative.orientation(Orientation.fromSouth(pos.getOpposite())).spinAxis(axis).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof SplitShaftBlockEntity split))
				return;
			Axis axis = axisOf(state);
			float offset = Orientation.cogOffset(false, axis, pos.getX(), pos.getY(), pos.getZ());
			Direction p = Direction.get(AxisDirection.POSITIVE, axis);
			positive.spin(time, split.getSpeed() * split.getRotationSpeedModifier(p), offset);
			negative.spin(time, split.getSpeed() * split.getRotationSpeedModifier(p.getOpposite()), offset);
		}
	}

}
