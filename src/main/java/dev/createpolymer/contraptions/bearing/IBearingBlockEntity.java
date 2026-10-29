package dev.createpolymer.contraptions.bearing;

import dev.createpolymer.contraptions.DirectionalExtenderScrollOptionSlot;
import dev.createpolymer.contraptions.IControlContraption;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.minecraft.core.Direction.Axis;

public interface IBearingBlockEntity extends IControlContraption {

	float getInterpolatedAngle(float partialTicks);

	boolean isWoodenTop();

	default ValueBoxTransform getMovementModeSlot() {
		return new DirectionalExtenderScrollOptionSlot((state, d) -> {
			Axis axis = d.getAxis();
			Axis bearingAxis = state.getValue(BearingBlock.FACING)
				.getAxis();
			return bearingAxis != axis;
		});
	}

	void setAngle(float forcedAngle);

}
