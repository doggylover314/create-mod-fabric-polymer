package dev.createpolymer.foundation.fluid;

import java.util.IdentityHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Stands in for NeoForge's fluid handler block capability ({@code Capabilities.FluidHandler.BLOCK}). Registered
 * block entities are also exposed to Fabric's {@link FluidStorage#SIDED}; {@link #get} wraps foreign storages.
 */
public final class FluidHandlerLookup {
	@FunctionalInterface
	public interface Provider<B extends BlockEntity> {
		@Nullable
		IFluidHandler get(B be, @Nullable Direction side);
	}

	private static final Map<BlockEntityType<?>, Provider<?>> PROVIDERS = new IdentityHashMap<>();

	private FluidHandlerLookup() {
	}

	public static <B extends BlockEntity> void registerBlockEntity(BlockEntityType<B> type, Provider<B> provider) {
		PROVIDERS.put(type, provider);
		FluidStorage.SIDED.registerForBlockEntity((B be, @Nullable Direction side) -> {
			IFluidHandler handler = provider.get(be, side);
			return handler == null ? null : new FluidHandlerStorage(handler);
		}, type);
	}

	@SuppressWarnings("unchecked")
	@Nullable
	public static IFluidHandler get(Level level, BlockPos pos, @Nullable Direction side) {
		BlockEntity be = level.getBlockEntity(pos);
		if (be != null) {
			Provider<BlockEntity> provider = (Provider<BlockEntity>) PROVIDERS.get(be.getType());
			if (provider != null)
				return provider.get(be, side);
		}
		Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, pos, null, be, side);
		if (storage == null)
			return null;
		if (storage instanceof FluidHandlerStorage wrapped)
			return wrapped.getHandler();
		return new StorageFluidHandler(storage);
	}
}
