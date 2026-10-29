package dev.createpolymer.kinetics.chainConveyor;

import java.util.Map;
import java.util.UUID;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.world.entity.player.Player;

/**
 * Create's ServerChainConveyorHandler: which players hang on a chain. Create's clients tell the server about it
 * and the server tells everyone else's clients to draw the pose; here the server moves the riders itself
 * ({@link ChainConveyorRidingHandler}), so it only keeps the list.
 */
public class ServerChainConveyorHandler {

	public static Object2IntMap<UUID> hangingPlayers = new Object2IntOpenHashMap<>();

	public static int ticks;

	public static void handleTTLPacket(Player player) {
		hangingPlayers.put(player.getUUID(), 20);
	}

	public static void handleStopRidingPacket(Player player) {
		hangingPlayers.removeInt(player.getUUID());
	}

	public static boolean isHanging(Player player) {
		return hangingPlayers.containsKey(player.getUUID());
	}

	public static void tick() {
		ticks++;

		ChainConveyorRidingHandler.tick();

		ObjectIterator<Object2IntMap.Entry<UUID>> iterator = hangingPlayers.object2IntEntrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Integer> entry = iterator.next();
			int newTTL = entry.getValue() - 1;
			if (newTTL <= 0) {
				iterator.remove();
			} else {
				entry.setValue(newTTL);
			}
		}
	}

}
