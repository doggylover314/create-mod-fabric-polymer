package dev.createpolymer.foundation.fluid;

public interface IFluidTank {
	FluidStack getFluid();

	int getFluidAmount();

	int getCapacity();

	boolean isFluidValid(FluidStack stack);

	int fill(FluidStack resource, IFluidHandler.FluidAction action);

	FluidStack drain(int maxDrain, IFluidHandler.FluidAction action);

	FluidStack drain(FluidStack resource, IFluidHandler.FluidAction action);
}
