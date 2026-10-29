package dev.createpolymer.trains.graph;

import java.util.List;

import dev.createpolymer.trains.station.GlobalStation;

import dev.createpolymer.util.Couple;

public class DiscoveredPath {
	public List<Couple<TrackNode>> path;
	public GlobalStation destination;
	public double distance;
	public double cost;

	public DiscoveredPath(double distance, double cost, List<Couple<TrackNode>> path, GlobalStation destination) {
		this.distance = distance;
		this.cost = cost;
		this.path = path;
		this.destination = destination;
	}
}
