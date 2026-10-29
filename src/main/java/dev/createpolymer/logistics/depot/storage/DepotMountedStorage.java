package dev.createpolymer.logistics.depot.storage;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import dev.createpolymer.api.contraption.storage.item.MountedItemStorageType;
import dev.createpolymer.api.contraption.storage.item.WrapperMountedItemStorage;
import dev.createpolymer.contraptions.Contraption;
import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import dev.createpolymer.logistics.depot.DepotBlockEntity;
import dev.createpolymer.registry.feature.ContraptionRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

public class DepotMountedStorage extends WrapperMountedItemStorage<ItemStackHandler> {
	public static final MapCodec<DepotMountedStorage> CODEC = ItemStack.OPTIONAL_CODEC.xmap(
		DepotMountedStorage::new, DepotMountedStorage::getItem
	).fieldOf("value");

	protected DepotMountedStorage(ItemStack stack) {
		this(ContraptionRegistry.DEPOT_MOUNTED_STORAGE, stack);
	}

	protected DepotMountedStorage(MountedItemStorageType<?> type, ItemStack stack) {
		super(type, new ItemStackHandler(1));
		this.wrapped.setStackInSlot(0, stack);
	}

	@Override
	public void unmount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
		if (be instanceof DepotBlockEntity depot) {
			depot.setHeldItem(this.getStackInSlot(0));
		}
	}

	@Override
	public boolean handleInteraction(ServerPlayer player, Contraption contraption, StructureBlockInfo info) {
		// interaction is handled in the Interaction Behavior, swaps items with the player
		return false;
	}

	public void setItem(ItemStack stack) {
		this.setStackInSlot(0, stack);
	}

	public ItemStack getItem() {
		return this.getStackInSlot(0);
	}

	public static DepotMountedStorage fromDepot(DepotBlockEntity depot) {
		ItemStack held = depot.getHeldItem();
		return new DepotMountedStorage(held.copy());
	}
}
