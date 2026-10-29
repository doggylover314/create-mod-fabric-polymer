package dev.createpolymer.processing.sequenced;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import dev.createpolymer.kinetics.deployer.ItemApplicationRecipe;
import dev.createpolymer.processing.recipe.ProcessingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

/** Ported from Create's SequencedRecipe (MIT, The Create Team). */
public class SequencedRecipe<T extends ProcessingRecipe<?>> {
	public static final Codec<SequencedRecipe<?>> CODEC = Recipe.DIRECT_CODEC
		.comapFlatMap(recipe -> recipe instanceof ProcessingRecipe<?> processing && recipe instanceof IAssemblyRecipe
				? DataResult.success(new SequencedRecipe<>(processing))
				: DataResult.error(() -> recipe.getClass().getSimpleName() + " is not supported in Sequenced Assembly"),
			SequencedRecipe::getRecipe
		);

	private final T wrapped;

	public SequencedRecipe(T wrapped) {
		this.wrapped = wrapped;
	}

	public IAssemblyRecipe getAsAssemblyRecipe() {
		return (IAssemblyRecipe) wrapped;
	}

	public T getRecipe() {
		return wrapped;
	}

	void initFromSequencedAssembly(SequencedAssemblyRecipe parent, boolean isFirst) {
		if (getAsAssemblyRecipe().supportsAssembly()) {
			// item components are not bound while recipes are decoded, so no ItemStack can be created here
			wrapped.setIngredient(0, Ingredient.of(parent.transitionalItem.item()));
			// Create replaces the ingredient with a compound of both. Tags are not bound while recipes are decoded, so
			// the item application recipe tests the first step's ingredient as an alternative instead.
			if (isFirst && wrapped instanceof ItemApplicationRecipe itemApplication)
				itemApplication.setAlternativeProcessedItem(parent.getIngredient());
		}
	}
}
