package dev.createpolymer.kinetics.transmission.sequencer;

import java.util.List;
import java.util.Vector;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.createpolymer.kinetics.base.KineticBlockEntity;

/** Ported from Create's Instruction (MIT, The Create Team). */
public class Instruction {
	private static <T extends Enum<T>> Codec<T> enumCodec(Class<T> enumClass) {
		T[] constants = enumClass.getEnumConstants();
		return Codec.STRING.xmap(name -> {
			for (T constant : constants)
				if (constant.name().equals(name))
					return constant;
			return constants[0];
		}, Enum::name);
	}

	public static final Codec<Instruction> CODEC = RecordCodecBuilder.create(i -> i.group(
		enumCodec(SequencerInstructions.class).fieldOf("Type").forGetter(instruction -> instruction.instruction),
		enumCodec(InstructionSpeedModifiers.class).fieldOf("Modifier").forGetter(instruction -> instruction.speedModifier),
		Codec.INT.fieldOf("Value").forGetter(instruction -> instruction.value)
	).apply(i, Instruction::new));

	SequencerInstructions instruction;
	InstructionSpeedModifiers speedModifier;
	int value;

	public Instruction(SequencerInstructions instruction) {
		this(instruction, 1);
	}

	public Instruction(SequencerInstructions instruction, int value) {
		this(instruction, InstructionSpeedModifiers.FORWARD, value);
	}

	public Instruction(SequencerInstructions instruction, InstructionSpeedModifiers speedModifier, int value) {
		this.instruction = instruction;
		this.speedModifier = speedModifier;
		this.value = value;
	}

	public Instruction copy() {
		return new Instruction(instruction, speedModifier, value);
	}

	public SequencerInstructions getInstruction() {
		return instruction;
	}

	public InstructionSpeedModifiers getSpeedModifierType() {
		return speedModifier;
	}

	public int getValue() {
		return value;
	}

	int getDuration(float currentProgress, float speed) {
		speed *= speedModifier.value;
		speed = Math.abs(speed);
		double target = value - currentProgress;

		switch (instruction) {

		// Always overshoot, target will stop early
		case TURN_ANGLE:
			double degreesPerTick = KineticBlockEntity.convertToAngular(speed);
			return (int) Math.ceil(target / degreesPerTick) + 2;
		case TURN_DISTANCE:
			double metersPerTick = KineticBlockEntity.convertToLinear(speed);
			return (int) Math.ceil(target / metersPerTick) + 2;

		// Timing instructions
		case DELAY:
			return (int) target;
		case AWAIT:
			return -1;
		case END:
		default:
			break;

		}
		return 0;
	}

	float getTickProgress(float speed) {
		switch (instruction) {

		case TURN_ANGLE:
			return KineticBlockEntity.convertToAngular(speed);

		case TURN_DISTANCE:
			return KineticBlockEntity.convertToLinear(speed);

		case DELAY:
			return 1;

		case AWAIT:
		case END:
		default:
			break;

		}
		return 0;
	}

	int getSpeedModifier() {
		switch (instruction) {

		case TURN_ANGLE:
		case TURN_DISTANCE:
			return speedModifier.value;

		case END:
		case DELAY:
		case AWAIT:
		default:
			break;

		}
		return 0;
	}

	OnIsPoweredResult onRedstonePulse() {
		return instruction == SequencerInstructions.AWAIT ? OnIsPoweredResult.CONTINUE : OnIsPoweredResult.NOTHING;
	}

	public static Vector<Instruction> copyAll(List<Instruction> instructions) {
		Vector<Instruction> copy = new Vector<>(5);
		instructions.forEach(i -> copy.add(i.copy()));
		return copy;
	}

	public static Vector<Instruction> createDefault() {
		Vector<Instruction> instructions = new Vector<>(5);
		instructions.add(new Instruction(SequencerInstructions.TURN_ANGLE, 90));
		instructions.add(new Instruction(SequencerInstructions.END));
		return instructions;
	}

}
