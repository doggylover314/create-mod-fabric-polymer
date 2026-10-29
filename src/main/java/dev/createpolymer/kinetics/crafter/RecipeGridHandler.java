package dev.createpolymer.kinetics.crafter;

import static dev.createpolymer.kinetics.base.HorizontalKineticBlock.HORIZONTAL_FACING;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.base.Predicates;

import dev.createpolymer.registry.feature.CrafterRegistry;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.Pointing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.FireworkRocketRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class RecipeGridHandler {

	// Create's CRecipes server config, at its defaults
	public static boolean allowRegularCraftingInCrafter = true;
	public static int maxFireworkIngredientsInCrafter = 9;

	public static List<MechanicalCrafterBlockEntity> getAllCraftersOfChain(MechanicalCrafterBlockEntity root) {
		return getAllCraftersOfChainIf(root, Predicates.alwaysTrue());
	}

	public static List<MechanicalCrafterBlockEntity> getAllCraftersOfChainIf(MechanicalCrafterBlockEntity root,
		Predicate<MechanicalCrafterBlockEntity> test) {
		return getAllCraftersOfChainIf(root, test, false);
	}

	public static List<MechanicalCrafterBlockEntity> getAllCraftersOfChainIf(MechanicalCrafterBlockEntity root,
		Predicate<MechanicalCrafterBlockEntity> test, boolean poweredStart) {
		List<MechanicalCrafterBlockEntity> crafters = new ArrayList<>();
		List<Pair<MechanicalCrafterBlockEntity, MechanicalCrafterBlockEntity>> frontier = new ArrayList<>();
		Set<MechanicalCrafterBlockEntity> visited = new HashSet<>();
		frontier.add(Pair.of(root, null));

		boolean empty = false;
		boolean allEmpty = true;

		while (!frontier.isEmpty()) {
			Pair<MechanicalCrafterBlockEntity, MechanicalCrafterBlockEntity> pair = frontier.remove(0);
			MechanicalCrafterBlockEntity current = pair.getKey();
			MechanicalCrafterBlockEntity last = pair.getValue();

			if (visited.contains(current))
				return null;
			if (!(test.test(current)))
				empty = true;
			else
				allEmpty = false;

			crafters.add(current);
			visited.add(current);

			MechanicalCrafterBlockEntity target = getTargetingCrafter(current);
			if (target != last && target != null)
				frontier.add(Pair.of(target, current));
			for (MechanicalCrafterBlockEntity preceding : getPrecedingCrafters(current))
				if (preceding != last)
					frontier.add(Pair.of(preceding, current));
		}

		return empty && !poweredStart || allEmpty ? null : crafters;
	}

	public static MechanicalCrafterBlockEntity getTargetingCrafter(MechanicalCrafterBlockEntity crafter) {
		BlockState state = crafter.getBlockState();
		if (!isCrafter(state))
			return null;

		BlockPos targetPos = crafter.getBlockPos()
			.relative(MechanicalCrafterBlock.getTargetDirection(state));
		MechanicalCrafterBlockEntity targetBE = CrafterHelper.getCrafter(crafter.getLevel(), targetPos);
		if (targetBE == null)
			return null;

		BlockState targetState = targetBE.getBlockState();
		if (!isCrafter(targetState))
			return null;
		if (state.getValue(HORIZONTAL_FACING) != targetState.getValue(HORIZONTAL_FACING))
			return null;
		return targetBE;
	}

	public static List<MechanicalCrafterBlockEntity> getPrecedingCrafters(MechanicalCrafterBlockEntity crafter) {
		BlockPos pos = crafter.getBlockPos();
		Level world = crafter.getLevel();
		List<MechanicalCrafterBlockEntity> crafters = new ArrayList<>();
		BlockState blockState = crafter.getBlockState();
		if (!isCrafter(blockState))
			return crafters;

		Direction blockFacing = blockState.getValue(HORIZONTAL_FACING);
		Direction blockPointing = MechanicalCrafterBlock.getTargetDirection(blockState);
		for (Direction facing : Iterate.directions) {
			if (blockFacing.getAxis() == facing.getAxis())
				continue;
			if (blockPointing == facing)
				continue;

			BlockPos neighbourPos = pos.relative(facing);
			BlockState neighbourState = world.getBlockState(neighbourPos);
			if (!isCrafter(neighbourState))
				continue;
			if (MechanicalCrafterBlock.getTargetDirection(neighbourState) != facing.getOpposite())
				continue;
			if (blockFacing != neighbourState.getValue(HORIZONTAL_FACING))
				continue;
			MechanicalCrafterBlockEntity be = CrafterHelper.getCrafter(world, neighbourPos);
			if (be == null)
				continue;

			crafters.add(be);
		}

		return crafters;
	}

	private static boolean isCrafter(BlockState state) {
		return state.is(CrafterRegistry.MECHANICAL_CRAFTER);
	}

	public static ItemStack tryToApplyRecipe(Level world, GroupedItems items) {
		if (!(world instanceof ServerLevel serverLevel))
			return null;
		items.calcStats();
		CraftingInput craftingInput = MechanicalCraftingInput.of(items);
		ItemStack result = null;
		if (allowRegularCraftingInCrafter)
			result = serverLevel.recipeAccess()
				.getRecipeFor(RecipeType.CRAFTING, craftingInput, serverLevel)
				.filter(r -> isRecipeAllowed(r, craftingInput))
				.map(r -> r.value().assemble(craftingInput))
				.orElse(null);
		if (result == null)
			result = serverLevel.recipeAccess()
				.getRecipeFor(CrafterRegistry.MECHANICAL_CRAFTING, craftingInput, serverLevel)
				.map(r -> r.value().assemble(craftingInput))
				.orElse(null);
		return result;
	}

	public static boolean isRecipeAllowed(RecipeHolder<CraftingRecipe> recipe, CraftingInput craftingInput) {
		if (recipe.value() instanceof FireworkRocketRecipe) {
			int numItems = 0;
			for (int i = 0; i < craftingInput.size(); i++) {
				if (!craftingInput.getItem(i).isEmpty()) {
					numItems++;
				}
			}
			if (numItems > maxFireworkIngredientsInCrafter) {
				return false;
			}
		}
		if (shouldIgnoreInAutomation(recipe))
			return false;
		return true;
	}

	/** Create's AllRecipeTypes.shouldIgnoreInAutomation: recipes whose id ends in "_manual_only". */
	private static boolean shouldIgnoreInAutomation(RecipeHolder<?> recipe) {
		return recipe.id().identifier().getPath().endsWith("_manual_only");
	}

	public static class GroupedItems {
		Map<Pair<Integer, Integer>, ItemStack> grid = new HashMap<>();
		int minX, minY, maxX, maxY, width, height;
		boolean statsReady;

		public GroupedItems() {}

		public GroupedItems(ItemStack stack) {
			grid.put(Pair.of(0, 0), stack);
		}

		public void mergeOnto(GroupedItems other, Pointing pointing) {
			int xOffset = pointing == Pointing.LEFT ? 1 : pointing == Pointing.RIGHT ? -1 : 0;
			int yOffset = pointing == Pointing.DOWN ? 1 : pointing == Pointing.UP ? -1 : 0;
			grid.forEach(
				(pair, stack) -> other.grid.put(Pair.of(pair.getKey() + xOffset, pair.getValue() + yOffset), stack));
			other.statsReady = false;
		}

		public void write(ValueOutput nbt) {
			ValueOutput.ValueOutputList gridNBT = nbt.childrenList("Grid");
			grid.forEach((pair, stack) -> {
				ValueOutput entry = gridNBT.addChild();
				entry.putInt("x", pair.getKey());
				entry.putInt("y", pair.getValue());
				entry.store("item", ItemStack.OPTIONAL_CODEC, stack);
			});
		}

		public static GroupedItems read(ValueInput nbt) {
			GroupedItems items = new GroupedItems();
			for (ValueInput entry : nbt.childrenListOrEmpty("Grid")) {
				int x = entry.getIntOr("x", 0);
				int y = entry.getIntOr("y", 0);
				ItemStack stack = entry.read("item", ItemStack.OPTIONAL_CODEC)
					.orElse(ItemStack.EMPTY);
				items.grid.put(Pair.of(x, y), stack);
			}
			return items;
		}

		public void calcStats() {
			if (statsReady)
				return;
			statsReady = true;

			minX = 0;
			minY = 0;
			maxX = 0;
			maxY = 0;

			for (Pair<Integer, Integer> pair : grid.keySet()) {
				int x = pair.getKey();
				int y = pair.getValue();
				minX = Math.min(minX, x);
				minY = Math.min(minY, y);
				maxX = Math.max(maxX, x);
				maxY = Math.max(maxY, y);
			}

			width = maxX - minX + 1;
			height = maxY - minY + 1;
		}

		public boolean onlyEmptyItems() {
			for (ItemStack stack : grid.values())
				if (!stack.isEmpty())
					return false;
			return true;
		}

	}

}
