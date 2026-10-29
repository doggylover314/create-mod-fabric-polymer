package dev.createpolymer.processing.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.createpolymer.foundation.fluid.FluidStack;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;

/**
 * Ported from Create's ProcessingRecipe (MIT, The Create Team).
 * Item and fluid ingredients share the "ingredients" list and item and fluid results the "results" list, as in Create:
 * fluid ingredients are objects ({@link SizedFluidIngredient}) and fluid results have an "amount"
 * ({@link FluidStack#CODEC}).
 */
public abstract class ProcessingRecipe<I extends RecipeInput> implements Recipe<I> {

	public record Params(List<Ingredient> ingredients, List<ProcessingOutput> results, int processingDuration,
						 HeatCondition requiredHeat, List<SizedFluidIngredient> fluidIngredients,
						 List<FluidStack> fluidResults) {

		private static final Codec<List<Either<SizedFluidIngredient, Ingredient>>> INGREDIENTS_CODEC =
			Codec.either(SizedFluidIngredient.CODEC, Ingredient.CODEC).listOf();
		private static final Codec<List<Either<FluidStack, ProcessingOutput>>> RESULTS_CODEC =
			Codec.either(FluidStack.CODEC, ProcessingOutput.CODEC).listOf();

		public static final MapCodec<Params> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			INGREDIENTS_CODEC.fieldOf("ingredients").forGetter(Params::allIngredients),
			RESULTS_CODEC.fieldOf("results").forGetter(Params::allResults),
			Codec.INT.optionalFieldOf("processing_time", 0).forGetter(Params::processingDuration),
			HeatCondition.CODEC.optionalFieldOf("heat_requirement", HeatCondition.NONE).forGetter(Params::requiredHeat)
		).apply(i, Params::of));

		public Params(List<Ingredient> ingredients, List<ProcessingOutput> results, int processingDuration,
					  HeatCondition requiredHeat) {
			this(ingredients, results, processingDuration, requiredHeat, List.of(), List.of());
		}

		private static Params of(List<Either<SizedFluidIngredient, Ingredient>> allIngredients,
								 List<Either<FluidStack, ProcessingOutput>> allResults, int processingDuration,
								 HeatCondition requiredHeat) {
			List<Ingredient> ingredients = new ArrayList<>();
			List<SizedFluidIngredient> fluidIngredients = new ArrayList<>();
			allIngredients.forEach(either -> either.ifRight(ingredients::add).ifLeft(fluidIngredients::add));
			List<ProcessingOutput> results = new ArrayList<>();
			List<FluidStack> fluidResults = new ArrayList<>();
			allResults.forEach(either -> either.ifRight(results::add).ifLeft(fluidResults::add));
			return new Params(List.copyOf(ingredients), List.copyOf(results), processingDuration, requiredHeat,
				List.copyOf(fluidIngredients), List.copyOf(fluidResults));
		}

		private List<Either<SizedFluidIngredient, Ingredient>> allIngredients() {
			List<Either<SizedFluidIngredient, Ingredient>> all = new ArrayList<>();
			ingredients.forEach(ingredient -> all.add(Either.right(ingredient)));
			fluidIngredients.forEach(ingredient -> all.add(Either.left(ingredient)));
			return all;
		}

		private List<Either<FluidStack, ProcessingOutput>> allResults() {
			List<Either<FluidStack, ProcessingOutput>> all = new ArrayList<>();
			results.forEach(result -> all.add(Either.right(result)));
			fluidResults.forEach(result -> all.add(Either.left(result)));
			return all;
		}
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

	protected int getMaxFluidInputCount() {
		return 0;
	}

	protected int getMaxFluidOutputCount() {
		return 0;
	}

	public List<String> validate() {
		List<String> errors = new ArrayList<>();
		int ingredientCount = params.ingredients().size();
		int outputCount = params.results().size();

		if (ingredientCount > getMaxInputCount())
			errors.add("Recipe has more item inputs (" + ingredientCount + ") than supported (" + getMaxInputCount() + ").");

		if (outputCount > getMaxOutputCount())
			errors.add("Recipe has more item outputs (" + outputCount + ") than supported (" + getMaxOutputCount() + ").");

		ingredientCount = params.fluidIngredients().size();
		outputCount = params.fluidResults().size();

		if (ingredientCount > getMaxFluidInputCount())
			errors.add("Recipe has more fluid inputs (" + ingredientCount + ") than supported ("
				+ getMaxFluidInputCount() + ").");

		if (outputCount > getMaxFluidOutputCount())
			errors.add("Recipe has more fluid outputs (" + outputCount + ") than supported ("
				+ getMaxFluidOutputCount() + ").");

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

	public List<SizedFluidIngredient> getFluidIngredients() {
		return params.fluidIngredients();
	}

	public List<ProcessingOutput> getRollableResults() {
		return params.results();
	}

	public List<FluidStack> getFluidResults() {
		return params.fluidResults();
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
		params = new Params(List.copyOf(ingredients), params.results(), params.processingDuration(), params.requiredHeat(),
			params.fluidIngredients(), params.fluidResults());
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
