package dev.createpolymer.kinetics;

import java.util.HashMap;
import java.util.Map;

import dev.createpolymer.kinetics.base.KineticBlockEntity;
import net.minecraft.world.level.LevelAccessor;

/** Ported from Create's TorquePropagator (MIT, The Create Team). */
public final class TorquePropagator {

	static final Map<LevelAccessor, Map<Long, KineticNetwork>> networks = new HashMap<>();

	private TorquePropagator() {}

	public static void onLoadWorld(LevelAccessor world) {
		networks.put(world, new HashMap<>());
	}

	public static void onUnloadWorld(LevelAccessor world) {
		networks.remove(world);
	}

	public static KineticNetwork getOrCreateNetworkFor(KineticBlockEntity be) {
		Long id = be.network;
		Map<Long, KineticNetwork> map = networks.computeIfAbsent(be.getLevel(), $ -> new HashMap<>());
		if (id == null)
			return null;

		return map.computeIfAbsent(id, $ -> {
			KineticNetwork network = new KineticNetwork();
			network.id = id;
			return network;
		});
	}
}
