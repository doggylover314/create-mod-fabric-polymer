package dev.createpolymer.processing.basin;

import java.util.ArrayList;
import java.util.List;

import dev.createpolymer.processing.recipe.BasinRecipe;
import dev.createpolymer.util.ItemSlots;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Ported from Create's BasinRecipe.match/apply (MIT, The Create Team), items only.
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
		if (recipe instanceof BasinRecipe basinRecipe && !basinRecipe.getRequiredHeat().isMetWithoutHeat())
			return false;

		List<Ingredient> ingredients = ingredientsOf(recipe);
		if (ingredients.isEmpty())
			return false;

		// Simulate on a copy first, then do it for real.
		ItemSlots simulated = basin.inputInventory.copy();
		List<ItemStack> consumed = new ArrayList<>();
		if (!extractIngredients(simulated, ingredients, consumed))
			return false;

		List<ItemStack> outputs = new ArrayList<>();
		if (recipe instanceof BasinRecipe basinRecipe) {
			outputs.addAll(basinRecipe.rollResults(basin.getLevel().getRandom()));
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

		if (!basin.acceptOutputs(outputs, true))
			return false;
		if (test)
			return true;

		extractIngredients(basin.inputInventory, ingredients, new ArrayList<>());
		basin.acceptOutputs(outputs, false);
		basin.notifyChangeOfContents();
		basin.setChanged();
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
