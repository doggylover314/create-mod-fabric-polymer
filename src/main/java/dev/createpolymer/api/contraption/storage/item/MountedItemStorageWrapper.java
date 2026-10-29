package dev.createpolymer.api.contraption.storage.item;

import com.google.common.collect.ImmutableMap;

import dev.createpolymer.foundation.item.handler.CombinedInvWrapper;
import net.minecraft.core.BlockPos;

/**
 * Wrapper around many MountedItemStorages, providing access to all of them as one storage.
 * They can still be accessed individually through the map.
 */
public class MountedItemStorageWrapper extends CombinedInvWrapper {
	public final ImmutableMap<BlockPos, MountedItemStorage> storages;

	public MountedItemStorageWrapper(ImmutableMap<BlockPos, MountedItemStorage> storages) {
		super(storages.values().toArray(MountedItemStorage[]::new));
		this.storages = storages;
	}
}
