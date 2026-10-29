package dev.createpolymer.contraptions.behaviour.dispenser.storage;

import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import dev.createpolymer.api.contraption.storage.item.MountedItemStorageType;
import dev.createpolymer.api.contraption.storage.item.menu.MountedStorageMenus;
import dev.createpolymer.api.contraption.storage.item.simple.SimpleMountedStorage;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.IItemHandlerModifiable;
import dev.createpolymer.registry.feature.ActorsRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Ported from Create's DispenserMountedStorage (MIT, The Create Team). */
public class DispenserMountedStorage extends SimpleMountedStorage {
	public static final MapCodec<DispenserMountedStorage> CODEC = SimpleMountedStorage.codec(DispenserMountedStorage::new);

	protected DispenserMountedStorage(MountedItemStorageType<?> type, IItemHandler handler) {
		super(type, handler);
	}

	public DispenserMountedStorage(IItemHandler handler) {
		this(ActorsRegistry.DISPENSER_MOUNTED_STORAGE, handler);
	}

	@Override
	@Nullable
	protected MenuProvider createMenuProvider(Component name, IItemHandlerModifiable handler,
											  Predicate<Player> stillValid, Consumer<Player> onClose) {
		return MountedStorageMenus.createGeneric9x9(name, handler, stillValid, onClose);
	}

	@Override
	protected void playOpeningSound(ServerLevel level, Vec3 pos) {
		// dispensers are silent
	}
}
