package dev.createpolymer.impl.contraption.storage;

import dev.createpolymer.api.contraption.storage.item.simple.SimpleMountedStorageType;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class FallbackMountedStorageType extends SimpleMountedStorageType<FallbackMountedStorage> {
	public FallbackMountedStorageType() {
		super(FallbackMountedStorage.CODEC);
	}

	@Override
	protected IItemHandler getHandler(Level level, BlockEntity be) {
		// vanilla containers are not item stack handlers: only handlers of Create block entities qualify
		IItemHandler handler = ItemHandlerLookup.get(level, be.getBlockPos(), null);
		return handler != null && FallbackMountedStorage.isValid(handler) ? handler : null;
	}
}
