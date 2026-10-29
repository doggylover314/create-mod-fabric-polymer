package dev.createpolymer.processing.recipe;

import dev.createpolymer.registry.feature.SawRegistry;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/** Ported from Create's CuttingRecipe (MIT, The Create Team). Sequenced assembly is not ported yet. */
public class CuttingRecipe extends ProcessingRecipe<SingleRecipeInput> {

	public CuttingRecipe(Params params) {
		super(params);
	}

	@Override
	public boolean matches(SingleRecipeInput inv, Level level) {
		if (inv.isEmpty() || getIngredients().isEmpty())
			return false;
		return getIngredients().getFirst().test(inv.item());
	}

	@Override
	protected int getMaxInputCount() {
		return 1;
	}

	@Override
	protected int getMaxOutputCount() {
		return 4;
	}

	@Override
	public RecipeSerializer<CuttingRecipe> getSerializer() {
		return SawRegistry.CUTTING_SERIALIZER;
	}

	@Override
	public RecipeType<CuttingRecipe> getType() {
		return SawRegistry.CUTTING;
	}
}
