package dev.createpolymer.contraptions.behaviour;

import dev.createpolymer.api.behaviour.movement.MovementBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Rings vanilla bells when the contraption they sit on changes direction or stops. Create's bell, desk bell and the
 * carriage/elevator special cases come with those blocks.
 */
public class BellMovementBehaviour implements MovementBehaviour {

	@Override
	public void onSpeedChanged(MovementContext context, Vec3 oldMotion, Vec3 motion) {
		double dotProduct = oldMotion.dot(motion);
		if (dotProduct <= 0 && (context.relativeMotion.length() != 0) || context.firstMovement)
			playSound(context);
	}

	@Override
	public void stopMoving(MovementContext context) {
		if (context.position != null && isActive(context))
			playSound(context);
	}

	public static void playSound(MovementContext context) {
		Level world = context.world;
		BlockPos pos = BlockPos.containing(context.position);

		// Vanilla bell sound
		world.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2f, 1f);
	}

}
