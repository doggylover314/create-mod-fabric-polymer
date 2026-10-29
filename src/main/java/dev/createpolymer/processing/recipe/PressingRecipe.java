package dev.createpolymer.processing.recipe;

import dev.createpolymer.registry.ModRecipeTypes;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/** Ported from Create's PressingRecipe (MIT, The Create Team). */
public class PressingRecipe extends ProcessingRecipe<SingleRecipeInput> {

	public PressingRecipe(Params params) {
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
		return 2;
	}

	@Override
	public RecipeSerializer<PressingRecipe> getSerializer() {
		return ModRecipeTypes.PRESSING_SERIALIZER;
	}

	@Override
	public RecipeType<PressingRecipe> getType() {
		return ModRecipeTypes.PRESSING;
	}
}
