package dev.createpolymer.contraptions.bearing;

import dev.createpolymer.api.behaviour.movement.MovementBehaviour;
import dev.createpolymer.contraptions.behaviour.MovementContext;
import net.minecraft.world.item.ItemStack;

/**
 * A mechanical bearing on a contraption. Create's renderer counter-rotates its top plate against the parent
 * contraption; the displays of a contraption stand still on this port, so only the flags remain.
 */
public class StabilizedBearingMovementBehaviour implements MovementBehaviour {

	@Override
	public ItemStack canBeDisabledVia(MovementContext context) {
		return null;
	}

	@Override
	public boolean disableBlockEntityRendering() {
		return true;
	}

}
