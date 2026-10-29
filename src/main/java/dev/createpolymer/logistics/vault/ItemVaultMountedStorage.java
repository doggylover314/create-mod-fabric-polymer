package dev.createpolymer.logistics.vault;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import dev.createpolymer.api.contraption.storage.item.MountedItemStorageType;
import dev.createpolymer.api.contraption.storage.item.WrapperMountedItemStorage;
import dev.createpolymer.api.contraption.storage.item.simple.SimpleMountedStorage;
import dev.createpolymer.contraptions.Contraption;
import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import dev.createpolymer.registry.feature.ContraptionRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

public class ItemVaultMountedStorage extends WrapperMountedItemStorage<ItemStackHandler> {
	public static final MapCodec<ItemVaultMountedStorage> CODEC = SimpleMountedStorage.ITEM_STACK_HANDLER.xmap(
		ItemVaultMountedStorage::new, storage -> storage.wrapped
	).fieldOf("value");

	protected ItemVaultMountedStorage(MountedItemStorageType<?> type, ItemStackHandler handler) {
		super(type, handler);
	}

	protected ItemVaultMountedStorage(ItemStackHandler handler) {
		this(ContraptionRegistry.VAULT_MOUNTED_STORAGE, handler);
	}

	@Override
	public void unmount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
		if (be instanceof ItemVaultBlockEntity vault) {
			// Create's applyInventoryToBlock
			ItemStackHandler target = vault.getInventoryOfBlock();
			for (int i = 0; i < target.getSlots(); i++)
				target.setStackInSlot(i, i < this.wrapped.getSlots() ? this.wrapped.getStackInSlot(i) : ItemStack.EMPTY);
		}
	}

	@Override
	public boolean handleInteraction(ServerPlayer player, Contraption contraption, StructureBlockInfo info) {
		// vaults should never be opened.
		return false;
	}

	public static ItemVaultMountedStorage fromVault(ItemVaultBlockEntity vault) {
		// Vault inventories have a world-affecting onContentsChanged, copy to a safe one
		return new ItemVaultMountedStorage(copyToItemStackHandler(vault.getInventoryOfBlock()));
	}
}
