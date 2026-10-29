package dev.createpolymer.trains.signal;

import dev.createpolymer.trains.graph.TrackGraph;
import dev.createpolymer.trains.graph.TrackNode;

/**
 * Create's SignalPropagator. The track graph calls it for every node it adds; the signals fill it in.
 */
public class SignalPropagator {

	public static void notifySignalsOfNewNode(TrackGraph graph, TrackNode node) {}

}
