package dev.createpolymer.test;

import java.util.ArrayList;
import java.util.List;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.decoration.TexturedCubeBlock;
import dev.createpolymer.decoration.palettes.AllPaletteStoneTypes;
import dev.createpolymer.decoration.palettes.ConnectedPillarBlock;
import dev.createpolymer.registry.feature.DecorationRegistry;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.loot.LootTable;

public class DecorationGameTests {

	private static Identifier id(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block);
	}

	private static ItemStack silkPickaxe(GameTestHelper helper) {
		ItemStack pickaxe = new ItemStack(Items.NETHERITE_PICKAXE);
		pickaxe.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
			.getOrThrow(Enchantments.SILK_TOUCH), 1);
		return pickaxe;
	}

	@GameTest
	public void polymerStatePoolsAreNotExhausted(GameTestHelper helper) {
		StringBuilder log = new StringBuilder("Polymer states left after registration:");
		for (BlockModelType type : List.of(BlockModelType.FULL_BLOCK, BlockModelType.LEAVES, BlockModelType.SLAB_BOTTOM,
			BlockModelType.SLAB_TOP, BlockModelType.SLAB_BOTTOM_WATERLOGGED, BlockModelType.STAIRS_NORTH_BOTTOM_STRAIGHT,
			BlockModelType.STAIRS_WEST_TOP_OUTER_RIGHT_WATERLOGGED, BlockModelType.BARS_CENTER, BlockModelType.BARS_NORTH_EAST_SOUTH_WEST_WATERLOGGED,
			BlockModelType.SCAFFOLDING_TOP, BlockModelType.SCAFFOLDING_BOTTOM, BlockModelType.TRAPDOOR_TOP, BlockModelType.TRAPDOOR_NORTH))
			log.append(' ').append(type).append('=').append(PolymerBlockResourceUtils.getBlocksLeft(type));
		CreatePolymer.LOGGER.info(log.toString());
		helper.assertTrue(PolymerBlockResourceUtils.getBlocksLeft(BlockModelType.FULL_BLOCK) > 500, "cube states are running out: " + log);
		helper.assertTrue(PolymerBlockResourceUtils.getBlocksLeft(BlockModelType.LEAVES) > 50, "leaves states are running out: " + log);
		helper.succeed();
	}

	@GameTest
	public void registryHoldsTheWholeDecorationSet(GameTestHelper helper) {
		helper.assertTrue(DecorationRegistry.BLOCKS.size() == 308, "registered " + DecorationRegistry.BLOCKS.size() + " decoration blocks");
		for (AllPaletteStoneTypes type : AllPaletteStoneTypes.values()) {
			helper.assertTrue(type.getVariants().registeredBlocks.size() == 6, type + " has " + type.getVariants().registeredBlocks.size() + " pattern blocks");
			helper.assertTrue(type.getVariants().registeredPartials.size() == 12, type + " has " + type.getVariants().registeredPartials.size() + " partials");
		}
		for (Block block : DecorationRegistry.BLOCKS)
			helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id(block)), "no item for " + id(block));
		helper.succeed();
	}

	@GameTest
	public void everyDecorationBlockHasALootTableThatDropsItself(GameTestHelper helper) {
		var server = helper.getLevel().getServer();
		ItemStack tool = silkPickaxe(helper);
		List<String> problems = new ArrayList<>();
		for (Block block : DecorationRegistry.BLOCKS) {
			var key = block.getLootTable();
			if (key.isEmpty() || server.reloadableRegistries().getLootTable(key.get()) == LootTable.EMPTY) {
				problems.add(id(block) + " has no loot table");
				continue;
			}
			List<ItemStack> drops = Block.getDrops(block.defaultBlockState(), helper.getLevel(), BlockPos.ZERO, null, null, tool);
			if (drops.stream().noneMatch(s -> s.is(block.asItem())))
				problems.add(id(block) + " drops " + drops + " instead of itself (asItem " + BuiltInRegistries.ITEM.getKey(block.asItem()) + ", registered item " + BuiltInRegistries.ITEM.getValue(id(block)) + " " + BuiltInRegistries.ITEM.getValue(id(block)).getClass() + ")");
		}
		helper.assertTrue(problems.isEmpty(), "loot table problems: " + problems);
		helper.succeed();
	}

	@GameTest
	public void stonecuttingRecipeForAPaletteVariantIsLoaded(GameTestHelper helper) {
		var manager = helper.getLevel().getServer().getRecipeManager();
		ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE,
			CreatePolymer.id("cut_limestone_slab_from_stone_types_limestone_stonecutting"));
		helper.assertTrue(manager.byKey(key).isPresent(), "stonecutting recipe not loaded");
		helper.assertTrue(manager.byKey(key).get().value() instanceof StonecutterRecipe, "not a stonecutting recipe");

		// the stone type tag lists the base block and the cut variants, so any of them is cut into a slab
		for (Block input : List.of(AllPaletteStoneTypes.LIMESTONE.getBaseBlock(),
			AllPaletteStoneTypes.LIMESTONE.getVariants().registeredBlocks.get(0))) {
			var options = manager.stonecutterRecipes().selectByInput(new ItemStack(input));
			boolean found = options.entries().stream().anyMatch(e -> e.recipe().recipe().map(RecipeHolder::id)
				.map(k -> k.equals(key)).orElse(false));
			helper.assertTrue(found, "the stonecutter does not offer the slab for " + id(input));
		}
		// granite and the others of the vanilla range join in through the vanilla block
		var granite = manager.stonecutterRecipes().selectByInput(new ItemStack(Items.GRANITE));
		helper.assertTrue(granite.entries().stream().anyMatch(e -> e.recipe().recipe().map(r -> r.id().identifier().getPath().equals("cut_granite_from_stone_types_granite_stonecutting")).orElse(false)),
			"vanilla granite is not cut into cut granite");
		// and the crafting recipes
		for (String name : List.of("cut_limestone_stairs", "cut_limestone_slab", "cut_limestone_slab_recycling", "cut_limestone_wall", "oak_window", "oak_window_pane", "crafting/materials/cardboard_block"))
			helper.assertTrue(manager.byKey(ResourceKey.create(Registries.RECIPE, CreatePolymer.id(name))).isPresent(), "recipe " + name + " not loaded");
		helper.succeed();
	}

	@GameTest
	public void everyDecorationBlockHasAClientState(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (Block block : DecorationRegistry.BLOCKS) {
			if (!(block instanceof PolymerBlock polymer)) {
				problems.add(id(block) + " is not a Polymer block");
				continue;
			}
			for (BlockState state : block.getStateDefinition().getPossibleStates()) {
				BlockState client = polymer.getPolymerBlockState(state, null);
				if (client == null || client.is(Blocks.BARRIER) || client.isAir())
					problems.add(id(block) + " shows " + client + " for " + state);
			}
			if (block instanceof TexturedCubeBlock cube && cube.getClientState() == null)
				problems.add(id(block) + " has no texture state");
		}
		helper.assertTrue(problems.isEmpty(), "client state problems: " + problems.subList(0, Math.min(8, problems.size())));
		helper.succeed();
	}

	@GameTest
	public void fullCubesUseTheirOwnModelStates(GameTestHelper helper) {
		// the stone types, their cut variants and the iron blocks are full blocks Polymer re-skins, never vanilla stone
		for (AllPaletteStoneTypes type : AllPaletteStoneTypes.values()) {
			for (Block block : type.getVariants().registeredBlocks) {
				if (block instanceof ConnectedPillarBlock pillar) {
					for (Axis axis : Axis.values()) {
						BlockState client = pillar.getPolymerBlockState(pillar.defaultBlockState().setValue(ConnectedPillarBlock.AXIS, axis), null);
						helper.assertTrue(client.is(Blocks.NOTE_BLOCK) || client.is(Blocks.TARGET) || !client.is(type.getLook().cube()),
							id(block) + " axis " + axis + " is shown as plain " + client);
					}
				} else {
					BlockState client = ((PolymerBlock) block).getPolymerBlockState(block.defaultBlockState(), null);
					helper.assertTrue(!client.is(type.getLook().cube()), id(block) + " is shown as plain " + client);
				}
			}
		}
		helper.assertTrue(PolymerBlockResourceUtils.getBlocksLeft(BlockModelType.FULL_BLOCK) > 100,
			"only " + PolymerBlockResourceUtils.getBlocksLeft(BlockModelType.FULL_BLOCK) + " cube states left");
		helper.succeed();
	}

	@GameTest
	public void stairsAndWallsKeepTheirShape(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (Block block : DecorationRegistry.BLOCKS) {
			for (BlockState state : block.getStateDefinition().getPossibleStates()) {
				BlockState client = ((PolymerBlock) block).getPolymerBlockState(state, null);
				if (block instanceof StairBlock) {
					if (!(client.getBlock() instanceof StairBlock) && !client.getBlock().getStateDefinition().getProperties().containsAll(state.getProperties())
						|| client.getValue(StairBlock.FACING) != state.getValue(StairBlock.FACING)
						|| client.getValue(StairBlock.HALF) != state.getValue(StairBlock.HALF)
						|| client.getValue(StairBlock.SHAPE) != state.getValue(StairBlock.SHAPE)
						|| client.getValue(StairBlock.WATERLOGGED) != state.getValue(StairBlock.WATERLOGGED))
						problems.add(id(block) + " " + state + " -> " + client);
				} else if (block instanceof WallBlock) {
					if (!(client.getBlock() instanceof WallBlock)
						|| client.getValue(WallBlock.UP) != state.getValue(WallBlock.UP)
						|| client.getValue(WallBlock.NORTH) != state.getValue(WallBlock.NORTH)
						|| client.getValue(WallBlock.EAST) != state.getValue(WallBlock.EAST)
						|| client.getValue(WallBlock.SOUTH) != state.getValue(WallBlock.SOUTH)
						|| client.getValue(WallBlock.WEST) != state.getValue(WallBlock.WEST))
						problems.add(id(block) + " " + state + " -> " + client);
				} else if (block instanceof IronBarsBlock) {
					if (client.getValue(IronBarsBlock.NORTH) != state.getValue(IronBarsBlock.NORTH)
						|| client.getValue(IronBarsBlock.EAST) != state.getValue(IronBarsBlock.EAST)
						|| client.getValue(IronBarsBlock.SOUTH) != state.getValue(IronBarsBlock.SOUTH)
						|| client.getValue(IronBarsBlock.WEST) != state.getValue(IronBarsBlock.WEST)
						|| client.getValue(IronBarsBlock.WATERLOGGED) != state.getValue(IronBarsBlock.WATERLOGGED))
						problems.add(id(block) + " " + state + " -> " + client);
				} else if (block instanceof LadderBlock) {
					if (!client.is(Blocks.LADDER) || client.getValue(LadderBlock.FACING) != state.getValue(LadderBlock.FACING))
						problems.add(id(block) + " " + state + " -> " + client);
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), "shape problems: " + problems.subList(0, Math.min(8, problems.size())));
		helper.succeed();
	}

	@GameTest
	public void slabsDoubleUpIntoTheCube(GameTestHelper helper) {
		TestUtil.floor(helper);
		Player player = helper.makeMockPlayer(GameType.CREATIVE);
		for (AllPaletteStoneTypes type : List.of(AllPaletteStoneTypes.LIMESTONE, AllPaletteStoneTypes.GRANITE, AllPaletteStoneTypes.SCORIA)) {
			int x = type.ordinal() % 7 + 1;
			SlabBlock slab = (SlabBlock) type.getVariants().registeredPartials.get(1);
			helper.assertTrue(slab == BuiltInRegistries.BLOCK.getValue(CreatePolymer.id("cut_" + type.getId() + "_slab")), "partial 1 is not the cut slab");
			ItemStack stack = new ItemStack(slab.asItem(), 2);
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
			helper.placeAt(player, stack, new BlockPos(x, 0, 1), Direction.UP);
			helper.assertTrue(helper.getBlockState(new BlockPos(x, 1, 1)).getValue(SlabBlock.TYPE) == SlabType.BOTTOM, "no bottom slab for " + type);
			helper.placeAt(player, stack, new BlockPos(x, 0, 1), Direction.UP);
			BlockState placed = helper.getBlockState(new BlockPos(x, 1, 1));
			helper.assertTrue(placed.is(slab) && placed.getValue(SlabBlock.TYPE) == SlabType.DOUBLE, "slabs of " + type + " did not double up: " + placed);

			BlockState client = ((PolymerBlock) slab).getPolymerBlockState(placed, null);
			BlockState cubeClient = ((PolymerBlock) type.getVariants().registeredBlocks.get(0)).getPolymerBlockState(
				type.getVariants().registeredBlocks.get(0).defaultBlockState(), null);
			helper.assertTrue(client == cubeClient, "the double slab of " + type + " shows " + client + ", the cube shows " + cubeClient);
		}
		helper.succeed();
	}

	@GameTest
	public void pillarsConnectInAColumn(GameTestHelper helper) {
		TestUtil.floor(helper);
		Block pillar = AllPaletteStoneTypes.LIMESTONE.getVariants().registeredBlocks.get(5);
		helper.assertTrue(pillar instanceof ConnectedPillarBlock, "not a connected pillar");
		for (int y = 1; y <= 3; y++)
			helper.setBlock(new BlockPos(1, y, 1), pillar);
		helper.setBlock(new BlockPos(2, 1, 1), pillar);
		helper.runAfterDelay(4, () -> {
			// a pillar with a neighbour next to only part of the column does not connect to it
			BlockState state = helper.getBlockState(new BlockPos(1, 2, 1));
			helper.assertTrue(state.is(pillar), "pillar replaced by " + state);
			helper.assertTrue(!state.getValue(ConnectedPillarBlock.EAST), "the middle pillar connects to a neighbour that is not beside the whole column");
			helper.succeed();
		});
	}

	@GameTest
	public void metalLaddersClimbAndStack(GameTestHelper helper) {
		TestUtil.floor(helper);
		for (int y = 1; y <= 4; y++)
			helper.setBlock(new BlockPos(2, y, 3), Blocks.STONE);
		Block ladder = DecorationRegistry.BRASS_LADDER;
		helper.setBlock(new BlockPos(2, 2, 2), ladder.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
		helper.assertTrue(helper.getBlockState(new BlockPos(2, 2, 2)).is(net.minecraft.tags.BlockTags.CLIMBABLE), "metal ladders are not climbable");
		// the ladder hangs from the one above it
		helper.setBlock(new BlockPos(2, 1, 2), ladder.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
		helper.assertTrue(helper.getBlockState(new BlockPos(2, 1, 2)).canSurvive(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2))), "a ladder cannot hang from the ladder above");
		helper.succeed();
	}

	@GameTest
	public void barsPickUpTheirModels(GameTestHelper helper) {
		for (var bars : List.of(DecorationRegistry.ANDESITE_BARS, DecorationRegistry.BRASS_BARS, DecorationRegistry.COPPER_BARS))
			helper.assertTrue(bars.isTextured(), id(bars) + " has no Polymer states for its model (" + PolymerBlockResourceUtils.getBlocksLeft(BlockModelType.BARS_CENTER) + " left)");
		helper.succeed();
	}

	@GameTest
	public void scaffoldingStacksUpwards(GameTestHelper helper) {
		TestUtil.floor(helper);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack stack = new ItemStack(DecorationRegistry.BRASS_SCAFFOLD.asItem(), 4);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
		helper.placeAt(player, stack, new BlockPos(2, 0, 2), Direction.UP);
		helper.assertTrue(helper.getBlockState(new BlockPos(2, 1, 2)).is(DecorationRegistry.BRASS_SCAFFOLD), "scaffold not placed");
		// clicking the scaffold itself adds the next one on top of the column
		helper.placeAt(player, stack, new BlockPos(2, 1, 3), Direction.NORTH);
		helper.assertTrue(helper.getBlockState(new BlockPos(2, 2, 2)).is(DecorationRegistry.BRASS_SCAFFOLD), "second scaffold not on top: " + helper.getBlockState(new BlockPos(2, 2, 2)));
		helper.succeed();
	}

	@GameTest
	public void cardboardFacesTheWayYouLook(GameTestHelper helper) {
		TestUtil.floor(helper);
		BlockState state = DecorationRegistry.CARDBOARD_BLOCK.defaultBlockState();
		helper.assertTrue(state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_AXIS), "no axis");
		BlockState client = DecorationRegistry.CARDBOARD_BLOCK.getPolymerBlockState(state, null);
		helper.assertTrue(!client.is(Blocks.MUSHROOM_STEM), "cardboard shows as " + client);
		helper.succeed();
	}
}
