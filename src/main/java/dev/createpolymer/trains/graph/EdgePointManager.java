package dev.createpolymer.trains.graph;

import dev.createpolymer.trains.signal.TrackEdgePoint;

import dev.createpolymer.util.Couple;
import dev.createpolymer.util.Iterate;

public class EdgePointManager {

	public static <T extends TrackEdgePoint> void onEdgePointAdded(TrackGraph graph, T point, EdgePointType<T> type) {
		Couple<TrackNodeLocation> edgeLocation = point.edgeLocation;
		Couple<TrackNode> startNodes = edgeLocation.map(graph::locateNode);
		Couple<TrackEdge> startEdges = startNodes.mapWithParams((l1, l2) -> graph.getConnectionsFrom(l1)
			.get(l2), startNodes.swap());

		for (boolean front : Iterate.trueAndFalse) {
			TrackEdge startEdge = startEdges.get(front);
			startEdge.getEdgeData()
				.addPoint(graph, point);
		}
	}

	public static <T extends TrackEdgePoint> void onEdgePointRemoved(TrackGraph graph, T point, EdgePointType<T> type) {
		point.onRemoved(graph);
		Couple<TrackNodeLocation> edgeLocation = point.edgeLocation;
		Couple<TrackNode> startNodes = edgeLocation.map(graph::locateNode);
		startNodes.forEachWithParams((l1, l2) -> {
			TrackEdge trackEdge = graph.getConnectionsFrom(l1)
				.get(l2);
			if (trackEdge == null)
				return;
			trackEdge.getEdgeData()
				.removePoint(graph, point);
		}, startNodes.swap());
	}

}
