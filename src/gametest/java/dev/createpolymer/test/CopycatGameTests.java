package dev.createpolymer.test;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.decoration.copycat.CopycatBlock;
import dev.createpolymer.decoration.copycat.CopycatBlockEntity;
import dev.createpolymer.decoration.copycat.CopycatModel;
import dev.createpolymer.decoration.copycat.CopycatPanelBlock;
import dev.createpolymer.decoration.copycat.CopycatPanelModel;
import dev.createpolymer.decoration.copycat.CopycatStepBlock;
import dev.createpolymer.decoration.copycat.CopycatStepModel;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.CopycatRegistry;
import dev.createpolymer.registry.feature.MaterialsRegistry;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class CopycatGameTests {

	private static final BlockPos P = new BlockPos(2, 1, 2);

	private static void check(GameTestHelper helper, boolean condition, String message) {
		helper.assertTrue(condition, message);
	}

	private static final Map<GameTestHelper, List<ServerPlayer>> PLAYERS = new IdentityHashMap<>();

	/** A player with a connection (GameTestHelper's mock players have none, and wrenching sends packets). */
	private static ServerPlayer player(GameTestHelper helper, GameType type) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(type);
		PLAYERS.computeIfAbsent(helper, h -> new ArrayList<>()).add(player);
		return player;
	}

	/** Ends the test: the players it made leave and the test succeeds. */
	@SuppressWarnings("removal")
	private static void done(GameTestHelper helper) {
		for (ServerPlayer player : PLAYERS.getOrDefault(helper, List.of())) {
			player.connection.disconnect(net.minecraft.network.chat.Component.literal("test over"));
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		PLAYERS.remove(helper);
		helper.succeed();
	}

	private static BlockHitResult hit(GameTestHelper helper, BlockPos rel, Direction face, double y) {
		BlockPos pos = helper.absolutePos(rel);
		Vec3 location = Vec3.atCenterOf(pos).add(face.getStepX() * .5, 0, face.getStepZ() * .5);
		location = new Vec3(location.x, pos.getY() + y, location.z);
		return new BlockHitResult(location, face, pos, false);
	}

	/** Right-clicks the block at {@code rel} with {@code stack} in the main hand. */
	private static InteractionResult use(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos rel,
		Direction face) {
		return use(helper, player, stack, rel, hit(helper, rel, face, .5));
	}

	private static InteractionResult use(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos rel,
		BlockHitResult hit) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		return helper.getBlockState(rel)
			.useItemOn(stack, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
	}

	private static InteractionResult wrench(GameTestHelper helper, ServerPlayer player, BlockPos rel) {
		ItemStack wrench = new ItemStack(ModItems.WRENCH);
		player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
		return wrench.getItem()
			.useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, wrench,
				hit(helper, rel, Direction.UP, 1)));
	}

	private static CopycatBlockEntity be(GameTestHelper helper, BlockPos rel) {
		return helper.getBlockEntity(rel, CopycatBlockEntity.class);
	}

	private static BlockState panel(Direction facing) {
		return CopycatRegistry.COPYCAT_PANEL.defaultBlockState()
			.setValue(CopycatPanelBlock.FACING, facing);
	}

	private static BlockState step(Direction facing, Half half) {
		return CopycatRegistry.COPYCAT_STEP.defaultBlockState()
			.setValue(CopycatStepBlock.FACING, facing)
			.setValue(CopycatStepBlock.HALF, half);
	}

	private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
		int count = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++)
			if (player.getInventory().getItem(i).is(item))
				count += player.getInventory().getItem(i).getCount();
		return count;
	}

	private static int droppedItems(GameTestHelper helper, net.minecraft.world.item.Item item) {
		int count = 0;
		for (ItemEntity entity : helper.getEntities(EntityTypes.ITEM, P, 6))
			if (entity.getItem().is(item))
				count += entity.getItem().getCount();
		return count;
	}

	/** The elements that currently draw the copycat at {@code rel}. */
	private static List<VirtualElement> pieces(GameTestHelper helper, BlockPos rel) {
		BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(rel));
		check(helper, attachment != null && attachment.holder() instanceof CopycatModel, "the copycat has no model");
		return ((CopycatModel) attachment.holder()).getPieces();
	}

	private static BlockDisplayElement materialDisplay(GameTestHelper helper, BlockPos rel) {
		List<BlockDisplayElement> displays = pieces(helper, rel).stream()
			.filter(BlockDisplayElement.class::isInstance)
			.map(BlockDisplayElement.class::cast)
			.toList();
		check(helper, displays.size() == 1, "expected one block display, found " + displays.size());
		return displays.get(0);
	}

	/** The box (min x, y, z, max x, y, z in blocks from the block's corner) a block display covers. */
	private static float[] extent(BlockDisplayElement display) {
		Matrix4f m = new Matrix4f().translation(display.getTranslation())
			.rotate(display.getLeftRotation())
			.scale(display.getScale())
			.rotate(display.getRightRotation());
		Vector3f a = m.transformPosition(new Vector3f(0, 0, 0));
		Vector3f b = m.transformPosition(new Vector3f(1, 1, 1));
		return new float[] { Math.min(a.x, b.x) + .5f, Math.min(a.y, b.y) + .5f, Math.min(a.z, b.z) + .5f,
			Math.max(a.x, b.x) + .5f, Math.max(a.y, b.y) + .5f, Math.max(a.z, b.z) + .5f };
	}

	/** Whether {@code outer} has {@code inner} inside (touching edges count). */
	private static boolean covers(AABB outer, AABB inner) {
		return outer.minX <= inner.minX + 1e-9 && outer.minY <= inner.minY + 1e-9 && outer.minZ <= inner.minZ + 1e-9
			&& outer.maxX >= inner.maxX - 1e-9 && outer.maxY >= inner.maxY - 1e-9 && outer.maxZ >= inner.maxZ - 1e-9;
	}

	private static void assertExtent(GameTestHelper helper, float[] actual, float... expectedPixels) {
		for (int i = 0; i < 6; i++)
			check(helper, Math.abs(actual[i] * 16 - expectedPixels[i]) < .01f,
				"the box is " + java.util.Arrays.toString(actual) + " (in blocks), expected pixels "
					+ java.util.Arrays.toString(expectedPixels));
	}

	// ---- registration ----

	@GameTest
	public void everythingIsRegisteredWithCreatesIds(GameTestHelper helper) {
		for (String name : List.of("copycat_base", "copycat_step", "copycat_panel", "copycat_bars"))
			check(helper, BuiltInRegistries.BLOCK.containsKey(CreatePolymer.id(name)), "no block " + name);
		check(helper, BuiltInRegistries.ITEM.containsKey(CreatePolymer.id("copycat_step")), "no copycat_step item");
		check(helper, BuiltInRegistries.ITEM.containsKey(CreatePolymer.id("copycat_panel")), "no copycat_panel item");
		check(helper, !BuiltInRegistries.ITEM.containsKey(CreatePolymer.id("copycat_base")), "copycat_base has an item");
		check(helper, !BuiltInRegistries.ITEM.containsKey(CreatePolymer.id("copycat_bars")), "copycat_bars has an item");
		check(helper, BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(CreatePolymer.id("copycat")), "no copycat block entity");
		check(helper, CopycatRegistry.COPYCAT_BLOCK_ENTITY.isValid(CopycatRegistry.COPYCAT_PANEL.defaultBlockState())
			&& CopycatRegistry.COPYCAT_BLOCK_ENTITY.isValid(CopycatRegistry.COPYCAT_STEP.defaultBlockState()),
			"the block entity does not accept the panel and the step");
		helper.succeed();
	}

	@GameTest
	public void stonecuttingRecipesGiveFourFromAZincIngot(GameTestHelper helper) {
		var manager = helper.getLevel().getServer().getRecipeManager();
		for (String name : List.of("copycat_panel", "copycat_step")) {
			ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE,
				CreatePolymer.id(name + "_from_ingots_zinc_stonecutting"));
			check(helper, manager.byKey(key).isPresent(), "recipe for " + name + " not loaded");
			check(helper, manager.byKey(key).get().value() instanceof StonecutterRecipe, name + " recipe is not a stonecutting recipe");
		}
		var offered = manager.stonecutterRecipes().selectByInput(new ItemStack(MaterialsRegistry.ZINC_INGOT));
		long copycats = offered.entries().stream()
			.filter(e -> e.recipe().recipe().map(r -> r.id().identifier().getPath().startsWith("copycat_")).orElse(false))
			.count();
		check(helper, copycats == 2, "the stonecutter offers " + copycats + " copycat recipes for a zinc ingot");
		helper.succeed();
	}

	// ---- applying a material ----

	@GameTest
	public void aBlockItemBecomesTheMaterialAndIsConsumed(GameTestHelper helper) {
		TestUtil.floor(helper);
		helper.setBlock(P, panel(Direction.UP));
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		ItemStack stone = new ItemStack(Items.STONE, 3);

		InteractionResult result = use(helper, player, stone, P, Direction.UP);
		check(helper, result.consumesAction(), "applying stone was refused: " + result);
		check(helper, stone.getCount() == 2, "one stone should be used up, " + stone.getCount() + " left");
		CopycatBlockEntity copycat = be(helper, P);
		check(helper, copycat.getMaterial().is(Blocks.STONE), "the material is " + copycat.getMaterial());
		check(helper, copycat.hasCustomMaterial(), "no custom material");
		check(helper, copycat.getConsumedItem().is(Items.STONE) && copycat.getConsumedItem().getCount() == 1,
			"the consumed item is " + copycat.getConsumedItem());

		// the last item is used up completely
		ItemStack last = new ItemStack(Items.STONE);
		helper.setBlock(P.east(), step(Direction.SOUTH, Half.BOTTOM));
		check(helper, use(helper, player, last, P.east(), Direction.UP).consumesAction(), "stone on a step was refused");
		check(helper, player.getMainHandItem().isEmpty(), "the used up stack stays in hand");
		check(helper, be(helper, P.east()).getMaterial().is(Blocks.STONE), "the step did not take the stone");
		done(helper);
	}

	@GameTest
	public void creativePlayersKeepTheirItem(GameTestHelper helper) {
		TestUtil.floor(helper);
		helper.setBlock(P, panel(Direction.UP));
		ServerPlayer player = player(helper, GameType.CREATIVE);
		ItemStack stone = new ItemStack(Items.STONE, 3);
		check(helper, use(helper, player, stone, P, Direction.UP).consumesAction(), "applying stone was refused");
		check(helper, stone.getCount() == 3, "creative players do not lose the item, " + stone.getCount() + " left");
		check(helper, be(helper, P).getMaterial().is(Blocks.STONE), "material not applied");
		done(helper);
	}

	@GameTest
	public void materialsThatDoNotFitAreRejected(GameTestHelper helper) {
		TestUtil.floor(helper);
		helper.setBlock(P, step(Direction.SOUTH, Half.BOTTOM));
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		List<ItemStack> rejected = List.of(
			new ItemStack(Items.OAK_STAIRS),      // StairBlock
			new ItemStack(Items.OAK_SLAB),        // not a full block
			new ItemStack(Items.CHEST),           // block entity
			new ItemStack(Items.OAK_SAPLING),     // create:copycat_deny (saplings)
			new ItemStack(Items.LADDER),          // create:copycat_deny (climbable)
			new ItemStack(Items.CAULDRON),        // create:copycat_deny (cauldrons)
			new ItemStack(Items.TORCH),           // no collision, not a full block
			new ItemStack(Items.GLASS_PANE),      // panes are not bars
			new ItemStack(Items.IRON_BARS),       // bars only fit panels
			new ItemStack(Items.OAK_TRAPDOOR),    // trapdoors only fit panels
			new ItemStack(CopycatRegistry.COPYCAT_PANEL_ITEM), // copycats do not copy copycats
			new ItemStack(Items.STICK));          // not a block
		for (ItemStack stack : rejected) {
			int before = stack.getCount();
			InteractionResult result = use(helper, player, stack, P, Direction.UP);
			check(helper, !result.consumesAction(), stack.getItem() + " was accepted: " + result);
			check(helper, stack.getCount() == before, stack.getItem() + " was used up");
			check(helper, !be(helper, P).hasCustomMaterial(), stack.getItem() + " became the material");
		}
		done(helper);
	}

	@GameTest
	public void barrelsAreAllowedByTag(GameTestHelper helper) {
		TestUtil.floor(helper);
		helper.setBlock(P, step(Direction.SOUTH, Half.BOTTOM));
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		// a barrel has a block entity, only the create:copycat_allow tag lets it through
		check(helper, use(helper, player, new ItemStack(Items.BARREL), P, Direction.NORTH).consumesAction(), "barrel was refused");
		check(helper, be(helper, P).getMaterial().is(Blocks.BARREL), "the material is " + be(helper, P).getMaterial());
		// the state follows the side that was clicked: a barrel clicked from the north faces north
		check(helper, be(helper, P).getMaterial().getValue(BlockStateProperties.FACING) == Direction.NORTH,
			"the barrel faces " + be(helper, P).getMaterial().getValue(BlockStateProperties.FACING));
		done(helper);
	}

	@GameTest
	public void panelsTakeBarsAndTrapdoors(GameTestHelper helper) {
		TestUtil.floor(helper);
		ServerPlayer player = player(helper, GameType.SURVIVAL);

		helper.setBlock(P, panel(Direction.UP));
		check(helper, use(helper, player, new ItemStack(Items.IRON_BARS, 2), P, Direction.UP).consumesAction(), "bars were refused by a panel");
		check(helper, be(helper, P).getMaterial().is(Blocks.IRON_BARS), "bars not applied");

		// on a floor panel a trapdoor lies flat, closed, facing the way the player does
		helper.setBlock(P.east(), panel(Direction.UP));
		check(helper, use(helper, player, new ItemStack(Items.OAK_TRAPDOOR), P.east(), Direction.UP).consumesAction(), "trapdoor refused");
		BlockState trapdoor = be(helper, P.east()).getMaterial();
		check(helper, trapdoor.is(Blocks.OAK_TRAPDOOR) && !trapdoor.getValue(TrapDoorBlock.OPEN)
			&& trapdoor.getValue(TrapDoorBlock.FACING) == player.getDirection(), "floor trapdoor: " + trapdoor);

		// on a ceiling panel it hangs from the top
		helper.setBlock(P.east().east(), panel(Direction.DOWN));
		use(helper, player, new ItemStack(Items.OAK_TRAPDOOR), P.east().east(), Direction.DOWN);
		BlockState ceiling = be(helper, P.east().east()).getMaterial();
		check(helper, ceiling.getValue(TrapDoorBlock.HALF) == Half.TOP && !ceiling.getValue(TrapDoorBlock.OPEN), "ceiling trapdoor: " + ceiling);

		// on a wall panel it is open, facing like the panel, on the half that was clicked
		helper.setBlock(P.north(), panel(Direction.SOUTH));
		BlockHitResult upper = hit(helper, P.north(), Direction.SOUTH, .9);
		use(helper, player, new ItemStack(Items.OAK_TRAPDOOR), P.north(), upper);
		BlockState wall = be(helper, P.north()).getMaterial();
		check(helper, wall.getValue(TrapDoorBlock.OPEN) && wall.getValue(TrapDoorBlock.FACING) == Direction.SOUTH
			&& wall.getValue(TrapDoorBlock.HALF) == Half.TOP, "wall trapdoor: " + wall);
		done(helper);
	}

	@GameTest
	public void theSameBlockTurnsTheMaterialAndOtherBlocksAreIgnored(GameTestHelper helper) {
		TestUtil.floor(helper);
		helper.setBlock(P, panel(Direction.UP));
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		ItemStack logs = new ItemStack(Items.OAK_LOG, 8);
		use(helper, player, logs, P, Direction.UP);
		check(helper, be(helper, P).getMaterial().getValue(BlockStateProperties.AXIS) == net.minecraft.core.Direction.Axis.Y,
			"a log clicked on top is upright: " + be(helper, P).getMaterial());
		check(helper, logs.getCount() == 7, "one log is used");

		// another log of the same kind cycles the axis and is not used
		InteractionResult result = use(helper, player, logs, P, Direction.UP);
		check(helper, result.consumesAction(), "cycling was refused");
		check(helper, logs.getCount() == 7, "cycling used up an item");
		check(helper, be(helper, P).getMaterial().getValue(BlockStateProperties.AXIS) == net.minecraft.core.Direction.Axis.Z,
			"the axis did not turn: " + be(helper, P).getMaterial());
		check(helper, be(helper, P).getConsumedItem().is(Items.OAK_LOG), "the consumed item changed");

		// a different block is not accepted while there is a material
		ItemStack stone = new ItemStack(Items.STONE, 2);
		check(helper, !use(helper, player, stone, P, Direction.UP).consumesAction(), "stone replaced a log");
		check(helper, stone.getCount() == 2 && be(helper, P).getMaterial().is(Blocks.OAK_LOG), "the material changed");
		done(helper);
	}

	@GameTest
	public void theOffhandBlockIsAppliedWhenPlacing(GameTestHelper helper) {
		TestUtil.floor(helper);
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE_BRICKS, 2));
		ItemStack panelItem = new ItemStack(CopycatRegistry.COPYCAT_PANEL_ITEM, 2);
		helper.placeAt(player, panelItem, new BlockPos(2, 0, 2), Direction.UP);

		check(helper, helper.getBlockState(P).is(CopycatRegistry.COPYCAT_PANEL), "panel not placed: " + helper.getBlockState(P));
		check(helper, be(helper, P).getMaterial().is(Blocks.STONE_BRICKS), "the off hand block was not applied");
		check(helper, player.getOffhandItem().getCount() == 1, "the off hand stack should lose one");
		done(helper);
	}

	// ---- removing the material ----

	@GameTest
	public void theWrenchTakesTheMaterialOffAndGivesTheItemBack(GameTestHelper helper) {
		TestUtil.floor(helper);
		helper.setBlock(P, panel(Direction.NORTH));
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		use(helper, player, new ItemStack(Items.STONE, 1), P, Direction.NORTH);
		check(helper, be(helper, P).hasCustomMaterial(), "material not applied");

		InteractionResult result = wrench(helper, player, P);
		check(helper, result.consumesAction(), "the wrench did nothing: " + result);
		check(helper, !be(helper, P).hasCustomMaterial() && be(helper, P).getConsumedItem().isEmpty(), "the material is still there");
		check(helper, count(player, Items.STONE) == 1, "the player got " + count(player, Items.STONE) + " stone back");
		check(helper, helper.getBlockState(P).is(CopycatRegistry.COPYCAT_PANEL)
			&& helper.getBlockState(P).getValue(CopycatPanelBlock.FACING) == Direction.NORTH, "the wrench rotated or removed the panel");

		// without a material the wrench does not turn the copycat either
		wrench(helper, player, P);
		check(helper, helper.getBlockState(P).getValue(CopycatPanelBlock.FACING) == Direction.NORTH, "the wrench rotated the panel");
		done(helper);
	}

	@GameTest
	public void wrenchingInCreativeGivesNothingBack(GameTestHelper helper) {
		TestUtil.floor(helper);
		helper.setBlock(P, step(Direction.SOUTH, Half.BOTTOM));
		ServerPlayer player = player(helper, GameType.CREATIVE);
		use(helper, player, new ItemStack(Items.STONE, 1), P, Direction.UP);
		wrench(helper, player, P);
		check(helper, !be(helper, P).hasCustomMaterial(), "the material is still there");
		check(helper, count(player, Items.STONE) == 0, "a creative player got the stone");
		done(helper);
	}

	@GameTest
	public void sneakWrenchingPicksUpTheCopycatAndTheMaterial(GameTestHelper helper) {
		TestUtil.floor(helper);
		helper.setBlock(P, panel(Direction.UP));
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		use(helper, player, new ItemStack(Items.STONE, 1), P, Direction.UP);
		player.setShiftKeyDown(true);
		wrench(helper, player, P);
		player.setShiftKeyDown(false);

		check(helper, helper.getBlockState(P).isAir(), "the copycat is still there");
		int stone = count(player, Items.STONE) + droppedItems(helper, Items.STONE);
		int panels = count(player, CopycatRegistry.COPYCAT_PANEL_ITEM) + droppedItems(helper, CopycatRegistry.COPYCAT_PANEL_ITEM);
		check(helper, stone == 1, "there are " + stone + " stone");
		check(helper, panels == 1, "there are " + panels + " panels");
		done(helper);
	}

	// ---- drops ----

	@GameTest
	public void removingTheBlockPopsTheMaterial(GameTestHelper helper) {
		TestUtil.floor(helper);
		helper.setBlock(P, panel(Direction.UP));
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		use(helper, player, new ItemStack(Items.GLOWSTONE, 1), P, Direction.UP);
		helper.getLevel().destroyBlock(helper.absolutePos(P), false);
		helper.runAfterDelay(2, () -> {
			check(helper, droppedItems(helper, Items.GLOWSTONE) == 1, "the glowstone was not dropped");
			check(helper, droppedItems(helper, CopycatRegistry.COPYCAT_PANEL_ITEM) == 0, "the panel was dropped without loot");
			done(helper);
		});
	}

	@GameTest
	public void theLootTableDropsTheCopycat(GameTestHelper helper) {
		TestUtil.floor(helper);
		for (BlockState state : List.of(panel(Direction.EAST), step(Direction.WEST, Half.TOP))) {
			helper.setBlock(P, state);
			var drops = Block.getDrops(helper.getBlockState(P), helper.getLevel(), helper.absolutePos(P), helper.getLevel().getBlockEntity(helper.absolutePos(P)));
			check(helper, drops.size() == 1 && drops.get(0).is(state.getBlock().asItem()), state.getBlock() + " drops " + drops);
		}
		check(helper, Block.getDrops(CopycatRegistry.COPYCAT_BASE.defaultBlockState(), helper.getLevel(), BlockPos.ZERO, null).isEmpty(),
			"copycat_base drops something");
		helper.succeed();
	}

	@GameTest
	public void theCopycatDropsOnlyIfTheMaterialCanBeHarvested(GameTestHelper helper) {
		TestUtil.floor(helper);
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		helper.setBlock(P, panel(Direction.UP));
		use(helper, player, new ItemStack(Items.STONE, 1), P, Direction.UP);
		BlockPos pos = helper.absolutePos(P);
		BlockState state = helper.getBlockState(P);
		BlockEntity blockEntity = helper.getLevel().getBlockEntity(pos);

		// stone needs a pickaxe: by hand nothing drops
		ItemStack hand = ItemStack.EMPTY;
		player.setItemInHand(InteractionHand.MAIN_HAND, hand);
		((CopycatBlock) state.getBlock()).playerDestroy(helper.getLevel(), player, pos, state, blockEntity, hand);
		check(helper, droppedItems(helper, CopycatRegistry.COPYCAT_PANEL_ITEM) == 0, "a panel with a stone material dropped when mined by hand");

		ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
		player.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);
		((CopycatBlock) state.getBlock()).playerDestroy(helper.getLevel(), player, pos, state, blockEntity, pickaxe);
		helper.runAfterDelay(2, () -> {
			check(helper, droppedItems(helper, CopycatRegistry.COPYCAT_PANEL_ITEM) == 1, "the panel did not drop for a pickaxe");
			done(helper);
		});
	}

	@GameTest
	public void breakingInCreativeDoesNotDropTheMaterial(GameTestHelper helper) {
		TestUtil.floor(helper);
		ServerPlayer player = player(helper, GameType.CREATIVE);
		helper.setBlock(P, panel(Direction.UP));
		use(helper, player, new ItemStack(Items.STONE, 1), P, Direction.UP);
		BlockPos pos = helper.absolutePos(P);
		BlockState state = helper.getBlockState(P);
		state.getBlock().playerWillDestroy(helper.getLevel(), pos, state, player);
		helper.getLevel().destroyBlock(pos, false);
		helper.runAfterDelay(2, () -> {
			check(helper, droppedItems(helper, Items.STONE) == 0, "the stone dropped in creative mode");
			done(helper);
		});
	}

	// ---- stacking ----

	@GameTest
	public void panelsLineUpNextToEachOther(GameTestHelper helper) {
		TestUtil.floor(helper);
		helper.setBlock(P, panel(Direction.NORTH));
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		ItemStack panels = new ItemStack(CopycatRegistry.COPYCAT_PANEL_ITEM, 4);

		// clicked near the east edge: the new panel goes east and faces the same way
		BlockHitResult east = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(P)).add(.45, 0, 0), Direction.NORTH, helper.absolutePos(P), false);
		check(helper, use(helper, player, panels, P, east).consumesAction(), "stacking was refused");
		BlockState placed = helper.getBlockState(P.east());
		check(helper, placed.is(CopycatRegistry.COPYCAT_PANEL) && placed.getValue(CopycatPanelBlock.FACING) == Direction.NORTH,
			"east of the panel is " + placed);
		check(helper, panels.getCount() == 3, "one panel should be used up");

		// clicked near the top: the new panel goes above
		BlockHitResult top = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(P)).add(0, .45, 0), Direction.NORTH, helper.absolutePos(P), false);
		use(helper, player, panels, P, top);
		check(helper, helper.getBlockState(P.above()).is(CopycatRegistry.COPYCAT_PANEL), "no panel above: " + helper.getBlockState(P.above()));

		// the panel's own axis is never chosen, and sneaking skips the helper
		player.setShiftKeyDown(true);
		BlockHitResult front = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(P)).add(0, 0, -.5), Direction.NORTH, helper.absolutePos(P), false);
		InteractionResult sneaking = use(helper, player, panels, P, front);
		player.setShiftKeyDown(false);
		check(helper, !sneaking.consumesAction(), "sneaking used the placement helper");
		done(helper);
	}

	@GameTest
	public void stepsExtendTheRowAndKeepTheirHalf(GameTestHelper helper) {
		TestUtil.floor(helper);
		helper.setBlock(P, step(Direction.SOUTH, Half.TOP));
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		ItemStack steps = new ItemStack(CopycatRegistry.COPYCAT_STEP_ITEM, 6);

		// a south facing step lines up along x
		BlockHitResult east = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(P)).add(.45, 0, 0), Direction.SOUTH, helper.absolutePos(P), false);
		check(helper, use(helper, player, steps, P, east).consumesAction(), "the step was not placed");
		BlockState placed = helper.getBlockState(P.east());
		check(helper, placed.is(CopycatRegistry.COPYCAT_STEP) && placed.getValue(CopycatStepBlock.FACING) == Direction.SOUTH
			&& placed.getValue(CopycatStepBlock.HALF) == Half.TOP, "east of the step is " + placed);

		// using it on the first one again continues past the end of the row
		use(helper, player, steps, P, east);
		check(helper, helper.getBlockState(P.east().east()).is(CopycatRegistry.COPYCAT_STEP), "the row was not extended: " + helper.getBlockState(P.east().east()));
		check(helper, steps.getCount() == 4, "two steps should be used up, " + steps.getCount() + " left");

		// the west side too
		BlockHitResult west = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(P)).add(-.45, 0, 0), Direction.SOUTH, helper.absolutePos(P), false);
		use(helper, player, steps, P, west);
		check(helper, helper.getBlockState(P.west()).is(CopycatRegistry.COPYCAT_STEP), "no step to the west");

		// a blocked row end is not placed over
		helper.setBlock(P.west().west(), Blocks.STONE);
		use(helper, player, steps, P, west);
		check(helper, helper.getBlockState(P.west().west()).is(Blocks.STONE), "the stone was replaced");
		done(helper);
	}

	@GameTest
	public void aStepRowStopsAtTheAssistRange(GameTestHelper helper) {
		TestUtil.floor(helper);
		int range = dev.createpolymer.decoration.DecorationPlacement.PLACEMENT_ASSIST_RANGE;
		dev.createpolymer.decoration.DecorationPlacement.PLACEMENT_ASSIST_RANGE = 2;
		try {
			for (int x = 2; x <= 4; x++)
				helper.setBlock(new BlockPos(x, 1, 2), step(Direction.SOUTH, Half.BOTTOM));
			ServerPlayer player = player(helper, GameType.SURVIVAL);
			ItemStack steps = new ItemStack(CopycatRegistry.COPYCAT_STEP_ITEM, 4);
			BlockPos start = new BlockPos(2, 1, 2);
			BlockHitResult east = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(start)).add(.45, 0, 0), Direction.SOUTH, helper.absolutePos(start), false);
			use(helper, player, steps, start, east);
			// two steps already stand east of the clicked one: the row is not extended there, but on the other end
			check(helper, helper.getBlockState(new BlockPos(5, 1, 2)).isAir(), "a step was placed beyond the assist range");
			check(helper, helper.getBlockState(new BlockPos(1, 1, 2)).is(CopycatRegistry.COPYCAT_STEP), "the step did not go to the other end of the row");
			done(helper);
		} finally {
			dev.createpolymer.decoration.DecorationPlacement.PLACEMENT_ASSIST_RANGE = range;
		}
	}

	@GameTest
	public void stepsPickTheirHalfFromTheClick(GameTestHelper helper) {
		TestUtil.floor(helper);
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		ItemStack steps = new ItemStack(CopycatRegistry.COPYCAT_STEP_ITEM, 4);
		helper.setBlock(P, Blocks.STONE);
		BlockPos pos = helper.absolutePos(P);

		BlockState onTop = CopycatRegistry.COPYCAT_STEP.getStateForPlacement(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, steps,
			new BlockHitResult(Vec3.atCenterOf(pos).add(0, .5, 0), Direction.UP, pos, false)));
		check(helper, onTop.getValue(CopycatStepBlock.HALF) == Half.BOTTOM, "a step on a floor is not in the lower half");
		BlockState below = CopycatRegistry.COPYCAT_STEP.getStateForPlacement(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, steps,
			new BlockHitResult(Vec3.atCenterOf(pos).add(0, -.5, 0), Direction.DOWN, pos, false)));
		check(helper, below.getValue(CopycatStepBlock.HALF) == Half.TOP, "a step under a ceiling is not in the upper half");
		BlockState sideLow = CopycatRegistry.COPYCAT_STEP.getStateForPlacement(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, steps,
			new BlockHitResult(new Vec3(pos.getX() + 1, pos.getY() + .2, pos.getZ() + .5), Direction.EAST, pos, false)));
		BlockState sideHigh = CopycatRegistry.COPYCAT_STEP.getStateForPlacement(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, steps,
			new BlockHitResult(new Vec3(pos.getX() + 1, pos.getY() + .8, pos.getZ() + .5), Direction.EAST, pos, false)));
		check(helper, sideLow.getValue(CopycatStepBlock.HALF) == Half.BOTTOM && sideHigh.getValue(CopycatStepBlock.HALF) == Half.TOP,
			"clicking the side picks " + sideLow.getValue(CopycatStepBlock.HALF) + " and " + sideHigh.getValue(CopycatStepBlock.HALF));
		check(helper, onTop.getValue(CopycatStepBlock.FACING) == player.getDirection(), "the step does not face the way the player looks");
		done(helper);
	}

	@GameTest
	public void panelsFaceAwayFromTheWayYouLook(GameTestHelper helper) {
		TestUtil.floor(helper);
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		player.setXRot(90);
		BlockPos pos = helper.absolutePos(P);
		BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, new ItemStack(CopycatRegistry.COPYCAT_PANEL_ITEM),
			new BlockHitResult(Vec3.atCenterOf(pos).add(0, -.5, 0), Direction.UP, pos.below(), false));
		BlockState state = CopycatRegistry.COPYCAT_PANEL.getStateForPlacement(context);
		check(helper, state.getValue(CopycatPanelBlock.FACING) == Direction.UP, "looking down gives " + state.getValue(CopycatPanelBlock.FACING));
		done(helper);
	}

	// ---- shapes and what players see ----

	@GameTest
	public void shapesFollowCreates(GameTestHelper helper) {
		var level = helper.getLevel();
		for (Direction facing : Direction.values()) {
			AABB box = panel(facing).getShape(level, BlockPos.ZERO).bounds();
			double thickness = facing.getAxis().choose(box.getXsize(), box.getYsize(), box.getZsize());
			check(helper, Math.abs(thickness - 3 / 16d) < 1e-6, "panel " + facing + " is " + thickness * 16 + " pixels thick");
			double edge = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE
				? facing.getAxis().choose(box.minX, box.minY, box.minZ) : facing.getAxis().choose(box.maxX, box.maxY, box.maxZ);
			check(helper, Math.abs(edge - (facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0 : 1)) < 1e-6,
				"panel " + facing + " is not against the side opposite its facing");
		}
		AABB south = step(Direction.SOUTH, Half.BOTTOM).getShape(level, BlockPos.ZERO).bounds();
		check(helper, south.equals(new AABB(0, 0, .5, 1, .5, 1)), "bottom step facing south is " + south);
		AABB north = step(Direction.NORTH, Half.TOP).getShape(level, BlockPos.ZERO).bounds();
		check(helper, north.equals(new AABB(0, .5, 0, 1, 1, .5)), "top step facing north is " + north);
		helper.succeed();
	}

	@GameTest
	public void playersBumpIntoAnInvisibleStandIn(GameTestHelper helper) {
		var level = helper.getLevel();
		for (Direction facing : Direction.values())
			for (boolean wet : new boolean[] { false, true }) {
				BlockState state = panel(facing).setValue(BlockStateProperties.WATERLOGGED, wet);
				BlockState client = CopycatRegistry.COPYCAT_PANEL.getPolymerBlockState(state, null);
				AABB serverBox = state.getCollisionShape(level, BlockPos.ZERO).bounds();
				AABB clientBox = client.getCollisionShape(level, BlockPos.ZERO).bounds();
				check(helper, covers(clientBox, serverBox),
					"the client collision " + clientBox + " does not cover the server's " + serverBox + " for " + facing);
				check(helper, clientBox.getXsize() * clientBox.getYsize() * clientBox.getZsize() < .5,
					"the client sees a full block for a panel facing " + facing);
			}
		for (Half half : Half.values()) {
			BlockState state = step(Direction.EAST, half);
			BlockState client = CopycatRegistry.COPYCAT_STEP.getPolymerBlockState(state, null);
			AABB serverBox = state.getCollisionShape(level, BlockPos.ZERO).bounds();
			AABB clientBox = client.getCollisionShape(level, BlockPos.ZERO).bounds();
			check(helper, covers(clientBox, serverBox),
				"the client collision " + clientBox + " does not cover the server's " + serverBox + " for a " + half + " step");
			check(helper, clientBox.getYsize() <= .5 + 1e-6, "the client sees more than a half block for a " + half + " step");
		}
		helper.succeed();
	}

	@GameTest
	public void panelsAndStepsCanBeWaterlogged(GameTestHelper helper) {
		TestUtil.floor(helper);
		helper.setBlock(P, Blocks.WATER);
		helper.setBlock(P.east(), Blocks.WATER);
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		for (var block : List.of(CopycatRegistry.COPYCAT_PANEL, CopycatRegistry.COPYCAT_STEP)) {
			BlockPos pos = helper.absolutePos(block == CopycatRegistry.COPYCAT_PANEL ? P : P.east());
			BlockState state = block.getStateForPlacement(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, new ItemStack(block),
				new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, .5, 0), Direction.UP, pos.below(), false)));
			check(helper, state.getValue(BlockStateProperties.WATERLOGGED), block + " placed in water is not waterlogged");
			check(helper, state.getFluidState().getType() == Fluids.WATER, "no water in the fluid state");
		}
		check(helper, !CopycatRegistry.COPYCAT_PANEL.defaultBlockState().getValue(BlockStateProperties.WATERLOGGED), "dry by default");
		done(helper);
	}

	// ---- what the material changes ----

	@GameTest
	public void theMaterialSetsTheLightOfTheBlock(GameTestHelper helper) {
		TestUtil.floor(helper);
		helper.setBlock(P, panel(Direction.UP));
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		check(helper, helper.getBlockState(P).getLightEmission() == 0, "a bare panel shines");

		use(helper, player, new ItemStack(Items.GLOWSTONE, 1), P, Direction.UP);
		BlockState lit = helper.getBlockState(P);
		check(helper, lit.is(CopycatRegistry.COPYCAT_PANEL) && lit.getValue(CopycatBlock.LIGHT) == 15 && lit.getLightEmission() == 15,
			"a glowstone panel has light " + lit.getLightEmission());
		check(helper, lit.getValue(CopycatPanelBlock.FACING) == Direction.UP && be(helper, P).getMaterial().is(Blocks.GLOWSTONE),
			"the panel lost its state or its block entity");

		wrench(helper, player, P);
		check(helper, helper.getBlockState(P).getLightEmission() == 0, "the panel still shines after the material was removed");

		// a lamp that is switched changes the light with it
		ServerPlayer other = player(helper, GameType.SURVIVAL);
		helper.setBlock(P.east(), step(Direction.SOUTH, Half.BOTTOM));
		use(helper, other, new ItemStack(Items.REDSTONE_LAMP, 2), P.east(), Direction.UP);
		check(helper, helper.getBlockState(P.east()).getLightEmission() == 0, "an unlit lamp shines");
		use(helper, other, other.getMainHandItem(), P.east(), Direction.UP);
		check(helper, helper.getBlockState(P.east()).getLightEmission() == 15 && be(helper, P.east()).getMaterial().getValue(BlockStateProperties.LIT),
			"turning the lamp material on did not light the step");
		done(helper);
	}

	@GameTest(maxTicks = 40)
	public void aBlockWhoseLightDoesNotMatchItsMaterialIsFixedWhenItLoads(GameTestHelper helper) {
		TestUtil.floor(helper);
		helper.setBlock(P, panel(Direction.UP));
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		use(helper, player, new ItemStack(Items.GLOWSTONE, 1), P, Direction.UP);
		BlockPos pos = helper.absolutePos(P);
		// the saved block state lost its light (or the material's light changed): the block entity sets it right when it loads
		helper.getLevel().setBlock(pos, helper.getBlockState(P).setValue(CopycatBlock.LIGHT, 0), 3);
		check(helper, helper.getBlockState(P).getLightEmission() == 0 && be(helper, P).getMaterial().is(Blocks.GLOWSTONE), "setup");
		be(helper, P).setLevel(helper.getLevel());
		helper.runAfterDelay(3, () -> {
			check(helper, helper.getBlockState(P).getLightEmission() == 15, "the light was not restored: " + helper.getBlockState(P));
			done(helper);
		});
	}

	@GameTest
	public void theMaterialSetsHowLongItTakesToBreak(GameTestHelper helper) {
		TestUtil.floor(helper);
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
		helper.setBlock(P, panel(Direction.UP));
		BlockPos pos = helper.absolutePos(P);
		float bare = helper.getBlockState(P).getDestroyProgress(player, helper.getLevel(), pos);
		use(helper, player, new ItemStack(Items.OBSIDIAN, 1), P, Direction.UP);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
		float obsidian = helper.getBlockState(P).getDestroyProgress(player, helper.getLevel(), pos);
		float expected = Blocks.OBSIDIAN.defaultBlockState().getDestroyProgress(player, helper.getLevel(), pos);
		check(helper, bare > 0 && Math.abs(obsidian - expected) < 1e-6, "obsidian panel: " + obsidian + ", obsidian: " + expected);
		check(helper, obsidian < bare, "an obsidian panel is as quick to break as a bare one");
		done(helper);
	}

	@GameTest
	public void theMaterialSoftensFalls(GameTestHelper helper) {
		TestUtil.floor(helper);
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		helper.setBlock(P, panel(Direction.UP));
		helper.setBlock(P.east(), panel(Direction.UP));
		use(helper, player, new ItemStack(Items.HAY_BLOCK, 1), P.east(), Direction.UP);
		BlockPos pos = helper.absolutePos(P);
		// villagers take fall damage at once (players are protected for a while after joining)
		var onBare = helper.spawn(EntityTypes.VILLAGER, P.above());
		var onHay = helper.spawn(EntityTypes.VILLAGER, P.east().above());
		float before = onBare.getHealth();
		helper.getBlockState(P).getBlock().fallOn(helper.getLevel(), helper.getBlockState(P), pos, onBare, 13);
		helper.getBlockState(P.east()).getBlock().fallOn(helper.getLevel(), helper.getBlockState(P.east()), pos.east(), onHay, 13);
		float bareDamage = before - onBare.getHealth();
		float hayDamage = before - onHay.getHealth();
		check(helper, bareDamage > 0 && hayDamage < bareDamage, "falling onto hay took " + hayDamage + ", onto a bare panel " + bareDamage);
		done(helper);
	}

	@GameTest
	public void pickBlockGivesTheMaterialsItem(GameTestHelper helper) {
		TestUtil.floor(helper);
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		helper.setBlock(P, panel(Direction.UP));
		BlockPos pos = helper.absolutePos(P);
		check(helper, helper.getBlockState(P).getCloneItemStack(helper.getLevel(), pos, false).is(CopycatRegistry.COPYCAT_PANEL_ITEM),
			"a bare panel does not clone as a panel");
		use(helper, player, new ItemStack(Items.STONE_BRICKS, 1), P, Direction.UP);
		check(helper, helper.getBlockState(P).getCloneItemStack(helper.getLevel(), pos, false).is(Items.STONE_BRICKS),
			"a stone brick panel does not clone as stone bricks");
		done(helper);
	}

	// ---- saving ----

	@GameTest
	public void theMaterialSurvivesSavingAndLoading(GameTestHelper helper) {
		TestUtil.floor(helper);
		helper.setBlock(P, panel(Direction.EAST));
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		use(helper, player, new ItemStack(Items.OAK_LOG, 2), P, Direction.EAST);
		CopycatBlockEntity copycat = be(helper, P);
		BlockState material = copycat.getMaterial();

		var registries = helper.getLevel().registryAccess();
		CompoundTag saved = copycat.saveWithFullMetadata(registries);
		BlockEntity loaded = BlockEntity.loadStatic(copycat.getBlockPos(), copycat.getBlockState(), saved, registries);
		check(helper, loaded instanceof CopycatBlockEntity, "the block entity did not load");
		CopycatBlockEntity again = (CopycatBlockEntity) loaded;
		again.setLevel(helper.getLevel());
		check(helper, again.getMaterial() == material, "the material changed from " + material + " to " + again.getMaterial());
		check(helper, again.getConsumedItem().is(Items.OAK_LOG), "the consumed item is " + again.getConsumedItem());

		// a material that does not match the item it came from is dropped (the saved data was edited)
		CompoundTag forged = saved.copy();
		forged.remove("Item");
		BlockEntity forgedLoaded = BlockEntity.loadStatic(copycat.getBlockPos(), copycat.getBlockState(), forged, registries);
		check(helper, forgedLoaded instanceof CopycatBlockEntity f && !f.hasCustomMaterial(), "a material without an item was accepted");
		done(helper);
	}

	@GameTest
	public void aSavedBareCopycatLoadsBare(GameTestHelper helper) {
		TestUtil.floor(helper);
		helper.setBlock(P, step(Direction.SOUTH, Half.BOTTOM));
		CopycatBlockEntity copycat = be(helper, P);
		var registries = helper.getLevel().registryAccess();
		BlockEntity loaded = BlockEntity.loadStatic(copycat.getBlockPos(), copycat.getBlockState(), copycat.saveWithFullMetadata(registries), registries);
		check(helper, loaded instanceof CopycatBlockEntity c && !c.hasCustomMaterial() && c.getConsumedItem().isEmpty(), "bare copycat did not load bare");
		helper.succeed();
	}

	@GameTest
	public void neighboursWithTheSameMaterialShareItsState(GameTestHelper helper) {
		TestUtil.floor(helper);
		helper.setBlock(P, panel(Direction.UP));
		helper.setBlock(P.east(), panel(Direction.UP));
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		use(helper, player, new ItemStack(Items.OAK_LOG, 4), P, Direction.UP);
		use(helper, player, player.getMainHandItem(), P, Direction.UP);   // turned to the z axis
		BlockState first = be(helper, P).getMaterial();
		use(helper, player, new ItemStack(Items.OAK_LOG, 4), P.east(), Direction.UP);
		check(helper, be(helper, P.east()).getMaterial() == first, "the second panel's log is " + be(helper, P.east()).getMaterial() + ", the first one's is " + first);
		done(helper);
	}

	// ---- the model ----

	@GameTest(maxTicks = 80)
	public void aBareCopycatShowsCreatesCopycatBase(GameTestHelper helper) {
		TestUtil.floor(helper);
		helper.setBlock(P, panel(Direction.UP));
		helper.setBlock(P.east(), step(Direction.SOUTH, Half.BOTTOM));
		helper.runAfterDelay(3, () -> {
			for (BlockPos rel : List.of(P, P.east())) {
				List<VirtualElement> pieces = pieces(helper, rel);
				check(helper, pieces.size() == 1 && pieces.get(0) instanceof ItemDisplayElement, "a bare copycat shows " + pieces);
			}
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void theModelShowsThePanelMaterialInThePanelsShape(GameTestHelper helper) {
		TestUtil.floor(helper);
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		helper.setBlock(P, panel(Direction.UP));
		helper.setBlock(P.east(), panel(Direction.NORTH));
		helper.setBlock(P.east().east(), panel(Direction.DOWN));
		use(helper, player, new ItemStack(Items.STONE, 1), P, Direction.UP);
		use(helper, player, new ItemStack(Items.STONE, 1), P.east(), Direction.UP);
		use(helper, player, new ItemStack(Items.STONE, 1), P.east().east(), Direction.UP);
		helper.runAfterDelay(3, () -> {
			BlockDisplayElement floor = materialDisplay(helper, P);
			check(helper, floor.getBlockState().is(Blocks.STONE), "the floor panel shows " + floor.getBlockState());
			assertExtent(helper, extent(floor), 0, 0, 0, 16, 3, 16);
			assertExtent(helper, extent(materialDisplay(helper, P.east())), 0, 0, 13, 16, 16, 16);
			assertExtent(helper, extent(materialDisplay(helper, P.east().east())), 0, 13, 0, 16, 16, 16);
			done(helper);
		});
	}

	@GameTest(maxTicks = 80)
	public void theModelShowsTheStepMaterialInTheStepsShape(GameTestHelper helper) {
		TestUtil.floor(helper);
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		helper.setBlock(P, step(Direction.SOUTH, Half.BOTTOM));
		helper.setBlock(P.east(), step(Direction.WEST, Half.TOP));
		use(helper, player, new ItemStack(Items.STONE_BRICKS, 2), P, Direction.UP);
		use(helper, player, player.getMainHandItem(), P.east(), Direction.UP);
		helper.runAfterDelay(3, () -> {
			BlockDisplayElement south = materialDisplay(helper, P);
			check(helper, south.getBlockState().is(Blocks.STONE_BRICKS), "the step shows " + south.getBlockState());
			assertExtent(helper, extent(south), 0, 0, 8, 16, 8, 16);
			assertExtent(helper, extent(materialDisplay(helper, P.east())), 0, 8, 0, 8, 16, 16);
			done(helper);
		});
	}

	@GameTest(maxTicks = 80)
	public void theModelFollowsTheMaterialWhenItChanges(GameTestHelper helper) {
		TestUtil.floor(helper);
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		helper.setBlock(P, panel(Direction.UP));
		helper.runAfterDelay(3, () -> {
			check(helper, pieces(helper, P).get(0) instanceof ItemDisplayElement, "bare panel shows no model item");
			use(helper, player, new ItemStack(Items.STONE, 1), P, Direction.UP);
			helper.runAfterDelay(3, () -> {
				check(helper, materialDisplay(helper, P).getBlockState().is(Blocks.STONE), "the model does not show the stone");
				check(helper, pieces(helper, P).stream().noneMatch(ItemDisplayElement.class::isInstance), "the bare model is still shown");
				wrench(helper, player, P);
				helper.runAfterDelay(3, () -> {
					List<VirtualElement> pieces = pieces(helper, P);
					check(helper, pieces.size() == 1 && pieces.get(0) instanceof ItemDisplayElement, "the bare model did not come back: " + pieces);
					done(helper);
				});
			});
		});
	}

	@GameTest(maxTicks = 80)
	public void aModelBuiltAfterTheMaterialWasSetShowsIt(GameTestHelper helper) {
		TestUtil.floor(helper);
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		helper.setBlock(P, panel(Direction.UP));
		use(helper, player, new ItemStack(Items.STONE, 1), P, Direction.UP);
		BlockPos pos = helper.absolutePos(P);
		var model = (CopycatModel) CopycatRegistry.COPYCAT_PANEL.createModel(helper.getLevel(), pos, helper.getBlockState(P));
		check(helper, model.getShownMaterial().is(Blocks.STONE), "a new model starts with " + model.getShownMaterial());
		check(helper, model.getPieces().size() == 1 && model.getPieces().get(0) instanceof BlockDisplayElement, "new model pieces: " + model.getPieces());
		done(helper);
	}

	@GameTest(maxTicks = 80)
	public void panelsShowBarsAsAFlatPaneAndTrapdoorsAsThemselves(GameTestHelper helper) {
		TestUtil.floor(helper);
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		helper.setBlock(P, panel(Direction.NORTH));
		helper.setBlock(P.east(), panel(Direction.UP));
		helper.setBlock(P.east().east(), panel(Direction.UP));
		use(helper, player, new ItemStack(Items.IRON_BARS, 1), P, Direction.NORTH);
		use(helper, player, new ItemStack(Items.IRON_BARS, 1), P.east(), Direction.UP);
		use(helper, player, new ItemStack(Items.OAK_TRAPDOOR, 1), P.east().east(), Direction.UP);
		helper.runAfterDelay(3, () -> {
			// facing north the pane stands in the south part of the block, running east to west: the display's cube
			// is moved 7 pixels south, so the 2 pixel thick pane in the middle of the cube is pixels 14 to 16
			BlockDisplayElement wall = materialDisplay(helper, P);
			BlockState wallState = wall.getBlockState();
			check(helper, wallState.is(Blocks.IRON_BARS) && wallState.getValue(IronBarsBlock.EAST) && wallState.getValue(IronBarsBlock.WEST)
				&& !wallState.getValue(IronBarsBlock.NORTH) && !wallState.getValue(IronBarsBlock.SOUTH), "wall bars: " + wallState);
			assertExtent(helper, extent(wall), 0, 0, 7, 16, 16, 23);

			// facing up the pane lies flat on the floor: the cube is moved 7 pixels down, the pane is pixels 0 to 2
			BlockDisplayElement floor = materialDisplay(helper, P.east());
			assertExtent(helper, extent(floor), 0, -7, 0, 16, 9, 16);

			BlockDisplayElement trapdoor = materialDisplay(helper, P.east().east());
			check(helper, trapdoor.getBlockState().is(Blocks.OAK_TRAPDOOR), "the trapdoor panel shows " + trapdoor.getBlockState());
			assertExtent(helper, extent(trapdoor), 0, 0, 0, 16, 16, 16);
			done(helper);
		});
	}

	@GameTest
	public void thePanelAndStepModelsBuildInEveryOrientation(GameTestHelper helper) {
		List<BlockState> materials = List.of(CopycatRegistry.COPYCAT_BASE.defaultBlockState(), Blocks.STONE.defaultBlockState(),
			Blocks.IRON_BARS.defaultBlockState(), Blocks.OAK_TRAPDOOR.defaultBlockState());
		for (Direction facing : Direction.values())
			for (BlockState material : materials) {
				var model = new CopycatPanelModel(panel(facing), material);
				check(helper, model.getPieces().size() == 1 && model.getShownMaterial() == material, "panel model: " + model.getPieces());
			}
		for (Direction facing : Direction.Plane.HORIZONTAL)
			for (Half half : Half.values())
				for (BlockState material : materials.subList(0, 2)) {
					var model = new CopycatStepModel(step(facing, half), material);
					check(helper, model.getPieces().size() == 1, "step model: " + model.getPieces());
				}
		helper.succeed();
	}
}
