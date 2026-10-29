package dev.createpolymer.trains.signal;

import java.util.UUID;

import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.registry.feature.TrackRegistry;
import dev.createpolymer.trains.TrackNbt;
import dev.createpolymer.trains.graph.DimensionPalette;
import dev.createpolymer.trains.graph.EdgePointType;
import dev.createpolymer.trains.graph.TrackEdge;
import dev.createpolymer.trains.graph.TrackGraph;
import dev.createpolymer.trains.graph.TrackNode;
import dev.createpolymer.trains.graph.TrackNodeLocation;
import dev.createpolymer.trains.track.TrackTargetingBehaviour;
import dev.createpolymer.util.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;

public abstract class TrackEdgePoint {

	public UUID id;
	public Couple<TrackNodeLocation> edgeLocation;
	public double position;
	private EdgePointType<?> type;

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getId() {
		return id;
	}

	public void setType(EdgePointType<?> type) {
		this.type = type;
	}

	public EdgePointType<?> getType() {
		return type;
	}

	public abstract boolean canMerge();

	public boolean canCoexistWith(EdgePointType<?> otherType, boolean front) {
		return false;
	}

	public abstract void invalidate(LevelAccessor level);

	protected void invalidateAt(LevelAccessor level, BlockPos blockEntityPos) {
		TrackTargetingBehaviour<?> behaviour =
			BlockEntityBehaviour.get(level, blockEntityPos, TrackTargetingBehaviour.TYPE);
		if (behaviour == null)
			return;
		CompoundTag migrationData = new CompoundTag();
		DimensionPalette dimensions = new DimensionPalette();
		write(migrationData, level.registryAccess(), dimensions);
		dimensions.write(migrationData);
		behaviour.invalidateEdgePoint(migrationData);
	}

	public abstract void blockEntityAdded(BlockEntity blockEntity, boolean front);

	public abstract void blockEntityRemoved(BlockPos blockEntityPos, boolean front);

	public void onRemoved(TrackGraph graph) {}

	public void setLocation(Couple<TrackNodeLocation> nodes, double position) {
		this.edgeLocation = nodes;
		this.position = position;
	}

	public double getLocationOn(TrackEdge edge) {
		return isPrimary(edge.node1) ? edge.getLength() - position : position;
	}

	public boolean canNavigateVia(TrackNode side) {
		return true;
	}

	public boolean isPrimary(TrackNode node1) {
		return edgeLocation.getSecond()
			.equals(node1.getLocation());
	}

	public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean migration, DimensionPalette dimensions) {
		if (migration)
			return;

		id = TrackNbt.getUUID(nbt, "Id");
		position = nbt.getDoubleOr("Position", 0);
		edgeLocation = Couple.deserializeEach(nbt.getListOrEmpty("Edge"),
			tag -> TrackNodeLocation.read(tag, dimensions));
	}

	public void write(CompoundTag nbt, HolderLookup.Provider registries, DimensionPalette dimensions) {
		TrackNbt.putUUID(nbt, "Id", id);
		nbt.putDouble("Position", position);
		nbt.put("Edge", edgeLocation.serializeEach(loc -> loc.write(dimensions)));
	}

	public void tick(TrackGraph graph, boolean preTrains) {}

	protected void removeFromAllGraphs() {
		for (TrackGraph trackGraph : TrackRegistry.RAILWAYS.trackNetworks.values())
			if (trackGraph.removePoint(getType(), id) != null)
				return;
	}

}
