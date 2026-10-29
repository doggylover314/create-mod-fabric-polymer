package dev.createpolymer.processing.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;

/**
 * Ported from Create's ProcessingRecipe (MIT, The Create Team), item-only:
 * fluid ingredients/results are not supported in this port yet.
 */
public abstract class ProcessingRecipe<I extends RecipeInput> implements Recipe<I> {

	public record Params(List<Ingredient> ingredients, List<ProcessingOutput> results, int processingDuration,
						 HeatCondition requiredHeat) {
		public static final MapCodec<Params> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Ingredient.CODEC.listOf().fieldOf("ingredients").forGetter(Params::ingredients),
			ProcessingOutput.CODEC.listOf().fieldOf("results").forGetter(Params::results),
			Codec.INT.optionalFieldOf("processing_time", 0).forGetter(Params::processingDuration),
			HeatCondition.CODEC.optionalFieldOf("heat_requirement", HeatCondition.NONE).forGetter(Params::requiredHeat)
		).apply(i, Params::new));
	}

	protected Params params;
	private Supplier<ItemStack> forcedResult;

	protected ProcessingRecipe(Params params) {
		this.params = params;
	}

	public Params getParams() {
		return params;
	}

	protected abstract int getMaxInputCount();

	protected abstract int getMaxOutputCount();

	public List<String> validate() {
		List<String> errors = new ArrayList<>();
		if (params.ingredients().size() > getMaxInputCount())
			errors.add("Recipe has more item inputs (" + params.ingredients().size() + ") than supported (" + getMaxInputCount() + ").");
		if (params.results().size() > getMaxOutputCount())
			errors.add("Recipe has more item outputs (" + params.results().size() + ") than supported (" + getMaxOutputCount() + ").");
		return errors;
	}

	public static <R extends ProcessingRecipe<?>> MapCodec<R> codec(java.util.function.Function<Params, R> factory) {
		return Params.CODEC.xmap(factory, ProcessingRecipe::getParams).validate(recipe -> {
			List<String> errors = recipe.validate();
			if (errors.isEmpty())
				return DataResult.success(recipe);
			return DataResult.error(() -> String.join("\n", errors), recipe);
		});
	}

	public List<Ingredient> getIngredients() {
		return params.ingredients();
	}

	public List<ProcessingOutput> getRollableResults() {
		return params.results();
	}

	public int getProcessingDuration() {
		return params.processingDuration();
	}

	public HeatCondition getRequiredHeat() {
		return params.requiredHeat();
	}

	/** Sequenced assembly makes the first output of the recipe step the next transitional item (or the final result). */
	public void enforceNextResult(Supplier<ItemStack> stack) {
		forcedResult = stack;
	}

	public List<ItemStack> rollResults(RandomSource randomSource) {
		return rollResults(this.getRollableResults(), randomSource);
	}

	public List<ItemStack> rollResults(List<ProcessingOutput> rollableResults, RandomSource randomSource) {
		List<ItemStack> results = new ArrayList<>();
		for (int i = 0; i < rollableResults.size(); i++) {
			ProcessingOutput output = rollableResults.get(i);
			ItemStack stack = i == 0 && forcedResult != null ? forcedResult.get() : output.rollOutput(randomSource);
			if (!stack.isEmpty())
				results.add(stack);
		}
		return results;
	}

	/** Replaces one ingredient; sequenced assembly points the steps at its transitional item. */
	public void setIngredient(int index, Ingredient ingredient) {
		List<Ingredient> ingredients = new ArrayList<>(params.ingredients());
		ingredients.set(index, ingredient);
		params = new Params(List.copyOf(ingredients), params.results(), params.processingDuration(), params.requiredHeat());
	}

	public ItemStack getResultItem() {
		return getRollableResults().isEmpty() ? ItemStack.EMPTY : getRollableResults().getFirst().getStack();
	}

	@Override
	public ItemStack assemble(I input) {
		return getResultItem();
	}

	@Override
	public boolean isSpecial() {
		return true;
	}

	@Override
	public boolean showNotification() {
		return false;
	}

	@Override
	public String group() {
		return "processing";
	}

	@Override
	public PlacementInfo placementInfo() {
		return PlacementInfo.NOT_PLACEABLE;
	}

	@Override
	public RecipeBookCategory recipeBookCategory() {
		return RecipeBookCategories.CRAFTING_MISC;
	}
}
