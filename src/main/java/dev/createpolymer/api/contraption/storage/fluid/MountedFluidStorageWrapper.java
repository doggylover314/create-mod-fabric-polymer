package dev.createpolymer.api.contraption.storage.fluid;

import com.google.common.collect.ImmutableMap;

import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.IFluidHandler;
import net.minecraft.core.BlockPos;

/**
 * Wrapper around many MountedFluidStorages, providing access to all of them as one storage.
 * They can still be accessed individually through the map. (Create extends its CombinedTankWrapper here.)
 */
public class MountedFluidStorageWrapper implements IFluidHandler {
	public final ImmutableMap<BlockPos, MountedFluidStorage> storages;
	private final IFluidHandler[] handlers;
	private final int[] baseIndex;

	public MountedFluidStorageWrapper(ImmutableMap<BlockPos, MountedFluidStorage> storages) {
		this.storages = storages;
		this.handlers = storages.values().toArray(IFluidHandler[]::new);
		this.baseIndex = new int[handlers.length];
		int index = 0;
		for (int i = 0; i < handlers.length; i++) {
			index += handlers[i].getTanks();
			baseIndex[i] = index;
		}
	}

	private int indexForTank(int tank) {
		if (tank < 0)
			return -1;
		for (int i = 0; i < baseIndex.length; i++)
			if (tank < baseIndex[i])
				return i;
		return -1;
	}

	private int tankInHandler(int tank, int index) {
		return index == 0 ? tank : tank - baseIndex[index - 1];
	}

	@Override
	public int getTanks() {
		return baseIndex.length == 0 ? 0 : baseIndex[baseIndex.length - 1];
	}

	@Override
	public FluidStack getFluidInTank(int tank) {
		int index = indexForTank(tank);
		return index < 0 ? FluidStack.EMPTY : handlers[index].getFluidInTank(tankInHandler(tank, index));
	}

	@Override
	public int getTankCapacity(int tank) {
		int index = indexForTank(tank);
		return index < 0 ? 0 : handlers[index].getTankCapacity(tankInHandler(tank, index));
	}

	@Override
	public boolean isFluidValid(int tank, FluidStack stack) {
		int index = indexForTank(tank);
		return index >= 0 && handlers[index].isFluidValid(tankInHandler(tank, index), stack);
	}

	@Override
	public int fill(FluidStack resource, FluidAction action) {
		if (resource.isEmpty())
			return 0;
		int filled = 0;
		FluidStack remaining = resource.copy();
		for (IFluidHandler handler : handlers) {
			int amount = handler.fill(remaining, action);
			filled += amount;
			remaining.shrink(amount);
			if (remaining.isEmpty())
				break;
		}
		return filled;
	}

	@Override
	public FluidStack drain(FluidStack resource, FluidAction action) {
		if (resource.isEmpty())
			return FluidStack.EMPTY;
		FluidStack drained = FluidStack.EMPTY;
		FluidStack remaining = resource.copy();
		for (IFluidHandler handler : handlers) {
			FluidStack part = handler.drain(remaining, action);
			if (part.isEmpty())
				continue;
			if (drained.isEmpty())
				drained = part.copy();
			else
				drained.grow(part.getAmount());
			remaining.shrink(part.getAmount());
			if (remaining.isEmpty())
				break;
		}
		return drained;
	}

	@Override
	public FluidStack drain(int maxDrain, FluidAction action) {
		FluidStack drained = FluidStack.EMPTY;
		int left = maxDrain;
		for (IFluidHandler handler : handlers) {
			FluidStack part = drained.isEmpty() ? handler.drain(left, action) : handler.drain(drained.copyWithAmount(left), action);
			if (part.isEmpty())
				continue;
			if (drained.isEmpty())
				drained = part.copy();
			else
				drained.grow(part.getAmount());
			left -= part.getAmount();
			if (left <= 0)
				break;
		}
		return drained;
	}
}
