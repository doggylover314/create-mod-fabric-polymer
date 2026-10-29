package dev.createpolymer.redstone.thresholdSwitch;

import dev.createpolymer.redstone.displayLink.DisplayLinkBlock;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.inventory.CapManipulationBehaviourBase.InterfaceProvider;
import dev.createpolymer.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.inventory.VersionedInventoryTrackerBehaviour;
import dev.createpolymer.foundation.fluid.FluidHandlerLookup;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.IFluidHandler;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.processing.recipe.ProcessingInventory;
import dev.createpolymer.redstone.DirectedDirectionalBlock;
import dev.createpolymer.redstone.FilteredDetectorFilterSlot;
import dev.createpolymer.util.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.ticks.TickPriority;

/**
 * Create's threshold switch (registered as "stockpile_switch"). Mod compatibility for storage drawers and similar
 * is not part of this port. Fluids are always counted unfiltered: fluid filters are not ported.
 */
public class ThresholdSwitchBlockEntity extends SmartBlockEntity implements Clearable {
	public int onWhenAbove;
	public int offWhenBelow;

	public int currentMinLevel;
	public int currentLevel;
	public int currentMaxLevel;
	public boolean inStacks;

	private boolean redstoneState;
	private boolean inverted;
	private boolean poweredAfterDelay;

	private FilteringBehaviour filtering;
	private InvManipulationBehaviour observedInventory;
	private VersionedInventoryTrackerBehaviour invVersionTracker;
	private @Nullable IFluidHandler observedTank;

	public ThresholdSwitchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		onWhenAbove = 128;
		offWhenBelow = 64;
		currentLevel = -1;
		redstoneState = false;
		inverted = false;
		poweredAfterDelay = false;
		setLazyTickRate(10);
	}

	@Override
	protected void read(ValueInput compound) {
		onWhenAbove = compound.getIntOr("OnAboveAmount", 128);
		offWhenBelow = compound.getIntOr("OffBelowAmount", 64);
		currentLevel = compound.getIntOr("CurrentAmount", -1);
		currentMinLevel = compound.getIntOr("CurrentMinAmount", 0);
		currentMaxLevel = compound.getIntOr("CurrentMaxAmount", 0);
		inStacks = compound.getBooleanOr("InStacks", false);
		redstoneState = compound.getBooleanOr("Powered", false);
		inverted = compound.getBooleanOr("Inverted", false);
		poweredAfterDelay = compound.getBooleanOr("PoweredAfterDelay", false);
		super.read(compound);
	}

	@Override
	public void write(ValueOutput compound) {
		compound.putInt("OnAboveAmount", onWhenAbove);
		compound.putInt("OffBelowAmount", offWhenBelow);
		compound.putBoolean("Inverted", inverted);
		compound.putInt("CurrentAmount", currentLevel);
		compound.putInt("CurrentMinAmount", currentMinLevel);
		compound.putInt("CurrentMaxAmount", currentMaxLevel);
		compound.putBoolean("InStacks", inStacks);
		compound.putBoolean("Powered", redstoneState);
		compound.putBoolean("PoweredAfterDelay", poweredAfterDelay);
		super.write(compound);
	}

	public int getMinLevel() {
		return currentMinLevel;
	}

	public int getStockLevel() {
		return currentLevel;
	}

	public int getMaxLevel() {
		return currentMaxLevel;
	}

	public void updateCurrentLevel() {
		boolean changed = false;
		int prevLevel = currentLevel;
		int prevMaxLevel = currentMaxLevel;

		BlockPos target = getTargetPos();
		BlockEntity targetBlockEntity = level.getBlockEntity(target);

		observedInventory.findNewCapability();
		observedTank = level.isLoaded(target) ? FluidHandlerLookup.get(level, target, null) : null;

		if (targetBlockEntity instanceof ThresholdSwitchObservable observable) {
			currentMinLevel = observable.getMinValue();
			currentLevel = observable.getCurrentValue();
			currentMaxLevel = observable.getMaxValue();

		} else if (observedInventory.hasInventory() || observedTank != null) {
			currentMinLevel = 0;
			currentLevel = 0;
			currentMaxLevel = 0;

			if (observedInventory.hasInventory()) {

				// Item inventory
				IItemHandler inv = observedInventory.getInventory();
				if (invVersionTracker.stillWaiting(inv)) {
					currentLevel = prevLevel;
					currentMaxLevel = prevMaxLevel;

				} else {
					invVersionTracker.awaitNewVersion(inv);
					for (int slot = 0; slot < inv.getSlots(); slot++) {
						ItemStack stackInSlot = inv.getStackInSlot(slot);

						long space = Math.min(stackInSlot.getOrDefault(DataComponents.MAX_STACK_SIZE, 64),
							inv.getSlotLimit(slot));

						int count = stackInSlot.getCount();
						if (space == 0)
							continue;

						currentMaxLevel += space;
						if (filtering.test(stackInSlot))
							currentLevel += count;
					}
				}
			}

			if (observedTank != null) {
				// Fluid inventory
				IFluidHandler tank = observedTank;
				for (int slot = 0; slot < tank.getTanks(); slot++) {
					FluidStack stackInSlot = tank.getFluidInTank(slot);
					int space = tank.getTankCapacity(slot);
					int count = stackInSlot.getAmount();
					if (space == 0)
						continue;

					currentMaxLevel += space;
					if (filtering.getFilter()
						.isEmpty())
						currentLevel += count;
				}
			}

		} else {
			// No compatible inventories found
			currentMinLevel = -1;
			currentMaxLevel = -1;
			if (currentLevel == -1)
				return;

			level.setBlock(worldPosition, getBlockState().setValue(ThresholdSwitchBlock.LEVEL, 0), Block.UPDATE_ALL);
			currentLevel = -1;
			redstoneState = false;
			setChanged();
			sendData();
			scheduleBlockTick();
			return;
		}

		currentLevel = Mth.clamp(currentLevel, currentMinLevel, currentMaxLevel);
		changed = currentLevel != prevLevel;

		boolean previouslyPowered = redstoneState;
		if (redstoneState && currentLevel <= offWhenBelow)
			redstoneState = false;
		else if (!redstoneState && currentLevel >= onWhenAbove)
			redstoneState = true;
		boolean update = previouslyPowered != redstoneState;

		int displayLevel = 0;
		float normedLevel = (float) (currentLevel - currentMinLevel) / (currentMaxLevel - currentMinLevel);
		if (currentLevel > 0)
			displayLevel = (int) (1 + normedLevel * 4);
		level.setBlock(worldPosition, getBlockState().setValue(ThresholdSwitchBlock.LEVEL, displayLevel),
			update ? 3 : 2);

		if (update)
			scheduleBlockTick();

		if (changed || update) {
			DisplayLinkBlock.notifyGatherers(level, worldPosition);
			notifyUpdate();
		}
	}

	private boolean isSuitableInventory(BlockEntity be) {
		return be != null && !(ItemHandlerLookup.get(level, be.getBlockPos(), null) instanceof ProcessingInventory);
	}

	private BlockPos getTargetPos() {
		return worldPosition.relative(ThresholdSwitchBlock.getTargetDirection(getBlockState()));
	}

	public ItemStack getDisplayItemForScreen() {
		BlockPos target = getTargetPos();
		return new ItemStack(level.getBlockState(target)
			.getBlock());
	}

	public static enum ThresholdType {
		UNSUPPORTED, ITEM, FLUID, CUSTOM;
	}

	public MutableComponent format(int value, boolean stacks) {
		ThresholdType type = getTypeOfCurrentTarget();
		if (type == ThresholdType.CUSTOM)
			if (level.getBlockEntity(getTargetPos()) instanceof ThresholdSwitchObservable tso)
				return tso.format(value);

		String suffix = type == ThresholdType.ITEM
			? stacks ? "create.schedule.condition.threshold.stacks" : "create.schedule.condition.threshold.items"
			: "create.schedule.condition.threshold.buckets";
		return Component.literal(value + " ")
			.append(Component.translatable(suffix));
	}

	public ThresholdType getTypeOfCurrentTarget() {
		if (observedInventory.hasInventory())
			return ThresholdType.ITEM;
		if (observedTank != null)
			return ThresholdType.FLUID;
		if (level.getBlockEntity(getTargetPos()) instanceof ThresholdSwitchObservable)
			return ThresholdType.CUSTOM;
		return ThresholdType.UNSUPPORTED;
	}

	protected void scheduleBlockTick() {
		Block block = getBlockState().getBlock();
		if (!level.getBlockTicks()
			.willTickThisTick(worldPosition, block))
			level.scheduleTick(worldPosition, block, 2, TickPriority.NORMAL);
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		if (level.isClientSide())
			return;
		updateCurrentLevel();
	}

	@Override
	public void clearContent() {
		filtering.setFilter(ItemStack.EMPTY);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		behaviours
			.add(filtering = new FilteringBehaviour(this, new FilteredDetectorFilterSlot(true)).withCallback($ -> {
				this.updateCurrentLevel();
				invVersionTracker.reset();
			}));

		behaviours.add(invVersionTracker = new VersionedInventoryTrackerBehaviour(this));

		InterfaceProvider towardBlockFacing =
			(w, p, s) -> new BlockFace(p, DirectedDirectionalBlock.getTargetDirection(s));

		behaviours.add(observedInventory = new InvManipulationBehaviour(this, towardBlockFacing).bypassSidedness()
			.withFilter(this::isSuitableInventory));
	}

	public float getLevelForDisplay() {
		return currentLevel == -1 ? 0 : currentLevel;
	}

	public boolean getState() {
		return redstoneState;
	}

	public boolean shouldBePowered() {
		return inverted != redstoneState;
	}

	public void updatePowerAfterDelay() {
		poweredAfterDelay = shouldBePowered();
		level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
		setChanged();
		sendData();
	}

	public boolean isPowered() {
		return poweredAfterDelay;
	}

	public boolean isInverted() {
		return inverted;
	}

	public void setInverted(boolean inverted) {
		if (inverted == this.inverted)
			return;
		this.inverted = inverted;
		updatePowerAfterDelay();
	}
}
