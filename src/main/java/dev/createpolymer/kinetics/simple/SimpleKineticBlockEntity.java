package dev.createpolymer.kinetics.simple;

import java.util.List;

import dev.createpolymer.api.contraption.transformable.TransformableBlockEntity;
import dev.createpolymer.contraptions.StructureTransform;
import dev.createpolymer.decoration.bracket.BracketedBlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.kinetics.base.ICogWheel;
import dev.createpolymer.kinetics.base.IRotate;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ported from Create's SimpleKineticBlockEntity (MIT, The Create Team). Used by shafts and cogwheels.
 * Create splits its bracket-holding BracketedKineticBlockEntity off for the shafts and cogwheels; here the same type
 * serves the encased cogwheels too, so the bracket behaviour is only added for {@link AbstractSimpleShaftBlock}s.
 */
public class SimpleKineticBlockEntity extends KineticBlockEntity implements TransformableBlockEntity {

	public SimpleKineticBlockEntity(BlockPos pos, BlockState state) {
		this(ModBlockEntities.SIMPLE_KINETIC, pos, state);
	}

	public SimpleKineticBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		if (getBlockState().getBlock() instanceof AbstractSimpleShaftBlock)
			behaviours.add(new BracketedBlockEntityBehaviour(this,
				state -> state.getBlock() instanceof AbstractSimpleShaftBlock));
		super.addBehaviours(behaviours);
	}

	@Override
	public void transform(BlockEntity be, StructureTransform transform) {
		BracketedBlockEntityBehaviour bracketBehaviour = getBehaviour(BracketedBlockEntityBehaviour.TYPE);
		if (bracketBehaviour != null)
			bracketBehaviour.transformBracket(transform);
	}

	@Override
	public List<BlockPos> addPropagationLocations(IRotate block, BlockState state, List<BlockPos> neighbours) {
		if (!ICogWheel.isLargeCog(state))
			return super.addPropagationLocations(block, state, neighbours);

		BlockPos.betweenClosedStream(new BlockPos(-1, -1, -1), new BlockPos(1, 1, 1))
			.forEach(offset -> {
				if (offset.distSqr(BlockPos.ZERO) == 2)
					neighbours.add(worldPosition.offset(offset));
			});
		return neighbours;
	}
}
