package dev.createpolymer.visual;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;

/** Quaternion helpers that match how vanilla applies blockstate "x"/"y" rotations. */
public final class Orientation {
	private Orientation() {}

	/** Same rotation a blockstate variant with the given x/y (degrees, multiples of 90) applies. */
	public static Quaternionf variant(int xDeg, int yDeg) {
		return new Quaternionf()
			.rotationY((float) Math.toRadians(-yDeg))
			.rotateX((float) Math.toRadians(-xDeg));
	}

	/** Rotation that turns a model's +Y onto the given axis, matching Create's axis blockstates. */
	public static Quaternionf forAxis(Axis axis) {
		return switch (axis) {
			case X -> variant(90, 90);
			case Y -> new Quaternionf();
			case Z -> variant(90, 180);
		};
	}

	/** Create's directionalBlockProvider rotations: model faces up, rotated to {@code facing}. */
	public static Quaternionf forFacing(Direction facing) {
		return switch (facing) {
			case UP -> new Quaternionf();
			case DOWN -> variant(180, 0);
			case NORTH -> variant(90, 0);
			case SOUTH -> variant(90, 180);
			case WEST -> variant(90, 270);
			case EAST -> variant(90, 90);
		};
	}

	/** Create's horizontalBlockProvider rotations: model faces north at y=0. */
	public static Quaternionf forHorizontal(Direction facing) {
		return switch (facing) {
			case EAST -> variant(0, 90);
			case SOUTH -> variant(0, 180);
			case WEST -> variant(0, 270);
			default -> new Quaternionf();
		};
	}

	/** Rotation that turns a model pointing north (-Z) to point at {@code facing} (Create's crank handle). */
	public static Quaternionf fromNorth(Direction facing) {
		return new Quaternionf().rotateTo(0, 0, -1, facing.getStepX(), facing.getStepY(), facing.getStepZ());
	}

	/** Create's AngleHelper.horizontalAngle based rotation about +Y (used for the press head). */
	public static Quaternionf horizontalAngle(Direction facing) {
		float angle = facing.toYRot();
		if (facing.getAxis() == Direction.Axis.X)
			angle = -angle;
		return new Quaternionf().rotationY((float) Math.toRadians(angle));
	}

	/** Rotation that turns a model pointing south (+Z) to point at {@code facing}. */
	public static Quaternionf fromSouth(Direction facing) {
		return new Quaternionf(facing.getRotation()).mul(new Quaternionf().rotationX((float) (-Math.PI / 2)));
	}

	public static Vector3f axisVector(Axis axis) {
		return switch (axis) {
			case X -> new Vector3f(1, 0, 0);
			case Y -> new Vector3f(0, 1, 0);
			case Z -> new Vector3f(0, 0, 1);
		};
	}

	/** Create's offset so neighbouring cogs mesh (KineticBlockEntityVisual.rotationOffset). */
	public static float cogOffset(boolean large, Axis axis, int x, int y, int z) {
		int sx = axis == Axis.X ? 0 : x;
		int sy = axis == Axis.Y ? 0 : y;
		int sz = axis == Axis.Z ? 0 : z;
		if (((sx + sy + sz) % 2) == 0)
			return 22.5f;
		return large ? 11.25f : 0;
	}

	public static Direction positive(Axis axis) {
		return Direction.get(Direction.AxisDirection.POSITIVE, axis);
	}
}
