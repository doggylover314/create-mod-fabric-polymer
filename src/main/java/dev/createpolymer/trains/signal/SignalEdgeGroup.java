package dev.createpolymer.trains.signal;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.apache.commons.lang3.mutable.MutableInt;

import com.google.common.base.Predicates;
import dev.createpolymer.registry.feature.TrackRegistry;
import dev.createpolymer.trains.entity.Train;

import dev.createpolymer.util.NBTHelper;
import net.minecraft.nbt.CompoundTag;
import dev.createpolymer.trains.TrackNbt;

public class SignalEdgeGroup {

	public UUID id;
	public EdgeGroupColor color;

	public Set<Train> trains;
	public SignalBoundary reserved;

	public Map<UUID, UUID> intersecting;
	public Set<SignalEdgeGroup> intersectingResolved;
	public Set<UUID> adjacent;

	public boolean fallbackGroup;

	public SignalEdgeGroup(UUID id) {
		this.id = id;
		trains = new HashSet<>();
		adjacent = new HashSet<>();
		intersecting = new HashMap<>();
		intersectingResolved = new HashSet<>();
		color = EdgeGroupColor.getDefault();
	}

	public SignalEdgeGroup asFallback() {
		fallbackGroup = true;
		return this;
	}

	public boolean isOccupiedUnless(Train train) {
		if (intersectingResolved.isEmpty())
			walkIntersecting(intersectingResolved::add);
		for (SignalEdgeGroup group : intersectingResolved)
			if (group.isThisOccupiedUnless(train))
				return true;
		return false;
	}

	private boolean isThisOccupiedUnless(Train train) {
		return reserved != null || trains.size() > 1 || !trains.contains(train) && !trains.isEmpty();
	}

	public boolean isOccupiedUnless(SignalBoundary boundary) {
		if (intersectingResolved.isEmpty())
			walkIntersecting(intersectingResolved::add);
		for (SignalEdgeGroup group : intersectingResolved)
			if (group.isThisOccupiedUnless(boundary))
				return true;
		return false;
	}

	private boolean isThisOccupiedUnless(SignalBoundary boundary) {
		return !trains.isEmpty() || reserved != null && reserved != boundary;
	}

	public void putIntersection(UUID intersectionId, UUID targetGroup) {
		intersecting.put(intersectionId, targetGroup);
		walkIntersecting(g -> g.intersectingResolved.clear());
		resolveColor();
	}

	public void removeIntersection(UUID intersectionId) {
		walkIntersecting(g -> g.intersectingResolved.clear());

		UUID removed = intersecting.remove(intersectionId);
		SignalEdgeGroup other = TrackRegistry.RAILWAYS.signalEdgeGroups.get(removed);
		if (other != null)
			other.intersecting.remove(intersectionId);

		resolveColor();
	}

	public void putAdjacent(UUID adjacent) {
		this.adjacent.add(adjacent);
	}

	public void removeAdjacent(UUID adjacent) {
		this.adjacent.remove(adjacent);
	}

	public void resolveColor() {
		if (intersectingResolved.isEmpty())
			walkIntersecting(intersectingResolved::add);

		MutableInt mask = new MutableInt(0);
		intersectingResolved.forEach(group -> group.adjacent.stream()
			.map(TrackRegistry.RAILWAYS.signalEdgeGroups::get)
			.filter(Objects::nonNull)
			.filter(Predicates.not(intersectingResolved::contains))
			.forEach(adjacent -> mask.setValue(adjacent.color.strikeFrom(mask.getValue()))));

		EdgeGroupColor newColour = EdgeGroupColor.findNextAvailable(mask.getValue());
		if (newColour == color)
			return;

		walkIntersecting(group -> group.color = newColour);
		TrackRegistry.RAILWAYS.markTracksDirty();
	}

	private void walkIntersecting(Consumer<SignalEdgeGroup> callback) {
		walkIntersectingRec(new HashSet<>(), callback);
	}

	private void walkIntersectingRec(Set<SignalEdgeGroup> visited, Consumer<SignalEdgeGroup> callback) {
		if (!visited.add(this))
			return;
		callback.accept(this);
		for (UUID uuid : intersecting.values()) {
			SignalEdgeGroup group = TrackRegistry.RAILWAYS.signalEdgeGroups.get(uuid);
			if (group != null)
				group.walkIntersectingRec(visited, callback);
		}
	}

	public static SignalEdgeGroup read(CompoundTag tag) {
		SignalEdgeGroup group = new SignalEdgeGroup(TrackNbt.getUUID(tag, "Id"));
		group.color = NBTHelper.readEnum(tag, "Color", EdgeGroupColor.class);
		NBTHelper.iterateCompoundList(tag.getListOrEmpty("Connected"),
			nbt -> group.intersecting.put(TrackNbt.getUUID(nbt, "Key"), TrackNbt.getUUID(nbt, "Value")));
		group.fallbackGroup = tag.getBooleanOr("Fallback", false);
		return group;
	}

	public CompoundTag write() {
		CompoundTag tag = new CompoundTag();
		TrackNbt.putUUID(tag, "Id", id);
		NBTHelper.writeEnum(tag, "Color", color);
		tag.put("Connected", NBTHelper.writeCompoundList(intersecting.entrySet(), e -> {
			CompoundTag nbt = new CompoundTag();
			TrackNbt.putUUID(nbt, "Key", e.getKey());
			TrackNbt.putUUID(nbt, "Value", e.getValue());
			return nbt;
		}));
		tag.putBoolean("Fallback", fallbackGroup);
		return tag;
	}

}
