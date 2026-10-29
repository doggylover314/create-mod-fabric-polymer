package dev.createpolymer.trains.bogey;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.registry.feature.TrainsRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class AllBogeyStyles {
	public static final Map<Identifier, BogeyStyle> BOGEY_STYLES = new HashMap<>();
	public static final Map<Identifier, Map<Identifier, BogeyStyle>> CYCLE_GROUPS = new HashMap<>();
	private static final Map<Identifier, BogeyStyle> EMPTY_GROUP = Collections.emptyMap();

	public static final Identifier STANDARD_CYCLE_GROUP = CreatePolymer.id("standard");

	public static final BogeyStyle STANDARD = builder("standard", STANDARD_CYCLE_GROUP)
		.displayName(Component.translatable("create.bogey.style.standard"))
		.size(BogeySizes.SMALL, () -> TrainsRegistry.SMALL_BOGEY, StandardBogeyVisual.Small::new)
		.size(BogeySizes.LARGE, () -> TrainsRegistry.LARGE_BOGEY, StandardBogeyVisual.Large::new)
		.build();

	public static Map<Identifier, BogeyStyle> getCycleGroup(Identifier cycleGroup) {
		return CYCLE_GROUPS.getOrDefault(cycleGroup, EMPTY_GROUP);
	}

	private static BogeyStyle.Builder builder(String name, Identifier cycleGroup) {
		return new BogeyStyle.Builder(CreatePolymer.id(name), cycleGroup);
	}

	public static void init() {
	}
}
