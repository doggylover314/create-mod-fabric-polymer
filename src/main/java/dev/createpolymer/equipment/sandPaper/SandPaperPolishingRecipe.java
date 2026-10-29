package dev.createpolymer.equipment.sandPaper;

import java.util.List;
import java.util.Optional;

import dev.createpolymer.processing.recipe.ProcessingRecipe;
import dev.createpolymer.registry.feature.EquipmentRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Ported from Create's SandPaperPolishingRecipe (MIT, The Create Team). */
public class SandPaperPolishingRecipe extends ProcessingRecipe<SingleRecipeInput> {

	public SandPaperPolishingRecipe(Params params) {
		super(params);
	}

	@Override
	public boolean matches(SingleRecipeInput inv, Level worldIn) {
		if (inv.isEmpty() || getIngredients().isEmpty())
			return false;
		return getIngredients().getFirst()
			.test(inv.item());
	}

	@Override
	protected int getMaxInputCount() {
		return 1;
	}

	@Override
	protected int getMaxOutputCount() {
		return 1;
	}

	@Override
	public RecipeSerializer<SandPaperPolishingRecipe> getSerializer() {
		return EquipmentRegistry.SANDPAPER_POLISHING_SERIALIZER;
	}

	@Override
	public RecipeType<SandPaperPolishingRecipe> getType() {
		return EquipmentRegistry.SANDPAPER_POLISHING;
	}

	public static boolean canPolish(Level world, ItemStack stack) {
		return !getMatchingRecipes(world, stack).isEmpty();
	}

	public static ItemStack applyPolish(Level world, Vec3 position, ItemStack stack, ItemStack sandPaperStack) {
		List<RecipeHolder<SandPaperPolishingRecipe>> matchingRecipes = getMatchingRecipes(world, stack);
		if (!matchingRecipes.isEmpty())
			return matchingRecipes.get(0)
				.value()
				.assemble(new SingleRecipeInput(stack))
				.copy();
		return stack;
	}

	public static List<RecipeHolder<SandPaperPolishingRecipe>> getMatchingRecipes(Level world, ItemStack stack) {
		if (!(world instanceof ServerLevel serverLevel))
			return List.of();
		Optional<RecipeHolder<SandPaperPolishingRecipe>> recipe = serverLevel.recipeAccess()
			.getRecipeFor(EquipmentRegistry.SANDPAPER_POLISHING, new SingleRecipeInput(stack), serverLevel);
		return recipe.map(List::of)
			.orElse(List.of());
	}
}
