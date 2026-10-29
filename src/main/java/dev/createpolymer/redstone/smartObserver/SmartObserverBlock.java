package dev.createpolymer.redstone.smartObserver;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import dev.createpolymer.foundation.fluid.FluidHandlerLookup;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import dev.createpolymer.redstone.DirectedDirectionalBlock;
import dev.createpolymer.registry.feature.RedstoneRegistry;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilterDisplay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;

public class SmartObserverBlock extends DirectedDirectionalBlock implements IBE<SmartObserverBlockEntity>, PolymerModelBlock {

	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

	public SmartObserverBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(POWERED, false));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(POWERED));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = defaultBlockState();

		Direction preferredFacing = null;
		for (Direction face : context.getNearestLookingDirections()) {
			BlockPos offsetPos = context.getClickedPos()
				.relative(face);
			Level world = context.getLevel();
			boolean canDetect = false;
			BlockEntity blockEntity = world.getBlockEntity(offsetPos);

			if (BlockEntityBehaviour.get(blockEntity, TransportedItemStackHandlerBehaviour.TYPE) != null)
				canDetect = true;
			else if (blockEntity != null && (ItemHandlerLookup.get(world, offsetPos, null) != null
				|| FluidHandlerLookup.get(world, offsetPos, null) != null))
				canDetect = true;

			if (canDetect) {
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
	protected boolean isSignalSource(BlockState state) {
		return state.getValue(POWERED);
	}

	@Override
	protected int getSignal(BlockState blockState, BlockGetter blockAccess, BlockPos pos, Direction side) {
		return isSignalSource(blockState) && (side == null || side != getTargetDirection(blockState)
			.getOpposite()) ? 15 : 0;
	}

	@Override
	protected int ownSignal(BlockState state, BlockGetter level, BlockPos pos) {
		return state.getValue(POWERED) ? 15 : 0;
	}

	@Override
	protected void tick(BlockState state, ServerLevel worldIn, BlockPos pos, RandomSource random) {
		worldIn.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_CLIENTS);
		worldIn.updateNeighborsAt(pos, this);
	}

	@Override
	protected boolean shouldRedstoneWireConnectTo(BlockState state, BlockGetter world, BlockPos pos, @Nullable Direction side) {
		return side != state.getValue(FACING)
			.getOpposite();
	}

	@Override
	protected void neighborChanged(BlockState state, Level worldIn, BlockPos pos, Block blockIn,
		@Nullable Orientation orientation, boolean isMoving) {
		InvManipulationBehaviour behaviour = BlockEntityBehaviour.get(worldIn, pos, InvManipulationBehaviour.TYPE);
		if (behaviour != null)
			// The neighbour that changed is not known any more: tell the behaviour about the one it watches.
			behaviour.onNeighborChanged(pos.relative(getTargetDirection(state)));
	}

	public void onFunnelTransfer(Level world, BlockPos funnelPos, ItemStack transferred) {
		for (Direction direction : Iterate.directions) {
			BlockPos detectorPos = funnelPos.relative(direction);
			BlockState detectorState = world.getBlockState(detectorPos);
			if (!detectorState.is(this))
				continue;
			if (SmartObserverBlock.getTargetDirection(detectorState) != direction.getOpposite())
				continue;
			withBlockEntityDo(world, detectorPos, be -> {
				FilteringBehaviour filteringBehaviour = BlockEntityBehaviour.get(be, FilteringBehaviour.TYPE);
				if (filteringBehaviour == null)
					return;
				if (!filteringBehaviour.test(transferred))
					return;
				be.activate(4);
			});
		}
	}

	@Override
	public Class<SmartObserverBlockEntity> getBlockEntityClass() {
		return SmartObserverBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends SmartObserverBlockEntity> getBlockEntityType() {
		return RedstoneRegistry.SMART_OBSERVER_BLOCK_ENTITY;
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

	/** Create's smart observer model (lit when powered) and its filter. */
	public static class Model extends BaseModel {
		private final Part block;
		private final FilterDisplay filter = new FilterDisplay(this);

		public Model(BlockState state) {
			super(state);
			block = addPart(ModelItems.block("content_observer/block"));
			showVariant(block, "content_observer", state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(block, "content_observer", state);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (be instanceof SmartObserverBlockEntity observer)
				filter.update(observer);
		}
	}

}
