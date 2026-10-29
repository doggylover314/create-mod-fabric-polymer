package dev.createpolymer.decoration.steamWhistle;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.decoration.steamWhistle.WhistleBlock.WhistleSize;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Create's AllSoundEvents.WHISTLE_HIGH / WHISTLE_MEDIUM / WHISTLE_LOW / WHISTLE_CHIFF. They are custom sounds defined
 * in the resource pack's sounds.json, so they are sent as unregistered events (the client looks them up by id).
 * Create loops the whistle on the client; the server cannot, so the whistle block entity plays the 3 second sample
 * again before it ends.
 */
public final class WhistleSounds {
	private WhistleSounds() {}

	/** Length of the whistle samples in ticks (Create's whistle.ogg, whistle_high.ogg and whistle_low.ogg are 3 seconds). */
	public static final int SAMPLE_TICKS = 60;
	public static final float RANGE = 64;
	/** The client never plays a sound slower than half speed. */
	public static final float MIN_PITCH = .5f;

	public static final Identifier WHISTLE_HIGH = CreatePolymer.id("whistle_high");
	public static final Identifier WHISTLE_MEDIUM = CreatePolymer.id("whistle");
	public static final Identifier WHISTLE_LOW = CreatePolymer.id("whistle_low");
	public static final Identifier WHISTLE_CHIFF = CreatePolymer.id("chiff");

	private static final Holder<SoundEvent> HIGH = Holder.direct(SoundEvent.createFixedRangeEvent(WHISTLE_HIGH, RANGE));
	private static final Holder<SoundEvent> MEDIUM = Holder.direct(SoundEvent.createFixedRangeEvent(WHISTLE_MEDIUM, RANGE));
	private static final Holder<SoundEvent> LOW = Holder.direct(SoundEvent.createFixedRangeEvent(WHISTLE_LOW, RANGE));
	private static final Holder<SoundEvent> CHIFF = Holder.direct(SoundEvent.createFixedRangeEvent(WHISTLE_CHIFF, RANGE));

	public static Identifier idOf(WhistleSize size) {
		return size == WhistleSize.SMALL ? WHISTLE_HIGH : size == WhistleSize.MEDIUM ? WHISTLE_MEDIUM : WHISTLE_LOW;
	}

	private static Holder<SoundEvent> eventOf(WhistleSize size) {
		return size == WhistleSize.SMALL ? HIGH : size == WhistleSize.MEDIUM ? MEDIUM : LOW;
	}

	public static void playWhistle(ServerLevel level, BlockPos pos, WhistleSize size, float pitch) {
		Vec3 c = Vec3.atCenterOf(pos);
		level.playSound(null, c.x, c.y, c.z, eventOf(size), SoundSource.RECORDS, 1, pitch);
	}

	public static void playChiff(ServerLevel level, BlockPos pos, float volume, float pitch) {
		Vec3 c = Vec3.atCenterOf(pos);
		level.playSound(null, c.x, c.y, c.z, CHIFF, SoundSource.RECORDS, volume, pitch);
	}

	/** Stops the whistle sound of this size for the players that can hear it. */
	public static void stopWhistle(ServerLevel level, BlockPos pos, WhistleSize size) {
		ClientboundStopSoundPacket packet = new ClientboundStopSoundPacket(idOf(size), SoundSource.RECORDS);
		Vec3 c = Vec3.atCenterOf(pos);
		for (ServerPlayer player : level.players())
			if (player.distanceToSqr(c) <= (RANGE + 16) * (RANGE + 16))
				player.connection.send(packet);
	}
}
