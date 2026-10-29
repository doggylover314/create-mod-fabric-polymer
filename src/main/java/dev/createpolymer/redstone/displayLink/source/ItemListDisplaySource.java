package dev.createpolymer.redstone.displayLink.source;

import java.util.stream.Stream;

import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.redstone.displayLink.DisplayLinkContext;
import dev.createpolymer.redstone.smartObserver.SmartObserverBlockEntity;
import dev.createpolymer.util.IntAttached;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ItemListDisplaySource extends ValueListDisplaySource {

	@Override
	protected Stream<IntAttached<MutableComponent>> provideEntries(DisplayLinkContext context, int maxRows) {
		BlockEntity sourceBE = context.getSourceBlockEntity();
		if (!(sourceBE instanceof SmartObserverBlockEntity cobe))
			return Stream.empty();

		InvManipulationBehaviour invManipulationBehaviour = cobe.getBehaviour(InvManipulationBehaviour.TYPE);
		FilteringBehaviour filteringBehaviour = cobe.getBehaviour(FilteringBehaviour.TYPE);
		IItemHandler handler = invManipulationBehaviour.getInventory();

		if (handler == null)
			return Stream.empty();

		return new CountedItemStackList(handler, filteringBehaviour).getTopNames(maxRows);
	}

	@Override
	protected String getTranslationKey() {
		return "list_items";
	}

	@Override
	protected boolean valueFirst() {
		return true;
	}

}
