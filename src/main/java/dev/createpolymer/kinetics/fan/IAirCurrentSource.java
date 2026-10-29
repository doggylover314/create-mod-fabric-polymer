package dev.createpolymer.kinetics.fan;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

public interface IAirCurrentSource {
	@Nullable
	AirCurrent getAirCurrent();

	@Nullable
	Level getAirCurrentWorld();

	BlockPos getAirCurrentPos();

	float getSpeed();

	Direction getAirflowOriginSide();

	@Nullable
	Direction getAirFlowDirection();

	default float getMaxDistance() {
		float speed = Math.abs(this.getSpeed());
		float distanceFactor = Math.min(speed / FanConfig.fanRotationArgmax, 1);
		float pushDistance = Mth.lerp(distanceFactor, 3, FanConfig.fanPushDistance);
		float pullDistance = Mth.lerp(distanceFactor, 3f, FanConfig.fanPullDistance);
		return this.getSpeed() > 0 ? pushDistance : pullDistance;
	}

	boolean isSourceRemoved();
}
