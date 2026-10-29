package dev.createpolymer.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * Create's sound events for these blocks are just combinations of vanilla
 * sounds (see Create's sounds.json), so they are played directly.
 */
public final class ModSounds {
	private ModSounds() {}

	public static void cranking(Level level, BlockPos pos, float volume, float pitch) {
		level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, volume * .075f, pitch);
		level.playSound(null, pos, SoundEvents.WOODEN_BUTTON_CLICK_OFF, SoundSource.BLOCKS, volume * .025f, pitch);
	}

	public static void pressActivation(Level level, BlockPos pos, float volume, float pitch) {
		level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, volume * .125f, pitch);
		level.playSound(null, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, volume * .5f, pitch * .85f);
	}

	public static void pressActivationOnBelt(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.WOOL_HIT, SoundSource.BLOCKS, 1f, 1f);
	}

	public static void mixing(Level level, BlockPos pos, float volume, float pitch) {
		level.playSound(null, pos, SoundEvents.GILDED_BLACKSTONE_BREAK, SoundSource.BLOCKS, volume * .125f, pitch * .5f);
		level.playSound(null, pos, SoundEvents.NETHERRACK_BREAK, SoundSource.BLOCKS, volume * .125f, pitch * .5f);
	}

	public static void wrenchRotate(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.BLOCKS, .25f, 1.25f);
	}

	public static void wrenchRemove(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, .25f, .75f);
		level.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_HIT, SoundSource.PLAYERS, .25f, .75f);
	}
}
