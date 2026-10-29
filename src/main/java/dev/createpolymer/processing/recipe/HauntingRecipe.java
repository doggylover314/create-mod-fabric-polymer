package dev.createpolymer.processing.recipe;

import dev.createpolymer.registry.feature.FanRegistry;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/** Ported from Create's HauntingRecipe (MIT, The Create Team). */
public class HauntingRecipe extends ProcessingRecipe<SingleRecipeInput> {

	public HauntingRecipe(Params params) {
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
		return 12;
	}

	@Override
	public RecipeSerializer<HauntingRecipe> getSerializer() {
		return FanRegistry.HAUNTING_SERIALIZER;
	}

	@Override
	public RecipeType<HauntingRecipe> getType() {
		return FanRegistry.HAUNTING;
	}
}
