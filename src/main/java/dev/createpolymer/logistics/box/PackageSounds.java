package dev.createpolymer.logistics.box;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Create's sound events of the package blocks. Those with an existing vanilla sound in Create's sounds.json play
 * it, the few with sound files of their own (the stock link, the frogport closing) use a similar vanilla sound,
 * since the resource pack carries no sounds.
 */
public final class PackageSounds {
	private PackageSounds() {}

	public static void packager(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.SHULKER_BOX_OPEN, SoundSource.BLOCKS, .5f, .75f);
	}

	public static void packagePop(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.CHISELED_BOOKSHELF_BREAK, SoundSource.BLOCKS, .75f, 1f);
		level.playSound(null, pos, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, .25f, 1.15f);
	}

	public static void depotPlop(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, .25f, 1.25f);
	}

	public static void itemPickup(Player player) {
		player.level()
			.playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, .2f,
				1f + player.level().getRandom().nextFloat());
	}

	public static void stockLink(Level level, BlockPos pos, float volume, float pitch) {
		level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, volume, pitch);
	}

	public static void frogportOpen(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.BLOCKS, .5f, 2f);
	}

	public static void frogportClose(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.FROG_EAT, SoundSource.BLOCKS, 1f,
			1.25f + level.getRandom().nextFloat() * .25f);
	}

	public static void frogportCatch(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.FROG_EAT, SoundSource.BLOCKS, 1f, 1f);
	}

	public static void frogportDeposit(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.FROG_TONGUE, SoundSource.BLOCKS, 1f, 1f);
	}

	public static void chainStep(Level level, Vec3 pos) {
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.CHAIN_STEP, SoundSource.BLOCKS, .25f, 1.2f);
	}
}
