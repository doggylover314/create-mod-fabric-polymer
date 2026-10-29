package dev.createpolymer.kinetics.deployer;

import java.util.Optional;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;

/**
 * Ported from Create's DeployerRecipeSearchEvent (MIT, The Create Team). Create posts it on the NeoForge bus; here
 * listeners register with {@link #EVENT}.
 */
public class DeployerRecipeSearchEvent {
	public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class,
		listeners -> event -> {
			for (Listener listener : listeners)
				listener.onSearch(event);
		});

	@FunctionalInterface
	public interface Listener {
		void onSearch(DeployerRecipeSearchEvent event);
	}

	private final DeployerBlockEntity blockEntity;
	private final ItemApplicationInput inventory;
	@Nullable
	RecipeHolder<? extends Recipe<? extends RecipeInput>> recipe = null;
	private int maxPriority = 0;
	private boolean canceled;

	public DeployerRecipeSearchEvent(DeployerBlockEntity blockEntity, ItemApplicationInput inventory) {
		this.blockEntity = blockEntity;
		this.inventory = inventory;
	}

	public DeployerBlockEntity getBlockEntity() {
		return blockEntity;
	}

	public ItemApplicationInput getInventory() {
		return inventory;
	}

	public boolean isCanceled() {
		return canceled;
	}

	public void setCanceled(boolean canceled) {
		this.canceled = canceled;
	}

	// lazyness to not scan for recipes that aren't selected
	public boolean shouldAddRecipeWithPriority(int priority) {
		return !isCanceled() && priority > maxPriority;
	}

	@Nullable
	public RecipeHolder<? extends Recipe<? extends RecipeInput>> getRecipe() {
		if (isCanceled())
			return null;
		return recipe;
	}

	public void addRecipe(Supplier<Optional<? extends RecipeHolder<? extends Recipe<? extends RecipeInput>>>> recipeSupplier,
		int priority) {
		if (!shouldAddRecipeWithPriority(priority))
			return;
		recipeSupplier.get()
			.ifPresent(newRecipe -> {
				this.recipe = newRecipe;
				maxPriority = priority;
			});
	}
}
