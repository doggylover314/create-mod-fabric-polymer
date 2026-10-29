package dev.createpolymer.redstone.thresholdSwitch;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilterDisplay;
import dev.createpolymer.foundation.fluid.FluidHandlerLookup;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.redstone.DirectedDirectionalBlock;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.RedstoneRegistry;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public class ThresholdSwitchBlock extends DirectedDirectionalBlock implements IBE<ThresholdSwitchBlockEntity>, PolymerModelBlock {

	public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 5);

	public ThresholdSwitchBlock(Properties p_i48377_1_) {
		super(p_i48377_1_);
	}

	@Override
	protected void onPlace(BlockState state, Level worldIn, BlockPos pos, BlockState oldState, boolean isMoving) {
		withBlockEntityDo(worldIn, pos, ThresholdSwitchBlockEntity::updateCurrentLevel);
	}

	@Override
	protected boolean shouldRedstoneWireConnectTo(BlockState state, BlockGetter world, BlockPos pos, @Nullable Direction side) {
		return side != null && side.getOpposite() != getTargetDirection(state);
	}

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	protected int getSignal(BlockState blockState, BlockGetter blockAccess, BlockPos pos, Direction side) {
		if (side == getTargetDirection(blockState)
			.getOpposite())
			return 0;
		return getBlockEntityOptional(blockAccess, pos).filter(ThresholdSwitchBlockEntity::isPowered)
			.map($ -> 15)
			.orElse(0);
	}

	@Override
	protected int ownSignal(BlockState state, BlockGetter level, BlockPos pos) {
		return getBlockEntityOptional(level, pos).filter(ThresholdSwitchBlockEntity::isPowered)
			.map($ -> 15)
			.orElse(0);
	}

	@Override
	protected void tick(BlockState blockState, ServerLevel world, BlockPos pos, RandomSource random) {
		getBlockEntityOptional(world, pos).ifPresent(ThresholdSwitchBlockEntity::updatePowerAfterDelay);
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(LEVEL));
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (player != null && stack.is(ModItems.WRENCH))
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (player instanceof ServerPlayer serverPlayer)
			withBlockEntityDo(level, pos, be -> ThresholdSwitchGui.open(serverPlayer, be));
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = defaultBlockState();

		Direction preferredFacing = null;
		for (Direction face : context.getNearestLookingDirections()) {
			BlockPos offsetPos = context.getClickedPos()
				.relative(face);
			BlockEntity be = context.getLevel()
				.getBlockEntity(offsetPos);
			if (be != null && (ItemHandlerLookup.get(context.getLevel(), offsetPos, null) != null
				|| FluidHandlerLookup.get(context.getLevel(), offsetPos, null) != null)) {
				preferredFacing = face;
				break;
			}
		}

		if (preferredFacing == null) {
			Direction facing = context.getNearestLookingDirection();
			preferredFacing = context.getPlayer() != null && context.getPlayer()
				.isShiftKeyDown() ? facing : facing.getOpposite();
		}

		if (preferredFacing.getAxis() == Axis.Y) {
			state = state.setValue(TARGET, preferredFacing == Direction.UP ? AttachFace.CEILING : AttachFace.FLOOR);
			preferredFacing = context.getHorizontalDirection();
		}

		return state.setValue(FACING, preferredFacing);
	}

	@Override
	public Class<ThresholdSwitchBlockEntity> getBlockEntityClass() {
		return ThresholdSwitchBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends ThresholdSwitchBlockEntity> getBlockEntityType() {
		return RedstoneRegistry.THRESHOLD_SWITCH_BLOCK_ENTITY;
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.TERRACOTTA.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/** Create's threshold switch model with its fill level indicator, and the filter. */
	public static class Model extends BaseModel {
		private final Part block;
		private final FilterDisplay filter = new FilterDisplay(this);

		public Model(BlockState state) {
			super(state);
			block = addPart(ModelItems.block("threshold_switch/block_wall_0"));
			showVariant(block, "stockpile_switch", state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(block, "stockpile_switch", state);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (be instanceof ThresholdSwitchBlockEntity threshold)
				filter.update(threshold);
		}
	}

}
