package dev.createpolymer.equipment.toolbox;

import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/** Create's UncontainableBlockItem: items that cannot be put into shulker boxes and similar containers */
public class UncontainableBlockItem extends PolymerBlockItem {

	public UncontainableBlockItem(Block block, Properties properties) {
		super(block, properties, Items.TRIAL_KEY, true);
	}

	@Override
	public boolean canFitInsideContainerItems() {
		return false;
	}
}
