package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.assertSpeed;
import static dev.createpolymer.test.TestUtil.floor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.createpolymer.api.contraption.BlockMovementChecks;
import dev.createpolymer.contraptions.AssemblyException;
import dev.createpolymer.contraptions.ControlledContraptionEntity;
import dev.createpolymer.contraptions.OrientedContraptionEntity;
import dev.createpolymer.contraptions.bearing.BearingBlock;
import dev.createpolymer.contraptions.bearing.BearingContraption;
import dev.createpolymer.contraptions.bearing.ClockworkBearingBlockEntity;
import dev.createpolymer.contraptions.bearing.MechanicalBearingBlockEntity;
import dev.createpolymer.contraptions.bearing.SailBlock;
import dev.createpolymer.contraptions.bearing.WindmillBearingBlockEntity;
import dev.createpolymer.contraptions.chassis.AbstractChassisBlock;
import dev.createpolymer.contraptions.chassis.ChassisBlockEntity;
import dev.createpolymer.contraptions.chassis.LinearChassisBlock;
import dev.createpolymer.contraptions.chassis.RadialChassisBlock;
import dev.createpolymer.contraptions.chassis.StickerBlock;
import dev.createpolymer.contraptions.glue.SuperGlueEntity;
import dev.createpolymer.contraptions.piston.MechanicalPistonBlock;
import dev.createpolymer.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.createpolymer.kinetics.base.DirectionalAxisKineticBlock;
import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.base.RotatedPillarKineticBlock;
import dev.createpolymer.kinetics.motor.CreativeMotorBlockEntity;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.feature.BearingRegistry;
import dev.createpolymer.registry.feature.ContraptionRegistry;
import dev.createpolymer.util.AngleHelper;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class BearingGameTests {

	private static final BlockPos BEARING = new BlockPos(3, 1, 3);
	private static final BlockPos PISTON = new BlockPos(2, 3, 2);

	private static void motor(GameTestHelper helper, BlockPos pos, Direction facing) {
		helper.setBlock(pos, ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, facing));
	}

	private static void setSpeed(GameTestHelper helper, BlockPos motor, int rpm) {
		helper.getBlockEntity(motor, CreativeMotorBlockEntity.class).setSetting(rpm);
	}

	private static BlockState bearing(Direction facing) {
		return BearingRegistry.MECHANICAL_BEARING.defaultBlockState().setValue(BearingBlock.FACING, facing);
	}

	private static void glue(GameTestHelper helper, BlockPos a, BlockPos b) {
		helper.getLevel().addFreshEntity(new SuperGlueEntity(helper.getLevel(),
			SuperGlueEntity.span(helper.absolutePos(a), helper.absolutePos(b))));
	}

	/** Tests run side by side: only the contraption entities anchored inside this test's box count. */
	private static List<ControlledContraptionEntity> controlled(GameTestHelper helper) {
		AABB bounds = helper.getBounds().inflate(1);
		return helper.getLevel().getEntitiesOfClass(ControlledContraptionEntity.class, bounds.inflate(16),
			e -> bounds.contains(e.position()));
	}

	private static List<OrientedContraptionEntity> oriented(GameTestHelper helper) {
		AABB bounds = helper.getBounds().inflate(1);
		return helper.getLevel().getEntitiesOfClass(OrientedContraptionEntity.class, bounds.inflate(16),
			e -> bounds.contains(e.position()));
	}

	private static BlockHitResult hit(GameTestHelper helper, BlockPos pos, Direction face) {
		BlockPos abs = helper.absolutePos(pos);
		return new BlockHitResult(Vec3.atCenterOf(abs).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(.5)), face, abs, false);
	}

	// ---- mechanical bearing ----

	/**
	 * A bearing facing up with a three block arm (stone, stairs, stone) to its east, glued together. At 32 RPM the
	 * arm turns 9.6 degrees a tick; stopping the motor after 90 degrees puts the arm back one quarter turn on.
	 */
	@GameTest(maxTicks = 300, padding = 24)
	public void mechanicalBearingRotatesGluedArmAndPlacesItBackRotated(GameTestHelper helper) {
		floor(helper);
		BlockPos motor = BEARING.below();
		motor(helper, motor, Direction.UP);
		helper.setBlock(BEARING, bearing(Direction.UP));
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(4, 2, 3), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(5, 2, 3), Blocks.STONE);
		glue(helper, new BlockPos(3, 2, 3), new BlockPos(5, 2, 3));
		helper.runAfterDelay(2, () -> setSpeed(helper, motor, 32));

		AtomicBoolean stopped = new AtomicBoolean();
		helper.succeedWhen(() -> {
			if (!stopped.get()) {
				List<ControlledContraptionEntity> entities = controlled(helper);
				if (!entities.isEmpty() && Math.abs(entities.get(0).getAngle(1)) >= 90) {
					setSpeed(helper, motor, 0);
					stopped.set(true);
				}
				helper.fail("still turning, " + entities.size() + " contraptions");
			}
			helper.assertTrue(controlled(helper).isEmpty(), "contraption entity still exists");
			helper.assertTrue(!helper.getBlockEntity(BEARING, MechanicalBearingBlockEntity.class).isRunning(), "bearing still runs");
			// the arm now lies along Z: north when the bearing turned counter-clockwise, south otherwise
			boolean north = helper.getBlockState(new BlockPos(3, 2, 2)).is(Blocks.OAK_STAIRS);
			Direction side = north ? Direction.NORTH : Direction.SOUTH;
			BlockPos stairs = new BlockPos(3, 2, 3 + side.getStepZ());
			BlockPos far = new BlockPos(3, 2, 3 + 2 * side.getStepZ());
			helper.assertTrue(helper.getBlockState(new BlockPos(3, 2, 3)).is(Blocks.STONE), "no stone at the anchor");
			helper.assertTrue(helper.getBlockState(stairs).is(Blocks.OAK_STAIRS), "no stairs at " + stairs + ", column: " + arm(helper));
			helper.assertTrue(helper.getBlockState(far).is(Blocks.STONE), "no stone at " + far + ", column: " + arm(helper));
			helper.assertTrue(helper.getBlockState(stairs).getValue(StairBlock.FACING) == side,
				"stairs face " + helper.getBlockState(stairs).getValue(StairBlock.FACING) + " but the arm turned " + side);
			helper.assertTrue(helper.getBlockState(new BlockPos(4, 2, 3)).isAir(), "the arm left a block at x=4");
			helper.assertTrue(helper.getBlockState(new BlockPos(5, 2, 3)).isAir(), "the arm left a block at x=5");
			helper.assertTrue(helper.getBlockState(BEARING).getBlock() == BearingRegistry.MECHANICAL_BEARING, "bearing is gone");
		});
	}

	private static String arm(GameTestHelper helper) {
		StringBuilder sb = new StringBuilder();
		for (int x = 1; x <= 5; x++)
			for (int z = 1; z <= 5; z++)
				if (!helper.getBlockState(new BlockPos(x, 2, z)).isAir())
					sb.append(x).append(',').append(z).append('=').append(helper.getBlockState(new BlockPos(x, 2, z)).getBlock().getDescriptionId()).append(' ');
		return sb.toString();
	}

	/** "Rotate, place when back" keeps the contraption running until the bearing is close to its start again. */
	@GameTest(maxTicks = 400, padding = 24)
	public void returnedMovementModeKeepsRunningUntilBackAtStart(GameTestHelper helper) {
		floor(helper);
		BlockPos motor = BEARING.below();
		motor(helper, motor, Direction.UP);
		helper.setBlock(BEARING, bearing(Direction.UP));
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(4, 2, 3), Blocks.COBBLESTONE);
		glue(helper, new BlockPos(3, 2, 3), new BlockPos(4, 2, 3));
		helper.getBlockEntity(BEARING, MechanicalBearingBlockEntity.class).getBehaviour(ScrollValueBehaviour.TYPE)
			.setValue(dev.createpolymer.contraptions.IControlContraption.RotationMode.ROTATE_PLACE_RETURNED.ordinal());
		helper.runAfterDelay(2, () -> setSpeed(helper, motor, 32));

		int[] phase = {0};
		helper.succeedWhen(() -> {
			List<ControlledContraptionEntity> entities = controlled(helper);
			if (phase[0] == 0) {
				if (!entities.isEmpty() && Math.abs(entities.get(0).getAngle(1)) >= 90) {
					setSpeed(helper, motor, 0);
					phase[0] = 1;
				}
				helper.fail("turning away");
			}
			if (phase[0] == 1) {
				helper.assertTrue(helper.getBlockEntity(BEARING, MechanicalBearingBlockEntity.class).isRunning(), "the bearing stopped away from its start");
				helper.assertTrue(entities.size() == 1, "the contraption left");
				helper.assertTrue(helper.getBlockState(new BlockPos(4, 2, 3)).isAir(), "the arm was placed away from its start");
				setSpeed(helper, motor, -32);
				phase[0] = 2;
				helper.fail("turning back");
			}
			if (phase[0] == 2) {
				if (!entities.isEmpty() && Math.abs(entities.get(0).getAngle(1)) <= 10) {
					setSpeed(helper, motor, 0);
					phase[0] = 3;
				}
				helper.fail("turning back");
			}
			helper.assertTrue(entities.isEmpty(), "the contraption still exists");
			helper.assertTrue(helper.getBlockState(new BlockPos(3, 2, 3)).is(Blocks.STONE), "no stone at the anchor");
			helper.assertTrue(helper.getBlockState(new BlockPos(4, 2, 3)).is(Blocks.COBBLESTONE), "the arm is not back in place: " + arm(helper));
		});
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void mechanicalBearingWithoutPowerDoesNotAssemble(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(BEARING, bearing(Direction.UP));
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.STONE);
		helper.useBlock(BEARING);
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(controlled(helper).isEmpty(), "bearing assembled without rotation");
			helper.assertTrue(helper.getBlockState(new BlockPos(3, 2, 3)).is(Blocks.STONE), "stone moved");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void bearingContraptionSavesAndLoads(GameTestHelper helper) {
		floor(helper);
		motor(helper, BEARING.below(), Direction.UP);
		helper.setBlock(BEARING, bearing(Direction.UP));
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(3, 3, 3), Blocks.COBBLESTONE);
		glue(helper, new BlockPos(3, 2, 3), new BlockPos(3, 3, 3));
		helper.runAfterDelay(2, () -> setSpeed(helper, BEARING.below(), 16));
		helper.runAfterDelay(20, () -> {
			List<ControlledContraptionEntity> list = controlled(helper);
			helper.assertTrue(list.size() == 1, "expected one contraption, found " + list.size());
			ControlledContraptionEntity entity = list.get(0);
			helper.assertTrue(entity.getContraption() instanceof BearingContraption, "not a bearing contraption");
			helper.assertTrue(entity.getContraption().getBlocks().size() == 2, "blocks " + entity.getContraption().getBlocks().size());
			helper.assertTrue(entity.getRotationAxis() == Axis.Y, "axis " + entity.getRotationAxis());
			helper.assertTrue(entity.getContraption().getBearingFacing() == Direction.UP, "bearing facing");

			var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
				net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
			entity.saveWithoutId(out);
			ControlledContraptionEntity copy = new ControlledContraptionEntity(ContraptionRegistry.CONTROLLED_CONTRAPTION, helper.getLevel());
			copy.load(net.minecraft.world.level.storage.TagValueInput.create(
				net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), out.buildResult()));
			helper.assertTrue(copy.getContraption() instanceof BearingContraption bc && bc.getBlocks().size() == 2
				&& bc.getFacing() == Direction.UP, "loaded contraption: " + copy.getContraption());
			helper.assertTrue(copy.getRotationAxis() == Axis.Y, "loaded axis");
			helper.succeed();
		});
	}

	/** A bearing carrying a second bearing keeps the load of the second one unturned. */
	@GameTest(maxTicks = 300, padding = 24)
	public void bearingOnABearingStabilizesItsLoad(GameTestHelper helper) {
		floor(helper);
		BlockPos motor = BEARING.below();
		motor(helper, motor, Direction.UP);
		helper.setBlock(BEARING, bearing(Direction.UP));
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(4, 2, 3), bearing(Direction.UP));
		helper.setBlock(new BlockPos(4, 3, 3), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
		glue(helper, new BlockPos(3, 2, 3), new BlockPos(4, 2, 3));
		helper.runAfterDelay(2, () -> setSpeed(helper, motor, 32));

		AtomicBoolean stopped = new AtomicBoolean();
		AtomicBoolean seenSub = new AtomicBoolean();
		helper.succeedWhen(() -> {
			if (!stopped.get()) {
				List<ControlledContraptionEntity> entities = controlled(helper);
				if (!oriented(helper).isEmpty())
					seenSub.set(true);
				if (!entities.isEmpty() && Math.abs(entities.get(0).getAngle(1)) >= 90) {
					helper.assertTrue(seenSub.get(), "no stabilized sub contraption was created");
					setSpeed(helper, motor, 0);
					stopped.set(true);
				}
				helper.fail("still turning");
			}
			helper.assertTrue(controlled(helper).isEmpty() && oriented(helper).isEmpty(), "contraption entities still exist");
			boolean north = helper.getBlockState(new BlockPos(3, 2, 2)).getBlock() == BearingRegistry.MECHANICAL_BEARING;
			BlockPos sub = new BlockPos(3, 2, north ? 2 : 4);
			helper.assertTrue(helper.getBlockState(sub).getBlock() == BearingRegistry.MECHANICAL_BEARING, "no second bearing at " + sub + ": " + arm(helper));
			BlockState stairs = helper.getBlockState(sub.above());
			helper.assertTrue(stairs.is(Blocks.OAK_STAIRS), "no stairs above the second bearing: " + stairs);
			helper.assertTrue(stairs.getValue(StairBlock.FACING) == Direction.EAST, "the stabilized stairs turned to " + stairs.getValue(StairBlock.FACING));
		});
	}

	// ---- windmill bearing ----

	private static void sailPlate(GameTestHelper helper, int xMin, int xMax, int zMin, int zMax) {
		for (int x = xMin; x <= xMax; x++)
			for (int z = zMin; z <= zMax; z++)
				helper.setBlock(new BlockPos(x, 2, z), BearingRegistry.SAIL.defaultBlockState().setValue(SailBlock.FACING, Direction.UP));
	}

	private static void windmill(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(BEARING, BearingRegistry.WINDMILL_BEARING.defaultBlockState().setValue(BearingBlock.FACING, Direction.UP));
		helper.setBlock(BEARING.below(), ModBlocks.SHAFT.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.Y));
		helper.setBlock(BEARING.below(2), Blocks.STONE);
	}

	@GameTest(maxTicks = 300, padding = 24)
	public void windmillWithEightSailsGivesOneRpmAndFiveHundredTwelveStress(GameTestHelper helper) {
		windmill(helper);
		sailPlate(helper, 3, 6, 3, 4);
		helper.useBlock(BEARING);
		// checked after the kinetic network validated itself a few times
		helper.runAfterDelay(150, () -> {
			assertSpeed(helper, BEARING, 1);
			assertSpeed(helper, BEARING.below(), 1);
			float capacity = helper.getBlockEntity(BEARING.below(), KineticBlockEntity.class).getNetworkCapacity();
			helper.assertTrue(Math.abs(capacity - 512) < 1, "network capacity " + capacity);
			ControlledContraptionEntity entity = controlled(helper).get(0);
			helper.assertTrue(((BearingContraption) entity.getContraption()).getSailBlocks() == 8, "sails");
			// 1 RPM is 0.3 degrees a tick
			helper.assertTrue(Math.abs(entity.getAngle(1)) > 30, "the windmill barely turns: " + entity.getAngle(1));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void windmillPlacesItsSailsBackWhenStoppedByHand(GameTestHelper helper) {
		windmill(helper);
		sailPlate(helper, 3, 6, 3, 4);
		helper.useBlock(BEARING);
		AtomicBoolean stopped = new AtomicBoolean();
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(controlled(helper).size() == 1, "the windmill is not running");
			helper.assertTrue(helper.getBlockState(new BlockPos(4, 2, 3)).isAir(), "the sails are still in the world");
			helper.useBlock(BEARING);
			stopped.set(true);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(stopped.get(), "waiting");
			helper.assertTrue(controlled(helper).isEmpty(), "the contraption entity still exists");
			assertSpeed(helper, BEARING.below(), 0);
			for (int x = 3; x <= 6; x++)
				for (int z = 3; z <= 4; z++)
					helper.assertTrue(helper.getBlockState(new BlockPos(x, 2, z)).getBlock() == BearingRegistry.SAIL,
						"no sail at " + x + "," + z + ": " + arm(helper));
		});
	}

	/** The bearing and its contraption entity find each other again after the bearing's data was reloaded. */
	@GameTest(maxTicks = 100, padding = 24)
	public void bearingAndContraptionAttachAgainAfterReload(GameTestHelper helper) {
		floor(helper);
		motor(helper, BEARING.below(), Direction.UP);
		helper.setBlock(BEARING, bearing(Direction.UP));
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.STONE);
		helper.runAfterDelay(2, () -> setSpeed(helper, BEARING.below(), 16));
		AtomicBoolean reloaded = new AtomicBoolean();
		helper.runAfterDelay(20, () -> {
			var level = helper.getLevel();
			BlockPos abs = helper.absolutePos(BEARING);
			MechanicalBearingBlockEntity old = helper.getBlockEntity(BEARING, MechanicalBearingBlockEntity.class);
			helper.assertTrue(old.isRunning(), "not running before the reload");
			var tag = old.saveWithFullMetadata(level.registryAccess());
			level.removeBlockEntity(abs);
			var fresh = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(abs, level.getBlockState(abs), tag, level.registryAccess());
			helper.assertTrue(fresh instanceof MechanicalBearingBlockEntity, "could not load the bearing: " + fresh);
			level.setBlockEntity(fresh);
			reloaded.set(true);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded.get(), "waiting for the reload");
			MechanicalBearingBlockEntity bearing = helper.getBlockEntity(BEARING, MechanicalBearingBlockEntity.class);
			helper.assertTrue(bearing.isRunning(), "the new bearing does not run");
			List<ControlledContraptionEntity> list = controlled(helper);
			helper.assertTrue(list.size() == 1, "contraptions " + list.size());
			helper.assertTrue(bearing.isAttachedTo(list.get(0)), "the new bearing did not take its contraption");
		});
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void windmillWithSixteenSailsGivesTwoRpm(GameTestHelper helper) {
		windmill(helper);
		sailPlate(helper, 3, 6, 3, 6);
		helper.useBlock(BEARING);
		helper.succeedWhen(() -> {
			assertSpeed(helper, BEARING.below(), 2);
			float capacity = helper.getBlockEntity(BEARING.below(), KineticBlockEntity.class).getNetworkCapacity();
			helper.assertTrue(Math.abs(capacity - 1024) < 1, "network capacity " + capacity);
		});
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void windmillCounterClockwiseGivesNegativeSpeed(GameTestHelper helper) {
		windmill(helper);
		sailPlate(helper, 3, 6, 3, 4);
		helper.getBlockEntity(BEARING, WindmillBearingBlockEntity.class).getBehaviour(ScrollValueBehaviour.TYPE)
			.setValue(WindmillBearingBlockEntity.RotationDirection.COUNTER_CLOCKWISE.ordinal());
		helper.useBlock(BEARING);
		helper.succeedWhen(() -> assertSpeed(helper, BEARING.below(), -1));
	}

	@GameTest(maxTicks = 60, padding = 24)
	public void windmillNeedsEightSails(GameTestHelper helper) {
		windmill(helper);
		sailPlate(helper, 3, 5, 3, 5);
		helper.setBlock(new BlockPos(5, 2, 5), Blocks.AIR);
		helper.setBlock(new BlockPos(5, 2, 4), Blocks.AIR);
		// seven sails: a 3x3 plate without two corners
		helper.useBlock(BEARING);
		helper.runAfterDelay(10, () -> {
			WindmillBearingBlockEntity windmill = helper.getBlockEntity(BEARING, WindmillBearingBlockEntity.class);
			helper.assertTrue(windmill.getLastAssemblyException() != null, "no assembly exception");
			helper.assertTrue(controlled(helper).isEmpty(), "assembled with too few sails");
			helper.assertTrue(helper.getBlockState(new BlockPos(3, 2, 3)).getBlock() == BearingRegistry.SAIL, "sail moved");
			helper.succeed();
		});
	}

	// ---- clockwork bearing ----

	@GameTest(maxTicks = 300, padding = 24)
	public void clockworkBearingHandsFollowTheTimeOfDay(GameTestHelper helper) {
		floor(helper);
		BlockPos motor = BEARING.below();
		motor(helper, motor, Direction.UP);
		helper.setBlock(BEARING, BearingRegistry.CLOCKWORK_BEARING.defaultBlockState().setValue(BearingBlock.FACING, Direction.UP));
		// the hour hand is the first layer above the bearing, the minute hand the second one
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(4, 2, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(3, 3, 3), Blocks.COBBLESTONE);
		helper.setBlock(new BlockPos(3, 3, 4), Blocks.COBBLESTONE);
		glue(helper, new BlockPos(3, 2, 3), new BlockPos(4, 2, 3));
		glue(helper, new BlockPos(3, 3, 3), new BlockPos(3, 3, 4));
		helper.setTime(10500);
		helper.runAfterDelay(2, () -> setSpeed(helper, motor, 256));

		helper.succeedWhen(() -> {
			List<ControlledContraptionEntity> hands = controlled(helper);
			helper.assertTrue(hands.size() == 2, "expected two hands, found " + hands.size());
			ClockworkBearingBlockEntity clock = helper.getBlockEntity(BEARING, ClockworkBearingBlockEntity.class);
			long time = helper.getLevel().getDefaultClockTime() % 24000;
			int hours = (int) ((time / 1000 + 6) % 24);
			int minutes = (int) ((time % 1000) * 60 / 1000);
			float hourTarget = -360 / 12f * (hours % 12);
			float minuteTarget = -360 / 60f * minutes;
			float hourError = Math.abs(AngleHelper.getShortestAngleDiff(clock.getHourAngle(), hourTarget));
			float minuteError = Math.abs(AngleHelper.getShortestAngleDiff(clock.getMinuteAngle(), minuteTarget));
			helper.assertTrue(hourError < 1.5f, "hour hand at " + clock.getHourAngle() + ", the time " + time + " wants " + hourTarget);
			helper.assertTrue(minuteError < 7f, "minute hand at " + clock.getMinuteAngle() + ", the time " + time + " wants " + minuteTarget);
			// the hands are the entities' angles
			helper.assertTrue(hands.stream().anyMatch(e -> Math.abs(AngleHelper.getShortestAngleDiff(e.getAngle(1), clock.getHourAngle())) < 1.5f), "no entity at the hour angle");
		});
	}

	@GameTest(maxTicks = 300, padding = 24)
	public void clockworkBearingReturnsItsHandsWhenStopped(GameTestHelper helper) {
		floor(helper);
		BlockPos motor = BEARING.below();
		motor(helper, motor, Direction.UP);
		helper.setBlock(BEARING, BearingRegistry.CLOCKWORK_BEARING.defaultBlockState().setValue(BearingBlock.FACING, Direction.UP));
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(3, 3, 3), Blocks.COBBLESTONE);
		helper.setTime(6000);
		helper.runAfterDelay(2, () -> setSpeed(helper, motor, 256));
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(!controlled(helper).isEmpty(), "the hands are not running");
			setSpeed(helper, motor, 0);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(controlled(helper).isEmpty(), "hands still exist");
			helper.assertTrue(helper.getBlockState(new BlockPos(3, 2, 3)).is(Blocks.STONE), "the hour hand is not back");
			helper.assertTrue(helper.getBlockState(new BlockPos(3, 3, 3)).is(Blocks.COBBLESTONE), "the minute hand is not back");
		});
	}

	// ---- chassis ----

	private static void pistonBase(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(2, 1, 2), ContraptionRegistry.PISTON_EXTENSION_POLE.defaultBlockState().setValue(MechanicalPistonBlock.FACING, Direction.UP));
		helper.setBlock(new BlockPos(2, 2, 2), ContraptionRegistry.PISTON_EXTENSION_POLE.defaultBlockState().setValue(MechanicalPistonBlock.FACING, Direction.UP));
		helper.setBlock(PISTON, ContraptionRegistry.MECHANICAL_PISTON.defaultBlockState()
			.setValue(MechanicalPistonBlock.FACING, Direction.UP)
			.setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, false));
		BlockPos motor = PISTON.north();
		helper.setBlock(motor, ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.SOUTH));
		helper.runAfterDelay(2, () -> setSpeed(helper, motor, -128));
	}

	/** A chassis lying on its side in front of a piston drags the blocks on its sticky end up with it. */
	@GameTest(maxTicks = 300, padding = 24)
	public void linearChassisMovesItsStickyColumnWithAPiston(GameTestHelper helper) {
		pistonBase(helper);
		helper.setBlock(new BlockPos(2, 4, 2), BearingRegistry.LINEAR_CHASSIS.defaultBlockState()
			.setValue(AbstractChassisBlock.AXIS, Axis.X)
			.setValue(LinearChassisBlock.STICKY_BOTTOM, true));
		helper.setBlock(new BlockPos(1, 4, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(0, 4, 2), Blocks.COBBLESTONE);
		helper.setBlock(new BlockPos(3, 4, 2), Blocks.DIRT);
		// the default range of 8 would reach the barrier around the test
		helper.getBlockEntity(new BlockPos(2, 4, 2), ChassisBlockEntity.class).getBehaviour(ScrollValueBehaviour.TYPE).setValue(2);
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(PISTON).getValue(MechanicalPistonBlock.STATE) == MechanicalPistonBlock.PistonState.EXTENDED,
				"piston is not extended: " + helper.getBlockEntity(PISTON, dev.createpolymer.contraptions.piston.MechanicalPistonBlockEntity.class).getLastAssemblyException());
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 6, 2)).getBlock() == BearingRegistry.LINEAR_CHASSIS, "chassis is not at y=6");
			helper.assertTrue(helper.getBlockState(new BlockPos(1, 6, 2)).is(Blocks.STONE), "stone is not at (1,6,2)");
			helper.assertTrue(helper.getBlockState(new BlockPos(0, 6, 2)).is(Blocks.COBBLESTONE), "cobblestone is not at (0,6,2)");
			helper.assertTrue(helper.getBlockState(new BlockPos(3, 4, 2)).is(Blocks.DIRT), "the block at the plain end moved");
			helper.assertTrue(helper.getBlockState(new BlockPos(1, 4, 2)).isAir(), "left a block at (1,4,2)");
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 6, 2)).getValue(LinearChassisBlock.STICKY_BOTTOM), "the chassis lost its sticky side");
			helper.assertTrue(controlled(helper).isEmpty(), "contraption entity still exists");
		});
	}

	/** Without a sticky side the same chassis only brings what lies in the direction of the push. */
	@GameTest(maxTicks = 300, padding = 24)
	public void plainLinearChassisLeavesTheSidesAlone(GameTestHelper helper) {
		pistonBase(helper);
		helper.setBlock(new BlockPos(2, 4, 2), BearingRegistry.LINEAR_CHASSIS.defaultBlockState().setValue(AbstractChassisBlock.AXIS, Axis.X));
		helper.setBlock(new BlockPos(1, 4, 2), Blocks.STONE);
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 6, 2)).getBlock() == BearingRegistry.LINEAR_CHASSIS, "chassis is not at y=6");
			helper.assertTrue(helper.getBlockState(new BlockPos(1, 4, 2)).is(Blocks.STONE), "the block beside the chassis moved");
			helper.assertTrue(helper.getBlockState(new BlockPos(1, 6, 2)).isAir(), "the block beside the chassis came along");
		});
	}

	@GameTest(maxTicks = 40, padding = 24)
	public void radialChassisIncludesBlocksWithinItsRadius(GameTestHelper helper) {
		floor(helper);
		BlockPos chassis = new BlockPos(2, 1, 3);
		helper.setBlock(chassis, BearingRegistry.RADIAL_CHASSIS.defaultBlockState()
			.setValue(AbstractChassisBlock.AXIS, Axis.Y).setValue(RadialChassisBlock.STICKY_EAST, true));
		helper.setBlock(new BlockPos(3, 1, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(4, 1, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(5, 1, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(3, 1, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(3, 1, 5), Blocks.STONE);
		helper.runAfterDelay(2, () -> {
			ChassisBlockEntity be = helper.getBlockEntity(chassis, ChassisBlockEntity.class);
			helper.assertTrue(be.getRange() == ContraptionRegistry.maxChassisRange / 2, "default range " + be.getRange());
			be.getBehaviour(ScrollValueBehaviour.TYPE).setValue(2);
			List<BlockPos> included = new ArrayList<>(be.getIncludedBlockPositions(null, false));
			helper.assertTrue(included.contains(helper.absolutePos(new BlockPos(3, 1, 3))), "first block missing");
			helper.assertTrue(included.contains(helper.absolutePos(new BlockPos(4, 1, 3))), "second block missing");
			helper.assertTrue(included.contains(helper.absolutePos(new BlockPos(3, 1, 2))), "the block next to the first one is missing");
			helper.assertTrue(!included.contains(helper.absolutePos(new BlockPos(5, 1, 3))), "a block out of the radius is included");
			helper.assertTrue(!included.contains(helper.absolutePos(new BlockPos(3, 1, 5))), "a far block is included");
			be.getBehaviour(ScrollValueBehaviour.TYPE).setValue(1);
			included = new ArrayList<>(be.getIncludedBlockPositions(null, false));
			helper.assertTrue(!included.contains(helper.absolutePos(new BlockPos(4, 1, 3))), "range 1 still includes the second block");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40, padding = 24)
	public void slimeBallsGlueChassisEndsAndSneakingRemovesThem(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, BearingRegistry.LINEAR_CHASSIS.defaultBlockState().setValue(AbstractChassisBlock.AXIS, Axis.Y));
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SLIME_BALL, 4));
		helper.useBlock(pos, player, hit(helper, pos, Direction.UP));
		helper.assertTrue(helper.getBlockState(pos).getValue(LinearChassisBlock.STICKY_TOP), "the top is not sticky");
		helper.assertTrue(!helper.getBlockState(pos).getValue(LinearChassisBlock.STICKY_BOTTOM), "the bottom became sticky");
		// the side of a pillar has nothing to glue
		helper.useBlock(pos, player, hit(helper, pos, Direction.NORTH));
		helper.assertTrue(!helper.getBlockState(pos).getValue(LinearChassisBlock.STICKY_BOTTOM), "a side became sticky");
		// clicking a sticky end with slime makes the other ends sticky as well
		helper.useBlock(pos, player, hit(helper, pos, Direction.UP));
		helper.assertTrue(helper.getBlockState(pos).getValue(LinearChassisBlock.STICKY_BOTTOM), "the bottom did not follow");

		Player sneaker = helper.makeMockPlayer(GameType.SURVIVAL);
		sneaker.setShiftKeyDown(true);
		helper.useBlock(pos, sneaker, hit(helper, pos, Direction.UP));
		helper.assertTrue(!helper.getBlockState(pos).getValue(LinearChassisBlock.STICKY_TOP), "sneaking did not remove the slime");
		helper.assertTrue(helper.getBlockState(pos).getValue(LinearChassisBlock.STICKY_BOTTOM), "sneaking removed the wrong side");
		helper.succeed();
	}

	@GameTest(maxTicks = 40, padding = 24)
	public void chassisHasTexturedClientStates(GameTestHelper helper) {
		helper.assertTrue(BearingRegistry.LINEAR_CHASSIS.isTextured(), "linear chassis has no textured states");
		helper.assertTrue(BearingRegistry.SECONDARY_LINEAR_CHASSIS.isTextured(), "secondary linear chassis has no textured states");
		helper.assertTrue(BearingRegistry.RADIAL_CHASSIS.isTextured(), "radial chassis has no textured states");
		BlockState a = BearingRegistry.LINEAR_CHASSIS.getPolymerBlockState(BearingRegistry.LINEAR_CHASSIS.defaultBlockState(), null);
		BlockState b = BearingRegistry.LINEAR_CHASSIS.getPolymerBlockState(BearingRegistry.LINEAR_CHASSIS.defaultBlockState()
			.setValue(LinearChassisBlock.STICKY_TOP, true), null);
		helper.assertTrue(a != b, "sticky and plain chassis look the same");
		helper.succeed();
	}

	@GameTest(maxTicks = 40, padding = 24)
	public void transformingAChassisTurnsItsStickySides(GameTestHelper helper) {
		BlockState chassis = BearingRegistry.LINEAR_CHASSIS.defaultBlockState()
			.setValue(AbstractChassisBlock.AXIS, Axis.Y).setValue(LinearChassisBlock.STICKY_TOP, true);
		var transform = new dev.createpolymer.contraptions.StructureTransform(BlockPos.ZERO, 0, 0, 90);
		BlockState turned = BearingRegistry.LINEAR_CHASSIS.transform(chassis, transform);
		helper.assertTrue(turned.getValue(AbstractChassisBlock.AXIS) != Axis.Y, "axis " + turned.getValue(AbstractChassisBlock.AXIS));
		Direction up = transform.rotateFacing(Direction.UP);
		helper.assertTrue(turned.getValue(BearingRegistry.LINEAR_CHASSIS.getGlueableSide(turned, up)), "the sticky side did not turn to " + up);
		helper.assertTrue(!turned.getValue(BearingRegistry.LINEAR_CHASSIS.getGlueableSide(turned, up.getOpposite())), "the other end became sticky");
		helper.succeed();
	}

	// ---- sticker ----

	@GameTest(maxTicks = 300, padding = 24)
	public void extendedStickerPullsTheBlockInFront(GameTestHelper helper) {
		pistonBase(helper);
		helper.setBlock(new BlockPos(2, 4, 2), BearingRegistry.STICKER.defaultBlockState()
			.setValue(StickerBlock.FACING, Direction.EAST).setValue(StickerBlock.EXTENDED, true));
		helper.setBlock(new BlockPos(3, 4, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(1, 4, 2), Blocks.DIRT);
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 6, 2)).getBlock() == BearingRegistry.STICKER, "sticker is not at y=6");
			helper.assertTrue(helper.getBlockState(new BlockPos(3, 6, 2)).is(Blocks.STONE), "the stone in front of the sticker did not come along");
			helper.assertTrue(helper.getBlockState(new BlockPos(1, 4, 2)).is(Blocks.DIRT), "the block behind the sticker moved");
			helper.assertTrue(helper.getBlockState(new BlockPos(3, 4, 2)).isAir(), "left the stone behind");
		});
	}

	@GameTest(maxTicks = 300, padding = 24)
	public void retractedStickerLeavesTheBlockInFront(GameTestHelper helper) {
		pistonBase(helper);
		helper.setBlock(new BlockPos(2, 4, 2), BearingRegistry.STICKER.defaultBlockState().setValue(StickerBlock.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(3, 4, 2), Blocks.STONE);
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 6, 2)).getBlock() == BearingRegistry.STICKER, "sticker is not at y=6");
			helper.assertTrue(helper.getBlockState(new BlockPos(3, 4, 2)).is(Blocks.STONE), "the stone moved with a retracted sticker");
		});
	}

	@GameTest(maxTicks = 60, padding = 24)
	public void stickerExtendsAndRetractsOnEveryOtherPulse(GameTestHelper helper) {
		floor(helper);
		BlockPos sticker = new BlockPos(2, 1, 2);
		helper.setBlock(sticker, BearingRegistry.STICKER.defaultBlockState().setValue(StickerBlock.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(3, 1, 2), Blocks.STONE);
		BlockPos power = new BlockPos(1, 1, 2);
		helper.setBlock(power, Blocks.REDSTONE_BLOCK);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(helper.getBlockState(sticker).getValue(StickerBlock.POWERED), "not powered");
			helper.assertTrue(helper.getBlockState(sticker).getValue(StickerBlock.EXTENDED), "not extended");
			helper.setBlock(power, Blocks.AIR);
		});
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(!helper.getBlockState(sticker).getValue(StickerBlock.POWERED), "still powered");
			helper.assertTrue(helper.getBlockState(sticker).getValue(StickerBlock.EXTENDED), "retracted without a pulse");
			helper.setBlock(power, Blocks.REDSTONE_BLOCK);
		});
		helper.runAfterDelay(9, () -> {
			helper.assertTrue(helper.getBlockState(sticker).getValue(StickerBlock.POWERED), "not powered again");
			helper.assertTrue(!helper.getBlockState(sticker).getValue(StickerBlock.EXTENDED), "did not retract");
			helper.succeed();
		});
	}

	// ---- sails ----

	@GameTest(maxTicks = 40, padding = 24)
	public void sailsAreDyedAndSheared(GameTestHelper helper) {
		floor(helper);
		BlockPos a = new BlockPos(2, 1, 2);
		BlockPos b = new BlockPos(3, 1, 2);
		BlockState white = BearingRegistry.SAIL.defaultBlockState().setValue(SailBlock.FACING, Direction.NORTH);
		helper.setBlock(a, white);
		helper.setBlock(b, white);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DYE.pick(DyeColor.RED), 8));
		helper.useBlock(a, player, hit(helper, a, Direction.EAST));
		helper.assertTrue(helper.getBlockState(a).getBlock() == BearingRegistry.DYED_SAILS.get(DyeColor.RED), "the clicked sail is not red");
		helper.assertTrue(helper.getBlockState(a).getValue(SailBlock.FACING) == Direction.NORTH, "the facing was lost");
		helper.assertTrue(helper.getBlockState(b).getBlock() == BearingRegistry.SAIL, "the neighbour was dyed too early");
		// a second click spreads the colour to the neighbour
		helper.useBlock(a, player, hit(helper, a, Direction.EAST));
		helper.assertTrue(helper.getBlockState(b).getBlock() == BearingRegistry.DYED_SAILS.get(DyeColor.RED), "the neighbour is not red");
		// shears take the canvas off
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
		helper.useBlock(a, player, hit(helper, a, Direction.EAST));
		helper.assertTrue(helper.getBlockState(a).getBlock() == BearingRegistry.SAIL_FRAME, "the sail was not sheared");
		// the frame ignores dye
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DYE.pick(DyeColor.BLUE), 8));
		helper.useBlock(a, player, hit(helper, a, Direction.EAST));
		helper.assertTrue(helper.getBlockState(a).getBlock() == BearingRegistry.SAIL_FRAME, "the frame was dyed");
		helper.succeed();
	}

	@GameTest(maxTicks = 40, padding = 24)
	public void sailsPlaceNextToEachOtherAndDyedSailsShareTheWhiteItem(GameTestHelper helper) {
		floor(helper);
		BlockPos a = new BlockPos(2, 1, 2);
		helper.setBlock(a, BearingRegistry.SAIL.defaultBlockState().setValue(SailBlock.FACING, Direction.UP));
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BearingRegistry.SAIL_ITEM, 2));
		helper.useBlock(a, player, new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(a)).add(.45, .1, 0), Direction.UP, helper.absolutePos(a), false));
		helper.assertTrue(helper.getBlockState(new BlockPos(3, 1, 2)).getBlock() == BearingRegistry.SAIL, "no sail was placed towards the click");
		helper.assertTrue(helper.getBlockState(new BlockPos(3, 1, 2)).getValue(SailBlock.FACING) == Direction.UP, "placed sail faces " + helper.getBlockState(new BlockPos(3, 1, 2)).getValue(SailBlock.FACING));
		helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1, "the item was not used");
		helper.assertTrue(Item.byBlock(BearingRegistry.SAIL_FRAME) == BearingRegistry.SAIL_FRAME_ITEM, "the frame has no item");
		helper.assertTrue(Item.byBlock(BearingRegistry.RADIAL_CHASSIS) == BearingRegistry.RADIAL_CHASSIS_ITEM, "the chassis has no item");
		for (SailBlock sail : BearingRegistry.DYED_SAILS.values())
			helper.assertTrue(Item.byBlock(sail) == BearingRegistry.SAIL_ITEM, sail.getDescriptionId() + " does not give the white sail");
		helper.succeed();
	}

	@GameTest(maxTicks = 40, padding = 24)
	public void sailsAreBrittleOnlyAcrossTheirPlate(GameTestHelper helper) {
		floor(helper);
		BlockPos a = new BlockPos(2, 1, 2);
		BlockState sail = BearingRegistry.SAIL.defaultBlockState().setValue(SailBlock.FACING, Direction.UP);
		helper.setBlock(a, sail);
		var level = helper.getLevel();
		BlockPos abs = helper.absolutePos(a);
		helper.assertTrue(BlockMovementChecks.isBlockAttachedTowards(sail, level, abs, Direction.EAST), "sails do not hold sideways");
		helper.assertTrue(!BlockMovementChecks.isBlockAttachedTowards(sail, level, abs, Direction.UP), "sails hold along their axis");
		helper.assertTrue(BlockMovementChecks.isNotSupportive(sail, Direction.UP), "sails support along their axis");
		helper.assertTrue(!BlockMovementChecks.isNotSupportive(sail, Direction.EAST), "sails do not support sideways");
		helper.succeed();
	}

	// ---- misc ----

	@GameTest(maxTicks = 40, padding = 24)
	public void bearingsKeepStillWhileAssemblyFailsOnUnmovableBlocks(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(BEARING.below(), ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
		helper.setBlock(BEARING, bearing(Direction.UP));
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.BEDROCK);
		helper.runAfterDelay(2, () -> {
			try {
				new BearingContraption(false, Direction.UP).assemble(helper.getLevel(), helper.absolutePos(BEARING));
				helper.fail("a bearing assembled around bedrock");
			} catch (AssemblyException e) {
				helper.assertTrue(e.hasPosition() && e.getPosition().equals(helper.absolutePos(new BlockPos(3, 2, 3))), "exception points at " + e.getPosition());
				helper.succeed();
			}
		});
	}

	@GameTest(maxTicks = 40, padding = 24)
	public void runningBearingCannotBeMovedItself(GameTestHelper helper) {
		floor(helper);
		motor(helper, BEARING.below(), Direction.UP);
		helper.setBlock(BEARING, bearing(Direction.UP));
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.STONE);
		helper.runAfterDelay(2, () -> setSpeed(helper, BEARING.below(), 32));
		helper.runAfterDelay(15, () -> {
			helper.assertTrue(!controlled(helper).isEmpty(), "the bearing is not running");
			helper.assertTrue(!BlockMovementChecks.isMovementAllowed(helper.getBlockState(BEARING), helper.getLevel(), helper.absolutePos(BEARING)),
				"a running bearing can be moved");
			helper.succeed();
		});
	}
}
