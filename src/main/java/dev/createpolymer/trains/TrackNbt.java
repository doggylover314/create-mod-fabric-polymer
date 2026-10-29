package dev.createpolymer.trains;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Small helpers for the parts of Create's tag handling that moved in 26.3 (UUIDs and block positions are codec
 * values now), and Create's translation keys.
 */
public final class TrackNbt {
	private TrackNbt() {}

	public static void putUUID(CompoundTag tag, String key, UUID id) {
		tag.store(key, UUIDUtil.CODEC, id);
	}

	@Nullable
	public static UUID getUUID(CompoundTag tag, String key) {
		return tag.read(key, UUIDUtil.CODEC)
			.orElse(null);
	}

	public static void putBlockPos(CompoundTag tag, String key, BlockPos pos) {
		tag.store(key, BlockPos.CODEC, pos);
	}

	public static BlockPos getBlockPos(CompoundTag tag, String key) {
		return tag.read(key, BlockPos.CODEC)
			.orElse(BlockPos.ZERO);
	}

	/** Create's {@code CreateLang.translateDirect}. */
	public static MutableComponent translateDirect(String key, Object... args) {
		return Component.translatable("create." + key, args);
	}
}
