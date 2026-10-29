package dev.createpolymer.kinetics.simple;

import java.util.List;

import dev.createpolymer.kinetics.base.ICogWheel;
import dev.createpolymer.kinetics.base.IRotate;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Ported from Create's SimpleKineticBlockEntity (MIT, The Create Team). Used by shafts and cogwheels. */
public class SimpleKineticBlockEntity extends KineticBlockEntity {

	public SimpleKineticBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SIMPLE_KINETIC, pos, state);
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
