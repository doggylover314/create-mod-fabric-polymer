package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.assertSpeed;
import static dev.createpolymer.test.TestUtil.floor;

import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.base.RotatedPillarKineticBlock;
import dev.createpolymer.kinetics.chaindrive.ChainDriveBlock;
import dev.createpolymer.kinetics.gauge.GaugeBlockEntity;
import dev.createpolymer.kinetics.motor.CreativeMotorBlockEntity;
import dev.createpolymer.kinetics.press.MechanicalPressBlock;
import dev.createpolymer.logistics.depot.DepotBlockEntity;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

public class TransmissionGameTests {

	private static void motorEast(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
	}

	private static void shaftX(GameTestHelper helper, int x, int y, int z) {
		helper.setBlock(new BlockPos(x, y, z), ModBlocks.SHAFT.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.X));
	}

	@GameTest(maxTicks = 60)
	public void casingEncasesShaftAndKeepsItTurning(GameTestHelper helper) {
		floor(helper);
		motorEast(helper, new BlockPos(1, 1, 1));
		shaftX(helper, 2, 1, 1);
		shaftX(helper, 3, 1, 1);
		helper.runAfterDelay(2, () -> {
			var player = helper.makeMockPlayer(GameType.SURVIVAL);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ANDESITE_CASING));
			helper.useBlock(new BlockPos(2, 1, 1), player);
		});
		helper.runAfterDelay(6, () -> helper.succeedWhen(() -> {
			helper.assertBlockPresent(ModBlocks.ANDESITE_ENCASED_SHAFT, new BlockPos(2, 1, 1));
			assertSpeed(helper, new BlockPos(3, 1, 1), 16);
		}));
	}

	@GameTest(maxTicks = 60)
	public void poweredClutchDisconnects(GameTestHelper helper) {
		floor(helper);
		motorEast(helper, new BlockPos(1, 1, 1));
		shaftX(helper, 2, 1, 1);
		helper.setBlock(new BlockPos(3, 1, 1), ModBlocks.CLUTCH.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.X));
		shaftX(helper, 4, 1, 1);
		helper.runAfterDelay(5, () -> {
			assertSpeed(helper, new BlockPos(4, 1, 1), 16);
			helper.setBlock(new BlockPos(3, 2, 1), Blocks.REDSTONE_BLOCK);
		});
		helper.runAfterDelay(10, () -> helper.succeedWhen(() -> assertSpeed(helper, new BlockPos(4, 1, 1), 0)));
	}

	@GameTest(maxTicks = 60)
	public void poweredGearshiftReverses(GameTestHelper helper) {
		floor(helper);
		motorEast(helper, new BlockPos(1, 1, 1));
		shaftX(helper, 2, 1, 1);
		helper.setBlock(new BlockPos(3, 1, 1), ModBlocks.GEARSHIFT.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.X));
		shaftX(helper, 4, 1, 1);
		helper.runAfterDelay(5, () -> {
			assertSpeed(helper, new BlockPos(4, 1, 1), 16);
			helper.setBlock(new BlockPos(3, 2, 1), Blocks.REDSTONE_BLOCK);
		});
		helper.runAfterDelay(10, () -> helper.succeedWhen(() -> assertSpeed(helper, new BlockPos(4, 1, 1), -16)));
	}

	@GameTest(maxTicks = 60)
	public void chainDrivesShareRotation(GameTestHelper helper) {
		floor(helper);
		motorEast(helper, new BlockPos(1, 1, 1));
		// two chain drives on the X axis, next to each other along Z, linked by updateShape
		helper.setBlock(new BlockPos(2, 1, 1), ChainDriveBlock.connect(
			ModBlocks.ENCASED_CHAIN_DRIVE.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.X), Direction.SOUTH,
			ModBlocks.ENCASED_CHAIN_DRIVE.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.X)));
		helper.setBlock(new BlockPos(2, 1, 2), ChainDriveBlock.connect(
			ModBlocks.ENCASED_CHAIN_DRIVE.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.X), Direction.NORTH,
			helper.getBlockState(new BlockPos(2, 1, 1))));
		shaftX(helper, 3, 1, 2);
		helper.succeedWhen(() -> {
			var a = helper.getBlockState(new BlockPos(2, 1, 1)).getValue(ChainDriveBlock.PART);
			var b = helper.getBlockState(new BlockPos(2, 1, 2)).getValue(ChainDriveBlock.PART);
			helper.assertTrue(a != ChainDriveBlock.Part.NONE && b != ChainDriveBlock.Part.NONE, "not linked: " + a + " " + b);
			assertSpeed(helper, new BlockPos(3, 1, 2), 16);
		});
	}

	@GameTest(maxTicks = 60)
	public void speedometerShowsSpeedAndPassesItOn(GameTestHelper helper) {
		floor(helper);
		motorEast(helper, new BlockPos(1, 1, 1));
		helper.setBlock(new BlockPos(2, 1, 1), ModBlocks.SPEEDOMETER.defaultBlockState()
			.setValue(DirectionalKineticBlock.FACING, Direction.UP).setValue(dev.createpolymer.kinetics.base.DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, true));
		shaftX(helper, 3, 1, 1);
		helper.runAfterDelay(2, () -> helper.getBlockEntity(new BlockPos(1, 1, 1), CreativeMotorBlockEntity.class).setSetting(64));
		helper.runAfterDelay(6, () -> helper.succeedWhen(() -> {
			assertSpeed(helper, new BlockPos(3, 1, 1), 64);
			GaugeBlockEntity gauge = helper.getBlockEntity(new BlockPos(2, 1, 1), GaugeBlockEntity.class);
			helper.assertTrue(gauge.dialTarget > .45f && gauge.dialTarget < .75f, "dial target " + gauge.dialTarget);
		}));
	}

	@GameTest(maxTicks = 60)
	public void stressometerShowsLoad(GameTestHelper helper) {
		floor(helper);
		BlockPos crank = new BlockPos(1, 1, 1);
		helper.setBlock(new BlockPos(2, 1, 1), ModBlocks.STRESSOMETER.defaultBlockState()
			.setValue(DirectionalKineticBlock.FACING, Direction.UP).setValue(dev.createpolymer.kinetics.base.DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, true));
		helper.setBlock(new BlockPos(3, 1, 1), ModBlocks.MECHANICAL_PRESS.defaultBlockState().setValue(MechanicalPressBlock.HORIZONTAL_FACING, Direction.EAST));
		// the crank faces away from the gauge it is mounted on, so its shaft points into the gauge
		helper.setBlock(crank, ModBlocks.HAND_CRANK.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.WEST));
		helper.runAfterDelay(2, () -> helper.useBlock(crank, helper.makeMockPlayer(GameType.SURVIVAL)));
		// press uses 8 su/rpm * 32 = 256 su, crank gives 256 su: full load
		helper.runAfterDelay(5, () -> helper.succeedWhen(() -> {
			GaugeBlockEntity gauge = helper.getBlockEntity(new BlockPos(2, 1, 1), GaugeBlockEntity.class);
			helper.assertTrue(Math.abs(gauge.dialTarget - 1) < 1e-3, "dial target " + gauge.dialTarget);
		}));
	}

	@GameTest(maxTicks = 400)
	public void pressFlattensItemOnDepot(GameTestHelper helper) {
		floor(helper);
		BlockPos depotPos = new BlockPos(2, 1, 2);
		helper.setBlock(depotPos, ModBlocks.DEPOT);
		helper.setBlock(new BlockPos(1, 3, 2), ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(2, 3, 2), ModBlocks.MECHANICAL_PRESS.defaultBlockState().setValue(MechanicalPressBlock.HORIZONTAL_FACING, Direction.EAST));
		helper.runAfterDelay(2, () -> {
			helper.getBlockEntity(new BlockPos(1, 3, 2), CreativeMotorBlockEntity.class).setSetting(256);
			helper.getBlockEntity(depotPos, DepotBlockEntity.class).setHeldItem(new ItemStack(Items.GOLD_INGOT, 2));
		});
		helper.succeedWhen(() -> {
			DepotBlockEntity depot = helper.getBlockEntity(depotPos, DepotBlockEntity.class);
			int sheets = 0;
			for (ItemStack s : depot.outputs.stacks())
				if (s.is(ModItems.GOLDEN_SHEET))
					sheets += s.getCount();
			helper.assertTrue(sheets == 2 && depot.getHeldItem().isEmpty(), "sheets " + sheets + ", held " + depot.getHeldItem());
		});
	}

	@GameTest(maxTicks = 120)
	public void largeWaterWheelBuildsStructureAndTurns(GameTestHelper helper) {
		floor(helper);
		BlockPos wheel = new BlockPos(3, 3, 3);
		helper.setBlock(wheel, ModBlocks.LARGE_WATER_WHEEL.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.Z));
		helper.setBlock(new BlockPos(3, 3, 4), ModBlocks.SHAFT.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.Z));
		helper.runAfterDelay(5, () -> {
			helper.assertBlockPresent(ModBlocks.WATER_WHEEL_STRUCTURAL, new BlockPos(2, 3, 3));
			helper.assertBlockPresent(ModBlocks.WATER_WHEEL_STRUCTURAL, new BlockPos(4, 4, 3));
			// water runs east along a channel over the top of the wheel (its structure blocks form the floor)
			helper.setBlock(new BlockPos(1, 4, 3), Blocks.STONE);
			helper.setBlock(new BlockPos(0, 5, 3), Blocks.STONE);
			for (int x = 0; x <= 5; x++) {
				helper.setBlock(new BlockPos(x, 5, 2), Blocks.STONE);
				helper.setBlock(new BlockPos(x, 5, 4), Blocks.STONE);
			}
			helper.setBlock(new BlockPos(1, 5, 3), Blocks.WATER);
		});
		helper.runAfterDelay(20, () -> helper.succeedWhen(() -> {
			float s = TestUtil.speed(helper, wheel);
			helper.assertTrue(Math.abs(s) == 4, "large wheel speed was " + s);
			helper.assertTrue(Math.abs(TestUtil.speed(helper, new BlockPos(3, 3, 4))) == 4, "shaft not turning");
		}));
	}
}
