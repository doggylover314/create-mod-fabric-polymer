package dev.createpolymer.trains.graph;

import java.util.UUID;

import dev.createpolymer.util.Couple;
import net.minecraft.nbt.CompoundTag;
import dev.createpolymer.trains.TrackNbt;

public class TrackEdgeIntersection {

	public double location;
	public Couple<TrackNodeLocation> target;
	public double targetLocation;
	public UUID groupId;
	public UUID id;

	public TrackEdgeIntersection() {
		id = UUID.randomUUID();
	}

	public boolean isNear(double location) {
		return Math.abs(location - this.location) < 1 / 32f;
	}

	public boolean targets(TrackNodeLocation target1, TrackNodeLocation target2) {
		return target1.equals(target.getFirst()) && target2.equals(target.getSecond())
			|| target1.equals(target.getSecond()) && target2.equals(target.getFirst());
	}

	public CompoundTag write(DimensionPalette dimensions) {
		CompoundTag nbt = new CompoundTag();
		TrackNbt.putUUID(nbt, "Id", id);
		if (groupId != null)
			TrackNbt.putUUID(nbt, "GroupId", groupId);
		nbt.putDouble("Location", location);
		nbt.putDouble("TargetLocation", targetLocation);
		nbt.put("TargetEdge", target.serializeEach(loc -> loc.write(dimensions)));
		return nbt;
	}

	public static TrackEdgeIntersection read(CompoundTag nbt, DimensionPalette dimensions) {
		TrackEdgeIntersection intersection = new TrackEdgeIntersection();
		intersection.id = TrackNbt.getUUID(nbt, "Id");
		if (nbt.contains("GroupId"))
			intersection.groupId = TrackNbt.getUUID(nbt, "GroupId");
		intersection.location = nbt.getDoubleOr("Location", 0);
		intersection.targetLocation = nbt.getDoubleOr("TargetLocation", 0);
		intersection.target = Couple.deserializeEach(nbt.getListOrEmpty("TargetEdge"),
			tag -> TrackNodeLocation.read(tag, dimensions));
		return intersection;
	}

}
