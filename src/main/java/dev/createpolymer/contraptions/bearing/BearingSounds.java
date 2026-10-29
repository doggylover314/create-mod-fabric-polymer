package dev.createpolymer.contraptions.bearing;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/** Create's CONTRAPTION_ASSEMBLE, CONTRAPTION_DISASSEMBLE and SLIME_ADDED sounds (combinations of vanilla sounds). */
public final class BearingSounds {
	private BearingSounds() {}

	public static void assemble(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.BLOCKS, .5f, .5f);
		level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, .045f, .74f);
	}

	public static void disassemble(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, .35f, .75f);
	}

	public static void slimeAdded(Level level, BlockPos pos, float volume, float pitch) {
		level.playSound(null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, volume, pitch);
	}
}
