package dev.createpolymer.impl.contraption.storage;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.api.contraption.storage.item.MountedItemStorageType;
import dev.createpolymer.api.registry.SimpleRegistry;
import dev.createpolymer.registry.feature.ContraptionRegistry;
import net.minecraft.world.level.block.Block;

public enum MountedItemStorageFallbackProvider implements SimpleRegistry.Provider<Block, MountedItemStorageType<?>> {
	INSTANCE;

	@Override
	@Nullable
	public MountedItemStorageType<?> get(Block block) {
		// Create skips blocks of the fallback_mounted_storage_blacklist tag; it is empty in Create itself
		return ContraptionRegistry.FALLBACK_MOUNTED_STORAGE;
	}
}
