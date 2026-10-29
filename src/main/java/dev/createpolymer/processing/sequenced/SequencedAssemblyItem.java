package dev.createpolymer.processing.sequenced;

import dev.createpolymer.registry.feature.DeployerRegistry;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Ported from Create's SequencedAssemblyItem (MIT, The Create Team). Create draws the progress as a durability bar
 * on the client; players here see the plain item.
 */
public class SequencedAssemblyItem extends SimplePolymerItem {

	public SequencedAssemblyItem(Item.Properties properties) {
		super(properties.stacksTo(1), Items.TRIAL_KEY, true);
	}

	public float getProgress(ItemStack stack) {
		SequencedAssemblyRecipe.SequencedAssembly assembly = stack.get(DeployerRegistry.SEQUENCED_ASSEMBLY);
		if (assembly == null)
			return 0;
		return assembly.progress();
	}
}
