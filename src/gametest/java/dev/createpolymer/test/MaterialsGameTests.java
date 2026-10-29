package dev.createpolymer.test;

import java.util.ArrayList;
import java.util.List;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.registry.feature.MaterialsRegistry;
import dev.createpolymer.worldgen.CreateWorldgen;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;

public class MaterialsGameTests {

	private static boolean inTag(ItemStack stack, String namespace, String path) {
		return stack.typeHolder().is(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(namespace, path)));
	}

	@GameTest
	public void zincOreDropsRawZincWithIronPickaxe(GameTestHelper helper) {
		List<ItemStack> drops = Block.getDrops(MaterialsRegistry.ZINC_ORE.defaultBlockState(), helper.getLevel(),
			BlockPos.ZERO, null, null, new ItemStack(Items.IRON_PICKAXE));
		helper.assertTrue(drops.stream().anyMatch(s -> s.is(MaterialsRegistry.RAW_ZINC)), "zinc ore dropped " + drops);
		helper.succeed();
	}

	@GameTest
	public void zincOreNeedsIronTool(GameTestHelper helper) {
		helper.assertTrue(MaterialsRegistry.ZINC_ORE.defaultBlockState().is(BlockTags.NEEDS_IRON_TOOL),
			"zinc ore is not in minecraft:needs_iron_tool");
		helper.succeed();
	}

	@GameTest
	public void zincNuggetsCraftIntoIngot(GameTestHelper helper) {
		var server = helper.getLevel().getServer();
		ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, CreatePolymer.id("crafting/materials/zinc_ingot_from_compacting"));
		helper.assertTrue(server.getRecipeManager().byKey(key).isPresent(), "zinc ingot recipe not loaded");
		List<ItemStack> nuggets = new ArrayList<>();
		for (int i = 0; i < 9; i++)
			nuggets.add(new ItemStack(MaterialsRegistry.ZINC_NUGGET));
		helper.assertTrue(server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
			CraftingInput.of(3, 3, nuggets), helper.getLevel()).isPresent(), "nine zinc nuggets do not craft");
		helper.succeed();
	}

	@GameTest
	public void zincRecipesLoaded(GameTestHelper helper) {
		var manager = helper.getLevel().getServer().getRecipeManager();
		for (String id : List.of("smelting/zinc_ingot_from_ore", "blasting/zinc_ingot_from_ore", "smelting/zinc_ingot_from_crushed")) {
			ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, CreatePolymer.id(id));
			helper.assertTrue(manager.byKey(key).isPresent(), "recipe " + id + " not loaded");
		}
		helper.succeed();
	}

	@GameTest
	public void zincTagsContainTheMaterials(GameTestHelper helper) {
		helper.assertTrue(inTag(new ItemStack(MaterialsRegistry.ZINC_ORE_ITEM), "c", "ores/zinc"), "zinc ore not in c:ores/zinc");
		helper.assertTrue(inTag(new ItemStack(MaterialsRegistry.ZINC_NUGGET), "c", "nuggets/zinc"), "zinc nugget not in c:nuggets/zinc");
		helper.assertTrue(inTag(new ItemStack(MaterialsRegistry.RAW_ZINC), "c", "raw_materials/zinc"), "raw zinc not in c:raw_materials/zinc");
		helper.succeed();
	}

	@GameTest
	public void zincOreFeatureResolves(GameTestHelper helper) {
		var access = helper.getLevel().registryAccess();
		helper.assertTrue(access.lookupOrThrow(Registries.PLACED_FEATURE).get(CreateWorldgen.ZINC_ORE).isPresent(),
			"placed feature create:zinc_ore does not resolve");
		helper.assertTrue(access.lookupOrThrow(Registries.FEATURE).get(
			ResourceKey.create(Registries.FEATURE, CreatePolymer.id("zinc_ore"))).isPresent(),
			"configured feature create:zinc_ore does not resolve");
		helper.succeed();
	}
}
