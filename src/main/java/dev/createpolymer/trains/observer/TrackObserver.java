package dev.createpolymer.trains.observer;

import dev.createpolymer.logistics.filter.FilterItemStack;
import dev.createpolymer.trains.entity.Train;
import dev.createpolymer.trains.signal.SingleBlockEntityEdgePoint;

/**
 * Create's observer edge point. Only the edge point itself exists so far (it is part of the graph's edge point
 * types); trains already report to it, the observer block comes with the observers.
 */
public class TrackObserver extends SingleBlockEntityEdgePoint {

	public FilterItemStack getFilter() {
		return FilterItemStack.empty();
	}

	public void keepAlive(Train train) {}

}
