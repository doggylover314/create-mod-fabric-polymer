package dev.createpolymer.fluids.transfer;

import dev.createpolymer.processing.recipe.ProcessingRecipe;
import dev.createpolymer.processing.recipe.SizedFluidIngredient;
import dev.createpolymer.registry.feature.TanksRegistry;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/** Ported from Create's FillingRecipe (MIT, The Create Team): what the spout does to an item. */
public class FillingRecipe extends ProcessingRecipe<SingleRecipeInput> {

	public FillingRecipe(Params params) {
		super(params);
	}

	@Override
	public boolean matches(SingleRecipeInput inv, Level p_77569_2_) {
		return getIngredients().get(0)
			.test(inv.getItem(0));
	}

	@Override
	protected int getMaxInputCount() {
		return 1;
	}

	@Override
	protected int getMaxOutputCount() {
		return 1;
	}

	@Override
	protected int getMaxFluidInputCount() {
		return 1;
	}

	public SizedFluidIngredient getRequiredFluid() {
		if (getFluidIngredients().isEmpty())
			throw new IllegalStateException("Filling Recipe has no fluid ingredient!");
		return getFluidIngredients().get(0);
	}

	@Override
	public RecipeSerializer<FillingRecipe> getSerializer() {
		return TanksRegistry.FILLING_SERIALIZER;
	}

	@Override
	public RecipeType<FillingRecipe> getType() {
		return TanksRegistry.FILLING;
	}

}
