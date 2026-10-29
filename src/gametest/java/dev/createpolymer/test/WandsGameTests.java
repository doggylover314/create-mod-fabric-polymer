package dev.createpolymer.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import dev.createpolymer.equipment.symmetryWand.SymmetryHandler;
import dev.createpolymer.equipment.symmetryWand.SymmetryWandConfig;
import dev.createpolymer.equipment.symmetryWand.SymmetryWandGui;
import dev.createpolymer.equipment.symmetryWand.SymmetryWandItem;
import dev.createpolymer.equipment.symmetryWand.mirror.CrossPlaneMirror;
import dev.createpolymer.equipment.symmetryWand.mirror.EmptyMirror;
import dev.createpolymer.equipment.symmetryWand.mirror.PlaneMirror;
import dev.createpolymer.equipment.symmetryWand.mirror.SymmetryMirror;
import dev.createpolymer.equipment.symmetryWand.mirror.TriplePlaneMirror;
import dev.createpolymer.equipment.zapper.PlacementPatterns;
import dev.createpolymer.equipment.zapper.ZapperInteractionHandler;
import dev.createpolymer.equipment.zapper.terrainzapper.Brush;
import dev.createpolymer.equipment.zapper.terrainzapper.CuboidBrush;
import dev.createpolymer.equipment.zapper.terrainzapper.CylinderBrush;
import dev.createpolymer.equipment.zapper.terrainzapper.DynamicBrush;
import dev.createpolymer.equipment.zapper.terrainzapper.PlacementOptions;
import dev.createpolymer.equipment.zapper.terrainzapper.SphereBrush;
import dev.createpolymer.equipment.zapper.terrainzapper.TerrainBrushes;
import dev.createpolymer.equipment.zapper.terrainzapper.TerrainTools;
import dev.createpolymer.equipment.zapper.terrainzapper.WorldshaperGui;
import dev.createpolymer.equipment.zapper.terrainzapper.WorldshaperItem;
import dev.createpolymer.equipment.zapper.terrainzapper.WorldshaperRenderHandler;
import dev.createpolymer.registry.feature.MaterialsRegistry;
import dev.createpolymer.registry.feature.WandsRegistry;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.gui.SimpleGui;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class WandsGameTests {

	private static void check(GameTestHelper helper, boolean condition, String message) {
		helper.assertTrue(condition, message);
	}

	// ---- players ----

	private static final Map<GameTestHelper, List<ServerPlayer>> PLAYERS = new IdentityHashMap<>();

	/** A player with a connection (GameTestHelper's mock players have none, and cooldowns and messages send packets). */
	private static ServerPlayer player(GameTestHelper helper, GameType type) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(type);
		player.setNoGravity(true);
		PLAYERS.computeIfAbsent(helper, h -> new ArrayList<>()).add(player);
		return player;
	}

	/** Ends the test: the players it made leave and the test succeeds. */
	@SuppressWarnings("removal")
	private static void done(GameTestHelper helper) {
		for (ServerPlayer player : PLAYERS.getOrDefault(helper, List.of())) {
			SymmetryHandler.remove(player);
			WorldshaperRenderHandler.remove(player);
			player.connection.disconnect(net.minecraft.network.chat.Component.literal("test over"));
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		PLAYERS.remove(helper);
		helper.succeed();
	}

	private static void click(SimpleGui gui, int slot) {
		var element = gui.getGuiElement(slot);
		if (element == null)
			throw new AssertionError("nothing in slot " + slot + " of " + gui.getClass().getSimpleName());
		element.getGuiCallback()
			.click(slot, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, gui);
	}

	private static void floor(GameTestHelper helper) {
		TestUtil.floor(helper);
	}

	private static BlockState at(GameTestHelper helper, int x, int y, int z) {
		return helper.getBlockState(new BlockPos(x, y, z));
	}

	// ---- worldshaper ----

	private static ItemStack shaper(TerrainBrushes brush, int x, int y, int z, TerrainTools tool, PlacementOptions option,
		BlockState block) {
		ItemStack stack = new ItemStack(WandsRegistry.WORLDSHAPER);
		WorldshaperItem.configureSettings(stack, PlacementPatterns.Solid, brush, x, y, z, tool, option);
		if (block != null)
			stack.set(WandsRegistry.SHAPER_BLOCK_USED, block);
		return stack;
	}

	/**
	 * Puts the player above the block at {@code rel}, looking straight down at it, with the stack in the main hand.
	 * The eyes stay below the top of the test structure, or the ray would hit the layer above it.
	 */
	private static void aimDown(GameTestHelper helper, ServerPlayer player, BlockPos rel, ItemStack stack) {
		Vec3 above = Vec3.atBottomCenterOf(helper.absolutePos(rel)).add(0, 2, 0);
		player.snapTo(above.x, above.y, above.z, 0, 90);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
	}

	private static InteractionResult zap(GameTestHelper helper, ServerPlayer player, ItemStack stack) {
		return stack.getItem()
			.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
	}

	private static int count(GameTestHelper helper, Block block, int x0, int y0, int z0, int x1, int y1, int z1) {
		int found = 0;
		for (int x = x0; x <= x1; x++)
			for (int y = y0; y <= y1; y++)
				for (int z = z0; z <= z1; z++)
					if (at(helper, x, y, z).is(block))
						found++;
		return found;
	}

	@GameTest
	public void itemsAreRegisteredWithCreatesIds(GameTestHelper helper) {
		check(helper, BuiltInRegistries.ITEM.getKey(WandsRegistry.WORLDSHAPER).equals(Identifier.parse("create:handheld_worldshaper")),
			"worldshaper id");
		check(helper, BuiltInRegistries.ITEM.getKey(WandsRegistry.WAND_OF_SYMMETRY).equals(Identifier.parse("create:wand_of_symmetry")),
			"wand id");
		check(helper, new ItemStack(WandsRegistry.WORLDSHAPER).getMaxStackSize() == 1, "the worldshaper does not stack");
		check(helper, new ItemStack(WandsRegistry.WAND_OF_SYMMETRY).getMaxStackSize() == 1, "the wand does not stack");
		helper.succeed();
	}

	@GameTest
	public void wandOfSymmetryHasCreatesRecipe(GameTestHelper helper) {
		var manager = helper.getLevel().getServer().getRecipeManager();
		ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, Identifier.parse("create:mechanical_crafting/wand_of_symmetry"));
		check(helper, manager.byKey(key).isPresent(), "the recipe is not loaded");
		check(helper, MaterialsRegistry.PRECISION_MECHANISM != null, "precision mechanism");
		helper.succeed();
	}

	@GameTest
	public void brushSizesHaveCreatesLimits(GameTestHelper helper) {
		Brush cuboid = TerrainBrushes.Cuboid.get();
		for (int i = 0; i < 3; i++) {
			check(helper, cuboid.getMin(i) == 1 && cuboid.getMax(i) == 32, "cuboid " + i + " is 1..32");
			check(helper, cuboid.clampParam(i, 100) == 32 && cuboid.clampParam(i, -4) == 1, "cuboid " + i + " clamps");
		}
		Brush sphere = TerrainBrushes.Sphere.get();
		check(helper, sphere.getMin(0) == 0 && sphere.getMax(0) == 10, "sphere radius is 0..10");
		check(helper, sphere.clampParam(0, 11) == 10 && sphere.clampParam(0, -1) == 0, "sphere clamps");
		check(helper, sphere.clampParam(1, 5) == 1 && sphere.clampParam(2, -5) == 0, "unused parameters are flags");
		Brush cylinder = TerrainBrushes.Cylinder.get();
		check(helper, cylinder.getMin(0) == 0 && cylinder.getMax(0) == 8, "cylinder radius is 0..8");
		check(helper, cylinder.getMin(1) == 1 && cylinder.getMax(1) == 8, "cylinder height is 1..8");
		check(helper, cylinder.clampParam(0, 9) == 8 && cylinder.clampParam(1, 9) == 8 && cylinder.clampParam(1, 0) == 1,
			"cylinder clamps");
		for (TerrainBrushes dynamic : new TerrainBrushes[] { TerrainBrushes.Surface, TerrainBrushes.Cluster }) {
			Brush brush = dynamic.get();
			check(helper, brush.getMin(0) == 1 && brush.getMax(0) == 10, dynamic + " range is 1..10");
			check(helper, brush.clampParam(0, 50) == 10 && brush.clampParam(0, 0) == 1, dynamic + " clamps");
			check(helper, brush.clampParam(1, 7) == 1 && brush.clampParam(2, 7) == 1 && brush.clampParam(1, -3) == 0,
				dynamic + " flags are 0 or 1");
		}
		check(helper, CuboidBrush.MAX_SIZE == 32 && SphereBrush.MAX_RADIUS == 10 && CylinderBrush.MAX_RADIUS == 8
			&& CylinderBrush.MAX_HEIGHT == 8 && DynamicBrush.MAX_RADIUS == 10, "constants");
		helper.succeed();
	}

	@GameTest
	public void configuringTheItemClampsTheSizes(GameTestHelper helper) {
		ItemStack stack = new ItemStack(WandsRegistry.WORLDSHAPER);
		WorldshaperItem.configureSettings(stack, PlacementPatterns.Checkered, TerrainBrushes.Cuboid, 999, 0, -5, TerrainTools.Place,
			PlacementOptions.Attached);
		BlockPos params = stack.get(WandsRegistry.SHAPER_BRUSH_PARAMS);
		check(helper, params.equals(new BlockPos(32, 1, 1)), "cuboid parameters were " + params);
		check(helper, stack.get(WandsRegistry.PLACEMENT_PATTERN) == PlacementPatterns.Checkered, "pattern");
		check(helper, stack.get(WandsRegistry.SHAPER_TOOL) == TerrainTools.Place, "tool");
		check(helper, stack.get(WandsRegistry.SHAPER_PLACEMENT_OPTIONS) == PlacementOptions.Attached, "placement");

		WorldshaperItem.configureSettings(stack, PlacementPatterns.Solid, TerrainBrushes.Sphere, 40, 40, 40, TerrainTools.Fill,
			PlacementOptions.Merged);
		check(helper, stack.get(WandsRegistry.SHAPER_BRUSH_PARAMS).equals(new BlockPos(10, 1, 1)), "sphere parameters were "
			+ stack.get(WandsRegistry.SHAPER_BRUSH_PARAMS));

		// a dynamic brush does not support filling: it falls back to its first tool
		WorldshaperItem.configureSettings(stack, PlacementPatterns.Solid, TerrainBrushes.Cluster, 3, 0, 0, TerrainTools.Fill,
			PlacementOptions.Merged);
		check(helper, stack.get(WandsRegistry.SHAPER_TOOL) == TerrainTools.Replace, "cluster tool was " + stack.get(WandsRegistry.SHAPER_TOOL));
		check(helper, stack.get(WandsRegistry.SHAPER_BRUSH_PARAMS).equals(new BlockPos(3, 0, 0)), "flags of the cluster brush");
		helper.succeed();
	}

	@GameTest
	public void settingsSurviveSavingTheItem(GameTestHelper helper) {
		ItemStack stack = shaper(TerrainBrushes.Cylinder, 5, 3, 1, TerrainTools.Overlay, PlacementOptions.Inserted,
			Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HALF, Half.TOP));
		stack.set(WandsRegistry.PLACEMENT_PATTERN, PlacementPatterns.Chance50);
		CompoundTag data = new CompoundTag();
		data.putString("x", "kept");
		stack.set(WandsRegistry.SHAPER_BLOCK_DATA, data);

		var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		var encoded = ItemStack.OPTIONAL_CODEC.encodeStart(ops, stack).getOrThrow();
		ItemStack decoded = ItemStack.OPTIONAL_CODEC.parse(ops, encoded).getOrThrow();
		check(helper, decoded.get(WandsRegistry.SHAPER_BRUSH) == TerrainBrushes.Cylinder, "brush");
		check(helper, decoded.get(WandsRegistry.SHAPER_BRUSH_PARAMS).equals(new BlockPos(5, 3, 1)), "parameters");
		check(helper, decoded.get(WandsRegistry.SHAPER_TOOL) == TerrainTools.Overlay, "tool");
		check(helper, decoded.get(WandsRegistry.SHAPER_PLACEMENT_OPTIONS) == PlacementOptions.Inserted, "placement");
		check(helper, decoded.get(WandsRegistry.PLACEMENT_PATTERN) == PlacementPatterns.Chance50, "pattern");
		check(helper, decoded.get(WandsRegistry.SHAPER_BLOCK_USED).getValue(BlockStateProperties.HALF) == Half.TOP, "block");
		check(helper, decoded.get(WandsRegistry.SHAPER_BLOCK_DATA).getStringOr("x", "").equals("kept"), "block data");
		helper.succeed();
	}

	@GameTest
	public void fillOnlyFillsReplaceableBlocks(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(3, 1, 3, Blocks.STONE);
		ServerPlayer player = player(helper, GameType.CREATIVE);
		ItemStack stack = shaper(TerrainBrushes.Cuboid, 3, 3, 3, TerrainTools.Fill, PlacementOptions.Merged, Blocks.GOLD_BLOCK.defaultBlockState());
		aimDown(helper, player, new BlockPos(3, 1, 3), stack);
		check(helper, zap(helper, player, stack) == InteractionResult.SUCCESS, "the zap works");
		// 3x3x3 around the stone: the floor layer (9) and the stone stay, the other 17 are filled
		check(helper, count(helper, Blocks.GOLD_BLOCK, 0, 0, 0, 7, 4, 7) == 17, "gold count was " + count(helper, Blocks.GOLD_BLOCK, 0, 0, 0, 7, 4, 7));
		check(helper, at(helper, 3, 1, 3).is(Blocks.STONE) && at(helper, 3, 0, 3).is(Blocks.STONE), "solid blocks stay");
		check(helper, at(helper, 2, 2, 2).is(Blocks.GOLD_BLOCK) && at(helper, 4, 2, 4).is(Blocks.GOLD_BLOCK), "corners are filled");
		check(helper, at(helper, 1, 2, 3).isAir() && at(helper, 3, 3, 3).isAir(), "outside the brush stays empty");
		done(helper);
	}

	@GameTest
	public void placeReplacesEverything(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(3, 1, 3, Blocks.STONE);
		ServerPlayer player = player(helper, GameType.CREATIVE);
		ItemStack stack = shaper(TerrainBrushes.Cuboid, 3, 3, 3, TerrainTools.Place, PlacementOptions.Merged, Blocks.GOLD_BLOCK.defaultBlockState());
		aimDown(helper, player, new BlockPos(3, 1, 3), stack);
		zap(helper, player, stack);
		check(helper, count(helper, Blocks.GOLD_BLOCK, 0, 0, 0, 7, 4, 7) == 27, "gold count was " + count(helper, Blocks.GOLD_BLOCK, 0, 0, 0, 7, 4, 7));
		check(helper, at(helper, 3, 1, 3).is(Blocks.GOLD_BLOCK), "the stone is replaced too");
		done(helper);
	}

	@GameTest
	public void replaceOnlyChangesSolidBlocks(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(3, 1, 3, Blocks.STONE);
		ServerPlayer player = player(helper, GameType.CREATIVE);
		ItemStack stack = shaper(TerrainBrushes.Cuboid, 3, 3, 3, TerrainTools.Replace, PlacementOptions.Merged, Blocks.GOLD_BLOCK.defaultBlockState());
		aimDown(helper, player, new BlockPos(3, 1, 3), stack);
		zap(helper, player, stack);
		// the nine floor blocks in the brush and the stone
		check(helper, count(helper, Blocks.GOLD_BLOCK, 0, 0, 0, 7, 4, 7) == 10, "gold count was " + count(helper, Blocks.GOLD_BLOCK, 0, 0, 0, 7, 4, 7));
		check(helper, at(helper, 3, 2, 3).isAir() && at(helper, 2, 1, 2).isAir(), "air is not replaced");
		check(helper, at(helper, 1, 0, 1).is(Blocks.STONE) && at(helper, 5, 0, 5).is(Blocks.STONE), "floor outside the brush stays");
		done(helper);
	}

	@GameTest
	public void clearRemovesBlocksWithoutASelectedBlock(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(3, 1, 3, Blocks.STONE);
		ServerPlayer player = player(helper, GameType.CREATIVE);
		ItemStack stack = shaper(TerrainBrushes.Cuboid, 3, 3, 3, TerrainTools.Clear, PlacementOptions.Merged, null);
		aimDown(helper, player, new BlockPos(3, 1, 3), stack);
		check(helper, zap(helper, player, stack) == InteractionResult.SUCCESS, "clear needs no block");
		check(helper, count(helper, Blocks.STONE, 0, 0, 0, 7, 4, 7) == 64 - 9, "stone left: " + count(helper, Blocks.STONE, 0, 0, 0, 7, 4, 7));
		check(helper, at(helper, 3, 1, 3).isAir() && at(helper, 2, 0, 2).isAir() && at(helper, 4, 0, 4).isAir(), "the brush is empty");
		done(helper);
	}

	@GameTest
	public void overlayPutsTheBlockOnTopOfSolidBlocks(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper, GameType.CREATIVE);
		ItemStack stack = shaper(TerrainBrushes.Cuboid, 3, 1, 3, TerrainTools.Overlay, PlacementOptions.Merged, Blocks.GOLD_BLOCK.defaultBlockState());
		aimDown(helper, player, new BlockPos(3, 0, 3), stack);
		zap(helper, player, stack);
		check(helper, count(helper, Blocks.GOLD_BLOCK, 0, 0, 0, 7, 4, 7) == 9, "gold count was " + count(helper, Blocks.GOLD_BLOCK, 0, 0, 0, 7, 4, 7));
		check(helper, at(helper, 2, 1, 2).is(Blocks.GOLD_BLOCK) && at(helper, 4, 1, 4).is(Blocks.GOLD_BLOCK), "gold lies on the floor");
		check(helper, at(helper, 3, 0, 3).is(Blocks.STONE), "the floor stays");
		done(helper);
	}

	@GameTest
	public void flattenLevelsABump(GameTestHelper helper) {
		floor(helper);
		for (int y = 1; y <= 3; y++)
			helper.setBlock(3, y, 3, Blocks.STONE);
		ServerPlayer player = player(helper, GameType.CREATIVE);
		ItemStack stack = shaper(TerrainBrushes.Cuboid, 7, 8, 7, TerrainTools.Flatten, PlacementOptions.Merged, null);
		aimDown(helper, player, new BlockPos(3, 3, 3), stack);
		check(helper, zap(helper, player, stack) == InteractionResult.SUCCESS, "flatten needs no block");
		check(helper, at(helper, 3, 3, 3).isAir() && at(helper, 3, 2, 3).isAir() && at(helper, 3, 1, 3).isAir(), "the bump is gone");
		check(helper, at(helper, 3, 0, 3).is(Blocks.STONE) && at(helper, 2, 0, 3).is(Blocks.STONE), "the ground stays");
		check(helper, count(helper, Blocks.STONE, 0, 1, 0, 7, 7, 7) == 0, "nothing is left above the ground");
		done(helper);
	}

	@GameTest
	public void sphereAndCylinderBrushesShapeTheRegion(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(3, 1, 3, Blocks.STONE);
		helper.setBlock(6, 1, 6, Blocks.STONE);
		ServerPlayer player = player(helper, GameType.CREATIVE);

		// radius 1 sphere: the centre, its six neighbours and the twelve next to its edges
		ItemStack stack = shaper(TerrainBrushes.Sphere, 1, 1, 1, TerrainTools.Place, PlacementOptions.Merged, Blocks.GOLD_BLOCK.defaultBlockState());
		aimDown(helper, player, new BlockPos(3, 1, 3), stack);
		zap(helper, player, stack);
		int gold = count(helper, Blocks.GOLD_BLOCK, 0, 0, 0, 7, 4, 7);
		check(helper, gold == 19, "sphere gold count was " + gold);
		check(helper, at(helper, 3, 2, 3).is(Blocks.GOLD_BLOCK) && at(helper, 3, 0, 3).is(Blocks.GOLD_BLOCK)
			&& at(helper, 2, 1, 2).is(Blocks.GOLD_BLOCK) && at(helper, 2, 2, 2).isAir(), "sphere shape");

		// radius 1, height 2 cylinder: a 3x3 disc, two layers high
		ItemStack cylinder = shaper(TerrainBrushes.Cylinder, 1, 2, 1, TerrainTools.Place, PlacementOptions.Merged, Blocks.EMERALD_BLOCK.defaultBlockState());
		aimDown(helper, player, new BlockPos(6, 1, 6), cylinder);
		zap(helper, player, cylinder);
		int emerald = count(helper, Blocks.EMERALD_BLOCK, 0, 0, 0, 7, 4, 7);
		check(helper, emerald == 18, "cylinder emerald count was " + emerald);
		check(helper, at(helper, 6, 2, 6).isAir() && at(helper, 6, 0, 6).is(Blocks.EMERALD_BLOCK) && at(helper, 6, 1, 6).is(Blocks.EMERALD_BLOCK),
			"the layers are one below the centre and the centre");
		done(helper);
	}

	@GameTest
	public void attachedAndInsertedMoveTheRegion(GameTestHelper helper) {
		Brush cuboid = TerrainBrushes.Cuboid.get();
		cuboid.set(3, 3, 3);
		Vec3 ray = new Vec3(0, -1, 0);
		check(helper, cuboid.getOffset(ray, Direction.UP, PlacementOptions.Merged).equals(BlockPos.ZERO), "merged is not moved");
		check(helper, cuboid.getOffset(ray, Direction.UP, PlacementOptions.Attached).equals(new BlockPos(0, 2, 0)), "attached sits on the surface");
		check(helper, cuboid.getOffset(ray, Direction.UP, PlacementOptions.Inserted).equals(new BlockPos(0, -1, 0)), "inserted sinks into it");
		check(helper, cuboid.getOffset(ray, Direction.EAST, PlacementOptions.Attached).equals(new BlockPos(2, 0, 0)), "attached on a side");

		floor(helper);
		ServerPlayer player = player(helper, GameType.CREATIVE);
		ItemStack stack = shaper(TerrainBrushes.Cuboid, 3, 3, 3, TerrainTools.Fill, PlacementOptions.Attached, Blocks.GOLD_BLOCK.defaultBlockState());
		aimDown(helper, player, new BlockPos(3, 0, 3), stack);
		zap(helper, player, stack);
		check(helper, count(helper, Blocks.GOLD_BLOCK, 0, 1, 0, 7, 3, 7) == 27, "attached region lies on the floor");
		check(helper, count(helper, Blocks.GOLD_BLOCK, 0, 0, 0, 7, 0, 7) == 0, "and does not touch it");
		done(helper);
	}

	@GameTest
	public void clusterBrushFollowsTheSameBlock(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(4, 0, 3, Blocks.DIRT);
		ServerPlayer player = player(helper, GameType.CREATIVE);

		// range 3 without diagonals, staying within the same material
		ItemStack stack = shaper(TerrainBrushes.Cluster, 3, 1, 1, TerrainTools.Replace, PlacementOptions.Merged, Blocks.GOLD_BLOCK.defaultBlockState());
		aimDown(helper, player, new BlockPos(3, 0, 3), stack);
		zap(helper, player, stack);
		int gold = count(helper, Blocks.GOLD_BLOCK, 0, -1, 0, 7, 2, 7);
		check(helper, gold == 24, "stone replaced with gold: " + gold);
		check(helper, at(helper, 4, 0, 3).is(Blocks.DIRT), "the dirt is another material");

		// ignoring material borders (the third parameter is 0)
		ItemStack fuzzy = shaper(TerrainBrushes.Cluster, 3, 1, 0, TerrainTools.Replace, PlacementOptions.Merged, Blocks.EMERALD_BLOCK.defaultBlockState());
		aimDown(helper, player, new BlockPos(3, 0, 3), fuzzy);
		zap(helper, player, fuzzy);
		check(helper, count(helper, Blocks.EMERALD_BLOCK, 0, -1, 0, 7, 2, 7) == 25, "emerald count was " + count(helper, Blocks.EMERALD_BLOCK, 0, -1, 0, 7, 2, 7));
		done(helper);
	}

	@GameTest
	public void surfaceBrushOverlaysTheSurface(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper, GameType.CREATIVE);
		ItemStack stack = shaper(TerrainBrushes.Surface, 3, 1, 1, TerrainTools.Overlay, PlacementOptions.Merged, Blocks.GOLD_BLOCK.defaultBlockState());
		aimDown(helper, player, new BlockPos(3, 0, 3), stack);
		zap(helper, player, stack);
		int gold = count(helper, Blocks.GOLD_BLOCK, 0, 0, 0, 7, 3, 7);
		check(helper, gold == 25, "gold count was " + gold);
		check(helper, count(helper, Blocks.GOLD_BLOCK, 0, 1, 0, 7, 1, 7) == 25, "on top of the floor");
		done(helper);
	}

	@GameTest
	public void sizesAreClampedWhenZapping(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper, GameType.CREATIVE);
		ItemStack stack = shaper(TerrainBrushes.Cuboid, 1, 1, 1, TerrainTools.Place, PlacementOptions.Merged, Blocks.GOLD_BLOCK.defaultBlockState());
		// as if set by a command: out of range, a cuboid has at least one block
		stack.set(WandsRegistry.SHAPER_BRUSH_PARAMS, new BlockPos(-5, 0, 0));
		aimDown(helper, player, new BlockPos(3, 0, 3), stack);
		zap(helper, player, stack);
		int gold = count(helper, Blocks.GOLD_BLOCK, 0, 0, 0, 7, 4, 7);
		check(helper, gold == 1 && at(helper, 3, 0, 3).is(Blocks.GOLD_BLOCK), "a single block was placed, gold count was " + gold);

		// a sphere beyond the largest radius still works: it is the largest sphere (checked on the brush, as it
		// would not fit into the test)
		Brush sphere = TerrainBrushes.Sphere.get();
		sphere.set(sphere.clampParam(0, 99), 1, 1);
		List<BlockPos> positions = new ArrayList<>();
		sphere.addToGlobalPositions(helper.getLevel(), BlockPos.ZERO, Direction.UP, positions, TerrainTools.Fill);
		check(helper, positions.contains(new BlockPos(10, 0, 0)) && !positions.contains(new BlockPos(11, 0, 0)), "radius 10 is the limit");
		done(helper);
	}

	@GameTest
	public void patternsFilterThePositions(GameTestHelper helper) {
		List<BlockPos> grid = new ArrayList<>();
		for (int x = 0; x < 10; x++)
			for (int y = 0; y < 10; y++)
				for (int z = 0; z < 10; z++)
					grid.add(new BlockPos(x, y, z));

		ItemStack stack = new ItemStack(WandsRegistry.WORLDSHAPER);
		RandomSource random = RandomSource.create(42);

		List<BlockPos> solid = new ArrayList<>(grid);
		PlacementPatterns.applyPattern(solid, stack, random);
		check(helper, solid.size() == 1000, "no pattern keeps everything");

		stack.set(WandsRegistry.PLACEMENT_PATTERN, PlacementPatterns.Checkered);
		List<BlockPos> checkered = new ArrayList<>(grid);
		PlacementPatterns.applyPattern(checkered, stack, random);
		// the pattern removes the positions it matches: checkered leaves the odd ones, inverse checkered the even ones
		check(helper, checkered.size() == 500 && checkered.stream().allMatch(p -> (p.getX() + p.getY() + p.getZ()) % 2 != 0), "checkered");

		stack.set(WandsRegistry.PLACEMENT_PATTERN, PlacementPatterns.InverseCheckered);
		List<BlockPos> inverse = new ArrayList<>(grid);
		PlacementPatterns.applyPattern(inverse, stack, random);
		check(helper, inverse.size() == 500 && inverse.stream().allMatch(p -> (p.getX() + p.getY() + p.getZ()) % 2 == 0), "inverse checkered");

		for (Object[] chance : new Object[][] { { PlacementPatterns.Chance25, 250 }, { PlacementPatterns.Chance50, 500 },
			{ PlacementPatterns.Chance75, 750 } }) {
			stack.set(WandsRegistry.PLACEMENT_PATTERN, (PlacementPatterns) chance[0]);
			List<BlockPos> kept = new ArrayList<>(grid);
			PlacementPatterns.applyPattern(kept, stack, random);
			int expected = (Integer) chance[1];
			check(helper, Math.abs(kept.size() - expected) < 80, chance[0] + " kept " + kept.size() + ", expected about " + expected);
		}
		helper.succeed();
	}

	@GameTest
	public void patternIsAppliedWhenZapping(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper, GameType.CREATIVE);
		ItemStack stack = shaper(TerrainBrushes.Cuboid, 3, 3, 3, TerrainTools.Fill, PlacementOptions.Attached, Blocks.GOLD_BLOCK.defaultBlockState());
		stack.set(WandsRegistry.PLACEMENT_PATTERN, PlacementPatterns.Checkered);
		aimDown(helper, player, new BlockPos(3, 0, 3), stack);
		zap(helper, player, stack);
		// 27 blocks of which 14 or 13 have an even coordinate sum
		int gold = count(helper, Blocks.GOLD_BLOCK, 0, 1, 0, 7, 3, 7);
		check(helper, gold == 13 || gold == 14, "checkered gold count was " + gold);
		done(helper);
	}

	@GameTest
	public void zapperNeedsSettingsAndABlock(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper, GameType.CREATIVE);

		ItemStack unset = new ItemStack(WandsRegistry.WORLDSHAPER);
		aimDown(helper, player, new BlockPos(3, 0, 3), unset);
		check(helper, zap(helper, player, unset) == InteractionResult.FAIL, "an unconfigured shaper does not work");
		check(helper, count(helper, Blocks.STONE, 0, 0, 0, 7, 0, 7) == 64, "nothing changed");

		ItemStack noBlock = shaper(TerrainBrushes.Cuboid, 3, 3, 3, TerrainTools.Fill, PlacementOptions.Merged, null);
		aimDown(helper, player, new BlockPos(3, 0, 3), noBlock);
		check(helper, zap(helper, player, noBlock) == InteractionResult.FAIL, "filling needs a block");
		check(helper, count(helper, Blocks.STONE, 0, 0, 0, 7, 0, 7) == 64, "still nothing changed");
		done(helper);
	}

	@GameTest
	public void leftClickingSelectsTheBlock(GameTestHelper helper) {
		floor(helper);
		BlockPos rel = new BlockPos(3, 1, 3);
		helper.setBlock(rel, Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		ItemStack stack = new ItemStack(WandsRegistry.WORLDSHAPER);
		aimDown(helper, player, rel, stack);

		check(helper, ZapperInteractionHandler.leftClickingBlocksWithTheZapperSelectsTheBlock(player, helper.getLevel(),
			InteractionHand.MAIN_HAND, helper.absolutePos(rel), Direction.UP) == InteractionResult.FAIL, "the attack is cancelled");
		BlockState selected = stack.get(WandsRegistry.SHAPER_BLOCK_USED);
		check(helper, selected != null && selected.is(Blocks.OAK_STAIRS), "the stairs are selected");
		check(helper, !selected.getValue(BlockStateProperties.WATERLOGGED), "the water is not copied");
		check(helper, selected.getValue(BlockStateProperties.STAIRS_SHAPE) == StairsShape.STRAIGHT, "the stairs are straight");

		// selecting the same block again does nothing
		check(helper, ZapperInteractionHandler.leftClickingBlocksWithTheZapperSelectsTheBlock(player, helper.getLevel(),
			InteractionHand.MAIN_HAND, helper.absolutePos(rel), Direction.UP) == InteractionResult.PASS, "nothing new to select");

		// sneaking attacks as usual
		helper.setBlock(rel, Blocks.GOLD_BLOCK);
		player.setShiftKeyDown(true);
		check(helper, !ZapperInteractionHandler.trySelect(stack, player), "sneaking does not select");
		player.setShiftKeyDown(false);
		check(helper, ZapperInteractionHandler.trySelect(stack, player) && stack.get(WandsRegistry.SHAPER_BLOCK_USED).is(Blocks.GOLD_BLOCK),
			"gold is selected");

		// block entities that cannot be copied, and half blocks
		helper.setBlock(rel, Blocks.CHEST);
		check(helper, !ZapperInteractionHandler.trySelect(stack, player), "chests are not selected");
		helper.setBlock(rel, Blocks.OAK_DOOR);
		// a closed door facing north stands in the south part of its block
		Vec3 above = Vec3.atBottomCenterOf(helper.absolutePos(rel)).add(0, 2, .4);
		player.snapTo(above.x, above.y, above.z, 0, 90);
		check(helper, !ZapperInteractionHandler.trySelect(stack, player), "doors are not selected");
		aimDown(helper, player, rel, stack);
		check(helper, stack.get(WandsRegistry.SHAPER_BLOCK_USED).is(Blocks.GOLD_BLOCK), "the selection stays");

		// the block entity data of a sign is kept
		helper.setBlock(rel, Blocks.OAK_SIGN);
		check(helper, ZapperInteractionHandler.trySelect(stack, player) && stack.has(WandsRegistry.SHAPER_BLOCK_DATA), "signs keep their data");

		// the shaper only listens to the main hand and only to its own item
		ItemStack other = new ItemStack(Items.STICK);
		player.setItemInHand(InteractionHand.MAIN_HAND, other);
		check(helper, ZapperInteractionHandler.leftClickingBlocksWithTheZapperSelectsTheBlock(player, helper.getLevel(),
			InteractionHand.MAIN_HAND, helper.absolutePos(rel), Direction.UP) == InteractionResult.PASS, "other items pass");
		done(helper);
	}

	@GameTest
	public void selectedBlockIsUsedByTheZap(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(1, 1, 1, Blocks.AMETHYST_BLOCK);
		ServerPlayer player = player(helper, GameType.CREATIVE);
		ItemStack stack = shaper(TerrainBrushes.Cuboid, 1, 1, 1, TerrainTools.Fill, PlacementOptions.Attached, null);
		aimDown(helper, player, new BlockPos(1, 1, 1), stack);
		check(helper, ZapperInteractionHandler.trySelect(stack, player), "the amethyst is selected");
		aimDown(helper, player, new BlockPos(5, 0, 5), stack);
		zap(helper, player, stack);
		check(helper, at(helper, 5, 1, 5).is(Blocks.AMETHYST_BLOCK), "the selected block is placed");
		done(helper);
	}

	@GameTest
	public void worldshaperGuiConfiguresTheItem(GameTestHelper helper) {
		ServerPlayer player = player(helper, GameType.CREATIVE);
		ItemStack stack = new ItemStack(WandsRegistry.WORLDSHAPER);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		WorldshaperGui gui = new WorldshaperGui(player, stack, InteractionHand.MAIN_HAND);
		check(helper, gui.open(), "the GUI opens");
		check(helper, gui.stillValid(), "valid while the item is held");

		// brush: cuboid is the default, pick the sphere (slot 2); its radius row is row 1
		click(gui, 2);
		check(helper, stack.get(WandsRegistry.SHAPER_BRUSH) == TerrainBrushes.Sphere, "sphere");
		check(helper, stack.get(WandsRegistry.SHAPER_BRUSH_PARAMS).getX() == 1, "radius 1 to begin with");
		click(gui, 9 + 5);
		click(gui, 9 + 5);
		check(helper, stack.get(WandsRegistry.SHAPER_BRUSH_PARAMS).getX() == 3, "radius raised to 3");
		click(gui, 9 + 3);
		check(helper, stack.get(WandsRegistry.SHAPER_BRUSH_PARAMS).getX() == 2, "radius lowered to 2");
		check(helper, gui.getGuiElement(18 + 4) == null, "a sphere has one parameter");

		// tool: replace is the third; placement: attached; pattern: checkered
		click(gui, 36 + 1 + TerrainTools.Replace.ordinal());
		click(gui, 45 + PlacementOptions.Attached.ordinal());
		click(gui, 45 + 3 + PlacementPatterns.Checkered.ordinal());
		check(helper, stack.get(WandsRegistry.SHAPER_TOOL) == TerrainTools.Replace, "tool");
		check(helper, stack.get(WandsRegistry.SHAPER_PLACEMENT_OPTIONS) == PlacementOptions.Attached, "placement");
		check(helper, stack.get(WandsRegistry.PLACEMENT_PATTERN) == PlacementPatterns.Checkered, "pattern");
		check(helper, stack.get(WandsRegistry.SHAPER_BRUSH) == TerrainBrushes.Sphere
			&& stack.get(WandsRegistry.SHAPER_BRUSH_PARAMS).getX() == 2, "the rest is kept");

		// closing the GUI keeps the settings; the GUI is no longer valid when the item is not held
		gui.close();
		check(helper, stack.get(WandsRegistry.SHAPER_TOOL) == TerrainTools.Replace, "settings kept on close");
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		check(helper, !gui.stillValid(), "invalid once the item is gone");
		done(helper);
	}

	@GameTest
	public void worldshaperGuiStopsAtTheLimits(GameTestHelper helper) {
		ServerPlayer player = player(helper, GameType.CREATIVE);
		ItemStack stack = new ItemStack(WandsRegistry.WORLDSHAPER);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		WorldshaperGui gui = new WorldshaperGui(player, stack, InteractionHand.MAIN_HAND);

		// cuboid: all three parameters go up to 32 with the +10 buttons
		for (int row = 1; row <= 3; row++) {
			check(helper, gui.getGuiElement(row * 9 + 3) == null, "cuboid " + row + " starts at its minimum");
			for (int i = 0; i < 3; i++)
				click(gui, row * 9 + 6);
			while (gui.getGuiElement(row * 9 + 5) != null)
				click(gui, row * 9 + 5);
			check(helper, gui.getGuiElement(row * 9 + 6) == null, "no more +10 at the maximum");
		}
		check(helper, stack.get(WandsRegistry.SHAPER_BRUSH_PARAMS).equals(new BlockPos(32, 32, 32)), "cuboid parameters were "
			+ stack.get(WandsRegistry.SHAPER_BRUSH_PARAMS));

		// the sphere goes up to 10, and a size that does not fit the new brush is lowered
		click(gui, 2);
		check(helper, stack.get(WandsRegistry.SHAPER_BRUSH_PARAMS).getX() == 10, "radius lowered to 10, was " + stack.get(WandsRegistry.SHAPER_BRUSH_PARAMS));
		check(helper, gui.getGuiElement(9 + 5) == null, "no way over 10");

		// the cylinder: radius 8, height 8
		click(gui, 3);
		BlockPos params = stack.get(WandsRegistry.SHAPER_BRUSH_PARAMS);
		check(helper, params.getX() == 8 && params.getY() == 8, "cylinder parameters were " + params);
		while (gui.getGuiElement(18 + 5) != null)
			click(gui, 18 + 5);
		check(helper, stack.get(WandsRegistry.SHAPER_BRUSH_PARAMS).getY() == 8, "cylinder height 8");
		check(helper, gui.getGuiElement(27 + 4) == null, "no third parameter");
		done(helper);
	}

	@GameTest
	public void worldshaperGuiHasConnectivityOptionsForDynamicBrushes(GameTestHelper helper) {
		ServerPlayer player = player(helper, GameType.CREATIVE);
		ItemStack stack = new ItemStack(WandsRegistry.WORLDSHAPER);
		stack.set(WandsRegistry.SHAPER_TOOL, TerrainTools.Fill);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		WorldshaperGui gui = new WorldshaperGui(player, stack, InteractionHand.MAIN_HAND);

		click(gui, 1 + TerrainBrushes.Cluster.ordinal());
		check(helper, stack.get(WandsRegistry.SHAPER_BRUSH) == TerrainBrushes.Cluster, "cluster");
		check(helper, stack.get(WandsRegistry.SHAPER_TOOL) == TerrainTools.Replace, "fill is not a cluster tool");
		check(helper, stack.get(WandsRegistry.SHAPER_BRUSH_PARAMS).equals(new BlockPos(1, 1, 1)), "no options set yet");
		click(gui, 18 + 4);
		check(helper, stack.get(WandsRegistry.SHAPER_BRUSH_PARAMS).getY() == 0, "follow diagonals");
		click(gui, 27 + 4);
		check(helper, stack.get(WandsRegistry.SHAPER_BRUSH_PARAMS).getZ() == 0, "ignore material borders");
		click(gui, 18 + 4);
		check(helper, stack.get(WandsRegistry.SHAPER_BRUSH_PARAMS).equals(new BlockPos(1, 1, 0)), "diagonals off again");
		check(helper, gui.getGuiElement(45) == null, "no placement options");
		check(helper, gui.getGuiElement(37) != null && gui.getGuiElement(38) != null && gui.getGuiElement(39) == null, "only two tools");
		done(helper);
	}

	@GameTest
	public void closingTheGuiStoresTheDefaults(GameTestHelper helper) {
		ServerPlayer player = player(helper, GameType.CREATIVE);
		ItemStack stack = new ItemStack(WandsRegistry.WORLDSHAPER);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		WorldshaperGui gui = new WorldshaperGui(player, stack, InteractionHand.MAIN_HAND);
		check(helper, !stack.has(WandsRegistry.SHAPER_BRUSH_PARAMS), "nothing stored before");
		gui.open();
		gui.close();
		check(helper, stack.get(WandsRegistry.SHAPER_BRUSH_PARAMS).equals(new BlockPos(1, 1, 1)), "defaults stored");
		check(helper, stack.get(WandsRegistry.SHAPER_BRUSH) == TerrainBrushes.Cuboid && stack.get(WandsRegistry.SHAPER_TOOL) == TerrainTools.Fill,
			"cuboid fill");
		done(helper);
	}

	@GameTest
	public void sneakUsingOpensTheGuiAndDoesNotZap(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper, GameType.CREATIVE);
		ItemStack stack = shaper(TerrainBrushes.Cuboid, 3, 3, 3, TerrainTools.Place, PlacementOptions.Merged, Blocks.GOLD_BLOCK.defaultBlockState());
		aimDown(helper, player, new BlockPos(3, 0, 3), stack);
		player.setShiftKeyDown(true);
		check(helper, zap(helper, player, stack) == InteractionResult.SUCCESS, "using succeeds");
		check(helper, player.containerMenu != player.inventoryMenu, "the GUI is open");
		check(helper, count(helper, Blocks.GOLD_BLOCK, 0, 0, 0, 7, 4, 7) == 0, "nothing was placed");
		check(helper, player.getCooldowns().isOnCooldown(stack), "the shaper is on cooldown");
		done(helper);
	}

	@GameTest
	public void zappingStartsACooldown(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper, GameType.CREATIVE);
		ItemStack stack = shaper(TerrainBrushes.Cuboid, 1, 1, 1, TerrainTools.Place, PlacementOptions.Attached, Blocks.GOLD_BLOCK.defaultBlockState());
		aimDown(helper, player, new BlockPos(3, 0, 3), stack);
		zap(helper, player, stack);
		check(helper, player.getCooldowns().isOnCooldown(stack), "cooldown after zapping");
		done(helper);
	}

	@GameTest
	public void previewOutlinesTheRegion(GameTestHelper helper) {
		check(helper, WorldshaperRenderHandler.outline(List.of(BlockPos.ZERO)).size() == 12, "a block has twelve edges");
		check(helper, WorldshaperRenderHandler.outline(List.of(BlockPos.ZERO, BlockPos.ZERO.east())).size() == 16, "two blocks have sixteen unit edges");
		List<BlockPos> cube = new ArrayList<>();
		for (int x = 0; x < 5; x++)
			for (int y = 0; y < 5; y++)
				for (int z = 0; z < 5; z++)
					cube.add(new BlockPos(x, y, z));
		check(helper, WorldshaperRenderHandler.outline(cube).size() == 60, "a cube of five has 12 * 5 unit edges");
		// an L-shaped corner has the concave edge too
		check(helper, WorldshaperRenderHandler.outline(List.of(BlockPos.ZERO, BlockPos.ZERO.east(), BlockPos.ZERO.south())).size() == 22,
			"an L has 22 unit edges");

		// the preview of a player holding a configured shaper
		floor(helper);
		ServerPlayer player = player(helper, GameType.CREATIVE);
		ItemStack stack = shaper(TerrainBrushes.Cuboid, 3, 3, 3, TerrainTools.Fill, PlacementOptions.Merged, Blocks.GOLD_BLOCK.defaultBlockState());
		aimDown(helper, player, new BlockPos(3, 0, 3), stack);
		WorldshaperRenderHandler.createBrushOutline(player, stack);
		check(helper, WorldshaperRenderHandler.previewPoints(player).size() == 36, "the preview has " + WorldshaperRenderHandler.previewPoints(player).size() + " points");
		// no settings, no preview
		ItemStack unset = new ItemStack(WandsRegistry.WORLDSHAPER);
		WorldshaperRenderHandler.createBrushOutline(player, unset);
		check(helper, WorldshaperRenderHandler.previewPoints(player).isEmpty(), "no preview without settings");
		// a big region shows a sample
		stack = shaper(TerrainBrushes.Sphere, 10, 1, 1, TerrainTools.Fill, PlacementOptions.Merged, Blocks.GOLD_BLOCK.defaultBlockState());
		WorldshaperRenderHandler.createBrushOutline(player, stack);
		int points = WorldshaperRenderHandler.previewPoints(player).size();
		check(helper, points > 0 && points <= WorldshaperRenderHandler.MAX_PARTICLES, "sample of " + points);
		done(helper);
	}

	// ---- wand of symmetry ----

	private static Vec3 corner(GameTestHelper helper, int x, int y, int z) {
		return Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(x, y, z)));
	}

	/** A player with a wand in the first hotbar slot, and slot 4 selected. */
	private static ServerPlayer holder(GameTestHelper helper, GameType type, SymmetryMirror mirror, boolean enabled) {
		ServerPlayer player = player(helper, type);
		ItemStack wand = new ItemStack(WandsRegistry.WAND_OF_SYMMETRY);
		SymmetryWandItem.configureSettings(wand, mirror);
		wand.set(WandsRegistry.SYMMETRY_WAND_ENABLE, enabled);
		player.getInventory().setItem(0, wand);
		player.getInventory().setSelectedSlot(4);
		player.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(0, 4, 0))));
		return player;
	}

	/** Places the stack's block on top of the floor block at {@code rel}, the way a right click does. */
	private static InteractionResult place(GameTestHelper helper, ServerPlayer player, ItemStack stack, int x, int z) {
		player.getInventory().setItem(player.getInventory().getSelectedSlot(), stack);
		BlockPos floor = helper.absolutePos(new BlockPos(x, 0, z));
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false);
		return stack.useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, stack, hit));
	}

	private static Set<BlockPos> blocksOf(GameTestHelper helper, Block block) {
		Set<BlockPos> found = new HashSet<>();
		for (int x = 0; x < 8; x++)
			for (int y = 1; y < 4; y++)
				for (int z = 0; z < 8; z++)
					if (at(helper, x, y, z).is(block))
						found.add(new BlockPos(x, y, z));
		return found;
	}

	private static Set<BlockPos> set(int... xz) {
		Set<BlockPos> result = new HashSet<>();
		for (int i = 0; i < xz.length; i += 2)
			result.add(new BlockPos(xz[i], 1, xz[i + 1]));
		return result;
	}

	@GameTest
	public void planeMirrorsAPlacedBlock(GameTestHelper helper) {
		floor(helper);
		// the mirror stands at (4,1,4) and divides along z, so z flips
		ServerPlayer player = holder(helper, GameType.CREATIVE, new PlaneMirror(corner(helper, 4, 1, 4)), true);
		check(helper, place(helper, player, new ItemStack(Items.STONE), 2, 6).consumesAction(), "placing works");
		check(helper, blocksOf(helper, Blocks.STONE).equals(set(2, 6, 2, 2)), "stones at " + blocksOf(helper, Blocks.STONE));

		// along the other axis x flips
		ServerPlayer other = holder(helper, GameType.CREATIVE, planeYZ(helper), true);
		place(helper, other, new ItemStack(Items.GOLD_BLOCK), 6, 5);
		check(helper, blocksOf(helper, Blocks.GOLD_BLOCK).equals(set(6, 5, 2, 5)), "gold at " + blocksOf(helper, Blocks.GOLD_BLOCK));
		done(helper);
	}

	private static SymmetryMirror planeYZ(GameTestHelper helper) {
		SymmetryMirror mirror = new PlaneMirror(corner(helper, 4, 1, 4));
		mirror.setOrientation(PlaneMirror.Align.YZ.ordinal());
		return mirror;
	}

	@GameTest
	public void crossPlaneMirrorsInThreeBlocks(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = holder(helper, GameType.CREATIVE, new CrossPlaneMirror(corner(helper, 4, 1, 4)), true);
		place(helper, player, new ItemStack(Items.STONE), 6, 5);
		check(helper, blocksOf(helper, Blocks.STONE).equals(set(6, 5, 2, 5, 6, 3, 2, 3)), "orthogonal: " + blocksOf(helper, Blocks.STONE));
		done(helper);
	}

	@GameTest
	public void diagonalCrossPlaneMirrorsAcrossTheDiagonals(GameTestHelper helper) {
		floor(helper);
		SymmetryMirror mirror = new CrossPlaneMirror(corner(helper, 4, 1, 4));
		mirror.setOrientation(CrossPlaneMirror.Align.D.ordinal());
		ServerPlayer player = holder(helper, GameType.CREATIVE, mirror, true);
		place(helper, player, new ItemStack(Items.STONE), 6, 5);
		check(helper, blocksOf(helper, Blocks.STONE).equals(set(6, 5, 3, 2, 5, 6, 2, 3)), "diagonal: " + blocksOf(helper, Blocks.STONE));
		done(helper);
	}

	@GameTest
	public void triplePlaneMirrorsInSevenBlocks(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = holder(helper, GameType.CREATIVE, new TriplePlaneMirror(corner(helper, 4, 1, 4)), true);
		place(helper, player, new ItemStack(Items.STONE), 6, 5);
		check(helper, blocksOf(helper, Blocks.STONE).equals(set(6, 5, 2, 5, 6, 3, 2, 3, 5, 6, 3, 6, 5, 2, 3, 2)),
			"octagonal: " + blocksOf(helper, Blocks.STONE));
		done(helper);
	}

	@GameTest
	public void mirroredBlocksAreFlippedToo(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = holder(helper, GameType.CREATIVE, new PlaneMirror(corner(helper, 4, 1, 4)), true);
		BlockPos floor = helper.absolutePos(new BlockPos(2, 0, 6));
		ItemStack stairs = new ItemStack(Items.OAK_STAIRS);
		player.getInventory().setItem(4, stairs);
		// facing north while clicking the top of the floor: the stairs face the player, i.e. north when looking north
		player.setYRot(180);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false);
		stairs.useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, stairs, hit));
		Direction placed = at(helper, 2, 1, 6).getValue(BlockStateProperties.HORIZONTAL_FACING);
		Direction mirrored = at(helper, 2, 1, 2).getValue(BlockStateProperties.HORIZONTAL_FACING);
		check(helper, mirrored == placed.getOpposite() && placed.getAxis() == Direction.Axis.Z, "stairs face " + placed + " and " + mirrored);

		check(helper, new PlaneMirror(Vec3.ZERO).process(BlockPos.ZERO.south(2), Blocks.STONE.defaultBlockState()).containsKey(BlockPos.ZERO.north(2)),
			"the position is mirrored");
		done(helper);
	}

	@GameTest
	public void mirrorStateCodecRoundTrips(GameTestHelper helper) {
		for (SymmetryMirror mirror : List.of(new PlaneMirror(new Vec3(1, 2, 3)), new CrossPlaneMirror(new Vec3(-4, 5, 6)),
			new TriplePlaneMirror(new Vec3(7, 8, 9)), new EmptyMirror(Vec3.ZERO))) {
			if (mirror instanceof PlaneMirror)
				mirror.setOrientation(1);
			if (mirror instanceof CrossPlaneMirror)
				mirror.setOrientation(1);
			var encoded = SymmetryMirror.CODEC.encodeStart(NbtOps.INSTANCE, mirror).getOrThrow();
			SymmetryMirror decoded = SymmetryMirror.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
			check(helper, decoded.getClass() == mirror.getClass(), mirror.typeName() + " type");
			check(helper, decoded.equals(mirror), mirror.typeName() + " state");
			check(helper, decoded.copy().equals(mirror) && decoded.copy() != decoded, mirror.typeName() + " copy");
		}
		helper.succeed();
	}

	@GameTest
	public void survivalPlayersPayForMirroredBlocks(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = holder(helper, GameType.SURVIVAL, new PlaneMirror(corner(helper, 4, 1, 4)), true);
		ItemStack stack = new ItemStack(Items.STONE, 5);
		place(helper, player, stack, 2, 6);
		check(helper, blocksOf(helper, Blocks.STONE).equals(set(2, 6, 2, 2)), "both placed: " + blocksOf(helper, Blocks.STONE));
		check(helper, stack.getCount() == 3, "two stones were used, " + stack.getCount() + " left");

		// stones from elsewhere in the inventory pay for the mirrored block, when the hand's stack is spent
		ServerPlayer second = holder(helper, GameType.SURVIVAL, new PlaneMirror(corner(helper, 4, 1, 4)), true);
		second.getInventory().setItem(20, new ItemStack(Items.GOLD_BLOCK, 2));
		ItemStack one = new ItemStack(Items.GOLD_BLOCK, 1);
		place(helper, second, one, 5, 6);
		check(helper, blocksOf(helper, Blocks.GOLD_BLOCK).equals(set(5, 6, 5, 2)), "gold placed twice: " + blocksOf(helper, Blocks.GOLD_BLOCK));
		check(helper, second.getInventory().getItem(20).getCount() == 1, "the stack in the inventory paid");

		// nothing left to pay with: only the block that was placed
		ServerPlayer broke = holder(helper, GameType.SURVIVAL, new PlaneMirror(corner(helper, 4, 1, 4)), true);
		place(helper, broke, new ItemStack(Items.DIAMOND_BLOCK, 1), 6, 6);
		check(helper, blocksOf(helper, Blocks.DIAMOND_BLOCK).equals(set(6, 6)), "no mirror without items: " + blocksOf(helper, Blocks.DIAMOND_BLOCK));
		done(helper);
	}

	@GameTest
	public void survivalMirrorsDoNotReplaceSolidBlocks(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(2, 1, 2, Blocks.DIRT);
		ServerPlayer player = holder(helper, GameType.SURVIVAL, new PlaneMirror(corner(helper, 4, 1, 4)), true);
		ItemStack stack = new ItemStack(Items.STONE, 5);
		place(helper, player, stack, 2, 6);
		check(helper, at(helper, 2, 1, 2).is(Blocks.DIRT), "dirt is not replaced");
		check(helper, stack.getCount() == 4, "and nothing was paid for it");
		done(helper);
	}

	@GameTest
	public void wandNeedsToBeEnabledInTheHotbarAndInRange(GameTestHelper helper) {
		floor(helper);
		// disabled
		ServerPlayer off = holder(helper, GameType.CREATIVE, new PlaneMirror(corner(helper, 4, 1, 4)), false);
		place(helper, off, new ItemStack(Items.STONE), 2, 6);
		check(helper, blocksOf(helper, Blocks.STONE).equals(set(2, 6)), "a disabled wand does not mirror");

		// in the inventory but not in the hotbar
		ServerPlayer stored = holder(helper, GameType.CREATIVE, new PlaneMirror(corner(helper, 4, 1, 4)), true);
		ItemStack wand = stored.getInventory().getItem(0);
		stored.getInventory().setItem(0, ItemStack.EMPTY);
		stored.getInventory().setItem(12, wand);
		place(helper, stored, new ItemStack(Items.GOLD_BLOCK), 5, 6);
		check(helper, blocksOf(helper, Blocks.GOLD_BLOCK).equals(set(5, 6)), "a wand outside the hotbar does not mirror");

		// too far away from the mirror
		SymmetryWandConfig.maxSymmetryWandRange = 1;
		try {
			ServerPlayer far = holder(helper, GameType.CREATIVE, new PlaneMirror(corner(helper, 4, 1, 4)), true);
			place(helper, far, new ItemStack(Items.EMERALD_BLOCK), 6, 6);
			check(helper, blocksOf(helper, Blocks.EMERALD_BLOCK).equals(set(6, 6)), "out of range does not mirror");
		} finally {
			SymmetryWandConfig.maxSymmetryWandRange = 50;
		}

		// an empty mirror does nothing
		ServerPlayer empty = holder(helper, GameType.CREATIVE, new EmptyMirror(Vec3.ZERO), true);
		place(helper, empty, new ItemStack(Items.DIAMOND_BLOCK), 1, 1);
		check(helper, blocksOf(helper, Blocks.DIAMOND_BLOCK).equals(set(1, 1)), "an empty mirror does not mirror");
		done(helper);
	}

	@GameTest
	public void breakingABlockBreaksTheMirroredOnes(GameTestHelper helper) {
		floor(helper);
		for (int[] p : new int[][] { { 6, 5 }, { 2, 5 }, { 6, 3 }, { 2, 3 } })
			helper.setBlock(p[0], 1, p[1], Blocks.STONE);
		ServerPlayer player = holder(helper, GameType.CREATIVE, new CrossPlaneMirror(corner(helper, 4, 1, 4)), true);
		check(helper, player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(6, 1, 5))), "the block breaks");
		check(helper, blocksOf(helper, Blocks.STONE).isEmpty(), "all four are gone: " + blocksOf(helper, Blocks.STONE));
		done(helper);
	}

	@GameTest
	public void survivalBreakingOnlyBreaksTheSameKind(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(6, 1, 5, Blocks.STONE);
		helper.setBlock(2, 1, 5, Blocks.STONE);
		helper.setBlock(6, 1, 3, Blocks.DIRT);
		helper.setBlock(2, 1, 3, Blocks.STONE);
		ServerPlayer player = holder(helper, GameType.SURVIVAL, new CrossPlaneMirror(corner(helper, 4, 1, 4)), true);
		player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(6, 1, 5)));
		check(helper, blocksOf(helper, Blocks.STONE).isEmpty(), "the stones are gone: " + blocksOf(helper, Blocks.STONE));
		check(helper, at(helper, 6, 1, 3).is(Blocks.DIRT), "the dirt stays");
		done(helper);
	}

	@GameTest
	public void wandUseSetsTheMirror(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		ItemStack wand = new ItemStack(WandsRegistry.WAND_OF_SYMMETRY);
		player.setItemInHand(InteractionHand.MAIN_HAND, wand);
		check(helper, !SymmetryWandItem.isEnabled(wand), "disabled to begin with");

		// facing south (yaw 0): the plane lies along x
		player.setYRot(0);
		BlockPos clicked = helper.absolutePos(new BlockPos(3, 0, 3));
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(clicked).add(0, .5, 0), Direction.UP, clicked, false);
		check(helper, wand.getItem().useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, wand, hit)).consumesAction(),
			"use on a block");
		SymmetryMirror mirror = SymmetryWandItem.getMirror(wand);
		check(helper, mirror instanceof PlaneMirror && mirror.getOrientationIndex() == PlaneMirror.Align.XY.ordinal(), "a plane along x, was " + mirror.getOrientation());
		check(helper, mirror.getPosition().equals(Vec3.atLowerCornerOf(clicked.above())), "above the clicked block, was " + mirror.getPosition());
		check(helper, SymmetryWandItem.isEnabled(wand), "enabled");

		// the type stays, the position and the orientation follow
		SymmetryWandItem.configureSettings(wand, new CrossPlaneMirror(Vec3.ZERO));
		player.setYRot(90);
		player.setYHeadRot(45);
		wand.getItem().useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, wand, hit));
		mirror = SymmetryWandItem.getMirror(wand);
		check(helper, mirror instanceof CrossPlaneMirror && mirror.getPosition().equals(Vec3.atLowerCornerOf(clicked.above())), "moved");
		check(helper, mirror.getOrientationIndex() == CrossPlaneMirror.Align.D.ordinal(), "diagonal at 45 degrees");

		// using it with no target clears it
		check(helper, wand.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS, "use in the air");
		check(helper, !SymmetryWandItem.isEnabled(wand), "disabled again");
		done(helper);
	}

	@GameTest
	public void wandGuiSavesTheSettingsOnTheItem(GameTestHelper helper) {
		ServerPlayer player = player(helper, GameType.CREATIVE);
		ItemStack wand = new ItemStack(WandsRegistry.WAND_OF_SYMMETRY);
		SymmetryWandItem.configureSettings(wand, new PlaneMirror(new Vec3(7, 8, 9)));
		player.setItemInHand(InteractionHand.MAIN_HAND, wand);
		SymmetryWandGui gui = new SymmetryWandGui(player, wand, InteractionHand.MAIN_HAND);
		check(helper, gui.open(), "the GUI opens");

		// the second orientation of the plane
		click(gui, 12);
		check(helper, SymmetryWandItem.getMirror(wand) instanceof PlaneMirror
			&& SymmetryWandItem.getMirror(wand).getOrientationIndex() == PlaneMirror.Align.YZ.ordinal(), "plane along z");

		// the double plane keeps the position and starts orthogonal; then its diagonal orientation
		click(gui, 3);
		SymmetryMirror mirror = SymmetryWandItem.getMirror(wand);
		check(helper, mirror instanceof CrossPlaneMirror && mirror.getOrientationIndex() == 0, "cross plane");
		check(helper, mirror.getPosition().equals(new Vec3(7, 8, 9)), "the position is kept");
		click(gui, 12);
		check(helper, SymmetryWandItem.getMirror(wand).getOrientationIndex() == CrossPlaneMirror.Align.D.ordinal(), "diagonal");

		// the triple plane has a single orientation
		click(gui, 4);
		check(helper, SymmetryWandItem.getMirror(wand) instanceof TriplePlaneMirror, "triple plane");
		check(helper, gui.getGuiElement(12) == null && gui.getGuiElement(11) != null, "one orientation");

		// back to a single plane, picked again
		click(gui, 2);
		check(helper, SymmetryWandItem.getMirror(wand) instanceof PlaneMirror, "plane");
		gui.close();

		// the settings are on the item, also after saving it
		var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		var encoded = ItemStack.OPTIONAL_CODEC.encodeStart(ops, wand).getOrThrow();
		ItemStack decoded = ItemStack.OPTIONAL_CODEC.parse(ops, encoded).getOrThrow();
		check(helper, SymmetryWandItem.getMirror(decoded).equals(SymmetryWandItem.getMirror(wand)), "the mirror survives saving");
		check(helper, SymmetryWandItem.getMirror(decoded).getPosition().equals(new Vec3(7, 8, 9)), "with its position");
		done(helper);
	}

	@GameTest
	public void wandGuiStartsWithAPlaneWhenThereIsNoMirror(GameTestHelper helper) {
		ServerPlayer player = player(helper, GameType.CREATIVE);
		ItemStack wand = new ItemStack(WandsRegistry.WAND_OF_SYMMETRY);
		player.setItemInHand(InteractionHand.MAIN_HAND, wand);
		SymmetryWandGui gui = new SymmetryWandGui(player, wand, InteractionHand.MAIN_HAND);
		gui.open();
		gui.close();
		check(helper, SymmetryWandItem.getMirror(wand) instanceof PlaneMirror, "closing stores a plane");
		check(helper, !SymmetryWandItem.isEnabled(wand), "which is not enabled");
		done(helper);
	}

	@GameTest
	public void mirrorsAreShownToTheirHolder(GameTestHelper helper) {
		ServerPlayer player = holder(helper, GameType.CREATIVE, new PlaneMirror(corner(helper, 4, 1, 4)), true);
		var server = helper.getLevel().getServer();
		SymmetryHandler.tick(server);
		check(helper, SymmetryHandler.displayCount(player) == 1, "one mirror shown, " + SymmetryHandler.displayCount(player));
		SymmetryHandler.tick(server);
		SymmetryHandler.tick(server);
		check(helper, SymmetryHandler.displayCount(player) == 1, "still one");

		// a second wand in the hotbar shows its own mirror
		ItemStack second = new ItemStack(WandsRegistry.WAND_OF_SYMMETRY);
		SymmetryWandItem.configureSettings(second, new TriplePlaneMirror(corner(helper, 1, 1, 1)));
		second.set(WandsRegistry.SYMMETRY_WAND_ENABLE, true);
		player.getInventory().setItem(1, second);
		SymmetryHandler.tick(server);
		check(helper, SymmetryHandler.displayCount(player) == 2, "two mirrors shown");

		// disabling hides it
		player.getInventory().getItem(0).set(WandsRegistry.SYMMETRY_WAND_ENABLE, false);
		SymmetryHandler.tick(server);
		check(helper, SymmetryHandler.displayCount(player) == 1, "one hidden");
		player.getInventory().setItem(1, ItemStack.EMPTY);
		SymmetryHandler.tick(server);
		check(helper, SymmetryHandler.displayCount(player) == 0, "none shown");
		done(helper);
	}

	@GameTest
	public void mirrorModelsAreInTheResourcePack(GameTestHelper helper) {
		for (SymmetryMirror mirror : List.of(new PlaneMirror(Vec3.ZERO), new CrossPlaneMirror(Vec3.ZERO), new TriplePlaneMirror(Vec3.ZERO))) {
			String path = mirror.getModelPath();
			check(helper, WandsGameTests.class.getResource("/assets/create/models/block/" + path + ".json") != null, "model of " + mirror.typeName());
		}
		check(helper, new EmptyMirror(Vec3.ZERO).getModelPath() == null, "an empty mirror has no model");
		check(helper, new PlaneMirror(Vec3.ZERO).getModelRotationDegrees() == 0, "plane along z");
		check(helper, planeYZ(helper).getModelRotationDegrees() == 90, "plane along x is turned");
		SymmetryMirror diagonal = new CrossPlaneMirror(Vec3.ZERO);
		diagonal.setOrientation(1);
		check(helper, diagonal.getModelRotationDegrees() == 45, "diagonal cross is turned");
		for (String item : List.of("handheld_worldshaper", "wand_of_symmetry"))
			check(helper, WandsGameTests.class.getResource("/assets/create/models/item/" + item + ".json") != null, "item model of " + item);
		helper.succeed();
	}
}
