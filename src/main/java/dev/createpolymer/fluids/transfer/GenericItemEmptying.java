package dev.createpolymer.fluids.transfer;

import java.util.List;
import java.util.Optional;

import dev.createpolymer.fluids.potion.PotionFluidHandler;
import dev.createpolymer.foundation.fluid.FluidItemHandlers;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.IFluidHandler.FluidAction;
import dev.createpolymer.foundation.fluid.IFluidHandlerItem;
import dev.createpolymer.processing.basin.RecipeCache;
import dev.createpolymer.registry.feature.TanksRegistry;
import dev.createpolymer.util.Pair;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

public class GenericItemEmptying {

	/** The emptying recipe for the item, if there is one. Create's AllRecipeTypes.EMPTYING.find. */
	public static Optional<EmptyingRecipe> findRecipe(Level level, ItemStack stack) {
		if (!(level instanceof ServerLevel serverLevel))
			return Optional.empty();
		SingleRecipeInput input = new SingleRecipeInput(stack);
		for (var recipe : RecipeCache.get(TanksRegistry.EMPTYING, serverLevel, h -> h.value().getType() == TanksRegistry.EMPTYING))
			if (recipe instanceof EmptyingRecipe emptyingRecipe && emptyingRecipe.matches(input, level))
				return Optional.of(emptyingRecipe);
		return Optional.empty();
	}

	public static boolean canItemBeEmptied(Level world, ItemStack stack) {
		if (PotionFluidHandler.isPotionItem(stack))
			return true;

		if (findRecipe(world, stack).isPresent())
			return true;

		Optional<IFluidHandlerItem> capability = FluidItemHandlers.get(stack.copyWithCount(1));
		if (capability.isEmpty())
			return false;
		for (int i = 0; i < capability.get().getTanks(); i++) {
			if (capability.get().getFluidInTank(i)
				.getAmount() > 0)
				return true;
		}
		return false;
	}

	public static Pair<FluidStack, ItemStack> emptyItem(Level level, ItemStack stack, boolean simulate) {
		FluidStack resultingFluid = FluidStack.EMPTY;
		ItemStack resultingItem = ItemStack.EMPTY;

		if (PotionFluidHandler.isPotionItem(stack))
			return PotionFluidHandler.emptyPotion(stack, simulate);

		Optional<EmptyingRecipe> recipe = findRecipe(level, stack);
		if (recipe.isPresent()) {
			EmptyingRecipe emptyingRecipe = recipe.get();
			List<ItemStack> results = emptyingRecipe.rollResults(level.getRandom());
			if (!simulate)
				stack.shrink(1);
			resultingItem = results.isEmpty() ? ItemStack.EMPTY : results.get(0);
			resultingFluid = emptyingRecipe.getResultingFluid();
			return Pair.of(resultingFluid, resultingItem);
		}

		ItemStack split = stack.copy();
		split.setCount(1);
		Optional<IFluidHandlerItem> capability = FluidItemHandlers.get(split);
		if (capability.isEmpty())
			return Pair.of(resultingFluid, resultingItem);
		resultingFluid = capability.get().drain(1000, simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE);
		resultingItem = capability.get().getContainer()
			.copy();
		if (!simulate)
			stack.shrink(1);

		return Pair.of(resultingFluid, resultingItem);
	}

}
