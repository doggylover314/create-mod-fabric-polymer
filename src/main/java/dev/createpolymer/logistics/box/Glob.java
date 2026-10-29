package dev.createpolymer.logistics.box;

/**
 * Turns a glob pattern into a regular expression, for package address wildcards: {@code *} matches any run of
 * characters, {@code ?} one character, {@code [abc]} a set, {@code {a,b}} an alternative. Catnip's Glob does the
 * same job for Create.
 */
public final class Glob {
	private Glob() {}

	public static String toRegexPattern(String glob) {
		StringBuilder regex = new StringBuilder("^");
		boolean inGroup = false;
		for (int i = 0; i < glob.length(); i++) {
			char c = glob.charAt(i);
			switch (c) {
			case '\\' -> {
				if (i + 1 < glob.length())
					appendLiteral(regex, glob.charAt(++i));
				else
					regex.append("\\\\");
			}
			case '*' -> regex.append(".*");
			case '?' -> regex.append('.');
			case '[' -> {
				int end = glob.indexOf(']', i + 2);
				if (end < 0) {
					regex.append("\\[");
					break;
				}
				String set = glob.substring(i + 1, end);
				boolean negate = set.startsWith("!") || set.startsWith("^");
				if (negate)
					set = set.substring(1);
				regex.append(negate ? "[^" : "[");
				for (char s : set.toCharArray()) {
					if (s == '\\' || s == '[' || s == '&' || s == '^')
						regex.append('\\');
					regex.append(s);
				}
				regex.append(']');
				i = end;
			}
			case '{' -> {
				if (inGroup) {
					regex.append("\\{");
				} else {
					regex.append("(?:(?:");
					inGroup = true;
				}
			}
			case '}' -> {
				if (inGroup) {
					regex.append("))");
					inGroup = false;
				} else
					regex.append("\\}");
			}
			case ',' -> {
				if (inGroup)
					regex.append(")|(?:");
				else
					regex.append(',');
			}
			default -> appendLiteral(regex, c);
			}
		}
		if (inGroup)
			regex.append("))");
		return regex.append('$').toString();
	}

	private static void appendLiteral(StringBuilder regex, char c) {
		if ("\\.^$|?*+()[]{}".indexOf(c) >= 0)
			regex.append('\\');
		regex.append(c);
	}
}
