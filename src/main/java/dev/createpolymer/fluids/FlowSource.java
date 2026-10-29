package dev.createpolymer.fluids;

import java.lang.ref.WeakReference;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.fluid.FluidHandlerLookup;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.IFluidHandler;
import dev.createpolymer.foundation.fluid.IFluidHandler.FluidAction;
import dev.createpolymer.util.BlockFace;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public abstract class FlowSource {

	private static final ICapabilityProvider<IFluidHandler> EMPTY = null;

	BlockFace location;

	public FlowSource(BlockFace location) {
		this.location = location;
	}

	public FluidStack provideFluid(Predicate<FluidStack> extractionPredicate) {
		@Nullable ICapabilityProvider<IFluidHandler> tankCache = provideHandler();
		if (tankCache == null)
			return FluidStack.EMPTY;
		IFluidHandler tank = tankCache.getCapability();
		if (tank == null)
			return FluidStack.EMPTY;
		FluidStack immediateFluid = tank.drain(1, FluidAction.SIMULATE);
		if (extractionPredicate.test(immediateFluid))
			return immediateFluid;

		for (int i = 0; i < tank.getTanks(); i++) {
			FluidStack contained = tank.getFluidInTank(i);
			if (contained.isEmpty())
				continue;
			if (!extractionPredicate.test(contained))
				continue;
			FluidStack toExtract = contained.copy();
			toExtract.setAmount(1);
			return tank.drain(toExtract, FluidAction.SIMULATE);
		}

		return FluidStack.EMPTY;
	}

	// Layer III. PFIs need active attention to prevent them from disengaging early
	public void keepAlive() {}

	public abstract boolean isEndpoint();

	public void manageSource(Level world, BlockEntity networkBE) {
	}

	public void whileFlowPresent(Level world, boolean pulling) {}

	public @Nullable ICapabilityProvider<IFluidHandler> provideHandler() {
		return EMPTY;
	}

	public static class FluidHandler extends FlowSource {
		@Nullable
		ICapabilityProvider<IFluidHandler> fluidHandlerCache;

		public FluidHandler(BlockFace location) {
			super(location);
			fluidHandlerCache = EMPTY;
		}

		public void manageSource(Level level, BlockEntity networkBE) {
			if (fluidHandlerCache == null) {
				BlockEntity blockEntity = level.getBlockEntity(location.getConnectedPos());
				if (blockEntity != null) {
					// Create caches the capability and drops the cache when it is invalidated. Here the handler is
					// looked up once per tick, which keeps the wrapper of a foreign fluid storage the same object
					// for all the transfers of a tick (FluidNetwork tracks pending fills by handler identity).
					fluidHandlerCache = new ICapabilityProvider<>() {
						private long lookedUpAt = Long.MIN_VALUE;
						private @Nullable IFluidHandler handler;

						@Override
						public @Nullable IFluidHandler getCapability() {
							if (networkBE.isRemoved())
								return null;
							long time = level.getGameTime();
							if (time != lookedUpAt) {
								lookedUpAt = time;
								handler = FluidHandlerLookup.get(level, location.getConnectedPos(), location.getOppositeFace());
							}
							return handler;
						}
					};
				}
			}
		}

		@Override
		@Nullable
		public ICapabilityProvider<IFluidHandler> provideHandler() {
			return fluidHandlerCache;
		}

		@Override
		public boolean isEndpoint() {
			return true;
		}
	}

	public static class OtherPipe extends FlowSource {
		WeakReference<FluidTransportBehaviour> cached;

		public OtherPipe(BlockFace location) {
			super(location);
		}

		@Override
		public void manageSource(Level world, BlockEntity networkBE) {
			if (cached != null && cached.get() != null && !cached.get().blockEntity.isRemoved())
				return;
			cached = null;
			FluidTransportBehaviour fluidTransportBehaviour =
				BlockEntityBehaviour.get(world, location.getConnectedPos(), FluidTransportBehaviour.TYPE);
			if (fluidTransportBehaviour != null)
				cached = new WeakReference<>(fluidTransportBehaviour);
		}

		@Override
		public FluidStack provideFluid(Predicate<FluidStack> extractionPredicate) {
			if (cached == null || cached.get() == null)
				return FluidStack.EMPTY;
			FluidTransportBehaviour behaviour = cached.get();
			FluidStack providedOutwardFluid = behaviour.getProvidedOutwardFluid(location.getOppositeFace());
			return extractionPredicate.test(providedOutwardFluid) ? providedOutwardFluid : FluidStack.EMPTY;
		}

		@Override
		public boolean isEndpoint() {
			return false;
		}

	}

	public static class Blocked extends FlowSource {

		public Blocked(BlockFace location) {
			super(location);
		}

		@Override
		public boolean isEndpoint() {
			return false;
		}

	}

}
