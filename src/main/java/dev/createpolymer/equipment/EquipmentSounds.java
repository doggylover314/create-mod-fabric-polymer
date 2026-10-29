package dev.createpolymer.equipment;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Create's own sound events for the equipment (sanding, fwoomp, potato hits, ...) need audio files in the resource
 * pack. These stand in with vanilla sounds, like Create's {@code playExisting} sounds do.
 */
public final class EquipmentSounds {
	private EquipmentSounds() {}

	public static void sandingLong(Level level, @Nullable Player player, BlockPos pos, float volume, float pitch) {
		level.playSound(player, pos, SoundEvents.AXE_SCRAPE.value(), SoundSource.BLOCKS, volume, pitch);
	}

	public static void sandingShort(Level level, Vec3 pos, float volume, float pitch) {
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.SAND_HIT, SoundSource.BLOCKS, volume, pitch);
	}

	public static void potatoHit(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.ITEM_FRAME_BREAK, SoundSource.PLAYERS, .75f, .75f);
		level.playSound(null, pos, SoundEvents.WEEPING_VINES_BREAK, SoundSource.PLAYERS, .75f, 1.25f);
	}

	public static void fwoomp(Level level, Vec3 pos, float volume, float pitch) {
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, volume * .5f, pitch);
	}

	public static void confirm(Level level, BlockPos pos, float volume, float pitch) {
		level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, volume * .5f, pitch * .8f);
	}

	public static void deny(Level level, BlockPos pos, float volume, float pitch) {
		level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, volume, pitch * .5f);
	}

	public static void steam(Level level, BlockPos pos, float volume, float pitch) {
		level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, volume, pitch);
	}
}
