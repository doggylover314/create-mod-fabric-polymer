package dev.createpolymer.kinetics.turntable;

import dev.createpolymer.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Ported from Create's TurntableBlockEntity (MIT, The Create Team). */
public class TurntableBlockEntity extends KineticBlockEntity {

	public TurntableBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

}
