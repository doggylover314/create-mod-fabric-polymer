package dev.createpolymer.redstone.contact;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.contraptions.elevator.ElevatorColumn;
import dev.createpolymer.contraptions.elevator.ElevatorColumn.ColumnCoords;
import dev.createpolymer.contraptions.elevator.ElevatorContactBlock;
import dev.createpolymer.redstone.WrenchableDirectionalBlock;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Create's redstone contact. Two contacts facing each other power each other; wrenching one that is lined up with an
 * elevator's column turns it into an elevator contact. The part of Create's contact that works with moving
 * contraptions is not ported. As for Create's {@code shouldCheckWeakPower = false},
 * the registration makes the block not a redstone conductor.
 */
public class RedstoneContactBlock extends WrenchableDirectionalBlock implements PolymerModelBlock {

	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

	public RedstoneContactBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(POWERED, false)
			.setValue(FACING, Direction.UP));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(POWERED);
		super.createBlockStateDefinition(builder);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = defaultBlockState().setValue(FACING, context.getNearestLookingDirection()
			.getOpposite());
		Direction placeDirection = context.getClickedFace()
			.getOpposite();

		if ((context.getPlayer() != null && context.getPlayer()
			.isShiftKeyDown()) || hasValidContact(context.getLevel(), context.getClickedPos(), placeDirection))
			state = state.setValue(FACING, placeDirection);
		if (hasValidContact(context.getLevel(), context.getClickedPos(), state.getValue(FACING)))
			state = state.setValue(POWERED, true);

		return state;
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		InteractionResult onWrenched = super.onWrenched(state, context);
		if (onWrenched != InteractionResult.SUCCESS)
			return onWrenched;

		Level level = context.getLevel();
		if (level.isClientSide())
			return onWrenched;

		BlockPos pos = context.getClickedPos();
		state = level.getBlockState(pos);
		Direction facing = state.getValue(RedstoneContactBlock.FACING);
		if (facing.getAxis() == Axis.Y)
			return onWrenched;
		if (ElevatorColumn.get(level, new ColumnCoords(pos.getX(), pos.getZ(), facing)) == null)
			return onWrenched;

		level.setBlockAndUpdate(pos, ElevatorContactBlock.copyProperties(state, ElevatorContactBlock.defaultContact()));

		return onWrenched;
	}

	@Override
	protected BlockState updateShape(BlockState stateIn, LevelReader level, ScheduledTickAccess ticks, BlockPos currentPos,
		Direction facing, BlockPos facingPos, BlockState facingState, RandomSource random) {
		if (facing != stateIn.getValue(FACING))
			return stateIn;
		boolean hasValidContact = hasValidContact(level, currentPos, facing);
		if (stateIn.getValue(POWERED) != hasValidContact)
			return stateIn.setValue(POWERED, hasValidContact);
		return stateIn;
	}

	/** Create does this when the old state is removed; here it is when the new one is placed. */
	@Override
	protected void onPlace(BlockState state, Level worldIn, BlockPos pos, BlockState oldState,
		boolean movedByPiston) {
		if (oldState.getBlock() == this && state == oldState.cycle(POWERED))
			worldIn.updateNeighborsAt(pos, this);
	}

	@Override
	protected void tick(BlockState state, ServerLevel worldIn, BlockPos pos, RandomSource random) {
		boolean hasValidContact = hasValidContact(worldIn, pos, state.getValue(FACING));
		if (state.getValue(POWERED) != hasValidContact)
			worldIn.setBlockAndUpdate(pos, state.setValue(POWERED, hasValidContact));
	}

	public static boolean hasValidContact(BlockGetter world, BlockPos pos, Direction direction) {
		BlockState blockState = world.getBlockState(pos.relative(direction));
		return (blockState.is(RedstoneRegistry.REDSTONE_CONTACT) || blockState.getBlock() instanceof ElevatorContactBlock)
			&& blockState.getValue(FACING) == direction.getOpposite();
	}

	@Override
	protected boolean isSignalSource(BlockState state) {
		return state.getValue(POWERED);
	}

	@Override
	protected boolean shouldRedstoneWireConnectTo(BlockState state, BlockGetter world, BlockPos pos, @Nullable Direction side) {
		return side != null && state.getValue(FACING) != side.getOpposite();
	}

	@Override
	protected int getSignal(BlockState state, BlockGetter blockAccess, BlockPos pos, Direction side) {
		return state.getValue(POWERED) && side != state.getValue(FACING)
			.getOpposite() ? 15 : 0;
	}

	@Override
	protected int ownSignal(BlockState state, BlockGetter level, BlockPos pos) {
		return state.getValue(POWERED) ? 15 : 0;
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.STONE.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/** Create's contact model, picked by facing and power from its blockstate file. */
	public static class Model extends BaseModel {
		private final Part block;

		public Model(BlockState state) {
			super(state);
			block = addPart(ModelItems.block("redstone_contact/block"));
			showVariant(block, "redstone_contact", state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(block, "redstone_contact", state);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
		}
	}

}
