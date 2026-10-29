package dev.createpolymer.processing.recipe;

import java.util.List;
import java.util.Set;

import dev.createpolymer.processing.sequenced.IAssemblyRecipe;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModRecipeTypes;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;

/** Ported from Create's PressingRecipe (MIT, The Create Team). */
public class PressingRecipe extends ProcessingRecipe<SingleRecipeInput> implements IAssemblyRecipe {

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
	public void addRequiredMachines(Set<ItemLike> list) {
		list.add(ModBlocks.MECHANICAL_PRESS);
	}

	@Override
	public void addAssemblyIngredients(List<Ingredient> list) {}

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
