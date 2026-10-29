package dev.createpolymer.trains.station;

import dev.createpolymer.trains.signal.SingleBlockEntityEdgePoint;

/**
 * Create's station edge point. Only the edge point itself exists so far (it is part of the graph's edge point
 * types); the station block and trains come with the stations.
 */
public class GlobalStation extends SingleBlockEntityEdgePoint {

	public String name = "Track Station";

}
