package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import java.util.ArrayList;
import java.util.List;

import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.base.RotatedPillarKineticBlock;
import dev.createpolymer.kinetics.belt.BeltBlock;
import dev.createpolymer.kinetics.belt.BeltBlockEntity;
import dev.createpolymer.kinetics.belt.BeltBlockEntity.CasingType;
import dev.createpolymer.kinetics.belt.BeltHelper;
import dev.createpolymer.kinetics.belt.BeltPart;
import dev.createpolymer.kinetics.belt.BeltScroll;
import dev.createpolymer.kinetics.belt.BeltSlope;
import dev.createpolymer.kinetics.belt.item.BeltConnectorItem;
import dev.createpolymer.kinetics.belt.transport.TransportedItemStack;
import dev.createpolymer.kinetics.motor.CreativeMotorBlockEntity;
import dev.createpolymer.kinetics.press.MechanicalPressBlock;
import dev.createpolymer.logistics.depot.DepotBlockEntity;
import dev.createpolymer.logistics.tunnel.BeltTunnelBlock;
import dev.createpolymer.logistics.tunnel.BeltTunnelBlockEntity;
import dev.createpolymer.logistics.tunnel.BrassTunnelBlockEntity;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.BeltRegistry;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class BeltGameTests {

	// The belt runs along +X at z = 1 and y = 1, from the shaft at x = 1 to the one at x = 6. A creative motor
	// at (1, 1, 0) faces south into the first pulley.
	private static final BlockPos MOTOR = new BlockPos(1, 1, 0);
	private static final BlockPos START = new BlockPos(1, 1, 1);
	private static final BlockPos END = new BlockPos(6, 1, 1);

	private static void shaftZ(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, ModBlocks.SHAFT.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.Z));
	}

	private static void motor(GameTestHelper helper, BlockPos pos, Direction facing, int speed) {
		helper.setBlock(pos, ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, facing));
		helper.runAfterDelay(1, () -> helper.getBlockEntity(pos, CreativeMotorBlockEntity.class).setSetting(speed));
	}

	/**
	 * Shafts, a motor driving them and the belt in between, built by the belt connector's own code. The motor turns
	 * the belt at -speed rpm: along X Create moves items against the direction of a positive speed, so items go east.
	 */
	private static void buildBelt(GameTestHelper helper, int speed) {
		floor(helper);
		shaftZ(helper, START);
		shaftZ(helper, END);
		motor(helper, MOTOR, Direction.SOUTH, -speed);
		BeltConnectorItem.createBelts(helper.getLevel(), helper.absolutePos(START), helper.absolutePos(END));
	}

	private static BeltBlockEntity belt(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockEntity(pos, BeltBlockEntity.class);
	}

	private static IItemHandler handler(GameTestHelper helper, BlockPos pos) {
		return ItemHandlerLookup.get(helper.getLevel(), helper.absolutePos(pos), Direction.UP);
	}

	private static BlockHitResult hit(GameTestHelper helper, BlockPos pos, Direction face) {
		BlockPos abs = helper.absolutePos(pos);
		return new BlockHitResult(Vec3.atCenterOf(abs).add(0, .4, 0), face, abs, false);
	}

	private static int itemsOnBelt(GameTestHelper helper, BlockPos controller, net.minecraft.world.item.Item item) {
		int found = 0;
		var inventory = belt(helper, controller).getInventory();
		if (inventory == null)
			return 0;
		for (TransportedItemStack t : inventory.getTransportedItems())
			if (t.stack.is(item))
				found += t.stack.getCount();
		return found;
	}

	// ---- building ----

	@GameTest(maxTicks = 80)
	public void connectorBuildsBeltBetweenShafts(GameTestHelper helper) {
		buildBelt(helper, 32);
		helper.succeedWhen(() -> {
			for (int x = 1; x <= 6; x++) {
				BlockPos pos = new BlockPos(x, 1, 1);
				var state = helper.getBlockState(pos);
				helper.assertTrue(state.getBlock() instanceof BeltBlock, "no belt at " + pos);
				BeltPart expected = x == 1 ? BeltPart.START : x == 6 ? BeltPart.END : BeltPart.MIDDLE;
				helper.assertTrue(state.getValue(BeltBlock.PART) == expected, "part at " + pos + " is " + state.getValue(BeltBlock.PART));
				helper.assertTrue(state.getValue(BeltBlock.SLOPE) == BeltSlope.HORIZONTAL, "slope at " + pos);
				helper.assertTrue(state.getValue(BeltBlock.HORIZONTAL_FACING) == Direction.EAST, "facing at " + pos);
				BeltBlockEntity be = belt(helper, pos);
				helper.assertTrue(be.beltLength == 6 && be.index == x - 1, "belt entity at " + pos + ": length " + be.beltLength + ", index " + be.index);
				helper.assertTrue(be.getController().equals(helper.absolutePos(START)), "controller of " + pos + " is " + be.getController());
				// pulleys pass the rotation on, all the middle segments follow through the belt
				TestUtil.assertSpeed(helper, pos, -32);
			}
			helper.assertTrue(belt(helper, START).isController(), "start is not the controller");
		});
	}

	@GameTest(maxTicks = 100)
	public void connectorItemMarksFirstShaftAndBuilds(GameTestHelper helper) {
		floor(helper);
		shaftZ(helper, START);
		shaftZ(helper, END);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BeltRegistry.BELT_CONNECTOR, 2));
		helper.runAfterDelay(2, () -> helper.useBlock(START, player, hit(helper, START, Direction.UP)));
		helper.runAfterDelay(4, () -> {
			ItemStack held = player.getMainHandItem();
			helper.assertTrue(helper.absolutePos(START).equals(held.get(BeltRegistry.BELT_FIRST_SHAFT)),
				"the first shaft was not remembered: " + held.get(BeltRegistry.BELT_FIRST_SHAFT));
		});
		// the item has a short cooldown after each click
		helper.runAfterDelay(14, () -> helper.useBlock(END, player, hit(helper, END, Direction.UP)));
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(helper.getBlockState(START).getBlock() instanceof BeltBlock, "no belt at the start");
			helper.assertTrue(helper.getBlockState(END).getBlock() instanceof BeltBlock, "no belt at the end");
			helper.assertTrue(player.getMainHandItem().getCount() == 1, "the belt connector was not used up: " + player.getMainHandItem());
			helper.assertTrue(!player.getMainHandItem().has(BeltRegistry.BELT_FIRST_SHAFT), "the first shaft stayed remembered");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void connectorRefusesShaftsOnDifferentAxes(GameTestHelper helper) {
		floor(helper);
		shaftZ(helper, START);
		helper.setBlock(END, ModBlocks.SHAFT.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.X));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(!BeltConnectorItem.canConnect(helper.getLevel(), helper.absolutePos(START), helper.absolutePos(END)),
				"shafts on different axes can be connected");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void diagonalBeltCarriesRotationAndItems(GameTestHelper helper) {
		floor(helper);
		BlockPos top = new BlockPos(4, 4, 1);
		shaftZ(helper, START);
		shaftZ(helper, top);
		motor(helper, MOTOR, Direction.SOUTH, -64);
		BeltConnectorItem.createBelts(helper.getLevel(), helper.absolutePos(START), helper.absolutePos(top));
		helper.runAfterDelay(8, () -> {
			for (int i = 0; i < 4; i++) {
				// one block up per block along
				BlockPos pos = new BlockPos(1 + i, 1 + i, 1);
				helper.assertTrue(helper.getBlockState(pos).getBlock() instanceof BeltBlock, "no belt at " + pos);
				helper.assertTrue(helper.getBlockState(pos).getValue(BeltBlock.SLOPE) == BeltSlope.UPWARD, "belt at " + pos + " is not sloped");
				TestUtil.assertSpeed(helper, pos, -64);
			}
			handler(helper, new BlockPos(2, 2, 1)).insertItem(0, new ItemStack(Items.GOLD_INGOT), false);
			helper.runAfterDelay(12, () -> {
				BeltBlockEntity controller = belt(helper, START);
				helper.assertTrue(itemsOnBelt(helper, START, Items.GOLD_INGOT) == 1, "the ingot is not on the belt");
				TransportedItemStack stack = controller.getInventory().getTransportedItems().get(0);
				helper.assertTrue(stack.beltPosition > 2.5f, "the ingot did not climb: " + stack.beltPosition);
				Vec3 at = BeltHelper.getVectorForOffset(controller, stack.beltPosition);
				helper.assertTrue(at.y > helper.absolutePos(START).getY() + 1.5, "the ingot is not above the start: " + at);
				helper.succeed();
			});
		});
	}

	// ---- items ----

	@GameTest(maxTicks = 300)
	public void itemTravelsAlongBeltAndDropsOffTheEnd(GameTestHelper helper) {
		buildBelt(helper, 64);
		helper.runAfterDelay(6, () -> {
			IItemHandler handler = handler(helper, new BlockPos(2, 1, 1));
			helper.assertTrue(handler != null, "no item handler on the belt");
			ItemStack remainder = handler.insertItem(0, new ItemStack(Items.EMERALD, 3), false);
			helper.assertTrue(remainder.isEmpty(), "the belt refused the emeralds: " + remainder);
		});
		helper.runAfterDelay(20, () -> {
			// 64 rpm: 2/15 of a block per tick, so the item has moved on but is still on the belt
			TransportedItemStack stack = belt(helper, START).getInventory().getTransportedItems().get(0);
			helper.assertTrue(stack.beltPosition > 2f && stack.beltPosition < 5.9f, "the emeralds are at " + stack.beltPosition);
		});
		helper.succeedWhen(() -> {
			helper.assertItemEntityPresent(Items.EMERALD, new BlockPos(7, 1, 1), 2.5);
			helper.assertTrue(itemsOnBelt(helper, START, Items.EMERALD) == 0, "an item stayed on the belt");
		});
	}

	@GameTest(maxTicks = 300)
	public void itemMovesOnToDepotAtTheEnd(GameTestHelper helper) {
		buildBelt(helper, 64);
		BlockPos depot = new BlockPos(7, 1, 1);
		helper.setBlock(depot, ModBlocks.DEPOT);
		helper.runAfterDelay(6, () -> handler(helper, new BlockPos(2, 1, 1)).insertItem(0, new ItemStack(Items.DIAMOND, 2), false));
		helper.succeedWhen(() -> {
			ItemStack held = helper.getBlockEntity(depot, DepotBlockEntity.class).getHeldItem();
			helper.assertTrue(held.is(Items.DIAMOND) && held.getCount() == 2, "the depot holds " + held);
		});
	}

	@GameTest(maxTicks = 60)
	public void beltHandlerInsertsAndExtracts(GameTestHelper helper) {
		buildBelt(helper, 16);
		BlockPos at = new BlockPos(3, 1, 1);
		helper.runAfterDelay(6, () -> {
			IItemHandler handler = handler(helper, at);
			ItemStack simulated = handler.insertItem(0, new ItemStack(Items.COPPER_INGOT, 5), true);
			helper.assertTrue(simulated.isEmpty(), "simulation refused the ingots");
			helper.assertTrue(itemsOnBelt(helper, START, Items.COPPER_INGOT) == 0, "simulated insertion changed the belt");

			ItemStack remainder = handler.insertItem(0, new ItemStack(Items.COPPER_INGOT, 5), false);
			helper.assertTrue(remainder.isEmpty(), "ingots were refused: " + remainder);
			// a second stack on the same spot has to wait, even before the first one has been taken in
			helper.assertTrue(handler.insertItem(0, new ItemStack(Items.COPPER_INGOT), true).getCount() == 1, "a second stack fits on the same spot");
		});
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(itemsOnBelt(helper, START, Items.COPPER_INGOT) == 5, "the belt does not hold the ingots");
		});
		helper.runAfterDelay(10, () -> {
			IItemHandler handler = handler(helper, new BlockPos(3, 1, 1));
			ItemStack in = handler.getStackInSlot(0);
			helper.assertTrue(in.isEmpty() || in.is(Items.COPPER_INGOT), "slot shows " + in);
			ItemStack taken = ItemStack.EMPTY;
			// the ingots move on slowly: they are on the segment they entered or the next one
			for (int x = 3; x <= 4 && taken.isEmpty(); x++)
				taken = handler(helper, new BlockPos(x, 1, 1)).extractItem(0, 2, false);
			helper.assertTrue(taken.is(Items.COPPER_INGOT) && taken.getCount() == 2, "extracted " + taken);
			helper.assertTrue(itemsOnBelt(helper, START, Items.COPPER_INGOT) == 3, "three ingots should be left");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void emptyHandTakesItemsOffTheBelt(GameTestHelper helper) {
		buildBelt(helper, 16);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		helper.runAfterDelay(6, () -> handler(helper, new BlockPos(3, 1, 1)).insertItem(0, new ItemStack(Items.GOLD_NUGGET, 4), false));
		helper.runAfterDelay(8, () -> {
			helper.useBlock(new BlockPos(3, 1, 1), player, hit(helper, new BlockPos(3, 1, 1), Direction.UP));
			helper.assertTrue(player.getInventory().contains(new ItemStack(Items.GOLD_NUGGET)), "the player got nothing");
			helper.assertTrue(itemsOnBelt(helper, START, Items.GOLD_NUGGET) == 0, "the nuggets stayed on the belt");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void itemEntityLandsOnBelt(GameTestHelper helper) {
		buildBelt(helper, 16);
		helper.runAfterDelay(6, () -> helper.spawnItem(Items.COAL, 3.5f, 2.5f, 1.5f));
		helper.succeedWhen(() -> helper.assertTrue(itemsOnBelt(helper, START, Items.COAL) == 1, "the coal is not on the belt"));
	}

	// ---- processing ----

	@GameTest(maxTicks = 500)
	public void pressPressesItemOnBelt(GameTestHelper helper) {
		buildBelt(helper, 32);
		// the press works two blocks above the belt: its own motor sits next to it
		helper.setBlock(new BlockPos(2, 3, 1), ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(3, 3, 1), ModBlocks.MECHANICAL_PRESS.defaultBlockState().setValue(MechanicalPressBlock.HORIZONTAL_FACING, Direction.EAST));
		helper.runAfterDelay(2, () -> helper.getBlockEntity(new BlockPos(2, 3, 1), CreativeMotorBlockEntity.class).setSetting(128));
		helper.runAfterDelay(8, () -> handler(helper, new BlockPos(2, 1, 1)).insertItem(0, new ItemStack(Items.IRON_INGOT), false));
		helper.succeedWhen(() -> {
			boolean sheet = itemsOnBelt(helper, START, ModItems.IRON_SHEET) > 0;
			if (!sheet)
				try {
					helper.assertItemEntityPresent(ModItems.IRON_SHEET, new BlockPos(7, 1, 1), 4);
					sheet = true;
				} catch (RuntimeException e) {
					// not dropped yet
				}
			helper.assertTrue(sheet, "no iron sheet came out of the press");
			helper.assertTrue(itemsOnBelt(helper, START, Items.IRON_INGOT) == 0, "the ingot was not pressed");
		});
	}

	// ---- entities ----

	@GameTest(maxTicks = 200)
	public void entityOnBeltIsCarried(GameTestHelper helper) {
		buildBelt(helper, 128);
		ArmorStand[] stand = new ArmorStand[1];
		helper.runAfterDelay(8, () -> stand[0] = helper.spawn(EntityTypes.ARMOR_STAND, new Vec3(2.5, 1.9, 1.5)));
		helper.succeedWhen(() -> {
			helper.assertTrue(stand[0] != null, "not spawned yet");
			Vec3 relative = helper.relativeVec(stand[0].position());
			helper.assertTrue(relative.x > 4.2, "the armor stand was not carried along: " + relative);
		});
	}

	@GameTest(maxTicks = 200)
	public void mobOnBeltIsCarried(GameTestHelper helper) {
		buildBelt(helper, 128);
		Mob[] pig = new Mob[1];
		helper.runAfterDelay(8, () -> {
			pig[0] = helper.spawn(EntityTypes.PIG, new Vec3(2.5, 1.9, 1.5));
			pig[0].setNoAi(true);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(pig[0] != null, "not spawned yet");
			Vec3 relative = helper.relativeVec(pig[0].position());
			helper.assertTrue(relative.x > 4.2, "the pig was not carried along: " + relative);
		});
	}

	// ---- casing and tools ----

	@GameTest(maxTicks = 100)
	public void casingAndWrench(GameTestHelper helper) {
		buildBelt(helper, 16);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		BlockPos at = new BlockPos(3, 1, 1);
		helper.runAfterDelay(6, () -> {
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ANDESITE_CASING, 2));
			helper.useBlock(at, player, hit(helper, at, Direction.UP));
			helper.assertTrue(helper.getBlockState(at).getValue(BeltBlock.CASING), "the belt has no casing");
			helper.assertTrue(belt(helper, at).casing == CasingType.ANDESITE, "casing type is " + belt(helper, at).casing);
			helper.assertTrue(belt(helper, at).getSpeed() != 0, "the casing stopped the belt");
		});
		helper.runAfterDelay(10, () -> {
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WRENCH));
			helper.useBlock(at, player, hit(helper, at, Direction.NORTH));
			helper.assertTrue(!helper.getBlockState(at).getValue(BeltBlock.CASING), "the wrench did not remove the casing");
			helper.assertTrue(belt(helper, at).casing == CasingType.NONE, "casing type is " + belt(helper, at).casing);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void casedBeltShowsTheCasingModel(GameTestHelper helper) {
		buildBelt(helper, 16);
		BlockPos at = new BlockPos(3, 1, 1);
		helper.runAfterDelay(6, () -> belt(helper, at).setCasingType(CasingType.ANDESITE));
		helper.runAfterDelay(12, () -> {
			boolean casing = false;
			for (ItemDisplayElement display : displays(helper, at))
				casing |= modelOf(display).contains("belt_casing_andesite/horizontal_middle");
			helper.assertTrue(casing, "the cased belt shows no andesite casing");
			belt(helper, at).setCasingType(CasingType.BRASS);
		});
		helper.runAfterDelay(18, () -> {
			boolean casing = false;
			for (ItemDisplayElement display : displays(helper, at))
				casing |= modelOf(display).contains("block/belt_casing/horizontal_middle") || modelOf(display).endsWith("belt_casing/horizontal_middle");
			helper.assertTrue(casing, "the brass cased belt shows no brass casing");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void shaftMakesAPulleyAndWrenchTakesItOut(GameTestHelper helper) {
		buildBelt(helper, 16);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		BlockPos at = new BlockPos(3, 1, 1);
		helper.runAfterDelay(6, () -> {
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SHAFT, 2));
			helper.useBlock(at, player, hit(helper, at, Direction.UP));
			helper.assertTrue(helper.getBlockState(at).getValue(BeltBlock.PART) == BeltPart.PULLEY, "no pulley");
			helper.assertTrue(player.getMainHandItem().getCount() == 1, "the shaft was not used");
			helper.assertTrue(belt(helper, at).hasPulley(), "the entity has no pulley");
		});
		helper.runAfterDelay(14, () -> {
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WRENCH));
			helper.useBlock(at, player, hit(helper, at, Direction.UP));
			helper.assertTrue(helper.getBlockState(at).getValue(BeltBlock.PART) == BeltPart.MIDDLE, "the pulley stayed: " + helper.getBlockState(at));
			helper.assertTrue(player.getInventory().contains(new ItemStack(ModItems.SHAFT)), "the shaft was not given back");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void wrenchSplitsTheBelt(GameTestHelper helper) {
		buildBelt(helper, 16);
		Player player = helper.makeMockPlayer(GameType.CREATIVE);
		BlockPos at = new BlockPos(3, 1, 1);
		helper.runAfterDelay(6, () -> {
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WRENCH));
			BlockPos abs = helper.absolutePos(at);
			// the hit is on the east half of the segment: the belt is cut there
			helper.useBlock(at, player, new BlockHitResult(Vec3.atCenterOf(abs).add(.3, .4, 0), Direction.UP, abs, false));
		});
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(helper.getBlockState(new BlockPos(3, 1, 1)).getValue(BeltBlock.PART) == BeltPart.END, "the segment hit is not an end");
			helper.assertTrue(helper.getBlockState(new BlockPos(4, 1, 1)).getValue(BeltBlock.PART) == BeltPart.START, "the next segment is not a start");
			helper.assertTrue(belt(helper, new BlockPos(1, 1, 1)).beltLength == 3, "first belt has length " + belt(helper, new BlockPos(1, 1, 1)).beltLength);
			helper.assertTrue(belt(helper, new BlockPos(4, 1, 1)).beltLength == 3, "second belt has length " + belt(helper, new BlockPos(4, 1, 1)).beltLength);
			helper.assertTrue(belt(helper, new BlockPos(5, 1, 1)).getController().equals(helper.absolutePos(new BlockPos(4, 1, 1))), "second controller is wrong");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void breakingASegmentTakesTheChainWithIt(GameTestHelper helper) {
		buildBelt(helper, 16);
		helper.runAfterDelay(8, () -> helper.getLevel().destroyBlock(helper.absolutePos(new BlockPos(3, 1, 1)), false));
		helper.runAfterDelay(12, () -> {
			for (int x = 1; x <= 6; x++) {
				BlockPos pos = new BlockPos(x, 1, 1);
				helper.assertTrue(!(helper.getBlockState(pos).getBlock() instanceof BeltBlock), "belt remains at " + pos);
			}
			// the pulleys of the ends stay as shafts
			helper.assertTrue(helper.getBlockState(START).is(ModBlocks.SHAFT), "no shaft where the start pulley was");
			helper.assertTrue(helper.getBlockState(END).is(ModBlocks.SHAFT), "no shaft where the end pulley was");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void brokenBeltsDropTheConnectorAndPulleyShafts(GameTestHelper helper) {
		buildBelt(helper, 16);
		helper.runAfterDelay(6, () -> {
			var level = helper.getLevel();
			BlockPos start = helper.absolutePos(START);
			BlockPos middle = helper.absolutePos(new BlockPos(3, 1, 1));
			var startDrops = Block.getDrops(level.getBlockState(start), level, start, level.getBlockEntity(start));
			var middleDrops = Block.getDrops(level.getBlockState(middle), level, middle, level.getBlockEntity(middle));
			helper.assertTrue(startDrops.stream().anyMatch(d -> d.is(BeltRegistry.BELT_CONNECTOR)), "the belt drops no connector: " + startDrops);
			helper.assertTrue(startDrops.stream().anyMatch(d -> d.is(ModItems.SHAFT)), "a pulley drops no shaft: " + startDrops);
			helper.assertTrue(middleDrops.size() == 1 && middleDrops.get(0).is(BeltRegistry.BELT_CONNECTOR), "a middle segment drops " + middleDrops);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void hopperFeedsTheBelt(GameTestHelper helper) {
		buildBelt(helper, 16);
		BlockPos hopper = new BlockPos(3, 2, 1);
		helper.setBlock(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
		helper.runAfterDelay(6, () -> helper.getBlockEntity(hopper, HopperBlockEntity.class).setItem(0, new ItemStack(Items.PRISMARINE_SHARD, 2)));
		helper.succeedWhen(() -> helper.assertTrue(itemsOnBelt(helper, START, Items.PRISMARINE_SHARD) > 0, "the hopper put nothing on the belt"));
	}

	@GameTest(maxTicks = 100)
	public void playerOnTheBeltIsPushed(GameTestHelper helper) {
		buildBelt(helper, 64);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		helper.runAfterDelay(8, () -> player.setPos(helper.absoluteVec(new Vec3(2.5, 1.85, 1.5))));
		// a mock player does not tick: tell the belt that it stands there, as a moving player would
		for (int tick = 9; tick < 30; tick++)
			helper.runAfterDelay(tick, () -> {
				BlockPos pos = BlockPos.containing(player.position());
				var state = helper.getLevel().getBlockState(pos);
				if (state.getBlock() instanceof BeltBlock)
					state.entityInside(helper.getLevel(), pos, player, net.minecraft.world.entity.InsideBlockEffectApplier.NOOP, true);
			});
		helper.runAfterDelay(32, () -> {
			Vec3 relative = helper.relativeVec(player.position());
			helper.assertTrue(relative.x > 4.5 && relative.x < 6, "the player was not carried along: " + relative);
			helper.succeed();
		});
	}

	// ---- saving ----

	@GameTest(maxTicks = 60)
	public void beltContentsSurviveSaving(GameTestHelper helper) {
		buildBelt(helper, 16);
		helper.runAfterDelay(6, () -> handler(helper, new BlockPos(3, 1, 1)).insertItem(0, new ItemStack(Items.GOLD_INGOT, 7), false));
		helper.runAfterDelay(10, () -> {
			BeltBlockEntity controller = belt(helper, START);
			var registries = helper.getLevel().registryAccess();
			TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
			controller.saveWithFullMetadata(out);
			BlockEntity loaded = BlockEntity.loadStatic(controller.getBlockPos(), controller.getBlockState(), out.buildResult(), registries);
			helper.assertTrue(loaded instanceof BeltBlockEntity, "the belt did not load");
			BeltBlockEntity copy = (BeltBlockEntity) loaded;
			helper.assertTrue(copy.isController() && copy.beltLength == 6, "length " + copy.beltLength + ", controller " + copy.isController());
			int ingots = 0;
			for (TransportedItemStack t : copy.getInventory().getTransportedItems())
				if (t.stack.is(Items.GOLD_INGOT))
					ingots += t.stack.getCount();
			helper.assertTrue(ingots == 7, "the copy holds " + ingots + " ingots");
			helper.succeed();
		});
	}

	// ---- visuals ----

	private static List<ItemDisplayElement> displays(GameTestHelper helper, BlockPos pos) {
		BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(pos));
		helper.assertTrue(attachment != null, "no model at " + pos);
		List<ItemDisplayElement> list = new ArrayList<>();
		for (VirtualElement element : attachment.holder().getElements())
			if (element instanceof ItemDisplayElement display && !display.getItem().isEmpty())
				list.add(display);
		return list;
	}

	private static String modelOf(ItemDisplayElement display) {
		var model = display.getItem().get(DataComponents.ITEM_MODEL);
		return model == null ? "" : model.toString();
	}

	@GameTest(maxTicks = 100)
	public void modelScrollsWithTheSpeedAndShowsItems(GameTestHelper helper) {
		buildBelt(helper, 32);
		BlockPos middle = new BlockPos(3, 1, 1);
		helper.runAfterDelay(10, () -> {
			boolean scrolling = false;
			for (ItemDisplayElement display : displays(helper, middle))
				scrolling |= modelOf(display).contains("belt_scroll/middle_");
			helper.assertTrue(scrolling, "the moving belt does not use a scrolling model");
			// the controller shows its items
			int before = displays(helper, START).size();
			handler(helper, new BlockPos(2, 1, 1)).insertItem(0, new ItemStack(Items.IRON_INGOT), false);
			helper.runAfterDelay(5, () -> {
				int shown = displays(helper, START).size();
				helper.assertTrue(shown == before + 1, "the controller shows " + shown + " displays, " + before + " before the item");
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = 40)
	public void stoppedBeltUsesTheStaticModel(GameTestHelper helper) {
		floor(helper);
		shaftZ(helper, START);
		shaftZ(helper, END);
		BeltConnectorItem.createBelts(helper.getLevel(), helper.absolutePos(START), helper.absolutePos(END));
		helper.runAfterDelay(8, () -> {
			boolean staticModel = false;
			for (ItemDisplayElement display : displays(helper, new BlockPos(3, 1, 1)))
				staticModel |= modelOf(display).contains("belt/middle");
			helper.assertTrue(staticModel, "an idle belt does not use Create's belt model");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 20)
	public void scrollModelsAndTexturesAreBundled(GameTestHelper helper) {
		String[] partials = { "start", "middle", "end", "start_bottom", "middle_bottom", "end_bottom",
			"diagonal_start", "diagonal_middle", "diagonal_end" };
		for (int i = 0; i < BeltScroll.RATES.length; i++)
			for (boolean positive : new boolean[] { true, false }) {
				for (String partial : partials) {
					String path = "/assets/create/models/" + "block/" + BeltScroll.modelPath(partial, positive, i) + ".json";
					helper.assertTrue(BeltGameTests.class.getResource(path) != null, "missing " + path);
				}
				for (String kind : new String[] { "belt", "belt_offset", "belt_diagonal" }) {
					int k = BeltScroll.RATES[i][0], f = BeltScroll.RATES[i][1];
					String tex = "/assets/create/textures/block/belt_scroll/" + kind + (positive ? "_p" : "_n") + "_r" + k + "_" + f + ".png";
					helper.assertTrue(BeltGameTests.class.getResource(tex) != null, "missing " + tex);
					helper.assertTrue(BeltGameTests.class.getResource(tex + ".mcmeta") != null, "missing " + tex + ".mcmeta");
				}
			}
		helper.assertTrue(BeltGameTests.class.getResource("/assets/create/models/block/belt_casing_andesite/horizontal_middle.json") != null,
			"missing the andesite casing model");
		helper.assertTrue(BeltGameTests.class.getResource("/assets/create/models/block/belt_pulley.json") != null, "missing the pulley model");
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void scrollRateFollowsTheSpeed(GameTestHelper helper) {
		helper.assertTrue(BeltScroll.rateIndex(0, false) == -1, "a stopped belt scrolls");
		// 32 rpm: one texture loop (16 rows) every 15.75 ticks, about one row per tick
		int[] rate = BeltScroll.RATES[BeltScroll.rateIndex(32, false)];
		helper.assertTrue(rate[0] == 1 && rate[1] == 1, "32 rpm uses " + rate[0] + "/" + rate[1]);
		rate = BeltScroll.RATES[BeltScroll.rateIndex(-16, false)];
		helper.assertTrue(rate[0] == 1 && rate[1] == 2, "16 rpm uses " + rate[0] + "/" + rate[1]);
		rate = BeltScroll.RATES[BeltScroll.rateIndex(256, false)];
		helper.assertTrue(rate[0] == 4 && rate[1] == 1, "256 rpm uses " + rate[0] + "/" + rate[1]);
		// the picture moves with the item: facing south at positive speed the items move south as the model
		// does, facing north they move the other way
		helper.assertTrue(BeltScroll.positiveScroll(BeltSlope.HORIZONTAL, Direction.SOUTH, 16), "south, forward");
		helper.assertTrue(!BeltScroll.positiveScroll(BeltSlope.HORIZONTAL, Direction.SOUTH, -16), "south, backward");
		helper.assertTrue(!BeltScroll.positiveScroll(BeltSlope.HORIZONTAL, Direction.NORTH, 16), "north, forward");
		helper.assertTrue(BeltScroll.positiveScroll(BeltSlope.HORIZONTAL, Direction.WEST, 16), "west, forward");
		helper.succeed();
	}

	// ---- tunnels ----

	private static void casedBeltWithTunnel(GameTestHelper helper, int speed, net.minecraft.world.level.block.Block tunnel, BlockPos tunnelPos) {
		buildBelt(helper, speed);
		helper.runAfterDelay(6, () -> {
			belt(helper, tunnelPos.below()).setCasingType(CasingType.ANDESITE);
			helper.setBlock(tunnelPos, tunnel);
			((BeltTunnelBlock) tunnel).updateTunnel(helper.getLevel(), helper.absolutePos(tunnelPos));
		});
	}

	@GameTest(maxTicks = 400)
	public void andesiteTunnelPassesItemsAndFlaps(GameTestHelper helper) {
		BlockPos tunnel = new BlockPos(3, 2, 1);
		casedBeltWithTunnel(helper, 64, BeltRegistry.ANDESITE_TUNNEL, tunnel);
		helper.runAfterDelay(10, () -> {
			var state = helper.getBlockState(tunnel);
			helper.assertTrue(state.getBlock() == BeltRegistry.ANDESITE_TUNNEL, "the tunnel was not placed");
			helper.assertTrue(state.getValue(BeltTunnelBlock.HORIZONTAL_AXIS) == Axis.X, "the tunnel is not along the belt");
			BeltTunnelBlockEntity be = helper.getBlockEntity(tunnel, BeltTunnelBlockEntity.class);
			helper.assertTrue(be.flaps.containsKey(Direction.EAST) && be.flaps.containsKey(Direction.WEST), "flaps: " + be.flaps.keySet());
			handler(helper, new BlockPos(2, 1, 1)).insertItem(0, new ItemStack(Items.QUARTZ, 4), false);
		});
		boolean[] flapped = { false };
		helper.succeedWhen(() -> {
			BeltTunnelBlockEntity be = helper.getBlockEntity(tunnel, BeltTunnelBlockEntity.class);
			for (var flap : be.flaps.values())
				flapped[0] |= !flap.settled();
			helper.assertTrue(flapped[0], "the flaps never moved");
			int found = 0;
			for (ItemEntity item : helper.getEntities(EntityTypes.ITEM, new BlockPos(7, 1, 1), 3))
				if (item.getItem().is(Items.QUARTZ))
					found += item.getItem().getCount();
			helper.assertTrue(found == 4, "found " + found + " quartz past the tunnel");
		});
	}

	@GameTest(maxTicks = 100)
	public void tunnelModelShowsBodyAndFlaps(GameTestHelper helper) {
		BlockPos tunnel = new BlockPos(3, 2, 1);
		casedBeltWithTunnel(helper, 16, BeltRegistry.ANDESITE_TUNNEL, tunnel);
		helper.runAfterDelay(14, () -> {
			// the body from Create's blockstate and four flap segments on each of the two open ends
			int shown = displays(helper, tunnel).size();
			helper.assertTrue(shown == 9, "the tunnel shows " + shown + " displays");
			boolean body = false;
			for (ItemDisplayElement display : displays(helper, tunnel))
				body |= modelOf(display).contains("tunnel/andesite_tunnel/");
			helper.assertTrue(body, "the tunnel body is missing");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void tunnelNeedsACasedBelt(GameTestHelper helper) {
		BlockPos tunnel = new BlockPos(3, 2, 1);
		buildBelt(helper, 16);
		helper.runAfterDelay(6, () -> {
			var state = BeltRegistry.ANDESITE_TUNNEL.defaultBlockState();
			helper.assertTrue(!state.canSurvive(helper.getLevel(), helper.absolutePos(tunnel)), "a tunnel can stand on an uncased belt");
			belt(helper, tunnel.below()).setCasingType(CasingType.ANDESITE);
		});
		helper.runAfterDelay(8, () -> {
			var state = BeltRegistry.ANDESITE_TUNNEL.defaultBlockState();
			helper.assertTrue(state.canSurvive(helper.getLevel(), helper.absolutePos(tunnel)), "a tunnel cannot stand on a cased belt");
			helper.setBlock(tunnel, BeltRegistry.ANDESITE_TUNNEL);
		});
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(helper.getBlockState(tunnel).getBlock() instanceof BeltTunnelBlock, "the tunnel is gone already");
			// taking the casing off takes the tunnel with it
			belt(helper, tunnel.below()).setCasingType(CasingType.NONE);
		});
		helper.runAfterDelay(16, () -> {
			helper.assertTrue(!(helper.getBlockState(tunnel).getBlock() instanceof BeltTunnelBlock), "the tunnel stands on an uncased belt");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 500)
	public void brassTunnelSplitsToTheSide(GameTestHelper helper) {
		BlockPos tunnel = new BlockPos(3, 2, 1);
		BlockPos depot = new BlockPos(3, 1, 2);
		casedBeltWithTunnel(helper, 64, BeltRegistry.BRASS_TUNNEL, tunnel);
		helper.setBlock(depot, ModBlocks.DEPOT);
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(helper.getBlockState(tunnel).getBlock() == BeltRegistry.BRASS_TUNNEL, "the tunnel was not placed");
			helper.assertTrue(BeltTunnelBlock.isJunction(helper.getBlockState(tunnel)), "no side connection: " + helper.getBlockState(tunnel));
			BrassTunnelBlockEntity be = helper.getBlockEntity(tunnel, BrassTunnelBlockEntity.class);
			helper.assertTrue(be.hasDistributionBehaviour(), "the tunnel does not distribute");
			handler(helper, new BlockPos(2, 1, 1)).insertItem(0, new ItemStack(Items.IRON_INGOT, 2), false);
		});
		helper.succeedWhen(() -> {
			ItemStack held = helper.getBlockEntity(depot, DepotBlockEntity.class).getHeldItem();
			helper.assertTrue(held.is(Items.IRON_INGOT), "the depot at the side holds " + held);
		});
	}

	@GameTest(maxTicks = 60)
	public void brassTunnelModeIsSaved(GameTestHelper helper) {
		BlockPos tunnel = new BlockPos(3, 2, 1);
		casedBeltWithTunnel(helper, 16, BeltRegistry.BRASS_TUNNEL, tunnel);
		helper.runAfterDelay(12, () -> {
			BrassTunnelBlockEntity be = helper.getBlockEntity(tunnel, BrassTunnelBlockEntity.class);
			be.setStackToDistribute(new ItemStack(Items.STICK, 3), Direction.WEST);
			var registries = helper.getLevel().registryAccess();
			TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
			be.saveWithFullMetadata(out);
			BlockEntity loaded = BlockEntity.loadStatic(be.getBlockPos(), be.getBlockState(), out.buildResult(), registries);
			helper.assertTrue(loaded instanceof BrassTunnelBlockEntity, "the tunnel did not load");
			ItemStack stack = ((BrassTunnelBlockEntity) loaded).getStackToDistribute();
			helper.assertTrue(stack.is(Items.STICK) && stack.getCount() == 3, "the tunnel holds " + stack);
			helper.succeed();
		});
	}

	// ---- client states ----

	@GameTest(maxTicks = 20)
	public void blocksAreShownWithBlocksOfTheirSize(GameTestHelper helper) {
		BeltBlock belt = BeltRegistry.BELT;
		var flat = belt.defaultBlockState().setValue(BeltBlock.SLOPE, BeltSlope.HORIZONTAL);
		var shown = belt.getPolymerBlockState(flat, null);
		helper.assertTrue(shown.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).max(Axis.Y) >= 13 / 16d,
			"flat belts are shown with " + shown);
		for (Direction d : Direction.Plane.HORIZONTAL)
			for (BeltSlope slope : new BeltSlope[] { BeltSlope.UPWARD, BeltSlope.DOWNWARD }) {
				var state = belt.defaultBlockState().setValue(BeltBlock.SLOPE, slope).setValue(BeltBlock.HORIZONTAL_FACING, d);
				var clientShape = belt.getPolymerBlockState(state, null).getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
				var serverShape = state.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
				helper.assertTrue(!net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(serverShape, clientShape, net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME),
					"server and client collision differ for " + state);
			}
		helper.succeed();
	}
}
