package dev.createpolymer.contraptions.minecart;

import java.util.concurrent.atomic.AtomicInteger;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.api.contraption.storage.fluid.MountedFluidStorageWrapper;
import dev.createpolymer.api.contraption.storage.item.MountedItemStorageWrapper;
import dev.createpolymer.contraptions.Contraption;
import dev.createpolymer.contraptions.MountedStorageManager;
import dev.createpolymer.foundation.fluid.FluidStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * The cargo of a train carriage (Create's TrainCargoManager): the mounted storages of the carriage, which the
 * train keeps even when the carriage entity is not loaded. It counts changes so observers can tell when the cargo
 * moved.
 */
public class TrainCargoManager extends MountedStorageManager {

	int ticksSinceLastExchange;
	AtomicInteger version;

	public TrainCargoManager() {
		version = new AtomicInteger();
		ticksSinceLastExchange = 0;
	}

	@Override
	public void initialize() {
		super.initialize();
		this.items = new CargoInvWrapper(this.items);
		this.allItems = this.items;
		if (this.fuelItems != null) {
			this.fuelItems = new CargoInvWrapper(this.fuelItems);
		}
		this.fluids = new CargoTankWrapper(this.fluids);
	}

	@Override
	public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(nbt, registries, clientPacket);
		nbt.putInt("TicksSinceLastExchange", ticksSinceLastExchange);
	}

	@Override
	public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket,
		@Nullable Contraption contraption) {
		super.read(nbt, registries, clientPacket, contraption);
		ticksSinceLastExchange = nbt.getIntOr("TicksSinceLastExchange", 0);
	}

	public void resetIdleCargoTracker() {
		ticksSinceLastExchange = 0;
	}

	public void tickIdleCargoTracker() {
		ticksSinceLastExchange++;
	}

	public int getTicksSinceLastExchange() {
		return ticksSinceLastExchange;
	}

	public int getVersion() {
		return version.get();
	}

	void changeDetected() {
		version.incrementAndGet();
		resetIdleCargoTracker();
	}

	class CargoInvWrapper extends MountedItemStorageWrapper {
		CargoInvWrapper(MountedItemStorageWrapper wrapped) {
			super(wrapped.storages);
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			ItemStack remainder = super.insertItem(slot, stack, simulate);
			if (!simulate && stack.getCount() != remainder.getCount())
				changeDetected();
			return remainder;
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			ItemStack extracted = super.extractItem(slot, amount, simulate);
			if (!simulate && !extracted.isEmpty())
				changeDetected();
			return extracted;
		}

		@Override
		public void setStackInSlot(int slot, ItemStack stack) {
			if (!stack.equals(getStackInSlot(slot)))
				changeDetected();
			super.setStackInSlot(slot, stack);
		}

	}

	class CargoTankWrapper extends MountedFluidStorageWrapper {
		CargoTankWrapper(MountedFluidStorageWrapper wrapped) {
			super(wrapped.storages);
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			int filled = super.fill(resource, action);
			if (action.execute() && filled > 0)
				changeDetected();
			return filled;
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			FluidStack drained = super.drain(resource, action);
			if (action.execute() && !drained.isEmpty())
				changeDetected();
			return drained;
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			FluidStack drained = super.drain(maxDrain, action);
			if (action.execute() && !drained.isEmpty())
				changeDetected();
			return drained;
		}

	}

}
