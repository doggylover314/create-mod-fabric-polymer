package dev.createpolymer.decoration.palettes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/** The blocks of one stone type: one block per pattern and the stairs, slab and wall of the patterns that have them. */
public final class PalettesVariantEntry {

	public final List<Block> registeredBlocks;
	public final List<Block> registeredPartials;

	public PalettesVariantEntry(String name, AllPaletteStoneTypes paletteStoneVariants) {
		List<Block> registeredBlocks = new ArrayList<>();
		List<Block> registeredPartials = new ArrayList<>();
		Block baseBlock = paletteStoneVariants.getBaseBlock();

		for (PaletteBlockPattern pattern : paletteStoneVariants.getVariantTypes()) {
			String blockName = pattern.createName(name);
			PaletteBlockPattern.PaletteContext context =
				new PaletteBlockPattern.PaletteContext(name, blockName, paletteStoneVariants.getLook());
			Block block = ModBlocks.register(blockName, p -> pattern.getBlockFactory()
				.create(p, context), Properties.ofFullCopy(baseBlock));
			ModItems.block(block);
			registeredBlocks.add(block);

			for (PaletteBlockPartial<? extends Block> partial : pattern.getPartials()) {
				String partialName = partial.blockName(blockName);
				boolean textured = paletteStoneVariants.hasTexturedStairsAndSlabs() && pattern == PaletteBlockPattern.CUT
					&& partial != PaletteBlockPartial.WALL;
				Block partialBlock = ModBlocks.register(partialName, p -> partial.createBlock(p, block, blockName,
					partialName, paletteStoneVariants.getLook(), textured), partial.properties(block));
				ModItems.block(partialBlock);
				registeredPartials.add(partialBlock);
			}
		}

		this.registeredBlocks = Collections.unmodifiableList(registeredBlocks);
		this.registeredPartials = Collections.unmodifiableList(registeredPartials);
	}

}
