package dev.createpolymer.kinetics.transmission.sequencer;

import java.util.Vector;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.createpolymer.kinetics.transmission.SplitShaftBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Ported from Create's SequencedGearshiftBlockEntity (MIT, The Create Team).
 * The editor screen and its configuration packet are replaced by {@link SequencedGearshiftGui}.
 */
public class SequencedGearshiftBlockEntity extends SplitShaftBlockEntity {

	Vector<Instruction> instructions;
	int currentInstruction;
	int currentInstructionDuration;
	float currentInstructionProgress;
	int timer;
	boolean poweredPreviously;

	/** In Create this is a field of KineticBlockEntity, copied along the network by setSource. */
	public @Nullable SequenceContext sequenceContext;

	public record SequenceContext(SequencerInstructions instruction, double relativeValue) {
		public static final Codec<SequenceContext> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("Mode").forGetter(context -> context.instruction.name()),
			Codec.DOUBLE.fieldOf("Value").forGetter(context -> context.relativeValue)
		).apply(i, (mode, value) -> new SequenceContext(SequencerInstructions.valueOf(mode), value)));

		public static @Nullable SequenceContext fromGearshift(SequencerInstructions instruction, double kineticSpeed,
			int absoluteValue) {
			return instruction.needsPropagation()
				? new SequenceContext(instruction, kineticSpeed == 0 ? 0 : absoluteValue / kineticSpeed)
				: null;
		}

		public double getEffectiveValue(double speedAtTarget) {
			return Math.abs(relativeValue * speedAtTarget);
		}
	}

	public SequencedGearshiftBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state, false);
		instructions = Instruction.createDefault();
		currentInstruction = -1;
		currentInstructionDuration = -1;
		currentInstructionProgress = 0;
		timer = 0;
		poweredPreviously = false;
	}

	@Override
	public void tick() {
		super.tick();

		if (isIdle())
			return;
		if (level.isClientSide())
			return;
		if (currentInstructionDuration < 0)
			return;
		if (timer < currentInstructionDuration) {
			timer++;
			currentInstructionProgress += getInstruction(currentInstruction).getTickProgress(speed);
			return;
		}
		run(currentInstruction + 1);
	}

	@Override
	public void onSpeedChanged(float previousSpeed) {
		super.onSpeedChanged(previousSpeed);
		if (isIdle())
			return;
		float currentSpeed = Math.abs(speed);
		if (Math.abs(previousSpeed) == currentSpeed)
			return;
		Instruction instruction = getInstruction(currentInstruction);
		if (instruction == null)
			return;
		if (getSpeed() == 0)
			run(-1);

		// Update instruction time with regards to new speed
		currentInstructionDuration = instruction.getDuration(currentInstructionProgress, getTheoreticalSpeed());
		timer = 0;
	}

	public boolean isIdle() {
		return currentInstruction == -1;
	}

	public void onRedstoneUpdate(boolean isPowered, boolean isRunning) {
		if (!poweredPreviously && isPowered)
			risingFlank();
		poweredPreviously = isPowered;
		if (!isIdle())
			return;
		if (isPowered == isRunning)
			return;
		if (!level.hasNeighborSignal(worldPosition)) {
			level.setBlock(worldPosition, getBlockState().setValue(SequencedGearshiftBlock.STATE, 0), Block.UPDATE_ALL);
			return;
		}
		if (getSpeed() == 0)
			return;
		run(0);
	}

	public void risingFlank() {
		Instruction instruction = getInstruction(currentInstruction);
		if (instruction == null)
			return;
		if (poweredPreviously)
			return;
		poweredPreviously = true;

		switch (instruction.onRedstonePulse()) {
		case CONTINUE:
			run(currentInstruction + 1);
			break;
		default:
			break;
		}
	}

	public void run(int instructionIndex) {
		Instruction instruction = getInstruction(instructionIndex);
		if (instruction == null || instruction.instruction == SequencerInstructions.END) {
			if (getModifier() != 0)
				detachKinetics();
			currentInstruction = -1;
			currentInstructionDuration = -1;
			currentInstructionProgress = 0;
			sequenceContext = null;
			timer = 0;
			if (!level.hasNeighborSignal(worldPosition))
				level.setBlock(worldPosition, getBlockState().setValue(SequencedGearshiftBlock.STATE, 0), Block.UPDATE_ALL);
			else
				sendData();
			return;
		}

		detachKinetics();
		currentInstructionDuration = instruction.getDuration(0, getTheoreticalSpeed());
		currentInstruction = instructionIndex;
		currentInstructionProgress = 0;
		sequenceContext = SequenceContext.fromGearshift(instruction.instruction, getTheoreticalSpeed() * getModifier(),
			instruction.value);
		timer = 0;
		level.setBlock(worldPosition, getBlockState().setValue(SequencedGearshiftBlock.STATE, instructionIndex + 1), Block.UPDATE_ALL);
	}

	public Instruction getInstruction(int instructionIndex) {
		return instructionIndex >= 0 && instructionIndex < instructions.size() ? instructions.get(instructionIndex)
			: null;
	}

	/** Create's ConfigureSequencedGearshiftPacket.applySettings: stop what is running and take the new list. */
	public void applyInstructions(Vector<Instruction> instructions) {
		run(-1);
		this.instructions = instructions;
		sendData();
		setChanged();
	}

	@Override
	protected void write(ValueOutput compound) {
		compound.putInt("InstructionIndex", currentInstruction);
		compound.putInt("InstructionDuration", currentInstructionDuration);
		compound.putFloat("InstructionProgress", currentInstructionProgress);
		compound.putInt("Timer", timer);
		compound.putBoolean("PrevPowered", poweredPreviously);
		ValueOutput.TypedOutputList<Instruction> list = compound.list("Instructions", Instruction.CODEC);
		instructions.forEach(list::add);
		if (sequenceContext != null)
			compound.store("Sequence", SequenceContext.CODEC, sequenceContext);
		super.write(compound);
	}

	@Override
	protected void read(ValueInput compound) {
		currentInstruction = compound.getIntOr("InstructionIndex", -1);
		currentInstructionDuration = compound.getIntOr("InstructionDuration", -1);
		currentInstructionProgress = compound.getFloatOr("InstructionProgress", 0);
		poweredPreviously = compound.getBooleanOr("PrevPowered", false);
		timer = compound.getIntOr("Timer", 0);
		instructions = new Vector<>(5);
		compound.listOrEmpty("Instructions", Instruction.CODEC).forEach(instructions::add);
		if (instructions.isEmpty())
			instructions = Instruction.createDefault();
		sequenceContext = compound.read("Sequence", SequenceContext.CODEC).orElse(null);
		super.read(compound);
	}

	@Override
	public float getRotationSpeedModifier(Direction face) {
		if (isVirtual())
			return 1;
		return (!hasSource() || face == getSourceFacing()) ? 1 : getModifier();
	}

	public int getModifier() {
		if (currentInstruction >= instructions.size())
			return 0;
		return isIdle() ? 0
			: instructions.get(currentInstruction)
				.getSpeedModifier();
	}

	public Vector<Instruction> getInstructions() {
		return this.instructions;
	}

}
