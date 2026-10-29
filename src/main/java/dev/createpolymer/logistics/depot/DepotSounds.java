package dev.createpolymer.logistics.depot;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/** Create's depot_slide and depot_plop sounds are single vanilla sounds (see Create's AllSoundEvents). */
public final class DepotSounds {
	private DepotSounds() {}

	public static void depotSlide(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.SAND_BREAK, SoundSource.BLOCKS, .125f, 1.5f);
	}

	public static void depotPlop(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, .25f, 1.25f);
	}
}
