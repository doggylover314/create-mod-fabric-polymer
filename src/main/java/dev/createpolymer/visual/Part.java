package dev.createpolymer.visual;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * One displayed model piece. Its final transform is
 * translate(offset) * spin(axis, angle) * orientation * scale.
 * Spinning parts are re-sent every few ticks and the client interpolates in between.
 */
public class Part {
	public final ItemDisplayElement element;
	private Quaternionf orientation = new Quaternionf();
	private Vector3f translation = new Vector3f();
	private Vector3f scale = new Vector3f(1, 1, 1);
	private Axis spinAxis = Axis.Y;
	private float angle;

	private float lastSpeed = 0;
	private long nextUpdate = Long.MIN_VALUE;
	private boolean dirty = true;

	public Part(ItemStack stack) {
		this.element = new ItemDisplayElement(stack);
		this.element.setItemDisplayContext(ItemDisplayContext.NONE);
		this.element.setViewRange(1f);
		this.element.setTeleportDuration(0);
		this.element.setInterpolationDuration(0);
	}

	public Part orientation(Quaternionf q) {
		this.orientation = new Quaternionf(q);
		dirty = true;
		return this;
	}

	public Part spinAxis(Axis axis) {
		this.spinAxis = axis;
		dirty = true;
		return this;
	}

	public Part translation(float x, float y, float z) {
		if (translation.x != x || translation.y != y || translation.z != z) {
			translation = new Vector3f(x, y, z);
			dirty = true;
		}
		return this;
	}

	public Part scale(float s) {
		this.scale = new Vector3f(s, s, s);
		dirty = true;
		return this;
	}

	public Part angle(float degrees) {
		if (this.angle != degrees) {
			this.angle = degrees;
			dirty = true;
		}
		return this;
	}

	public void setItem(ItemStack stack) {
		element.setItem(stack);
	}

	/** Pushes the current transform to the element if anything changed. */
	public void apply(int interpolation) {
		if (!dirty)
			return;
		dirty = false;
		Quaternionf spin = new Quaternionf().rotationAxis((float) Math.toRadians(angle), Orientation.axisVector(spinAxis));
		Matrix4f m = new Matrix4f()
			.translate(translation)
			.rotate(spin)
			.rotate(orientation)
			.scale(scale);
		element.setTransformation(m);
		element.setInterpolationDuration(interpolation);
		element.startInterpolation();
	}

	/**
	 * Spins the part like Create does: angle = time * rpm * 0.3 + offset (degrees).
	 * Updates are spaced so that each step is well under 180 degrees, otherwise the
	 * client's shortest-path interpolation would spin the wrong way.
	 */
	public void spin(long time, float speed, float offsetDeg) {
		if (speed == 0) {
			if (lastSpeed != 0) {
				lastSpeed = 0;
				apply(0);
			}
			return;
		}
		float degPerTick = Math.abs(speed) * 0.3f;
		int interval = Mth.clamp((int) (90f / degPerTick), 1, 10);
		if (speed != lastSpeed || time >= nextUpdate) {
			lastSpeed = speed;
			nextUpdate = time + interval;
			angle(((time + interval) * speed * 0.3f + offsetDeg) % 360f);
			dirty = true;
			apply(interval);
		}
	}

	/** Sets the angle for the next tick; the caller sends it with {@link #apply(int)} using 1 tick of interpolation. */
	public void spinStep(long time, float speed, float offsetDeg) {
		// More than ~120 degrees per tick would look like spinning backwards.
		speed = Mth.clamp(speed, -400, 400);
		lastSpeed = speed;
		if (speed != 0)
			angle(((time + 1) * speed * 0.3f + offsetDeg) % 360f);
	}

	public float getAngle() {
		return angle;
	}
}
