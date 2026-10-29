package dev.createpolymer.redstone.displayLink;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.createpolymer.CreatePolymer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Text helpers for what display links write into signs, books, nixie tubes and boards. Those hold plain text that is
 * not translated per player, and a dedicated server does not know Create's language file, so translatable components
 * are resolved here with Create's English texts (Create's own server relies on NeoForge loading the mods' language
 * files on the server).
 */
public final class DisplayText {
	private static final Pattern FORMAT = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");
	private static Map<String, String> lang;

	private DisplayText() {}

	private static synchronized Map<String, String> lang() {
		if (lang != null)
			return lang;
		Map<String, String> map = new HashMap<>();
		try (InputStream in = DisplayText.class.getResourceAsStream("/assets/" + CreatePolymer.MOD_ID + "/lang/en_us.json")) {
			if (in != null) {
				JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				for (Map.Entry<String, JsonElement> entry : json.entrySet())
					if (entry.getValue().isJsonPrimitive())
						map.put(entry.getKey(), entry.getValue().getAsString());
			}
		} catch (Exception e) {
			CreatePolymer.LOGGER.error("Could not read Create's language file", e);
		}
		return lang = map;
	}

	/** Replaces the translatable parts of a component by literal text, keeping the styles. */
	public static MutableComponent resolve(Component component) {
		MutableComponent out;
		if (component.getContents() instanceof TranslatableContents translatable) {
			Object[] args = translatable.getArgs();
			String template = lang().get(translatable.getKey());
			if (template != null) {
				out = Component.empty();
				format(template, args, out);
			} else {
				Object[] resolved = new Object[args.length];
				for (int i = 0; i < args.length; i++)
					resolved[i] = args[i] instanceof Component c ? resolve(c) : args[i];
				out = MutableComponent.create(new TranslatableContents(translatable.getKey(), translatable.getFallback(), resolved));
			}
		} else {
			out = MutableComponent.create(component.getContents());
		}
		out.setStyle(component.getStyle());
		for (Component sibling : component.getSiblings())
			out.append(resolve(sibling));
		return out;
	}

	private static void format(String template, Object[] args, MutableComponent out) {
		Matcher matcher = FORMAT.matcher(template);
		int position = 0;
		int next = 0;
		while (matcher.find()) {
			if (matcher.start() > position)
				out.append(Component.literal(template.substring(position, matcher.start())));
			position = matcher.end();
			String type = matcher.group(2);
			if ("%".equals(type)) {
				out.append(Component.literal("%"));
				continue;
			}
			int index = matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) - 1 : next++;
			if (index >= 0 && index < args.length)
				out.append(args[index] instanceof Component c ? resolve(c) : Component.literal(String.valueOf(args[index])));
		}
		if (position < template.length())
			out.append(Component.literal(template.substring(position)));
	}

	/** Create's NBTProcessors.textComponentHasClickEvent: text with click events must not reach signs and books. */
	public static boolean hasClickEvent(Component component) {
		if (component.getStyle().getClickEvent() != null)
			return true;
		if (component.getContents() instanceof TranslatableContents translatable)
			for (Object arg : translatable.getArgs())
				if (arg instanceof Component c && hasClickEvent(c))
					return true;
		for (Component sibling : component.getSiblings())
			if (hasClickEvent(sibling))
				return true;
		return false;
	}

	/** Create's CreateLang.translatedOptions: {@code create.<prefix>.<key>} for each key. */
	public static java.util.List<Component> translatedOptions(String prefix, String... keys) {
		java.util.List<Component> result = new java.util.ArrayList<>(keys.length);
		for (String key : keys)
			result.add(Component.translatable(CreatePolymer.MOD_ID + "." + prefix + "." + key));
		return result;
	}

	/** Create's LangNumberFormat: at most two decimals, no grouping. */
	public static String number(double d) {
		if (d == (long) d)
			return String.valueOf((long) d);
		java.text.DecimalFormat format = new java.text.DecimalFormat("0.##", java.text.DecimalFormatSymbols.getInstance(java.util.Locale.ROOT));
		return format.format(d);
	}
}
