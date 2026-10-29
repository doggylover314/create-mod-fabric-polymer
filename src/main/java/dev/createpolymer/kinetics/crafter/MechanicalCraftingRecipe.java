package dev.createpolymer.kinetics.crafter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.createpolymer.registry.feature.CrafterRegistry;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

/**
 * Create's shaped recipe for the mechanical crafter. Unlike Create's version this is not a ShapedRecipe subclass:
 * in 26.3 a ShapedRecipe is a CraftingRecipe (so its type would have to be {@code minecraft:crafting}) and its
 * pattern codec refuses anything over 3x3, while mechanical crafting recipes can be as large as the crafter grid.
 */
public class MechanicalCraftingRecipe implements Recipe<CraftingInput> {
	private final Recipe.CommonInfo commonInfo;
	private final CraftingRecipe.CraftingBookInfo bookInfo;
	private final Map<Character, Ingredient> key;
	private final List<String> patternRows;
	private final ShapedRecipePattern pattern;
	private final ItemStackTemplate result;
	private final boolean acceptMirrored;
	private PlacementInfo placementInfo;

	public MechanicalCraftingRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo,
		Map<Character, Ingredient> key, List<String> patternRows, ShapedRecipePattern pattern, ItemStackTemplate result,
		boolean acceptMirrored) {
		this.commonInfo = commonInfo;
		this.bookInfo = bookInfo;
		this.key = key;
		this.patternRows = patternRows;
		this.pattern = pattern;
		this.result = result;
		this.acceptMirrored = acceptMirrored;
	}

	public int getWidth() {
		return pattern.width();
	}

	public int getHeight() {
		return pattern.height();
	}

	public List<Optional<Ingredient>> getIngredients() {
		return pattern.ingredients();
	}

	@Override
	public boolean matches(CraftingInput input, Level worldIn) {
		if (acceptsMirrored())
			return pattern.matches(input);

		// From ShapedRecipePattern except the symmetry
		for (int i = 0; i <= input.width() - this.getWidth(); ++i)
			for (int j = 0; j <= input.height() - this.getHeight(); ++j)
				if (this.matchesSpecific(input, i, j))
					return true;
		return false;
	}

	// From ShapedRecipe
	private boolean matchesSpecific(CraftingInput input, int offsetX, int offsetY) {
		List<Optional<Ingredient>> ingredients = getIngredients();
		int width = getWidth();
		int height = getHeight();
		for (int i = 0; i < input.width(); ++i) {
			for (int j = 0; j < input.height(); ++j) {
				int k = i - offsetX;
				int l = j - offsetY;
				Optional<Ingredient> ingredient = Optional.empty();
				if (k >= 0 && l >= 0 && k < width && l < height)
					ingredient = ingredients.get(k + l * width);
				if (!Ingredient.testOptionalIngredient(ingredient, input.getItem(i + j * input.width())))
					return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		return result.create();
	}

	@Override
	public boolean isSpecial() {
		return true;
	}

	@Override
	public boolean showNotification() {
		return commonInfo.showNotification();
	}

	@Override
	public String group() {
		return bookInfo.group();
	}

	@Override
	public PlacementInfo placementInfo() {
		if (placementInfo == null)
			placementInfo = PlacementInfo.createFromOptionals(pattern.ingredients());
		return placementInfo;
	}

	@Override
	public RecipeBookCategory recipeBookCategory() {
		return RecipeBookCategories.CRAFTING_MISC;
	}

	@Override
	public RecipeType<MechanicalCraftingRecipe> getType() {
		return CrafterRegistry.MECHANICAL_CRAFTING;
	}

	@Override
	public RecipeSerializer<MechanicalCraftingRecipe> getSerializer() {
		return CrafterRegistry.MECHANICAL_CRAFTING_SERIALIZER;
	}

	public boolean acceptsMirrored() {
		return acceptMirrored;
	}

	public static class Serializer {
		private static final Codec<Character> SYMBOL_CODEC = Codec.STRING.comapFlatMap(symbol -> {
			if (symbol.length() != 1)
				return DataResult.error(() -> "Invalid key entry: '" + symbol + "' is an invalid symbol (must be 1 character only).");
			return " ".equals(symbol) ? DataResult.error(() -> "Invalid key entry: ' ' is a reserved symbol.")
				: DataResult.success(symbol.charAt(0));
		}, String::valueOf);

		// ShapedRecipePattern's own codec stops at 3x3
		private static final Codec<List<String>> PATTERN_CODEC = Codec.STRING.listOf().comapFlatMap(strings -> {
			if (strings.isEmpty())
				return DataResult.error(() -> "Invalid pattern: empty pattern not allowed");
			int firstLength = strings.getFirst().length();
			for (String line : strings)
				if (firstLength != line.length())
					return DataResult.error(() -> "Invalid pattern: each row must be the same width");
			return DataResult.success(strings);
		}, java.util.function.Function.identity());

		private record Data(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo,
			Map<Character, Ingredient> key, List<String> pattern, ItemStackTemplate result, boolean acceptMirrored) {

			DataResult<MechanicalCraftingRecipe> toRecipe() {
				try {
					return DataResult.success(new MechanicalCraftingRecipe(commonInfo, bookInfo, key, pattern,
						ShapedRecipePattern.of(key, pattern), result, acceptMirrored));
				} catch (RuntimeException e) {
					return DataResult.error(() -> "Invalid mechanical crafting recipe: " + e.getMessage());
				}
			}

			static Data of(MechanicalCraftingRecipe recipe) {
				return new Data(recipe.commonInfo, recipe.bookInfo, new HashMap<>(recipe.key), recipe.patternRows,
					recipe.result, recipe.acceptMirrored);
			}
		}

		public static final MapCodec<MechanicalCraftingRecipe> CODEC = RecordCodecBuilder.<Data>mapCodec(instance -> instance.group(
			Recipe.CommonInfo.MAP_CODEC.forGetter(Data::commonInfo),
			CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(Data::bookInfo),
			ExtraCodecs.strictUnboundedMap(SYMBOL_CODEC, Ingredient.CODEC).fieldOf("key").forGetter(Data::key),
			PATTERN_CODEC.fieldOf("pattern").forGetter(Data::pattern),
			ItemStackTemplate.CODEC.fieldOf("result").forGetter(Data::result),
			Codec.BOOL.fieldOf("accept_mirrored").forGetter(Data::acceptMirrored)
		).apply(instance, Data::new))
			.flatXmap(Data::toRecipe, recipe -> DataResult.success(Data.of(recipe)));
	}
}
