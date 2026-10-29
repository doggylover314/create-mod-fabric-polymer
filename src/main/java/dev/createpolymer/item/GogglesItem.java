package dev.createpolymer.item;

import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.world.item.Items;

/**
 * Engineer's goggles. Wear them (or hold them) and look at a Create block to see
 * its speed and stress in the action bar, see {@link GogglesInfo}.
 */
public class GogglesItem extends SimplePolymerItem {
	public GogglesItem(Properties settings) {
		super(settings, Items.TRIAL_KEY, true);
	}
}
