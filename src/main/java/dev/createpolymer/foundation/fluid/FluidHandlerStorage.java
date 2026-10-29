package dev.createpolymer.foundation.fluid;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import dev.createpolymer.foundation.fluid.IFluidHandler.FluidAction;

/**
 * Presents an {@link IFluidHandler} as a Fabric fluid storage. Operations are simulated during the transaction and
 * replayed when the outermost transaction commits; an aborted transaction drops them. Because a simulation cannot
 * see a pending operation, at most one fill and one drain are pending at a time.
 */
public class FluidHandlerStorage extends SnapshotParticipant<FluidHandlerStorage.Pending> implements Storage<FluidVariant> {
	private static final long MB = StorageFluidHandler.DROPLETS_PER_MB;

	record Pending(FluidStack fill, FluidStack drain) {
		static final Pending NONE = new Pending(FluidStack.EMPTY, FluidStack.EMPTY);
	}

	private final IFluidHandler handler;
	private Pending pending = Pending.NONE;

	@Override
	protected Pending createSnapshot() {
		return pending;
	}

	@Override
	protected void readSnapshot(Pending snapshot) {
		pending = snapshot;
	}

	@Override
	protected void onFinalCommit() {
		Pending op = pending;
		pending = Pending.NONE;
		if (!op.fill().isEmpty())
			handler.fill(op.fill(), FluidAction.EXECUTE);
		if (!op.drain().isEmpty())
			handler.drain(op.drain(), FluidAction.EXECUTE);
	}

	public FluidHandlerStorage(IFluidHandler handler) {
		this.handler = handler;
	}

	public IFluidHandler getHandler() {
		return handler;
	}

	@Override
	public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
		StoragePreconditions.notBlankNotNegative(resource, maxAmount);
		int mb = (int) Math.min(Integer.MAX_VALUE, maxAmount / MB);
		if (mb <= 0)
			return 0;
		if (!pending.fill().isEmpty())
			return 0;
		FluidStack stack = FluidStack.fromVariant(resource, mb);
		int filled = handler.fill(stack, FluidAction.SIMULATE);
		if (filled <= 0)
			return 0;
		updateSnapshots(transaction);
		pending = new Pending(stack.copyWithAmount(filled), pending.drain());
		return filled * MB;
	}

	@Override
	public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
		StoragePreconditions.notBlankNotNegative(resource, maxAmount);
		int mb = (int) Math.min(Integer.MAX_VALUE, maxAmount / MB);
		if (mb <= 0)
			return 0;
		if (!pending.drain().isEmpty())
			return 0;
		FluidStack request = FluidStack.fromVariant(resource, mb);
		FluidStack drained = handler.drain(request, FluidAction.SIMULATE);
		if (drained.isEmpty())
			return 0;
		updateSnapshots(transaction);
		pending = new Pending(pending.fill(), drained.copy());
		return drained.getAmount() * MB;
	}

	@Override
	public Iterator<StorageView<FluidVariant>> iterator() {
		List<StorageView<FluidVariant>> views = new ArrayList<>();
		for (int i = 0; i < handler.getTanks(); i++)
			views.add(new TankView(i));
		return views.iterator();
	}

	private class TankView implements StorageView<FluidVariant> {
		private final int tank;

		TankView(int tank) {
			this.tank = tank;
		}

		@Override
		public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
			if (!resource.equals(getResource()))
				return 0;
			return FluidHandlerStorage.this.extract(resource, maxAmount, transaction);
		}

		@Override
		public boolean isResourceBlank() {
			return handler.getFluidInTank(tank).isEmpty();
		}

		@Override
		public FluidVariant getResource() {
			return handler.getFluidInTank(tank).toVariant();
		}

		@Override
		public long getAmount() {
			return handler.getFluidInTank(tank).getAmount() * MB;
		}

		@Override
		public long getCapacity() {
			return handler.getTankCapacity(tank) * MB;
		}
	}
}
