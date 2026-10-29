package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import java.util.Arrays;
import java.util.List;

import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.base.RotatedPillarKineticBlock;
import dev.createpolymer.kinetics.mechanicalArm.ArmBlock;
import dev.createpolymer.kinetics.mechanicalArm.ArmBlockEntity;
import dev.createpolymer.kinetics.mechanicalArm.ArmInteractionPoint;
import dev.createpolymer.kinetics.mechanicalArm.ArmInteractionPoint.Mode;
import dev.createpolymer.kinetics.mechanicalArm.ArmInteractionPointHandler;
import dev.createpolymer.kinetics.mechanicalArm.ArmInteractionPointHandler.Selection;
import dev.createpolymer.kinetics.motor.CreativeMotorBlockEntity;
import dev.createpolymer.logistics.depot.DepotBlockEntity;
import dev.createpolymer.processing.basin.BasinBlockEntity;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.feature.ArmRegistry;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class ArmGameTests {
	private static final BlockPos ARM = new BlockPos(3, 1, 3);

	/**
	 * A motor under a cogwheel next to the arm. Small cogwheels mesh, so the arm turns the other way at the same
	 * speed.
	 */
	private static void power(GameTestHelper helper, int rpm) {
		BlockPos cog = ARM.east();
		helper.setBlock(cog.below(), ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
		helper.setBlock(cog, ModBlocks.COGWHEEL.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.Y));
		helper.runAfterDelay(2, () -> helper.getBlockEntity(cog.below(), CreativeMotorBlockEntity.class).setSetting(rpm));
	}

	private static ArmInteractionPoint point(GameTestHelper helper, BlockPos pos, boolean input) {
		BlockPos abs = helper.absolutePos(pos);
		ArmInteractionPoint point = ArmInteractionPoint.create(helper.getLevel(), abs, helper.getLevel().getBlockState(abs));
		helper.assertTrue(point != null, "no interaction point for the block at " + pos);
		if (input)
			point.cycleMode();
		return point;
	}

	private static void setPoints(GameTestHelper helper, ArmInteractionPoint... points) {
		BlockPos anchor = helper.absolutePos(ARM);
		helper.getBlockEntity(ARM, ArmBlockEntity.class)
			.setInteractionPoints(Arrays.stream(points).map(point -> point.serialize(anchor)).toList());
	}

	private static void fill(GameTestHelper helper, BlockPos chest, Item item, int slots) {
		ChestBlockEntity be = helper.getBlockEntity(chest, ChestBlockEntity.class);
		for (int i = 0; i < slots; i++)
			be.setItem(i, new ItemStack(item));
	}

	private static int countIn(ChestBlockEntity chest, Item item) {
		int count = 0;
		for (int i = 0; i < chest.getContainerSize(); i++)
			if (chest.getItem(i).is(item))
				count += chest.getItem(i).getCount();
		return count;
	}

	@GameTest(maxTicks = 400)
	public void armMovesItemFromChestToDepot(GameTestHelper helper) {
		floor(helper);
		BlockPos chest = new BlockPos(1, 1, 3);
		BlockPos depot = new BlockPos(3, 1, 5);
		helper.setBlock(ARM, ArmRegistry.MECHANICAL_ARM);
		helper.setBlock(chest, Blocks.CHEST);
		helper.setBlock(depot, ModBlocks.DEPOT);
		power(helper, 128);
		helper.runAfterDelay(3, () -> {
			helper.getBlockEntity(chest, ChestBlockEntity.class).setItem(0, new ItemStack(Items.DIAMOND, 3));
			setPoints(helper, point(helper, chest, true), point(helper, depot, false));
		});
		helper.succeedWhen(() -> {
			ItemStack held = helper.getBlockEntity(depot, DepotBlockEntity.class).getHeldItem();
			helper.assertTrue(held.is(Items.DIAMOND) && held.getCount() == 3, "depot holds " + held);
			helper.assertTrue(countIn(helper.getBlockEntity(chest, ChestBlockEntity.class), Items.DIAMOND) == 0,
				"the chest still has diamonds");
		});
	}

	@GameTest(maxTicks = 400)
	public void armMovesItemFromDepotToBasin(GameTestHelper helper) {
		floor(helper);
		BlockPos depot = new BlockPos(1, 1, 3);
		BlockPos basin = new BlockPos(3, 1, 5);
		helper.setBlock(ARM, ArmRegistry.MECHANICAL_ARM);
		helper.setBlock(depot, ModBlocks.DEPOT);
		helper.setBlock(basin, ModBlocks.BASIN);
		power(helper, 128);
		helper.runAfterDelay(3, () -> {
			helper.getBlockEntity(depot, DepotBlockEntity.class).depotBehaviour.itemHandler
				.insertItem(0, new ItemStack(Items.GOLD_INGOT, 2), false);
			setPoints(helper, point(helper, depot, true), point(helper, basin, false));
		});
		helper.succeedWhen(() -> {
			BasinBlockEntity be = helper.getBlockEntity(basin, BasinBlockEntity.class);
			int found = 0;
			for (int i = 0; i < be.inputInventory.size(); i++)
				if (be.inputInventory.get(i).is(Items.GOLD_INGOT))
					found += be.inputInventory.get(i).getCount();
			helper.assertTrue(found == 2, "basin holds " + found + " gold ingots");
			helper.assertTrue(helper.getBlockEntity(depot, DepotBlockEntity.class).getHeldItem().isEmpty(), "the depot still holds an item");
		});
	}

	@GameTest(maxTicks = 600)
	public void roundRobinSharesItemsBetweenOutputs(GameTestHelper helper) {
		floor(helper);
		BlockPos in = new BlockPos(1, 1, 3);
		BlockPos outA = new BlockPos(5, 1, 3);
		BlockPos outB = new BlockPos(3, 1, 5);
		helper.setBlock(ARM, ArmRegistry.MECHANICAL_ARM);
		helper.setBlock(in, Blocks.CHEST);
		helper.setBlock(outA, Blocks.CHEST);
		helper.setBlock(outB, Blocks.CHEST);
		power(helper, 256);
		helper.runAfterDelay(3, () -> {
			fill(helper, in, Items.DIRT, 6);
			setPoints(helper, point(helper, in, true), point(helper, outA, false), point(helper, outB, false));
		});
		helper.succeedWhen(() -> {
			int a = countIn(helper.getBlockEntity(outA, ChestBlockEntity.class), Items.DIRT);
			int b = countIn(helper.getBlockEntity(outB, ChestBlockEntity.class), Items.DIRT);
			helper.assertTrue(a == 3 && b == 3, "outputs got " + a + " and " + b + " items, expected 3 each");
		});
	}

	@GameTest(maxTicks = 600)
	public void preferFirstFillsTheFirstOutput(GameTestHelper helper) {
		floor(helper);
		BlockPos in = new BlockPos(1, 1, 3);
		BlockPos outA = new BlockPos(5, 1, 3);
		BlockPos outB = new BlockPos(3, 1, 5);
		helper.setBlock(ARM, ArmRegistry.MECHANICAL_ARM);
		helper.setBlock(in, Blocks.CHEST);
		helper.setBlock(outA, Blocks.CHEST);
		helper.setBlock(outB, Blocks.CHEST);
		power(helper, 256);
		helper.runAfterDelay(3, () -> {
			fill(helper, in, Items.DIRT, 4);
			setPoints(helper, point(helper, in, true), point(helper, outA, false), point(helper, outB, false));
		});
		helper.runAfterDelay(4, () -> {
			var mode = helper.getBlockEntity(ARM, ArmBlockEntity.class)
				.getBehaviour(dev.createpolymer.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE);
			helper.assertTrue(mode != null, "no distribution setting");
			mode.setValue(ArmBlockEntity.SelectionMode.PREFER_FIRST.ordinal());
		});
		helper.succeedWhen(() -> {
			int a = countIn(helper.getBlockEntity(outA, ChestBlockEntity.class), Items.DIRT);
			int b = countIn(helper.getBlockEntity(outB, ChestBlockEntity.class), Items.DIRT);
			helper.assertTrue(a == 4 && b == 0, "outputs got " + a + " and " + b + " items, expected 4 and 0");
		});
	}

	@GameTest(maxTicks = 40)
	public void rightClickCyclesInputOutputRemoved(GameTestHelper helper) {
		floor(helper);
		BlockPos chest = new BlockPos(1, 1, 1);
		helper.setBlock(chest, Blocks.CHEST);
		helper.runAfterDelay(2, () -> {
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			ItemStack arm = new ItemStack(ArmRegistry.MECHANICAL_ARM_ITEM);
			player.setItemInHand(InteractionHand.MAIN_HAND, arm);
			BlockPos abs = helper.absolutePos(chest);
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);

			for (Mode expected : new Mode[] { Mode.TAKE, Mode.DEPOSIT }) {
				InteractionResult result = ArmInteractionPointHandler.rightClickingBlocksSelectsThem(player, helper.getLevel(),
					InteractionHand.MAIN_HAND, hit);
				helper.assertTrue(result == InteractionResult.SUCCESS, "click was not handled: " + result);
				Selection selection = arm.get(ArmRegistry.ARM_POINTS);
				helper.assertTrue(selection != null && selection.points().size() == 1 && selection.points().get(0).pos().equals(abs)
					&& selection.points().get(0).mode() == expected, "selection is " + selection + ", expected " + expected);
			}
			ArmInteractionPointHandler.rightClickingBlocksSelectsThem(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit);
			helper.assertTrue(arm.get(ArmRegistry.ARM_POINTS) == null, "the third click did not remove the point");

			// a block an arm cannot use is not selected, and the click is left to normal placement
			BlockPos stone = helper.absolutePos(new BlockPos(2, 0, 2));
			InteractionResult ignored = ArmInteractionPointHandler.rightClickingBlocksSelectsThem(player, helper.getLevel(),
				InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(stone), Direction.UP, stone, false));
			helper.assertTrue(ignored == InteractionResult.PASS && arm.get(ArmRegistry.ARM_POINTS) == null, "stone was selected");

			// left click removes a selected point
			ArmInteractionPointHandler.rightClickingBlocksSelectsThem(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit);
			InteractionResult removed = ArmInteractionPointHandler.leftClickingBlocksDeselectsThem(player, helper.getLevel(),
				InteractionHand.MAIN_HAND, abs, Direction.UP);
			helper.assertTrue(removed == InteractionResult.SUCCESS && arm.get(ArmRegistry.ARM_POINTS) == null, "left click did not deselect");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void placementTransfersPointsAndDropsOutOfRangeOnes(GameTestHelper helper) {
		floor(helper);
		BlockPos near = new BlockPos(6, 1, 5);
		BlockPos nearDepot = new BlockPos(5, 1, 6);
		BlockPos far = new BlockPos(0, 1, 0);
		helper.setBlock(near, Blocks.CHEST);
		helper.setBlock(nearDepot, ModBlocks.DEPOT);
		helper.setBlock(far, Blocks.CHEST);
		helper.runAfterDelay(2, () -> {
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			ItemStack stack = new ItemStack(ArmRegistry.MECHANICAL_ARM_ITEM, 2);
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			// near chest becomes an input, the depot an output (two clicks), the far chest an input
			for (BlockPos pos : new BlockPos[] { near, nearDepot, nearDepot, far }) {
				BlockPos abs = helper.absolutePos(pos);
				ArmInteractionPointHandler.rightClickingBlocksSelectsThem(player, helper.getLevel(), InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
			}
			Selection selection = stack.get(ArmRegistry.ARM_POINTS);
			helper.assertTrue(selection != null && selection.points().size() == 3, "selection is " + selection);

			// place the arm on the floor at (6, 1, 6): the chest at (0, 1, 0) is out of the range of 5
			BlockPos floor = helper.absolutePos(new BlockPos(6, 0, 6));
			BlockHitResult top = new BlockHitResult(Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false);
			InteractionResult placed = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, top));
			helper.assertTrue(placed.consumesAction(), "the arm was not placed: " + placed);
			helper.assertTrue(helper.getBlockState(new BlockPos(6, 1, 6)).is(ArmRegistry.MECHANICAL_ARM), "no arm at the target");
			helper.assertTrue(stack.getCount() == 1 && stack.get(ArmRegistry.ARM_POINTS) == null,
				"the rest of the stack kept the selection: " + stack + " " + stack.get(ArmRegistry.ARM_POINTS));
			helper.runAfterDelay(3, () -> {
				ArmBlockEntity arm = helper.getBlockEntity(new BlockPos(6, 1, 6), ArmBlockEntity.class);
				helper.assertTrue(arm.getInputs().size() == 1 && arm.getInputs().get(0).getPos().equals(helper.absolutePos(near)),
					"inputs are " + arm.getInputs().size());
				helper.assertTrue(arm.getOutputs().size() == 1 && arm.getOutputs().get(0).getPos().equals(helper.absolutePos(nearDepot)),
					"outputs are " + arm.getOutputs().size());
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = 40)
	public void pointsAndHeldItemSurviveSaving(GameTestHelper helper) {
		floor(helper);
		BlockPos chest = new BlockPos(1, 1, 3);
		BlockPos depot = new BlockPos(3, 1, 5);
		helper.setBlock(ARM, ArmRegistry.MECHANICAL_ARM);
		helper.setBlock(chest, Blocks.CHEST);
		helper.setBlock(depot, ModBlocks.DEPOT);
		helper.runAfterDelay(3, () -> {
			setPoints(helper, point(helper, chest, true), point(helper, depot, false));
			ArmBlockEntity arm = helper.getBlockEntity(ARM, ArmBlockEntity.class);
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(arm.getInputs().size() == 1 && arm.getOutputs().size() == 1, "points were not loaded");
				var registries = helper.getLevel().registryAccess();
				CompoundTag saved = arm.saveWithFullMetadata(registries);
				BlockEntity loaded = BlockEntity.loadStatic(arm.getBlockPos(), arm.getBlockState(), saved, registries);
				helper.assertTrue(loaded instanceof ArmBlockEntity, "block entity did not load");
				CompoundTag again = loaded.saveWithFullMetadata(registries);
				helper.assertTrue(again.getListOrEmpty("InteractionPoints").size() == 2, "points were lost: " + again);
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = 100)
	public void ceilingArmAndModelParts(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 3, 3);
		helper.setBlock(pos, ArmRegistry.MECHANICAL_ARM.defaultBlockState().setValue(ArmBlock.CEILING, true));
		helper.runAfterDelay(4, () -> {
			BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(pos));
			helper.assertTrue(attachment != null, "the arm has no model");
			int shown = 0;
			for (VirtualElement element : attachment.holder().getElements())
				if (element instanceof ItemDisplayElement display && !display.getItem().isEmpty())
					shown++;
			// casing, cog, base, lower arm, upper arm, claw and two grips
			helper.assertTrue(shown == 8, "the arm shows " + shown + " parts");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void goggledArmKeepsGoggles(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(ARM, ArmRegistry.MECHANICAL_ARM);
		helper.runAfterDelay(2, () -> {
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(dev.createpolymer.registry.ModItems.GOGGLES));
			helper.useBlock(ARM, player);
			ArmBlockEntity arm = helper.getBlockEntity(ARM, ArmBlockEntity.class);
			helper.assertTrue(arm.hasGoggles(), "the goggles were not put on");
			var registries = helper.getLevel().registryAccess();
			BlockEntity loaded = BlockEntity.loadStatic(arm.getBlockPos(), arm.getBlockState(), arm.saveWithFullMetadata(registries), registries);
			helper.assertTrue(loaded instanceof ArmBlockEntity a && a.hasGoggles(), "the goggles were lost when saving");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 20)
	public void allTypesAreRegisteredInPriorityOrder(GameTestHelper helper) {
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
		helper.setBlock(new BlockPos(2, 1, 1), Blocks.JUKEBOX);
		helper.setBlock(new BlockPos(3, 1, 1), Blocks.COMPOSTER);
		helper.setBlock(new BlockPos(4, 1, 1), Blocks.STONE);
		helper.runAfterDelay(2, () -> {
			var level = helper.getLevel();
			var chest = ArmInteractionPoint.create(level, helper.absolutePos(new BlockPos(1, 1, 1)), Blocks.CHEST.defaultBlockState());
			helper.assertTrue(chest != null && chest.getType() == dev.createpolymer.kinetics.mechanicalArm.AllArmInteractionPointTypes.INVENTORY,
				"a chest is not an inventory point");
			var jukebox = ArmInteractionPoint.create(level, helper.absolutePos(new BlockPos(2, 1, 1)), Blocks.JUKEBOX.defaultBlockState());
			helper.assertTrue(jukebox instanceof dev.createpolymer.kinetics.mechanicalArm.AllArmInteractionPointTypes.JukeboxPoint,
				"a jukebox is not a jukebox point");
			var composter = ArmInteractionPoint.create(level, helper.absolutePos(new BlockPos(3, 1, 1)), Blocks.COMPOSTER.defaultBlockState());
			helper.assertTrue(composter instanceof dev.createpolymer.kinetics.mechanicalArm.AllArmInteractionPointTypes.ComposterPoint,
				"a composter is not a composter point");
			helper.assertTrue(ArmInteractionPoint.create(level, helper.absolutePos(new BlockPos(4, 1, 1)), Blocks.STONE.defaultBlockState()) == null,
				"stone is a point");
			helper.succeed();
		});
	}
}
