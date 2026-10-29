package dev.createpolymer.api.contraption.storage.fluid;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

import dev.createpolymer.api.registry.SimpleRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public abstract class MountedFluidStorageType<T extends MountedFluidStorage> {
	private static final Map<Identifier, MountedFluidStorageType<?>> TYPES = new LinkedHashMap<>();

	public static final Codec<MountedFluidStorageType<?>> CODEC = Identifier.CODEC.comapFlatMap(
		id -> {
			MountedFluidStorageType<?> type = TYPES.get(id);
			return type == null ? DataResult.error(() -> "Unknown mounted fluid storage type " + id) : DataResult.success(type);
		}, type -> type.id);
	public static final SimpleRegistry<Block, MountedFluidStorageType<?>> REGISTRY = SimpleRegistry.create();

	public final MapCodec<? extends T> codec;
	public Identifier id;

	protected MountedFluidStorageType(MapCodec<? extends T> codec) {
		this.codec = codec;
	}

	/** Gives the type its id (needed to save mounted storages). Returns the type for chaining. */
	public static <S extends MountedFluidStorageType<?>> S register(Identifier id, S type) {
		if (TYPES.putIfAbsent(id, type) != null)
			throw new IllegalArgumentException("Duplicate mounted fluid storage type " + id);
		type.id = id;
		return type;
	}

	@Nullable
	public abstract T mount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be);

	/** Associates this type with a block (Create's {@code mountedFluidStorage(...)} registrate transformer). */
	public void associate(Block block) {
		REGISTRY.register(block, this);
	}
}
