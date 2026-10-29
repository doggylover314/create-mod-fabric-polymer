package dev.createpolymer.kinetics.crafter;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;

/**
 * Create subclasses CraftingInput to tell mechanical crafting apart from the crafting table. CraftingInput's
 * constructor is private in 26.3 and recipe types are separate anyway, so this only builds a regular CraftingInput.
 */
public final class MechanicalCraftingInput {
	private MechanicalCraftingInput() {}

	public static CraftingInput of(RecipeGridHandler.GroupedItems items) {
		List<ItemStack> list = new ArrayList<>(items.width * items.height);
		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;

		for (int y = 0; y < items.height; y++) {
			for (int x = 0; x < items.width; x++) {
				int xp = x + items.minX;
				int yp = y + items.minY;
				ItemStack stack = items.grid.get(Pair.of(xp, yp));
				if (stack == null || stack.isEmpty())
					continue;
				minX = Math.min(minX, xp);
				maxX = Math.max(maxX, xp);
				minY = Math.min(minY, yp);
				maxY = Math.max(maxY, yp);
			}
		}

		// nothing but covers: Create ends up with a bogus 2x2 of empty stacks here, which matches no recipe
		if (maxX < minX)
			return CraftingInput.EMPTY;

		int w = maxX - minX + 1;
		int h = maxY - minY + 1;

		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				ItemStack stack = items.grid.get(Pair.of(x + minX, maxY - y));
				list.add(stack == null ? ItemStack.EMPTY : stack.copy());
			}
		}

		return CraftingInput.of(w, h, list);
	}
}
