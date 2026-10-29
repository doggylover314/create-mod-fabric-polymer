package dev.createpolymer.kinetics.mechanicalArm;

import org.joml.Matrix4f;

import dev.createpolymer.util.AngleHelper;

/**
 * The transform chain of Create's ArmRenderer, applied to a {@link Matrix4f} instead of a PoseStack. The model in
 * {@link ArmBlock.Model} walks it from the base to the claw.
 */
public class ArmRenderer {

	public static void transformClawHalf(Matrix4f m, boolean hasItem, boolean isBlockItem, int flip) {
		m.translate(0, -flip * (hasItem ? isBlockItem ? 3 / 16f : 5 / 64f : 1 / 16f), -6 / 16f);
	}

	public static void transformHead(Matrix4f m, float headAngle) {
		m.translate(0, 0, -15 / 16f);
		m.rotateX(AngleHelper.rad(headAngle - 45f));
	}

	public static void transformUpperArm(Matrix4f m, float upperArmAngle) {
		m.translate(0, 0, -14 / 16f);
		m.rotateX(AngleHelper.rad(upperArmAngle - 90));
	}

	public static void transformLowerArm(Matrix4f m, float lowerArmAngle) {
		m.translate(0, 2 / 16f, 0);
		m.rotateX(AngleHelper.rad(lowerArmAngle + 135));
	}

	public static void transformBase(Matrix4f m, float baseAngle) {
		m.translate(0, 4 / 16f, 0);
		m.rotateY(AngleHelper.rad(baseAngle));
	}

}
