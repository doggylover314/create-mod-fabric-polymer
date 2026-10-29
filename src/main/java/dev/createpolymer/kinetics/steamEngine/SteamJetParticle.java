package dev.createpolymer.kinetics.steamEngine;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

/**
 * Create's steam jet particle is a client-side particle type. Players here have no such type, so a jet is a vanilla
 * cloud particle sent from the server with the same position and direction.
 */
public final class SteamJetParticle {
	private SteamJetParticle() {}

	public static void spawn(ServerLevel level, double x, double y, double z, double dx, double dy, double dz) {
		// count 0: the offset is the particle's velocity, scaled by the speed
		level.sendParticles(ParticleTypes.CLOUD, x, y, z, 0, dx, dy, dz, .2);
	}
}
