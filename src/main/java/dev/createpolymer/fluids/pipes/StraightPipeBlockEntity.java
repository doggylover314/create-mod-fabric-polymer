package dev.createpolymer.fluids.pipes;

import java.util.List;

import dev.createpolymer.decoration.bracket.BracketedBlockEntityBehaviour;
import dev.createpolymer.fluids.FluidPropagator;
import dev.createpolymer.fluids.FluidTransportBehaviour;
import dev.createpolymer.fluids.pipes.valve.FluidValveBlock;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class StraightPipeBlockEntity extends SmartBlockEntity {

	public StraightPipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		behaviours.add(new StraightPipeFluidTransportBehaviour(this));
		behaviours.add(new BracketedBlockEntityBehaviour(this));
	}

	/** Create's Block#onRemove: a replaced pipe tells the pipes and pumps around it to rebuild their network. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null && !level.isClientSide())
			FluidPropagator.propagateChangedPipe(level, pos, state);
		super.preRemoveSideEffects(pos, state);
	}

	public static class StraightPipeFluidTransportBehaviour extends FluidTransportBehaviour {

		public StraightPipeFluidTransportBehaviour(SmartBlockEntity be) {
			super(be);
		}

		@Override
		public boolean canHaveFlowToward(BlockState state, Direction direction) {
			return state.hasProperty(AxisPipeBlock.AXIS) && state.getValue(AxisPipeBlock.AXIS) == direction.getAxis();
		}

		@Override
		public AttachmentTypes getRenderedRimAttachment(BlockGetter world, BlockPos pos, BlockState state,
			Direction direction) {
			AttachmentTypes attachment = super.getRenderedRimAttachment(world, pos, state, direction);
			BlockState otherState = world.getBlockState(pos.relative(direction));

			Axis axis = IAxisPipe.getAxisOf(state);
			Axis otherAxis = IAxisPipe.getAxisOf(otherState);

			if (attachment == AttachmentTypes.RIM && state.getBlock() instanceof FluidValveBlock)
				return AttachmentTypes.NONE;
			if (attachment == AttachmentTypes.RIM && !(state.getBlock() instanceof GlassFluidPipeBlock)
				&& otherState.getBlock() instanceof GlassFluidPipeBlock)
				return AttachmentTypes.PARTIAL_RIM;

			if (attachment == AttachmentTypes.RIM && FluidPipeBlock.isPipe(otherState))
				return AttachmentTypes.NONE;
			if (axis == otherAxis && axis != null)
				return AttachmentTypes.NONE;

			if (otherState.getBlock() instanceof FluidValveBlock
				&& FluidValveBlock.getPipeAxis(otherState) == direction.getAxis())
				return AttachmentTypes.NONE;

			return attachment.withoutConnector();
		}

	}

}
