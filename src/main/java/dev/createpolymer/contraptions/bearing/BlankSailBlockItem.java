package dev.createpolymer.contraptions.bearing;

import java.util.Map;

import dev.createpolymer.registry.feature.BearingRegistry;
import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/** The white sail's item, which stands for every dyed sail as well. */
public class BlankSailBlockItem extends PolymerBlockItem {
	public BlankSailBlockItem(Block block, Properties properties) {
		super(block, properties, Items.TRIAL_KEY, true);
	}

	@Override
	public void registerBlocks(Map<Block, Item> blockToItemMap, Item item) {
		super.registerBlocks(blockToItemMap, item);
		for (SailBlock sail : BearingRegistry.DYED_SAILS.values())
			blockToItemMap.put(sail, item);
	}
}
