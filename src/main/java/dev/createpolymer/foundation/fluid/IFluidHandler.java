package dev.createpolymer.foundation.fluid;

/** Same contract as NeoForge's IFluidHandler. Amounts in millibuckets. */
public interface IFluidHandler {
	enum FluidAction {
		EXECUTE, SIMULATE;

		public boolean execute() {
			return this == EXECUTE;
		}

		public boolean simulate() {
			return this == SIMULATE;
		}
	}

	int getTanks();

	/** The returned stack must not be modified. */
	FluidStack getFluidInTank(int tank);

	int getTankCapacity(int tank);

	boolean isFluidValid(int tank, FluidStack stack);

	/** @return amount filled (or that would be filled). */
	int fill(FluidStack resource, FluidAction action);

	/** Drains a matching fluid. @return the drained stack. */
	FluidStack drain(FluidStack resource, FluidAction action);

	/** Drains any fluid. @return the drained stack. */
	FluidStack drain(int maxDrain, FluidAction action);
}
