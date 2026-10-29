package dev.createpolymer.impl.unpacking;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.api.packager.unpacking.UnpackingHandler;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.logistics.stockTicker.PackageOrderWithCrafts;
import dev.createpolymer.processing.basin.BasinBlockEntity;
import dev.createpolymer.util.ItemSlots;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create switches the basin's input inventory to "packager mode", where its one-slot-per-item-type rule (which only
 * matters for belt setups) is off. The basin here applies that rule in its container methods, so this handler
 * inserts into the input slots directly instead.
 */
public enum BasinUnpackingHandler implements UnpackingHandler {
	INSTANCE;

	@Override
	public boolean unpack(Level level, BlockPos pos, BlockState state, Direction side, List<ItemStack> items,
		@Nullable PackageOrderWithCrafts orderContext, boolean simulate) {
		BlockEntity be = level.getBlockEntity(pos);
		if (!(be instanceof BasinBlockEntity basin))
			return false;

		boolean unpacked = DefaultUnpackingHandler.unpackInto(new SlotsHandler(basin.inputInventory), items, simulate);
		if (unpacked && !simulate)
			basin.notifyChangeOfContents();
		return unpacked;
	}

	private record SlotsHandler(ItemSlots slots) implements IItemHandler {
		@Override
		public int getSlots() {
			return slots.size();
		}

		@Override
		public ItemStack getStackInSlot(int slot) {
			return slots.get(slot);
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			return slots.insert(slot, stack, simulate);
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			return slots.extract(slot, amount, simulate);
		}

		@Override
		public int getSlotLimit(int slot) {
			return 64;
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return true;
		}
	}
}
