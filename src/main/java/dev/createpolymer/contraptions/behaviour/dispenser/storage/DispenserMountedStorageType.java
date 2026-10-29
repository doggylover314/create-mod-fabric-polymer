package dev.createpolymer.contraptions.behaviour.dispenser.storage;

import dev.createpolymer.api.contraption.storage.item.simple.SimpleMountedStorage;
import dev.createpolymer.api.contraption.storage.item.simple.SimpleMountedStorageType;
import dev.createpolymer.foundation.item.handler.IItemHandler;

public class DispenserMountedStorageType extends SimpleMountedStorageType<DispenserMountedStorage> {
	public DispenserMountedStorageType() {
		super(DispenserMountedStorage.CODEC);
	}

	@Override
	protected SimpleMountedStorage createStorage(IItemHandler handler) {
		return new DispenserMountedStorage(handler);
	}
}
