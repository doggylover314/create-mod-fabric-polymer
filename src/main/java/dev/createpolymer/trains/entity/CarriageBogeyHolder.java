package dev.createpolymer.trains.entity;

import org.joml.Matrix4f;

import dev.createpolymer.trains.bogey.BogeySizes.BogeySize;
import dev.createpolymer.trains.bogey.BogeyStyle;
import dev.createpolymer.trains.bogey.BogeyVisual;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the bogeys of a carriage (what Create's CarriageContraptionVisual / renderer do): the bogey parts follow
 * the wheels' travelling points, each with its own yaw and pitch, so they cannot ride the contraption's blocks.
 * <p>
 * The display parts glide to each new position like the carrier of the contraption does (teleport and transformation
 * interpolation of three ticks), so bogeys and blocks stay together. The wheels turn by the distance the carriage
 * travelled; at high speed they turn slower than they should, as a display cannot show more than half a turn per
 * interpolation step without appearing to spin backwards.
 */
public class CarriageBogeyHolder extends ElementHolder {
	/** Must match the teleport duration of the contraption entity (see AbstractContraptionEntity). */
	static final int INTERPOLATION = 3;
	/** Degrees per tick the wheels may turn on the display. */
	static final float MAX_WHEEL_STEP = 50;

	private static final int MAX_BOGEYS = 2;

	private final CarriageContraptionEntity entity;
	private final BogeyVisual[] visuals = new BogeyVisual[MAX_BOGEYS];
	private final BogeyStyle[] shownStyles = new BogeyStyle[MAX_BOGEYS];
	private final BogeySize[] shownSizes = new BogeySize[MAX_BOGEYS];
	private final float[] shownWheelAngle = new float[MAX_BOGEYS];
	private final boolean[] angleKnown = new boolean[MAX_BOGEYS];

	public CarriageBogeyHolder(CarriageContraptionEntity entity) {
		this.entity = entity;
	}

	/** The display parts of the bogey at the given index (0 = leading), null if it has none yet. */
	public BogeyVisual getVisual(int index) {
		return visuals[index];
	}

	@Override
	protected void onTick() {
		Carriage carriage = entity.getCarriage();
		if (entity.isRemoved() || carriage == null)
			return;

		int index = 0;
		for (CarriageBogey bogey : carriage.bogeys) {
			if (bogey == null)
				continue;
			updateBogey(carriage, bogey, index);
			index++;
		}
	}

	private void updateBogey(Carriage carriage, CarriageBogey bogey, int index) {
		BogeyStyle style = bogey.getStyle();
		BogeySize size = bogey.getSize();
		if (visuals[index] == null || shownStyles[index] != style || shownSizes[index] != size) {
			if (visuals[index] != null)
				for (ItemDisplayElement element : visuals[index].elements())
					removeElement(element);
			shownStyles[index] = style;
			shownSizes[index] = size;
			visuals[index] = style.createVisual(size);
			angleKnown[index] = false;
			if (visuals[index] == null)
				return;
			for (ItemDisplayElement element : visuals[index].elements()) {
				element.setTeleportDuration(INTERPOLATION);
				element.setViewRange(1.5f);
				addElement(element);
			}
		}
		BogeyVisual visual = visuals[index];
		if (visual == null)
			return;

		boolean leadingUpsideDown = carriage.leadingBogey()
			.isUpsideDown();
		boolean selfUpsideDown = bogey.isUpsideDown();
		boolean sameDimension = bogey.getDimension() != null && bogey.getDimension()
			.equals(entity.level()
				.dimension());
		// The trailing bogey is placed in the frame of the leading one, like Create's rotation anchors do
		Vec3 anchor = !sameDimension || carriage.train.graph == null ? null
			: bogey.getAnchorPosition(!bogey.isLeading() && (selfUpsideDown ^ leadingUpsideDown));
		if (anchor == null) {
			visual.hide(INTERPOLATION);
			return;
		}

		float target = bogey.wheelAngle.getValue();
		if (!angleKnown[index]) {
			angleKnown[index] = true;
			shownWheelAngle[index] = target;
		} else {
			shownWheelAngle[index] =
				(shownWheelAngle[index] + Mth.clamp((float) bogey.wheelAngleStep, -MAX_WHEEL_STEP, MAX_WHEEL_STEP)) % 360;
		}

		// Create's translateBogey, from the bogey's anchor on: its own yaw and pitch, half a block up, flipped
		// when it hangs under the track, and the visual's -1.5 - 1/128 down to the rail.
		Matrix4f base = new Matrix4f().rotateY((float) Math.toRadians(bogey.yaw.getValue()))
			.rotateX((float) Math.toRadians(bogey.pitch.getValue()))
			.translate(0, .5f, 0)
			.rotateZ(selfUpsideDown ? (float) Math.PI : 0)
			.translate(0, selfUpsideDown != leadingUpsideDown ? 2 : 0, 0)
			.translate(0, -1.5f - 1 / 128f, 0);

		Vec3 offset = anchor.subtract(entity.position());
		for (ItemDisplayElement element : visual.elements())
			element.setOffset(offset);
		CompoundTag bogeyData = bogey.bogeyData == null ? new CompoundTag() : bogey.bogeyData;
		visual.update(bogeyData, shownWheelAngle[index], base, INTERPOLATION);
	}

}
