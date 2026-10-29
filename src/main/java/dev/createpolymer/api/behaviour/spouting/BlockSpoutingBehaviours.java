package dev.createpolymer.api.behaviour.spouting;

import java.util.List;
import java.util.function.Predicate;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** Create's AllBlockSpoutingBehaviours. */
public final class BlockSpoutingBehaviours {
	private BlockSpoutingBehaviours() {}

	public static void registerDefaults() {
		Predicate<Fluid> isWater = fluid -> fluid.isSame(Fluids.WATER);
		BlockSpoutingBehaviour toMud = StateChangingBehavior.setTo(250, isWater, Blocks.MUD);

		for (Block dirt : List.of(Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT)) {
			BlockSpoutingBehaviour.BY_BLOCK.register(dirt, toMud);
		}

		BlockSpoutingBehaviour.BY_BLOCK.register(Blocks.FARMLAND, StateChangingBehavior.incrementingState(100, isWater, FarmlandBlock.MOISTURE));
		BlockSpoutingBehaviour.BY_BLOCK.register(Blocks.WATER_CAULDRON, StateChangingBehavior.incrementingState(250, isWater, LayeredCauldronBlock.LEVEL));
		BlockSpoutingBehaviour.BY_BLOCK.register(Blocks.CAULDRON, CauldronSpoutingBehavior.INSTANCE);
	}
}
