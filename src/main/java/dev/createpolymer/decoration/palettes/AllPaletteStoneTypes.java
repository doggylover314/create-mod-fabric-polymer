package dev.createpolymer.decoration.palettes;

import static dev.createpolymer.decoration.palettes.PaletteBlockPattern.STANDARD_RANGE;
import static dev.createpolymer.decoration.palettes.PaletteBlockPattern.VANILLA_RANGE;

import java.util.function.UnaryOperator;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.decoration.TexturedCubeBlock;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;

/**
 * The stone types that get a palette of cut, polished, brick, layered and pillar blocks. Granite, diorite, andesite,
 * calcite, dripstone, deepslate and tuff are vanilla blocks, the others are Create's own natural stones.
 */
public enum AllPaletteStoneTypes {

	GRANITE(VANILLA_RANGE, new Look(Blocks.GRANITE, Blocks.GRANITE_STAIRS, Blocks.GRANITE_SLAB, Blocks.GRANITE_WALL),
		Blocks.GRANITE),
	DIORITE(VANILLA_RANGE, new Look(Blocks.DIORITE, Blocks.DIORITE_STAIRS, Blocks.DIORITE_SLAB, Blocks.DIORITE_WALL),
		Blocks.DIORITE),
	ANDESITE(VANILLA_RANGE, new Look(Blocks.ANDESITE, Blocks.ANDESITE_STAIRS, Blocks.ANDESITE_SLAB, Blocks.ANDESITE_WALL),
		Blocks.ANDESITE),
	CALCITE(VANILLA_RANGE, new Look(Blocks.CALCITE, Blocks.DIORITE_STAIRS, Blocks.DIORITE_SLAB, Blocks.DIORITE_WALL),
		Blocks.CALCITE),
	DRIPSTONE(VANILLA_RANGE, new Look(Blocks.DRIPSTONE_BLOCK, Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICK_SLAB, Blocks.MUD_BRICK_WALL),
		Blocks.DRIPSTONE_BLOCK),
	DEEPSLATE(VANILLA_RANGE, new Look(Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE_STAIRS, Blocks.COBBLED_DEEPSLATE_SLAB, Blocks.COBBLED_DEEPSLATE_WALL),
		Blocks.DEEPSLATE),
	TUFF(VANILLA_RANGE, new Look(Blocks.TUFF, Blocks.TUFF_STAIRS, Blocks.TUFF_SLAB, Blocks.TUFF_WALL),
		Blocks.TUFF),

	ASURINE(STANDARD_RANGE, new Look(Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILE_WALL),
		new NewStone("asurine", Blocks.DEEPSLATE, true, p -> p.destroyTime(1.25f)
			.mapColor(MapColor.COLOR_BLUE)), false),

	CRIMSITE(STANDARD_RANGE, new Look(Blocks.RED_NETHER_BRICKS, Blocks.RED_NETHER_BRICK_STAIRS, Blocks.RED_NETHER_BRICK_SLAB, Blocks.RED_NETHER_BRICK_WALL),
		new NewStone("crimsite", Blocks.DEEPSLATE, true, p -> p.destroyTime(1.25f)
			.mapColor(MapColor.COLOR_RED)), false),

	LIMESTONE(STANDARD_RANGE, new Look(Blocks.SANDSTONE, Blocks.SANDSTONE_STAIRS, Blocks.SANDSTONE_SLAB, Blocks.SANDSTONE_WALL),
		new NewStone("limestone", Blocks.SANDSTONE, false, p -> p.destroyTime(1.25f)
			.mapColor(MapColor.SAND)), true),

	OCHRUM(STANDARD_RANGE, new Look(Blocks.END_STONE_BRICKS, Blocks.END_STONE_BRICK_STAIRS, Blocks.END_STONE_BRICK_SLAB, Blocks.END_STONE_BRICK_WALL),
		new NewStone("ochrum", Blocks.CALCITE, true, p -> p.destroyTime(1.25f)
			.mapColor(MapColor.TERRACOTTA_YELLOW)), true),

	SCORIA(STANDARD_RANGE, new Look(Blocks.BLACKSTONE, Blocks.BLACKSTONE_STAIRS, Blocks.BLACKSTONE_SLAB, Blocks.BLACKSTONE_WALL),
		new NewStone("scoria", Blocks.BLACKSTONE, false, p -> p.mapColor(MapColor.COLOR_BROWN)), true),

	SCORCHIA(STANDARD_RANGE, new Look(Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_STAIRS, Blocks.POLISHED_BLACKSTONE_SLAB, Blocks.POLISHED_BLACKSTONE_WALL),
		new NewStone("scorchia", Blocks.BLACKSTONE, false, p -> p.mapColor(MapColor.TERRACOTTA_GRAY)), false),

	VERIDIUM(STANDARD_RANGE, new Look(Blocks.PRISMARINE, Blocks.PRISMARINE_STAIRS, Blocks.PRISMARINE_SLAB, Blocks.PRISMARINE_WALL),
		new NewStone("veridium", Blocks.TUFF, true, p -> p.destroyTime(1.25f)
			.mapColor(MapColor.WARPED_NYLIUM)), false)

	;

	/**
	 * The vanilla blocks a stone type looks like to players where Polymer has no state for Create's model: the cube
	 * is shown if Polymer's cube states ran out, the stairs, slab and wall are shown instead of Create's.
	 */
	public record Look(Block cube, Block stairs, Block slab, Block wall) {}

	/** A stone that exists only in Create (name, vanilla block its properties start from, four random models, tweak). */
	private record NewStone(String name, Block propertiesFrom, boolean hasNaturalVariants,
		UnaryOperator<Properties> properties) {}

	private final PaletteBlockPattern[] variantTypes;
	private final Look look;
	private final @Nullable NewStone newStone;
	private final boolean texturedStairsAndSlabs;
	private Block baseBlock;
	private PalettesVariantEntry variants;
	public TagKey<Item> materialTag;

	private AllPaletteStoneTypes(PaletteBlockPattern[] variantTypes, Look look, Block vanillaBlock) {
		this.variantTypes = variantTypes;
		this.look = look;
		this.newStone = null;
		this.texturedStairsAndSlabs = false;
		this.baseBlock = vanillaBlock;
	}

	private AllPaletteStoneTypes(PaletteBlockPattern[] variantTypes, Look look, NewStone newStone,
		boolean texturedStairsAndSlabs) {
		this.variantTypes = variantTypes;
		this.look = look;
		this.newStone = newStone;
		this.texturedStairsAndSlabs = texturedStairsAndSlabs;
	}

	public Block getBaseBlock() {
		return baseBlock;
	}

	public PaletteBlockPattern[] getVariantTypes() {
		return variantTypes;
	}

	public Look getLook() {
		return look;
	}

	public PalettesVariantEntry getVariants() {
		return variants;
	}

	/** Whether the cut stairs and slab of this stone take Polymer's few stair and slab states (Create's own models). */
	public boolean hasTexturedStairsAndSlabs() {
		return texturedStairsAndSlabs;
	}

	public String getId() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}

	public static void register() {
		for (AllPaletteStoneTypes type : values()) {
			if (type.newStone != null) {
				NewStone stone = type.newStone;
				String[] models = new String[stone.hasNaturalVariants() ? 4 : 1];
				for (int i = 0; i < models.length; i++)
					models[i] = stone.hasNaturalVariants() ? stone.name() + "_natural_" + i : stone.name();
				Properties properties = stone.properties().apply(Properties.ofFullCopy(stone.propertiesFrom()));
				type.baseBlock = ModBlocks.register(stone.name(), p -> new TexturedCubeBlock(p, type.look.cube(), models),
					properties);
				ModItems.block(type.baseBlock);
			}
			String id = type.getId();
			type.materialTag = TagKey.create(Registries.ITEM, CreatePolymer.id("stone_types/" + id));
			type.variants = new PalettesVariantEntry(id, type);
		}
	}

}
