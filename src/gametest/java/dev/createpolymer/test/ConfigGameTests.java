package dev.createpolymer.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.tree.CommandNode;

import dev.createpolymer.Config;
import dev.createpolymer.api.contraption.ContraptionMovementSetting;
import dev.createpolymer.fluids.FluidConfig;
import dev.createpolymer.fluids.transfer.FluidManipulationBehaviour.BottomlessFluidMode;
import dev.createpolymer.infrastructure.config.ConfigMappings;
import dev.createpolymer.infrastructure.config.ConfigOption;
import dev.createpolymer.infrastructure.config.CreateConfig;
import dev.createpolymer.kinetics.base.BlockStressValues;
import dev.createpolymer.kinetics.belt.item.BeltConnectorItem;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.trains.TrainConfig;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

public class ConfigGameTests {

	/** Runs the body with the config pointed at an empty temporary directory, then loads the real file again. */
	private interface Body {
		void run(Path dir) throws IOException;
	}

	private static void withTempConfig(GameTestHelper helper, Body body) {
		Path real = CreateConfig.file();
		helper.assertTrue(real != null, "The config was not loaded at startup");
		try {
			Path dir = Files.createTempDirectory("create-config-test");
			try {
				body.run(dir);
			} finally {
				try (Stream<Path> files = Files.walk(dir)) {
					files.sorted((a, b) -> b.compareTo(a)).forEach(p -> p.toFile().delete());
				}
				CreateConfig.resetToDefaults();
				CreateConfig.load(real.getParent());
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void write(Path dir, String json) throws IOException {
		Files.writeString(dir.resolve(CreateConfig.FILE_NAME), json, StandardCharsets.UTF_8);
	}

	private static JsonObject read(Path dir) throws IOException {
		return JsonParser.parseString(Files.readString(dir.resolve(CreateConfig.FILE_NAME))).getAsJsonObject();
	}

	private static boolean mentions(List<String> warnings, String text) {
		return warnings.stream().anyMatch(w -> w.contains(text));
	}

	@GameTest
	public void startupWroteTheConfigFile(GameTestHelper helper) {
		Path file = CreateConfig.file();
		helper.assertTrue(file != null && Files.exists(file), "config/create.json was not written at startup: " + file);
		helper.succeed();
	}

	@GameTest
	public void writingDefaultsProducesEveryMappedKey(GameTestHelper helper) {
		withTempConfig(helper, dir -> {
			Path file = dir.resolve(CreateConfig.FILE_NAME);
			CreateConfig.writeDefaults(file);
			JsonObject root = read(dir);
			helper.assertTrue(!ConfigMappings.OPTIONS.isEmpty(), "No mapped options");
			for (ConfigOption<?> option : ConfigMappings.OPTIONS) {
				helper.assertTrue(root.has(option.section) && root.getAsJsonObject(option.section).has(option.key),
					"Missing key " + option.path());
				JsonElement written = root.getAsJsonObject(option.section).get(option.key);
				Object parsed = option.parse(written, new java.util.ArrayList<>());
				helper.assertTrue(option.defaultValue.equals(parsed), option.path() + " is " + written + ", expected " + option.defaultValue);
			}
			for (String section : List.of("kinetics", "logistics", "fluids", "equipment", "trains", "recipes", "stress"))
				helper.assertTrue(root.has(section), "Missing section " + section);
			JsonObject stress = root.getAsJsonObject("stress");
			helper.assertTrue(stress.getAsJsonObject("impact").get("millstone").getAsDouble() == 4, "millstone impact");
			helper.assertTrue(stress.getAsJsonObject("impact").get("mechanical_press").getAsDouble() == 8, "press impact");
			helper.assertTrue(stress.getAsJsonObject("capacity").get("hand_crank").getAsDouble() == 8, "hand crank capacity");
		});
		helper.succeed();
	}

	@GameTest
	public void firstLoadWritesDefaultsAndAppliesThem(GameTestHelper helper) {
		withTempConfig(helper, dir -> {
			CreateConfig.Result result = CreateConfig.load(dir);
			helper.assertTrue(result.loaded() && result.warnings().isEmpty(), "Warnings: " + result.warnings());
			helper.assertTrue(Files.exists(dir.resolve(CreateConfig.FILE_NAME)), "File not written");
			JsonObject root = read(dir);
			for (ConfigOption<?> option : ConfigMappings.OPTIONS) {
				helper.assertTrue(root.getAsJsonObject(option.section).has(option.key), "Missing key " + option.path());
				helper.assertTrue(option.current().equals(option.defaultValue),
					option.path() + " is " + option.current() + ", expected the default " + option.defaultValue);
			}
		});
		helper.succeed();
	}

	@GameTest
	public void loadingAModifiedFileChangesTheStaticFields(GameTestHelper helper) {
		withTempConfig(helper, dir -> {
			write(dir, """
				{
				  "kinetics": { "maxBeltLength": 33, "disableStress": true, "crankHungerMultiplier": 0.5, "movableObsidian": "movable" },
				  "fluids": { "bottomlessFluidMode": "DENY_ALL" },
				  "trains": { "maxBogeyCount": 40 }
				}
				""");
			CreateConfig.Result result = CreateConfig.load(dir);
			helper.assertTrue(result.loaded() && result.warnings().isEmpty(), "Warnings: " + result.warnings());
			helper.assertTrue(BeltConnectorItem.maxBeltLength == 33, "maxBeltLength " + BeltConnectorItem.maxBeltLength);
			helper.assertTrue(Config.disableStress, "disableStress");
			helper.assertTrue(Config.crankHungerMultiplier == 0.5f, "crankHungerMultiplier " + Config.crankHungerMultiplier);
			helper.assertTrue(FluidConfig.bottomlessFluidMode == BottomlessFluidMode.DENY_ALL, "bottomlessFluidMode");
			helper.assertTrue(TrainConfig.maxBogeyCount == 40, "maxBogeyCount " + TrainConfig.maxBogeyCount);
			helper.assertTrue(ContraptionMovementSetting.get(Blocks.OBSIDIAN) == ContraptionMovementSetting.MOVABLE, "obsidian");
			// keys the file did not mention keep their defaults
			helper.assertTrue(Config.maxRotationSpeed == 256, "maxRotationSpeed " + Config.maxRotationSpeed);
			// and were added to the file, next to the admin's values
			JsonObject written = read(dir);
			helper.assertTrue(written.getAsJsonObject("kinetics").get("maxBeltLength").getAsInt() == 33, "admin value lost");
			helper.assertTrue(written.getAsJsonObject("kinetics").has("maxRotationSpeed"), "missing key not added");

			// a reload takes back removed settings
			write(dir, "{}");
			CreateConfig.reload();
			helper.assertTrue(BeltConnectorItem.maxBeltLength == 20, "maxBeltLength after reload " + BeltConnectorItem.maxBeltLength);
			helper.assertTrue(!Config.disableStress, "disableStress after reload");
		});
		helper.assertTrue(BeltConnectorItem.maxBeltLength == 20, "maxBeltLength was not restored");
		helper.succeed();
	}

	@GameTest
	public void outOfRangeValuesAreClamped(GameTestHelper helper) {
		withTempConfig(helper, dir -> {
			write(dir, """
				{
				  "kinetics": { "maxBeltLength": 1, "crankHungerMultiplier": 5, "maxRotationSpeed": 100000, "fanPushDistance": -3 },
				  "logistics": { "brassTunnelTimer": 500, "vaultCapacity": 99999 },
				  "trains": { "maxTrackPlacementLength": 9999, "maxAssemblyLength": 2 },
				  "fluids": { "hosePulleyBlockThreshold": -50 }
				}
				""");
			CreateConfig.Result result = CreateConfig.load(dir);
			helper.assertTrue(BeltConnectorItem.maxBeltLength == 5, "maxBeltLength " + BeltConnectorItem.maxBeltLength);
			helper.assertTrue(Config.crankHungerMultiplier == 1f, "crankHungerMultiplier " + Config.crankHungerMultiplier);
			helper.assertTrue(Config.maxRotationSpeed == 100000, "maxRotationSpeed has no maximum in Create");
			helper.assertTrue(dev.createpolymer.kinetics.fan.FanConfig.fanPushDistance == 5, "fanPushDistance");
			helper.assertTrue(dev.createpolymer.logistics.tunnel.BrassTunnelBlockEntity.BRASS_TUNNEL_TIMER == 10, "brassTunnelTimer");
			helper.assertTrue(dev.createpolymer.logistics.vault.ItemVaultBlockEntity.VAULT_CAPACITY == 2048, "vaultCapacity");
			helper.assertTrue(TrainConfig.maxTrackPlacementLength == 128, "maxTrackPlacementLength " + TrainConfig.maxTrackPlacementLength);
			helper.assertTrue(TrainConfig.maxAssemblyLength == 5, "maxAssemblyLength " + TrainConfig.maxAssemblyLength);
			helper.assertTrue(FluidConfig.hosePulleyBlockThreshold == -1, "hosePulleyBlockThreshold");
			helper.assertTrue(mentions(result.warnings(), "kinetics.maxBeltLength"), "no warning for maxBeltLength: " + result.warnings());
			helper.assertTrue(mentions(result.warnings(), "trains.maxTrackPlacementLength"), "no warning for track length");
			helper.assertTrue(result.warnings().size() == 8, "Expected 8 warnings, got " + result.warnings());
		});
		helper.succeed();
	}

	@GameTest
	public void unusableValuesAndUnknownKeysAreIgnoredWithAWarning(GameTestHelper helper) {
		withTempConfig(helper, dir -> {
			write(dir, """
				{
				  "kinetics": { "maxBeltLength": "long", "maxBlocksMoved": 12.5, "disableStress": 1, "ignoreDeployerAttacks": "SOMETIMES", "notAKey": 3 },
				  "madeUpSection": { "a": 1 },
				  "stress": { "impact": { "not_a_block": 4, "minecraft:stone": 4 }, "other": {} }
				}
				""");
			CreateConfig.Result result = CreateConfig.load(dir);
			List<String> w = result.warnings();
			helper.assertTrue(result.loaded(), "not loaded");
			helper.assertTrue(BeltConnectorItem.maxBeltLength == 20, "maxBeltLength " + BeltConnectorItem.maxBeltLength);
			helper.assertTrue(dev.createpolymer.registry.feature.ContraptionRegistry.maxBlocksMoved == 2048, "maxBlocksMoved");
			helper.assertTrue(!Config.disableStress, "disableStress");
			helper.assertTrue(mentions(w, "kinetics.maxBeltLength: expected a whole number"), "" + w);
			helper.assertTrue(mentions(w, "kinetics.maxBlocksMoved: 12.5 is not a whole number"), "" + w);
			helper.assertTrue(mentions(w, "kinetics.disableStress: expected true or false"), "" + w);
			helper.assertTrue(mentions(w, "kinetics.ignoreDeployerAttacks: expected one of ALL, CREEPERS, NONE"), "" + w);
			helper.assertTrue(mentions(w, "kinetics.notAKey: unknown key"), "" + w);
			helper.assertTrue(mentions(w, "madeUpSection: unknown section"), "" + w);
			helper.assertTrue(mentions(w, "stress.impact.not_a_block"), "" + w);
			helper.assertTrue(mentions(w, "stress.impact.minecraft:stone"), "" + w);
			helper.assertTrue(mentions(w, "stress.other: unknown key"), "" + w);
		});
		helper.succeed();
	}

	@GameTest
	public void aBrokenFileLeavesTheSettingsAlone(GameTestHelper helper) {
		withTempConfig(helper, dir -> {
			write(dir, "{ \"kinetics\": { \"maxBeltLength\": 30 } }");
			CreateConfig.load(dir);
			helper.assertTrue(BeltConnectorItem.maxBeltLength == 30, "maxBeltLength " + BeltConnectorItem.maxBeltLength);
			write(dir, "{ \"kinetics\": { \"maxBeltLength\": ");
			CreateConfig.Result result = CreateConfig.reload();
			helper.assertTrue(!result.loaded(), "A broken file was reported as loaded");
			helper.assertTrue(BeltConnectorItem.maxBeltLength == 30, "maxBeltLength changed to " + BeltConnectorItem.maxBeltLength);
		});
		helper.succeed();
	}

	@GameTest
	public void stressOverridesChangeBlockStressValues(GameTestHelper helper) {
		withTempConfig(helper, dir -> {
			helper.assertTrue(BlockStressValues.getImpact(ModBlocks.MILLSTONE) == 4, "default millstone impact");
			write(dir, """
				{
				  "stress": {
				    "impact": { "millstone": 12.5, "create:mechanical_mixer": 0, "mechanical_press": -3 },
				    "capacity": { "hand_crank": 64, "shaft": 7 }
				  }
				}
				""");
			CreateConfig.Result result = CreateConfig.load(dir);
			helper.assertTrue(BlockStressValues.getImpact(ModBlocks.MILLSTONE) == 12.5, "millstone impact " + BlockStressValues.getImpact(ModBlocks.MILLSTONE));
			helper.assertTrue(BlockStressValues.getImpact(ModBlocks.MECHANICAL_MIXER) == 0, "mixer impact");
			helper.assertTrue(BlockStressValues.getImpact(ModBlocks.MECHANICAL_PRESS) == 0, "negative impact is clamped to 0");
			helper.assertTrue(mentions(result.warnings(), "stress.impact.mechanical_press"), "" + result.warnings());
			helper.assertTrue(BlockStressValues.getCapacity(ModBlocks.HAND_CRANK) == 64, "hand crank capacity");
			helper.assertTrue(BlockStressValues.getCapacity(ModBlocks.SHAFT) == 7, "a block without a default can be given a value");
			// untouched blocks keep their registered values
			helper.assertTrue(BlockStressValues.getCapacity(ModBlocks.WATER_WHEEL) == 32, "water wheel capacity");
			helper.assertTrue(BlockStressValues.getGeneratorSpeed(ModBlocks.HAND_CRANK) == 32, "generator speed");

			// removing the overrides restores the registered values
			write(dir, "{}");
			CreateConfig.reload();
			helper.assertTrue(BlockStressValues.getImpact(ModBlocks.MILLSTONE) == 4, "millstone impact after reload");
			helper.assertTrue(BlockStressValues.getImpact(ModBlocks.MECHANICAL_MIXER) == 4, "mixer impact after reload");
			helper.assertTrue(BlockStressValues.getCapacity(ModBlocks.HAND_CRANK) == 8, "hand crank capacity after reload");
			helper.assertTrue(BlockStressValues.getCapacity(ModBlocks.SHAFT) == 0, "shaft capacity after reload");
		});
		helper.assertTrue(BlockStressValues.getImpact(ModBlocks.MILLSTONE) == 4, "millstone impact was not restored");
		helper.succeed();
	}

	@GameTest
	public void reloadCommandIsRegisteredForOperators(GameTestHelper helper) {
		CommandNode<CommandSourceStack> create = helper.getLevel().getServer().getCommands().getDispatcher().getRoot().getChild("create");
		helper.assertTrue(create != null, "/create is missing");
		CommandNode<CommandSourceStack> config = create.getChild("config");
		helper.assertTrue(config != null, "/create config is missing");
		helper.assertTrue(config.getChild("reload") != null, "/create config reload is missing");
		helper.assertTrue(config.getRequirement() != null, "no permission requirement");
		helper.succeed();
	}
}
