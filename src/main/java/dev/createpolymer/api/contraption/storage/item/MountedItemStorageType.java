package dev.createpolymer.api.contraption.storage.item;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

import dev.createpolymer.api.registry.SimpleRegistry;
import dev.createpolymer.impl.contraption.storage.MountedItemStorageFallbackProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public abstract class MountedItemStorageType<T extends MountedItemStorage> {
	/** Create keeps these in a custom registry; here they are a plain map filled by {@link #register}. */
	private static final Map<Identifier, MountedItemStorageType<?>> TYPES = new LinkedHashMap<>();

	public static final Codec<MountedItemStorageType<?>> CODEC = Identifier.CODEC.comapFlatMap(
		id -> {
			MountedItemStorageType<?> type = TYPES.get(id);
			return type == null ? DataResult.error(() -> "Unknown mounted item storage type " + id) : DataResult.success(type);
		}, type -> type.id);

	public static final SimpleRegistry<Block, MountedItemStorageType<?>> REGISTRY = SimpleRegistry.create();

	static {
		REGISTRY.registerProvider(MountedItemStorageFallbackProvider.INSTANCE);
	}

	public final MapCodec<? extends T> codec;
	public Identifier id;
	private boolean internal;
	private boolean fuelBlacklisted;

	protected MountedItemStorageType(MapCodec<? extends T> codec) {
		this.codec = codec;
	}

	/** Gives the type its id (needed to save mounted storages). Returns the type for chaining. */
	public static <S extends MountedItemStorageType<?>> S register(Identifier id, S type) {
		if (TYPES.putIfAbsent(id, type) != null)
			throw new IllegalArgumentException("Duplicate mounted item storage type " + id);
		type.id = id;
		return type;
	}

	/**
	 * Create's create:internal type tag: storages of this type are not exposed in the contraption's combined
	 * inventory (the mounted dispenser).
	 */
	public MountedItemStorageType<T> markInternal() {
		this.internal = true;
		return this;
	}

	/** Create's create:fuel_blacklist type tag: furnace carts do not burn items from these storages (vaults). */
	public MountedItemStorageType<T> markFuelBlacklisted() {
		this.fuelBlacklisted = true;
		return this;
	}

	public boolean isInternal() {
		return internal;
	}

	public boolean isFuelBlacklisted() {
		return fuelBlacklisted;
	}

	@Nullable
	public abstract T mount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be);

	/**
	 * Associates this type with a block (Create's {@code mountedItemStorage(...)} registrate transformer).
	 */
	public void associate(Block block) {
		REGISTRY.register(block, this);
	}
}
