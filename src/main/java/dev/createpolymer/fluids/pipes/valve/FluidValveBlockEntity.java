package dev.createpolymer.fluids.pipes.valve;

import java.util.List;

import dev.createpolymer.fluids.FluidPropagator;
import dev.createpolymer.fluids.pipes.StraightPipeBlockEntity.StraightPipeFluidTransportBehaviour;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.util.LerpedFloat;
import dev.createpolymer.util.LerpedFloat.Chaser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class FluidValveBlockEntity extends KineticBlockEntity {

	LerpedFloat pointer;

	public FluidValveBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
		super(typeIn, pos, state);
		pointer = LerpedFloat.linear()
			.startWithValue(0)
			.chase(0, 0, Chaser.LINEAR);
	}

	@Override
	public void onSpeedChanged(float previousSpeed) {
		super.onSpeedChanged(previousSpeed);
		float speed = getSpeed();
		pointer.chase(speed > 0 ? 1 : 0, getChaseSpeed(), Chaser.LINEAR);
		sendData();
	}

	@Override
	public void tick() {
		super.tick();
		pointer.tickChaser();

		if (level.isClientSide())
			return;

		BlockState blockState = getBlockState();
		if (!(blockState.getBlock() instanceof FluidValveBlock))
			return;
		boolean stateOpen = blockState.getValue(FluidValveBlock.ENABLED);

		if (stateOpen && pointer.getValue() == 0) {
			switchToBlockState(level, worldPosition, blockState.setValue(FluidValveBlock.ENABLED, false));
			return;
		}
		if (!stateOpen && pointer.getValue() == 1) {
			switchToBlockState(level, worldPosition, blockState.setValue(FluidValveBlock.ENABLED, true));
			return;
		}
	}

	private float getChaseSpeed() {
		return Mth.clamp(Math.abs(getSpeed()) / 16 / 20, 0, 1);
	}

	/** How far open the valve is turned, 0 to 1; the visible handle pointer follows this. */
	public float getPointerValue() {
		return pointer.getValue();
	}

	@Override
	protected void write(ValueOutput compound) {
		super.write(compound);
		compound.store("Pointer", CompoundTag.CODEC, pointer.writeNBT());
	}

	@Override
	protected void read(ValueInput compound) {
		super.read(compound);
		compound.read("Pointer", CompoundTag.CODEC)
			.ifPresent(tag -> pointer.readNBT(tag, false));
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		behaviours.add(new ValvePipeBehaviour(this));
	}

	/** Create's Block#onRemove: a replaced valve tells the pipes and pumps around it to rebuild their network. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null && !level.isClientSide())
			FluidPropagator.propagateChangedPipe(level, pos, state);
		super.preRemoveSideEffects(pos, state);
	}

	class ValvePipeBehaviour extends StraightPipeFluidTransportBehaviour {

		public ValvePipeBehaviour(SmartBlockEntity be) {
			super(be);
		}

		@Override
		public boolean canHaveFlowToward(BlockState state, Direction direction) {
			return FluidValveBlock.getPipeAxis(state) == direction.getAxis();
		}

		@Override
		public boolean canPullFluidFrom(FluidStack fluid, BlockState state, Direction direction) {
			if (state.hasProperty(FluidValveBlock.ENABLED) && state.getValue(FluidValveBlock.ENABLED))
				return super.canPullFluidFrom(fluid, state, direction);
			return false;
		}

	}

}
