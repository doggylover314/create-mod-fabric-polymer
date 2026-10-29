package dev.createpolymer.fluids.pipes;

import java.util.List;

import dev.createpolymer.api.contraption.transformable.TransformableBlockEntity;
import dev.createpolymer.contraptions.StructureTransform;
import dev.createpolymer.decoration.bracket.BracketedBlockEntityBehaviour;
import dev.createpolymer.fluids.FluidPropagator;
import dev.createpolymer.fluids.FluidTransportBehaviour;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class FluidPipeBlockEntity extends SmartBlockEntity implements TransformableBlockEntity {

	public FluidPipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		behaviours.add(new StandardPipeFluidTransportBehaviour(this));
		behaviours.add(new BracketedBlockEntityBehaviour(this, this::canHaveBracket));
	}

	@Override
	public void transform(BlockEntity be, StructureTransform transform) {
		BracketedBlockEntityBehaviour bracketBehaviour = getBehaviour(BracketedBlockEntityBehaviour.TYPE);
		if (bracketBehaviour != null)
			bracketBehaviour.transformBracket(transform);
	}

	private boolean canHaveBracket(BlockState state) {
		return !(state.getBlock() instanceof EncasedPipeBlock);
	}

	/** Create's Block#onRemove: a replaced pipe tells the pipes and pumps around it to rebuild their network. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null && !level.isClientSide())
			FluidPropagator.propagateChangedPipe(level, pos, state);
		super.preRemoveSideEffects(pos, state);
	}

	class StandardPipeFluidTransportBehaviour extends FluidTransportBehaviour {

		public StandardPipeFluidTransportBehaviour(SmartBlockEntity be) {
			super(be);
		}

		@Override
		public boolean canHaveFlowToward(BlockState state, Direction direction) {
			return (FluidPipeBlock.isPipe(state) || state.getBlock() instanceof EncasedPipeBlock)
				&& state.getValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(direction));
		}

		@Override
		public AttachmentTypes getRenderedRimAttachment(BlockGetter world, BlockPos pos, BlockState state,
			Direction direction) {
			AttachmentTypes attachment = super.getRenderedRimAttachment(world, pos, state, direction);

			BlockPos offsetPos = pos.relative(direction);
			BlockState otherState = world.getBlockState(offsetPos);

			if (state.getBlock() instanceof EncasedPipeBlock && attachment != AttachmentTypes.DRAIN)
				return AttachmentTypes.NONE;

			if (attachment == AttachmentTypes.RIM) {
				if (!FluidPipeBlock.isPipe(otherState) && !(otherState.getBlock() instanceof EncasedPipeBlock)
					&& !(otherState.getBlock() instanceof GlassFluidPipeBlock)) {
					FluidTransportBehaviour pipeBehaviour =
						BlockEntityBehaviour.get(world, offsetPos, FluidTransportBehaviour.TYPE);
					if (pipeBehaviour != null && pipeBehaviour.canHaveFlowToward(otherState, direction.getOpposite()))
						return AttachmentTypes.DETAILED_CONNECTION;
				}

				if (!FluidPipeBlock.shouldDrawRim(world, pos, state, direction))
					return FluidPropagator.getStraightPipeAxis(state) == direction.getAxis()
						? AttachmentTypes.CONNECTION
						: AttachmentTypes.DETAILED_CONNECTION;
			}

			if (attachment == AttachmentTypes.NONE
				&& state.getValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(direction)))
				return AttachmentTypes.DETAILED_CONNECTION;

			return attachment;
		}

	}

}
