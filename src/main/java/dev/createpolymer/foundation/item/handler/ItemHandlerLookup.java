package dev.createpolymer.foundation.item.handler;

import java.util.IdentityHashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Stands in for NeoForge's item handler block capability ({@code Capabilities.ItemHandler.BLOCK}).
 * <p>
 * Register Create block entities with {@link #registerBlockEntity}: they become visible to Fabric's
 * {@link ItemStorage#SIDED} too. {@link #get} returns our own handler directly when the target is a Create block
 * entity, and otherwise wraps whatever Fabric storage is there (chests, hoppers, other mods).
 */
public final class ItemHandlerLookup {
	@FunctionalInterface
	public interface Provider<B extends BlockEntity> {
		@Nullable
		IItemHandler get(B be, @Nullable Direction side);
	}

	private static final Map<BlockEntityType<?>, Provider<?>> PROVIDERS = new IdentityHashMap<>();

	private ItemHandlerLookup() {
	}

	public static <B extends BlockEntity> void registerBlockEntity(BlockEntityType<B> type, Provider<B> provider) {
		PROVIDERS.put(type, provider);
		ItemStorage.SIDED.registerForBlockEntity((B be, @Nullable Direction side) -> {
			IItemHandler handler = provider.get(be, side);
			return handler == null ? null : new ItemHandlerStorage(handler);
		}, type);
	}

	@SuppressWarnings("unchecked")
	@Nullable
	public static IItemHandler get(Level level, BlockPos pos, @Nullable Direction side) {
		BlockEntity be = level.getBlockEntity(pos);
		if (be != null) {
			Provider<BlockEntity> provider = (Provider<BlockEntity>) PROVIDERS.get(be.getType());
			if (provider != null)
				return provider.get(be, side);
		}
		Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos, null, be, side);
		if (storage == null)
			return null;
		if (storage instanceof ItemHandlerStorage wrapped)
			return wrapped.getHandler();
		return new StorageItemHandler(storage);
	}
}
