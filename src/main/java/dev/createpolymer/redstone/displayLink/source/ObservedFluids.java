package dev.createpolymer.redstone.displayLink.source;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.foundation.fluid.FluidHandlerLookup;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.IFluidHandler;
import dev.createpolymer.redstone.smartObserver.SmartObserverBlock;
import dev.createpolymer.redstone.smartObserver.SmartObserverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/**
 * What Create's TankManipulationBehaviour.OBSERVE gives a smart observer: the fluid handler of the block in front of
 * it. This port's smart observer has no such behaviour, so the handler is looked up directly.
 */
final class ObservedFluids {
	private ObservedFluids() {}

	@Nullable
	static IFluidHandler getHandler(SmartObserverBlockEntity observer) {
		BlockPos targetPos = observer.getBlockPos()
			.relative(SmartObserverBlock.getTargetDirection(observer.getBlockState()));
		if (!observer.getLevel()
			.isLoaded(targetPos))
			return null;
		return FluidHandlerLookup.get(observer.getLevel(), targetPos, null);
	}

	/** A fluid passes a filter when the filter is empty or accepts the bucket of the fluid. */
	static boolean test(FilteringBehaviour filter, FluidStack stack) {
		if (filter.getFilter()
			.isEmpty())
			return true;
		return filter.test(new ItemStack(stack.getFluid()
			.getBucket()));
	}
}
