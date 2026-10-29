package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import java.util.List;

import dev.createpolymer.contraptions.AbstractContraptionEntity;
import dev.createpolymer.contraptions.ControlledContraptionEntity;
import dev.createpolymer.contraptions.bearing.BearingBlock;
import dev.createpolymer.contraptions.bearing.MechanicalBearingBlockEntity;
import dev.createpolymer.contraptions.gantry.GantryCarriageBlock;
import dev.createpolymer.contraptions.gantry.GantryContraptionEntity;
import dev.createpolymer.contraptions.glue.SuperGlueEntity;
import dev.createpolymer.contraptions.piston.MechanicalPistonBlock;
import dev.createpolymer.contraptions.piston.MechanicalPistonBlock.PistonState;
import dev.createpolymer.contraptions.piston.MechanicalPistonBlockEntity;
import dev.createpolymer.contraptions.pulley.PulleyBlockEntity;
import dev.createpolymer.kinetics.base.DirectionalAxisKineticBlock;
import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.base.HorizontalAxisKineticBlock;
import dev.createpolymer.kinetics.gantry.GantryShaftBlock;
import dev.createpolymer.kinetics.gantry.GantryShaftBlock.Part;
import dev.createpolymer.kinetics.motor.CreativeMotorBlockEntity;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.BearingRegistry;
import dev.createpolymer.registry.feature.ContraptionRegistry;
import dev.createpolymer.registry.feature.PulleyRegistry;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Contraption actuators set up the way a player or an admin does: blocks placed with /setblock or with items, a creative
 * motor at its default 16 RPM, and kinetics left to propagate on their own (no setSpeed, no setSetting). The other
 * contraption tests change the motor setting after placing it, which hides everything that happens at the very start.
 * <p>
 * A piston facing up with its shaft along Z extends when the motor sits SOUTH of it (facing north, so at -16 RPM) and
 * does nothing, silently and as in Create, when the motor sits NORTH of it (+16 RPM): it is already retracted.
 */
public class PlacedContraptionGameTests {

	private static final BlockPos PISTON = new BlockPos(2, 3, 2);
	/** Extends the piston: facing north, i.e. towards the piston, which makes -16 RPM there. */
	private static final BlockPos EXTENDING_MOTOR = PISTON.south();
	/** Retracts the piston: facing south, +16 RPM. */
	private static final BlockPos RETRACTING_MOTOR = PISTON.north();

	// ---- placing ----

	/** Runs the real /setblock command, as the QA did. */
	private static void setblock(GameTestHelper helper, BlockPos pos, BlockState state) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(pos);
		CommandSourceStack source = level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput();
		level.getServer().getCommands().performPrefixedCommand(source,
			"setblock " + abs.getX() + " " + abs.getY() + " " + abs.getZ() + " " + BlockStateParser.serialize(state));
		helper.assertTrue(helper.getBlockState(pos).getBlock() == state.getBlock(), "/setblock did not place " + state + " at " + pos);
	}

	private static BlockState pole() {
		return ContraptionRegistry.PISTON_EXTENSION_POLE.defaultBlockState().setValue(MechanicalPistonBlock.FACING, Direction.UP);
	}

	private static BlockState piston() {
		return ContraptionRegistry.MECHANICAL_PISTON.defaultBlockState().setValue(MechanicalPistonBlock.FACING, Direction.UP)
			.setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, false);
	}

	private static BlockState motor(Direction facing) {
		return ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, facing);
	}

	private static void poles(GameTestHelper helper, int count) {
		for (int i = 1; i <= count; i++)
			setblock(helper, PISTON.below(i), pole());
	}

	private static MechanicalPistonBlockEntity pistonBE(GameTestHelper helper) {
		return helper.getBlockEntity(PISTON, MechanicalPistonBlockEntity.class);
	}

	private static String pistonInfo(GameTestHelper helper) {
		MechanicalPistonBlockEntity be = pistonBE(helper);
		return "speed=" + be.getSpeed() + " running=" + be.running + " exception=" + be.getLastAssemblyException()
			+ " state=" + helper.getBlockState(PISTON).getValue(MechanicalPistonBlock.STATE);
	}

	/** Stone on the piston's head side, two poles behind it: the stone ends up two blocks higher. */
	private static void assertExtended(GameTestHelper helper) {
		helper.assertTrue(helper.getBlockState(PISTON).getValue(MechanicalPistonBlock.STATE) == PistonState.EXTENDED, "piston is not extended: " + pistonInfo(helper));
		helper.assertTrue(helper.getBlockState(PISTON.above(3)).is(Blocks.STONE), "stone did not move to y=6");
		helper.assertTrue(helper.getBlockState(PISTON.above()).is(ContraptionRegistry.PISTON_EXTENSION_POLE), "no pole in front of the piston");
		helper.assertTrue(helper.getBlockState(PISTON.above(2)).is(ContraptionRegistry.MECHANICAL_PISTON_HEAD), "no piston head");
		helper.assertTrue(contraptions(helper, ControlledContraptionEntity.class).isEmpty(), "contraption entity still exists");
	}

	private static <T extends AbstractContraptionEntity> List<T> contraptions(GameTestHelper helper, Class<T> type) {
		AABB bounds = helper.getBounds().inflate(1);
		return helper.getLevel().getEntitiesOfClass(type, bounds.inflate(16), e -> bounds.contains(e.position()));
	}

	private static BlockHitResult hit(GameTestHelper helper, BlockPos pos, Direction face) {
		BlockPos abs = helper.absolutePos(pos);
		return new BlockHitResult(Vec3.atCenterOf(abs).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(.5)), face, abs, false);
	}

	private static Player player(GameTestHelper helper, float pitch, float yaw) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(6, 1, 6))));
		player.setXRot(pitch);
		player.setYRot(yaw);
		return player;
	}

	/** Right-clicks the face of a block with an item, exactly like the client would. */
	private static void place(GameTestHelper helper, Player player, Item item, BlockPos clicked, Direction face, BlockPos expected) {
		ItemStack stack = new ItemStack(item);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(helper, clicked, face)));
		helper.assertTrue(result.consumesAction(), item + " was not placed on " + clicked + " " + face + ": " + result);
		helper.assertTrue(!helper.getBlockState(expected).isAir(), item + " is not at " + expected);
	}

	// ---- piston, placed by commands ----

	private static void pistonCommandSetup(GameTestHelper helper, boolean motorFirst, Direction motorFacing, BlockPos motorPos) {
		floor(helper);
		if (motorFirst)
			setblock(helper, motorPos, motor(motorFacing));
		poles(helper, 2);
		setblock(helper, PISTON, piston());
		setblock(helper, PISTON.above(), Blocks.STONE.defaultBlockState());
		if (!motorFirst)
			setblock(helper, motorPos, motor(motorFacing));
	}

	@GameTest(maxTicks = 200)
	public void pistonExtendsWhenTheMotorIsSetBlockedLast(GameTestHelper helper) {
		pistonCommandSetup(helper, false, Direction.NORTH, EXTENDING_MOTOR);
		helper.succeedWhen(() -> assertExtended(helper));
	}

	@GameTest(maxTicks = 200)
	public void pistonExtendsWhenTheMotorIsSetBlockedFirst(GameTestHelper helper) {
		pistonCommandSetup(helper, true, Direction.NORTH, EXTENDING_MOTOR);
		helper.succeedWhen(() -> assertExtended(helper));
	}

	@GameTest(maxTicks = 200)
	public void pistonWithThreePolesExtendsThreeBlocks(GameTestHelper helper) {
		floor(helper);
		poles(helper, 3);
		setblock(helper, PISTON, piston());
		setblock(helper, PISTON.above(), Blocks.STONE.defaultBlockState());
		setblock(helper, EXTENDING_MOTOR, motor(Direction.NORTH));
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(PISTON).getValue(MechanicalPistonBlock.STATE) == PistonState.EXTENDED, "piston is not extended: " + pistonInfo(helper));
			helper.assertTrue(helper.getBlockState(PISTON.above(4)).is(Blocks.STONE), "stone did not move to y=7");
		});
	}

	/**
	 * The report: motor beside the piston, poles behind it, Running stays false and there is no exception. This is the
	 * motor on the side that spins the piston the retracting way: Create's getMovementSpeed gives a negative movement,
	 * assemble() sees resultingOffset <= 0 and returns without a message. Reversing the motor extends it.
	 */
	@GameTest(maxTicks = 200)
	public void pistonWithTheMotorOnTheRetractingSideStaysIdleSilentlyUntilReversed(GameTestHelper helper) {
		pistonCommandSetup(helper, false, Direction.SOUTH, RETRACTING_MOTOR);
		helper.runAfterDelay(40, () -> {
			MechanicalPistonBlockEntity be = pistonBE(helper);
			helper.assertTrue(be.getSpeed() == 16, "the piston is not turning at +16: " + pistonInfo(helper));
			helper.assertTrue(!be.running && be.getLastAssemblyException() == null, "expected the silent idle state: " + pistonInfo(helper));
			helper.assertTrue(helper.getBlockState(PISTON.above()).is(Blocks.STONE), "stone moved");
			helper.getBlockEntity(RETRACTING_MOTOR, CreativeMotorBlockEntity.class).setSetting(-16);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(pistonBE(helper).getSpeed() < 0, "waiting for the reversal");
			assertExtended(helper);
		});
	}

	/** A /setblock without a block state gives facing=north and axis_along_first=true (the property's first value): the shaft is along X. */
	@GameTest(maxTicks = 60)
	public void pistonWithDefaultStateTakesRotationOnlyFromEastAndWest(GameTestHelper helper) {
		floor(helper);
		BlockPos at = new BlockPos(2, 2, 2);
		setblock(helper, at, ContraptionRegistry.MECHANICAL_PISTON.defaultBlockState());
		helper.assertTrue(((dev.createpolymer.kinetics.base.IRotate) helper.getBlockState(at).getBlock()).getRotationAxis(helper.getBlockState(at)) == Axis.X,
			"default piston does not turn around X: " + helper.getBlockState(at));
		// behind the piston, facing it (the motor's shaft is along Z): nothing is driven
		setblock(helper, at.south(), motor(Direction.NORTH));
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(helper.getBlockEntity(at, MechanicalPistonBlockEntity.class).getSpeed() == 0, "driven along the wrong axis");
			setblock(helper, at.east(), motor(Direction.WEST));
		});
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(Math.abs(helper.getBlockEntity(at, MechanicalPistonBlockEntity.class).getSpeed()) == 16,
				"the piston is not driven from the east, speed " + helper.getBlockEntity(at, MechanicalPistonBlockEntity.class).getSpeed());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void pistonWithTheMotorOnItsShaftlessSideIsNotDriven(GameTestHelper helper) {
		pistonCommandSetup(helper, false, Direction.WEST, PISTON.east());
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(pistonBE(helper).getSpeed() == 0, "driven from the side: " + pistonInfo(helper));
			helper.assertTrue(!pistonBE(helper).running, "ran");
			helper.succeed();
		});
	}

	/** Poles placed after the piston: Create only clears the error; an empty-handed click (or a speed change) assembles. */
	@GameTest(maxTicks = 300)
	public void polesPlacedAfterThePistonNeedAClickToAssemble(GameTestHelper helper) {
		long start = helper.getLevel().getGameTime();
		floor(helper);
		setblock(helper, PISTON, piston());
		setblock(helper, PISTON.above(), Blocks.STONE.defaultBlockState());
		setblock(helper, EXTENDING_MOTOR, motor(Direction.NORTH));
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(pistonBE(helper).getLastAssemblyException() != null, "no 'missing poles' error: " + pistonInfo(helper));
			poles(helper, 2);
		});
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(pistonBE(helper).getLastAssemblyException() == null, "the error was not cleared by the poles: " + pistonInfo(helper));
			helper.assertTrue(!pistonBE(helper).running, "assembled without being asked, as Create does not: " + pistonInfo(helper));
			Player player = player(helper, 0, 0);
			helper.useBlock(PISTON, player, hit(helper, PISTON, Direction.NORTH));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getLevel().getGameTime() - start > 45, "waiting for the click");
			assertExtended(helper);
		});
	}

	@GameTest(maxTicks = 300)
	public void polesPlacedAfterThePistonWorkAfterASpeedChange(GameTestHelper helper) {
		long start = helper.getLevel().getGameTime();
		floor(helper);
		setblock(helper, PISTON, piston());
		setblock(helper, PISTON.above(), Blocks.STONE.defaultBlockState());
		setblock(helper, EXTENDING_MOTOR, motor(Direction.NORTH));
		helper.runAfterDelay(20, () -> poles(helper, 2));
		helper.runAfterDelay(40, () -> helper.getBlockEntity(EXTENDING_MOTOR, CreativeMotorBlockEntity.class).setSetting(32));
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getLevel().getGameTime() - start > 45, "waiting");
			assertExtended(helper);
		});
	}

	// ---- piston, placed with items ----

	/**
	 * Poles, piston and stone are placed by clicking on blocks, looking down (the piston faces up), then the motor by
	 * clicking the piston's south side: placement picks the piston as the motor's neighbour and faces it.
	 */
	@GameTest(maxTicks = 200)
	public void pistonBuiltWithItemsExtendsWhenTheMotorIsPlacedLast(GameTestHelper helper) {
		floor(helper);
		Player player = player(helper, 90, 0);
		place(helper, player, ContraptionRegistry.PISTON_EXTENSION_POLE_ITEM, new BlockPos(2, 0, 2), Direction.UP, new BlockPos(2, 1, 2));
		place(helper, player, ContraptionRegistry.PISTON_EXTENSION_POLE_ITEM, new BlockPos(2, 1, 2), Direction.UP, new BlockPos(2, 2, 2));
		place(helper, player, ContraptionRegistry.MECHANICAL_PISTON_ITEM, new BlockPos(2, 2, 2), Direction.UP, PISTON);
		place(helper, player, Items.STONE, PISTON, Direction.UP, PISTON.above());
		helper.assertTrue(helper.getBlockState(PISTON).getValue(MechanicalPistonBlock.FACING) == Direction.UP, "piston faces " + helper.getBlockState(PISTON));
		place(helper, player, ModItems.CREATIVE_MOTOR, PISTON, Direction.SOUTH, EXTENDING_MOTOR);
		helper.assertTrue(helper.getBlockState(EXTENDING_MOTOR).getValue(DirectionalKineticBlock.FACING) == Direction.NORTH,
			"the motor faces " + helper.getBlockState(EXTENDING_MOTOR));
		helper.succeedWhen(() -> assertExtended(helper));
	}

	/** The motor first, on a support block, looking south so that it faces north; then the poles and the piston next to it. */
	@GameTest(maxTicks = 200)
	public void pistonBuiltWithItemsExtendsWhenTheMotorIsPlacedFirst(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(EXTENDING_MOTOR.below(), Blocks.STONE);
		Player player = player(helper, 0, 0);
		place(helper, player, ModItems.CREATIVE_MOTOR, EXTENDING_MOTOR.below(), Direction.UP, EXTENDING_MOTOR);
		helper.assertTrue(helper.getBlockState(EXTENDING_MOTOR).getValue(DirectionalKineticBlock.FACING) == Direction.NORTH,
			"the motor faces " + helper.getBlockState(EXTENDING_MOTOR));
		player.setXRot(90);
		place(helper, player, ContraptionRegistry.PISTON_EXTENSION_POLE_ITEM, new BlockPos(2, 0, 2), Direction.UP, new BlockPos(2, 1, 2));
		place(helper, player, ContraptionRegistry.PISTON_EXTENSION_POLE_ITEM, new BlockPos(2, 1, 2), Direction.UP, new BlockPos(2, 2, 2));
		place(helper, player, ContraptionRegistry.MECHANICAL_PISTON_ITEM, new BlockPos(2, 2, 2), Direction.UP, PISTON);
		place(helper, player, Items.STONE, PISTON, Direction.UP, PISTON.above());
		helper.assertTrue(helper.getBlockState(PISTON).getValue(MechanicalPistonBlock.FACING) == Direction.UP
			&& !helper.getBlockState(PISTON).getValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE), "piston is " + helper.getBlockState(PISTON));
		helper.succeedWhen(() -> assertExtended(helper));
	}

	// ---- bearing, rope pulley, gantry ----

	@GameTest(maxTicks = 120, padding = 24)
	public void mechanicalBearingPlacedWithCommandsTurnsItsArm(GameTestHelper helper) {
		floor(helper);
		BlockPos bearing = new BlockPos(3, 1, 3);
		BlockPos motor = bearing.below();
		setblock(helper, bearing, BearingRegistry.MECHANICAL_BEARING.defaultBlockState().setValue(BearingBlock.FACING, Direction.UP));
		setblock(helper, bearing.above(), Blocks.STONE.defaultBlockState());
		setblock(helper, bearing.above().east(), Blocks.STONE.defaultBlockState());
		helper.getLevel().addFreshEntity(new SuperGlueEntity(helper.getLevel(),
			SuperGlueEntity.span(helper.absolutePos(bearing.above()), helper.absolutePos(bearing.above().east()))));
		// the floor is in the way below the bearing
		helper.setBlock(motor, Blocks.AIR);
		setblock(helper, motor, motor(Direction.UP));
		helper.succeedWhen(() -> {
			List<ControlledContraptionEntity> entities = contraptions(helper, ControlledContraptionEntity.class);
			helper.assertTrue(entities.size() == 1, "expected one contraption, found " + entities.size() + ", bearing running="
				+ helper.getBlockEntity(bearing, MechanicalBearingBlockEntity.class).isRunning() + " speed=" + helper.getBlockEntity(bearing, MechanicalBearingBlockEntity.class).getSpeed());
			helper.assertTrue(Math.abs(entities.get(0).getAngle(1)) > 10, "arm does not turn, angle " + entities.get(0).getAngle(1));
		});
	}

	@GameTest(maxTicks = 200)
	public void ropePulleyPlacedWithCommandsLowersItsPlatform(GameTestHelper helper) {
		floor(helper);
		BlockPos pulley = new BlockPos(2, 5, 2);
		setblock(helper, pulley, PulleyRegistry.ROPE_PULLEY.defaultBlockState().setValue(HorizontalAxisKineticBlock.HORIZONTAL_AXIS, Axis.Z));
		setblock(helper, pulley.below(), Blocks.STONE.defaultBlockState());
		setblock(helper, pulley.below().south(), Blocks.STONE.defaultBlockState());
		helper.getLevel().addFreshEntity(new SuperGlueEntity(helper.getLevel(),
			SuperGlueEntity.span(helper.absolutePos(pulley.below()), helper.absolutePos(pulley.below().south()))));
		setblock(helper, pulley.north(), motor(Direction.SOUTH));
		helper.succeedWhen(() -> {
			PulleyBlockEntity be = helper.getBlockEntity(pulley, PulleyBlockEntity.class);
			helper.assertTrue(be.running, "pulley is idle, speed " + be.getSpeed() + " exception " + be.getLastAssemblyException());
			helper.assertTrue(be.getDisplayOffset() > 1.5f, "platform has not lowered yet: " + be.getDisplayOffset());
		});
	}

	@GameTest(maxTicks = 400)
	public void gantryCarriagePlacedWithCommandsMovesAlongItsShaft(GameTestHelper helper) {
		floor(helper);
		Part[] parts = {Part.START, Part.MIDDLE, Part.MIDDLE, Part.END};
		for (int i = 0; i < 4; i++)
			setblock(helper, new BlockPos(1 + i, 2, 3), PulleyRegistry.GANTRY_SHAFT.defaultBlockState()
				.setValue(GantryShaftBlock.FACING, Direction.EAST)
				.setValue(GantryShaftBlock.PART, parts[i]));
		BlockPos carriage = new BlockPos(2, 3, 3);
		setblock(helper, carriage, PulleyRegistry.GANTRY_CARRIAGE.defaultBlockState()
			.setValue(GantryCarriageBlock.FACING, Direction.UP)
			.setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, false));
		setblock(helper, carriage.above(), Blocks.STONE.defaultBlockState());
		setblock(helper, new BlockPos(0, 2, 3), motor(Direction.EAST));
		helper.succeedWhen(() -> {
			boolean atStart = helper.getBlockState(new BlockPos(1, 3, 3)).getBlock() instanceof GantryCarriageBlock;
			boolean atEnd = helper.getBlockState(new BlockPos(4, 3, 3)).getBlock() instanceof GantryCarriageBlock;
			helper.assertTrue(atStart || atEnd, "the carriage did not arrive at either end of the shaft, contraptions "
				+ contraptions(helper, GantryContraptionEntity.class).size() + ", carriage at its place: "
				+ (helper.getBlockState(carriage).getBlock() instanceof GantryCarriageBlock));
			helper.assertTrue(contraptions(helper, GantryContraptionEntity.class).isEmpty(), "contraption entity still exists");
			BlockPos end = atEnd ? new BlockPos(4, 4, 3) : new BlockPos(1, 4, 3);
			helper.assertTrue(helper.getBlockState(end).is(Blocks.STONE), "stone did not come along");
		});
	}
}
