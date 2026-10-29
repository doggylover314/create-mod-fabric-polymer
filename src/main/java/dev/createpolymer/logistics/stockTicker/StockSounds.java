package dev.createpolymer.logistics.stockTicker;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * Create's sound events of the stock ticker, the redstone requester and the shops (AllSoundEvents). They have sound
 * files of their own, which the resource pack does not carry, so similar vanilla sounds play instead.
 */
public final class StockSounds {
	private StockSounds() {}

	public static void stockTickerRequest(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, .5f, 1.5f);
		level.playSound(null, pos, SoundEvents.WOODEN_BUTTON_CLICK_ON, SoundSource.BLOCKS, .5f, 1.2f);
	}

	public static void stockTickerTrade(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1f, 1.2f);
		level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, .5f, 1.4f);
	}

	public static void stockLink(Level level, BlockPos pos, float volume, float pitch) {
		level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, volume, pitch);
	}

	public static void confirm(Level level, BlockPos pos, float volume, float pitch) {
		level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, volume, pitch);
	}

	public static void confirm2(Level level, BlockPos pos, float volume, float pitch) {
		level.playSound(null, pos, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, volume, pitch);
	}

	public static void deny(Level level, BlockPos pos, float volume, float pitch) {
		level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, volume, pitch * .5f);
	}

	public static void itemPickup(net.minecraft.world.entity.player.Player player) {
		player.level()
			.playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, .2f,
				1f + player.level().getRandom().nextFloat());
	}
}
