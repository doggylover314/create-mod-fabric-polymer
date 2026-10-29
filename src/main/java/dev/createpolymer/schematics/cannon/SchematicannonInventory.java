package dev.createpolymer.schematics.cannon;

import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import dev.createpolymer.registry.feature.SchematicsRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class SchematicannonInventory extends ItemStackHandler {
	private final SchematicannonBlockEntity blockEntity;

	public SchematicannonInventory(SchematicannonBlockEntity blockEntity) {
		super(5);
		this.blockEntity = blockEntity;
	}

	@Override
	public void onContentsChanged(int slot) {
		super.onContentsChanged(slot);
		blockEntity.setChanged();
	}

	public void clearAll() {
		stacks.clear();
	}

	@Override
	public boolean isItemValid(int slot, ItemStack stack) {
		switch (slot) {
		case 0: // Blueprint Slot
			return stack.is(SchematicsRegistry.SCHEMATIC);
		case 1: // Blueprint output
			return false;
		case 2: // Book input
			return SchematicsRegistry.isChecklistPaper(stack) || stack.is(Items.BOOK)
				|| stack.is(Items.WRITTEN_BOOK);
		case 3: // Material List output
			return false;
		case 4: // Gunpowder
			return stack.is(Items.GUNPOWDER);
		default:
			return super.isItemValid(slot, stack);
		}
	}
}
