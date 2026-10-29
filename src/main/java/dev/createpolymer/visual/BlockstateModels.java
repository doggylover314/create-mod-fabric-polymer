package dev.createpolymer.visual;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.createpolymer.CreatePolymer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Looks up which of Create's models (and which x/y rotation) a block state uses,
 * from Create's own blockstate files bundled at {@code create_polymer/blockstates/}.
 * Only "variants" style files are supported.
 */
public final class BlockstateModels {
	public record Variant(Identifier model, int x, int y) {
		public ItemStack stack() {
			return ModelItems.of(model);
		}

		public Quaternionf rotation() {
			return Orientation.variant(x, y);
		}
	}

	private record Entry(Map<String, String> conditions, Variant variant) {}

	private static final Map<String, List<Entry>> CACHE = new HashMap<>();

	private BlockstateModels() {}

	public static synchronized @Nullable Variant get(String file, BlockState state) {
		List<Entry> entries = CACHE.computeIfAbsent(file, BlockstateModels::load);
		for (Entry e : entries) {
			boolean match = true;
			for (var c : e.conditions.entrySet()) {
				if (!c.getValue().equals(valueOf(state, c.getKey()))) {
					match = false;
					break;
				}
			}
			if (match)
				return e.variant;
		}
		return null;
	}

	private static @Nullable String valueOf(BlockState state, String name) {
		for (Property<?> p : state.getProperties())
			if (p.getName().equals(name))
				return nameOf(state, p);
		return null;
	}

	private static <T extends Comparable<T>> String nameOf(BlockState state, Property<T> p) {
		return p.getName(state.getValue(p));
	}

	private static List<Entry> load(String file) {
		List<Entry> list = new ArrayList<>();
		String path = "/create_polymer/blockstates/" + file + ".json";
		try (InputStream in = BlockstateModels.class.getResourceAsStream(path)) {
			if (in == null) {
				CreatePolymer.LOGGER.error("Missing bundled blockstate file {}", path);
				return list;
			}
			JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (var v : root.getAsJsonObject("variants").entrySet()) {
				Map<String, String> conditions = new HashMap<>();
				if (!v.getKey().isEmpty())
					for (String part : v.getKey().split(",")) {
						String[] kv = part.split("=");
						conditions.put(kv[0], kv[1]);
					}
				JsonElement value = v.getValue();
				JsonObject obj = value.isJsonArray() ? value.getAsJsonArray().get(0).getAsJsonObject() : value.getAsJsonObject();
				list.add(new Entry(conditions, new Variant(Identifier.parse(obj.get("model").getAsString()),
					obj.has("x") ? obj.get("x").getAsInt() : 0, obj.has("y") ? obj.get("y").getAsInt() : 0)));
			}
		} catch (Exception e) {
			CreatePolymer.LOGGER.error("Could not read bundled blockstate file {}", path, e);
		}
		return list;
	}
}
