package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.assertSpeed;
import static dev.createpolymer.test.TestUtil.floor;
import static dev.createpolymer.test.TestUtil.speed;

import java.util.Vector;

import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.base.HorizontalAxisKineticBlock;
import dev.createpolymer.kinetics.base.RotatedPillarKineticBlock;
import dev.createpolymer.kinetics.chaindrive.ChainDriveBlock;
import dev.createpolymer.kinetics.clock.CuckooClockBlockEntity;
import dev.createpolymer.kinetics.crank.ValveHandleBlockEntity;
import dev.createpolymer.kinetics.encased.EncasedCogwheelBlock;
import dev.createpolymer.kinetics.flywheel.FlywheelBlockEntity;
import dev.createpolymer.kinetics.transmission.sequencer.Instruction;
import dev.createpolymer.kinetics.transmission.sequencer.InstructionSpeedModifiers;
import dev.createpolymer.kinetics.transmission.sequencer.SequencedGearshiftBlock;
import dev.createpolymer.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import dev.createpolymer.kinetics.transmission.sequencer.SequencedGearshiftGui;
import dev.createpolymer.kinetics.transmission.sequencer.SequencerInstructions;
import dev.createpolymer.kinetics.turntable.TurntableHandler;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.KineticsExtraRegistry;
import dev.createpolymer.util.AngleHelper;
import eu.pb4.sgui.api.ClickType;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class KineticsExtraGameTests {

	private static void motorEast(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
	}

	private static void shaftX(GameTestHelper helper, int x, int y, int z) {
		helper.setBlock(new BlockPos(x, y, z), ModBlocks.SHAFT.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.X));
	}

	/** A right-click the way the server does it: use-block callbacks first, then the block itself. */
	private static void use(GameTestHelper helper, BlockPos pos, Player player) {
		BlockPos abs = helper.absolutePos(pos);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false);
		InteractionResult result = UseBlockCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit);
		if (!result.consumesAction())
			helper.useBlock(pos, player, hit);
	}

	private static Player player(GameTestHelper helper, Item held) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(held));
		return player;
	}

	// ---- flywheel ----

	@GameTest(maxTicks = 100)
	public void flywheelSpinsWhenDriven(GameTestHelper helper) {
		floor(helper);
		motorEast(helper, new BlockPos(1, 1, 1));
		BlockPos wheel = new BlockPos(2, 1, 1);
		helper.setBlock(wheel, KineticsExtraRegistry.FLYWHEEL.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.X));
		helper.succeedWhen(() -> {
			assertSpeed(helper, wheel, 16);
			FlywheelBlockEntity be = helper.getBlockEntity(wheel, FlywheelBlockEntity.class);
			// the wheel speeds up slowly towards the shaft speed
			helper.assertTrue(be.getVisualSpeed() > 2 && be.getVisualSpeed() < 16, "visual speed " + be.getVisualSpeed());
			helper.assertTrue(be.getAngle() != 0, "wheel has not turned");
		});
	}

	// ---- turntable ----

	@GameTest(maxTicks = 100)
	public void turntableSpinsEntitiesStandingOnIt(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(2, 1, 2), ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
		helper.setBlock(new BlockPos(2, 2, 2), KineticsExtraRegistry.TURNTABLE);
		ArmorStand[] stand = new ArmorStand[1];
		float[] start = new float[1];
		helper.runAfterDelay(5, () -> {
			stand[0] = helper.spawn(net.minecraft.world.entity.EntityTypes.ARMOR_STAND, new BlockPos(2, 3, 2));
			start[0] = stand[0].getYRot();
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(stand[0] != null, "not spawned yet");
			helper.assertTrue(Math.abs(AngleHelper.getShortestAngleDiff(start[0], stand[0].getYRot())) > 20,
				"armor stand yaw " + start[0] + " -> " + stand[0].getYRot());
		});
	}

	@GameTest(maxTicks = 40)
	public void turntableTurnsPlayers(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(2, 1, 2), ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
		helper.setBlock(new BlockPos(2, 2, 2), KineticsExtraRegistry.TURNTABLE);
		ServerPlayer player = FakePlayer.get(helper.getLevel());
		helper.runAfterDelay(5, () -> {
			player.snapTo(helper.absoluteVec(new Vec3(2.5, 2.5, 2.5)));
			player.setYRot(0);
			player.setOnGround(true);
			TurntableHandler.tickPlayer(player);
			// 16 RPM turns the player 2/3 of a degree per RPM and tick
			helper.assertTrue(Math.abs(AngleHelper.getShortestAngleDiff(0, player.getYRot()) + 16 * 2 / 3f) < 0.01f,
				"player yaw " + player.getYRot());
			helper.succeed();
		});
	}

	// ---- chain gearshift ----

	private static BlockState linked(BlockState state, Direction towards, BlockState other) {
		return ChainDriveBlock.connect(state.setValue(RotatedPillarKineticBlock.AXIS, Axis.X), towards, other);
	}

	@GameTest(maxTicks = 80)
	public void chainGearshiftDoublesSpeedWithRedstone(GameTestHelper helper) {
		floor(helper);
		motorEast(helper, new BlockPos(1, 1, 1));
		BlockState drive = ModBlocks.ENCASED_CHAIN_DRIVE.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.X);
		BlockState gearshift = KineticsExtraRegistry.ADJUSTABLE_CHAIN_GEARSHIFT.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.X);
		// motor -> gearshift -> chain drive next to it -> shaft
		BlockState first = linked(gearshift, Direction.SOUTH, drive);
		helper.setBlock(new BlockPos(2, 1, 1), first);
		helper.setBlock(new BlockPos(2, 1, 2), linked(drive, Direction.NORTH, first));
		shaftX(helper, 3, 1, 2);
		BlockPos out = new BlockPos(3, 1, 2);
		helper.runAfterDelay(6, () -> {
			assertSpeed(helper, out, 16);
			helper.setBlock(new BlockPos(2, 2, 1), Blocks.REDSTONE_BLOCK);
		});
		helper.runAfterDelay(14, () -> {
			// signal 15 gives 1 + 16/16
			assertSpeed(helper, out, 32);
			helper.setBlock(new BlockPos(2, 2, 1), Blocks.AIR);
		});
		helper.runAfterDelay(22, () -> helper.succeedWhen(() -> assertSpeed(helper, out, 16)));
	}

	@GameTest(maxTicks = 80)
	public void chainGearshiftHalvesSpeedWhenItReceivesRotation(GameTestHelper helper) {
		floor(helper);
		motorEast(helper, new BlockPos(1, 1, 1));
		BlockState drive = ModBlocks.ENCASED_CHAIN_DRIVE.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.X);
		BlockState gearshift = KineticsExtraRegistry.ADJUSTABLE_CHAIN_GEARSHIFT.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.X);
		// motor -> chain drive -> gearshift next to it -> shaft
		BlockState first = linked(drive, Direction.SOUTH, gearshift);
		helper.setBlock(new BlockPos(2, 1, 1), first);
		helper.setBlock(new BlockPos(2, 1, 2), linked(gearshift, Direction.NORTH, first));
		shaftX(helper, 3, 1, 2);
		BlockPos out = new BlockPos(3, 1, 2);
		helper.runAfterDelay(6, () -> {
			assertSpeed(helper, out, 16);
			helper.setBlock(new BlockPos(2, 2, 2), Blocks.REDSTONE_BLOCK);
		});
		helper.runAfterDelay(16, () -> helper.succeedWhen(() -> assertSpeed(helper, out, 8)));
	}

	// ---- sequenced gearshift ----

	private static BlockPos sequencerLine(GameTestHelper helper) {
		floor(helper);
		motorEast(helper, new BlockPos(1, 1, 1));
		shaftX(helper, 2, 1, 1);
		BlockPos gearshift = new BlockPos(3, 1, 1);
		helper.setBlock(gearshift, KineticsExtraRegistry.SEQUENCED_GEARSHIFT.defaultBlockState()
			.setValue(HorizontalAxisKineticBlock.HORIZONTAL_AXIS, Axis.X));
		shaftX(helper, 4, 1, 1);
		return gearshift;
	}

	@GameTest(maxTicks = 100)
	public void sequencedGearshiftTurns90DegreesAndStops(GameTestHelper helper) {
		BlockPos gearshift = sequencerLine(helper);
		BlockPos out = new BlockPos(4, 1, 1);
		int[] turningTicks = new int[1];
		// (tests cannot register tick callbacks from inside a delayed callback)
		helper.onEachTick(() -> {
			if (speed(helper, out) == 16)
				turningTicks[0]++;
		});
		helper.runAfterDelay(6, () -> {
			// idle: the input turns, nothing goes through
			assertSpeed(helper, new BlockPos(2, 1, 1), 16);
			assertSpeed(helper, out, 0);
			helper.setBlock(new BlockPos(3, 2, 1), Blocks.REDSTONE_BLOCK);
		});
		helper.runAfterDelay(12, () -> {
			assertSpeed(helper, out, 16);
			helper.assertTrue(helper.getBlockState(gearshift).getValue(SequencedGearshiftBlock.STATE) == 1, "not running instruction 1");
		});
		helper.runAfterDelay(50, () -> helper.succeedWhen(() -> {
			assertSpeed(helper, out, 0);
			helper.assertTrue(helper.getBlockEntity(gearshift, SequencedGearshiftBlockEntity.class).isIdle(), "still running");
			// 16 RPM is 4.8 degrees per tick: 90 degrees take 19 ticks and the instruction overshoots by 2
			float degrees = turningTicks[0] * 4.8f;
			helper.assertTrue(degrees >= 90 && degrees <= 90 + 6 * 4.8f, "turned " + degrees + " degrees in " + turningTicks[0] + " ticks");
		}));
	}

	@GameTest(maxTicks = 120)
	public void sequencedGearshiftRunsDelayThenReversedTurn(GameTestHelper helper) {
		BlockPos gearshift = sequencerLine(helper);
		BlockPos out = new BlockPos(4, 1, 1);
		helper.runAfterDelay(4, () -> {
			Vector<Instruction> program = new Vector<>();
			program.add(new Instruction(SequencerInstructions.DELAY, 20));
			program.add(new Instruction(SequencerInstructions.TURN_ANGLE, InstructionSpeedModifiers.BACK_FAST, 90));
			program.add(new Instruction(SequencerInstructions.END));
			helper.getBlockEntity(gearshift, SequencedGearshiftBlockEntity.class).applyInstructions(program);
			helper.setBlock(new BlockPos(3, 2, 1), Blocks.REDSTONE_BLOCK);
		});
		helper.runAfterDelay(14, () -> assertSpeed(helper, out, 0));
		helper.runAfterDelay(30, () -> assertSpeed(helper, out, -32));
		helper.runAfterDelay(60, () -> helper.succeedWhen(() -> {
			assertSpeed(helper, out, 0);
			helper.assertTrue(helper.getBlockEntity(gearshift, SequencedGearshiftBlockEntity.class).isIdle(), "still running");
		}));
	}

	@GameTest(maxTicks = 100)
	public void sequencedGearshiftWaitsForAnotherPulse(GameTestHelper helper) {
		BlockPos gearshift = sequencerLine(helper);
		BlockPos out = new BlockPos(4, 1, 1);
		helper.runAfterDelay(4, () -> {
			Vector<Instruction> program = new Vector<>();
			program.add(new Instruction(SequencerInstructions.AWAIT));
			program.add(new Instruction(SequencerInstructions.TURN_ANGLE, 90));
			program.add(new Instruction(SequencerInstructions.END));
			helper.getBlockEntity(gearshift, SequencedGearshiftBlockEntity.class).applyInstructions(program);
			helper.setBlock(new BlockPos(3, 2, 1), Blocks.REDSTONE_BLOCK);
		});
		helper.runAfterDelay(15, () -> {
			// running the await instruction: nothing turns until the next pulse
			assertSpeed(helper, out, 0);
			helper.setBlock(new BlockPos(3, 2, 1), Blocks.AIR);
		});
		helper.runAfterDelay(20, () -> helper.setBlock(new BlockPos(3, 2, 1), Blocks.REDSTONE_BLOCK));
		helper.runAfterDelay(26, () -> assertSpeed(helper, out, 16));
		helper.runAfterDelay(60, () -> helper.succeedWhen(() -> assertSpeed(helper, out, 0)));
	}

	@GameTest(maxTicks = 40)
	public void sequencedGearshiftEditorChangesTheInstructions(GameTestHelper helper) {
		BlockPos pos = sequencerLine(helper);
		ServerPlayer player = FakePlayer.get(helper.getLevel());
		helper.runAfterDelay(3, () -> {
			SequencedGearshiftBlockEntity be = helper.getBlockEntity(pos, SequencedGearshiftBlockEntity.class);
			SequencedGearshiftGui gui = new SequencedGearshiftGui(player, be);
			// row 0: type, "-", value, "+", speed
			click(gui, 3, ClickType.MOUSE_LEFT);
			click(gui, 3, ClickType.MOUSE_LEFT_SHIFT);
			click(gui, 4, ClickType.MOUSE_LEFT);
			// row 1 is the END row: three steps on in the list (turn, piston, delay)
			click(gui, 9, ClickType.MOUSE_LEFT);
			click(gui, 9, ClickType.MOUSE_LEFT);
			click(gui, 9, ClickType.MOUSE_LEFT);
			click(gui, 12, ClickType.MOUSE_LEFT_SHIFT);
			gui.onPlayerClose(true);

			Vector<Instruction> list = be.getInstructions();
			helper.assertTrue(list.size() == 3, "instructions: " + list.size());
			helper.assertTrue(list.get(0).getInstruction() == SequencerInstructions.TURN_ANGLE, "type 0");
			// 90 + 1 + 45 degrees, speed forward -> one step on in the list (back)
			helper.assertTrue(list.get(0).getValue() == 136, "angle " + list.get(0).getValue());
			helper.assertTrue(list.get(0).getSpeedModifierType() == InstructionSpeedModifiers.BACK, "speed " + list.get(0).getSpeedModifierType());
			helper.assertTrue(list.get(1).getInstruction() == SequencerInstructions.DELAY, "type 1 " + list.get(1).getInstruction());
			// default delay 10 ticks, shift step below 20 ticks is 20
			helper.assertTrue(list.get(1).getValue() == 30, "delay " + list.get(1).getValue());
			helper.assertTrue(list.get(2).getInstruction() == SequencerInstructions.END, "type 2");
			helper.succeed();
		});
	}

	private static void click(SequencedGearshiftGui gui, int slot, ClickType type) {
		gui.getGuiElement(slot).getGuiCallback().click(slot, type, ContainerInput.PICKUP, gui);
	}

	@GameTest(maxTicks = 40)
	public void wrenchRotatesSequencedGearshift(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, KineticsExtraRegistry.SEQUENCED_GEARSHIFT.defaultBlockState().setValue(HorizontalAxisKineticBlock.HORIZONTAL_AXIS, Axis.X));
		BlockPos abs = helper.absolutePos(pos);
		helper.runAfterDelay(2, () -> {
			// clicking the top turns it around the vertical axis
			helper.useBlock(pos, player(helper, ModItems.WRENCH), new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
			helper.assertTrue(helper.getBlockState(pos).getValue(HorizontalAxisKineticBlock.HORIZONTAL_AXIS) == Axis.Z, "not turned");
			// clicking a side that is not along its axis lays it down vertically
			helper.useBlock(pos, player(helper, ModItems.WRENCH), new BlockHitResult(Vec3.atCenterOf(abs), Direction.EAST, abs, false));
			helper.assertTrue(helper.getBlockState(pos).getValue(SequencedGearshiftBlock.VERTICAL), "not vertical");
		});
		helper.runAfterDelay(6, () -> helper.succeedWhen(() -> helper.assertTrue(
			KineticsExtraRegistry.SEQUENCED_GEARSHIFT.getRotationAxis(helper.getBlockState(pos)) == Axis.Y, "rotation axis")));
	}

	@GameTest(maxTicks = 20)
	public void everyStateHasACasingModel(GameTestHelper helper) {
		String[][] files = {
			{ "brass_encased_shaft" }, { "brass_encased_cogwheel" }, { "brass_encased_large_cogwheel" },
			{ "adjustable_chain_gearshift" }, { "sequenced_gearshift" } };
		Block[] blocks = { KineticsExtraRegistry.BRASS_ENCASED_SHAFT, KineticsExtraRegistry.BRASS_ENCASED_COGWHEEL,
			KineticsExtraRegistry.BRASS_ENCASED_LARGE_COGWHEEL, KineticsExtraRegistry.ADJUSTABLE_CHAIN_GEARSHIFT,
			KineticsExtraRegistry.SEQUENCED_GEARSHIFT };
		for (int i = 0; i < blocks.length; i++)
			for (BlockState state : blocks[i].getStateDefinition().getPossibleStates())
				helper.assertTrue(dev.createpolymer.visual.BlockstateModels.get(files[i][0], state) != null,
					files[i][0] + " has no model for " + state);
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void blockEntitiesSurviveSaving(GameTestHelper helper) {
		BlockPos gearshift = sequencerLine(helper);
		BlockPos handle = new BlockPos(5, 1, 3);
		helper.setBlock(handle, KineticsExtraRegistry.COPPER_VALVE_HANDLE.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
		helper.setBlock(new BlockPos(5, 0, 3), Blocks.STONE);
		helper.runAfterDelay(3, () -> {
			var registries = helper.getLevel().registryAccess();
			SequencedGearshiftBlockEntity be = helper.getBlockEntity(gearshift, SequencedGearshiftBlockEntity.class);
			Vector<Instruction> program = new Vector<>();
			program.add(new Instruction(SequencerInstructions.TURN_DISTANCE, InstructionSpeedModifiers.BACK_FAST, 12));
			program.add(new Instruction(SequencerInstructions.DELAY, 40));
			program.add(new Instruction(SequencerInstructions.END));
			be.applyInstructions(program);
			var loaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(helper.absolutePos(gearshift),
				helper.getBlockState(gearshift), be.saveWithFullMetadata(registries), registries);
			helper.assertTrue(loaded instanceof SequencedGearshiftBlockEntity, "not loaded");
			Vector<Instruction> read = ((SequencedGearshiftBlockEntity) loaded).getInstructions();
			helper.assertTrue(read.size() == 3, "size " + read.size());
			helper.assertTrue(read.get(0).getInstruction() == SequencerInstructions.TURN_DISTANCE && read.get(0).getValue() == 12
				&& read.get(0).getSpeedModifierType() == InstructionSpeedModifiers.BACK_FAST, "first instruction");
			helper.assertTrue(read.get(1).getInstruction() == SequencerInstructions.DELAY && read.get(1).getValue() == 40, "second instruction");

			ValveHandleBlockEntity valve = helper.getBlockEntity(handle, ValveHandleBlockEntity.class);
			valve.setAngleInput(-135);
			var loadedValve = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(helper.absolutePos(handle),
				helper.getBlockState(handle), valve.saveWithFullMetadata(registries), registries);
			helper.assertTrue(loadedValve instanceof ValveHandleBlockEntity v && v.getAngleInput() == -135, "valve angle");
			helper.succeed();
		});
	}

	// ---- valve handle ----

	private static BlockPos valveSetup(GameTestHelper helper, Block handle) {
		floor(helper);
		shaftX(helper, 2, 1, 1);
		BlockPos pos = new BlockPos(3, 1, 1);
		helper.setBlock(pos, handle.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
		return pos;
	}

	@GameTest(maxTicks = 60)
	public void valveHandleTurnsByItsAngleAndStops(GameTestHelper helper) {
		BlockPos handle = valveSetup(helper, KineticsExtraRegistry.COPPER_VALVE_HANDLE);
		helper.runAfterDelay(2, () -> use(helper, handle, helper.makeMockPlayer(GameType.SURVIVAL)));
		helper.runAfterDelay(5, () -> {
			assertSpeed(helper, handle, 32);
			assertSpeed(helper, new BlockPos(2, 1, 1), 32);
		});
		helper.runAfterDelay(8, () -> {
			// a handle that is turning cannot be used again
			ValveHandleBlockEntity be = helper.getBlockEntity(handle, ValveHandleBlockEntity.class);
			helper.assertTrue(be.inUse > 0, "not in use");
			helper.assertTrue(!be.activate(false), "activated twice");
		});
		helper.runAfterDelay(20, () -> helper.succeedWhen(() -> {
			assertSpeed(helper, handle, 0);
			assertSpeed(helper, new BlockPos(2, 1, 1), 0);
			// 45 degrees round to a quarter turn
			helper.assertTrue(helper.getBlockEntity(handle, ValveHandleBlockEntity.class).getIndependentAngle(0) == 90,
				"angle " + helper.getBlockEntity(handle, ValveHandleBlockEntity.class).getIndependentAngle(0));
		}));
	}

	@GameTest(maxTicks = 60)
	public void valveHandleTurnsTheOtherWayWhenSneaking(GameTestHelper helper) {
		BlockPos handle = valveSetup(helper, KineticsExtraRegistry.COPPER_VALVE_HANDLE);
		helper.runAfterDelay(2, () -> {
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			player.setShiftKeyDown(true);
			use(helper, handle, player);
		});
		helper.runAfterDelay(5, () -> helper.succeedWhen(() -> assertSpeed(helper, new BlockPos(2, 1, 1), -32)));
	}

	@GameTest(maxTicks = 60)
	public void valveHandleLongTurnsGoHalfAround(GameTestHelper helper) {
		BlockPos handle = valveSetup(helper, KineticsExtraRegistry.COPPER_VALVE_HANDLE);
		helper.getBlockEntity(handle, ValveHandleBlockEntity.class).setAngleInput(-180);
		helper.runAfterDelay(2, () -> use(helper, handle, helper.makeMockPlayer(GameType.SURVIVAL)));
		// a negative angle turns the other way; 180 degrees at 9.6 degrees per tick plus 2 ticks
		helper.runAfterDelay(5, () -> assertSpeed(helper, new BlockPos(2, 1, 1), -32));
		helper.runAfterDelay(40, () -> helper.succeedWhen(() -> {
			assertSpeed(helper, new BlockPos(2, 1, 1), 0);
			helper.assertTrue(helper.getBlockEntity(handle, ValveHandleBlockEntity.class).getIndependentAngle(0) == -180,
				"angle " + helper.getBlockEntity(handle, ValveHandleBlockEntity.class).getIndependentAngle(0));
		}));
	}

	@GameTest(maxTicks = 40)
	public void dyeColoursValveHandleAndKeepsItsAngle(GameTestHelper helper) {
		BlockPos handle = valveSetup(helper, KineticsExtraRegistry.COPPER_VALVE_HANDLE);
		helper.getBlockEntity(handle, ValveHandleBlockEntity.class).setAngleInput(135);
		helper.runAfterDelay(2, () -> use(helper, handle, player(helper, Items.DYE.red())));
		helper.succeedWhen(() -> {
			Block red = KineticsExtraRegistry.DYED_VALVE_HANDLES.get(DyeColor.RED);
			helper.assertBlockPresent(red, handle);
			helper.assertTrue(helper.getBlockState(handle).getValue(DirectionalKineticBlock.FACING) == Direction.EAST, "lost the facing");
			helper.assertTrue(helper.getBlockEntity(handle, ValveHandleBlockEntity.class).getAngleInput() == 135, "lost the angle");
		});
	}

	@GameTest(maxTicks = 40)
	public void wrenchStepsThroughValveHandleAngles(GameTestHelper helper) {
		BlockPos handle = valveSetup(helper, KineticsExtraRegistry.DYED_VALVE_HANDLES.get(DyeColor.BLUE));
		helper.runAfterDelay(2, () -> use(helper, handle, player(helper, ModItems.WRENCH)));
		helper.succeedWhen(() -> helper.assertTrue(helper.getBlockEntity(handle, ValveHandleBlockEntity.class).getAngleInput() == 90,
			"angle " + helper.getBlockEntity(handle, ValveHandleBlockEntity.class).getAngleInput()));
	}

	// ---- cuckoo clocks ----

	@GameTest(maxTicks = 120)
	public void cuckooClockShowsTheTime(GameTestHelper helper) {
		floor(helper);
		motorEast(helper, new BlockPos(1, 1, 1));
		BlockPos clock = new BlockPos(2, 1, 1);
		helper.setBlock(clock, KineticsExtraRegistry.CUCKOO_CLOCK.defaultBlockState().setValue(
			net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		helper.setTime(9500);
		helper.runAfterDelay(60, () -> helper.succeedWhen(() -> {
			CuckooClockBlockEntity be = helper.getBlockEntity(clock, CuckooClockBlockEntity.class);
			long time = helper.getLevel().getDefaultClockTime() % 24000;
			int hours = (int) ((time / 1000 + 6) % 24);
			int minutes = (int) ((time % 1000) * 60 / 1000);
			float hour = 360 / 12 * (hours % 12);
			float minute = 360 / 60 * minutes;
			helper.assertTrue(Math.abs(AngleHelper.getShortestAngleDiff(hour, be.hourHand.getValue())) < 15, "hour hand " + be.hourHand.getValue() + " for " + hour + " time " + time + " speed " + be.getSpeed());
			helper.assertTrue(Math.abs(AngleHelper.getShortestAngleDiff(minute, be.minuteHand.getValue())) < 15, "minute hand " + be.minuteHand.getValue() + " for " + minute + " time " + time + " speed " + be.getSpeed());
			helper.assertTrue(be.hourHand.getValue() != 0 || be.minuteHand.getValue() != 0, "hands did not move");
		}));
	}

	@GameTest(maxTicks = 200)
	public void cuckooClockAnimatesAtNoon(GameTestHelper helper) {
		floor(helper);
		motorEast(helper, new BlockPos(1, 1, 1));
		BlockPos clock = new BlockPos(2, 1, 1);
		helper.setBlock(clock, KineticsExtraRegistry.CUCKOO_CLOCK.defaultBlockState().setValue(
			net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		helper.runAfterDelay(3, () -> helper.setTime(6081));
		helper.runAfterDelay(10, () -> helper.assertTrue(helper.getBlockEntity(clock, CuckooClockBlockEntity.class).animationType
			== CuckooClockBlockEntity.Animation.PIG, "no pig yet"));
		helper.runAfterDelay(140, () -> helper.succeedWhen(() -> helper.assertTrue(helper.getBlockEntity(clock, CuckooClockBlockEntity.class).animationType
			== CuckooClockBlockEntity.Animation.NONE, "still animating")));
	}

	@GameTest(maxTicks = 150)
	public void mysteriousCuckooClockExplodes(GameTestHelper helper) {
		floor(helper);
		motorEast(helper, new BlockPos(1, 1, 1));
		BlockPos clock = new BlockPos(2, 1, 1);
		helper.setBlock(clock, KineticsExtraRegistry.MYSTERIOUS_CUCKOO_CLOCK.defaultBlockState().setValue(
			net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		helper.runAfterDelay(3, () -> helper.setTime(6000));
		helper.succeedWhen(() -> helper.assertBlockNotPresent(KineticsExtraRegistry.MYSTERIOUS_CUCKOO_CLOCK, clock));
	}

	// ---- casings ----

	@GameTest(maxTicks = 60)
	public void brassCasingEncasesShaftAndKeepsItTurning(GameTestHelper helper) {
		floor(helper);
		motorEast(helper, new BlockPos(1, 1, 1));
		shaftX(helper, 2, 1, 1);
		shaftX(helper, 3, 1, 1);
		helper.runAfterDelay(2, () -> use(helper, new BlockPos(2, 1, 1), player(helper, KineticsExtraRegistry.BRASS_CASING_ITEM)));
		helper.runAfterDelay(6, () -> helper.succeedWhen(() -> {
			helper.assertBlockPresent(KineticsExtraRegistry.BRASS_ENCASED_SHAFT, new BlockPos(2, 1, 1));
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 1, 1)).getValue(RotatedPillarKineticBlock.AXIS) == Axis.X, "axis");
			assertSpeed(helper, new BlockPos(2, 1, 1), 16);
			assertSpeed(helper, new BlockPos(3, 1, 1), 16);
		}));
	}

	@GameTest(maxTicks = 60)
	public void brassCasingEncasesCogwheels(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(1, 1, 1), ModBlocks.COGWHEEL.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.Y));
		helper.setBlock(new BlockPos(4, 1, 4), ModBlocks.LARGE_COGWHEEL.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.Y));
		helper.runAfterDelay(2, () -> {
			use(helper, new BlockPos(1, 1, 1), player(helper, KineticsExtraRegistry.BRASS_CASING_ITEM));
			use(helper, new BlockPos(4, 1, 4), player(helper, KineticsExtraRegistry.BRASS_CASING_ITEM));
		});
		helper.runAfterDelay(4, () -> helper.succeedWhen(() -> {
			helper.assertBlockPresent(KineticsExtraRegistry.BRASS_ENCASED_COGWHEEL, new BlockPos(1, 1, 1));
			helper.assertBlockPresent(KineticsExtraRegistry.BRASS_ENCASED_LARGE_COGWHEEL, new BlockPos(4, 1, 4));
			helper.assertTrue(helper.getBlockState(new BlockPos(1, 1, 1)).getValue(RotatedPillarKineticBlock.AXIS) == Axis.Y, "axis");
			helper.assertTrue(!helper.getBlockState(new BlockPos(1, 1, 1)).getValue(EncasedCogwheelBlock.TOP_SHAFT), "top shaft");
		}));
	}

	@GameTest(maxTicks = 40)
	public void sneakingDoesNotEncase(GameTestHelper helper) {
		floor(helper);
		shaftX(helper, 2, 1, 1);
		helper.runAfterDelay(2, () -> {
			Player player = player(helper, KineticsExtraRegistry.BRASS_CASING_ITEM);
			player.setShiftKeyDown(true);
			use(helper, new BlockPos(2, 1, 1), player);
		});
		helper.runAfterDelay(6, () -> helper.succeedWhen(() -> helper.assertBlockPresent(ModBlocks.SHAFT, new BlockPos(2, 1, 1))));
	}

	@GameTest(maxTicks = 40)
	public void copperIngotTurnsStrippedLogIntoCopperCasing(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(2, 1, 1), Blocks.STRIPPED_OAK_LOG);
		Player player = player(helper, Items.COPPER_INGOT);
		helper.runAfterDelay(2, () -> use(helper, new BlockPos(2, 1, 1), player));
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(KineticsExtraRegistry.COPPER_CASING, new BlockPos(2, 1, 1));
			helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(), "ingot not used up");
		});
	}

	@GameTest(maxTicks = 20)
	public void casingsAreRegistered(GameTestHelper helper) {
		helper.assertTrue(KineticsExtraRegistry.BRASS_CASING.defaultBlockState().getBlock() != KineticsExtraRegistry.COPPER_CASING, "same block");
		helper.assertTrue(KineticsExtraRegistry.DYED_VALVE_HANDLES.size() == 16, "dyed handles");
		helper.assertTrue(KineticsExtraRegistry.DYED_VALVE_HANDLE_ITEMS.size() == 16, "dyed handle items");
		helper.succeed();
	}
}
