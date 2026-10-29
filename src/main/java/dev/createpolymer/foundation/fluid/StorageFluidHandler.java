package dev.createpolymer.foundation.fluid;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;

/**
 * Presents a Fabric fluid {@link Storage} as an {@link IFluidHandler}. Each view is one tank. Amounts are converted
 * between millibuckets and droplets ({@link #DROPLETS_PER_MB}); partial millibuckets are ignored.
 */
public class StorageFluidHandler implements IFluidHandler {
	public static final long DROPLETS_PER_MB = 81;

	protected final Storage<FluidVariant> storage;

	public StorageFluidHandler(Storage<FluidVariant> storage) {
		this.storage = storage;
	}

	public Storage<FluidVariant> getStorage() {
		return storage;
	}

	@SuppressWarnings("deprecation")
	static Transaction open() {
		return Transaction.openNested(Transaction.isOpen() ? Transaction.getCurrentUnsafe() : null);
	}

	static int toMb(long droplets) {
		return (int) Math.min(Integer.MAX_VALUE, droplets / DROPLETS_PER_MB);
	}

	private List<StorageView<FluidVariant>> views() {
		List<StorageView<FluidVariant>> list = new ArrayList<>();
		for (StorageView<FluidVariant> view : storage)
			list.add(view);
		return list;
	}

	@Override
	public int getTanks() {
		return Math.max(1, views().size());
	}

	@Override
	public FluidStack getFluidInTank(int tank) {
		List<StorageView<FluidVariant>> views = views();
		if (tank < 0 || tank >= views.size())
			return FluidStack.EMPTY;
		StorageView<FluidVariant> view = views.get(tank);
		if (view.isResourceBlank())
			return FluidStack.EMPTY;
		return FluidStack.fromVariant(view.getResource(), toMb(view.getAmount()));
	}

	@Override
	public int getTankCapacity(int tank) {
		List<StorageView<FluidVariant>> views = views();
		if (tank < 0 || tank >= views.size())
			return 0;
		return toMb(views.get(tank).getCapacity());
	}

	@Override
	public boolean isFluidValid(int tank, FluidStack stack) {
		return fill(stack.copyWithAmount(1), FluidAction.SIMULATE) > 0;
	}

	@Override
	public int fill(FluidStack resource, FluidAction action) {
		if (resource.isEmpty())
			return 0;
		long inserted;
		try (Transaction t = open()) {
			inserted = storage.insert(resource.toVariant(), resource.getAmount() * DROPLETS_PER_MB, t);
			inserted -= inserted % DROPLETS_PER_MB;
			if (action.execute() && inserted > 0) {
				// Re-run with the whole-mB amount so we never leave a partial millibucket behind.
				t.abort();
				try (Transaction t2 = open()) {
					long exact = storage.insert(resource.toVariant(), inserted, t2);
					t2.commit();
					return toMb(exact);
				}
			}
		}
		return toMb(inserted);
	}

	@Override
	public FluidStack drain(FluidStack resource, FluidAction action) {
		if (resource.isEmpty())
			return FluidStack.EMPTY;
		long extracted;
		try (Transaction t = open()) {
			extracted = storage.extract(resource.toVariant(), resource.getAmount() * DROPLETS_PER_MB, t);
			extracted -= extracted % DROPLETS_PER_MB;
			if (action.execute() && extracted > 0) {
				t.abort();
				try (Transaction t2 = open()) {
					extracted = storage.extract(resource.toVariant(), extracted, t2);
					t2.commit();
				}
			}
		}
		return resource.copyWithAmount(toMb(extracted));
	}

	@Override
	public FluidStack drain(int maxDrain, FluidAction action) {
		for (StorageView<FluidVariant> view : views()) {
			if (view.isResourceBlank() || view.getAmount() < DROPLETS_PER_MB)
				continue;
			FluidStack request = FluidStack.fromVariant(view.getResource(), maxDrain);
			FluidStack drained = drain(request, action);
			if (!drained.isEmpty())
				return drained;
		}
		return FluidStack.EMPTY;
	}
}
