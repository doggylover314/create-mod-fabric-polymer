package dev.createpolymer.contraptions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps players standing on moving contraptions. Vanilla clients run their own physics, so the server cannot push a
 * player the way Create's collider pushes mobs. Two things make a platform usable instead:
 * <ul>
 * <li>the {@code ContraptionVisual} gives solid blocks an invisible shulker hitbox the client collides with;</li>
 * <li>this class notices (from Create's collider, run against the server-side player position) that a player stands
 * on the contraption, resets the anti-fly counter, and while the contraption moves it hands the platform's motion to
 * the player with a motion packet each tick, adding it to the walking motion estimated from the player's last
 * movement.</li>
 * </ul>
 * This is an approximation: the player is carried by velocity, not by collision, and rotating contraptions only
 * carry the player along the contact point's motion (the player does not turn with it).
 */
public final class ContraptionPlayerSupport {
	/** How far under the feet the server still counts a player as standing on a block (client/server lag). */
	public static final double SUPPORT_REACH = 0.15;
	/** Horizontal velocity kept per tick while walking on the ground (0.6 block friction * 0.91). */
	private static final double GROUND_DRAG = 0.546;

	private static final Map<UUID, Vec3> LAST_CARRY = new HashMap<>();
	private static final Set<UUID> SUPPORTED = new HashSet<>();

	private ContraptionPlayerSupport() {}

	static void supportPlayer(ServerPlayer player, AbstractContraptionEntity contraptionEntity) {
		player.fallDistance = 0;
		player.connection.resetFlyingTicks();
		SUPPORTED.add(player.getUUID());

		Vec3 contact = contraptionEntity.getContactPointMotion(player.position());
		if (contact.lengthSqr() < 1e-6) {
			LAST_CARRY.remove(player.getUUID());
			return;
		}

		Vec3 previousCarry = LAST_CARRY.getOrDefault(player.getUUID(), Vec3.ZERO);
		Vec3 walking = player.getKnownMovement().subtract(previousCarry);
		Vec3 motion = new Vec3(walking.x * GROUND_DRAG + contact.x, contact.y, walking.z * GROUND_DRAG + contact.z);
		player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), motion));
		LAST_CARRY.put(player.getUUID(), contact);
	}

	/** Forgets players that were not on a contraption this tick. */
	static void tick(ServerLevel level) {
		LAST_CARRY.keySet().removeIf(id -> !SUPPORTED.contains(id)
			&& (level.getPlayerByUUID(id) != null || level.getServer().getPlayerList().getPlayer(id) == null));
		SUPPORTED.removeIf(id -> level.getPlayerByUUID(id) != null);
	}

	public static boolean isCarried(UUID player) {
		return LAST_CARRY.containsKey(player);
	}
}
