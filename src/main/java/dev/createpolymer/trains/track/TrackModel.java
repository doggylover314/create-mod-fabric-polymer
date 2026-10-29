package dev.createpolymer.trains.track;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import dev.createpolymer.util.Couple;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.VecHelper;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.InteractionElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What players see of a track block: the track model of its shape, and for every curve that starts here (the primary
 * end) a tie and two rails per segment, placed like Create's renderer does. Every other segment also gets an
 * invisible interaction box, which is what a player points at to break the curve or to put a signal on it.
 */
public class TrackModel extends BaseModel {

	/** Create's models for the three pieces of a curve segment. */
	public static final ItemStack TIE = ModelItems.block("track/tie");
	public static final ItemStack SEGMENT_LEFT = ModelItems.block("track/segment_left");
	public static final ItemStack SEGMENT_RIGHT = ModelItems.block("track/segment_right");

	private final Part main;
	private TrackShape shape;
	private double appliedTilt = Double.NaN;

	private final List<VirtualElement> curveElements = new ArrayList<>();
	private int curveVersion = -1;
	private int curveHash;

	public TrackModel(BlockState state) {
		super(state);
		main = addPart(ItemStack.EMPTY);
		applyShape(state.getValue(TrackBlock.SHAPE), Double.NaN);
	}

	@Override
	protected void onStateChanged(BlockState state) {
		TrackShape newShape = state.getValue(TrackBlock.SHAPE);
		if (newShape != shape)
			applyShape(newShape, appliedTilt);
	}

	@Override
	protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
		if (!(be instanceof TrackBlockEntity track)) {
			if (curveVersion != -1) {
				clearCurves();
				curveVersion = -1;
				applyShape(shape, Double.NaN);
			}
			return;
		}

		double tilt = track.isTilted() ? track.tilt.smoothingAngle.get() : Double.NaN;
		if (Double.compare(tilt, appliedTilt) != 0)
			applyShape(shape, tilt);

		if (track.getDataVersion() == curveVersion)
			return;
		curveVersion = track.getDataVersion();
		int hash = 0;
		for (BezierConnection bc : track.getConnections()
			.values())
			hash += bc.isPrimary() ? bc.getKey()
				.hashCode() * 31 + bc.starts.hashCode() + bc.axes.hashCode() : 0;
		if (hash == curveHash && !curveElements.isEmpty())
			return;
		curveHash = hash;
		rebuildCurves(level, pos, track);
	}

	private void applyShape(TrackShape newShape, double tilt) {
		shape = newShape;
		appliedTilt = tilt;
		if (newShape == TrackShape.NONE) {
			main.setItem(ItemStack.EMPTY);
			return;
		}
		main.setItem(ModelItems.block("track/" + newShape.getModel()));
		Quaternionf rotation = new Quaternionf().rotationY(-(float) Math.toRadians(newShape.getModelRotation()));
		if (Double.isNaN(tilt)) {
			main.orientation(rotation)
				.apply(0);
			return;
		}
		main.setMatrix(tiltedMatrix(newShape, tilt), 0);
	}

	/** Create's TrackModel: a track between two curves going up or down is bent by the angle of the slope. */
	private static Matrix4f tiltedMatrix(TrackShape trackShape, double angleIn) {
		double angle = Math.abs(angleIn);
		boolean flip = angleIn < 0;

		double hAngle = switch (trackShape) {
		case XO -> 0;
		case PD -> 45;
		case ZO -> 90;
		case ND -> 135;
		default -> 0;
		};

		Vector3f verticalOffset = new Vector3f(0, -0.25f, 0);
		Vector3f diagonalRotationPoint = (trackShape == TrackShape.ND || trackShape == TrackShape.PD)
			? new Vector3f((Mth.SQRT_OF_TWO - 1) / 2, 0, 0)
			: new Vector3f();

		// In block space, applied right to left: + vo, rotate around the centre by h, + d, rotate around z, - d,
		// rotate around the centre by -h (+ 180 when flipped), - vo.
		Matrix4f m = new Matrix4f().translate(0, .25f, 0)
			.translate(0.5f, 0.5f, 0.5f)
			.rotateY((float) Math.toRadians(-hAngle + (flip ? 180 : 0)))
			.translate(-0.5f, -0.5f, -0.5f)
			.translate(new Vector3f(diagonalRotationPoint).negate())
			.rotateZ((float) Math.toRadians(angle))
			.translate(diagonalRotationPoint)
			.translate(0.5f, 0.5f, 0.5f)
			.rotateY((float) Math.toRadians(hAngle))
			.translate(-0.5f, -0.5f, -0.5f)
			.translate(verticalOffset);
		// the display is centred on the block
		return new Matrix4f().translate(-0.5f, -0.5f, -0.5f)
			.mul(m)
			.translate(0.5f, 0.5f, 0.5f);
	}

	private void clearCurves() {
		for (VirtualElement element : curveElements)
			removeElement(element);
		curveElements.clear();
	}

	private void rebuildCurves(ServerLevel level, BlockPos pos, TrackBlockEntity track) {
		clearCurves();
		for (BezierConnection bc : track.getConnections()
			.values()) {
			if (!bc.isPrimary())
				continue;
			addCurve(level, pos, track, bc);
		}
	}

	private void addCurve(ServerLevel level, BlockPos pos, TrackBlockEntity track, BezierConnection bc) {
		BezierConnection.SegmentAngles segments = bc.getBakedSegments();
		Vector3f half = new Vector3f(.5f, .5f, .5f);

		for (int i = 1; i < segments.length; i++) {
			addPiece(TIE, segments.tieTransform[i], half);
			Couple<Matrix4f> rails = segments.railTransforms[i];
			addPiece(SEGMENT_LEFT, rails.getFirst(), half);
			addPiece(SEGMENT_RIGHT, rails.getSecond(), half);
		}

		// Boxes to point at: one per block of curve
		BlockPos key = bc.getKey();
		int index = 0;
		for (BezierConnection.Segment segment : bc) {
			if (index++ % 2 != 0)
				continue;
			InteractionElement hitbox = new InteractionElement();
			hitbox.setSize(1.0f, 0.35f);
			hitbox.setResponse(true);
			Vec3 at = segment.position;
			hitbox.setOffset(new Vec3(at.x - .5, at.y - .28 - .5, at.z - .5));
			hitbox.setInteractionHandler(new VirtualElement.InteractionHandler() {
				@Override
				public void interact(ServerPlayer player, InteractionHand hand, Vec3 hit, boolean secondary) {
					Vec3 world = Vec3.atLowerCornerOf(pos)
						.add(at.x, at.y - .28, at.z)
						.add(hit);
					CurvedTrackInteraction.onInteract(player, hand, level, pos, key, world);
				}

				@Override
				public void attack(ServerPlayer player) {
					CurvedTrackInteraction.onAttack(player, level, pos, key);
				}
			});
			curveElements.add(addElement(hitbox));
		}
	}

	/** The element sits where the piece is (so it lights and culls right), its transform only turns and stretches it. */
	private void addPiece(ItemStack model, Matrix4f pose, Vector3f half) {
		ItemDisplayElement element = new ItemDisplayElement(model);
		element.setItemDisplayContext(ItemDisplayContext.NONE);
		element.setViewRange(1f);
		element.setTeleportDuration(0);
		element.setInterpolationDuration(0);

		Vector3f center = pose.transformPosition(new Vector3f(half));
		element.setOffset(new Vec3(center.x - .5, center.y - .5, center.z - .5));
		element.setTransformation(new Matrix4f().set3x3(pose));
		curveElements.add(addElement(element));
	}

}
