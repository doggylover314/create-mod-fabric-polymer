package dev.createpolymer.redstone.displayLink.source;

import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.redstone.displayLink.DisplayLinkContext;
import dev.createpolymer.redstone.displayLink.target.DisplayTargetStats;
import dev.createpolymer.redstone.smartObserver.SmartObserverBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ItemCountDisplaySource extends NumericSingleLineDisplaySource {

	@Override
	protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
		BlockEntity sourceBE = context.getSourceBlockEntity();
		if (!(sourceBE instanceof SmartObserverBlockEntity cobe))
			return ZERO.copy();

		InvManipulationBehaviour invManipulationBehaviour = cobe.getBehaviour(InvManipulationBehaviour.TYPE);
		FilteringBehaviour filteringBehaviour = cobe.getBehaviour(FilteringBehaviour.TYPE);
		IItemHandler handler = invManipulationBehaviour.getInventory();

		if (handler == null)
			return ZERO.copy();

		int collected = 0;
		for (int i = 0; i < handler.getSlots(); i++) {
			ItemStack stack = handler.extractItem(i, handler.getSlotLimit(i), true);
			if (stack.isEmpty())
				continue;
			if (!filteringBehaviour.test(stack))
				continue;
			collected += stack.getCount();
		}

		return Component.literal(String.valueOf(collected));
	}

	@Override
	protected String getTranslationKey() {
		return "count_items";
	}

	@Override
	protected boolean allowsLabeling(DisplayLinkContext context) {
		return true;
	}

}
