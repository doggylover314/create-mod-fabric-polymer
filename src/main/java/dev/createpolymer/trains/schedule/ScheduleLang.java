package dev.createpolymer.trains.schedule;

import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.trains.TrackNbt;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Create's translation helpers (CreateLang) for the schedule texts. Keys Create has are looked up as usual; texts
 * that only the editor of this port has carry an English fallback, which the client shows when the language file of
 * the resource pack lacks the key.
 */
public final class ScheduleLang {
	private ScheduleLang() {}

	/** Create's {@code CreateLang.translateDirect}. */
	public static MutableComponent translate(String key, Object... args) {
		return TrackNbt.translateDirect(key, args);
	}

	public static MutableComponent text(String key, String fallback, Object... args) {
		return Component.translatableWithFallback("create." + key, fallback, args);
	}

	/** Create's {@code CreateLang.translatedOptions}: with a null prefix every key is a full translation key. */
	public static List<Component> translatedOptions(@Nullable String prefix, String... keys) {
		return Arrays.stream(keys)
			.<Component>map(key -> translate(prefix == null ? key : prefix + "." + key))
			.toList();
	}
}
