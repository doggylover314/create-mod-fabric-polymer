package dev.createpolymer.api.contraption.storage.item.simple;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import dev.createpolymer.api.contraption.storage.item.MountedItemStorageType;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.IItemHandlerModifiable;
import dev.createpolymer.foundation.item.handler.InvWrapper;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public abstract class SimpleMountedStorageType<T extends SimpleMountedStorage> extends MountedItemStorageType<SimpleMountedStorage> {
	protected SimpleMountedStorageType(MapCodec<T> codec) {
		super(codec);
	}

	@Override
	@Nullable
	public SimpleMountedStorage mount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
		return Optional.ofNullable(be)
			.map(b -> getHandler(level, b))
			.map(this::createStorage)
			.orElse(null);
	}

	protected IItemHandler getHandler(Level level, BlockEntity be) {
		if (be instanceof Container container)
			return new InvWrapper(container);
		IItemHandler handler = ItemHandlerLookup.get(level, be.getBlockPos(), null);
		// make sure the handler is modifiable so new contents can be moved over on disassembly
		return handler instanceof IItemHandlerModifiable modifiable ? modifiable : null;
	}

	protected SimpleMountedStorage createStorage(IItemHandler handler) {
		return new SimpleMountedStorage(this, handler);
	}

	public static final class Impl extends SimpleMountedStorageType<SimpleMountedStorage> {
		public Impl() {
			super(SimpleMountedStorage.CODEC);
		}
	}
}
