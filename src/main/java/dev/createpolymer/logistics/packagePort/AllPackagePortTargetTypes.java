package dev.createpolymer.logistics.packagePort;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.ApiStatus.Internal;
import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.logistics.packagePort.PackagePortTarget.ChainConveyorFrogportTarget;
import dev.createpolymer.logistics.packagePort.PackagePortTarget.TrainStationFrogportTarget;
import net.minecraft.resources.Identifier;

/**
 * Create keeps the target types in a registry of its own. This is a small map with the same two entries; other mods'
 * (or later ports') types are added with {@link #register}.
 */
public class AllPackagePortTargetTypes {
	private static final Map<Identifier, PackagePortTargetType> REGISTRY = new LinkedHashMap<>();
	private static final Map<PackagePortTargetType, Identifier> KEYS = new java.util.IdentityHashMap<>();

	public static final PackagePortTargetType CHAIN_CONVEYOR = register(CreatePolymer.id("chain_conveyor"),
		new ChainConveyorFrogportTarget.Type());
	public static final PackagePortTargetType TRAIN_STATION = register(CreatePolymer.id("train_station"),
		new TrainStationFrogportTarget.Type());

	/** Codec for the registry names of the target types. */
	public static final Codec<PackagePortTargetType> BY_NAME_CODEC = Identifier.CODEC.comapFlatMap(id -> {
		PackagePortTargetType type = REGISTRY.get(id);
		return type == null ? DataResult.error(() -> "Unknown package port target type " + id) : DataResult.success(type);
	}, type -> KEYS.get(type));

	public static synchronized PackagePortTargetType register(Identifier id, PackagePortTargetType type) {
		if (REGISTRY.putIfAbsent(id, type) != null)
			throw new IllegalArgumentException("Duplicate package port target type " + id);
		KEYS.put(type, id);
		return type;
	}

	@Nullable
	public static PackagePortTargetType get(Identifier id) {
		return REGISTRY.get(id);
	}

	@Internal
	public static void init() {
	}
}
