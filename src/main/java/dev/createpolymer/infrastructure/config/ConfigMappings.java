package dev.createpolymer.infrastructure.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import dev.createpolymer.Config;
import dev.createpolymer.api.contraption.ContraptionMovementSetting;
import dev.createpolymer.decoration.DecorationPlacement;
import dev.createpolymer.equipment.EquipmentConfig;
import dev.createpolymer.fluids.FluidConfig;
import dev.createpolymer.fluids.tank.FluidTankBlockEntity;
import dev.createpolymer.fluids.transfer.FluidManipulationBehaviour.BottomlessFluidMode;
import dev.createpolymer.infrastructure.config.ConfigOption.BoolOption;
import dev.createpolymer.infrastructure.config.ConfigOption.EnumOption;
import dev.createpolymer.infrastructure.config.ConfigOption.FloatOption;
import dev.createpolymer.infrastructure.config.ConfigOption.IntOption;
import dev.createpolymer.kinetics.belt.item.BeltConnectorItem;
import dev.createpolymer.kinetics.crafter.RecipeGridHandler;
import dev.createpolymer.kinetics.deployer.DeployerFakePlayer;
import dev.createpolymer.kinetics.deployer.DeployerFakePlayer.DeployerAggroSetting;
import dev.createpolymer.kinetics.fan.FanConfig;
import dev.createpolymer.logistics.funnel.FunnelBlockEntity;
import dev.createpolymer.logistics.tunnel.BrassTunnelBlockEntity;
import dev.createpolymer.logistics.vault.ItemVaultBlockEntity;
import dev.createpolymer.redstone.displayLink.DisplayLinkBlockItem;
import dev.createpolymer.redstone.link.RedstoneLinkNetworkHandler;
import dev.createpolymer.registry.feature.ActorsRegistry;
import dev.createpolymer.registry.feature.ArmRegistry;
import dev.createpolymer.registry.feature.BearingRegistry;
import dev.createpolymer.registry.feature.ChainConveyorRegistry;
import dev.createpolymer.registry.feature.ContraptionRegistry;
import dev.createpolymer.registry.feature.CrushingRegistry;
import dev.createpolymer.registry.feature.EjectorRegistry;
import dev.createpolymer.registry.feature.FactoryGaugeRegistry;
import dev.createpolymer.registry.feature.MinecartRegistry;
import dev.createpolymer.registry.feature.PackagesRegistry;
import dev.createpolymer.registry.feature.SawRegistry;
import dev.createpolymer.trains.TrainConfig;

/**
 * Every setting of create.json, in file order. Sections and keys are Create's (CKinetics, CLogistics, CFluids,
 * CEquipment, CTrains, CRecipes), with Create's defaults and ranges. Each entry reads and writes the static field the
 * feature code uses, so that code does not need to know about the file.
 *
 * To expose another setting, add one line here.
 */
public final class ConfigMappings {
	public static final String KINETICS = "kinetics";
	public static final String LOGISTICS = "logistics";
	public static final String FLUIDS = "fluids";
	public static final String EQUIPMENT = "equipment";
	public static final String TRAINS = "trains";
	public static final String RECIPES = "recipes";

	public static final List<ConfigOption<?>> OPTIONS;

	private static final List<ConfigOption<?>> list = new ArrayList<>();

	private ConfigMappings() {}

	static {
		// kinetics
		b(KINETICS, "disableStress", false, () -> Config.disableStress, v -> Config.disableStress = v);
		i(KINETICS, "maxBeltLength", 20, 5, () -> BeltConnectorItem.maxBeltLength, v -> BeltConnectorItem.maxBeltLength = v);
		i(KINETICS, "maxChainConveyorLength", 32, 5, () -> ChainConveyorRegistry.maxChainConveyorLength,
			v -> ChainConveyorRegistry.maxChainConveyorLength = v);
		i(KINETICS, "maxChainConveyorConnections", 4, 1, () -> ChainConveyorRegistry.maxChainConveyorConnections,
			v -> ChainConveyorRegistry.maxChainConveyorConnections = v);
		i(KINETICS, "crushingDamage", 4, 0, () -> CrushingRegistry.crushingDamage, v -> CrushingRegistry.crushingDamage = v);
		i(KINETICS, "maxRotationSpeed", 256, 64, () -> Config.maxRotationSpeed, v -> Config.maxRotationSpeed = v);
		e(KINETICS, "ignoreDeployerAttacks", DeployerAggroSetting.CREEPERS, DeployerAggroSetting.class,
			() -> DeployerFakePlayer.ignoreDeployerAttacks, v -> DeployerFakePlayer.ignoreDeployerAttacks = v);
		i(KINETICS, "kineticValidationFrequency", 60, 5, () -> Config.kineticValidationFrequency,
			v -> Config.kineticValidationFrequency = v);
		f(KINETICS, "crankHungerMultiplier", .01f, 0, 1, () -> Config.crankHungerMultiplier,
			v -> Config.crankHungerMultiplier = v);
		i(KINETICS, "minimumWindmillSails", 8, 0, () -> ContraptionRegistry.minimumWindmillSails,
			v -> ContraptionRegistry.minimumWindmillSails = v);
		i(KINETICS, "windmillSailsPerRPM", 8, 1, () -> BearingRegistry.windmillSailsPerRPM,
			v -> BearingRegistry.windmillSailsPerRPM = v);
		i(KINETICS, "maxEjectorDistance", 32, 0, () -> EjectorRegistry.maxEjectorDistance,
			v -> EjectorRegistry.maxEjectorDistance = v);
		i(KINETICS, "ejectorScanInterval", 120, 10, () -> EjectorRegistry.ejectorScanInterval,
			v -> EjectorRegistry.ejectorScanInterval = v);

		// kinetics: encased fan. fanParticleDensity is a client option in Create; here the server sends the particles.
		i(KINETICS, "fanPushDistance", 20, 5, () -> FanConfig.fanPushDistance, v -> FanConfig.fanPushDistance = v);
		i(KINETICS, "fanPullDistance", 20, 5, () -> FanConfig.fanPullDistance, v -> FanConfig.fanPullDistance = v);
		i(KINETICS, "fanBlockCheckRate", 30, 10, () -> FanConfig.fanBlockCheckRate, v -> FanConfig.fanBlockCheckRate = v);
		i(KINETICS, "fanRotationArgmax", 256, 64, () -> FanConfig.fanRotationArgmax, v -> FanConfig.fanRotationArgmax = v);
		i(KINETICS, "fanProcessingTime", 150, 0, () -> FanConfig.fanProcessingTime, v -> FanConfig.fanProcessingTime = v);
		f(KINETICS, "fanParticleDensity", .25f, 0, 1, () -> FanConfig.fanParticleDensity,
			v -> FanConfig.fanParticleDensity = v);

		// kinetics: moving contraptions
		i(KINETICS, "maxBlocksMoved", 2048, 1, () -> ContraptionRegistry.maxBlocksMoved,
			v -> ContraptionRegistry.maxBlocksMoved = v);
		i(KINETICS, "maxChassisRange", 16, 1, () -> ContraptionRegistry.maxChassisRange,
			v -> ContraptionRegistry.maxChassisRange = v);
		i(KINETICS, "maxPistonPoles", 64, 1, () -> ContraptionRegistry.maxPistonPoles,
			v -> ContraptionRegistry.maxPistonPoles = v);
		i(KINETICS, "maxRopeLength", 384, 1, () -> ContraptionRegistry.maxRopeLength,
			v -> ContraptionRegistry.maxRopeLength = v);
		i(KINETICS, "maxCartCouplingLength", 32, 1, () -> MinecartRegistry.maxCartCouplingLength,
			v -> MinecartRegistry.maxCartCouplingLength = v);
		i(KINETICS, "rollerFillDepth", 12, 1, () -> ActorsRegistry.rollerFillDepth, v -> ActorsRegistry.rollerFillDepth = v);
		b(KINETICS, "survivalContraptionPickup", true, () -> MinecartRegistry.survivalContraptionPickup,
			v -> MinecartRegistry.survivalContraptionPickup = v);
		e(KINETICS, "movableSpawners", ContraptionMovementSetting.NO_PICKUP, ContraptionMovementSetting.class,
			() -> CreateConfig.spawnerMovement, v -> CreateConfig.spawnerMovement = v);
		e(KINETICS, "amethystMovement", ContraptionMovementSetting.NO_PICKUP, ContraptionMovementSetting.class,
			() -> CreateConfig.amethystMovement, v -> CreateConfig.amethystMovement = v);
		e(KINETICS, "movableObsidian", ContraptionMovementSetting.UNMOVABLE, ContraptionMovementSetting.class,
			() -> CreateConfig.obsidianMovement, v -> CreateConfig.obsidianMovement = v);
		e(KINETICS, "movableReinforcedDeepslate", ContraptionMovementSetting.UNMOVABLE, ContraptionMovementSetting.class,
			() -> CreateConfig.reinforcedDeepslateMovement, v -> CreateConfig.reinforcedDeepslateMovement = v);
		b(KINETICS, "moveItemsToStorage", true, () -> ContraptionRegistry.moveItemsToStorage,
			v -> ContraptionRegistry.moveItemsToStorage = v);
		b(KINETICS, "harvestPartiallyGrown", false, () -> ActorsRegistry.harvestPartiallyGrown,
			v -> ActorsRegistry.harvestPartiallyGrown = v);
		b(KINETICS, "harvesterReplants", true, () -> ActorsRegistry.harvesterReplants,
			v -> ActorsRegistry.harvesterReplants = v);
		b(KINETICS, "minecartContraptionInContainers", false, () -> MinecartRegistry.minecartContraptionInContainers,
			v -> MinecartRegistry.minecartContraptionInContainers = v);
		b(KINETICS, "stabiliseStableContraptions", false, () -> ContraptionRegistry.stabiliseStableContraptions,
			v -> ContraptionRegistry.stabiliseStableContraptions = v);
		b(KINETICS, "noDropWhenContraptionReplaceBlocks", false, () -> ContraptionRegistry.noDropWhenContraptionReplaceBlocks,
			v -> ContraptionRegistry.noDropWhenContraptionReplaceBlocks = v);

		// kinetics: stats
		f(KINETICS, "mediumSpeed", 30, 0, 4096, () -> Config.mediumSpeed, v -> Config.mediumSpeed = v);
		f(KINETICS, "fastSpeed", 100, 0, 65535, () -> Config.fastSpeed, v -> Config.fastSpeed = v);

		// logistics
		i(LOGISTICS, "defaultExtractionTimer", 8, 1, () -> FunnelBlockEntity.DEFAULT_EXTRACTION_TIMER,
			v -> FunnelBlockEntity.DEFAULT_EXTRACTION_TIMER = v);
		i(LOGISTICS, "psiTimeout", 60, 1, () -> ActorsRegistry.psiTimeout, v -> ActorsRegistry.psiTimeout = v);
		i(LOGISTICS, "mechanicalArmRange", 5, 1, () -> ArmRegistry.mechanicalArmRange, v -> ArmRegistry.mechanicalArmRange = v);
		i(LOGISTICS, "packagePortRange", 5, 1, () -> PackagesRegistry.packagePortRange,
			v -> PackagesRegistry.packagePortRange = v);
		i(LOGISTICS, "linkRange", 256, 1, () -> RedstoneLinkNetworkHandler.linkRange,
			v -> RedstoneLinkNetworkHandler.linkRange = v);
		i(LOGISTICS, "displayLinkRange", 64, 1, () -> DisplayLinkBlockItem.displayLinkRange,
			v -> DisplayLinkBlockItem.displayLinkRange = v);
		i(LOGISTICS, "vaultCapacity", 20, 1, 2048, () -> ItemVaultBlockEntity.VAULT_CAPACITY,
			v -> ItemVaultBlockEntity.VAULT_CAPACITY = v);
		i(LOGISTICS, "chainConveyorCapacity", 20, 1, () -> ChainConveyorRegistry.chainConveyorCapacity,
			v -> ChainConveyorRegistry.chainConveyorCapacity = v);
		i(LOGISTICS, "brassTunnelTimer", 10, 1, 10, () -> BrassTunnelBlockEntity.BRASS_TUNNEL_TIMER,
			v -> BrassTunnelBlockEntity.BRASS_TUNNEL_TIMER = v);
		i(LOGISTICS, "factoryGaugeTimer", 100, 5, () -> FactoryGaugeRegistry.factoryGaugeTimer,
			v -> FactoryGaugeRegistry.factoryGaugeTimer = v);
		b(LOGISTICS, "seatHostileMobs", true, () -> ActorsRegistry.seatHostileMobs, v -> ActorsRegistry.seatHostileMobs = v);

		// fluids
		i(FLUIDS, "fluidTankCapacity", 8, 1, () -> FluidTankBlockEntity.FLUID_TANK_CAPACITY,
			v -> FluidTankBlockEntity.FLUID_TANK_CAPACITY = v);
		i(FLUIDS, "fluidTankMaxHeight", 32, 1, () -> FluidTankBlockEntity.FLUID_TANK_MAX_HEIGHT,
			v -> FluidTankBlockEntity.FLUID_TANK_MAX_HEIGHT = v);
		i(FLUIDS, "mechanicalPumpRange", 16, 1, () -> FluidConfig.mechanicalPumpRange, v -> FluidConfig.mechanicalPumpRange = v);
		i(FLUIDS, "hosePulleyRange", 128, 1, () -> FluidConfig.hosePulleyRange, v -> FluidConfig.hosePulleyRange = v);
		i(FLUIDS, "hosePulleyBlockThreshold", 10000, -1, () -> FluidConfig.hosePulleyBlockThreshold,
			v -> FluidConfig.hosePulleyBlockThreshold = v);
		b(FLUIDS, "fillInfinite", false, () -> FluidConfig.fillInfinite, v -> FluidConfig.fillInfinite = v);
		e(FLUIDS, "bottomlessFluidMode", BottomlessFluidMode.ALLOW_BY_TAG, BottomlessFluidMode.class,
			() -> FluidConfig.bottomlessFluidMode, v -> FluidConfig.bottomlessFluidMode = v);
		b(FLUIDS, "fluidFillPlaceFluidSourceBlocks", true, () -> FluidConfig.fluidFillPlaceFluidSourceBlocks,
			v -> FluidConfig.fluidFillPlaceFluidSourceBlocks = v);
		b(FLUIDS, "pipesPlaceFluidSourceBlocks", true, () -> FluidConfig.pipesPlaceFluidSourceBlocks,
			v -> FluidConfig.pipesPlaceFluidSourceBlocks = v);

		// equipment
		i(EQUIPMENT, "placementAssistRange", 12, 3, () -> DecorationPlacement.PLACEMENT_ASSIST_RANGE,
			v -> DecorationPlacement.PLACEMENT_ASSIST_RANGE = v);
		i(EQUIPMENT, "toolboxRange", 10, 1, () -> EquipmentConfig.toolboxRange, v -> EquipmentConfig.toolboxRange = v);
		i(EQUIPMENT, "airInBacktank", 900, 1, () -> EquipmentConfig.airInBacktank, v -> EquipmentConfig.airInBacktank = v);
		i(EQUIPMENT, "enchantedBacktankCapacity", 300, 1, () -> EquipmentConfig.enchantedBacktankCapacity,
			v -> EquipmentConfig.enchantedBacktankCapacity = v);
		i(EQUIPMENT, "maxExtendoGripActions", 1000, 0, () -> EquipmentConfig.maxExtendoGripActions,
			v -> EquipmentConfig.maxExtendoGripActions = v);
		i(EQUIPMENT, "maxPotatoCannonShots", 200, 0, () -> EquipmentConfig.maxPotatoCannonShots,
			v -> EquipmentConfig.maxPotatoCannonShots = v);

		// trains
		b(TRAINS, "trainsCauseDamage", true, () -> TrainConfig.trainsCauseDamage, v -> TrainConfig.trainsCauseDamage = v);
		i(TRAINS, "maxTrackPlacementLength", 32, 16, 128, () -> TrainConfig.maxTrackPlacementLength,
			v -> TrainConfig.maxTrackPlacementLength = v);
		i(TRAINS, "maxAssemblyLength", 128, 5, 512, () -> TrainConfig.maxAssemblyLength,
			v -> TrainConfig.maxAssemblyLength = v);
		i(TRAINS, "maxBogeyCount", 20, 1, 200, () -> TrainConfig.maxBogeyCount, v -> TrainConfig.maxBogeyCount = v);

		// recipes
		b(RECIPES, "bulkPressing", false, () -> Config.bulkPressing, v -> Config.bulkPressing = v);
		b(RECIPES, "bulkCutting", false, () -> SawRegistry.bulkCutting, v -> SawRegistry.bulkCutting = v);
		b(RECIPES, "allowShapelessInMixer", true, () -> Config.allowShapelessInMixer, v -> Config.allowShapelessInMixer = v);
		b(RECIPES, "allowShapedSquareInPress", true, () -> Config.allowShapedSquareInPress,
			v -> Config.allowShapedSquareInPress = v);
		b(RECIPES, "allowRegularCraftingInCrafter", true, () -> RecipeGridHandler.allowRegularCraftingInCrafter,
			v -> RecipeGridHandler.allowRegularCraftingInCrafter = v);
		i(RECIPES, "maxFireworkIngredientsInCrafter", 9, 1, () -> RecipeGridHandler.maxFireworkIngredientsInCrafter,
			v -> RecipeGridHandler.maxFireworkIngredientsInCrafter = v);
		b(RECIPES, "allowStonecuttingOnSaw", true, () -> SawRegistry.allowStonecuttingOnSaw,
			v -> SawRegistry.allowStonecuttingOnSaw = v);

		OPTIONS = Collections.unmodifiableList(list);
	}

	private static void i(String section, String key, int def, int min, Supplier<Integer> get, Consumer<Integer> set) {
		i(section, key, def, min, Integer.MAX_VALUE, get, set);
	}

	private static void i(String section, String key, int def, int min, int max, Supplier<Integer> get,
		Consumer<Integer> set) {
		list.add(new IntOption(section, key, def, min, max, get, set));
	}

	private static void f(String section, String key, float def, float min, float max, Supplier<Float> get,
		Consumer<Float> set) {
		list.add(new FloatOption(section, key, def, min, max, get, set));
	}

	private static void b(String section, String key, boolean def, Supplier<Boolean> get, Consumer<Boolean> set) {
		list.add(new BoolOption(section, key, def, get, set));
	}

	private static <E extends Enum<E>> void e(String section, String key, E def, Class<E> type, Supplier<E> get,
		Consumer<E> set) {
		list.add(new EnumOption<>(section, key, def, type, get, set));
	}
}
