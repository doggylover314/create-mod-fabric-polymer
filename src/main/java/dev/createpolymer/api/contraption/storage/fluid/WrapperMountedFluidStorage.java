package dev.createpolymer.api.contraption.storage.fluid;

import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.IFluidHandler;

/**
 * Partial implementation of a MountedFluidStorage that wraps a fluid handler.
 */
public abstract class WrapperMountedFluidStorage<T extends IFluidHandler> extends MountedFluidStorage {
	protected final T wrapped;

	protected WrapperMountedFluidStorage(MountedFluidStorageType<?> type, T wrapped) {
		super(type);
		this.wrapped = wrapped;
	}

	@Override
	public int getTanks() {
		return this.wrapped.getTanks();
	}

	@Override
	public FluidStack getFluidInTank(int tank) {
		return this.wrapped.getFluidInTank(tank);
	}

	@Override
	public int getTankCapacity(int tank) {
		return this.wrapped.getTankCapacity(tank);
	}

	@Override
	public boolean isFluidValid(int tank, FluidStack stack) {
		return this.wrapped.isFluidValid(tank, stack);
	}

	@Override
	public int fill(FluidStack resource, FluidAction action) {
		return this.wrapped.fill(resource, action);
	}

	@Override
	public FluidStack drain(FluidStack resource, FluidAction action) {
		return this.wrapped.drain(resource, action);
	}

	@Override
	public FluidStack drain(int maxDrain, FluidAction action) {
		return this.wrapped.drain(maxDrain, action);
	}
}
