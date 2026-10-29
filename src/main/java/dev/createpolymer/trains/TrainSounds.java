package dev.createpolymer.trains;

import net.minecraft.core.Vec3i;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Create's sound events for the train blocks are combinations of vanilla sounds (see AllSoundEvents).
 */
public final class TrainSounds {
	private TrainSounds() {}

	/** AllSoundEvents.DENY */
	public static void deny(Level level, Player except, Vec3i pos, float volume, float pitch) {
		level.playSound(except, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, SoundEvents.NOTE_BLOCK_BASS.value(),
			SoundSource.PLAYERS, volume, pitch * .5f);
	}

	/** AllSoundEvents.CONTROLLER_CLICK */
	public static void controllerClick(Level level, Player except, Vec3i pos, float volume, float pitch) {
		level.playSound(except, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, SoundEvents.ITEM_FRAME_ADD_ITEM,
			SoundSource.BLOCKS, volume * .35f, pitch);
	}

}
