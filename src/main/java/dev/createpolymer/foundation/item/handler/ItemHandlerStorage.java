package dev.createpolymer.foundation.item.handler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.item.ItemStack;

/**
 * Presents an {@link IItemHandler} as a Fabric item storage, so hoppers and other mods can use Create's inventories.
 * <p>
 * Modifiable handlers apply changes immediately and roll back to a snapshot of every slot if the transaction aborts.
 * Other handlers (whose insert/extract can have side effects we cannot undo, e.g. putting an item on a belt) only
 * simulate during the transaction and replay the operation when the outermost transaction commits. An aborted
 * (nested) transaction drops its pending operation. Because a simulation cannot see a pending operation, each such
 * slot holds at most one at a time.
 */
public class ItemHandlerStorage extends SnapshotParticipant<ItemStack[]> implements SlottedStorage<ItemVariant> {
	private final IItemHandler handler;
	private final IItemHandlerModifiable modifiable;
	private final List<Slot> slots = new ArrayList<>();

	public ItemHandlerStorage(IItemHandler handler) {
		this.handler = handler;
		this.modifiable = handler instanceof IItemHandlerModifiable m ? m : null;
	}

	public IItemHandler getHandler() {
		return handler;
	}

	@Override
	public int getSlotCount() {
		return handler.getSlots();
	}

	@Override
	public SingleSlotStorage<ItemVariant> getSlot(int slot) {
		while (slots.size() <= slot)
			slots.add(new Slot(slots.size()));
		return slots.get(slot);
	}

	@Override
	public Iterator<StorageView<ItemVariant>> iterator() {
		List<StorageView<ItemVariant>> views = new ArrayList<>();
		for (int i = 0; i < getSlotCount(); i++)
			views.add(getSlot(i));
		return views.iterator();
	}

	@Override
	public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
		StoragePreconditions.notBlankNotNegative(resource, maxAmount);
		long inserted = 0;
		for (int i = 0; i < getSlotCount() && inserted < maxAmount; i++)
			inserted += getSlot(i).insert(resource, maxAmount - inserted, transaction);
		return inserted;
	}

	@Override
	public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
		StoragePreconditions.notBlankNotNegative(resource, maxAmount);
		long extracted = 0;
		for (int i = 0; i < getSlotCount() && extracted < maxAmount; i++)
			extracted += getSlot(i).extract(resource, maxAmount - extracted, transaction);
		return extracted;
	}

	@Override
	protected ItemStack[] createSnapshot() {
		ItemStack[] copy = new ItemStack[handler.getSlots()];
		for (int i = 0; i < copy.length; i++)
			copy[i] = handler.getStackInSlot(i).copy();
		return copy;
	}

	@Override
	protected void readSnapshot(ItemStack[] snapshot) {
		for (int i = 0; i < snapshot.length && i < modifiable.getSlots(); i++)
			if (!ItemStack.matches(snapshot[i], modifiable.getStackInSlot(i)))
				modifiable.setStackInSlot(i, snapshot[i]);
	}

	/** A deferred operation on a non-modifiable handler, replayed when the outermost transaction commits. */
	private record Pending(ItemStack insert, int extract) {
		static final Pending NONE = new Pending(ItemStack.EMPTY, 0);
	}

	private class Slot extends SnapshotParticipant<Pending> implements SingleSlotStorage<ItemVariant> {
		private final int index;
		private Pending pending = Pending.NONE;

		Slot(int index) {
			this.index = index;
		}

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
			if (index >= handler.getSlots())
				return;
			if (!op.insert().isEmpty())
				handler.insertItem(index, op.insert(), false);
			if (op.extract() > 0)
				handler.extractItem(index, op.extract(), false);
		}

		@Override
		public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
			StoragePreconditions.notBlankNotNegative(resource, maxAmount);
			if (index >= handler.getSlots())
				return 0;
			ItemStack stack = resource.toStack((int) Math.min(maxAmount, resource.toStack().getMaxStackSize()));
			if (modifiable != null) {
				ItemHandlerStorage.this.updateSnapshots(transaction);
				ItemStack remainder = handler.insertItem(index, stack, false);
				return stack.getCount() - remainder.getCount();
			}
			// Simulation cannot see an earlier pending operation, so allow one per slot at a time.
			if (pending != Pending.NONE)
				return 0;
			int amount = stack.getCount() - handler.insertItem(index, stack, true).getCount();
			if (amount <= 0)
				return 0;
			updateSnapshots(transaction);
			pending = new Pending(stack.copyWithCount(amount), 0);
			return amount;
		}

		@Override
		public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
			StoragePreconditions.notBlankNotNegative(resource, maxAmount);
			if (index >= handler.getSlots() || !resource.matches(handler.getStackInSlot(index)))
				return 0;
			int max = (int) Math.min(maxAmount, Integer.MAX_VALUE);
			if (modifiable != null) {
				ItemHandlerStorage.this.updateSnapshots(transaction);
				return handler.extractItem(index, max, false).getCount();
			}
			if (pending != Pending.NONE)
				return 0;
			int amount = handler.extractItem(index, max, true).getCount();
			if (amount <= 0)
				return 0;
			updateSnapshots(transaction);
			pending = new Pending(ItemStack.EMPTY, amount);
			return amount;
		}

		@Override
		public boolean isResourceBlank() {
			return index >= handler.getSlots() || handler.getStackInSlot(index).isEmpty();
		}

		@Override
		public ItemVariant getResource() {
			return isResourceBlank() ? ItemVariant.blank() : ItemVariant.of(handler.getStackInSlot(index));
		}

		@Override
		public long getAmount() {
			return isResourceBlank() ? 0 : handler.getStackInSlot(index).getCount();
		}

		@Override
		public long getCapacity() {
			if (index >= handler.getSlots())
				return 0;
			ItemStack stack = handler.getStackInSlot(index);
			int limit = handler.getSlotLimit(index);
			return stack.isEmpty() ? limit : Math.min(limit, stack.getMaxStackSize());
		}
	}
}
