package dev.createpolymer.processing.basin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.createpolymer.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour.TankSegment;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.IFluidHandler;
import dev.createpolymer.processing.burner.BlazeBurnerBlock.HeatLevel;
import dev.createpolymer.processing.recipe.BasinRecipe;
import dev.createpolymer.processing.recipe.SizedFluidIngredient;
import dev.createpolymer.util.ItemSlots;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Ported from Create's BasinRecipe.match/apply (MIT, The Create Team).
 * Works for Create's mixing/compacting recipes and for vanilla crafting recipes
 * (shapeless in the mixer, 2x2/3x3 squares in the press).
 */
public final class BasinRecipeLogic {
	private BasinRecipeLogic() {}

	public static List<Ingredient> ingredientsOf(Recipe<?> recipe) {
		if (recipe instanceof BasinRecipe basinRecipe)
			return basinRecipe.getIngredients();
		return recipe.placementInfo().ingredients();
	}

	public static boolean match(BasinBlockEntity basin, Recipe<?> recipe) {
		return apply(basin, recipe, true);
	}

	public static boolean apply(BasinBlockEntity basin, Recipe<?> recipe) {
		return apply(basin, recipe, false);
	}

	private static boolean apply(BasinBlockEntity basin, Recipe<?> recipe, boolean test) {
		HeatLevel heat = basin.getHeatLevel();
		if (recipe instanceof BasinRecipe basinRecipe && !basinRecipe.getRequiredHeat().testBlazeBurner(heat))
			return false;

		List<Ingredient> ingredients = ingredientsOf(recipe);
		List<SizedFluidIngredient> fluidIngredients =
			recipe instanceof BasinRecipe basinRecipe ? basinRecipe.getFluidIngredients() : Collections.emptyList();
		if (ingredients.isEmpty() && fluidIngredients.isEmpty())
			return false;

		// Simulate on a copy first, then do it for real.
		ItemSlots simulated = basin.inputInventory.copy();
		List<ItemStack> consumed = new ArrayList<>();
		if (!extractIngredients(simulated, ingredients, consumed))
			return false;

		IFluidHandler availableFluids = basin.getFluidCapability();
		if (!extractFluids(basin, availableFluids, fluidIngredients, true))
			return false;

		List<ItemStack> outputs = new ArrayList<>();
		List<FluidStack> outputFluids = new ArrayList<>();
		if (recipe instanceof BasinRecipe basinRecipe) {
			outputs.addAll(basinRecipe.rollResults(basin.getLevel().getRandom()));
			for (FluidStack fluidStack : basinRecipe.getFluidResults())
				if (!fluidStack.isEmpty())
					outputFluids.add(fluidStack);
		} else if (recipe instanceof CraftingRecipe craftingRecipe) {
			ItemStack result = craftingRecipe.assemble(CraftingInput.EMPTY);
			if (result.isEmpty())
				return false;
			outputs.add(result);
		} else {
			return false;
		}
		for (ItemStack stack : consumed) {
			ItemStackTemplate remainder = stack.getItem().getCraftingRemainder();
			if (remainder != null)
				outputs.add(remainder.create());
		}

		if (!basin.acceptOutputs(outputs, outputFluids, true))
			return false;
		if (test)
			return true;

		extractIngredients(basin.inputInventory, ingredients, new ArrayList<>());
		extractFluids(basin, availableFluids, fluidIngredients, false);
		basin.acceptOutputs(outputs, outputFluids, false);
		basin.notifyChangeOfContents();
		basin.setChanged();
		return true;
	}

	/** Create's fluid half of BasinRecipe.apply: takes the fluid ingredients out of the tanks, or just checks they are there. */
	private static boolean extractFluids(BasinBlockEntity basin, IFluidHandler availableFluids,
										 List<SizedFluidIngredient> fluidIngredients, boolean simulate) {
		int[] extractedFluidsFromTank = new int[availableFluids.getTanks()];
		boolean fluidsAffected = false;

		FluidIngredients:
		for (SizedFluidIngredient fluidIngredient : fluidIngredients) {
			int amountRequired = fluidIngredient.amount();

			for (int tank = 0; tank < availableFluids.getTanks(); tank++) {
				FluidStack fluidStack = availableFluids.getFluidInTank(tank);
				if (simulate && fluidStack.getAmount() <= extractedFluidsFromTank[tank])
					continue;
				if (!fluidIngredient.test(fluidStack))
					continue;
				int drainedAmount = Math.min(amountRequired, fluidStack.getAmount());
				if (!simulate) {
					fluidStack.shrink(drainedAmount);
					fluidsAffected = true;
				}
				amountRequired -= drainedAmount;
				if (amountRequired != 0)
					continue;
				extractedFluidsFromTank[tank] += drainedAmount;
				continue FluidIngredients;
			}

			// something wasn't found
			return false;
		}

		if (fluidsAffected) {
			basin.getTanks().forEach(tanks -> tanks.forEach(TankSegment::onFluidStackChanged));
		}
		return true;
	}

	private static boolean extractIngredients(ItemSlots inv, List<Ingredient> ingredients, List<ItemStack> consumed) {
		Ingredients:
		for (Ingredient ingredient : ingredients) {
			for (int slot = 0; slot < inv.size(); slot++) {
				ItemStack inSlot = inv.get(slot);
				if (inSlot.isEmpty() || !ingredient.test(inSlot))
					continue;
				consumed.add(inv.extract(slot, 1, false));
				continue Ingredients;
			}
			return false;
		}
		return true;
	}
}
