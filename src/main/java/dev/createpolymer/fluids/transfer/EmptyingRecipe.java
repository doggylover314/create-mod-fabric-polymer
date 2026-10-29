package dev.createpolymer.fluids.transfer;

import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.processing.recipe.ProcessingRecipe;
import dev.createpolymer.registry.feature.TanksRegistry;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/** Ported from Create's EmptyingRecipe (MIT, The Create Team): what the item drain does to an item. */
public class EmptyingRecipe extends ProcessingRecipe<SingleRecipeInput> {

	public EmptyingRecipe(Params params) {
		super(params);
	}

	@Override
	public boolean matches(SingleRecipeInput inv, Level p_77569_2_) {
		return getIngredients().get(0).test(inv.getItem(0));
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
	protected int getMaxFluidOutputCount() {
		return 1;
	}

	public FluidStack getResultingFluid() {
		if (getFluidResults().isEmpty())
			throw new IllegalStateException("Emptying Recipe has no fluid output!");
		return getFluidResults().get(0);
	}

	@Override
	public RecipeSerializer<EmptyingRecipe> getSerializer() {
		return TanksRegistry.EMPTYING_SERIALIZER;
	}

	@Override
	public RecipeType<EmptyingRecipe> getType() {
		return TanksRegistry.EMPTYING;
	}

}
