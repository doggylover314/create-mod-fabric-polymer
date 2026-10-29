package dev.createpolymer.redstone.displayLink.source;

import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.kinetics.chainConveyor.ChainConveyorBlockEntity;
import dev.createpolymer.kinetics.chainConveyor.ChainConveyorPackage;
import dev.createpolymer.logistics.box.PackageItem;
import dev.createpolymer.redstone.displayLink.DisplayLinkContext;
import dev.createpolymer.redstone.displayLink.target.DisplayTargetStats;
import dev.createpolymer.redstone.smartObserver.SmartObserverBlock;
import dev.createpolymer.redstone.smartObserver.SmartObserverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class PackageAddressDisplaySource extends SingleLineDisplaySource {

	@Override
	protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
		BlockEntity sourceBE = context.getSourceBlockEntity();
		if (!(sourceBE instanceof SmartObserverBlockEntity cobe))
			return EMPTY_LINE;

		InvManipulationBehaviour invManipulationBehaviour = cobe.getBehaviour(InvManipulationBehaviour.TYPE);
		FilteringBehaviour filteringBehaviour = cobe.getBehaviour(FilteringBehaviour.TYPE);
		IItemHandler handler = invManipulationBehaviour.getInventory();

		if (handler == null) {
			BlockPos targetPos = cobe.getBlockPos().relative(SmartObserverBlock.getTargetDirection(cobe.getBlockState()));

			if (context.level().getBlockEntity(targetPos) instanceof ChainConveyorBlockEntity ccbe)
				for (ChainConveyorPackage box : ccbe.getLoopingPackages())
					if (filteringBehaviour.test(box.item))
						return Component.literal(PackageItem.getAddress(box.item));

			return EMPTY_LINE;
		}

		for (int i = 0; i < handler.getSlots(); i++) {
			ItemStack stack = handler.getStackInSlot(i);
			if (PackageItem.isPackage(stack) && filteringBehaviour.test(stack))
				return Component.literal(PackageItem.getAddress(stack));
		}

		return EMPTY_LINE;
	}

	@Override
	protected String getTranslationKey() {
		return "read_package_address";
	}

	@Override
	protected boolean allowsLabeling(DisplayLinkContext context) {
		return true;
	}

	@Override
	protected String getFlapDisplayLayoutName(DisplayLinkContext context) {
		return "Default";
	}

}
