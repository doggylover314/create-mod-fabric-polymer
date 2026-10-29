package dev.createpolymer.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Small vector helpers matching the ones Create uses (right-hand rotation about an axis). */
public final class VecHelper {
	private VecHelper() {}

	public static Vec3 rotate(Vec3 vec, double deg, Axis axis) {
		if (deg == 0)
			return vec;
		float angle = (float) (deg / 180f * Math.PI);
		double sin = Mth.sin(angle);
		double cos = Mth.cos(angle);
		double x = vec.x, y = vec.y, z = vec.z;
		return switch (axis) {
			case X -> new Vec3(x, y * cos - z * sin, z * cos + y * sin);
			case Y -> new Vec3(x * cos + z * sin, y, z * cos - x * sin);
			case Z -> new Vec3(x * cos - y * sin, y * cos + x * sin, z);
		};
	}

	public static Vec3 getCenterOf(BlockPos pos) {
		return Vec3.atCenterOf(pos);
	}

	public static Vec3 offsetRandomly(Vec3 vec, RandomSource r, float radius) {
		return new Vec3(vec.x + (r.nextFloat() - .5f) * 2 * radius, vec.y + (r.nextFloat() - .5f) * 2 * radius,
			vec.z + (r.nextFloat() - .5f) * 2 * radius);
	}
}
