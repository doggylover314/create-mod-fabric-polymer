package dev.createpolymer.processing.basin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.createpolymer.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Ported from Create's BasinOperatingBlockEntity (MIT, The Create Team). */
public abstract class BasinOperatingBlockEntity extends KineticBlockEntity {

	private boolean basinUpdateScheduled = true;
	protected Recipe<?> currentRecipe;

	public BasinOperatingBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
		super(typeIn, pos, state);
	}

	/** Create's DeferralBehaviour: run the basin check on the next tick. */
	public void scheduleBasinUpdate() {
		basinUpdateScheduled = true;
	}

	@Override
	public void onSpeedChanged(float prevSpeed) {
		super.onSpeedChanged(prevSpeed);
		scheduleBasinUpdate();
	}

	@Override
	public void tick() {
		super.tick();
		if (basinUpdateScheduled && level != null && !level.isClientSide()) {
			basinUpdateScheduled = false;
			updateBasin();
		}
	}

	protected boolean updateBasin() {
		if (!isSpeedRequirementFulfilled())
			return true;
		if (getSpeed() == 0)
			return true;
		if (isRunning())
			return true;
		if (level == null || level.isClientSide())
			return true;
		Optional<BasinBlockEntity> basin = getBasin();
		if (basin.filter(BasinBlockEntity::canContinueProcessing).isEmpty())
			return true;

		List<Recipe<?>> recipes = getMatchingRecipes();
		if (recipes.isEmpty())
			return true;
		currentRecipe = recipes.getFirst();
		startProcessingBasin();
		sendData();
		return true;
	}

	protected abstract boolean isRunning();

	public void startProcessingBasin() {
	}

	public boolean continueWithPreviousRecipe() {
		return true;
	}

	protected boolean matchBasinRecipe(Recipe<?> recipe) {
		if (recipe == null)
			return false;
		Optional<BasinBlockEntity> basin = getBasin();
		return basin.isPresent() && BasinRecipeLogic.match(basin.get(), recipe);
	}

	protected void applyBasinRecipe() {
		if (currentRecipe == null)
			return;

		Optional<BasinBlockEntity> optionalBasin = getBasin();
		if (optionalBasin.isEmpty())
			return;
		BasinBlockEntity basin = optionalBasin.get();
		boolean wasEmpty = basin.canContinueProcessing();
		if (!BasinRecipeLogic.apply(basin, currentRecipe))
			return;

		// Continue mixing
		if (wasEmpty && matchBasinRecipe(currentRecipe)) {
			continueWithPreviousRecipe();
			sendData();
		}

		basin.notifyChangeOfContents();
	}

	protected List<Recipe<?>> getMatchingRecipes() {
		Optional<BasinBlockEntity> basin = getBasin();
		if (basin.isEmpty() || basin.get().isEmpty() || !(level instanceof ServerLevel serverLevel))
			return new ArrayList<>();

		List<Recipe<?>> list = new ArrayList<>();
		for (Recipe<?> r : RecipeCache.get(getRecipeCacheKey(), serverLevel, this::matchStaticFilters))
			if (matchBasinRecipe(r))
				list.add(r);

		list.sort((r1, r2) -> BasinRecipeLogic.ingredientsOf(r2).size() - BasinRecipeLogic.ingredientsOf(r1).size());
		return list;
	}

	protected abstract void onBasinRemoved();

	protected Optional<BasinBlockEntity> getBasin() {
		if (level == null)
			return Optional.empty();
		BlockEntity basinBE = level.getBlockEntity(worldPosition.below(2));
		if (!(basinBE instanceof BasinBlockEntity basin))
			return Optional.empty();
		return Optional.of(basin);
	}

	protected abstract boolean matchStaticFilters(RecipeHolder<?> recipe);

	protected abstract Object getRecipeCacheKey();
}
