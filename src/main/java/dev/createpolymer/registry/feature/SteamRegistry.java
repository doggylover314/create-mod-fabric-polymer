package dev.createpolymer.registry.feature;

import dev.createpolymer.decoration.steamWhistle.WhistleBlock;
import dev.createpolymer.decoration.steamWhistle.WhistleBlockEntity;
import dev.createpolymer.decoration.steamWhistle.WhistleExtenderBlock;
import dev.createpolymer.fluids.tank.BoilerData;
import dev.createpolymer.kinetics.base.BlockStressValues;
import dev.createpolymer.kinetics.steamEngine.PoweredShaftBlock;
import dev.createpolymer.kinetics.steamEngine.PoweredShaftBlockEntity;
import dev.createpolymer.kinetics.steamEngine.SteamEngineBlock;
import dev.createpolymer.kinetics.steamEngine.SteamEngineBlockEntity;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Steam engine, powered shaft, steam whistle and its extension. They attach to the fluid tanks of {@link TanksRegistry}
 * (boilers) through {@link BoilerData#ENGINE_ATTACHMENTS} and {@link BoilerData#WHISTLE_ATTACHMENTS}.
 */
public final class SteamRegistry {
	private SteamRegistry() {}

	// Create: SharedProperties.copperMetal is a copy of the copper block
	private static BlockBehaviour.Properties copperMetal() {
		return BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK.weathering().unaffected()).noOcclusion();
	}

	// ---- blocks ----

	public static final SteamEngineBlock STEAM_ENGINE = ModBlocks.register("steam_engine", SteamEngineBlock::new,
		copperMetal());
	public static final WhistleBlock STEAM_WHISTLE = ModBlocks.register("steam_whistle", WhistleBlock::new,
		copperMetal().mapColor(MapColor.GOLD));
	public static final WhistleExtenderBlock STEAM_WHISTLE_EXTENSION = ModBlocks.register("steam_whistle_extension", WhistleExtenderBlock::new,
		// Create's loot table for the extension is an empty one (an air item, which 26.3 refuses to load)
		copperMetal().mapColor(MapColor.GOLD).forceSolidOn().noLootTable());
	public static final PoweredShaftBlock POWERED_SHAFT = ModBlocks.register("powered_shaft", PoweredShaftBlock::new,
		ModBlocks.stone().mapColor(MapColor.METAL).forceSolidOn());

	// ---- block entities ----

	public static final BlockEntityType<SteamEngineBlockEntity> STEAM_ENGINE_BE = ModBlockEntities.register("steam_engine",
		(pos, state) -> new SteamEngineBlockEntity(SteamRegistry.STEAM_ENGINE_BE, pos, state), STEAM_ENGINE);
	public static final BlockEntityType<WhistleBlockEntity> STEAM_WHISTLE_BE = ModBlockEntities.register("steam_whistle",
		(pos, state) -> new WhistleBlockEntity(SteamRegistry.STEAM_WHISTLE_BE, pos, state), STEAM_WHISTLE);
	public static final BlockEntityType<PoweredShaftBlockEntity> POWERED_SHAFT_BE = ModBlockEntities.register("powered_shaft",
		(pos, state) -> new PoweredShaftBlockEntity(SteamRegistry.POWERED_SHAFT_BE, pos, state), POWERED_SHAFT);

	// ---- items ----

	public static final Item STEAM_ENGINE_ITEM = ModItems.block(STEAM_ENGINE);
	public static final Item STEAM_WHISTLE_ITEM = ModItems.block(STEAM_WHISTLE);

	public static void init() {
		// Create's AllBlocks: the steam engine provides 1024 SU per RPM at 64 RPM
		BlockStressValues.setCapacity(STEAM_ENGINE, 1024);
		BlockStressValues.setGeneratorSpeed(STEAM_ENGINE, 64);

		BoilerData.engineCapacity = () -> BlockStressValues.getCapacity(STEAM_ENGINE);
		BoilerData.ENGINE_ATTACHMENTS.add((state, d) -> state.getBlock() instanceof SteamEngineBlock
			&& SteamEngineBlock.getFacing(state) == d);
		BoilerData.WHISTLE_ATTACHMENTS.add((state, d) -> state.getBlock() instanceof WhistleBlock
			&& WhistleBlock.getAttachedDirection(state).getOpposite() == d);
	}
}
