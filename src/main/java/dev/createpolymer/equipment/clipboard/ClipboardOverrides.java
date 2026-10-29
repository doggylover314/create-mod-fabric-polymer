package dev.createpolymer.equipment.clipboard;

import org.jetbrains.annotations.NotNull;

import com.mojang.serialization.Codec;

import dev.createpolymer.util.Lang;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * Create picks the clipboard's item model from a model predicate. Here the item picks one of three item models by
 * the content's {@link ClipboardType} (see {@link ClipboardBlockItem#getPolymerItemModel}).
 */
public class ClipboardOverrides {

	public enum ClipboardType implements StringRepresentable {
		EMPTY("empty_clipboard"), WRITTEN("clipboard"), EDITING("clipboard_and_quill");

		public static final Codec<ClipboardType> CODEC = StringRepresentable.fromValues(ClipboardType::values);
		public static final StreamCodec<io.netty.buffer.ByteBuf, ClipboardType> STREAM_CODEC =
			ByteBufCodecs.idMapper(i -> values()[i], ClipboardType::ordinal);

		public final String file;

		ClipboardType(String file) {
			this.file = file;
		}

		@Override
		public @NotNull String getSerializedName() {
			return Lang.asId(name());
		}
	}

}
