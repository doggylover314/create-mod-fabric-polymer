package dev.createpolymer.registry.feature;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.worldgen.CreateWorldgen;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * Create's basic materials: zinc and brass, crushed ores, the zinc ore and the metal and andesite storage blocks.
 * Block ids and item ids are Create's, so its recipes, tags and loot tables apply unchanged.
 */
public final class MaterialsRegistry {
	private MaterialsRegistry() {}

	/**
	 * A full cube shown with a Create block model, like the andesite casing. Players see the model through a vanilla
	 * block state that Polymer reserves for it; {@code fallback} is shown when Polymer has no state left.
	 */
	public static final class TexturedBlock extends Block implements PolymerTexturedBlock {
		private final BlockState clientState;
		private final Block fallback;

		public TexturedBlock(BlockBehaviour.Properties properties, String model, Block fallback) {
			super(properties);
			this.fallback = fallback;
			this.clientState = PolymerBlockResourceUtils.requestBlock(BlockModelType.FULL_BLOCK,
				PolymerBlockModel.of(CreatePolymer.id("block/" + model)));
		}

		@Override
		public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
			return clientState != null ? clientState : fallback.defaultBlockState();
		}

		@Override
		public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
			return fallback.defaultBlockState();
		}
	}

	// Blocks. Properties follow Create's AllBlocks: the ores start from the gold ores, the metal blocks from iron.
	public static final Block ZINC_ORE = ModBlocks.register("zinc_ore",
		p -> new TexturedBlock(p, "zinc_ore", Blocks.IRON_ORE),
		BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_ORE).mapColor(MapColor.METAL).requiresCorrectToolForDrops().sound(SoundType.STONE));
	public static final Block DEEPSLATE_ZINC_ORE = ModBlocks.register("deepslate_zinc_ore",
		p -> new TexturedBlock(p, "deepslate_zinc_ore", Blocks.DEEPSLATE_IRON_ORE),
		BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_GOLD_ORE).mapColor(MapColor.STONE).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE));
	public static final Block RAW_ZINC_BLOCK = ModBlocks.register("raw_zinc_block",
		p -> new TexturedBlock(p, "raw_zinc_block", Blocks.RAW_IRON_BLOCK),
		BlockBehaviour.Properties.ofFullCopy(Blocks.RAW_GOLD_BLOCK).mapColor(MapColor.GLOW_LICHEN).requiresCorrectToolForDrops());
	public static final Block ZINC_BLOCK = ModBlocks.register("zinc_block",
		p -> new TexturedBlock(p, "zinc_block", Blocks.IRON_BLOCK),
		BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).mapColor(MapColor.GLOW_LICHEN).requiresCorrectToolForDrops());
	public static final Block BRASS_BLOCK = ModBlocks.register("brass_block",
		p -> new TexturedBlock(p, "brass_block", Blocks.GOLD_BLOCK),
		BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).mapColor(MapColor.TERRACOTTA_YELLOW).requiresCorrectToolForDrops());
	public static final Block ANDESITE_ALLOY_BLOCK = ModBlocks.register("andesite_alloy_block",
		p -> new TexturedBlock(p, "andesite_alloy_block", Blocks.ANDESITE),
		BlockBehaviour.Properties.ofFullCopy(Blocks.ANDESITE).mapColor(MapColor.STONE).requiresCorrectToolForDrops());

	// Block items
	public static final Item ZINC_ORE_ITEM = ModItems.block(ZINC_ORE);
	public static final Item DEEPSLATE_ZINC_ORE_ITEM = ModItems.block(DEEPSLATE_ZINC_ORE);
	public static final Item RAW_ZINC_BLOCK_ITEM = ModItems.block(RAW_ZINC_BLOCK);
	public static final Item ZINC_BLOCK_ITEM = ModItems.block(ZINC_BLOCK);
	public static final Item BRASS_BLOCK_ITEM = ModItems.block(BRASS_BLOCK);
	public static final Item ANDESITE_ALLOY_BLOCK_ITEM = ModItems.block(ANDESITE_ALLOY_BLOCK);

	// Items
	public static final Item ZINC_INGOT = ModItems.simple("zinc_ingot");
	public static final Item ZINC_NUGGET = ModItems.simple("zinc_nugget");
	public static final Item RAW_ZINC = ModItems.simple("raw_zinc");
	public static final Item BRASS_INGOT = ModItems.simple("brass_ingot");
	public static final Item BRASS_NUGGET = ModItems.simple("brass_nugget");
	public static final Item BRASS_SHEET = ModItems.simple("brass_sheet");
	public static final Item ROSE_QUARTZ = ModItems.simple("rose_quartz");
	public static final Item POLISHED_ROSE_QUARTZ = ModItems.simple("polished_rose_quartz");
	public static final Item ELECTRON_TUBE = ModItems.simple("electron_tube");
	public static final Item CRUSHED_RAW_IRON = ModItems.simple("crushed_raw_iron");
	public static final Item CRUSHED_RAW_GOLD = ModItems.simple("crushed_raw_gold");
	public static final Item CRUSHED_RAW_COPPER = ModItems.simple("crushed_raw_copper");
	public static final Item CRUSHED_RAW_ZINC = ModItems.simple("crushed_raw_zinc");
	public static final Item EXPERIENCE_NUGGET = ModItems.simple("experience_nugget");
	public static final Item CINDER_FLOUR = ModItems.simple("cinder_flour");
	public static final Item POWDERED_OBSIDIAN = ModItems.simple("powdered_obsidian");
	public static final Item DOUGH = ModItems.simple("dough");
	public static final Item STURDY_SHEET = ModItems.simple("sturdy_sheet");
	public static final Item PROPELLER = ModItems.simple("propeller");
	public static final Item BRASS_HAND = ModItems.simple("brass_hand");
	public static final Item PRECISION_MECHANISM = ModItems.simple("precision_mechanism");
	public static final Item INCOMPLETE_PRECISION_MECHANISM = ModItems.simple("incomplete_precision_mechanism");
	public static final Item CARDBOARD = ModItems.simple("cardboard");

	public static void init() {
		CreateWorldgen.init();
	}
}
