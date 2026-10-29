package dev.createpolymer.trains.graph;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.trains.observer.TrackObserver;
import dev.createpolymer.trains.signal.SignalBoundary;
import dev.createpolymer.trains.signal.TrackEdgePoint;
import dev.createpolymer.trains.station.GlobalStation;

import net.minecraft.resources.Identifier;

public class EdgePointType<T extends TrackEdgePoint> {

	public static final Map<Identifier, EdgePointType<?>> TYPES = new HashMap<>();
	private Identifier id;
	private Supplier<T> factory;

	public static final EdgePointType<SignalBoundary> SIGNAL =
		register(CreatePolymer.id("signal"), SignalBoundary::new);
	public static final EdgePointType<GlobalStation> STATION =
		register(CreatePolymer.id("station"), GlobalStation::new);
	public static final EdgePointType<TrackObserver> OBSERVER =
		register(CreatePolymer.id("observer"), TrackObserver::new);

	public static <T extends TrackEdgePoint> EdgePointType<T> register(Identifier id, Supplier<T> factory) {
		EdgePointType<T> type = new EdgePointType<>(id, factory);
		TYPES.put(id, type);
		return type;
	}

	public EdgePointType(Identifier id, Supplier<T> factory) {
		this.id = id;
		this.factory = factory;
	}

	public T create() {
		T t = factory.get();
		t.setType(this);
		return t;
	}

	public Identifier getId() {
		return id;
	}

}
