package dev.createpolymer.redstone.displayLink.source;

import dev.createpolymer.redstone.displayLink.DisplayLinkContext;
import dev.createpolymer.trains.display.FlapDisplaySection;
import net.minecraft.network.chat.Component;

public abstract class NumericSingleLineDisplaySource extends SingleLineDisplaySource {

	protected static final Component ZERO;

	static {
		ZERO = Component.literal("0");
	}

	@Override
	protected String getFlapDisplayLayoutName(DisplayLinkContext context) {
		return "Number";
	}

	@Override
	protected FlapDisplaySection createSectionForValue(DisplayLinkContext context, int size) {
		return new FlapDisplaySection(size * FlapDisplaySection.MONOSPACE, "numeric", false, false);
	}

}
