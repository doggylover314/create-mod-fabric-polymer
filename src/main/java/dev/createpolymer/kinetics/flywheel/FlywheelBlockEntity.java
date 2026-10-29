package dev.createpolymer.kinetics.flywheel;

import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.util.LerpedFloat;
import dev.createpolymer.util.LerpedFloat.Chaser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ported from Create's FlywheelBlockEntity (MIT, The Create Team).
 * The wheel lags behind the shaft: its visual speed chases the real speed.
 */
public class FlywheelBlockEntity extends KineticBlockEntity {

	LerpedFloat visualSpeed = LerpedFloat.linear();
	float angle;

	public FlywheelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		visualSpeed.chase(0, 1 / 64f, Chaser.EXP);
	}

	public float getAngle() {
		return angle;
	}

	public float getVisualSpeed() {
		return visualSpeed.getValue();
	}

	@Override
	public void tick() {
		super.tick();

		if (level.isClientSide())
			return;

		float targetSpeed = getSpeed();
		visualSpeed.updateChaseTarget(targetSpeed);
		visualSpeed.tickChaser();
		angle += visualSpeed.getValue() * 3 / 10f;
		angle %= 360;
	}
}
