package dev.createpolymer.redstone.displayLink.source;

import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.IFluidHandler;
import dev.createpolymer.redstone.displayLink.DisplayLinkContext;
import dev.createpolymer.redstone.displayLink.target.DisplayTargetStats;
import dev.createpolymer.redstone.smartObserver.SmartObserverBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

public class FluidAmountDisplaySource extends SingleLineDisplaySource {

	@Override
	protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
		BlockEntity sourceBE = context.getSourceBlockEntity();
		if (!(sourceBE instanceof SmartObserverBlockEntity cobe))
			return EMPTY_LINE;

		FilteringBehaviour filteringBehaviour = cobe.getBehaviour(FilteringBehaviour.TYPE);
		IFluidHandler handler = ObservedFluids.getHandler(cobe);

		if (handler == null)
			return EMPTY_LINE;

		long collected = 0;
		for (int i = 0; i < handler.getTanks(); i++) {
			FluidStack stack = handler.getFluidInTank(i);
			if (stack.isEmpty())
				continue;
			if (!ObservedFluids.test(filteringBehaviour, stack))
				continue;
			collected += stack.getAmount();
		}

		return Component.literal(FluidFormatter.asString(collected, false));
	}

	@Override
	protected String getTranslationKey() {
		return "fluid_amount";
	}

	@Override
	protected boolean allowsLabeling(DisplayLinkContext context) {
		return true;
	}
}
