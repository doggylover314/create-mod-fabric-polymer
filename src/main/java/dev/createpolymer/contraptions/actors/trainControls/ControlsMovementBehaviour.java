package dev.createpolymer.contraptions.actors.trainControls;

import dev.createpolymer.api.behaviour.movement.MovementBehaviour;
import dev.createpolymer.contraptions.behaviour.MovementContext;
import net.minecraft.world.item.ItemStack;

/**
 * Create's ControlsMovementBehaviour. Its lever animation is client rendering; what is left is that the controls
 * let go of the player when the contraption stops moving.
 */
public class ControlsMovementBehaviour implements MovementBehaviour {

	@Override
	public ItemStack canBeDisabledVia(MovementContext context) {
		return null;
	}

	@Override
	public void stopMoving(MovementContext context) {
		context.contraption.entity.stopControlling(context.localPos);
		MovementBehaviour.super.stopMoving(context);
	}

}
