package dev.createpolymer.processing.sequenced;

import java.util.List;
import java.util.Set;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

/** Ported from Create's IAssemblyRecipe (MIT, The Create Team) without the client-side description and JEI parts. */
public interface IAssemblyRecipe {
	default boolean supportsAssembly() {
		return true;
	}

	void addRequiredMachines(Set<ItemLike> list);

	void addAssemblyIngredients(List<Ingredient> list);
}
