package dev.createpolymer.logistics.stockTicker;

import java.util.List;

import dev.createpolymer.logistics.BigItemStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;

public class CraftableBigItemStack extends BigItemStack {

	public Recipe<?> recipe;

	/** {@code stack} is the recipe's result: its count is what one craft makes. */
	public CraftableBigItemStack(ItemStack stack, Recipe<?> recipe) {
		super(stack);
		this.recipe = recipe;
	}

	public List<Ingredient> getIngredients() {
		return recipe.placementInfo()
			.ingredients();
	}

	public int getOutputCount(Level level) {
		return Math.max(1, stack.getCount());
	}

}
