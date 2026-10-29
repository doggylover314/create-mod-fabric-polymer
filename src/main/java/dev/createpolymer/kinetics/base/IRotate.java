package dev.createpolymer.kinetics.base;

import dev.createpolymer.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/** Ported from Create's IRotate (MIT, The Create Team). */
public interface IRotate {

	enum SpeedLevel {
		NONE(ChatFormatting.DARK_GRAY),
		SLOW(ChatFormatting.GREEN),
		MEDIUM(ChatFormatting.AQUA),
		FAST(ChatFormatting.LIGHT_PURPLE);

		private final ChatFormatting textColor;

		SpeedLevel(ChatFormatting textColor) {
			this.textColor = textColor;
		}

		public ChatFormatting getTextColor() {
			return textColor;
		}

		public float getSpeedValue() {
			return switch (this) {
				case FAST -> Config.fastSpeed;
				case MEDIUM -> Config.mediumSpeed;
				case SLOW -> 1;
				case NONE -> 0;
			};
		}

		public static SpeedLevel of(float speed) {
			speed = Math.abs(speed);
			if (speed >= Config.fastSpeed)
				return FAST;
			if (speed >= Config.mediumSpeed)
				return MEDIUM;
			if (speed >= 1)
				return SLOW;
			return NONE;
		}
	}

	enum StressImpact {
		LOW(ChatFormatting.GREEN),
		MEDIUM(ChatFormatting.YELLOW),
		HIGH(ChatFormatting.GOLD),
		OVERSTRESSED(ChatFormatting.RED);

		private final ChatFormatting color;

		StressImpact(ChatFormatting color) {
			this.color = color;
		}

		public ChatFormatting getColor() {
			return color;
		}

		public static StressImpact of(double stressPercent) {
			if (stressPercent > 1)
				return OVERSTRESSED;
			if (stressPercent > .75d)
				return HIGH;
			if (stressPercent > .5d)
				return MEDIUM;
			return LOW;
		}

		public static boolean isEnabled() {
			return !Config.disableStress;
		}
	}

	boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face);

	Axis getRotationAxis(BlockState state);

	default SpeedLevel getMinimumRequiredSpeedLevel() {
		return SpeedLevel.NONE;
	}

	default boolean hideStressImpact() {
		return false;
	}
}
