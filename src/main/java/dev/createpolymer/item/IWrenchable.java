package dev.createpolymer.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

/** Lets a block override what the wrench does (Create's IWrenchable). Return null to use the default. */
public interface IWrenchable {
	default InteractionResult onWrenched(BlockState state, UseOnContext context) {
		return null;
	}

	default InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
		return null;
	}
}
