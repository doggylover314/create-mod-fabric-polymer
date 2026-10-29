package dev.createpolymer.kinetics.drill;

import dev.createpolymer.kinetics.base.BlockBreakingKineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ported from Create's DrillBlockEntity (MIT, The Create Team).
 * The cobble generator optimisation (CobbleGenOptimisation) is not ported: it needs NeoForge's fluid interaction
 * registry and belt/chute inventories.
 */
public class DrillBlockEntity extends BlockBreakingKineticBlockEntity {

	public DrillBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	protected BlockPos getBreakingPos() {
		return getBlockPos().relative(getBlockState().getValue(DrillBlock.FACING));
	}

}
