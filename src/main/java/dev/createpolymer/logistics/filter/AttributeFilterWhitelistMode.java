package dev.createpolymer.logistics.filter;

import org.jetbrains.annotations.NotNull;

import com.mojang.serialization.Codec;

import dev.createpolymer.util.Lang;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

public enum AttributeFilterWhitelistMode implements StringRepresentable {
	WHITELIST_DISJ, WHITELIST_CONJ, BLACKLIST;

	public static final Codec<AttributeFilterWhitelistMode> CODEC = StringRepresentable.fromValues(AttributeFilterWhitelistMode::values);
	public static final StreamCodec<io.netty.buffer.ByteBuf, AttributeFilterWhitelistMode> STREAM_CODEC =
		ByteBufCodecs.idMapper(i -> values()[i], AttributeFilterWhitelistMode::ordinal);

	@Override
	public @NotNull String getSerializedName() {
		return Lang.asId(name());
	}
}
