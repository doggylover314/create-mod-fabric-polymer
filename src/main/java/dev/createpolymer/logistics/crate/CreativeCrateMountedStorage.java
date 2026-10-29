package dev.createpolymer.logistics.crate;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import dev.createpolymer.api.contraption.storage.item.MountedItemStorage;
import dev.createpolymer.api.contraption.storage.item.MountedItemStorageType;
import dev.createpolymer.registry.feature.ActorsRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Ported from Create's CreativeCrateMountedStorage (MIT, The Create Team). */
public class CreativeCrateMountedStorage extends MountedItemStorage {
	public static final MapCodec<CreativeCrateMountedStorage> CODEC = ItemStack.OPTIONAL_CODEC.xmap(
		CreativeCrateMountedStorage::new, storage -> storage.suppliedStack
	).fieldOf("value");

	private final ItemStack suppliedStack;
	private final ItemStack cachedStackInSlot;

	protected CreativeCrateMountedStorage(MountedItemStorageType<?> type, ItemStack suppliedStack) {
		super(type);
		this.suppliedStack = suppliedStack;
		this.cachedStackInSlot = suppliedStack.copyWithCount(suppliedStack.getMaxStackSize());
	}

	public CreativeCrateMountedStorage(ItemStack suppliedStack) {
		this(ActorsRegistry.CREATIVE_CRATE_MOUNTED_STORAGE, suppliedStack);
	}

	@Override
	public void unmount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
		// no need to do anything here, the supplied item can't change while mounted
	}

	@Override
	public int getSlots() {
		return 2; // 0 holds the supplied stack endlessly, 1 is always empty to accept
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		return slot == 0 ? this.cachedStackInSlot : ItemStack.EMPTY;
	}

	@Override
	public void setStackInSlot(int slot, ItemStack stack) {
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		return ItemStack.EMPTY; // no remainder, accept any input
	}

	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		if (slot == 0 && !this.suppliedStack.isEmpty()) {
			int count = Math.min(amount, this.suppliedStack.getMaxStackSize());
			return this.suppliedStack.copyWithCount(count);
		}

		return ItemStack.EMPTY;
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
