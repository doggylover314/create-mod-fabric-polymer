package dev.createpolymer.impl.contraption.storage;

import java.util.Optional;

import com.mojang.serialization.MapCodec;

import dev.createpolymer.api.contraption.storage.item.simple.SimpleMountedStorage;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.IItemHandlerModifiable;
import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import dev.createpolymer.registry.feature.ContraptionRegistry;

/**
 * A fallback mounted storage impl that will try to be used when no type is
 * registered for a block. This requires that the mounted block provide an item handler
 * whose class is exactly {@link ItemStackHandler}.
 */
public class FallbackMountedStorage extends SimpleMountedStorage {
	public static final MapCodec<FallbackMountedStorage> CODEC = SimpleMountedStorage.codec(FallbackMountedStorage::new);

	public FallbackMountedStorage(IItemHandler handler) {
		super(ContraptionRegistry.FALLBACK_MOUNTED_STORAGE, handler);
	}

	@Override
	protected Optional<IItemHandlerModifiable> validate(IItemHandler handler) {
		return super.validate(handler).filter(FallbackMountedStorage::isValid);
	}

	public static boolean isValid(IItemHandler handler) {
		return handler.getClass() == ItemStackHandler.class;
	}
}
