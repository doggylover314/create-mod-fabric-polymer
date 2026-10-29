package dev.createpolymer.api.behaviour.interaction;

import dev.createpolymer.contraptions.AbstractContraptionEntity;
import dev.createpolymer.processing.burner.BlazeBurnerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Partial interaction behavior implementation that allows blocks to act as conductors on trains, like Blaze Burners.
 * The carriage assembly asks it which blocks can drive a train; the handling of schedule items on a conductor
 * (Create's {@code handlePlayerInteraction}) comes with the schedules.
 */
public abstract class ConductorBlockInteractionBehavior extends MovingInteractionBehaviour {
	/**
	 * Check if the given state is capable of being a conductor.
	 */
	public abstract boolean isValidConductor(BlockState state);

	@Override
	public boolean handlePlayerInteraction(Player player, InteractionHand activeHand, BlockPos localPos,
		AbstractContraptionEntity contraptionEntity) {
		return false;
	}

	/**
	 * Implementation used for Blaze Burners. May be reused by addons if applicable.
	 */
	public static class BlazeBurner extends ConductorBlockInteractionBehavior {
		@Override
		public boolean isValidConductor(BlockState state) {
			return state.getValue(BlazeBurnerBlock.HEAT_LEVEL) != BlazeBurnerBlock.HeatLevel.NONE;
		}
	}
}
