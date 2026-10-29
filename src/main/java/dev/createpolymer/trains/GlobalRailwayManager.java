package dev.createpolymer.trains;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.trains.entity.Train;
import dev.createpolymer.trains.graph.TrackGraph;
import dev.createpolymer.trains.graph.TrackNodeLocation;
import dev.createpolymer.trains.signal.SignalEdgeGroup;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

/**
 * Create's GlobalRailwayManager (Create.RAILWAYS): the track graphs, signal groups and trains of the world. There is
 * no client copy and nothing is synced to players, so the graph sync calls of Create are gone.
 */
public class GlobalRailwayManager {

	public Map<UUID, TrackGraph> trackNetworks;
	public Map<UUID, SignalEdgeGroup> signalEdgeGroups;
	public Map<UUID, Train> trains;

	private List<Train> movingTrains;
	private List<Train> waitingTrains;

	private RailwaySavedData savedData;
	private HolderLookup.Provider registries = RegistryAccess.EMPTY;

	public int version;

	public GlobalRailwayManager() {
		cleanUp();
	}

	public void levelLoaded(LevelAccessor level) {
		MinecraftServer server = level.getServer();
		if (server == null || server.overworld() != level)
			return;
		cleanUp();
		savedData = null;
		loadTrackData(server);
	}

	/** Forgets everything of the world that was closed. */
	public void levelUnloaded(LevelAccessor level) {
		MinecraftServer server = level.getServer();
		if (server == null || server.overworld() != level)
			return;
		cleanUp();
		savedData = null;
	}

	private void loadTrackData(MinecraftServer server) {
		if (savedData != null)
			return;
		registries = server.registryAccess();
		savedData = RailwaySavedData.load(server);
		trains = savedData.getTrains();
		trackNetworks = savedData.getTrackNetworks();
		signalEdgeGroups = savedData.getSignalBlocks();
		movingTrains.addAll(trains.values());
	}

	public void cleanUp() {
		trackNetworks = new HashMap<>();
		signalEdgeGroups = new HashMap<>();
		trains = new HashMap<>();
		movingTrains = new LinkedList<>();
		waitingTrains = new LinkedList<>();
	}

	/** The registries to save and load the graph with (edge points can hold items). */
	public HolderLookup.Provider registries() {
		return registries;
	}

	public RailwaySavedData getSavedData() {
		return savedData;
	}

	public void markTracksDirty() {
		if (savedData != null)
			savedData.setDirty();
	}

	public void addTrain(Train train) {
		trains.put(train.id, train);
		movingTrains.add(train);
	}

	public void removeTrain(UUID id) {
		Train removed = trains.remove(id);
		if (removed == null)
			return;
		movingTrains.remove(removed);
		waitingTrains.remove(removed);
	}

	//

	public TrackGraph getOrCreateGraph(UUID graphID, int netId) {
		return trackNetworks.computeIfAbsent(graphID, uid -> {
			TrackGraph trackGraph = new TrackGraph(graphID);
			trackGraph.setNetId(netId);
			return trackGraph;
		});
	}

	public void putGraphWithDefaultGroup(TrackGraph graph) {
		SignalEdgeGroup group = new SignalEdgeGroup(graph.id);
		signalEdgeGroups.put(graph.id, group.asFallback());
		putGraph(graph);
	}

	public void putGraph(TrackGraph graph) {
		trackNetworks.put(graph.id, graph);
		markTracksDirty();
	}

	public void removeGraphAndGroup(TrackGraph graph) {
		signalEdgeGroups.remove(graph.id);
		removeGraph(graph);
	}

	public void removeGraph(TrackGraph graph) {
		trackNetworks.remove(graph.id);
		markTracksDirty();
	}

	public void updateSplitGraph(LevelAccessor level, TrackGraph graph) {
		Set<TrackGraph> disconnected = graph.findDisconnectedGraphs(level, null);
		for (TrackGraph d : disconnected) {
			putGraphWithDefaultGroup(d);
		}
		if (!disconnected.isEmpty()) {
			markTracksDirty();
		}
	}

	@Nullable
	public TrackGraph getGraph(LevelAccessor level, TrackNodeLocation vertex) {
		if (trackNetworks == null)
			return null;
		for (TrackGraph railGraph : trackNetworks.values())
			if (railGraph.locateNode(vertex) != null)
				return railGraph;
		return null;
	}

	public List<TrackGraph> getGraphs(LevelAccessor level, TrackNodeLocation vertex) {
		if (trackNetworks == null)
			return Collections.emptyList();
		ArrayList<TrackGraph> intersecting = new ArrayList<>();
		for (TrackGraph railGraph : trackNetworks.values())
			if (railGraph.locateNode(vertex) != null)
				intersecting.add(railGraph);
		return intersecting;
	}

	public void tick(Level level) {
		if (level.dimension() != Level.OVERWORLD)
			return;

		for (SignalEdgeGroup group : signalEdgeGroups.values()) {
			group.trains.clear();
			group.reserved = null;
		}

		for (TrackGraph graph : trackNetworks.values()) {
			graph.tickPoints(true);
			graph.resolveIntersectingEdgeGroups(level);
		}

		tickTrains(level);

		for (TrackGraph graph : trackNetworks.values()) {
			graph.tickPoints(false);
		}
	}

	private void tickTrains(Level level) {
		// keeping two lists ensures a tick order starting at longest waiting
		for (Train train : waitingTrains)
			train.earlyTick(level);
		for (Train train : movingTrains)
			train.earlyTick(level);
		for (Train train : waitingTrains)
			train.tick(level);
		for (Train train : movingTrains)
			train.tick(level);

		for (Iterator<Train> iterator = waitingTrains.iterator(); iterator.hasNext();) {
			Train train = iterator.next();

			if (train.invalid) {
				iterator.remove();
				trains.remove(train.id);
				continue;
			}

			if (train.navigation.waitingForSignal != null)
				continue;
			movingTrains.add(train);
			iterator.remove();
		}

		for (Iterator<Train> iterator = movingTrains.iterator(); iterator.hasNext();) {
			Train train = iterator.next();

			if (train.invalid) {
				iterator.remove();
				trains.remove(train.id);
				continue;
			}

			if (train.navigation.waitingForSignal == null)
				continue;
			waitingTrains.add(train);
			iterator.remove();
		}
	}

	/** Create's {@code sided}: there is only the server's manager. */
	public GlobalRailwayManager sided(@Nullable LevelAccessor level) {
		return this;
	}
}
