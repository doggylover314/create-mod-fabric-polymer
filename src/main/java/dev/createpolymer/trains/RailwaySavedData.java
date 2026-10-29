package dev.createpolymer.trains;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.registry.feature.TrackRegistry;
import dev.createpolymer.trains.entity.Train;
import dev.createpolymer.trains.graph.DimensionPalette;
import dev.createpolymer.trains.graph.EdgePointType;
import dev.createpolymer.trains.graph.TrackGraph;
import dev.createpolymer.trains.signal.SignalBoundary;
import dev.createpolymer.trains.signal.SignalEdgeGroup;
import dev.createpolymer.util.NBTHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The railway data of the world, saved in the overworld's data storage ({@code data/create/tracks.dat}). The maps
 * of {@link TrackRegistry#RAILWAYS} are the maps of the loaded instance, so saving writes what the manager holds.
 */
public class RailwaySavedData extends SavedData {

	public static final Codec<RailwaySavedData> CODEC = CompoundTag.CODEC.xmap(RailwaySavedData::fromTag, RailwaySavedData::toTag);

	// Create's data has no vanilla data fixer; this type keeps the tag away from the ones that change with Minecraft.
	public static final SavedDataType<RailwaySavedData> TYPE = new SavedDataType<>(CreatePolymer.id("tracks"),
		RailwaySavedData::new, CODEC, DataFixTypes.SAVED_DATA_STOPWATCHES);

	private Map<UUID, TrackGraph> trackNetworks = new HashMap<>();
	private Map<UUID, SignalEdgeGroup> signalEdgeGroups = new HashMap<>();
	private Map<UUID, Train> trains = new HashMap<>();

	public static CompoundTag toTag(RailwaySavedData sd) {
		return sd.save(new CompoundTag(), TrackRegistry.RAILWAYS.registries());
	}

	public CompoundTag save(CompoundTag nbt, HolderLookup.Provider registries) {
		DimensionPalette dimensions = new DimensionPalette();
		nbt.put("RailGraphs", NBTHelper.writeCompoundList(trackNetworks.values(), tg -> tg.write(registries, dimensions)));
		nbt.put("SignalBlocks", NBTHelper.writeCompoundList(signalEdgeGroups.values(), seg -> {
			if (seg.fallbackGroup && !trackNetworks.containsKey(seg.id))
				return null;
			return seg.write();
		}));
		nbt.put("Trains", NBTHelper.writeCompoundList(trains.values(), t -> t.write(dimensions, registries)));
		dimensions.write(nbt);
		return nbt;
	}

	public static RailwaySavedData fromTag(CompoundTag nbt) {
		return load(nbt, TrackRegistry.RAILWAYS.registries());
	}

	public static RailwaySavedData load(CompoundTag nbt, HolderLookup.Provider registries) {
		RailwaySavedData sd = new RailwaySavedData();
		sd.trackNetworks = new HashMap<>();
		sd.signalEdgeGroups = new HashMap<>();
		sd.trains = new HashMap<>();

		DimensionPalette dimensions = DimensionPalette.read(nbt);
		NBTHelper.iterateCompoundList(nbt.getListOrEmpty("RailGraphs"), c -> {
			TrackGraph graph = TrackGraph.read(c, registries, dimensions);
			sd.trackNetworks.put(graph.id, graph);
		});
		NBTHelper.iterateCompoundList(nbt.getListOrEmpty("SignalBlocks"), c -> {
			SignalEdgeGroup group = SignalEdgeGroup.read(c);
			sd.signalEdgeGroups.put(group.id, group);
		});
		NBTHelper.iterateCompoundList(nbt.getListOrEmpty("Trains"), c -> {
			Train train = Train.read(c, registries, sd.trackNetworks, dimensions);
			sd.trains.put(train.id, train);
		});

		for (TrackGraph graph : sd.trackNetworks.values()) {
			for (SignalBoundary signal : graph.getPoints(EdgePointType.SIGNAL)) {
				UUID groupId = signal.groups.getFirst();
				UUID otherGroupId = signal.groups.getSecond();
				if (groupId == null || otherGroupId == null)
					continue;
				SignalEdgeGroup group = sd.signalEdgeGroups.get(groupId);
				SignalEdgeGroup otherGroup = sd.signalEdgeGroups.get(otherGroupId);
				if (group == null || otherGroup == null)
					continue;
				group.putAdjacent(otherGroupId);
				otherGroup.putAdjacent(groupId);
			}
		}

		return sd;
	}

	public Map<UUID, TrackGraph> getTrackNetworks() {
		return trackNetworks;
	}

	public Map<UUID, Train> getTrains() {
		return trains;
	}

	public Map<UUID, SignalEdgeGroup> getSignalBlocks() {
		return signalEdgeGroups;
	}

	private RailwaySavedData() {}

	public static RailwaySavedData load(MinecraftServer server) {
		return server.overworld()
			.getDataStorage()
			.computeIfAbsent(TYPE);
	}

}
