package dev.createpolymer.decoration.palettes;

import static dev.createpolymer.decoration.palettes.PaletteBlockPartial.ALL_PARTIALS;
import static dev.createpolymer.decoration.palettes.PaletteBlockPartial.FOR_POLISHED;
import static dev.createpolymer.decoration.palettes.PaletteBlockPattern.PatternNameType.PREFIX;
import static dev.createpolymer.decoration.palettes.PaletteBlockPattern.PatternNameType.SUFFIX;
import static dev.createpolymer.decoration.palettes.PaletteBlockPattern.PatternNameType.WRAP;

import dev.createpolymer.decoration.TexturedCubeBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * One shape of palette block (cut, bricks, pillar, ...) that is made for every stone type, with its stairs, slab and
 * wall. Create's model and texture bookkeeping is gone: the models are Create's own generated block models, which
 * are named after the block.
 */
public class PaletteBlockPattern {

	public static final PaletteBlockPattern

	CUT = create("cut", PREFIX, ALL_PARTIALS),

		BRICKS = create("cut_bricks", WRAP, ALL_PARTIALS),

		SMALL_BRICKS = create("small_bricks", WRAP, ALL_PARTIALS),

		POLISHED = create("polished_cut", PREFIX, FOR_POLISHED),

		LAYERED = create("layered", PREFIX),

		PILLAR = create("pillar", SUFFIX)
			.block((p, c) -> new ConnectedPillarBlock(p, c.look().cube(), c.blockName(), c.blockName() + "_horizontal"))

	;

	public static final PaletteBlockPattern[] VANILLA_RANGE = { CUT, POLISHED, BRICKS, SMALL_BRICKS, LAYERED, PILLAR };

	public static final PaletteBlockPattern[] STANDARD_RANGE = { CUT, POLISHED, BRICKS, SMALL_BRICKS, LAYERED, PILLAR };

	/** What a block of a pattern needs to know about the stone type it is made for. */
	public record PaletteContext(String variant, String blockName, AllPaletteStoneTypes.Look look) {}

	@FunctionalInterface
	public interface BlockFactory {
		Block create(Properties properties, PaletteContext context);
	}

	private PatternNameType nameType;
	private String id;
	private BlockFactory blockFactory;
	private PaletteBlockPartial<? extends Block>[] partials;

	private static PaletteBlockPattern create(String name, PatternNameType nameType,
		PaletteBlockPartial<?>... partials) {
		PaletteBlockPattern pattern = new PaletteBlockPattern();
		pattern.id = name;
		pattern.nameType = nameType;
		pattern.partials = partials;
		pattern.blockFactory = (p, c) -> new TexturedCubeBlock(p, c.look().cube(), c.blockName());
		return pattern;
	}

	public String getId() {
		return id;
	}

	public BlockFactory getBlockFactory() {
		return blockFactory;
	}

	public PaletteBlockPartial<? extends Block>[] getPartials() {
		return partials;
	}

	// Builder

	private PaletteBlockPattern block(BlockFactory blockFactory) {
		this.blockFactory = blockFactory;
		return this;
	}

	// Utility

	public String createName(String variant) {
		if (nameType == WRAP) {
			String[] split = id.split("_");
			if (split.length == 2) {
				String formatString = "%s_%s_%s";
				return String.format(formatString, split[0], variant, split[1]);
			}
		}
		String formatString = "%s_%s";
		return nameType == SUFFIX ? String.format(formatString, variant, id) : String.format(formatString, id, variant);
	}

	enum PatternNameType {
		PREFIX, SUFFIX, WRAP
	}

}
