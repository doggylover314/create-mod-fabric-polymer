package dev.createpolymer.util;

import java.text.NumberFormat;
import java.util.Locale;

public class Lang {

	public static String asId(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	public static String nonPluralId(String name) {
		String asId = asId(name);
		return asId.endsWith("s") ? asId.substring(0, asId.length() - 1) : asId;
	}

	public static LangBuilder builder(String namespace) {
		return new LangBuilder(namespace);
	}

	/** Catnip's LangNumberFormat: at most two decimals, thousands separators. */
	public static String formatNumber(double d) {
		NumberFormat format = NumberFormat.getInstance(Locale.ROOT);
		format.setMaximumFractionDigits(2);
		format.setMinimumFractionDigits(0);
		format.setGroupingUsed(true);
		return format.format(d)
			.replace('\u00A0', ' ');
	}

}
