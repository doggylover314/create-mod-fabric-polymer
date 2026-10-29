package dev.createpolymer.kinetics.deployer;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

/** Create's RecipeWrapper over the deployer's two slots: the processed item and the held item. */
public record ItemApplicationInput(ItemStack processed, ItemStack held) implements RecipeInput {

	@Override
	public ItemStack getItem(int index) {
		return switch (index) {
			case 0 -> processed;
			case 1 -> held;
			default -> throw new IllegalArgumentException("No item for index " + index);
		};
	}

	@Override
	public int size() {
		return 2;
	}
}
