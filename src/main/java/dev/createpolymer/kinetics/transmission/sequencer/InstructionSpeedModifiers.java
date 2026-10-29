package dev.createpolymer.kinetics.transmission.sequencer;

import java.util.Arrays;

import dev.createpolymer.util.Lang;
import net.minecraft.network.chat.Component;

/** Ported from Create's InstructionSpeedModifiers (MIT, The Create Team). */
public enum InstructionSpeedModifiers {

	FORWARD_FAST(2, ">>"), FORWARD(1, "->"), BACK(-1, "<-"), BACK_FAST(-2, "<<"),

	;

	String translationKey;
	int value;
	Component label;

	private InstructionSpeedModifiers(int modifier, String label) {
		this.label = Component.literal(label);
		translationKey = "create.gui.sequenced_gearshift.speed." + Lang.asId(name());
		value = modifier;
	}

	public Component getDescription() {
		return Component.translatable(translationKey);
	}

	public Component getLabel() {
		return label;
	}

	public int getValue() {
		return value;
	}

	public static InstructionSpeedModifiers getByModifier(int modifier) {
		return Arrays.stream(InstructionSpeedModifiers.values())
				.filter(speedModifier -> speedModifier.value == modifier)
				.findAny()
				.orElse(InstructionSpeedModifiers.FORWARD);
	}

}
