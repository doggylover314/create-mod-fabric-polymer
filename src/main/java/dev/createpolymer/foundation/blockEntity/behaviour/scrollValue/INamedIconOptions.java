package dev.createpolymer.foundation.blockEntity.behaviour.scrollValue;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public interface INamedIconOptions {
	String getTranslationKey();

	/** Create draws one of its own GUI icons here; the server-side GUI shows this item instead. */
	default Item getIconItem() {
		return Items.PAPER;
	}
}
