package dev.createpolymer.kinetics.transmission.sequencer;

import dev.createpolymer.util.Lang;
import net.minecraft.network.chat.Component;

/** Ported from Create's SequencerInstructions (MIT, The Create Team). */
public enum SequencerInstructions {
	TURN_ANGLE("angle", true, true, 360, 45, 90),
	TURN_DISTANCE("distance", true, true, 128, 5, 5),
	DELAY("duration", true, false, 600, 20, 10),
	AWAIT("", false, false, -1, -1, -1),
	END("", false, false, -1, -1, -1),

	;

	public final String translationKey;
	public final String descriptiveTranslationKey;
	public final String parameterKey;
	public final boolean hasValueParameter;
	public final boolean hasSpeedParameter;
	public final int maxValue;
	public final int shiftStep;
	public final int defaultValue;

	SequencerInstructions(String parameterName, boolean hasValueParameter, boolean hasSpeedParameter, int maxValue,
		int shiftStep, int defaultValue) {
		this.hasValueParameter = hasValueParameter;
		this.hasSpeedParameter = hasSpeedParameter;
		this.maxValue = maxValue;
		this.shiftStep = shiftStep;
		this.defaultValue = defaultValue;
		translationKey = "create.gui.sequenced_gearshift.instruction." + Lang.asId(name());
		descriptiveTranslationKey = translationKey + ".descriptive";
		parameterKey = translationKey + "." + parameterName;
	}

	public boolean needsPropagation() {
		return this == TURN_ANGLE || this == TURN_DISTANCE;
	}

	public Component getLabel() {
		return Component.translatable(translationKey);
	}

	public Component getDescription() {
		return Component.translatable(descriptiveTranslationKey);
	}

	public Component getParameterLabel() {
		return Component.translatable(parameterKey);
	}

	Component formatValue(int value) {
		if (this == TURN_ANGLE)
			return Component.literal(value + "").append(Component.translatable("create.generic.unit.degrees"));
		if (this == TURN_DISTANCE)
			return Component.literal(value + "m");
		if (this == DELAY) {
			if (value >= 20)
				return Component.literal((value / 20) + "s");
			return Component.literal(value + "t");
		}
		return Component.literal("" + value);
	}

}
