package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.assertSpeed;
import static dev.createpolymer.test.TestUtil.floor;

import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.base.RotatedPillarKineticBlock;
import dev.createpolymer.kinetics.crank.HandCrankBlock;
import dev.createpolymer.kinetics.motor.CreativeMotorBlockEntity;
import dev.createpolymer.kinetics.press.MechanicalPressBlock;
import dev.createpolymer.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

public class KineticGameTests {

	private static void shaft(GameTestHelper helper, int x, int y, int z, Axis axis) {
		helper.setBlock(new BlockPos(x, y, z), ModBlocks.SHAFT.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, axis));
	}

	private static void motor(GameTestHelper helper, BlockPos pos, Direction facing) {
		helper.setBlock(pos, ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, facing));
	}

	@GameTest(maxTicks = 40)
	public void motorDrivesShaftLine(GameTestHelper helper) {
		floor(helper);
		motor(helper, new BlockPos(1, 1, 1), Direction.EAST);
		for (int x = 2; x <= 5; x++)
			shaft(helper, x, 1, 1, Axis.X);
		helper.succeedWhen(() -> {
			for (int x = 2; x <= 5; x++)
				assertSpeed(helper, new BlockPos(x, 1, 1), 16);
		});
	}

	@GameTest(maxTicks = 40)
	public void motorFacingWestSpinsNegative(GameTestHelper helper) {
		floor(helper);
		motor(helper, new BlockPos(5, 1, 1), Direction.WEST);
		shaft(helper, 4, 1, 1, Axis.X);
		helper.succeedWhen(() -> assertSpeed(helper, new BlockPos(4, 1, 1), -16));
	}

	@GameTest(maxTicks = 40)
	public void largeCogDrivesSmallCogAtDoubleSpeed(GameTestHelper helper) {
		floor(helper);
		motor(helper, new BlockPos(2, 1, 2), Direction.UP);
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.LARGE_COGWHEEL.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.Y));
		helper.setBlock(new BlockPos(3, 2, 3), ModBlocks.COGWHEEL.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.Y));
		helper.setBlock(new BlockPos(4, 2, 3), ModBlocks.COGWHEEL.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.Y));
		helper.succeedWhen(() -> {
			assertSpeed(helper, new BlockPos(2, 2, 2), 16);
			assertSpeed(helper, new BlockPos(3, 2, 3), -32);
			// small cog next to small cog: same speed, opposite direction
			assertSpeed(helper, new BlockPos(4, 2, 3), 32);
		});
	}

	@GameTest(maxTicks = 40)
	public void gearboxTurnsRotationSideways(GameTestHelper helper) {
		floor(helper);
		motor(helper, new BlockPos(1, 1, 1), Direction.EAST);
		shaft(helper, 2, 1, 1, Axis.X);
		helper.setBlock(new BlockPos(3, 1, 1), ModBlocks.GEARBOX.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.Y));
		shaft(helper, 3, 1, 2, Axis.Z);
		shaft(helper, 4, 1, 1, Axis.X);
		helper.succeedWhen(() -> {
			assertSpeed(helper, new BlockPos(3, 1, 1), 16);
			helper.assertTrue(TestUtil.speed(helper, new BlockPos(3, 1, 2)) != 0, "side shaft not turning");
			// straight through a gearbox the rotation is reversed
			assertSpeed(helper, new BlockPos(4, 1, 1), -16);
		});
	}

	@GameTest(maxTicks = 60)
	public void breakingTheSourceStopsTheLine(GameTestHelper helper) {
		floor(helper);
		BlockPos motorPos = new BlockPos(1, 1, 1);
		motor(helper, motorPos, Direction.EAST);
		shaft(helper, 2, 1, 1, Axis.X);
		shaft(helper, 3, 1, 1, Axis.X);
		helper.runAfterDelay(10, () -> {
			assertSpeed(helper, new BlockPos(3, 1, 1), 16);
			helper.setBlock(motorPos, Blocks.AIR);
		});
		helper.runAfterDelay(20, () -> helper.succeedWhen(() -> assertSpeed(helper, new BlockPos(3, 1, 1), 0)));
	}

	@GameTest(maxTicks = 40)
	public void motorSpeedChangePropagates(GameTestHelper helper) {
		floor(helper);
		BlockPos motorPos = new BlockPos(1, 1, 1);
		motor(helper, motorPos, Direction.EAST);
		shaft(helper, 2, 1, 1, Axis.X);
		helper.runAfterDelay(5, () -> helper.getBlockEntity(motorPos, CreativeMotorBlockEntity.class).setSetting(64));
		helper.runAfterDelay(10, () -> helper.succeedWhen(() -> assertSpeed(helper, new BlockPos(2, 1, 1), 64)));
	}

	@GameTest(maxTicks = 60)
	public void handCrankTurnsWhenUsed(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
		BlockPos crank = new BlockPos(2, 1, 1);
		helper.setBlock(crank, ModBlocks.HAND_CRANK.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
		// crank faces east, its shaft side is west: drive a shaft on the other side? The crank's shaft faces the support.
		// So drive something via a cog-less shaft below instead: check the crank itself turns.
		helper.runAfterDelay(2, () -> helper.useBlock(crank, helper.makeMockPlayer(GameType.SURVIVAL)));
		helper.runAfterDelay(4, () -> helper.succeedWhen(() ->
			assertSpeed(helper, crank, 32)));
	}

	@GameTest(maxTicks = 60)
	public void handCrankDrivesShaft(GameTestHelper helper) {
		floor(helper);
		BlockPos crank = new BlockPos(3, 1, 1);
		// shaft is the support block for the crank (cranks need something solid-ish behind them)
		shaft(helper, 2, 1, 1, Axis.X);
		helper.setBlock(crank, ModBlocks.HAND_CRANK.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
		helper.runAfterDelay(2, () -> helper.useBlock(crank, helper.makeMockPlayer(GameType.SURVIVAL)));
		helper.runAfterDelay(4, () -> helper.succeedWhen(() -> assertSpeed(helper, new BlockPos(2, 1, 1), 32)));
	}

	@GameTest(maxTicks = 60)
	public void tooMuchLoadOverstresses(GameTestHelper helper) {
		floor(helper);
		BlockPos crank = new BlockPos(1, 1, 1);
		// hand crank: 8 su/rpm * 32 rpm = 256 su. Two presses at 32 rpm: 2 * 8 * 32 = 512 su.
		shaft(helper, 2, 1, 1, Axis.X);
		helper.setBlock(new BlockPos(3, 1, 1), ModBlocks.MECHANICAL_PRESS.defaultBlockState().setValue(MechanicalPressBlock.HORIZONTAL_FACING, Direction.EAST));
		helper.setBlock(new BlockPos(4, 1, 1), ModBlocks.MECHANICAL_PRESS.defaultBlockState().setValue(MechanicalPressBlock.HORIZONTAL_FACING, Direction.EAST));
		helper.setBlock(new BlockPos(0, 1, 1), Blocks.STONE);
		helper.setBlock(crank, ModBlocks.HAND_CRANK.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.WEST));
		helper.runAfterDelay(2, () -> helper.useBlock(crank, helper.makeMockPlayer(GameType.SURVIVAL)));
		helper.runAfterDelay(6, () -> helper.succeedWhen(() -> {
			KineticBlockEntity press = helper.getBlockEntity(new BlockPos(4, 1, 1), KineticBlockEntity.class);
			helper.assertTrue(press.isOverStressed(), "press should be overstressed (stress " + press.getNetworkStress() + " / " + press.getNetworkCapacity() + ")");
			assertSpeed(helper, new BlockPos(4, 1, 1), 0);
		}));
	}

	@GameTest(maxTicks = 100)
	public void waterWheelTurnsInFlowingWater(GameTestHelper helper) {
		floor(helper);
		BlockPos wheel = new BlockPos(3, 2, 3);
		helper.setBlock(wheel, ModBlocks.WATER_WHEEL.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.SOUTH));
		helper.setBlock(new BlockPos(3, 1, 3), Blocks.STONE);
		// a channel so the water can only run east, over the top of the wheel
		for (BlockPos p : new BlockPos[] { new BlockPos(2, 2, 3), new BlockPos(1, 2, 3), new BlockPos(2, 2, 2), new BlockPos(2, 2, 4),
			new BlockPos(1, 3, 3), new BlockPos(2, 3, 2), new BlockPos(2, 3, 4), new BlockPos(3, 3, 2), new BlockPos(3, 3, 4) })
			helper.setBlock(p, Blocks.STONE);
		helper.setBlock(new BlockPos(2, 3, 3), Blocks.WATER);
		shaft(helper, 3, 2, 4, Axis.Z);
		helper.succeedWhen(() -> {
			float s = TestUtil.speed(helper, wheel);
			helper.assertTrue(Math.abs(s) == 8, "water wheel speed was " + s);
			helper.assertTrue(Math.abs(TestUtil.speed(helper, new BlockPos(3, 2, 4))) == 8, "shaft behind wheel not turning");
		});
	}
}
