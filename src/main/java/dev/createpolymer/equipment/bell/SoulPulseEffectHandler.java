package dev.createpolymer.equipment.bell;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Create's SoulPulseEffectHandler: the pulses of one level, ticked by the server. */
public class SoulPulseEffectHandler {

	private final List<SoulPulseEffect> pulses = new ArrayList<>();
	private final Set<BlockPos> occupied = new HashSet<>();

	public void tick(ServerLevel world) {
		for (SoulPulseEffect pulse : pulses) {
			List<BlockPos> spawns = pulse.tick(world);
			if (spawns == null)
				continue;

			if (pulse.canOverlap()) {
				for (BlockPos pos : spawns) {
					pulse.spawnParticles(world, pos);
				}
			} else {
				for (BlockPos pos : spawns) {
					if (occupied.contains(pos))
						continue;

					pulse.spawnParticles(world, pos);
					pulse.added.add(pos);
					occupied.add(pos);
				}
			}
		}

		for (SoulPulseEffect pulse : pulses) {
			if (pulse.finished() && !pulse.canOverlap())
				occupied.removeAll(pulse.added);
		}
		pulses.removeIf(SoulPulseEffect::finished);
	}

	public void addPulse(SoulPulseEffect pulse) {
		pulses.add(pulse);
	}
}
