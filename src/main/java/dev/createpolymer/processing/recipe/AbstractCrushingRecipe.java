package dev.createpolymer.processing.recipe;

import net.minecraft.world.item.crafting.SingleRecipeInput;

/** Ported from Create's AbstractCrushingRecipe (MIT, The Create Team). */
public abstract class AbstractCrushingRecipe extends ProcessingRecipe<SingleRecipeInput> {

	protected AbstractCrushingRecipe(Params params) {
		super(params);
	}

	@Override
	protected int getMaxInputCount() {
		return 1;
	}
}
