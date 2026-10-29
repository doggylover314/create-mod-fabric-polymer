package dev.createpolymer.trains.entity;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.createpolymer.CreatePolymer;
import net.minecraft.resources.Identifier;

/**
 * The icon of a train (Create draws it in the station screen and on maps). Here it is the name of a look that the
 * station GUI lets the player choose; the id is saved with the train.
 */
public class TrainIconType {

	public static Map<Identifier, TrainIconType> REGISTRY = new LinkedHashMap<>();

	public static void register(Identifier id) {
		REGISTRY.put(id, new TrainIconType(id));
	}

	static {
		register(CreatePolymer.id("traditional"));
		register(CreatePolymer.id("electric"));
		register(CreatePolymer.id("modern"));
	}

	public final Identifier id;

	public TrainIconType(Identifier id) {
		this.id = id;
	}

	public static TrainIconType byId(Identifier id) {
		return REGISTRY.getOrDefault(id, getDefault());
	}

	public static TrainIconType getDefault() {
		return REGISTRY.get(CreatePolymer.id("traditional"));
	}

	public Identifier getId() {
		return id;
	}

	public TrainIconType next() {
		TrainIconType[] all = REGISTRY.values()
			.toArray(TrainIconType[]::new);
		for (int i = 0; i < all.length; i++)
			if (all[i] == this)
				return all[(i + 1) % all.length];
		return getDefault();
	}

}
