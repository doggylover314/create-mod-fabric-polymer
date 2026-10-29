package dev.createpolymer.contraptions.elevator;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.api.schematic.requirement.SpecialBlockItemRequirement;
import dev.createpolymer.contraptions.elevator.ElevatorColumn.ColumnCoords;
import dev.createpolymer.contraptions.render.ContraptionDisplay;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.redstone.WrenchableDirectionalBlock;
import dev.createpolymer.redstone.contact.RedstoneContactBlock;
import dev.createpolymer.redstone.diodes.BrassDiodeBlock;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.PulleyRegistry;
import dev.createpolymer.registry.feature.RedstoneRegistry;
import dev.createpolymer.schematics.requirement.ItemRequirement;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.BlockstateModels;
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
import net.minecraft.world.item.context.UseOnContext;
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
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Create's elevator contact: a redstone contact that stands for one floor of an elevator column. It lights up and
 * emits a signal while the cabin is on its floor, calls the cabin when powered, and has a name.
 * <p>
 * Right-clicking it opens {@link ElevatorContactGui}, which stands in for Create's contact screen and the floor
 * selection of the contraption controls.
 */
public class ElevatorContactBlock extends WrenchableDirectionalBlock
	implements SpecialBlockItemRequirement, IBE<ElevatorContactBlockEntity>, PolymerModelBlock, ContraptionDisplay {

	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	public static final BooleanProperty CALLING = BooleanProperty.create("calling");
	public static final BooleanProperty POWERING = BrassDiodeBlock.POWERING;

	public ElevatorContactBlock(Properties pProperties) {
		super(pProperties);
		registerDefaultState(defaultBlockState().setValue(CALLING, false)
			.setValue(POWERING, false)
			.setValue(POWERED, false)
			.setValue(FACING, Direction.SOUTH));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(CALLING, POWERING, POWERED));
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
		if (facing.getAxis() != Axis.Y
			&& ElevatorColumn.get(level, new ColumnCoords(pos.getX(), pos.getZ(), facing)) != null)
			return onWrenched;

		level.setBlockAndUpdate(pos, copyProperties(state, RedstoneRegistry.REDSTONE_CONTACT.defaultBlockState()));

		return onWrenched;
	}

	public static BlockState defaultContact() {
		return PulleyRegistry.ELEVATOR_CONTACT.defaultBlockState();
	}

	/** Create's BlockHelper.copyProperties: every property both states have is taken over. */
	public static BlockState copyProperties(BlockState fromState, BlockState toState) {
		for (Property<?> property : fromState.getProperties())
			toState = copyProperty(fromState, toState, property);
		return toState;
	}

	private static <T extends Comparable<T>> BlockState copyProperty(BlockState from, BlockState to, Property<T> property) {
		return to.hasProperty(property) ? to.setValue(property, from.getValue(property)) : to;
	}

	@Nullable
	public static ColumnCoords getColumnCoords(LevelAccessor level, BlockPos pos) {
		BlockState blockState = level.getBlockState(pos);
		if (!(blockState.getBlock() instanceof ElevatorContactBlock) && !blockState.is(RedstoneRegistry.REDSTONE_CONTACT))
			return null;
		Direction facing = blockState.getValue(FACING);
		BlockPos target = pos;
		return new ColumnCoords(target.getX(), target.getZ(), facing);
	}

	@Override
	protected void neighborChanged(BlockState pState, Level pLevel, BlockPos pPos, Block pBlock,
		net.minecraft.world.level.redstone.@Nullable Orientation orientation, boolean pIsMoving) {
		if (pLevel.isClientSide())
			return;

		boolean isPowered = pState.getValue(POWERED);
		if (isPowered == pLevel.hasNeighborSignal(pPos))
			return;

		pLevel.setBlock(pPos, pState.cycle(POWERED), Block.UPDATE_CLIENTS);

		if (isPowered)
			return;
		if (pState.getValue(CALLING))
			return;

		ElevatorColumn elevatorColumn = ElevatorColumn.getOrCreate(pLevel, getColumnCoords(pLevel, pPos));
		callToContactAndUpdate(elevatorColumn, pState, pLevel, pPos, true);
	}

	public void callToContactAndUpdate(ElevatorColumn elevatorColumn, BlockState pState, Level pLevel, BlockPos pPos,
		boolean powered) {
		pLevel.setBlock(pPos, pState.cycle(CALLING), Block.UPDATE_CLIENTS);

		for (BlockPos otherPos : elevatorColumn.getContacts()) {
			if (otherPos.equals(pPos))
				continue;
			BlockState otherState = pLevel.getBlockState(otherPos);
			if (!(otherState.getBlock() instanceof ElevatorContactBlock))
				continue;
			pLevel.setBlock(otherPos, otherState.setValue(CALLING, false), 2 | 16);
			scheduleActivation(pLevel, otherPos);
		}

		if (powered)
			pState = pState.setValue(POWERED, true);
		pLevel.setBlock(pPos, pState.setValue(CALLING, true), Block.UPDATE_CLIENTS);
		pLevel.updateNeighborsAt(pPos, this);

		elevatorColumn.target(pPos.getY());
		elevatorColumn.markDirty();
	}

	public void scheduleActivation(ScheduledTickAccess pLevel, BlockPos pPos) {
		if (!pLevel.getBlockTicks()
			.hasScheduledTick(pPos, this))
			pLevel.scheduleTick(pPos, this, 1);
	}

	@Override
	protected void tick(BlockState pState, ServerLevel pLevel, BlockPos pPos, RandomSource pRand) {
		boolean wasPowering = pState.getValue(POWERING);

		Optional<ElevatorContactBlockEntity> optionalBE = getBlockEntityOptional(pLevel, pPos);
		boolean shouldBePowering = optionalBE.map(be -> {
			boolean activateBlock = be.activateBlock;
			be.activateBlock = false;
			be.setChanged();
			return activateBlock;
		})
			.orElse(false);

		shouldBePowering |= RedstoneContactBlock.hasValidContact(pLevel, pPos, pState.getValue(FACING));

		if (wasPowering || shouldBePowering)
			pLevel.setBlock(pPos, pState.setValue(POWERING, shouldBePowering), 2 | 16);

		pLevel.updateNeighborsAt(pPos, this);
	}

	@Override
	protected BlockState updateShape(BlockState stateIn, LevelReader level, ScheduledTickAccess ticks, BlockPos currentPos,
		Direction facing, BlockPos facingPos, BlockState facingState, RandomSource random) {
		if (facing != stateIn.getValue(FACING))
			return stateIn;
		boolean hasValidContact = RedstoneContactBlock.hasValidContact(level, currentPos, facing);
		if (stateIn.getValue(POWERING) != hasValidContact)
			scheduleActivation(ticks, currentPos);
		return stateIn;
	}

	@Override
	protected boolean isSignalSource(BlockState state) {
		return state.getValue(POWERING);
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(RedstoneRegistry.REDSTONE_CONTACT_ITEM);
	}

	@Override
	protected boolean shouldRedstoneWireConnectTo(BlockState state, BlockGetter world, BlockPos pos,
		@Nullable Direction side) {
		if (side == null)
			return true;
		return state.getValue(FACING) != side.getOpposite();
	}

	@Override
	protected int getSignal(BlockState state, BlockGetter blockAccess, BlockPos pos, Direction side) {
		BlockState toState = blockAccess.getBlockState(pos.relative(side.getOpposite()));
		if (toState.is(this))
			return 0;
		return state.getValue(POWERING) ? 15 : 0;
	}

	@Override
	protected int ownSignal(BlockState state, BlockGetter level, BlockPos pos) {
		return state.getValue(POWERING) ? 15 : 0;
	}

	@Override
	public Class<ElevatorContactBlockEntity> getBlockEntityClass() {
		return ElevatorContactBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends ElevatorContactBlockEntity> getBlockEntityType() {
		return PulleyRegistry.ELEVATOR_CONTACT_BLOCK_ENTITY;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (stack.is(ModItems.WRENCH))
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (level.isClientSide())
			return InteractionResult.SUCCESS;
		if (player instanceof ServerPlayer serverPlayer)
			withBlockEntityDo(level, pos, be -> ElevatorContactGui.open(serverPlayer, be));
		return InteractionResult.SUCCESS;
	}

	public static int getLight(BlockState state) {
		return state.getValue(POWERING) ? 10 : 0;
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.GOLD_BLOCK.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	@Override
	public List<Piece> getContraptionPieces(BlockState state) {
		BlockstateModels.Variant variant = BlockstateModels.get("elevator_contact", state);
		return variant == null ? List.of() : List.of(new Piece(variant.stack(), variant.rotation()));
	}

	/** The contact model, picked by facing and state from Create's blockstate file. */
	public static class Model extends BaseModel {
		private final Part block;

		public Model(BlockState state) {
			super(state);
			block = addPart(ModelItems.block("elevator_contact/block"));
			showVariant(block, "elevator_contact", state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(block, "elevator_contact", state);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
		}
	}

	@Override
	public ItemRequirement getRequiredItems(BlockState state, @Nullable BlockEntity be) {
		return ItemRequirement.of(RedstoneRegistry.REDSTONE_CONTACT.defaultBlockState(), be);
	}
}
