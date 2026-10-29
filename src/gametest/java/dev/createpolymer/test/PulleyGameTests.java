package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import java.util.List;

import dev.createpolymer.contraptions.AbstractContraptionEntity;
import dev.createpolymer.contraptions.Contraption;
import dev.createpolymer.contraptions.ControlledContraptionEntity;
import dev.createpolymer.contraptions.elevator.ElevatorColumn;
import dev.createpolymer.contraptions.elevator.ElevatorColumn.ColumnCoords;
import dev.createpolymer.contraptions.elevator.ElevatorContactBlock;
import dev.createpolymer.contraptions.elevator.ElevatorContactBlockEntity;
import dev.createpolymer.contraptions.elevator.ElevatorContraption;
import dev.createpolymer.contraptions.elevator.ElevatorPulleyBlockEntity;
import dev.createpolymer.contraptions.gantry.GantryCarriageBlock;
import dev.createpolymer.contraptions.gantry.GantryContraptionEntity;
import dev.createpolymer.contraptions.glue.SuperGlueEntity;
import dev.createpolymer.contraptions.pulley.PulleyBlock;
import dev.createpolymer.contraptions.pulley.PulleyBlockEntity;
import dev.createpolymer.contraptions.pulley.PulleyContraption;
import dev.createpolymer.kinetics.base.DirectionalAxisKineticBlock;
import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.base.HorizontalAxisKineticBlock;
import dev.createpolymer.kinetics.base.HorizontalKineticBlock;
import dev.createpolymer.kinetics.gantry.GantryShaftBlock;
import dev.createpolymer.kinetics.gantry.GantryShaftBlock.Part;
import dev.createpolymer.kinetics.motor.CreativeMotorBlockEntity;
import dev.createpolymer.redstone.contact.RedstoneContactBlock;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.feature.PulleyRegistry;
import dev.createpolymer.registry.feature.RedstoneRegistry;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class PulleyGameTests {

	private static final BlockPos PULLEY = new BlockPos(2, 5, 2);

	private static void motor(GameTestHelper helper, BlockPos pos, Direction facing, int rpm) {
		helper.setBlock(pos, ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, facing));
		helper.runAfterDelay(2, () -> helper.getBlockEntity(pos, CreativeMotorBlockEntity.class).setSetting(rpm));
	}

	private static void setMotor(GameTestHelper helper, BlockPos pos, int rpm) {
		helper.getBlockEntity(pos, CreativeMotorBlockEntity.class).setSetting(rpm);
	}

	private static boolean is(GameTestHelper helper, BlockPos pos, net.minecraft.world.level.block.Block block) {
		return helper.getBlockState(pos).is(block);
	}

	private static <T extends AbstractContraptionEntity> List<T> entities(GameTestHelper helper, Class<T> type) {
		return helper.getLevel().getEntitiesOfClass(type, helper.getBounds());
	}

	// ---- rope pulley ----

	/** A pulley at y=5 with two glued stone blocks under it at y=4. The floor is at y=0, so the platform lands on y=1. */
	private static void ropePulleySetup(GameTestHelper helper, int rpm) {
		floor(helper);
		helper.setBlock(PULLEY, PulleyRegistry.ROPE_PULLEY.defaultBlockState().setValue(HorizontalAxisKineticBlock.HORIZONTAL_AXIS, Axis.Z));
		helper.setBlock(new BlockPos(2, 4, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(2, 4, 3), Blocks.STONE);
		BlockPos a = helper.absolutePos(new BlockPos(2, 4, 2));
		BlockPos b = helper.absolutePos(new BlockPos(2, 4, 3));
		helper.getLevel().addFreshEntity(new SuperGlueEntity(helper.getLevel(), SuperGlueEntity.span(a, b)));
		motor(helper, PULLEY.north(), Direction.SOUTH, rpm);
	}

	@GameTest(maxTicks = 400)
	public void ropePulleyLowersAGluedPlatformThreeBlocksAndRaisesItBack(GameTestHelper helper) {
		long start = helper.getLevel().getGameTime();
		ropePulleySetup(helper, 128);

		// lowered: the platform sits on the floor, a magnet on top of it and rope up to the pulley
		helper.runAfterDelay(100, () -> {
			helper.assertTrue(is(helper, new BlockPos(2, 1, 2), Blocks.STONE), "stone is not at y=1");
			helper.assertTrue(is(helper, new BlockPos(2, 1, 3), Blocks.STONE), "glued stone is not at y=1");
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 4, 2)).is(PulleyRegistry.ROPE), "no rope at y=4: " + helper.getBlockState(new BlockPos(2, 4, 2)));
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 3, 2)).is(PulleyRegistry.ROPE), "no rope at y=3");
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 2, 2)).is(PulleyRegistry.PULLEY_MAGNET), "no magnet at y=2");
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 4, 3)).isAir(), "glued stone was left behind at y=4");
			helper.assertTrue(entities(helper, ControlledContraptionEntity.class).isEmpty(), "contraption entity still exists");
			PulleyBlockEntity pulley = helper.getBlockEntity(PULLEY, PulleyBlockEntity.class);
			helper.assertTrue(!pulley.running, "pulley still running");
			setMotor(helper, PULLEY.north(), -128);
		});

		// raised again: the rope is gone and the platform is back
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getLevel().getGameTime() - start > 105, "waiting for the lowering");
			helper.assertTrue(is(helper, new BlockPos(2, 4, 2), Blocks.STONE), "stone is not back at y=4");
			helper.assertTrue(is(helper, new BlockPos(2, 4, 3), Blocks.STONE), "glued stone is not back at y=4");
			for (int y = 1; y <= 3; y++)
				helper.assertTrue(helper.getBlockState(new BlockPos(2, y, 2)).isAir(), "left something at y=" + y + ": " + helper.getBlockState(new BlockPos(2, y, 2)));
			helper.assertTrue(entities(helper, ControlledContraptionEntity.class).isEmpty(), "contraption entity still exists");
		});
	}

	@GameTest(maxTicks = 100)
	public void ropePulleyContraptionSavesAndLoadsAndItsModelShowsTheRope(GameTestHelper helper) {
		ropePulleySetup(helper, 32);
		helper.runAfterDelay(30, () -> {
			List<ControlledContraptionEntity> list = entities(helper, ControlledContraptionEntity.class);
			helper.assertTrue(list.size() == 1, "expected one contraption entity, found " + list.size());
			ControlledContraptionEntity entity = list.get(0);
			helper.assertTrue(entity.getContraption() instanceof PulleyContraption, "not a pulley contraption");
			helper.assertTrue(entity.getContraption().getBlocks().size() == 2, "blocks " + entity.getContraption().getBlocks().size());

			TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
			entity.saveWithoutId(out);
			CompoundTag tag = out.buildResult();
			ControlledContraptionEntity copy = new ControlledContraptionEntity(dev.createpolymer.registry.feature.ContraptionRegistry.CONTROLLED_CONTRAPTION, helper.getLevel());
			copy.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
			helper.assertTrue(copy.getContraption() instanceof PulleyContraption p && p.getInitialOffset() == 0,
				"loaded contraption " + copy.getContraption());

			// the pulley shows a magnet and some rope under it
			PulleyBlockEntity pulley = helper.getBlockEntity(PULLEY, PulleyBlockEntity.class);
			float offset = pulley.getDisplayOffset();
			helper.assertTrue(offset > 1 && offset < 3.5, "display offset " + offset);
			BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(PULLEY));
			helper.assertTrue(attachment != null, "no model");
			int shown = 0;
			for (VirtualElement element : attachment.holder().getElements())
				if (element instanceof ItemDisplayElement display && !display.getItem().isEmpty())
					shown++;
			// casing, shaft, coil, magnet, the half rope in the pulley's block and at least the stretched rope piece
			helper.assertTrue(shown >= 6, "shown parts " + shown);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void itemOnTheLoweredPlatformIsCarriedDown(GameTestHelper helper) {
		ropePulleySetup(helper, 64);
		// the platform's top is at y=5; the pulley takes the first block's place above it
		helper.runAfterDelay(1, () -> helper.spawnItem(Items.ECHO_SHARD, 2.5f, 5.1f, 3.5f).setDeltaMovement(Vec3.ZERO));
		helper.succeedWhen(() -> {
			List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(new BlockPos(2, 0, 3))).inflate(2, 10, 2),
				e -> e.getItem().is(Items.ECHO_SHARD));
			helper.assertTrue(items.size() == 1, "item entities: " + items.size());
			double y = items.get(0).getY() - helper.absolutePos(BlockPos.ZERO).getY();
			helper.assertTrue(y < 2.3 && y > 1.9, "item is at y=" + y + ", it should rest on the platform at y=2");
			helper.assertTrue(is(helper, new BlockPos(2, 1, 2), Blocks.STONE), "platform has not landed yet");
		});
	}

	@GameTest(maxTicks = 200)
	public void mobOnTheLoweredPlatformIsCarriedDown(GameTestHelper helper) {
		ropePulleySetup(helper, 64);
		// without free will it stays where it is, but it still falls: a mob without AI would hang in the air
		Pig pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(2, 5, 3));
		helper.succeedWhen(() -> {
			helper.assertTrue(pig.isAlive(), "the pig died");
			double y = pig.getY() - helper.absolutePos(BlockPos.ZERO).getY();
			helper.assertTrue(y < 2.2 && y > 1.8, "pig is at y=" + y + ", it should stand on the platform at y=2");
			helper.assertTrue(is(helper, new BlockPos(2, 1, 3), Blocks.STONE), "platform has not landed yet");
		});
	}

	@GameTest(maxTicks = 40)
	public void ropeBreaksWithItsPulleyAndTheOtherWayAround(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(PULLEY, PulleyRegistry.ROPE_PULLEY.defaultBlockState());
		helper.setBlock(PULLEY.below(), PulleyRegistry.ROPE.defaultBlockState());
		helper.setBlock(PULLEY.below(2), PulleyRegistry.ROPE.defaultBlockState());
		helper.setBlock(PULLEY.below(3), PulleyRegistry.PULLEY_MAGNET.defaultBlockState());
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(helper.getBlockState(PULLEY.below(3)).is(PulleyRegistry.PULLEY_MAGNET), "no magnet");
			// breaking the pulley takes the whole rope with it
			helper.getLevel().destroyBlock(helper.absolutePos(PULLEY), false);
		});
		helper.runAfterDelay(6, () -> {
			for (int i = 1; i <= 3; i++)
				helper.assertTrue(helper.getBlockState(PULLEY.below(i)).isAir(), "rope left at " + i + ": " + helper.getBlockState(PULLEY.below(i)));
			helper.succeed();
		});
	}

	// ---- gantry ----

	private static final BlockPos CARRIAGE = new BlockPos(2, 3, 3);

	@GameTest(maxTicks = 300)
	public void gantryCarriageMovesAlongAFourBlockShaftWhenItRotates(GameTestHelper helper) {
		long start = helper.getLevel().getGameTime();
		floor(helper);
		Part[] parts = {Part.START, Part.MIDDLE, Part.MIDDLE, Part.END};
		for (int i = 0; i < 4; i++)
			helper.setBlock(new BlockPos(1 + i, 2, 3), PulleyRegistry.GANTRY_SHAFT.defaultBlockState()
				.setValue(GantryShaftBlock.FACING, Direction.EAST)
				.setValue(GantryShaftBlock.PART, parts[i]));
		helper.setBlock(CARRIAGE, PulleyRegistry.GANTRY_CARRIAGE.defaultBlockState()
			.setValue(GantryCarriageBlock.FACING, Direction.UP)
			.setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, false));
		helper.setBlock(CARRIAGE.above(), Blocks.STONE);
		motor(helper, new BlockPos(0, 2, 3), Direction.EAST, -32);

		helper.runAfterDelay(15, () -> {
			List<GantryContraptionEntity> list = entities(helper, GantryContraptionEntity.class);
			helper.assertTrue(list.size() == 1, "expected one gantry contraption, found " + list.size());
			helper.assertTrue(list.get(0).getContraption().getBlocks().size() == 2, "blocks " + list.get(0).getContraption().getBlocks().size());
			helper.assertTrue(helper.getBlockState(CARRIAGE).isAir(), "carriage was not picked up");
		});

		// it ends on the last block of the shaft, with the stone on top of it
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getLevel().getGameTime() - start > 20, "waiting");
			helper.assertTrue(helper.getBlockState(new BlockPos(4, 3, 3)).getBlock() instanceof GantryCarriageBlock, "no carriage at x=4: " + helper.getBlockState(new BlockPos(4, 3, 3)));
			helper.assertTrue(is(helper, new BlockPos(4, 4, 3), Blocks.STONE), "stone did not come along");
			helper.assertTrue(helper.getBlockState(CARRIAGE).isAir() && helper.getBlockState(CARRIAGE.above()).isAir(), "left something behind");
			helper.assertTrue(entities(helper, GantryContraptionEntity.class).isEmpty(), "contraption entity still exists");
		});
	}

	@GameTest(maxTicks = 200)
	public void gantryCarriageTurnsBackWhenTheShaftReverses(GameTestHelper helper) {
		long start = helper.getLevel().getGameTime();
		floor(helper);
		Part[] parts = {Part.START, Part.MIDDLE, Part.MIDDLE, Part.END};
		for (int i = 0; i < 4; i++)
			helper.setBlock(new BlockPos(1 + i, 2, 3), PulleyRegistry.GANTRY_SHAFT.defaultBlockState()
				.setValue(GantryShaftBlock.FACING, Direction.EAST)
				.setValue(GantryShaftBlock.PART, parts[i]));
		helper.setBlock(new BlockPos(3, 3, 3), PulleyRegistry.GANTRY_CARRIAGE.defaultBlockState()
			.setValue(GantryCarriageBlock.FACING, Direction.UP)
			.setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, false));
		motor(helper, new BlockPos(0, 2, 3), Direction.EAST, 128);
		// the other way: the carriage runs to the start of the shaft
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getLevel().getGameTime() - start > 20, "waiting");
			helper.assertTrue(helper.getBlockState(new BlockPos(1, 3, 3)).getBlock() instanceof GantryCarriageBlock, "no carriage at x=1: " + helper.getBlockState(new BlockPos(1, 3, 3)));
			helper.assertTrue(entities(helper, GantryContraptionEntity.class).isEmpty(), "contraption entity still exists");
		});
	}

	@GameTest(maxTicks = 60)
	public void poweredGantryShaftsFollowTheirNeighbours(GameTestHelper helper) {
		floor(helper);
		for (int i = 0; i < 3; i++)
			helper.setBlock(new BlockPos(1 + i, 2, 3), PulleyRegistry.GANTRY_SHAFT.defaultBlockState()
				.setValue(GantryShaftBlock.FACING, Direction.EAST)
				.setValue(GantryShaftBlock.PART, i == 0 ? Part.START : i == 1 ? Part.MIDDLE : Part.END));
		helper.runAfterDelay(2, () -> helper.setBlock(new BlockPos(1, 3, 3), Blocks.REDSTONE_BLOCK));
		helper.runAfterDelay(8, () -> {
			for (int i = 0; i < 3; i++)
				helper.assertTrue(helper.getBlockState(new BlockPos(1 + i, 2, 3)).getValue(GantryShaftBlock.POWERED), "shaft " + i + " is not powered");
			helper.setBlock(new BlockPos(1, 3, 3), Blocks.AIR);
		});
		helper.runAfterDelay(16, () -> {
			for (int i = 0; i < 3; i++)
				helper.assertTrue(!helper.getBlockState(new BlockPos(1 + i, 2, 3)).getValue(GantryShaftBlock.POWERED), "shaft " + i + " is still powered");
			helper.succeed();
		});
	}

	// ---- elevator ----

	private static final BlockPos ELEVATOR_PULLEY = new BlockPos(2, 7, 3);

	/**
	 * Cabin: a stone block at y=3 (the anchor) glued to a redstone contact at y=2 that faces south. The wall contacts
	 * are in the next column, at y=2 and y=5, facing north. The pulley hangs three blocks above the cabin; the test
	 * box ends at y=7, where a barrier ceiling follows.
	 */
	private static void elevatorSetup(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(2, 3, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(2, 2, 3), RedstoneRegistry.REDSTONE_CONTACT.defaultBlockState().setValue(RedstoneContactBlock.FACING, Direction.SOUTH));
		BlockPos a = helper.absolutePos(new BlockPos(2, 3, 3));
		BlockPos b = helper.absolutePos(new BlockPos(2, 2, 3));
		helper.getLevel().addFreshEntity(new SuperGlueEntity(helper.getLevel(), SuperGlueEntity.span(a, b)));
		helper.setBlock(new BlockPos(2, 2, 4), RedstoneRegistry.REDSTONE_CONTACT.defaultBlockState().setValue(RedstoneContactBlock.FACING, Direction.NORTH));
		helper.setBlock(new BlockPos(2, 5, 4), RedstoneRegistry.REDSTONE_CONTACT.defaultBlockState().setValue(RedstoneContactBlock.FACING, Direction.NORTH));
		helper.setBlock(ELEVATOR_PULLEY, PulleyRegistry.ELEVATOR_PULLEY.defaultBlockState().setValue(HorizontalKineticBlock.HORIZONTAL_FACING, Direction.EAST));
		motor(helper, ELEVATOR_PULLEY.north(), Direction.SOUTH, 64);
		helper.runAfterDelay(8, () -> helper.getBlockEntity(ELEVATOR_PULLEY, ElevatorPulleyBlockEntity.class).clicked());
	}

	private static String elevatorInfo(GameTestHelper helper) {
		ElevatorPulleyBlockEntity pulley = helper.getBlockEntity(ELEVATOR_PULLEY, ElevatorPulleyBlockEntity.class);
		return "pulley speed " + pulley.getSpeed() + " running " + pulley.running + " exception "
			+ (pulley.getLastAssemblyException() == null ? null : pulley.getLastAssemblyException().component.getString());
	}

	private static ColumnCoords column(GameTestHelper helper) {
		BlockPos abs = helper.absolutePos(new BlockPos(2, 0, 4));
		return new ColumnCoords(abs.getX(), abs.getZ(), Direction.NORTH);
	}

	@GameTest(maxTicks = 400)
	public void elevatorWithTwoContactsMovesToTheCalledFloor(GameTestHelper helper) {
		long start = helper.getLevel().getGameTime();
		elevatorSetup(helper);

		helper.runAfterDelay(30, () -> {
			// the contacts of the column became elevator contacts and the cabin was picked up
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 2, 4)).getBlock() instanceof ElevatorContactBlock, "lower contact is " + helper.getBlockState(new BlockPos(2, 2, 4)) + ", " + elevatorInfo(helper));
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 5, 4)).getBlock() instanceof ElevatorContactBlock, "upper contact is " + helper.getBlockState(new BlockPos(2, 5, 4)));
			List<ControlledContraptionEntity> list = entities(helper, ControlledContraptionEntity.class);
			helper.assertTrue(list.size() == 1 && list.get(0).getContraption() instanceof ElevatorContraption, "no elevator contraption: " + list);
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 3, 3)).isAir(), "cabin is still in the world");
			ElevatorColumn column = ElevatorColumn.get(helper.getLevel(), column(helper));
			helper.assertTrue(column != null && column.isActive(), "column is not active");
			helper.assertTrue(column.compileNamesList().size() == 2, "floors " + column.compileNamesList());
			// call the upper floor with a redstone signal
			helper.setBlock(new BlockPos(1, 5, 4), Blocks.REDSTONE_BLOCK);
		});

		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getLevel().getGameTime() - start > 35, "waiting");
			List<ControlledContraptionEntity> list = entities(helper, ControlledContraptionEntity.class);
			helper.assertTrue(list.size() == 1, "contraptions " + list.size());
			double y = list.get(0).getY() - helper.absolutePos(BlockPos.ZERO).getY();
			// the cabin's contact is at the upper floor when its anchor is at y=6
			helper.assertTrue(Math.abs(y - 6) < 0.05, "cabin anchor is at y=" + y);
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 5, 4)).getValue(ElevatorContactBlock.POWERING), "upper contact does not signal arrival");
			helper.assertTrue(!helper.getBlockState(new BlockPos(2, 2, 4)).getValue(ElevatorContactBlock.POWERING), "lower contact still signals");
		});
	}

	@GameTest(maxTicks = 700)
	public void elevatorGoesDownAgainWhenTheLowerFloorIsCalledFromTheGui(GameTestHelper helper) {
		long start = helper.getLevel().getGameTime();
		elevatorSetup(helper);
		helper.runAfterDelay(30, () -> helper.setBlock(new BlockPos(1, 5, 4), Blocks.REDSTONE_BLOCK));
		helper.runAfterDelay(250, () -> {
			helper.setBlock(new BlockPos(1, 5, 4), Blocks.AIR);
			double y = entities(helper, ControlledContraptionEntity.class).get(0).getY() - helper.absolutePos(BlockPos.ZERO).getY();
			helper.assertTrue(Math.abs(y - 6) < 0.05, "cabin did not arrive upstairs: " + y);
			// the same thing the floor selection GUI does
			ElevatorColumn column = ElevatorColumn.get(helper.getLevel(), column(helper));
			BlockPos lower = column.contactAt(helper.absolutePos(new BlockPos(2, 2, 4)).getY());
			BlockState state = helper.getLevel().getBlockState(lower);
			((ElevatorContactBlock) state.getBlock()).callToContactAndUpdate(column, state, helper.getLevel(), lower, false);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getLevel().getGameTime() - start > 255, "waiting");
			double y = entities(helper, ControlledContraptionEntity.class).get(0).getY() - helper.absolutePos(BlockPos.ZERO).getY();
			helper.assertTrue(Math.abs(y - 3) < 0.05, "cabin anchor is at y=" + y);
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 2, 4)).getValue(ElevatorContactBlock.POWERING), "lower contact does not signal arrival");
		});
	}

	@GameTest(maxTicks = 120)
	public void elevatorContractionsReportMissingContactsAndCanBePutBackInPlace(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(2, 3, 3), Blocks.STONE);
		helper.setBlock(ELEVATOR_PULLEY, PulleyRegistry.ELEVATOR_PULLEY.defaultBlockState().setValue(HorizontalKineticBlock.HORIZONTAL_FACING, Direction.EAST));
		motor(helper, ELEVATOR_PULLEY.north(), Direction.SOUTH, 64);
		helper.runAfterDelay(8, () -> helper.getBlockEntity(ELEVATOR_PULLEY, ElevatorPulleyBlockEntity.class).clicked());
		helper.runAfterDelay(20, () -> {
			ElevatorPulleyBlockEntity pulley = helper.getBlockEntity(ELEVATOR_PULLEY, ElevatorPulleyBlockEntity.class);
			helper.assertTrue(pulley.getLastAssemblyException() != null, "no assembly exception");
			helper.assertTrue(!pulley.running, "pulley runs without contacts");
			helper.assertTrue(is(helper, new BlockPos(2, 3, 3), Blocks.STONE), "cabin was moved");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void elevatorContactNamesItsFloorsAndSaves(GameTestHelper helper) {
		floor(helper);
		BlockPos low = new BlockPos(2, 2, 4);
		BlockPos high = new BlockPos(2, 6, 4);
		helper.setBlock(low, PulleyRegistry.ELEVATOR_CONTACT.defaultBlockState().setValue(ElevatorContactBlock.FACING, Direction.NORTH));
		helper.setBlock(high, PulleyRegistry.ELEVATOR_CONTACT.defaultBlockState().setValue(ElevatorContactBlock.FACING, Direction.NORTH));
		helper.runAfterDelay(10, () -> {
			ElevatorContactBlockEntity lowBe = helper.getBlockEntity(low, ElevatorContactBlockEntity.class);
			ElevatorContactBlockEntity highBe = helper.getBlockEntity(high, ElevatorContactBlockEntity.class);
			// floors are numbered from the bottom
			helper.assertTrue(lowBe.shortName.equals("0") && highBe.shortName.equals("1"), "names " + lowBe.shortName + " " + highBe.shortName);
			highBe.updateName("R", "Roof");
			TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
			highBe.saveCustomOnly(out);
			ElevatorContactBlockEntity copy = new ElevatorContactBlockEntity(PulleyRegistry.ELEVATOR_CONTACT_BLOCK_ENTITY, helper.absolutePos(high), helper.getBlockState(high));
			copy.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), out.buildResult()));
			helper.assertTrue(copy.shortName.equals("R") && copy.longName.equals("Roof"), "loaded " + copy.shortName + "/" + copy.longName);
			ElevatorColumn column = ElevatorColumn.get(helper.getLevel(), column(helper));
			helper.assertTrue(column != null && column.compileNamesList().size() == 2, "column " + column);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void wrenchingARedstoneContactInAnElevatorColumnMakesItAnElevatorContact(GameTestHelper helper) {
		floor(helper);
		BlockPos contact = new BlockPos(2, 2, 4);
		helper.setBlock(contact, PulleyRegistry.ELEVATOR_CONTACT.defaultBlockState().setValue(ElevatorContactBlock.FACING, Direction.NORTH));
		helper.setBlock(new BlockPos(2, 6, 4), RedstoneRegistry.REDSTONE_CONTACT.defaultBlockState().setValue(RedstoneContactBlock.FACING, Direction.NORTH));
		helper.runAfterDelay(5, () -> {
			// what the contraption does when an elevator is assembled
			ElevatorColumn column = ElevatorColumn.get(helper.getLevel(), column(helper));
			helper.assertTrue(column != null, "no column");
			column.gatherAll();
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 6, 4)).getBlock() instanceof ElevatorContactBlock, "contact was not converted");
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 6, 4)).getValue(ElevatorContactBlock.FACING) == Direction.NORTH, "facing was lost");
			helper.succeed();
		});
	}
}
