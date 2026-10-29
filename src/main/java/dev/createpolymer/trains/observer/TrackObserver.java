package dev.createpolymer.trains.observer;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.logistics.filter.FilterItemStack;
import dev.createpolymer.registry.feature.TrackRegistry;
import dev.createpolymer.trains.TrackNbt;
import dev.createpolymer.trains.entity.Train;
import dev.createpolymer.trains.graph.DimensionPalette;
import dev.createpolymer.trains.graph.TrackEdge;
import dev.createpolymer.trains.graph.TrackGraph;
import dev.createpolymer.trains.signal.SignalPropagator;
import dev.createpolymer.trains.signal.SingleBlockEntityEdgePoint;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class TrackObserver extends SingleBlockEntityEdgePoint {

	private int activated;
	private FilterItemStack filter;
	private UUID currentTrain;

	public TrackObserver() {
		activated = 0;
		filter = FilterItemStack.empty();
		currentTrain = null;
	}

	@Override
	public void blockEntityAdded(BlockEntity blockEntity, boolean front) {
		super.blockEntityAdded(blockEntity, front);
		FilteringBehaviour filteringBehaviour = BlockEntityBehaviour.get(blockEntity, FilteringBehaviour.TYPE);
		if (filteringBehaviour != null)
			setFilterAndNotify(blockEntity.getLevel(), filteringBehaviour.getFilter());
	}

	@Override
	public void tick(TrackGraph graph, boolean preTrains) {
		super.tick(graph, preTrains);
		if (isActivated())
			activated--;
		if (!isActivated())
			currentTrain = null;
	}

	public void setFilterAndNotify(Level level, ItemStack filter) {
		this.filter = FilterItemStack.of(filter.copy());
		notifyTrains(level);
	}

	private void notifyTrains(Level level) {
		TrackGraph graph = TrackRegistry.RAILWAYS.sided(level)
			.getGraph(level, edgeLocation.getFirst());
		if (graph == null)
			return;
		TrackEdge edge = graph.getConnection(edgeLocation.map(graph::locateNode));
		if (edge == null)
			return;
		SignalPropagator.notifyTrains(graph, edge);
	}

	public FilterItemStack getFilter() {
		return filter;
	}

	@Nullable
	public UUID getCurrentTrain() {
		return currentTrain;
	}

	public boolean isActivated() {
		return activated > 0;
	}

	public void keepAlive(Train train) {
		activated = 8;
		currentTrain = train.id;
	}

	/** Create tests the filter directly; this is {@link FilterItemStack#test(Level, FluidStack)}. */
	public static boolean testFluid(Level level, FilterItemStack filter, FluidStack stack) {
		return filter.test(level, stack);
	}

	@Override
	public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean migration, DimensionPalette dimensions) {
		super.read(nbt, registries, migration, dimensions);
		activated = nbt.getIntOr("Activated", 0);
		filter = FilterItemStack.of(nbt.read("Filter", ItemStack.OPTIONAL_CODEC, registries.createSerializationContext(NbtOps.INSTANCE))
			.orElse(ItemStack.EMPTY));
		if (nbt.contains("TrainId"))
			currentTrain = TrackNbt.getUUID(nbt, "TrainId");
	}

	@Override
	public void write(CompoundTag nbt, HolderLookup.Provider registries, DimensionPalette dimensions) {
		super.write(nbt, registries, dimensions);
		nbt.putInt("Activated", activated);
		nbt.store("Filter", ItemStack.OPTIONAL_CODEC, registries.createSerializationContext(NbtOps.INSTANCE),
			filter.item());
		if (currentTrain != null)
			TrackNbt.putUUID(nbt, "TrainId", currentTrain);
	}

}
