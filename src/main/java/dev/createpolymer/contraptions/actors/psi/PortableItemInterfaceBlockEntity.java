package dev.createpolymer.contraptions.actors.psi;

import dev.createpolymer.contraptions.Contraption;
import dev.createpolymer.foundation.item.handler.IItemHandlerModifiable;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import dev.createpolymer.registry.feature.ActorsRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Ported from Create's PortableItemInterfaceBlockEntity (MIT, The Create Team). */
public class PortableItemInterfaceBlockEntity extends PortableStorageInterfaceBlockEntity {

	protected IItemHandlerModifiable capability;

	public PortableItemInterfaceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		capability = createEmptyHandler();
	}

	public static void registerCapabilities() {
		ItemHandlerLookup.registerBlockEntity(ActorsRegistry.PORTABLE_STORAGE_INTERFACE_BLOCK_ENTITY,
			(be, side) -> be.capability);
	}

	@Override
	public void startTransferringTo(Contraption contraption, float distance) {
		capability = new InterfaceItemHandler(contraption.getStorage()
			.getAllItems());
		invalidateCapability();
		if (level != null && !level.isClientSide())
			level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
		super.startTransferringTo(contraption, distance);
	}

	@Override
	protected void stopTransferring() {
		capability = createEmptyHandler();
		invalidateCapability();
		if (level != null && !level.isClientSide())
			level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
		super.stopTransferring();
	}

	private IItemHandlerModifiable createEmptyHandler() {
		return new InterfaceItemHandler(new ItemStackHandler(0));
	}

	@Override
	protected void invalidateCapability() {
		// handlers are looked up when they are used
	}

	class InterfaceItemHandler implements IItemHandlerModifiable {
		private final IItemHandlerModifiable wrapped;

		public InterfaceItemHandler(IItemHandlerModifiable wrapped) {
			this.wrapped = wrapped;
		}

		@Override
		public void setStackInSlot(int slot, ItemStack stack) {
			wrapped.setStackInSlot(slot, stack);
		}

		@Override
		public int getSlots() {
			return wrapped.getSlots();
		}

		@Override
		public ItemStack getStackInSlot(int slot) {
			return wrapped.getStackInSlot(slot);
		}

		@Override
		public int getSlotLimit(int slot) {
			return wrapped.getSlotLimit(slot);
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return wrapped.isItemValid(slot, stack);
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			if (!canTransfer())
				return ItemStack.EMPTY;
			ItemStack extractItem = wrapped.extractItem(slot, amount, simulate);
			if (!simulate && !extractItem.isEmpty())
				onContentTransferred();
			return extractItem;
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			if (!canTransfer())
				return stack;
			ItemStack insertItem = wrapped.insertItem(slot, stack, simulate);
			if (!simulate && !ItemStack.matches(insertItem, stack))
				onContentTransferred();
			return insertItem;
		}

	}

}
