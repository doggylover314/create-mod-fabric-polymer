package dev.createpolymer.kinetics.fan.processing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.Identifier;

/**
 * Stands in for Create's FAN_PROCESSING_TYPE registry (a NeoForge registry there): a plain static registry,
 * kept sorted by priority so that {@link FanProcessingType#getAt} finds the highest priority type first.
 */
public class FanProcessingTypeRegistry {
	private static final Map<Identifier, FanProcessingType> TYPES = new HashMap<>();
	private static final Map<FanProcessingType, Identifier> KEYS = new IdentityHashMap<>();
	private static final List<FanProcessingType> SORTED_TYPES = new ArrayList<>();
	public static final List<FanProcessingType> SORTED_TYPES_VIEW = Collections.unmodifiableList(SORTED_TYPES);

	public static synchronized <T extends FanProcessingType> T register(Identifier id, T type) {
		if (TYPES.putIfAbsent(id, type) != null)
			throw new IllegalArgumentException("Duplicate fan processing type " + id);
		KEYS.put(type, id);
		SORTED_TYPES.add(type);
		SORTED_TYPES.sort((t1, t2) -> t2.getPriority() - t1.getPriority());
		return type;
	}

	@Nullable
	public static FanProcessingType get(Identifier id) {
		return TYPES.get(id);
	}

	@Nullable
	public static Identifier getKey(FanProcessingType type) {
		return KEYS.get(type);
	}
}
