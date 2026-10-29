package dev.createpolymer.util;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Builds rotated variants of a shape authored "facing up" (Create's convention),
 * using plain coordinate swaps so the result is easy to check by hand.
 */
public final class ShapeUtil {
	private ShapeUtil() {}

	public static VoxelShape box(double x1, double y1, double z1, double x2, double y2, double z2) {
		return Shapes.box(x1 / 16, y1 / 16, z1 / 16, x2 / 16, y2 / 16, z2 / 16);
	}

	public static Map<Direction, VoxelShape> forDirectional(VoxelShape up) {
		Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
		for (Direction d : Direction.values())
			map.put(d, transform(up, d));
		return map;
	}

	public static Map<Axis, VoxelShape> forAxis(VoxelShape up) {
		Map<Axis, VoxelShape> map = new EnumMap<>(Axis.class);
		map.put(Axis.Y, up);
		map.put(Axis.X, transform(up, Direction.EAST));
		map.put(Axis.Z, transform(up, Direction.SOUTH));
		return map;
	}

	/** Re-maps a shape whose "top" is +Y so that its top points to {@code facing}. */
	public static VoxelShape transform(VoxelShape up, Direction facing) {
		if (facing == Direction.UP)
			return up;
		VoxelShape result = Shapes.empty();
		for (AABB b : up.toAabbs()) {
			double[] p1 = map(b.minX, b.minY, b.minZ, facing);
			double[] p2 = map(b.maxX, b.maxY, b.maxZ, facing);
			result = Shapes.or(result, Shapes.box(
				Math.min(p1[0], p2[0]), Math.min(p1[1], p2[1]), Math.min(p1[2], p2[2]),
				Math.max(p1[0], p2[0]), Math.max(p1[1], p2[1]), Math.max(p1[2], p2[2])));
		}
		return result.optimize();
	}

	private static double[] map(double x, double y, double z, Direction facing) {
		return switch (facing) {
			case UP -> new double[] { x, y, z };
			case DOWN -> new double[] { x, 1 - y, 1 - z };
			case NORTH -> new double[] { x, z, 1 - y };
			case SOUTH -> new double[] { x, 1 - z, y };
			case WEST -> new double[] { 1 - y, x, z };
			case EAST -> new double[] { y, 1 - x, z };
		};
	}
}
