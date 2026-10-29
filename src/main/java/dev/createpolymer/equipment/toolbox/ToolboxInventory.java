package dev.createpolymer.equipment.toolbox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import dev.createpolymer.util.NBTHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Ported from Create's ToolboxInventory (MIT, The Create Team): 8 compartments of 4 slots each, a compartment holds
 * one kind of item. As an item component it is a value; the toolbox block entity owns the only mutable one.
 */
public class ToolboxInventory extends ItemStackHandler {
	public static final int STACKS_PER_COMPARTMENT = 4;
	public static final int COMPARTMENTS = 8;
	public static final Codec<ToolboxInventory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		ItemStack.OPTIONAL_CODEC.listOf().fieldOf("items").forGetter(inv -> List.copyOf(inv.stacks)),
		ItemStack.OPTIONAL_CODEC.listOf().fieldOf("filters").forGetter(toolbox -> toolbox.filters)
	).apply(instance, ToolboxInventory::deserialize));

	List<ItemStack> filters;
	boolean settling;
	private final @Nullable ToolboxBlockEntity blockEntity;

	private boolean limitedMode;

	public ToolboxInventory(@Nullable ToolboxBlockEntity be) {
		super(COMPARTMENTS * STACKS_PER_COMPARTMENT);
		this.blockEntity = be;
		limitedMode = false;
		filters = new ArrayList<>();
		settling = false;
		for (int i = 0; i < COMPARTMENTS; i++)
			filters.add(ItemStack.EMPTY);
	}

	public void inLimitedMode(Consumer<ToolboxInventory> action) {
		limitedMode = true;
		action.accept(this);
		limitedMode = false;
	}

	public void settle(int compartment) {
		int totalCount = 0;
		boolean valid = true;
		boolean shouldBeEmpty = false;
		ItemStack sample = ItemStack.EMPTY;

		for (int i = 0; i < STACKS_PER_COMPARTMENT; i++) {
			ItemStack stackInSlot = getStackInSlot(compartment * STACKS_PER_COMPARTMENT + i);
			totalCount += stackInSlot.getCount();
			if (!shouldBeEmpty)
				shouldBeEmpty = stackInSlot.isEmpty() || stackInSlot.getCount() != stackInSlot.getMaxStackSize();
			else if (!stackInSlot.isEmpty()) {
				valid = false;
				sample = stackInSlot;
			}
		}

		if (valid)
			return;

		settling = true;
		if (!sample.isStackable()) {
			for (int i = 0; i < STACKS_PER_COMPARTMENT; i++) {
				if (!getStackInSlot(compartment * STACKS_PER_COMPARTMENT + i).isEmpty())
					continue;
				for (int j = i + 1; j < STACKS_PER_COMPARTMENT; j++) {
					ItemStack stackInSlot = getStackInSlot(compartment * STACKS_PER_COMPARTMENT + j);
					if (stackInSlot.isEmpty())
						continue;
					setStackInSlot(compartment * STACKS_PER_COMPARTMENT + i, stackInSlot);
					setStackInSlot(compartment * STACKS_PER_COMPARTMENT + j, ItemStack.EMPTY);
					break;
				}
			}
		} else {
			for (int i = 0; i < STACKS_PER_COMPARTMENT; i++) {
				ItemStack copy = totalCount <= 0 ? ItemStack.EMPTY
					: sample.copyWithCount(Math.min(totalCount, sample.getMaxStackSize()));
				setStackInSlot(compartment * STACKS_PER_COMPARTMENT + i, copy);
				totalCount -= copy.getCount();
			}
		}
		settling = false;
		notifyUpdate();
	}

	@Override
	public boolean isItemValid(int slot, ItemStack stack) {
		if (!stack.getItem().canFitInsideContainerItems())
			return false;

		if (slot < 0 || slot >= getSlots())
			return false;
		int compartment = slot / STACKS_PER_COMPARTMENT;
		ItemStack filter = filters.get(compartment);
		if (limitedMode && filter.isEmpty())
			return false;
		if (filter.isEmpty() || ToolboxInventory.canItemsShareCompartment(filter, stack))
			return super.isItemValid(slot, stack);
		return false;
	}

	@Override
	public void setStackInSlot(int slot, ItemStack stack) {
		super.setStackInSlot(slot, stack);
		int compartment = slot / STACKS_PER_COMPARTMENT;
		if (!stack.isEmpty() && filters.get(compartment)
			.isEmpty()) {
			filters.set(compartment, stack.copyWithCount(1));
			notifyUpdate();
		}
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		ItemStack insertItem = super.insertItem(slot, stack, simulate);
		if (!simulate && insertItem.getCount() != stack.getCount()) {
			int compartment = slot / STACKS_PER_COMPARTMENT;
			if (!stack.isEmpty() && filters.get(compartment)
				.isEmpty()) {
				filters.set(compartment, stack.copyWithCount(1));
				notifyUpdate();
			}
		}
		return insertItem;
	}

	@Override
	public void serialize(ValueOutput output) {
		super.serialize(output);
		NBTHelper.writeItemList(output, "Compartments", filters);
	}

	@Override
	public void onContentsChanged(int slot) {
		if (!settling && (blockEntity == null || blockEntity.getLevel() == null || !blockEntity.getLevel().isClientSide()))
			settle(slot / STACKS_PER_COMPARTMENT);
		notifyUpdate();
		super.onContentsChanged(slot);
	}

	@Override
	public void deserialize(ValueInput nbt) {
		filters = new ArrayList<>(NBTHelper.readItemList(nbt, "Compartments"));
		if (filters.size() != COMPARTMENTS) {
			filters.clear();
			for (int i = 0; i < COMPARTMENTS; i++)
				filters.add(ItemStack.EMPTY);
		}
		super.deserialize(nbt);
	}

	public ItemStack distributeToCompartment(ItemStack stack, int compartment, boolean simulate) {
		if (stack.isEmpty())
			return stack;
		if (filters.get(compartment)
			.isEmpty())
			return stack;

		for (int i = STACKS_PER_COMPARTMENT - 1; i >= 0; i--) {
			int slot = compartment * STACKS_PER_COMPARTMENT + i;
			stack = insertItem(slot, stack, simulate);
			if (stack.isEmpty())
				return ItemStack.EMPTY;
		}

		return stack;
	}

	public ItemStack takeFromCompartment(int amount, int compartment, boolean simulate) {
		if (amount == 0)
			return ItemStack.EMPTY;

		int remaining = amount;
		ItemStack lastValid = ItemStack.EMPTY;

		for (int i = STACKS_PER_COMPARTMENT - 1; i >= 0; i--) {
			int slot = compartment * STACKS_PER_COMPARTMENT + i;
			ItemStack extracted = extractItem(slot, remaining, simulate);
			remaining -= extracted.getCount();
			if (!extracted.isEmpty())
				lastValid = extracted;
			if (remaining == 0)
				return lastValid.copyWithCount(amount);
		}

		if (remaining == amount)
			return ItemStack.EMPTY;

		return lastValid.copyWithCount(amount - remaining);
	}

	public List<ItemStack> getFilters() {
		return filters;
	}

	public ItemStack getFilter(int compartment) {
		return filters.get(compartment);
	}

	public void clearFilter(int compartment) {
		filters.set(compartment, ItemStack.EMPTY);
		notifyUpdate();
	}

	public static boolean canItemsShareCompartment(ItemStack stack1, ItemStack stack2) {
		if (!stack1.isStackable() && !stack2.isStackable() && stack1.isDamageableItem() && stack2.isDamageableItem())
			return stack1.getItem() == stack2.getItem();
		return ItemStack.isSameItemSameComponents(stack1, stack2);
	}

	/** A detached copy, for item components */
	public ToolboxInventory copy() {
		ToolboxInventory inventory = new ToolboxInventory(null);
		inventory.settling = true;
		for (int i = 0; i < getSlots(); i++)
			inventory.stacks.set(i, getStackInSlot(i).copy());
		inventory.settling = false;
		inventory.filters = new ArrayList<>(filters.stream().map(ItemStack::copy).toList());
		return inventory;
	}

	private void notifyUpdate() {
		if (blockEntity != null)
			blockEntity.notifyUpdate();
	}

	private static ToolboxInventory deserialize(List<ItemStack> slots, List<ItemStack> filters) {
		ToolboxInventory inventory = new ToolboxInventory(null);
		for (int i = 0; i < slots.size() && i < inventory.getSlots(); i++)
			inventory.stacks.set(i, slots.get(i)
				.copy());
		inventory.filters = new ArrayList<>(filters);
		while (inventory.filters.size() < COMPARTMENTS)
			inventory.filters.add(ItemStack.EMPTY);
		return inventory;
	}

	public boolean isEmpty() {
		for (ItemStack stack : stacks)
			if (!stack.isEmpty())
				return false;
		return true;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ToolboxInventory that))
			return false;
		return ItemStack.listMatches(stacks, that.stacks) && ItemStack.listMatches(filters, that.filters);
	}

	@Override
	public int hashCode() {
		int result = ItemStack.hashStackList(stacks);
		result = 31 * result + ItemStack.hashStackList(filters);
		return result;
	}
}
