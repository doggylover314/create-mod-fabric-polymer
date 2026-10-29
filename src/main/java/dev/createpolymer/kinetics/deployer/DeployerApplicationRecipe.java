package dev.createpolymer.kinetics.deployer;

import java.util.List;
import java.util.Set;

import dev.createpolymer.registry.feature.DeployerRegistry;
import dev.createpolymer.processing.sequenced.IAssemblyRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;

/** Ported from Create's DeployerApplicationRecipe (MIT, The Create Team). */
public class DeployerApplicationRecipe extends ItemApplicationRecipe implements IAssemblyRecipe {

	public DeployerApplicationRecipe(Params params, boolean keepHeldItem) {
		super(params, keepHeldItem);
	}

	@Override
	protected int getMaxOutputCount() {
		return 4;
	}

	@Override
	public void addAssemblyIngredients(List<Ingredient> list) {
		list.add(getIngredients().get(1));
	}

	@Override
	public void addRequiredMachines(Set<ItemLike> list) {
		list.add(DeployerRegistry.DEPLOYER);
	}

	@Override
	public RecipeSerializer<DeployerApplicationRecipe> getSerializer() {
		return DeployerRegistry.DEPLOYING_SERIALIZER;
	}

	@Override
	public RecipeType<DeployerApplicationRecipe> getType() {
		return DeployerRegistry.DEPLOYING;
	}
}
