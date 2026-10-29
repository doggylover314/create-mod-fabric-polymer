package dev.createpolymer.registry.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import dev.createpolymer.decoration.CardboardBlock;
import dev.createpolymer.decoration.MetalBarsBlock;
import dev.createpolymer.decoration.MetalCubeBlock;
import dev.createpolymer.decoration.MetalLadderBlock;
import dev.createpolymer.decoration.MetalScaffoldingBlock;
import dev.createpolymer.decoration.MetalScaffoldingBlockItem;
import dev.createpolymer.decoration.palettes.AllPaletteStoneTypes;
import dev.createpolymer.decoration.palettes.ConnectedGlassBlock;
import dev.createpolymer.decoration.palettes.ConnectedGlassPaneBlock;
import dev.createpolymer.decoration.palettes.GlassPaneBlock;
import dev.createpolymer.decoration.palettes.WindowBlock;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.providers.number.floats.ContextFloatProviders;
import net.minecraft.world.level.storage.loot.providers.number.floats.ResolvableFloat;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;

/**
 * Create's decoration blocks: the stone-type palettes (cut, polished, bricks, layered and pillars of granite, diorite,
 * andesite, calcite, dripstone, deepslate, tuff, asurine, crimsite, limestone, ochrum, scoria, scorchia and veridium,
 * with their stairs, slabs and walls), glass, windows and panes, industrial and weathered iron, metal bars, ladders
 * and scaffolding and cardboard blocks. Block ids and item ids are Create's.
 */
public final class DecorationRegistry {
	private DecorationRegistry() {}

	/** Every block this registry added (the palette blocks too), in registration order. */
	public static final List<Block> BLOCKS = new ArrayList<>();

	// Create's SharedProperties.softMetal / the glass the windows start from
	private static Properties softMetal() {
		return Properties.ofFullCopy(Blocks.GOLD_BLOCK);
	}

	private static Properties glassProperties(Properties p) {
		return p.isValidSpawn((state, level, pos, type) -> false)
			.isRedstoneConductor((state, level, pos) -> false)
			.isSuffocating((state, level, pos) -> false)
			.isViewBlocking((state, level, pos, box) -> false);
	}

	private static <T extends Block> T block(String name, Function<Properties, T> factory, Properties properties) {
		T block = ModBlocks.register(name, factory, properties);
		BLOCKS.add(block);
		ModItems.block(block);
		return block;
	}

	// ---- stone types ----

	static {
		AllPaletteStoneTypes.register();
		for (AllPaletteStoneTypes type : AllPaletteStoneTypes.values()) {
			if (type.getBaseBlock() instanceof dev.createpolymer.decoration.TexturedCubeBlock)
				BLOCKS.add(type.getBaseBlock());
			BLOCKS.addAll(type.getVariants().registeredBlocks);
			BLOCKS.addAll(type.getVariants().registeredPartials);
		}
	}

	// ---- iron blocks ----

	public static final MetalCubeBlock INDUSTRIAL_IRON_BLOCK = block("industrial_iron_block",
		p -> new MetalCubeBlock(p, Blocks.IRON_BLOCK, "industrial_iron_block"),
		palettesIronBlock());
	public static final MetalCubeBlock WEATHERED_IRON_BLOCK = block("weathered_iron_block",
		p -> new MetalCubeBlock(p, Blocks.IRON_BLOCK, "weathered_iron_block"),
		palettesIronBlock());

	private static Properties palettesIronBlock() {
		return softMetal().mapColor(MapColor.COLOR_GRAY)
			.sound(SoundType.NETHERITE_BLOCK)
			.requiresCorrectToolForDrops();
	}

	// ---- glass and windows ----

	public static final ConnectedGlassBlock TILED_GLASS = block("tiled_glass",
		p -> new ConnectedGlassBlock(p, "tiled_glass"), Properties.ofFullCopy(Blocks.GLASS));
	public static final ConnectedGlassBlock FRAMED_GLASS = framedGlass("framed_glass");
	public static final ConnectedGlassBlock HORIZONTAL_FRAMED_GLASS = framedGlass("horizontal_framed_glass");
	public static final ConnectedGlassBlock VERTICAL_FRAMED_GLASS = framedGlass("vertical_framed_glass");

	public static final GlassPaneBlock TILED_GLASS_PANE = pane("tiled_glass_pane", TILED_GLASS, Blocks.GLASS_PANE, false);
	public static final ConnectedGlassPaneBlock FRAMED_GLASS_PANE = connectedPane("framed_glass_pane", FRAMED_GLASS, Blocks.GLASS_PANE);
	public static final ConnectedGlassPaneBlock HORIZONTAL_FRAMED_GLASS_PANE = connectedPane("horizontal_framed_glass_pane", HORIZONTAL_FRAMED_GLASS, Blocks.GLASS_PANE);
	public static final ConnectedGlassPaneBlock VERTICAL_FRAMED_GLASS_PANE = connectedPane("vertical_framed_glass_pane", VERTICAL_FRAMED_GLASS, Blocks.GLASS_PANE);

	public static final WindowBlock OAK_WINDOW = woodenWindow("oak", Blocks.OAK_PLANKS, false),
		SPRUCE_WINDOW = woodenWindow("spruce", Blocks.SPRUCE_PLANKS, false),
		BIRCH_WINDOW = woodenWindow("birch", Blocks.BIRCH_PLANKS, true),
		JUNGLE_WINDOW = woodenWindow("jungle", Blocks.JUNGLE_PLANKS, false),
		ACACIA_WINDOW = woodenWindow("acacia", Blocks.ACACIA_PLANKS, false),
		DARK_OAK_WINDOW = woodenWindow("dark_oak", Blocks.DARK_OAK_PLANKS, false),
		MANGROVE_WINDOW = woodenWindow("mangrove", Blocks.MANGROVE_PLANKS, false),
		CRIMSON_WINDOW = woodenWindow("crimson", Blocks.CRIMSON_PLANKS, false),
		WARPED_WINDOW = woodenWindow("warped", Blocks.WARPED_PLANKS, false),
		CHERRY_WINDOW = woodenWindow("cherry", Blocks.CHERRY_PLANKS, false),
		BAMBOO_WINDOW = woodenWindow("bamboo", Blocks.BAMBOO_PLANKS, false),
		ORNATE_IRON_WINDOW = window("ornate_iron_window", false, MapColor.TERRACOTTA_LIGHT_GRAY, "ornate_iron_window"),
		INDUSTRIAL_IRON_WINDOW = window("industrial_iron_window", false, MapColor.COLOR_GRAY, "industrial_iron_window"),
		// Create picks one of four end textures at random for each weathered iron window
		WEATHERED_IRON_WINDOW = window("weathered_iron_window", true, MapColor.TERRACOTTA_LIGHT_GRAY,
			"weathered_iron_window_1", "weathered_iron_window_2", "weathered_iron_window_3", "weathered_iron_window_4");

	public static final ConnectedGlassPaneBlock OAK_WINDOW_PANE = connectedPane("oak_window_pane", OAK_WINDOW, Blocks.GLASS_PANE),
		SPRUCE_WINDOW_PANE = connectedPane("spruce_window_pane", SPRUCE_WINDOW, Blocks.GLASS_PANE),
		BIRCH_WINDOW_PANE = connectedPane("birch_window_pane", BIRCH_WINDOW, Blocks.GLASS_PANE),
		JUNGLE_WINDOW_PANE = connectedPane("jungle_window_pane", JUNGLE_WINDOW, Blocks.GLASS_PANE),
		ACACIA_WINDOW_PANE = connectedPane("acacia_window_pane", ACACIA_WINDOW, Blocks.GLASS_PANE),
		DARK_OAK_WINDOW_PANE = connectedPane("dark_oak_window_pane", DARK_OAK_WINDOW, Blocks.GLASS_PANE),
		MANGROVE_WINDOW_PANE = connectedPane("mangrove_window_pane", MANGROVE_WINDOW, Blocks.GLASS_PANE),
		CRIMSON_WINDOW_PANE = connectedPane("crimson_window_pane", CRIMSON_WINDOW, Blocks.GLASS_PANE),
		WARPED_WINDOW_PANE = connectedPane("warped_window_pane", WARPED_WINDOW, Blocks.GLASS_PANE),
		CHERRY_WINDOW_PANE = connectedPane("cherry_window_pane", CHERRY_WINDOW, Blocks.GLASS_PANE),
		BAMBOO_WINDOW_PANE = connectedPane("bamboo_window_pane", BAMBOO_WINDOW, Blocks.GLASS_PANE),
		// the iron windows have no glass pane look-alike: their panes show as iron bars
		ORNATE_IRON_WINDOW_PANE = connectedPane("ornate_iron_window_pane", ORNATE_IRON_WINDOW, Blocks.IRON_BARS),
		INDUSTRIAL_IRON_WINDOW_PANE = connectedPane("industrial_iron_window_pane", INDUSTRIAL_IRON_WINDOW, Blocks.IRON_BARS),
		WEATHERED_IRON_WINDOW_PANE = connectedPane("weathered_iron_window_pane", WEATHERED_IRON_WINDOW, Blocks.IRON_BARS);

	private static ConnectedGlassBlock framedGlass(String name) {
		return block(name, p -> new ConnectedGlassBlock(p, name), glassProperties(Properties.ofFullCopy(Blocks.GLASS)));
	}

	private static WindowBlock woodenWindow(String woodName, Block planksBlock, boolean translucent) {
		String name = woodName + "_window";
		return block(name, p -> new WindowBlock(p, translucent, name),
			glassProperties(Properties.ofFullCopy(Blocks.GLASS)).mapColor(planksBlock.defaultMapColor()));
	}

	private static WindowBlock window(String name, boolean translucent, MapColor color, String... models) {
		return block(name, p -> new WindowBlock(p, translucent, models),
			glassProperties(Properties.ofFullCopy(Blocks.GLASS)).mapColor(color));
	}

	private static GlassPaneBlock pane(String name, Block parent, Block look, boolean ignored) {
		return block(name, p -> new GlassPaneBlock(p, look),
			Properties.ofFullCopy(Blocks.GLASS_PANE).mapColor(parent.defaultMapColor()));
	}

	private static ConnectedGlassPaneBlock connectedPane(String name, Block parent, Block look) {
		return block(name, p -> new ConnectedGlassPaneBlock(p, look),
			Properties.ofFullCopy(Blocks.GLASS_PANE).mapColor(parent.defaultMapColor()));
	}

	// ---- bars, ladders, scaffolding ----

	public static final MetalBarsBlock ANDESITE_BARS = bars("andesite", MapColor.STONE);
	public static final MetalBarsBlock BRASS_BARS = bars("brass", MapColor.TERRACOTTA_YELLOW);
	public static final MetalBarsBlock COPPER_BARS = bars("copper", MapColor.COLOR_ORANGE);

	public static final MetalLadderBlock ANDESITE_LADDER = ladder("andesite", MapColor.STONE);
	public static final MetalLadderBlock BRASS_LADDER = ladder("brass", MapColor.TERRACOTTA_YELLOW);
	public static final MetalLadderBlock COPPER_LADDER = ladder("copper", MapColor.COLOR_ORANGE);

	public static final MetalScaffoldingBlock ANDESITE_SCAFFOLD = scaffold("andesite", MapColor.STONE);
	public static final MetalScaffoldingBlock BRASS_SCAFFOLD = scaffold("brass", MapColor.TERRACOTTA_YELLOW);
	public static final MetalScaffoldingBlock COPPER_SCAFFOLD = scaffold("copper", MapColor.COLOR_ORANGE);

	private static MetalBarsBlock bars(String name, MapColor color) {
		return block(name + "_bars", p -> new MetalBarsBlock(p, name),
			Properties.ofFullCopy(Blocks.IRON_BARS).sound(SoundType.COPPER).mapColor(color));
	}

	private static MetalLadderBlock ladder(String name, MapColor color) {
		return block(name + "_ladder", MetalLadderBlock::new,
			Properties.ofFullCopy(Blocks.LADDER).mapColor(color).sound(SoundType.COPPER));
	}

	private static MetalScaffoldingBlock scaffold(String name, MapColor color) {
		String id = name + "_scaffolding";
		MetalScaffoldingBlock block = ModBlocks.register(id, p -> new MetalScaffoldingBlock(p, id),
			Properties.ofFullCopy(Blocks.SCAFFOLDING).sound(SoundType.COPPER).mapColor(color));
		BLOCKS.add(block);
		ModItems.register(id, p -> new MetalScaffoldingBlockItem(block, p), new Item.Properties().useBlockDescriptionPrefix());
		return block;
	}

	// ---- cardboard ----

	public static final CardboardBlock CARDBOARD_BLOCK = cardboard("cardboard_block");
	public static final CardboardBlock BOUND_CARDBOARD_BLOCK = cardboard("bound_cardboard_block");

	private static CardboardBlock cardboard(String name) {
		CardboardBlock block = ModBlocks.register(name, p -> new CardboardBlock(p, name),
			Properties.ofFullCopy(Blocks.MUSHROOM_STEM).mapColor(MapColor.COLOR_BROWN).sound(SoundType.CHISELED_BOOKSHELF)
				.ignitedByLava());
		BLOCKS.add(block);
		// burns for 4000 ticks, like Create's block item
		ModItems.block(block, new Item.Properties().component(DataComponents.COOKING_FUEL,
			new CookingFuel(new ResolvableInt.Constant(4000),
				ResolvableFloat.fromKey(ContextFloatProviders.COOKING_DEFAULT_SPEED_MULTIPLIER))));
		return block;
	}

	public static void init() {
		// Create's CardboardBlock: spreads fire with 100 and burns with 20
		FlammableBlockRegistry.getDefaultInstance().add(CARDBOARD_BLOCK, 100, 20);
		FlammableBlockRegistry.getDefaultInstance().add(BOUND_CARDBOARD_BLOCK, 100, 20);
	}
}
