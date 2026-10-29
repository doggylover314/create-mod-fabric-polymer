package dev.createpolymer.kinetics.gearbox;

import dev.createpolymer.kinetics.base.DirectionalShaftHalvesBlockEntity;
import dev.createpolymer.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Ported from Create's GearboxBlockEntity (MIT, The Create Team). */
public class GearboxBlockEntity extends DirectionalShaftHalvesBlockEntity {

	public GearboxBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.GEARBOX, pos, state);
	}
}
