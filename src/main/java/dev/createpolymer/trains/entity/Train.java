package dev.createpolymer.trains.entity;

import java.util.Map;
import java.util.UUID;

import dev.createpolymer.trains.TrackNbt;
import dev.createpolymer.trains.graph.DimensionPalette;
import dev.createpolymer.trains.graph.TrackGraph;
import dev.createpolymer.trains.graph.TrackNode;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/**
 * Create's Train. This holds the hooks the track graph and the railway manager need (which graph a train is on, the
 * per-tick calls, the saving); the carriages, bogeys and driving come with the trains.
 */
public class Train {

	public UUID id;
	public TrackGraph graph;
	public Navigation navigation = new Navigation();
	public boolean invalid;

	public void earlyTick(Level level) {}

	public void tick(Level level) {}

	public boolean isTravellingOn(TrackNode node) {
		return false;
	}

	public void detachFromTracks() {}

	public CompoundTag write(DimensionPalette dimensions, HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		TrackNbt.putUUID(tag, "Id", id);
		return tag;
	}

	public static Train read(CompoundTag tag, HolderLookup.Provider registries, Map<UUID, TrackGraph> trackNetworks,
		DimensionPalette dimensions) {
		Train train = new Train();
		train.id = TrackNbt.getUUID(tag, "Id");
		return train;
	}

}
