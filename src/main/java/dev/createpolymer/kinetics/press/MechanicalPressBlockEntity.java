package dev.createpolymer.kinetics.press;

import java.util.List;
import java.util.Optional;

import dev.createpolymer.Config;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.item.ItemHelper;
import dev.createpolymer.kinetics.belt.transport.TransportedItemStack;
import dev.createpolymer.kinetics.press.PressingBehaviour.Mode;
import dev.createpolymer.kinetics.press.PressingBehaviour.PressingBehaviourSpecifics;
import dev.createpolymer.processing.basin.BasinBlockEntity;
import dev.createpolymer.processing.basin.BasinOperatingBlockEntity;
import dev.createpolymer.processing.recipe.PressingRecipe;
import dev.createpolymer.processing.recipe.RecipeApplier;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModRecipeTypes;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's MechanicalPressBlockEntity (MIT, The Create Team).
 * Sequenced assembly is not ported yet.
 */
public class MechanicalPressBlockEntity extends BasinOperatingBlockEntity implements PressingBehaviourSpecifics {

	private static final Object compressingRecipesKey = new Object();

	public PressingBehaviour pressingBehaviour;

	public MechanicalPressBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.MECHANICAL_PRESS, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		pressingBehaviour = new PressingBehaviour(this);
		behaviours.add(pressingBehaviour);
	}

	public PressingBehaviour getPressingBehaviour() {
		return pressingBehaviour;
	}

	@Override
	public boolean tryProcessInBasin(boolean simulate) {
		// Create adds the basin's inputs for the particles after applying the recipe; most are used up by then
		getBasin().ifPresent(basin -> {
			for (ItemStack stackInSlot : basin.inputInventory.stacks()) {
				if (stackInSlot.isEmpty())
					continue;
				pressingBehaviour.particleItems.add(stackInSlot.copy());
			}
		});

		applyBasinRecipe();
		return true;
	}

	@Override
	public boolean tryProcessInWorld(ItemEntity itemEntity, boolean simulate) {
		ItemStack item = itemEntity.getItem();
		Optional<PressingRecipe> recipe = getRecipe(item);
		if (recipe.isEmpty())
			return false;
		if (simulate)
			return true;

		pressingBehaviour.particleItems.add(item.copy());
		if (canProcessInBulk() || item.getCount() == 1) {
			RecipeApplier.applyRecipeOn(itemEntity, recipe.get(), true);
		} else {
			for (ItemStack result : RecipeApplier.applyRecipeOn(level, item.copyWithCount(1), recipe.get(), true)) {
				ItemEntity created =
					new ItemEntity(level, itemEntity.getX(), itemEntity.getY(), itemEntity.getZ(), result);
				created.setDefaultPickUpDelay();
				created.setDeltaMovement(VecHelper.offsetRandomly(Vec3.ZERO, level.getRandom(), .05f));
				level.addFreshEntity(created);
			}
			item.shrink(1);
		}

		return true;
	}

	@Override
	public boolean tryProcessOnBelt(TransportedItemStack input, List<ItemStack> outputList, boolean simulate) {
		Optional<PressingRecipe> recipe = getRecipe(input.stack);
		if (recipe.isEmpty())
			return false;
		if (simulate)
			return true;
		pressingBehaviour.particleItems.add(input.stack.copy());
		List<ItemStack> outputs = RecipeApplier.applyRecipeOn(level,
			canProcessInBulk() ? input.stack : input.stack.copyWithCount(1), recipe.get(), true);

		outputList.addAll(outputs);
		return true;
	}

	@Override
	public void onPressingCompleted() {
		if (pressingBehaviour.onBasin() && matchBasinRecipe(currentRecipe)
			&& getBasin().filter(BasinBlockEntity::canContinueProcessing)
				.isPresent())
			startProcessingBasin();
		else
			basinChecker.scheduleUpdate();
	}

	public Optional<PressingRecipe> getRecipe(ItemStack item) {
		if (!(level instanceof ServerLevel serverLevel))
			return Optional.empty();
		return serverLevel.recipeAccess().getRecipeFor(ModRecipeTypes.PRESSING, new SingleRecipeInput(item), level)
			.map(RecipeHolder::value);
	}

	public static boolean canCompress(Recipe<?> recipe) {
		if (!(recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe) || !Config.allowShapedSquareInPress)
			return false;
		List<Ingredient> ingredients = recipe.placementInfo().ingredients();
		return (ingredients.size() == 4 || ingredients.size() == 9) && ItemHelper.matchAllIngredients(ingredients);
	}

	@Override
	protected boolean matchStaticFilters(RecipeHolder<?> recipe) {
		Recipe<?> r = recipe.value();
		return (r instanceof CraftingRecipe && canCompress(r)) || r.getType() == ModRecipeTypes.COMPACTING;
	}

	@Override
	public float getKineticSpeed() {
		return getSpeed();
	}

	@Override
	public boolean canProcessInBulk() {
		return Config.bulkPressing;
	}

	@Override
	protected Object getRecipeCacheKey() {
		return compressingRecipesKey;
	}

	@Override
	public int getParticleAmount() {
		return 15;
	}

	@Override
	public void startProcessingBasin() {
		if (pressingBehaviour.running && pressingBehaviour.runningTicks <= PressingBehaviour.CYCLE / 2)
			return;
		super.startProcessingBasin();
		pressingBehaviour.start(Mode.BASIN);
	}

	@Override
	protected void onBasinRemoved() {
		pressingBehaviour.particleItems.clear();
		pressingBehaviour.running = false;
		pressingBehaviour.runningTicks = 0;
		sendData();
	}

	@Override
	protected boolean isRunning() {
		return pressingBehaviour.running;
	}

}
