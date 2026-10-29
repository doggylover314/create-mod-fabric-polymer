package dev.createpolymer.processing.burner;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.api.behaviour.movement.MovementBehaviour;
import dev.createpolymer.contraptions.behaviour.MovementContext;
import dev.createpolymer.processing.burner.BlazeBurnerBlock.HeatLevel;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's BlazeBurnerMovementBehaviour (MIT, The Create Team). The burner's head turning towards the
 * viewer and the train conductor's hat are drawn by the client in Create; here only the smoke is kept, sent by the
 * server.
 */
public class BlazeBurnerMovementBehaviour implements MovementBehaviour {

	@Override
	public @Nullable ItemStack canBeDisabledVia(MovementContext context) {
		return null;
	}

	@Override
	public void tick(MovementContext context) {
		if (!(context.world instanceof ServerLevel level) || context.position == null)
			return;
		if (!shouldRender(context))
			return;

		RandomSource r = level.getRandom();
		Vec3 c = context.position;
		Vec3 v = c.add(VecHelper.offsetRandomly(Vec3.ZERO, r, .125f)
			.multiply(1, 0, 1));
		if (r.nextInt(3) == 0 && context.motion.length() < 1 / 64f)
			level.sendParticles(ParticleTypes.LARGE_SMOKE, v.x, v.y, v.z, 0, 0, 0, 0, 0);
	}

	public void invalidate(MovementContext context) {
		context.data.remove("Conductor");
	}

	private boolean shouldRender(MovementContext context) {
		return context.state.getOptionalValue(BlazeBurnerBlock.HEAT_LEVEL)
			.orElse(HeatLevel.NONE) != HeatLevel.NONE;
	}

}
