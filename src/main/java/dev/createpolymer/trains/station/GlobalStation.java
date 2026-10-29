package dev.createpolymer.trains.station;

import java.lang.ref.WeakReference;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.trains.entity.Train;
import dev.createpolymer.trains.graph.DimensionPalette;
import dev.createpolymer.trains.graph.TrackNode;
import dev.createpolymer.trains.signal.SingleBlockEntityEdgePoint;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class GlobalStation extends SingleBlockEntityEdgePoint {

	public String name;
	public WeakReference<Train> nearestTrain;
	public boolean assembling;

	public GlobalStation() {
		name = "Track Station";
		nearestTrain = new WeakReference<>(null);
	}

	@Override
	public void blockEntityAdded(BlockEntity blockEntity, boolean front) {
		super.blockEntityAdded(blockEntity, front);
		BlockState state = blockEntity.getBlockState();
		assembling =
			state != null && state.hasProperty(StationBlock.ASSEMBLING) && state.getValue(StationBlock.ASSEMBLING);
	}

	@Override
	public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean migration, DimensionPalette dimensions) {
		super.read(nbt, registries, migration, dimensions);
		name = nbt.getStringOr("Name", "");
		assembling = nbt.getBooleanOr("Assembling", false);
		nearestTrain = new WeakReference<>(null);
	}

	@Override
	public void write(CompoundTag nbt, HolderLookup.Provider registries, DimensionPalette dimensions) {
		super.write(nbt, registries, dimensions);
		nbt.putString("Name", name);
		nbt.putBoolean("Assembling", assembling);
	}

	public boolean canApproachFrom(TrackNode side) {
		return isPrimary(side) && !assembling;
	}

	@Override
	public boolean canNavigateVia(TrackNode side) {
		return super.canNavigateVia(side) && !assembling;
	}

	public void reserveFor(Train train) {
		Train nearestTrain = getNearestTrain();
		if (nearestTrain == null
			|| nearestTrain.navigation.distanceToDestination > train.navigation.distanceToDestination)
			this.nearestTrain = new WeakReference<>(train);
	}

	public void cancelReservation(Train train) {
		if (nearestTrain.get() == train)
			nearestTrain = new WeakReference<>(null);
	}

	public void trainDeparted(Train train) {
		cancelReservation(train);
	}

	@Nullable
	public Train getPresentTrain() {
		Train nearestTrain = getNearestTrain();
		if (nearestTrain == null || nearestTrain.getCurrentStation() != this)
			return null;
		return nearestTrain;
	}

	@Nullable
	public Train getImminentTrain() {
		Train nearestTrain = getNearestTrain();
		if (nearestTrain == null)
			return nearestTrain;
		if (nearestTrain.getCurrentStation() == this)
			return nearestTrain;
		if (!nearestTrain.navigation.isActive())
			return null;
		if (nearestTrain.navigation.distanceToDestination > 30)
			return null;
		return nearestTrain;
	}

	@Nullable
	public Train getNearestTrain() {
		return this.nearestTrain.get();
	}

	/**
	 * Create moves packages between the train's cargo and the package ports (postboxes) of the station here. The
	 * postbox does not connect to stations yet, so there is nothing to move.
	 */
	public void runMailTransfer() {}

}
