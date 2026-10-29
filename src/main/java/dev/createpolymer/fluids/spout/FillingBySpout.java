package dev.createpolymer.fluids.spout;

import java.util.ArrayList;
import java.util.List;

import dev.createpolymer.fluids.transfer.FillingRecipe;
import dev.createpolymer.fluids.transfer.GenericItemFilling;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.processing.basin.RecipeCache;
import dev.createpolymer.processing.recipe.SizedFluidIngredient;
import dev.createpolymer.registry.feature.TanksRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/** Ported from Create's FillingBySpout (MIT, The Create Team), without sequenced assembly. */
public class FillingBySpout {

	/** The filling recipes that take the item, whatever fluid they need. */
	private static List<FillingRecipe> recipesFor(Level world, SingleRecipeInput input) {
		List<FillingRecipe> matching = new ArrayList<>();
		if (!(world instanceof ServerLevel serverLevel))
			return matching;
		for (var recipe : RecipeCache.get(TanksRegistry.FILLING, serverLevel, h -> h.value().getType() == TanksRegistry.FILLING))
			if (recipe instanceof FillingRecipe fillingRecipe && fillingRecipe.matches(input, world))
				matching.add(fillingRecipe);
		return matching;
	}

	public static boolean canItemBeFilled(Level world, ItemStack stack) {
		SingleRecipeInput input = new SingleRecipeInput(stack);

		if (!recipesFor(world, input).isEmpty())
			return true;
		return GenericItemFilling.canItemBeFilled(world, stack);
	}

	public static int getRequiredAmountForItem(Level world, ItemStack stack, FluidStack availableFluid) {
		SingleRecipeInput input = new SingleRecipeInput(stack);

		for (FillingRecipe fillingRecipe : recipesFor(world, input)) {
			SizedFluidIngredient requiredFluid = fillingRecipe.getRequiredFluid();
			if (requiredFluid.ingredient().test(availableFluid))
				return requiredFluid.amount();
		}
		return GenericItemFilling.getRequiredAmountForItem(world, stack, availableFluid);
	}

	public static ItemStack fillItem(Level level, int requiredAmount, ItemStack stack, FluidStack availableFluid) {
		FluidStack toFill = availableFluid.copy();
		toFill.setAmount(requiredAmount);

		SingleRecipeInput input = new SingleRecipeInput(stack);

		FillingRecipe fillingRecipe = null;
		for (FillingRecipe fr : recipesFor(level, input)) {
			if (fr.getRequiredFluid().test(toFill)) {
				fillingRecipe = fr;
				break;
			}
		}

		if (fillingRecipe != null) {
			List<ItemStack> results = fillingRecipe.rollResults(level.getRandom());
			availableFluid.shrink(requiredAmount);
			stack.shrink(1);
			return results.isEmpty() ? ItemStack.EMPTY : results.get(0);
		}

		return GenericItemFilling.fillItem(level, requiredAmount, stack, availableFluid);
	}

}
