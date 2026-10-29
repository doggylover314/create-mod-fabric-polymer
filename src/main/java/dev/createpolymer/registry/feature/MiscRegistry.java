package dev.createpolymer.registry.feature;

import dev.createpolymer.kinetics.speedController.SpeedControllerBlock;
import dev.createpolymer.kinetics.speedController.SpeedControllerBlockEntity;
import dev.createpolymer.logistics.crate.CreativeCrateBlock;
import dev.createpolymer.logistics.crate.CreativeCrateBlockEntity;
import dev.createpolymer.logistics.itemHatch.ItemHatchBlock;
import dev.createpolymer.logistics.itemHatch.ItemHatchBlockEntity;
import dev.createpolymer.logistics.itemHatch.ItemHatchHandler;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Rotation speed controller, creative crate and item hatch. */
public final class MiscRegistry {
	private MiscRegistry() {}

	// Create's SharedProperties.softMetal (a gold block copy)
	private static BlockBehaviour.Properties softMetal() {
		return BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK);
	}

	// ---- blocks ----

	public static final SpeedControllerBlock ROTATION_SPEED_CONTROLLER = ModBlocks.register("rotation_speed_controller",
		SpeedControllerBlock::new, softMetal().mapColor(MapColor.TERRACOTTA_YELLOW));
	public static final CreativeCrateBlock CREATIVE_CRATE = ModBlocks.register("creative_crate", CreativeCrateBlock::new,
		ModBlocks.stone().mapColor(MapColor.COLOR_PURPLE));
	public static final ItemHatchBlock ITEM_HATCH = ModBlocks.register("item_hatch", ItemHatchBlock::new,
		softMetal().mapColor(MapColor.TERRACOTTA_BLUE).sound(SoundType.NETHERITE_BLOCK));

	// ---- block entities ----

	public static final BlockEntityType<SpeedControllerBlockEntity> ROTATION_SPEED_CONTROLLER_BLOCK_ENTITY = ModBlockEntities.register(
		"rotation_speed_controller",
		(pos, state) -> new SpeedControllerBlockEntity(MiscRegistry.ROTATION_SPEED_CONTROLLER_BLOCK_ENTITY, pos, state),
		ROTATION_SPEED_CONTROLLER);
	public static final BlockEntityType<CreativeCrateBlockEntity> CREATIVE_CRATE_BLOCK_ENTITY = ModBlockEntities.register("creative_crate",
		(pos, state) -> new CreativeCrateBlockEntity(MiscRegistry.CREATIVE_CRATE_BLOCK_ENTITY, pos, state), CREATIVE_CRATE);
	public static final BlockEntityType<ItemHatchBlockEntity> ITEM_HATCH_BLOCK_ENTITY = ModBlockEntities.register("item_hatch",
		(pos, state) -> new ItemHatchBlockEntity(MiscRegistry.ITEM_HATCH_BLOCK_ENTITY, pos, state), ITEM_HATCH);

	// ---- items ----

	public static final Item ROTATION_SPEED_CONTROLLER_ITEM = ModItems.block(ROTATION_SPEED_CONTROLLER);
	public static final Item CREATIVE_CRATE_ITEM = ModItems.block(CREATIVE_CRATE);
	public static final Item ITEM_HATCH_ITEM = ModItems.block(ITEM_HATCH);

	public static void init() {
		CreativeCrateBlockEntity.registerCapabilities();
		ItemHatchHandler.registerEvents();
	}
}
