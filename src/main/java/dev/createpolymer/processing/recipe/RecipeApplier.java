package dev.createpolymer.processing.recipe;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;

/** Ported from Create's RecipeApplier (MIT, The Create Team). */
public final class RecipeApplier {
	private RecipeApplier() {}

	public static void applyRecipeOn(ItemEntity entity, ProcessingRecipe<?> recipe, boolean returnProcessingRemainder) {
		List<ItemStack> stacks = applyRecipeOn(entity.level(), entity.getItem(), recipe, returnProcessingRemainder);
		if (stacks.isEmpty()) {
			entity.discard();
			return;
		}
		entity.setItem(stacks.removeFirst());
		for (ItemStack additional : stacks) {
			ItemEntity entityIn = new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), additional);
			entityIn.setDeltaMovement(entity.getDeltaMovement());
			entity.level().addFreshEntity(entityIn);
		}
	}

	public static List<ItemStack> applyRecipeOn(Level level, ItemStack stackIn, ProcessingRecipe<?> recipe, boolean returnProcessingRemainder) {
		List<ItemStack> stacks = new ArrayList<>();
		ItemStackTemplate remainder = stackIn.getItem().getCraftingRemainder();
		for (int i = 0; i < stackIn.getCount(); i++) {
			for (ItemStack stack : recipe.rollResults(level.getRandom())) {
				for (ItemStack previouslyRolled : stacks) {
					if (stack.isEmpty())
						continue;
					if (!ItemStack.isSameItemSameComponents(stack, previouslyRolled))
						continue;
					int amount = Math.min(previouslyRolled.getMaxStackSize() - previouslyRolled.getCount(), stack.getCount());
					previouslyRolled.grow(amount);
					stack.shrink(amount);
				}
				if (!stack.isEmpty())
					stacks.add(stack);
			}
			if (returnProcessingRemainder && remainder != null)
				stacks.add(remainder.create());
		}
		return stacks;
	}
}
