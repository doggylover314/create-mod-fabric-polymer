package dev.createpolymer.kinetics.mechanicalArm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ported from Create's ArmInteractionPointType (MIT, The Create Team).
 * <p>
 * Create keeps these in a NeoForge registry (CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE) that is frozen at
 * startup. Here they live in a plain static registry: {@link #register} adds a type and keeps the lookup list sorted.
 */
public abstract class ArmInteractionPointType {
	private static final Map<Identifier, ArmInteractionPointType> REGISTRY = new LinkedHashMap<>();
	private static final List<ArmInteractionPointType> SORTED_TYPES = new ArrayList<>();
	public static final List<ArmInteractionPointType> SORTED_TYPES_VIEW = Collections.unmodifiableList(SORTED_TYPES);

	public static <T extends ArmInteractionPointType> T register(Identifier id, T type) {
		if (REGISTRY.putIfAbsent(id, type) != null)
			throw new IllegalArgumentException("Duplicate arm interaction point type " + id);
		SORTED_TYPES.add(type);
		// stable: types of equal priority stay in registration order
		SORTED_TYPES.sort((t1, t2) -> t2.getPriority() - t1.getPriority());
		return type;
	}

	@Nullable
	public static ArmInteractionPointType get(Identifier id) {
		return REGISTRY.get(id);
	}

	@Nullable
	public static Identifier getKey(ArmInteractionPointType type) {
		for (Map.Entry<Identifier, ArmInteractionPointType> entry : REGISTRY.entrySet())
			if (entry.getValue() == type)
				return entry.getKey();
		return null;
	}

	@Nullable
	public static ArmInteractionPointType getPrimaryType(Level level, BlockPos pos, BlockState state) {
		for (ArmInteractionPointType type : SORTED_TYPES_VIEW)
			if (type.canCreatePoint(level, pos, state))
				return type;
		return null;
	}

	public abstract boolean canCreatePoint(Level level, BlockPos pos, BlockState state);

	@Nullable
	public abstract ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state);

	public int getPriority() {
		return 0;
	}
}
