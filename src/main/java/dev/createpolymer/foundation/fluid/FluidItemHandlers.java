package dev.createpolymer.foundation.fluid;

import java.util.Optional;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.world.item.ItemStack;

/**
 * Item fluid handlers (buckets, bottles, other mods' tanks), standing in for NeoForge's
 * {@code Capabilities.FluidHandler.ITEM} / {@code FluidUtil.getFluidHandler(ItemStack)}.
 * <p>
 * The handler works on a private copy of the stack; read the resulting item with {@link IFluidHandlerItem#getContainer()}.
 * Like NeoForge's, it handles one item: pass a stack of count 1 when the result must be a single container.
 */
public final class FluidItemHandlers {
	private FluidItemHandlers() {
	}

	public static Optional<IFluidHandlerItem> get(ItemStack stack) {
		if (stack.isEmpty())
			return Optional.empty();
		ItemSlot slot = new ItemSlot(stack.copy());
		Storage<FluidVariant> storage = ContainerItemContext.ofSingleSlot(slot).find(FluidStorage.ITEM);
		if (storage == null)
			return Optional.empty();
		return Optional.of(new ItemHandler(storage, slot));
	}

	private static class ItemSlot extends SingleStackStorage {
		private ItemStack stack;

		ItemSlot(ItemStack stack) {
			this.stack = stack;
		}

		@Override
		protected ItemStack getStack() {
			return stack;
		}

		@Override
		protected void setStack(ItemStack stack) {
			this.stack = stack;
		}
	}

	private static class ItemHandler extends StorageFluidHandler implements IFluidHandlerItem {
		private final ItemSlot slot;

		ItemHandler(Storage<FluidVariant> storage, ItemSlot slot) {
			super(storage);
			this.slot = slot;
		}

		@Override
		public ItemStack getContainer() {
			return slot.getStack();
		}
	}
}
