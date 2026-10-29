package dev.createpolymer.foundation.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Text for the server-side GUIs. Keys Create has are looked up as usual; keys that only exist in this port carry an
 * English fallback, which the client shows when the resource pack's language file lacks the key.
 */
final class GuiText {
	private GuiText() {}

	static Item dye(DyeColor color) {
		return Items.DYE.pick(color);
	}

	static Item glassPane(DyeColor color) {
		return Items.STAINED_GLASS_PANE.pick(color);
	}

	static MutableComponent text(String key, String fallback, Object... args) {
		return Component.translatableWithFallback(key, fallback, args);
	}
}
