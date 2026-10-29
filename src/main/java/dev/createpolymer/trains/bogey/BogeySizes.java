package dev.createpolymer.trains.bogey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.createpolymer.CreatePolymer;
import net.minecraft.resources.Identifier;

public final class BogeySizes {
	private static final Map<Identifier, BogeySize> BOGEY_SIZES = new HashMap<>();
	private static final List<BogeySize> SORTED_INCREASING = new ArrayList<>();
	private static final List<BogeySize> SORTED_DECREASING = new ArrayList<>();
	private static final Map<Identifier, BogeySize> BOGEY_SIZES_VIEW = Collections.unmodifiableMap(BOGEY_SIZES);
	private static final List<BogeySize> SORTED_INCREASING_VIEW = Collections.unmodifiableList(SORTED_INCREASING);
	private static final List<BogeySize> SORTED_DECREASING_VIEW = Collections.unmodifiableList(SORTED_DECREASING);

	public static final BogeySize SMALL = new BogeySize(CreatePolymer.id("small"), 6.5f / 16f);
	public static final BogeySize LARGE = new BogeySize(CreatePolymer.id("large"), 12.5f / 16f);

	static {
		register(SMALL);
		register(LARGE);
	}

	private BogeySizes() {
	}

	public static void register(BogeySize size) {
		Identifier id = size.id();
		if (BOGEY_SIZES.containsKey(id)) {
			throw new IllegalArgumentException();
		}
		BOGEY_SIZES.put(id, size);

		SORTED_INCREASING.add(size);
		SORTED_DECREASING.add(size);
		SORTED_INCREASING.sort(Comparator.comparing(BogeySize::wheelRadius));
		SORTED_DECREASING.sort(Comparator.comparing(BogeySize::wheelRadius).reversed());
	}

	public static Map<Identifier, BogeySize> all() {
		return BOGEY_SIZES_VIEW;
	}

	public static List<BogeySize> allSortedIncreasing() {
		return SORTED_INCREASING_VIEW;
	}

	public static List<BogeySize> allSortedDecreasing() {
		return SORTED_DECREASING_VIEW;
	}

	public static void init() {
	}

	public record BogeySize(Identifier id, float wheelRadius) {
		public BogeySize nextBySize() {
			List<BogeySize> values = allSortedIncreasing();
			int ordinal = values.indexOf(this);
			return values.get((ordinal + 1) % values.size());
		}
	}
}
