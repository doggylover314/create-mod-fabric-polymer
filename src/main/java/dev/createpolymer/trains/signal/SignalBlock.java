package dev.createpolymer.trains.signal;

import net.minecraft.util.StringRepresentable;

/**
 * Stand-in for Create's SignalBlock: only the signal types that trains and navigation compare against exist until
 * the signals come.
 */
public class SignalBlock {

	public enum SignalType implements StringRepresentable {
		ENTRY_SIGNAL, CROSS_SIGNAL;

		@Override
		public String getSerializedName() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

}
