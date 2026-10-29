package dev.createpolymer.processing.recipe;

import dev.createpolymer.registry.feature.CrushingRegistry;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/** Ported from Create's CrushingRecipe (MIT, The Create Team). */
public class CrushingRecipe extends AbstractCrushingRecipe {

	public CrushingRecipe(Params params) {
		super(params);
	}

	@Override
	public boolean matches(SingleRecipeInput inv, Level level) {
		if (inv.isEmpty() || getIngredients().isEmpty())
			return false;
		return getIngredients().getFirst().test(inv.item());
	}

	@Override
	protected int getMaxOutputCount() {
		return 7;
	}

	@Override
	public RecipeSerializer<CrushingRecipe> getSerializer() {
		return CrushingRegistry.CRUSHING_SERIALIZER;
	}

	@Override
	public RecipeType<CrushingRecipe> getType() {
		return CrushingRegistry.CRUSHING;
	}
}
