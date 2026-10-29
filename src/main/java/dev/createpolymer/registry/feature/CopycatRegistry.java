package dev.createpolymer.registry.feature;

import dev.createpolymer.decoration.TexturedCubeBlock;
import dev.createpolymer.decoration.copycat.CopycatBarsBlock;
import dev.createpolymer.decoration.copycat.CopycatBlockEntity;
import dev.createpolymer.decoration.copycat.CopycatPanelBlock;
import dev.createpolymer.decoration.copycat.CopycatStepBlock;
import dev.createpolymer.redstone.RedstoneClientStates;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;

/**
 * Create's copycat blocks: a panel and a step that take on the look of the block used on them. copycat_base is the
 * plain look every copycat starts with (never placed, no item) and copycat_bars is the model a panel borrows for
 * bars (never placed, no item). Block, block entity and item ids are Create's.
 */
public final class CopycatRegistry {
	private CopycatRegistry() {}

	// Create's SharedProperties.softMetal (a gold block copy) and BuilderTransformers.copycat
	private static Properties softMetal() {
		return Properties.ofFullCopy(Blocks.GOLD_BLOCK);
	}

	private static Properties copycat() {
		return softMetal().noOcclusion()
			.mapColor(MapColor.NONE)
			.isValidSpawn((state, level, pos, type) -> false);
	}

	// ---- blocks ----

	public static final TexturedCubeBlock COPYCAT_BASE = ModBlocks.register("copycat_base",
		p -> new TexturedCubeBlock(p, Blocks.GOLD_BLOCK, "copycat_base/block"),
		softMetal().mapColor(MapColor.GLOW_LICHEN).noLootTable());
	public static final CopycatStepBlock COPYCAT_STEP = ModBlocks.register("copycat_step", CopycatStepBlock::new,
		copycat().forceSolidOn());
	public static final CopycatPanelBlock COPYCAT_PANEL = ModBlocks.register("copycat_panel", CopycatPanelBlock::new,
		copycat());
	public static final CopycatBarsBlock COPYCAT_BARS = ModBlocks.register("copycat_bars", CopycatBarsBlock::new,
		softMetal().noOcclusion().noLootTable());

	// ---- block entities ----

	public static final BlockEntityType<CopycatBlockEntity> COPYCAT_BLOCK_ENTITY = ModBlockEntities.register("copycat",
		(pos, state) -> new CopycatBlockEntity(CopycatRegistry.COPYCAT_BLOCK_ENTITY, pos, state), COPYCAT_PANEL,
		COPYCAT_STEP);

	// ---- items ----

	public static final Item COPYCAT_STEP_ITEM = ModItems.block(COPYCAT_STEP);
	public static final Item COPYCAT_PANEL_ITEM = ModItems.block(COPYCAT_PANEL);

	public static void init() {
		// the invisible plates and half blocks the copycats show to players (Polymer wants them before the pack is built)
		RedstoneClientStates.init();
	}
}
