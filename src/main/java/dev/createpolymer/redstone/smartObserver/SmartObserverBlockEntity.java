package dev.createpolymer.redstone.smartObserver;

import java.util.List;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.inventory.CapManipulationBehaviourBase.InterfaceProvider;
import dev.createpolymer.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.inventory.VersionedInventoryTrackerBehaviour;
import dev.createpolymer.foundation.fluid.FluidHandlerLookup;
import dev.createpolymer.foundation.fluid.IFluidHandler;
import dev.createpolymer.foundation.fluid.IFluidHandler.FluidAction;
import dev.createpolymer.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import dev.createpolymer.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import dev.createpolymer.redstone.DirectedDirectionalBlock;
import dev.createpolymer.redstone.FilteredDetectorFilterSlot;
import dev.createpolymer.util.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Create's smart observer. Detecting fluids in pipes and packages on chain conveyors is not part of this port (those
 * blocks are not ported); fluid containers are detected through their fluid handler, without a fluid filter.
 */
public class SmartObserverBlockEntity extends SmartBlockEntity implements Clearable {
	private static final int DEFAULT_DELAY = 6;
	private FilteringBehaviour filtering;
	private InvManipulationBehaviour observedInventory;

	private VersionedInventoryTrackerBehaviour invVersionTracker;
	private boolean sustainSignal;

	public int turnOffTicks = 0;

	public SmartObserverBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		setLazyTickRate(20);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		behaviours.add(filtering = new FilteringBehaviour(this, new FilteredDetectorFilterSlot(false))
			.withCallback($ -> invVersionTracker.reset()));
		behaviours.add(invVersionTracker = new VersionedInventoryTrackerBehaviour(this));

		InterfaceProvider towardBlockFacing =
			(w, p, s) -> new BlockFace(p, DirectedDirectionalBlock.getTargetDirection(s));

		behaviours.add(observedInventory = new InvManipulationBehaviour(this, towardBlockFacing).bypassSidedness());
	}

	@Override
	public void tick() {
		super.tick();

		if (level.isClientSide())
			return;

		BlockState state = getBlockState();
		if (turnOffTicks > 0) {
			turnOffTicks--;
			if (turnOffTicks == 0)
				level.scheduleTick(worldPosition, state.getBlock(), 1);
		}

		if (!isActive())
			return;

		BlockPos targetPos = worldPosition.relative(SmartObserverBlock.getTargetDirection(state));
		Block block = level.getBlockState(targetPos)
			.getBlock();

		if (!filtering.getFilter()
			.isEmpty() && block.asItem() != null) {
			ItemStack stack = new ItemStack(block);
			if (block instanceof ShulkerBoxBlock
				&& level.getBlockEntity(targetPos) instanceof ShulkerBoxBlockEntity shulkerBoxBlockEntity
				&& !shulkerBoxBlockEntity.isEmpty()) {
				stack.applyComponents(shulkerBoxBlockEntity.collectComponents());
			}
			if (filtering.test(stack)) {
				activate(3);
				return;
			}
		}

		// Detect items on belt
		TransportedItemStackHandlerBehaviour behaviour =
			BlockEntityBehaviour.get(level, targetPos, TransportedItemStackHandlerBehaviour.TYPE);
		if (behaviour != null) {
			behaviour.handleCenteredProcessingOnAllItems(.45f, stack -> {
				if (!filtering.test(stack.stack) || turnOffTicks == 6)
					return TransportedResult.doNothing();
				activate();
				return TransportedResult.doNothing();
			});
			return;
		}

		if (observedInventory.hasInventory()) {
			boolean skipInv = invVersionTracker.stillWaiting(observedInventory);
			invVersionTracker.awaitNewVersion(observedInventory);

			if (skipInv && sustainSignal)
				turnOffTicks = DEFAULT_DELAY;

			if (!skipInv) {
				sustainSignal = false;
				if (!observedInventory.simulate()
					.extract()
					.isEmpty()) {
					sustainSignal = true;
					activate();
					return;
				}
			}
		}

		if (hasObservedFluid()) {
			activate();
			return;
		}
	}

	/** Whether the block in front holds any fluid (a fluid filter is not supported, an item filter hides fluids). */
	private boolean hasObservedFluid() {
		if (!filtering.getFilter()
			.isEmpty())
			return false;
		BlockPos targetPos = worldPosition.relative(SmartObserverBlock.getTargetDirection(getBlockState()));
		if (!level.isLoaded(targetPos))
			return false;
		IFluidHandler tank = FluidHandlerLookup.get(level, targetPos, null);
		return tank != null && !tank.drain(Integer.MAX_VALUE, FluidAction.SIMULATE)
			.isEmpty();
	}

	public void activate() {
		activate(DEFAULT_DELAY);
	}

	public void activate(int ticks) {
		BlockState state = getBlockState();
		turnOffTicks = ticks;
		if (state.getValue(SmartObserverBlock.POWERED))
			return;
		level.setBlockAndUpdate(worldPosition, state.setValue(SmartObserverBlock.POWERED, true));
		level.updateNeighborsAt(worldPosition, state.getBlock());
	}

	private boolean isActive() {
		return true;
	}

	@Override
	public void write(ValueOutput compound) {
		compound.putInt("TurnOff", turnOffTicks);
		super.write(compound);
	}

	@Override
	protected void read(ValueInput compound) {
		super.read(compound);
		turnOffTicks = compound.getIntOr("TurnOff", 0);
	}

	@Override
	public void clearContent() {
		filtering.setFilter(ItemStack.EMPTY);
	}
}
