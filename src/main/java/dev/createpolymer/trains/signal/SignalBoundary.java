package dev.createpolymer.trains.signal;

import java.util.UUID;

import dev.createpolymer.trains.TrackNbt;
import dev.createpolymer.trains.graph.DimensionPalette;
import dev.createpolymer.trains.graph.EdgePointType;
import dev.createpolymer.trains.graph.TrackNode;
import dev.createpolymer.trains.signal.SignalBlock.SignalType;
import dev.createpolymer.util.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Create's signal edge point. This is the part the track graph depends on (the signal groups of both sides and their
 * saving); the signal block, its states and the train interaction come with the signals.
 */
public class SignalBoundary extends TrackEdgePoint {

	public Couple<UUID> groups;
	public Couple<SignalType> types;

	public SignalBoundary() {
		groups = Couple.create(null, null);
		types = Couple.create(SignalType.ENTRY_SIGNAL, SignalType.ENTRY_SIGNAL);
	}

	/** Whether redstone holds the signal red for trains entering from this side. */
	public boolean isForcedRed(TrackNode side) {
		return false;
	}

	/** Same, for the side of the primary (true) or the other group. */
	public boolean isForcedRed(boolean primary) {
		return false;
	}

	public UUID getGroup(TrackNode side) {
		return groups.get(isPrimary(side));
	}

	@Override
	public boolean canCoexistWith(EdgePointType<?> otherType, boolean front) {
		return otherType == EdgePointType.OBSERVER;
	}

	@Override
	public boolean canMerge() {
		return true;
	}

	@Override
	public void invalidate(LevelAccessor level) {}

	@Override
	public void blockEntityAdded(BlockEntity blockEntity, boolean front) {}

	@Override
	public void blockEntityRemoved(BlockPos blockEntityPos, boolean front) {
		removeFromAllGraphs();
	}

	@Override
	public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean migration, DimensionPalette dimensions) {
		super.read(nbt, registries, migration, dimensions);
		if (migration)
			return;
		groups = Couple.create(null, null);
		if (nbt.contains("Group1"))
			groups.setFirst(TrackNbt.getUUID(nbt, "Group1"));
		if (nbt.contains("Group2"))
			groups.setSecond(TrackNbt.getUUID(nbt, "Group2"));
	}

	@Override
	public void write(CompoundTag nbt, HolderLookup.Provider registries, DimensionPalette dimensions) {
		super.write(nbt, registries, dimensions);
		if (groups.getFirst() != null)
			TrackNbt.putUUID(nbt, "Group1", groups.getFirst());
		if (groups.getSecond() != null)
			TrackNbt.putUUID(nbt, "Group2", groups.getSecond());
	}

}
