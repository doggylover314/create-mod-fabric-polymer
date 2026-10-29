package dev.createpolymer.processing.recipe;

import dev.createpolymer.registry.ModRecipeTypes;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * Ported from Create's BasinRecipe / MixingRecipe / CompactingRecipe (MIT, The Create Team).
 * Matching is done by {@link dev.createpolymer.processing.basin.BasinRecipeLogic}, not by {@link #matches}.
 */
public class BasinRecipe extends ProcessingRecipe<RecipeInput> {
	private final boolean compacting;

	private BasinRecipe(Params params, boolean compacting) {
		super(params);
		this.compacting = compacting;
	}

	public static BasinRecipe mixing(Params params) {
		return new BasinRecipe(params, false);
	}

	public static BasinRecipe compacting(Params params) {
		return new BasinRecipe(params, true);
	}

	@Override
	protected int getMaxInputCount() {
		return 64;
	}

	@Override
	protected int getMaxOutputCount() {
		return 4;
	}

	@Override
	public boolean matches(RecipeInput input, Level level) {
		return false;
	}

	@Override
	public RecipeSerializer<BasinRecipe> getSerializer() {
		return compacting ? ModRecipeTypes.COMPACTING_SERIALIZER : ModRecipeTypes.MIXING_SERIALIZER;
	}

	@Override
	public RecipeType<BasinRecipe> getType() {
		return compacting ? ModRecipeTypes.COMPACTING : ModRecipeTypes.MIXING;
	}
}
