package dev.createpolymer.infrastructure.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.jetbrains.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.api.contraption.ContraptionMovementSetting;
import dev.createpolymer.kinetics.base.BlockStressValues;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The server config, config/create.json. It holds Create's server settings under the names Create gives them
 * (sections kinetics, logistics, fluids, equipment, trains and recipes), plus a stress section that overrides the
 * stress impact and capacity of individual blocks.
 *
 * The values live in the static fields the features already read; {@link ConfigMappings} lists which key sets which
 * field. Loading writes the file with all defaults when it does not exist, warns about and ignores unknown keys, and
 * clamps values to Create's ranges.
 */
public final class CreateConfig {
	public static final String FILE_NAME = "create.json";
	public static final String STRESS = "stress";
	public static final String IMPACT = "impact";
	public static final String CAPACITY = "capacity";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** How Create's special blocks behave on contraptions (kinetics.movableSpawners and the like). */
	public static ContraptionMovementSetting spawnerMovement = ContraptionMovementSetting.NO_PICKUP;
	public static ContraptionMovementSetting amethystMovement = ContraptionMovementSetting.NO_PICKUP;
	public static ContraptionMovementSetting obsidianMovement = ContraptionMovementSetting.UNMOVABLE;
	public static ContraptionMovementSetting reinforcedDeepslateMovement = ContraptionMovementSetting.UNMOVABLE;

	@Nullable
	private static Map<Block, Double> defaultImpacts;
	@Nullable
	private static Map<Block, Double> defaultCapacities;
	@Nullable
	private static Path file;
	private static boolean movementSettingsRegistered;

	private CreateConfig() {}

	/** The outcome of a load: the warnings, and whether the file could be read at all. */
	public record Result(boolean loaded, List<String> warnings) {}

	/**
	 * Loads config/create.json from the given config directory. Call after all blocks are registered, so the default
	 * stress values can be listed.
	 */
	public static Result load(Path configDir) {
		file = configDir.resolve(FILE_NAME);
		return reload();
	}

	/** The file the settings were loaded from, null before the first load. */
	@Nullable
	public static Path file() {
		return file;
	}

	/** Reads the file loaded last again. Settings missing from the file go back to their defaults. */
	public static Result reload() {
		List<String> warnings = new ArrayList<>();
		if (file == null)
			throw new IllegalStateException("The config has not been loaded yet");
		snapshotStressDefaults();
		registerMovementSettings();

		JsonObject root;
		boolean write = false;
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				JsonElement parsed = JsonParser.parseReader(reader);
				if (!parsed.isJsonObject())
					throw new IllegalStateException("the top level must be an object");
				root = parsed.getAsJsonObject();
			} catch (Exception e) {
				String message = "Could not read " + file + " (" + e.getMessage() + "), keeping the current settings";
				CreatePolymer.LOGGER.error(message);
				warnings.add(message);
				return new Result(false, warnings);
			}
		} else {
			root = defaults();
			write = true;
			CreatePolymer.LOGGER.info("Writing the default config to {}", file);
		}

		write |= applyOptions(root, warnings);
		write |= applyStress(root, warnings);
		checkUnknown(root, warnings);

		if (write)
			write(file, root, warnings);
		for (String warning : warnings)
			CreatePolymer.LOGGER.warn("{}: {}", FILE_NAME, warning);
		return new Result(true, warnings);
	}

	/** A config tree holding every mapped key and every registered stress value, all at their defaults. */
	public static JsonObject defaults() {
		snapshotStressDefaults();
		JsonObject root = new JsonObject();
		for (ConfigOption<?> option : ConfigMappings.OPTIONS)
			section(root, option.section).add(option.key, option.defaultJson());

		JsonObject stress = new JsonObject();
		stress.add(IMPACT, stressJson(defaultImpacts));
		stress.add(CAPACITY, stressJson(defaultCapacities));
		root.add(STRESS, stress);
		return root;
	}

	/** Writes the defaults to the given file, replacing it. */
	public static void writeDefaults(Path target) throws IOException {
		List<String> warnings = new ArrayList<>();
		write(target, defaults(), warnings);
		if (!warnings.isEmpty())
			throw new IOException(warnings.get(0));
	}

	/** Puts every setting and every stress value back to its default. */
	public static void resetToDefaults() {
		snapshotStressDefaults();
		for (ConfigOption<?> option : ConfigMappings.OPTIONS)
			option.reset();
		BlockStressValues.replaceAll(defaultImpacts, defaultCapacities);
	}

	/** Sets the fields from the file. Returns true when keys were missing and have been added to the tree. */
	private static boolean applyOptions(JsonObject root, List<String> warnings) {
		boolean added = false;
		for (ConfigOption<?> option : ConfigMappings.OPTIONS) {
			JsonElement sectionElement = root.get(option.section);
			JsonObject section;
			if (sectionElement == null || !sectionElement.isJsonObject()) {
				if (sectionElement != null)
					warnings.add(option.section + ": expected an object, using the defaults");
				section = null;
			} else {
				section = sectionElement.getAsJsonObject();
			}

			JsonElement value = section == null ? null : section.get(option.key);
			if (value == null) {
				option.reset();
				if (section == null) {
					section = new JsonObject();
					root.add(option.section, section);
				}
				section.add(option.key, option.defaultJson());
				added = true;
			} else {
				option.apply(value, warnings);
			}
		}
		return added;
	}

	/**
	 * Restores the stress values registered by the blocks, then applies the overrides of the file. Stress entries of
	 * blocks that have none yet are added to the tree.
	 */
	private static boolean applyStress(JsonObject root, List<String> warnings) {
		Map<Block, Double> impacts = new IdentityHashMap<>(defaultImpacts);
		Map<Block, Double> capacities = new IdentityHashMap<>(defaultCapacities);
		boolean added = false;

		JsonObject stress = object(root, STRESS, STRESS, warnings);
		if (stress == null) {
			stress = new JsonObject();
			root.add(STRESS, stress);
			added = true;
		}
		added |= applyStressMap(stress, IMPACT, impacts, defaultImpacts, warnings);
		added |= applyStressMap(stress, CAPACITY, capacities, defaultCapacities, warnings);

		BlockStressValues.replaceAll(impacts, capacities);
		return added;
	}

	private static boolean applyStressMap(JsonObject stress, String name, Map<Block, Double> target,
		Map<Block, Double> defaults, List<String> warnings) {
		boolean added = false;
		JsonObject map = object(stress, name, STRESS + "." + name, warnings);
		if (map == null) {
			map = new JsonObject();
			stress.add(name, map);
			added = true;
		}

		for (Map.Entry<String, JsonElement> entry : new ArrayList<>(map.entrySet())) {
			String path = STRESS + "." + name + "." + entry.getKey();
			Block block = findBlock(entry.getKey());
			if (block == null) {
				warnings.add(path + ": no such Create block, ignored");
				continue;
			}
			JsonElement element = entry.getValue();
			if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
				warnings.add(path + ": expected a number, using the default");
				continue;
			}
			double value = element.getAsDouble();
			if (value < 0) {
				warnings.add(path + ": " + value + " is below the minimum 0, using 0");
				value = 0;
			}
			target.put(block, value);
		}

		// blocks registered since the file was written get their default entry
		for (Map.Entry<Identifier, Double> entry : sorted(defaults).entrySet()) {
			String key = entry.getKey().getPath();
			if (!map.has(key)) {
				map.add(key, new JsonPrimitive(entry.getValue()));
				added = true;
			}
		}
		return added;
	}

	private static void checkUnknown(JsonObject root, List<String> warnings) {
		for (Map.Entry<String, JsonElement> section : root.entrySet()) {
			if (section.getKey().equals(STRESS)) {
				if (section.getValue().isJsonObject())
					for (String key : section.getValue().getAsJsonObject().keySet())
						if (!key.equals(IMPACT) && !key.equals(CAPACITY))
							warnings.add(STRESS + "." + key + ": unknown key, ignored");
				continue;
			}
			boolean known = ConfigMappings.OPTIONS.stream().anyMatch(o -> o.section.equals(section.getKey()));
			if (!known) {
				warnings.add(section.getKey() + ": unknown section, ignored");
			} else if (section.getValue().isJsonObject()) {
				for (String key : section.getValue().getAsJsonObject().keySet())
					if (ConfigMappings.OPTIONS.stream().noneMatch(o -> o.section.equals(section.getKey()) && o.key.equals(key)))
						warnings.add(section.getKey() + "." + key + ": unknown key, ignored");
			}
		}
	}

	@Nullable
	private static JsonObject object(JsonObject parent, String key, String path, List<String> warnings) {
		JsonElement element = parent.get(key);
		if (element == null)
			return null;
		if (element.isJsonObject())
			return element.getAsJsonObject();
		warnings.add(path + ": expected an object, ignored");
		return null;
	}

	/** Only Create's own blocks can be given stress values, like in Create. Accepts "millstone" and "create:millstone". */
	@Nullable
	private static Block findBlock(String key) {
		Identifier id = Identifier.tryParse(key.contains(":") ? key : CreatePolymer.MOD_ID + ":" + key);
		if (id == null || !id.getNamespace().equals(CreatePolymer.MOD_ID))
			return null;
		Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);
		return block.orElse(null);
	}

	private static JsonObject stressJson(Map<Block, Double> values) {
		JsonObject json = new JsonObject();
		for (Map.Entry<Identifier, Double> entry : sorted(values).entrySet())
			json.add(entry.getKey().getPath(), new JsonPrimitive(entry.getValue()));
		return json;
	}

	private static Map<Identifier, Double> sorted(Map<Block, Double> values) {
		Map<Identifier, Double> sorted = new TreeMap<>();
		for (Map.Entry<Block, Double> entry : values.entrySet()) {
			Identifier id = BuiltInRegistries.BLOCK.getKey(entry.getKey());
			if (id != null && id.getNamespace().equals(CreatePolymer.MOD_ID))
				sorted.put(id, entry.getValue());
		}
		return sorted;
	}

	private static JsonObject section(JsonObject root, String name) {
		JsonElement existing = root.get(name);
		if (existing != null)
			return existing.getAsJsonObject();
		JsonObject section = new JsonObject();
		root.add(name, section);
		return section;
	}

	private static void snapshotStressDefaults() {
		if (defaultImpacts == null) {
			defaultImpacts = BlockStressValues.impacts();
			defaultCapacities = BlockStressValues.capacities();
		}
	}

	private static void registerMovementSettings() {
		if (movementSettingsRegistered)
			return;
		movementSettingsRegistered = true;
		ContraptionMovementSetting.REGISTRY.register(Blocks.SPAWNER, () -> spawnerMovement);
		ContraptionMovementSetting.REGISTRY.register(Blocks.BUDDING_AMETHYST, () -> amethystMovement);
		ContraptionMovementSetting.REGISTRY.register(Blocks.OBSIDIAN, () -> obsidianMovement);
		ContraptionMovementSetting.REGISTRY.register(Blocks.CRYING_OBSIDIAN, () -> obsidianMovement);
		ContraptionMovementSetting.REGISTRY.register(Blocks.RESPAWN_ANCHOR, () -> obsidianMovement);
		ContraptionMovementSetting.REGISTRY.register(Blocks.REINFORCED_DEEPSLATE, () -> reinforcedDeepslateMovement);
	}

	private static void write(Path target, JsonObject root, List<String> warnings) {
		try {
			if (target.getParent() != null)
				Files.createDirectories(target.getParent());
			Path temp = target.resolveSibling(target.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
				writer.write('\n');
			}
			Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			warnings.add("Could not write " + target + ": " + e.getMessage());
		}
	}
}
