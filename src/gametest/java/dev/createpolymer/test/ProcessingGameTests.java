package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.base.RotatedPillarKineticBlock;
import dev.createpolymer.kinetics.millstone.MillstoneBlockEntity;
import dev.createpolymer.kinetics.motor.CreativeMotorBlockEntity;
import dev.createpolymer.kinetics.press.MechanicalPressBlock;
import dev.createpolymer.processing.basin.BasinBlockEntity;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public class ProcessingGameTests {

	private static boolean contains(BasinBlockEntity basin, Item item) {
		for (ItemStack s : basin.outputInventory.stacks())
			if (s.is(item))
				return true;
		return false;
	}

	@GameTest(maxTicks = 300)
	public void millstoneGrindsWheat(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(2, 1, 2), ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
		BlockPos mill = new BlockPos(2, 2, 2);
		helper.setBlock(mill, ModBlocks.MILLSTONE);
		helper.runAfterDelay(2, () -> helper.getBlockEntity(new BlockPos(2, 1, 2), CreativeMotorBlockEntity.class).setSetting(128));
		helper.runAfterDelay(4, () -> helper.spawnItem(Items.WHEAT, new BlockPos(2, 3, 2)));
		helper.succeedWhen(() -> {
			MillstoneBlockEntity be = helper.getBlockEntity(mill, MillstoneBlockEntity.class);
			boolean flour = false;
			for (ItemStack s : be.outputInv.stacks())
				flour |= s.is(ModItems.WHEAT_FLOUR);
			helper.assertTrue(flour, "no wheat flour yet");
		});
	}

	@GameTest(maxTicks = 400)
	public void pressFlattensIngotOnTheGround(GameTestHelper helper) {
		floor(helper);
		// the press works on items lying in the block space directly below it
		helper.setBlock(new BlockPos(1, 2, 2), ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.MECHANICAL_PRESS.defaultBlockState().setValue(MechanicalPressBlock.HORIZONTAL_FACING, Direction.EAST));
		helper.runAfterDelay(2, () -> helper.getBlockEntity(new BlockPos(1, 2, 2), CreativeMotorBlockEntity.class).setSetting(256));
		helper.runAfterDelay(4, () -> helper.spawnItem(Items.IRON_INGOT, 2.5f, 1.2f, 2.5f));
		helper.succeedWhen(() -> helper.assertItemEntityPresent(ModItems.IRON_SHEET, new BlockPos(2, 1, 2), 2));
	}

	@GameTest(maxTicks = 400)
	public void mixerMakesAndesiteAlloy(GameTestHelper helper) {
		floor(helper);
		BlockPos basinPos = new BlockPos(2, 1, 2);
		helper.setBlock(basinPos, ModBlocks.BASIN);
		helper.setBlock(new BlockPos(2, 3, 2), ModBlocks.MECHANICAL_MIXER);
		// drive the mixer through a cogwheel next to it, powered from below
		helper.setBlock(new BlockPos(3, 2, 2), ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
		helper.setBlock(new BlockPos(3, 3, 2), ModBlocks.COGWHEEL.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.Y));
		helper.runAfterDelay(2, () -> {
			helper.getBlockEntity(new BlockPos(3, 2, 2), CreativeMotorBlockEntity.class).setSetting(128);
			BasinBlockEntity basin = helper.getBlockEntity(basinPos, BasinBlockEntity.class);
			basin.insertInput(new ItemStack(Items.ANDESITE), false);
			basin.insertInput(new ItemStack(Items.IRON_NUGGET), false);
		});
		helper.succeedWhen(() -> helper.assertTrue(
			contains(helper.getBlockEntity(basinPos, BasinBlockEntity.class), ModItems.ANDESITE_ALLOY), "no andesite alloy yet"));
	}

	@GameTest(maxTicks = 500)
	public void pressCompactsIngotsIntoBlock(GameTestHelper helper) {
		floor(helper);
		BlockPos basinPos = new BlockPos(2, 1, 2);
		helper.setBlock(basinPos, ModBlocks.BASIN);
		helper.setBlock(new BlockPos(1, 3, 2), ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(2, 3, 2), ModBlocks.MECHANICAL_PRESS.defaultBlockState().setValue(MechanicalPressBlock.HORIZONTAL_FACING, Direction.EAST));
		helper.runAfterDelay(2, () -> {
			helper.getBlockEntity(new BlockPos(1, 3, 2), CreativeMotorBlockEntity.class).setSetting(256);
			helper.getBlockEntity(basinPos, BasinBlockEntity.class).insertInput(new ItemStack(Items.IRON_INGOT, 9), false);
		});
		helper.succeedWhen(() -> helper.assertTrue(
			contains(helper.getBlockEntity(basinPos, BasinBlockEntity.class), Items.IRON_BLOCK), "no iron block yet"));
	}

	@GameTest(maxTicks = 100)
	public void hopperPullsFromBasinOutput(GameTestHelper helper) {
		floor(helper);
		BlockPos basinPos = new BlockPos(2, 2, 2);
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.HOPPER);
		helper.setBlock(basinPos, ModBlocks.BASIN);
		helper.runAfterDelay(2, () -> helper.getBlockEntity(basinPos, BasinBlockEntity.class).outputInventory.set(0, new ItemStack(Items.DIAMOND)));
		helper.succeedWhen(() -> {
			var hopper = helper.getBlockEntity(new BlockPos(2, 1, 2), net.minecraft.world.level.block.entity.HopperBlockEntity.class);
			helper.assertTrue(hopper.getItem(0).is(Items.DIAMOND), "hopper did not pull from basin");
		});
	}
}
