package dev.createpolymer.equipment;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * Create's clipboard sounds are audio files in its resource pack, vanilla sounds stand in here
 * (AllSoundEvents.CLIPBOARD_CHECKMARK / CLIPBOARD_ERASE). The controller sounds are vanilla sounds in Create too
 * (AllSoundEvents.CONTROLLER_CLICK / CONTROLLER_PUT / CONTROLLER_TAKE).
 */
public final class ToolsSounds {
	private ToolsSounds() {}

	public static void clipboardCheck(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1, .95f + level.getRandom().nextFloat() * .05f);
	}

	public static void clipboardErase(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, .6f,
			.9f + level.getRandom().nextFloat() * .2f);
	}

	/** CONTROLLER_CLICK, which only the player using the controller hears (Create plays it on the client). */
	public static void controllerClick(ServerPlayer player, float volume, float pitch) {
		player.connection.send(new ClientboundSoundPacket(Holder.direct(SoundEvents.ITEM_FRAME_ADD_ITEM), SoundSource.BLOCKS,
			player.getX(), player.getY(), player.getZ(), volume * .35f, pitch, player.level().getRandom().nextLong()));
	}

	public static void controllerPut(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1, 1);
	}

	public static void controllerTake(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1, 1);
	}
}
