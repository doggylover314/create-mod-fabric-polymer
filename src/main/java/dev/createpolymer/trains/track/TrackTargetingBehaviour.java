package dev.createpolymer.trains.track;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.contraptions.StructureTransform;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BehaviourType;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.registry.feature.TrackRegistry;
import dev.createpolymer.trains.graph.DimensionPalette;
import dev.createpolymer.trains.graph.EdgeData;
import dev.createpolymer.trains.graph.EdgePointType;
import dev.createpolymer.trains.graph.TrackEdge;
import dev.createpolymer.trains.graph.TrackGraph;
import dev.createpolymer.trains.graph.TrackGraphHelper;
import dev.createpolymer.trains.graph.TrackGraphLocation;
import dev.createpolymer.trains.graph.TrackNode;
import dev.createpolymer.trains.signal.SingleBlockEntityEdgePoint;
import dev.createpolymer.trains.signal.TrackEdgePoint;
import dev.createpolymer.util.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class TrackTargetingBehaviour<T extends TrackEdgePoint> extends BlockEntityBehaviour {

	public static final BehaviourType<TrackTargetingBehaviour<?>> TYPE = new BehaviourType<>();

	private BlockPos targetTrack;
	private BezierTrackPointLocation targetBezier;
	private AxisDirection targetDirection;
	private UUID id;

	private Vec3 prevDirection;
	private Vec3 rotatedDirection;

	private CompoundTag migrationData;
	private EdgePointType<T> edgePointType;
	private T edgePoint;
	private boolean orthogonal;

	public TrackTargetingBehaviour(SmartBlockEntity be, EdgePointType<T> edgePointType) {
		super(be);
		this.edgePointType = edgePointType;
		targetDirection = AxisDirection.POSITIVE;
		targetTrack = BlockPos.ZERO;
		id = UUID.randomUUID();
		migrationData = null;
		orthogonal = false;
	}

	@Override
	public void write(ValueOutput nbt) {
		nbt.store("Id", UUIDUtil.CODEC, id);
		nbt.store("TargetTrack", BlockPos.CODEC, targetTrack);
		nbt.putBoolean("Ortho", orthogonal);
		nbt.putBoolean("TargetDirection", targetDirection == AxisDirection.POSITIVE);
		if (rotatedDirection != null)
			nbt.store("RotatedAxis", Vec3.CODEC, rotatedDirection);
		if (prevDirection != null)
			nbt.store("PrevAxis", Vec3.CODEC, prevDirection);
		if (migrationData != null)
			nbt.store("Migrate", CompoundTag.CODEC, migrationData);
		if (targetBezier != null) {
			ValueOutput bezierNbt = nbt.child("Bezier");
			bezierNbt.putInt("Segment", targetBezier.segment());
			bezierNbt.store("Key", BlockPos.CODEC, targetBezier.curveTarget()
				.subtract(getPos()));
		}
		super.write(nbt);
	}

	@Override
	public void read(ValueInput nbt) {
		id = nbt.read("Id", UUIDUtil.CODEC)
			.orElseGet(UUID::randomUUID);
		targetTrack = nbt.read("TargetTrack", BlockPos.CODEC)
			.orElse(BlockPos.ZERO);
		targetDirection = nbt.getBooleanOr("TargetDirection", false) ? AxisDirection.POSITIVE : AxisDirection.NEGATIVE;
		orthogonal = nbt.getBooleanOr("Ortho", false);
		prevDirection = nbt.read("PrevAxis", Vec3.CODEC)
			.orElse(null);
		rotatedDirection = nbt.read("RotatedAxis", Vec3.CODEC)
			.orElse(null);
		migrationData = nbt.read("Migrate", CompoundTag.CODEC)
			.orElse(null);
		nbt.child("Bezier")
			.ifPresentOrElse(bezierNbt -> {
				BlockPos key = bezierNbt.read("Key", BlockPos.CODEC)
					.orElse(BlockPos.ZERO);
				targetBezier = new BezierTrackPointLocation(key.offset(getPos()), bezierNbt.getIntOr("Segment", 0));
			}, () -> targetBezier = null);
		super.read(nbt);
	}

	@Nullable
	public T getEdgePoint() {
		return edgePoint;
	}

	public void invalidateEdgePoint(CompoundTag migrationData) {
		this.migrationData = migrationData;
		edgePoint = null;
		blockEntity.sendData();
	}

	@Override
	public void tick() {
		super.tick();
		if (edgePoint == null)
			edgePoint = createEdgePoint();
	}

	@SuppressWarnings("unchecked")
	public T createEdgePoint() {
		Level level = getWorld();
		boolean isClientSide = level.isClientSide();
		if (migrationData == null || isClientSide)
			for (TrackGraph trackGraph : TrackRegistry.RAILWAYS.sided(level).trackNetworks.values()) {
				T point = trackGraph.getPoint(edgePointType, id);
				if (point == null)
					continue;
				return point;
			}

		if (isClientSide)
			return null;
		if (!hasValidTrack())
			return null;
		TrackGraphLocation loc = determineGraphLocation();
		if (loc == null)
			return null;

		TrackGraph graph = loc.graph;
		TrackNode node1 = graph.locateNode(loc.edge.getFirst());
		TrackNode node2 = graph.locateNode(loc.edge.getSecond());
		TrackEdge edge = graph.getConnectionsFrom(node1)
			.get(node2);
		if (edge == null)
			return null;

		T point = edgePointType.create();
		boolean front = getTargetDirection() == AxisDirection.POSITIVE;

		prevDirection = edge.getDirectionAt(loc.position)
			.scale(front ? -1 : 1);

		if (rotatedDirection != null) {
			double dot = prevDirection.dot(rotatedDirection);
			if (dot < -.85f) {
				rotatedDirection = null;
				targetDirection = targetDirection.opposite();
				return null;
			}

			rotatedDirection = null;
		}

		double length = edge.getLength();
		CompoundTag data = migrationData;
		migrationData = null;

		{
			orthogonal = targetBezier == null;
			Vec3 direction = edge.getDirection(true);
			int nonZeroComponents = 0;
			for (Axis axis : Iterate.axes)
				nonZeroComponents += direction.get(axis) != 0 ? 1 : 0;
			orthogonal &= nonZeroComponents <= 1;
		}

		EdgeData signalData = edge.getEdgeData();
		if (signalData.hasPoints()) {
			for (EdgePointType<?> otherType : EdgePointType.TYPES.values()) {
				TrackEdgePoint otherPoint = signalData.get(otherType, loc.position);
				if (otherPoint == null)
					continue;
				if (otherType != edgePointType) {
					if (!otherPoint.canCoexistWith(edgePointType, front))
						return null;
					continue;
				}
				if (!otherPoint.canMerge())
					return null;
				otherPoint.blockEntityAdded(blockEntity, front);
				id = otherPoint.getId();
				blockEntity.notifyUpdate();
				return (T) otherPoint;
			}
		}

		if (data != null)
			point.read(data, level.registryAccess(), true, DimensionPalette.read(data));

		point.setId(id);
		boolean reverseEdge = front || point instanceof SingleBlockEntityEdgePoint;
		point.setLocation(reverseEdge ? loc.edge : loc.edge.swap(), reverseEdge ? loc.position : length - loc.position);
		point.blockEntityAdded(blockEntity, front);
		loc.graph.addPoint(edgePointType, point);
		blockEntity.sendData();
		return point;
	}

	@Override
	public void destroy() {
		super.destroy();
		if (edgePoint != null && !getWorld().isClientSide())
			edgePoint.blockEntityRemoved(getPos(), getTargetDirection() == AxisDirection.POSITIVE);
	}

	@Override
	public BehaviourType<?> getType() {
		return TYPE;
	}

	public boolean isOnCurve() {
		return targetBezier != null;
	}

	public boolean isOrthogonal() {
		return orthogonal;
	}

	public boolean hasValidTrack() {
		return getTrackBlockState().getBlock() instanceof ITrackBlock;
	}

	public ITrackBlock getTrack() {
		return (ITrackBlock) getTrackBlockState().getBlock();
	}

	public BlockState getTrackBlockState() {
		return getWorld().getBlockState(getGlobalPosition());
	}

	public BlockPos getGlobalPosition() {
		return targetTrack.offset(blockEntity.getBlockPos());
	}

	public BlockPos getPositionForMapMarker() {
		BlockPos target = targetTrack.offset(blockEntity.getBlockPos());
		if (targetBezier != null && getWorld().getBlockEntity(target) instanceof TrackBlockEntity tbe) {
			BezierConnection bc = tbe.getConnections()
				.get(targetBezier.curveTarget());
			if (bc == null)
				return target;
			double length = Mth.floor(bc.getLength() * 2);
			int seg = targetBezier.segment() + 1;
			double t = seg / length;
			return BlockPos.containing(bc.getPosition(t));
		}
		return target;
	}

	public AxisDirection getTargetDirection() {
		return targetDirection;
	}

	public BezierTrackPointLocation getTargetBezier() {
		return targetBezier;
	}

	public TrackGraphLocation determineGraphLocation() {
		Level level = getWorld();
		BlockPos pos = getGlobalPosition();
		BlockState state = getTrackBlockState();
		ITrackBlock track = getTrack();
		List<Vec3> trackAxes = track.getTrackAxes(level, pos, state);
		AxisDirection targetDirection = getTargetDirection();

		return targetBezier != null
			? TrackGraphHelper.getBezierGraphLocationAt(level, pos, targetDirection, targetBezier)
			: TrackGraphHelper.getGraphLocationAt(level, pos, targetDirection, trackAxes.get(0));
	}

	public void transform(BlockEntity be, StructureTransform transform) {
		id = UUID.randomUUID();
		targetTrack = transform.applyWithoutOffset(targetTrack);
		if (prevDirection != null)
			rotatedDirection = transform.applyWithoutOffsetUncentered(prevDirection);
		if (targetBezier != null)
			targetBezier = new BezierTrackPointLocation(transform.applyWithoutOffset(targetBezier.curveTarget()
				.subtract(getPos()))
				.offset(getPos()), targetBezier.segment());
		blockEntity.notifyUpdate();
	}

	public static enum RenderedTrackOverlayType {
		STATION, SIGNAL, DUAL_SIGNAL, OBSERVER;
	}

}
