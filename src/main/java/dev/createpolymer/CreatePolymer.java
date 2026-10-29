package dev.createpolymer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.createpolymer.item.GogglesInfo;
import dev.createpolymer.kinetics.TorquePropagator;
import dev.createpolymer.kinetics.simple.ShaftBlock;
import dev.createpolymer.processing.basin.RecipeCache;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.ModRecipeTypes;
import dev.createpolymer.registry.feature.VaultRegistry;
import dev.createpolymer.registry.feature.ChuteRegistry;
import dev.createpolymer.registry.feature.CrushingRegistry;
import dev.createpolymer.registry.feature.DeployerRegistry;
import dev.createpolymer.registry.feature.BeltRegistry;
import dev.createpolymer.registry.feature.CrafterRegistry;
import dev.createpolymer.registry.feature.FilterRegistry;
import dev.createpolymer.registry.feature.BurnerRegistry;
import dev.createpolymer.registry.feature.MaterialsRegistry;
import dev.createpolymer.registry.feature.KineticsExtraRegistry;
import dev.createpolymer.registry.feature.FanRegistry;
import dev.createpolymer.registry.feature.SawRegistry;
import dev.createpolymer.registry.feature.EjectorRegistry;
import dev.createpolymer.registry.feature.MiscRegistry;
import dev.createpolymer.registry.feature.PipesRegistry;
import dev.createpolymer.registry.feature.TanksRegistry;
import dev.createpolymer.registry.feature.DecorationRegistry;
import dev.createpolymer.registry.feature.RedstoneRegistry;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.extras.api.ResourcePackExtras;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.resources.Identifier;

public class CreatePolymer implements ModInitializer {
	public static final String MOD_ID = "create";
	public static final Logger LOGGER = LoggerFactory.getLogger("Create (Polymer)");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ShaftBlock.requestClientStates();
		ModBlocks.init();
		ModBlockEntities.init();
		ModItems.init();
		ModRecipeTypes.init();
		VaultRegistry.init();
		FilterRegistry.init();
		BurnerRegistry.init();
		MaterialsRegistry.init();
		ChuteRegistry.init();
		CrushingRegistry.init();
		KineticsExtraRegistry.init();
		FanRegistry.init();
		SawRegistry.init();
		DeployerRegistry.init();
		BeltRegistry.init();
		CrafterRegistry.init();
		EjectorRegistry.init();
		PipesRegistry.init();
		TanksRegistry.init();
		DecorationRegistry.init();
		RedstoneRegistry.init();
		MiscRegistry.init();

		// Resource pack: Create's models/textures/lang from this jar, served by polymer-autohost.
		enableAutoHostByDefault();
		PolymerResourcePackUtils.addModAssets(MOD_ID);
		PolymerResourcePackUtils.markAsRequired();
		ResourcePackExtras.forDefault().addBridgedModelsFolder(id("block"));

		ServerLevelEvents.LOAD.register((server, level) -> TorquePropagator.onLoadWorld(level));
		ServerLevelEvents.UNLOAD.register((server, level) -> TorquePropagator.onUnloadWorld(level));
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> RecipeCache.invalidate());
		ServerLifecycleEvents.SERVER_STARTED.register(server -> RecipeCache.invalidate());
		ServerTickEvents.END_SERVER_TICK.register(GogglesInfo::tick);

		LOGGER.info("Create (Polymer) loaded");
	}

	/**
	 * Players need the resource pack to see anything, and Polymer's auto-hosting is
	 * off by default outside development. On a dedicated server with no auto-host
	 * config yet, write one with hosting enabled. Admins can still turn it off.
	 */
	private static void enableAutoHostByDefault() {
		if (FabricLoader.getInstance().getEnvironmentType() != EnvType.SERVER)
			return;
		Path file = FabricLoader.getInstance().getConfigDir().resolve("polymer").resolve("auto-host.json");
		if (Files.exists(file))
			return;
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, "{\n  \"enabled\": true\n}\n");
			LOGGER.info("Enabled Polymer resource pack auto-hosting ({}) so players receive Create's textures", file);
		} catch (IOException e) {
			LOGGER.warn("Could not write {}; enable Polymer auto-hosting there so players get the resource pack", file, e);
		}
	}
}
