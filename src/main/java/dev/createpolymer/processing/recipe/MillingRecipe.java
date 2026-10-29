package dev.createpolymer.processing.recipe;

import dev.createpolymer.registry.ModRecipeTypes;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/** Ported from Create's MillingRecipe (MIT, The Create Team). */
public class MillingRecipe extends ProcessingRecipe<SingleRecipeInput> {

	public MillingRecipe(Params params) {
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
	public RecipeSerializer<MillingRecipe> getSerializer() {
		return ModRecipeTypes.MILLING_SERIALIZER;
	}

	@Override
	public RecipeType<MillingRecipe> getType() {
		return ModRecipeTypes.MILLING;
	}
}
