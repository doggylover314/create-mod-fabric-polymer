package dev.createpolymer.foundation.item.handler;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.world.item.ItemStack;

/**
 * Presents a Fabric item {@link Storage} (vanilla containers, other mods) as an {@link IItemHandler}. Each call runs
 * in its own transaction, committed unless simulating.
 * <p>
 * Slotted storages map slot for slot. Other storages show one slot per current view plus one always-empty slot at
 * the end; inserting into any slot inserts into the storage as a whole.
 */
public class StorageItemHandler implements IItemHandler {
	private final Storage<ItemVariant> storage;
	private final SlottedStorage<ItemVariant> slotted;

	public StorageItemHandler(Storage<ItemVariant> storage) {
		this.storage = storage;
		this.slotted = storage instanceof SlottedStorage<ItemVariant> s ? s : null;
	}

	public Storage<ItemVariant> getStorage() {
		return storage;
	}

	@SuppressWarnings("deprecation")
	static Transaction open() {
		return Transaction.openNested(Transaction.isOpen() ? Transaction.getCurrentUnsafe() : null);
	}

	private List<StorageView<ItemVariant>> views() {
		List<StorageView<ItemVariant>> list = new ArrayList<>();
		for (StorageView<ItemVariant> view : storage)
			list.add(view);
		return list;
	}

	private StorageView<ItemVariant> view(int slot) {
		if (slotted != null)
			return slot >= 0 && slot < slotted.getSlotCount() ? slotted.getSlot(slot) : null;
		List<StorageView<ItemVariant>> list = views();
		return slot >= 0 && slot < list.size() ? list.get(slot) : null;
	}

	@Override
	public int getSlots() {
		return slotted != null ? slotted.getSlotCount() : views().size() + 1;
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		StorageView<ItemVariant> view = view(slot);
		if (view == null || view.isResourceBlank() || view.getAmount() <= 0)
			return ItemStack.EMPTY;
		return view.getResource().toStack((int) Math.min(Integer.MAX_VALUE, view.getAmount()));
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		if (stack.isEmpty())
			return ItemStack.EMPTY;
		Storage<ItemVariant> target = slotted != null ? (slot >= 0 && slot < slotted.getSlotCount() ? slotted.getSlot(slot) : null)
			: storage;
		if (target == null)
			return stack;
		long inserted;
		try (Transaction t = open()) {
			inserted = target.insert(ItemVariant.of(stack), stack.getCount(), t);
			if (!simulate)
				t.commit();
		}
		if (inserted <= 0)
			return stack;
		return inserted >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - (int) inserted);
	}

	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		if (amount <= 0)
			return ItemStack.EMPTY;
		StorageView<ItemVariant> view = view(slot);
		if (view == null || view.isResourceBlank())
			return ItemStack.EMPTY;
		ItemVariant resource = view.getResource();
		int max = Math.min(amount, resource.toStack().getMaxStackSize());
		long extracted;
		try (Transaction t = open()) {
			extracted = view.extract(resource, max, t);
			if (!simulate)
				t.commit();
		}
		return extracted <= 0 ? ItemStack.EMPTY : resource.toStack((int) extracted);
	}

	@Override
	public int getSlotLimit(int slot) {
		StorageView<ItemVariant> view = view(slot);
		if (view == null)
			return 64;
		return (int) Math.min(Integer.MAX_VALUE, view.getCapacity());
	}

	@Override
	public boolean isItemValid(int slot, ItemStack stack) {
		return insertItem(slot, stack.copyWithCount(1), true).isEmpty();
	}
}
