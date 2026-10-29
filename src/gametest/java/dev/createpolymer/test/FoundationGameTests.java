package dev.createpolymer.test;

import dev.createpolymer.foundation.fluid.FluidHandlerStorage;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.FluidTank;
import dev.createpolymer.foundation.fluid.IFluidHandler;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.ItemHandlerStorage;
import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.SingleFluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

/** Checks the bridges between Create's handlers and Fabric's transfer API. */
public class FoundationGameTests {

	/** A handler that is not modifiable, so the bridge has to defer its operations. */
	private static IItemHandler unmodifiable(ItemStackHandler inner) {
		return new IItemHandler() {
			public int getSlots() { return inner.getSlots(); }
			public ItemStack getStackInSlot(int slot) { return inner.getStackInSlot(slot); }
			public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return inner.insertItem(slot, stack, simulate); }
			public ItemStack extractItem(int slot, int amount, boolean simulate) { return inner.extractItem(slot, amount, simulate); }
			public int getSlotLimit(int slot) { return inner.getSlotLimit(slot); }
			public boolean isItemValid(int slot, ItemStack stack) { return inner.isItemValid(slot, stack); }
		};
	}

	private static SingleStackStorage target() {
		return new SingleStackStorage() {
			ItemStack stack = ItemStack.EMPTY;
			protected ItemStack getStack() { return stack; }
			protected void setStack(ItemStack s) { stack = s; }
		};
	}

	@GameTest
	public void deferredExtractionWorksWithStorageUtilMove(GameTestHelper helper) {
		ItemStackHandler inner = new ItemStackHandler(1);
		inner.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 5));
		ItemHandlerStorage storage = new ItemHandlerStorage(unmodifiable(inner));
		SingleStackStorage out = target();

		// StorageUtil.move extracts in a nested transaction first, aborts, then extracts for real.
		long moved;
		try (Transaction t = Transaction.openOuter()) {
			moved = StorageUtil.move(storage, out, v -> true, 2, t);
			helper.assertTrue(inner.getStackInSlot(0).getCount() == 5, "Handler changed before commit");
			t.commit();
		}
		helper.assertTrue(moved == 2, "Moved " + moved + ", expected 2");
		helper.assertTrue(inner.getStackInSlot(0).getCount() == 3, "Handler has " + inner.getStackInSlot(0).getCount());
		helper.assertTrue(out.getAmount() == 2, "Target has " + out.getAmount());
		helper.succeed();
	}

	@GameTest
	public void deferredOperationIsDroppedOnAbort(GameTestHelper helper) {
		ItemStackHandler inner = new ItemStackHandler(1);
		ItemHandlerStorage storage = new ItemHandlerStorage(unmodifiable(inner));
		try (Transaction t = Transaction.openOuter()) {
			long inserted = storage.insert(ItemVariant.of(Items.DIRT), 10, t);
			helper.assertTrue(inserted == 10, "Inserted " + inserted);
			t.abort();
		}
		helper.assertTrue(inner.getStackInSlot(0).isEmpty(), "Aborted insert was applied");
		try (Transaction t = Transaction.openOuter()) {
			storage.insert(ItemVariant.of(Items.DIRT), 10, t);
			t.commit();
		}
		helper.assertTrue(inner.getStackInSlot(0).getCount() == 10, "Committed insert missing");
		helper.succeed();
	}

	@GameTest
	public void fluidTankBridgeMovesWholeMillibuckets(GameTestHelper helper) {
		FluidTank tank = new FluidTank(1000);
		tank.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
		FluidHandlerStorage storage = new FluidHandlerStorage(tank);
		SingleFluidStorage out = SingleFluidStorage.withFixedCapacity(81000, () -> {});
		long moved;
		try (Transaction t = Transaction.openOuter()) {
			moved = StorageUtil.move(storage, out, v -> true, 40500, t);
			t.commit();
		}
		helper.assertTrue(moved == 40500, "Moved " + moved);
		helper.assertTrue(tank.getFluidAmount() == 500, "Tank has " + tank.getFluidAmount());
		helper.assertTrue(out.variant.equals(FluidVariant.of(Fluids.WATER)), "Wrong fluid");
		helper.succeed();
	}
}
