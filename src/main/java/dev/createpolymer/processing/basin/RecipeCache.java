package dev.createpolymer.processing.basin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Pre-filtered recipe lists per machine type, rebuilt after /reload. Stands in for Create's RecipeFinder. */
public final class RecipeCache {
	private static final Map<Object, List<Recipe<?>>> CACHE = new HashMap<>();

	private RecipeCache() {}

	public static synchronized List<Recipe<?>> get(Object key, ServerLevel level, Predicate<RecipeHolder<?>> filter) {
		return CACHE.computeIfAbsent(key, k -> {
			List<Recipe<?>> list = new ArrayList<>();
			for (RecipeHolder<?> holder : level.recipeAccess().getRecipes())
				if (filter.test(holder))
					list.add(holder.value());
			return list;
		});
	}

	public static synchronized void invalidate() {
		CACHE.clear();
	}
}
