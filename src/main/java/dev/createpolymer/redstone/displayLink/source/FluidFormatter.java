package dev.createpolymer.redstone.displayLink.source;

import dev.createpolymer.redstone.displayLink.DisplayText;
import dev.createpolymer.util.Couple;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Create's FluidFormatter. */
public class FluidFormatter {

	public static String asString(long amount, boolean shorten) {
		Couple<MutableComponent> couple = asComponents(amount, shorten);
		return DisplayText.resolve(couple.getFirst()).getString() + " " + DisplayText.resolve(couple.getSecond()).getString();
	}

	public static Couple<MutableComponent> asComponents(long amount, boolean shorten) {
		if (shorten && amount >= 1000) {
			return Couple.create(
					Component.literal(String.format(java.util.Locale.ROOT, "%.1f" , amount / 1000d)),
					Component.translatable("create.generic.unit.buckets")
			);
		}

		return Couple.create(
				Component.literal(String.valueOf(amount)),
				Component.translatable("create.generic.unit.millibuckets")
		);
	}

}
