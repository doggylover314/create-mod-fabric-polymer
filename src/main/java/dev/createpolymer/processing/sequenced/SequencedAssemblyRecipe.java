package dev.createpolymer.processing.sequenced;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.createpolymer.processing.recipe.ProcessingOutput;
import dev.createpolymer.processing.recipe.ProcessingRecipe;
import dev.createpolymer.registry.feature.DeployerRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * Ported from Create's SequencedAssemblyRecipe and its serializer (MIT, The Create Team).
 * The serializer's codec is {@link #CODEC}; stream codecs and the client tooltip are not needed server-side.
 */
public class SequencedAssemblyRecipe implements Recipe<RecipeInput> {
	public static final MapCodec<SequencedAssemblyRecipe> CODEC = RecordCodecBuilder.<SequencedAssemblyRecipe>mapCodec(
		i -> i.group(
			Ingredient.CODEC.fieldOf("ingredient").forGetter(SequencedAssemblyRecipe::getIngredient),
			ProcessingOutput.CODEC.fieldOf("transitional_item").forGetter(r -> r.transitionalItem),
			SequencedRecipe.CODEC.listOf().fieldOf("sequence").forGetter(SequencedAssemblyRecipe::getSequence),
			ProcessingOutput.CODEC.listOf().fieldOf("results").forGetter(r -> r.resultPool),
			ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("loops", 1).forGetter(SequencedAssemblyRecipe::getLoops)
		).apply(i, (ingredient, transitionalItem, sequence, results, loops) -> {
			SequencedAssemblyRecipe recipe = new SequencedAssemblyRecipe();
			recipe.ingredient = ingredient;
			recipe.transitionalItem = transitionalItem;
			recipe.sequence.addAll(sequence);
			recipe.resultPool.addAll(results);
			recipe.loops = loops;

			for (int j = 0; j < recipe.sequence.size(); j++)
				sequence.get(j).initFromSequencedAssembly(recipe, j == 0);

			return recipe;
		})
	).validate(recipe -> {
		if (recipe.sequence.isEmpty())
			return DataResult.error(() -> "Sequenced assembly recipe needs at least one step");
		if (recipe.resultPool.isEmpty())
			return DataResult.error(() -> "Sequenced assembly recipe needs at least one result");
		return DataResult.success(recipe);
	});

	// recipe lists are rebuilt after a reload, which swaps the recipe manager
	private static RecipeManager cachedManager;
	private static List<RecipeHolder<SequencedAssemblyRecipe>> cachedRecipes = List.of();

	protected Ingredient ingredient;
	protected List<SequencedRecipe<?>> sequence;
	protected int loops;
	protected ProcessingOutput transitionalItem;

	public final List<ProcessingOutput> resultPool;

	public SequencedAssemblyRecipe() {
		sequence = new ArrayList<>();
		resultPool = new ArrayList<>();
		loops = 5;
	}

	@SuppressWarnings("unchecked")
	private static synchronized List<RecipeHolder<SequencedAssemblyRecipe>> getAllRecipes(ServerLevel level) {
		RecipeManager manager = level.recipeAccess();
		if (manager != cachedManager) {
			List<RecipeHolder<SequencedAssemblyRecipe>> all = new ArrayList<>();
			for (RecipeHolder<?> holder : manager.getRecipes())
				if (holder.value() instanceof SequencedAssemblyRecipe)
					all.add((RecipeHolder<SequencedAssemblyRecipe>) holder);
			cachedRecipes = all;
			cachedManager = manager;
		}
		return cachedRecipes;
	}

	public static <I extends RecipeInput, R extends ProcessingRecipe<I>> Optional<RecipeHolder<R>> getRecipe(Level world, I inv,
		RecipeType<R> type, Class<R> recipeClass) {
		return getRecipe(world, inv, type, recipeClass, r -> r.value().matches(inv, world));
	}

	public static <I extends RecipeInput, R extends ProcessingRecipe<I>> Optional<RecipeHolder<R>> getRecipe(Level world, I inv,
		RecipeType<R> type, Class<R> recipeClass, Predicate<? super RecipeHolder<R>> recipeFilter) {
		List<RecipeHolder<R>> list = getRecipes(world, inv.getItem(0), type, recipeClass, recipeFilter);

		if (!list.isEmpty()) {
			return Optional.of(list.getFirst());
		} else {
			return Optional.empty();
		}
	}

	public static <R extends ProcessingRecipe<?>> Optional<RecipeHolder<R>> getRecipe(Level level, ItemStack item,
		RecipeType<R> type, Class<R> recipeClass) {
		if (!(level instanceof ServerLevel serverLevel))
			return Optional.empty();
		for (RecipeHolder<SequencedAssemblyRecipe> sequencedAssemblyRecipe : getAllRecipes(serverLevel)) {
			Identifier id = sequencedAssemblyRecipe.id().identifier();
			if (!sequencedAssemblyRecipe.value().appliesTo(id, item))
				continue;
			SequencedRecipe<?> nextRecipe = sequencedAssemblyRecipe.value().getNextRecipe(item);
			ProcessingRecipe<?> recipe = nextRecipe.getRecipe();
			if (recipe.getType() != type || !recipeClass.isInstance(recipe))
				continue;
			recipe.enforceNextResult(() -> sequencedAssemblyRecipe.value().advance(id, item, level.getRandom()));
			return Optional.of(new RecipeHolder<>(sequencedAssemblyRecipe.id(), recipeClass.cast(recipe)));
		}
		return Optional.empty();
	}

	public static <R extends ProcessingRecipe<?>> List<RecipeHolder<R>> getRecipes(Level level, ItemStack item,
		RecipeType<R> type, Class<R> recipeClass, Predicate<? super RecipeHolder<R>> recipeFilter) {
		List<RecipeHolder<R>> result = new ArrayList<>();
		if (!(level instanceof ServerLevel serverLevel))
			return result;

		for (RecipeHolder<SequencedAssemblyRecipe> holder : getAllRecipes(serverLevel)) {
			Identifier id = holder.id().identifier();
			if (holder.value().appliesTo(id, item)) {
				ProcessingRecipe<?> recipe = holder.value().getNextRecipe(item).getRecipe();

				if (recipe.getType() == type && recipeClass.isInstance(recipe)) {
					recipe.enforceNextResult(() -> holder.value().advance(id, item, level.getRandom()));
					R castedRecipe = recipeClass.cast(recipe);
					RecipeHolder<R> h = new RecipeHolder<>(holder.id(), castedRecipe);
					if (recipeFilter.test(h))
						result.add(h);
				}
			}
		}

		return result;
	}

	private ItemStack advance(Identifier id, ItemStack input, RandomSource random) {
		int step = getStep(input);
		if ((step + 1) / sequence.size() >= loops)
			return rollResult(random);

		ItemStack advancedItem = getTransitionalItem().copyWithCount(1);
		SequencedAssembly sequencedAssembly = new SequencedAssembly(
			id,
			step + 1,
			(step + 1f) / (sequence.size() * loops)
		);
		advancedItem.set(DeployerRegistry.SEQUENCED_ASSEMBLY, sequencedAssembly);
		return advancedItem;
	}

	public int getLoops() {
		return loops;
	}

	private ItemStack rollResult(RandomSource random) {
		float totalWeight = 0;
		for (ProcessingOutput entry : resultPool)
			totalWeight += entry.chance();
		float number = random.nextFloat() * totalWeight;
		for (ProcessingOutput entry : resultPool) {
			number -= entry.chance();
			if (number < 0)
				return entry.getStack()
					.copy();
		}
		return ItemStack.EMPTY;
	}

	private boolean appliesTo(Identifier id, ItemStack input) {
		// First, check if the item is already in the middle of a sequenced assembly recipe
		SequencedAssembly assembly = input.get(DeployerRegistry.SEQUENCED_ASSEMBLY);
		if (assembly != null)
			return transitionalItem.item() == input.getItem() && assembly.id().equals(id);
		// Else it must be the first step in a new sequenced assembly recipe
		return ingredient.test(input);
	}

	private SequencedRecipe<?> getNextRecipe(ItemStack input) {
		return sequence.get(getStep(input) % sequence.size());
	}

	private int getStep(ItemStack input) {
		SequencedAssembly assembly = input.get(DeployerRegistry.SEQUENCED_ASSEMBLY);
		if (assembly == null)
			return 0;
		return assembly.step();
	}

	@Override
	public boolean matches(RecipeInput inv, Level level) {
		return false;
	}

	@Override
	public ItemStack assemble(RecipeInput input) {
		return ItemStack.EMPTY;
	}

	public ItemStack getResultItem() {
		return resultPool.getFirst().getStack();
	}

	public float getOutputChance() {
		float totalWeight = 0;
		for (ProcessingOutput entry : resultPool)
			totalWeight += entry.chance();
		return resultPool.getFirst().chance() / totalWeight;
	}

	@Override
	public RecipeSerializer<SequencedAssemblyRecipe> getSerializer() {
		return DeployerRegistry.SEQUENCED_ASSEMBLY_SERIALIZER;
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

	@Override
	public RecipeType<SequencedAssemblyRecipe> getType() {
		return DeployerRegistry.SEQUENCED_ASSEMBLY_TYPE;
	}

	public Ingredient getIngredient() {
		return ingredient;
	}

	public List<SequencedRecipe<?>> getSequence() {
		return sequence;
	}

	public ItemStack getTransitionalItem() {
		return transitionalItem.getStack();
	}

	public record SequencedAssembly(Identifier id, int step, float progress) {
		public static final Codec<SequencedAssembly> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("id").forGetter(SequencedAssembly::id),
			Codec.INT.fieldOf("step").forGetter(SequencedAssembly::step),
			Codec.FLOAT.fieldOf("progress").forGetter(SequencedAssembly::progress)
		).apply(i, SequencedAssembly::new));
	}
}
