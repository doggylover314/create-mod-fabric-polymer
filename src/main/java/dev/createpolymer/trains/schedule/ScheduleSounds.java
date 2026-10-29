package dev.createpolymer.trains.schedule;

import dev.createpolymer.logistics.box.PackageSounds;
import dev.createpolymer.trains.TrainSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Create's sound events for the schedules are combinations of vanilla sounds (see AllSoundEvents).
 */
public final class ScheduleSounds {
	private ScheduleSounds() {}

	/** AllSoundEvents.CONFIRM */
	public static void confirm(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, .5f, .8f);
	}

	public static void confirm(Level level, Player player) {
		confirm(level, player.blockPosition());
	}

	/** AllSoundEvents.DENY */
	public static void deny(Level level, Player player) {
		TrainSounds.deny(level, null, player.blockPosition(), 1, 1);
	}

	/** AllSoundEvents.playItemPickup */
	public static void itemPickup(Player player) {
		PackageSounds.itemPickup(player);
	}
}
