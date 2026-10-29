package dev.createpolymer.trains.track;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import com.mojang.serialization.Codec;
import java.util.Set;

import dev.createpolymer.foundation.block.ProperWaterloggedBlock;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.registry.feature.TrackRegistry;
import dev.createpolymer.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class TrackBlockEntity extends SmartBlockEntity {

	Map<BlockPos, BezierConnection> connections;
	boolean cancelDrops;

	public Pair<ResourceKey<Level>, BlockPos> boundLocation;
	public TrackBlockEntityTilt tilt;

	public TrackBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		connections = new HashMap<>();
		setLazyTickRate(100);
		tilt = new TrackBlockEntityTilt(this);
	}

	public Map<BlockPos, BezierConnection> getConnections() {
		return connections;
	}

	@Override
	public void tick() {
		super.tick();
		tilt.undoSmoothing();
	}

	@Override
	public void lazyTick() {
		for (BezierConnection connection : connections.values())
			if (connection.isPrimary())
				manageFakeTracksAlong(connection, false);
	}

	public void validateConnections() {
		Set<BlockPos> invalid = new HashSet<>();

		for (Entry<BlockPos, BezierConnection> entry : connections.entrySet()) {
			BlockPos key = entry.getKey();
			BezierConnection bc = entry.getValue();

			if (!key.equals(bc.getKey()) || !worldPosition.equals(bc.bePositions.getFirst())) {
				invalid.add(key);
				continue;
			}

			BlockState blockState = level.getBlockState(key);
			if (blockState.getBlock() instanceof ITrackBlock trackBlock && !blockState.getValue(TrackBlock.HAS_BE))
				for (Vec3 v : trackBlock.getTrackAxes(level, key, blockState)) {
					Vec3 bcEndAxis = bc.axes.getSecond();
					if (v.distanceTo(bcEndAxis) < 1 / 1024f || v.distanceTo(bcEndAxis.scale(-1)) < 1 / 1024f)
						level.setBlock(key, blockState.setValue(TrackBlock.HAS_BE, true), Block.UPDATE_ALL);
				}

			BlockEntity blockEntity = level.getBlockEntity(key);
			if (!(blockEntity instanceof TrackBlockEntity trackBE) || blockEntity.isRemoved()) {
				invalid.add(key);
				continue;
			}

			if (!trackBE.connections.containsKey(worldPosition)) {
				trackBE.addConnection(bc.secondary());
				trackBE.tilt.tryApplySmoothing();
			}
		}

		for (BlockPos blockPos : invalid)
			removeConnection(blockPos);
	}

	public void addConnection(BezierConnection connection) {
		// don't replace existing connections with different materials
		if (connections.containsKey(connection.getKey())
			&& connection.equalsSansMaterial(connections.get(connection.getKey())))
			return;
		connections.put(connection.getKey(), connection);
		level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
		notifyUpdate();

		if (connection.isPrimary())
			manageFakeTracksAlong(connection, false);
	}

	public void removeConnection(BlockPos target) {
		if (isTilted())
			tilt.captureSmoothingHandles();

		BezierConnection removed = connections.remove(target);
		notifyUpdate();

		if (removed != null)
			manageFakeTracksAlong(removed, true);

		if (!connections.isEmpty() || getBlockState().getOptionalValue(TrackBlock.SHAPE)
			.orElse(TrackShape.NONE)
			.isPortal())
			return;

		BlockState blockState = level.getBlockState(worldPosition);
		if (blockState.hasProperty(TrackBlock.HAS_BE))
			level.setBlockAndUpdate(worldPosition, blockState.setValue(TrackBlock.HAS_BE, false));
	}

	public void removeInboundConnections(boolean dropAndDiscard) {
		for (BezierConnection bezierConnection : connections.values()) {
			if (!(level.getBlockEntity(bezierConnection.getKey()) instanceof TrackBlockEntity tbe))
				return;
			tbe.removeConnection(bezierConnection.bePositions.getFirst());
			if (!dropAndDiscard)
				continue;
			if (!cancelDrops)
				bezierConnection.spawnItems(level);
			bezierConnection.spawnDestroyParticles(level);
		}
	}

	public void bind(ResourceKey<Level> boundDimension, BlockPos boundLocation) {
		this.boundLocation = Pair.of(boundDimension, boundLocation);
		setChanged();
	}

	public boolean isTilted() {
		return tilt.smoothingAngle.isPresent();
	}

	@Override
	protected void write(ValueOutput output) {
		super.write(output);
		writeTurns(output);
		if (isTilted())
			output.putDouble("Smoothing", tilt.smoothingAngle.get());
		if (boundLocation == null)
			return;
		output.store("BoundLocation", BlockPos.CODEC, boundLocation.getSecond());
		output.putString("BoundDimension", boundLocation.getFirst()
			.identifier()
			.toString());
	}

	private void writeTurns(ValueOutput output) {
		List<CompoundTag> list = connections.values()
			.stream()
			.map(bc -> bc.write(worldPosition))
			.toList();
		output.store("Connections", CompoundTag.CODEC.listOf(), list);
	}

	@Override
	protected void read(ValueInput input) {
		super.read(input);
		connections.clear();
		for (CompoundTag t : input.read("Connections", CompoundTag.CODEC.listOf())
			.orElse(List.of())) {
			BezierConnection connection = new BezierConnection(t, worldPosition);
			connections.put(connection.getKey(), connection);
		}

		tilt.smoothingAngle = input.read("Smoothing", Codec.DOUBLE);
		sendData();

		Optional<BlockPos> bound = input.read("BoundLocation", BlockPos.CODEC);
		if (bound.isPresent())
			boundLocation = Pair.of(ResourceKey.create(Registries.DIMENSION,
				Identifier.parse(input.getStringOr("BoundDimension", "minecraft:overworld"))), bound.get());
	}

	public boolean hasInteractableConnections() {
		for (BezierConnection connection : connections.values())
			if (connection.isPrimary())
				return true;
		return false;
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		// The track with its curves is replaced by another block (Create's TrackBlock.onRemove)
		if (level instanceof ServerLevel) {
			cancelDrops |= level.getBlockState(pos)
				.getBlock() == state.getBlock();
			removeInboundConnections(true);
			TrackPropagator.onRailRemoved(level, pos, state);
		}
		super.preRemoveSideEffects(pos, state);
	}

	@Override
	public void remove() {
		super.remove();

		for (BezierConnection connection : connections.values())
			manageFakeTracksAlong(connection, true);

		if (boundLocation != null && level instanceof ServerLevel) {
			ServerLevel otherLevel = level.getServer()
				.getLevel(boundLocation.getFirst());
			if (otherLevel == null)
				return;
			if (TrackRegistry.isTrack(otherLevel.getBlockState(boundLocation.getSecond())))
				otherLevel.destroyBlock(boundLocation.getSecond(), false);
		}
	}

	public void manageFakeTracksAlong(BezierConnection bc, boolean remove) {
		Map<Pair<Integer, Integer>, Double> yLevels = bc.rasterise();

		for (Entry<Pair<Integer, Integer>, Double> entry : yLevels.entrySet()) {
			double yValue = entry.getValue();
			int floor = Mth.floor(yValue);
			BlockPos targetPos = new BlockPos(entry.getKey()
				.getFirst(), floor,
				entry.getKey()
					.getSecond());
			targetPos = targetPos.offset(bc.bePositions.getFirst())
				.above(1);

			BlockState stateAtPos = level.getBlockState(targetPos);
			boolean present = TrackRegistry.FAKE_TRACK.defaultBlockState().getBlock() == stateAtPos.getBlock();

			if (remove) {
				if (present)
					level.removeBlock(targetPos, false);
				continue;
			}

			FluidState fluidState = stateAtPos.getFluidState();
			if (!fluidState.isEmpty() && !fluidState.isSourceOfType(Fluids.WATER))
				continue;

			if (!present && stateAtPos.canBeReplaced())
				level.setBlock(targetPos,
					ProperWaterloggedBlock.withWater(level, TrackRegistry.FAKE_TRACK.defaultBlockState(), targetPos),
					Block.UPDATE_ALL);
			FakeTrackBlock.keepAlive(level, targetPos);
		}
	}

}
