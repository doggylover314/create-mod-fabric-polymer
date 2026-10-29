package dev.createpolymer.api.contraption.storage.item.chest;

import dev.createpolymer.api.contraption.storage.item.simple.SimpleMountedStorage;
import dev.createpolymer.api.contraption.storage.item.simple.SimpleMountedStorageType;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.InvWrapper;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ChestMountedStorageType extends SimpleMountedStorageType<ChestMountedStorage> {
	public ChestMountedStorageType() {
		super(ChestMountedStorage.CODEC);
	}

	@Override
	protected IItemHandler getHandler(Level level, BlockEntity be) {
		return be instanceof Container container ? new InvWrapper(container) : null;
	}

	@Override
	protected SimpleMountedStorage createStorage(IItemHandler handler) {
		return new ChestMountedStorage(handler);
	}
}
