package dev.createpolymer.equipment.bell;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.createpolymer.registry.feature.ExtrasRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Create's HauntedBellPulser. A player holding a haunted bell sends soul pulses after a short warm-up; each pulse
 * is run by the server tick of its level.
 */
public final class HauntedBellPulser {
	private HauntedBellPulser() {}

	public static final int DISTANCE = 3;
	public static final int RECHARGE_TICKS = 8;
	public static final int WARMUP_TICKS = 10;

	/** Warm-up ticks left per player. Create expires these after 250 ms without holding the bell; here they reset on the tick the bell is not held. */
	private static final Map<UUID, Integer> WARMUP = new HashMap<>();
	private static final Map<ResourceKey<Level>, SoulPulseEffectHandler> HANDLERS = new HashMap<>();

	/** Server tick: the pulses of the players holding a haunted bell, then the running pulses. */
	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || !holdsHauntedBell(player)) {
				WARMUP.remove(player.getUUID());
				continue;
			}

			int ticks = WARMUP.getOrDefault(player.getUUID(), WARMUP_TICKS);
			boolean firstPulse = ticks == 1;
			ticks--;
			WARMUP.put(player.getUUID(), ticks);
			if (ticks > 0)
				continue;

			long gameTime = player.level().getGameTime();
			if ((firstPulse || gameTime % RECHARGE_TICKS != 0) && player.level() instanceof ServerLevel serverLevel)
				sendPulse(serverLevel, player.blockPosition(), DISTANCE, false);
		}

		for (ServerLevel level : server.getAllLevels()) {
			SoulPulseEffectHandler handler = HANDLERS.get(level.dimension());
			if (handler != null)
				handler.tick(level);
		}
	}

	private static boolean holdsHauntedBell(ServerPlayer player) {
		return isHauntedBell(player.getMainHandItem()) || isHauntedBell(player.getOffhandItem());
	}

	private static boolean isHauntedBell(ItemStack stack) {
		return stack.is(ExtrasRegistry.HAUNTED_BELL.asItem());
	}

	public static void sendPulse(ServerLevel world, BlockPos pos, int distance, boolean canOverlap) {
		HANDLERS.computeIfAbsent(world.dimension(), k -> new SoulPulseEffectHandler())
			.addPulse(new SoulPulseEffect(pos, distance, canOverlap));
	}

	/** Forgets the pulses of a level that was unloaded. */
	public static void unload(ResourceKey<Level> dimension) {
		HANDLERS.remove(dimension);
	}
}
