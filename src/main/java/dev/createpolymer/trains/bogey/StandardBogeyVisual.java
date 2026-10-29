package dev.createpolymer.trains.bogey;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import dev.createpolymer.visual.ModelItems;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.nbt.CompoundTag;

/**
 * Create's StandardBogeyVisual as display parts: the shafts, and for the small bogey the frame and two wheels, for
 * the large bogey the drive with its belt, piston, wheels and pin. The wheels turn with the wheel angle.
 */
public class StandardBogeyVisual extends BogeyVisual {
	/** Create's AllPartialModels.SHAFT and the bogey partials. */
	protected static final String SHAFT = "shaft";
	protected static final String BOGEY_FRAME = "track/bogey/bogey_frame";
	protected static final String SMALL_BOGEY_WHEELS = "track/bogey/bogey_wheel";
	protected static final String BOGEY_PIN = "track/bogey/bogey_drive_wheel_pin";
	protected static final String BOGEY_PISTON = "track/bogey/bogey_drive_piston";
	protected static final String BOGEY_DRIVE = "track/bogey/bogey_drive";
	protected static final String LARGE_BOGEY_WHEELS = "track/bogey/bogey_drive_wheel";
	protected static final String BOGEY_DRIVE_BELT = "track/bogey/bogey_drive_belt";

	private static final Quaternionf UP_TO_SOUTH = new Quaternionf().rotateTo(0, 1, 0, 0, 0, 1);
	private static final Quaternionf UP_TO_EAST = new Quaternionf().rotateTo(0, 1, 0, 1, 0, 0);

	private final ItemDisplayElement shaft1;
	private final ItemDisplayElement shaft2;

	public StandardBogeyVisual() {
		shaft1 = add(ModelItems.block(SHAFT));
		shaft2 = add(ModelItems.block(SHAFT));
	}

	protected static Matrix4f shaftPose(float x, float y, float z, Quaternionf rotateTo, float wheelAngle) {
		return new Matrix4f().translate(x, y, z)
			.translate(.5f, .5f, .5f)
			.rotate(rotateTo)
			.rotateY((float) Math.toRadians(wheelAngle))
			.translate(-.5f, -.5f, -.5f);
	}

	@Override
	public void update(CompoundTag bogeyData, float wheelAngle, Matrix4f base, int interpolation) {
		place(shaft1, base, shaftPose(-.5f, .25f, 0, UP_TO_SOUTH, wheelAngle), interpolation);
		place(shaft2, base, shaftPose(-.5f, .25f, -1, UP_TO_SOUTH, wheelAngle), interpolation);
	}

	public static class Small extends StandardBogeyVisual {
		private final ItemDisplayElement frame;
		private final ItemDisplayElement wheel1;
		private final ItemDisplayElement wheel2;

		public Small() {
			frame = add(ModelItems.block(BOGEY_FRAME));
			wheel1 = add(ModelItems.block(SMALL_BOGEY_WHEELS));
			wheel2 = add(ModelItems.block(SMALL_BOGEY_WHEELS));
		}

		@Override
		public void update(CompoundTag bogeyData, float wheelAngle, Matrix4f base, int interpolation) {
			super.update(bogeyData, wheelAngle, base, interpolation);
			float angle = (float) Math.toRadians(wheelAngle);
			place(wheel1, base, new Matrix4f().translate(0, 12 / 16f, -1)
				.rotateX(angle), interpolation);
			place(wheel2, base, new Matrix4f().translate(0, 12 / 16f, 1)
				.rotateX(angle), interpolation);
			place(frame, base, new Matrix4f().scale(SLIGHTLY_SMALLER), interpolation);
		}
	}

	public static class Large extends StandardBogeyVisual {
		private final ItemDisplayElement secondaryShaft1;
		private final ItemDisplayElement secondaryShaft2;
		private final ItemDisplayElement drive;
		private final ItemDisplayElement belt;
		private final ItemDisplayElement piston;
		private final ItemDisplayElement wheels;
		private final ItemDisplayElement pin;

		public Large() {
			secondaryShaft1 = add(ModelItems.block(SHAFT));
			secondaryShaft2 = add(ModelItems.block(SHAFT));
			drive = add(ModelItems.block(BOGEY_DRIVE));
			belt = add(ModelItems.block(BOGEY_DRIVE_BELT));
			piston = add(ModelItems.block(BOGEY_PISTON));
			wheels = add(ModelItems.block(LARGE_BOGEY_WHEELS));
			pin = add(ModelItems.block(BOGEY_PIN));
		}

		@Override
		public void update(CompoundTag bogeyData, float wheelAngle, Matrix4f base, int interpolation) {
			super.update(bogeyData, wheelAngle, base, interpolation);
			float angle = (float) Math.toRadians(wheelAngle);
			place(secondaryShaft1, base, shaftPose(-.5f, .25f, .5f, UP_TO_EAST, wheelAngle), interpolation);
			place(secondaryShaft2, base, shaftPose(-.5f, .25f, -1.5f, UP_TO_EAST, wheelAngle), interpolation);
			place(drive, base, new Matrix4f().scale(SLIGHTLY_SMALLER), interpolation);
			place(belt, base, new Matrix4f().scale(SLIGHTLY_SMALLER), interpolation);
			place(piston, base, new Matrix4f().translate(0, 0, (float) (1 / 4f * Math.sin(angle))), interpolation);
			place(wheels, base, new Matrix4f().translate(0, 1, 0)
				.rotateX(angle), interpolation);
			place(pin, base, new Matrix4f().translate(0, 1, 0)
				.rotateX(angle)
				.translate(0, 1 / 4f, 0)
				.rotateX(-angle), interpolation);
		}
	}
}
