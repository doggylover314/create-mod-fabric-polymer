package dev.createpolymer.equipment.zapper;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

/**
 * Stands in for Create's ZapperBeamPacket, ShootGadgetPacket and ZapperRenderHandler: the laser beam Create draws
 * client side as a fading outline line, and the burst where it lands, are sent as particles to the players near
 * them, and the zap sound (Create's worldshaper_place, which plays an existing note block sound) is played.
 */
public final class ZapperBeamEffect {
	private ZapperBeamEffect() {}

	/** Particles are sent about every two blocks of the beam, no more than this many. */
	private static final int MAX_BEAM_PARTICLES = 48;

	public static void send(ServerLevel level, Vec3 start, Vec3 end, InteractionHand hand) {
		RandomSource random = level.getRandom();

		// Create's LaserBeam line
		Vec3 diff = end.subtract(start);
		double length = diff.length();
		int steps = Math.min(MAX_BEAM_PARTICLES, Math.max(1, (int) (length / 2)));
		for (int i = 1; i < steps; i++) {
			Vec3 pos = start.add(diff.scale(i / (double) steps));
			level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
		}

		// Create's addBeam: ten sparks and ten firework bursts around the end of the beam
		level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 10, .1, .1, .1, .1);
		level.sendParticles(ParticleTypes.FIREWORK, end.x, end.y, end.z, 10, .1, .1, .1, 0);

		float pitch = hand == InteractionHand.MAIN_HAND ? 0.1f : 0.9f;
		level.playSound(null, start.x, start.y, start.z, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.PLAYERS, 0.1f,
			pitch);
	}
}
