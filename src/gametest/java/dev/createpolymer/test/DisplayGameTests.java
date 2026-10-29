package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import java.util.List;

import dev.createpolymer.api.behaviour.display.DisplaySource;
import dev.createpolymer.api.behaviour.display.DisplayTarget;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.kinetics.base.DirectionalAxisKineticBlock;
import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.base.RotatedPillarKineticBlock;
import dev.createpolymer.kinetics.motor.CreativeMotorBlockEntity;
import dev.createpolymer.redstone.displayLink.ClickToLinkBlockItem.ClickToLinkData;
import dev.createpolymer.redstone.displayLink.DisplayLinkBlock;
import dev.createpolymer.redstone.displayLink.DisplayLinkBlockEntity;
import dev.createpolymer.redstone.displayLink.DisplayLinkGui;
import dev.createpolymer.redstone.displayLink.DisplayText;
import dev.createpolymer.redstone.nixieTube.DoubleFaceAttachedBlock.DoubleAttachFace;
import dev.createpolymer.redstone.nixieTube.NixieTubeBlock;
import dev.createpolymer.redstone.nixieTube.NixieTubeBlockEntity;
import dev.createpolymer.redstone.smartObserver.SmartObserverBlock;
import dev.createpolymer.redstone.smartObserver.SmartObserverBlockEntity;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.feature.DisplayRegistry;
import dev.createpolymer.registry.feature.KineticsExtraRegistry;
import dev.createpolymer.registry.feature.RedstoneRegistry;
import dev.createpolymer.registry.feature.TanksRegistry;
import dev.createpolymer.foundation.fluid.FluidHandlerLookup;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.IFluidHandler.FluidAction;
import net.minecraft.world.level.material.Fluids;
import dev.createpolymer.trains.display.FlapDisplayBlock;
import dev.createpolymer.trains.display.FlapDisplayBlockEntity;
import dev.createpolymer.trains.display.FlapDisplaySection;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import eu.pb4.sgui.api.ClickType;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class DisplayGameTests {

	private static final BlockPos MOTOR = new BlockPos(1, 1, 1);

	private static void motorEast(GameTestHelper helper, int rpm) {
		helper.setBlock(MOTOR, ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
		helper.runAfterDelay(2, () -> helper.getBlockEntity(MOTOR, CreativeMotorBlockEntity.class).setSetting(rpm));
	}

	private static CompoundTag config(String source) {
		CompoundTag tag = new CompoundTag();
		tag.putString("Id", "create:" + source);
		return tag;
	}

	private static BlockPos linkOnTop(GameTestHelper helper, BlockPos source, BlockPos target) {
		BlockPos link = source.above();
		helper.setBlock(link, DisplayRegistry.DISPLAY_LINK.defaultBlockState().setValue(DisplayLinkBlock.FACING, Direction.UP));
		helper.getBlockEntity(link, DisplayLinkBlockEntity.class).target(helper.absolutePos(target));
		return link;
	}

	private static String signLine(GameTestHelper helper, BlockPos sign, int line) {
		return helper.getBlockEntity(sign, SignBlockEntity.class)
			.getText(SignTextSlot.FRONT)
			.getMessages(false)
			.get(line)
			.getString();
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = FakePlayer.get(helper.getLevel());
		player.setShiftKeyDown(false);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		return player;
	}

	// ---- registry ----

	@GameTest
	public void sourcesAndTargetsAreOfferedForTheirBlocks(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(1, 1, 1), ModBlocks.SPEEDOMETER);
		helper.setBlock(new BlockPos(2, 1, 1), KineticsExtraRegistry.CUCKOO_CLOCK);
		helper.setBlock(new BlockPos(3, 1, 1), RedstoneRegistry.SMART_OBSERVER);
		helper.setBlock(new BlockPos(4, 1, 1), Blocks.OAK_SIGN);
		helper.setBlock(new BlockPos(5, 1, 1), DisplayRegistry.DISPLAY_BOARD);
		helper.setBlock(new BlockPos(6, 1, 1), Blocks.TARGET);
		helper.runAfterDelay(2, () -> {
			var level = helper.getLevel();
			helper.assertTrue(DisplaySource.getAll(level, helper.absolutePos(new BlockPos(1, 1, 1))).equals(List.of(DisplayRegistry.KINETIC_SPEED)), "speedometer");
			helper.assertTrue(DisplaySource.getAll(level, helper.absolutePos(new BlockPos(2, 1, 1))).equals(List.of(DisplayRegistry.TIME_OF_DAY, DisplayRegistry.STOPWATCH)), "cuckoo clock");
			helper.assertTrue(DisplaySource.getAll(level, helper.absolutePos(new BlockPos(3, 1, 1))).size() == 5, "smart observer");
			helper.assertTrue(DisplaySource.getAll(level, helper.absolutePos(new BlockPos(6, 1, 1))).equals(List.of(DisplayRegistry.REDSTONE_POWER)), "target block");
			helper.assertTrue(DisplaySource.getAll(level, helper.absolutePos(new BlockPos(4, 1, 1))).isEmpty(), "sign is not a source");
			helper.assertTrue(DisplayTarget.get(level, helper.absolutePos(new BlockPos(4, 1, 1))) == DisplayRegistry.SIGN, "sign target");
			helper.assertTrue(DisplayTarget.get(level, helper.absolutePos(new BlockPos(5, 1, 1))) == DisplayRegistry.DISPLAY_BOARD_TARGET, "board target");
			helper.assertTrue(DisplayTarget.get(level, helper.absolutePos(new BlockPos(1, 1, 1))) == null, "speedometer is no target");
			helper.assertTrue(DisplaySource.get(net.minecraft.resources.Identifier.parse("create:cuckoo_clock_source_time_of_day")) == DisplayRegistry.TIME_OF_DAY, "legacy name");
			helper.succeed();
		});
	}

	@GameTest
	public void translatableTextIsResolvedForTheServer(GameTestHelper helper) {
		String text = DisplayText.resolve(Component.translatable("create.display_source.scoreboard.objective_not_found", "kills")).getString();
		helper.assertTrue(text.equals("'kills' not found"), text);
		helper.assertTrue(DisplayText.resolve(Component.translatable("create.generic.daytime.pm")).getString().equals("pm"), "no daytime");
		helper.succeed();
	}

	// ---- kinetic speed to a sign ----

	@GameTest(maxTicks = 80)
	public void displayLinkWritesTheSpeedOnASign(GameTestHelper helper) {
		floor(helper);
		motorEast(helper, 64);
		BlockPos gauge = new BlockPos(2, 1, 1);
		helper.setBlock(gauge, ModBlocks.SPEEDOMETER.defaultBlockState()
			.setValue(DirectionalKineticBlock.FACING, Direction.UP).setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, true));
		helper.setBlock(new BlockPos(3, 1, 1), ModBlocks.SHAFT.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.X));
		BlockPos sign = new BlockPos(2, 1, 4);
		helper.setBlock(sign, Blocks.OAK_SIGN);
		BlockPos link = linkOnTop(helper, gauge, sign);

		helper.runAfterDelay(10, () -> {
			DisplayLinkBlockEntity be = helper.getBlockEntity(link, DisplayLinkBlockEntity.class);
			CompoundTag data = config("kinetic_speed");
			data.putString("Label", "Speed");
			be.applySettings(data, 1);
			helper.assertTrue(be.activeSource == DisplayRegistry.KINETIC_SPEED, "source " + be.activeSource);
			helper.assertTrue(be.activeTarget == DisplayRegistry.SIGN, "target " + be.activeTarget);
			helper.assertTrue(signLine(helper, sign, 0).isEmpty(), "line 0 written: " + signLine(helper, sign, 0));
			helper.assertTrue(signLine(helper, sign, 1).equals("Speed 64 RPM"), "line 1: " + signLine(helper, sign, 1));
			// the sign is changed on both sides
			String back = helper.getBlockEntity(sign, SignBlockEntity.class).getText(SignTextSlot.BACK).getMessages(false).get(1).getString();
			helper.assertTrue(back.equals("Speed 64 RPM"), "back: " + back);
			helper.assertTrue(be.getGlow(1) > 1, "the bulb did not light up: " + be.getGlow(1));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 300)
	public void displayLinkUpdatesPeriodicallyAndStopsWhenPowered(GameTestHelper helper) {
		floor(helper);
		motorEast(helper, 32);
		BlockPos gauge = new BlockPos(2, 1, 1);
		helper.setBlock(gauge, ModBlocks.SPEEDOMETER.defaultBlockState()
			.setValue(DirectionalKineticBlock.FACING, Direction.UP).setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, true));
		BlockPos sign = new BlockPos(2, 1, 4);
		helper.setBlock(sign, Blocks.OAK_SIGN);
		BlockPos link = linkOnTop(helper, gauge, sign);

		helper.runAfterDelay(10, () -> {
			helper.getBlockEntity(link, DisplayLinkBlockEntity.class).applySettings(config("kinetic_speed"), 0);
			helper.assertTrue(signLine(helper, sign, 0).equals("32 RPM"), "first: " + signLine(helper, sign, 0));
			helper.getBlockEntity(MOTOR, CreativeMotorBlockEntity.class).setSetting(64);
		});
		// Create's passive refresh: every 100 ticks
		helper.runAfterDelay(30, () -> helper.assertTrue(signLine(helper, sign, 0).equals("32 RPM"), "updated too early: " + signLine(helper, sign, 0)));
		helper.runAfterDelay(125, () -> {
			helper.assertTrue(signLine(helper, sign, 0).equals("64 RPM"), "after the interval: " + signLine(helper, sign, 0));
			// powered links do not update
			helper.setBlock(link.north(), Blocks.REDSTONE_BLOCK);
			helper.getBlockEntity(MOTOR, CreativeMotorBlockEntity.class).setSetting(16);
		});
		helper.runAfterDelay(135, () -> helper.assertTrue(helper.getBlockState(link).getValue(DisplayLinkBlock.POWERED), "not powered"));
		helper.runAfterDelay(250, () -> {
			helper.assertTrue(signLine(helper, sign, 0).equals("64 RPM"), "updated while powered: " + signLine(helper, sign, 0));
			helper.succeed();
		});
	}

	// ---- time of day to a nixie tube ----

	@GameTest(maxTicks = 120)
	public void timeOfDayGoesToANixieTube(GameTestHelper helper) {
		floor(helper);
		motorEast(helper, 16);
		BlockPos clock = new BlockPos(2, 1, 1);
		helper.setBlock(clock, KineticsExtraRegistry.CUCKOO_CLOCK.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		BlockPos nixie = new BlockPos(5, 1, 4);
		helper.setBlock(nixie, RedstoneRegistry.ORANGE_NIXIE_TUBE.defaultBlockState()
			.setValue(NixieTubeBlock.FACE, DoubleAttachFace.FLOOR)
			.setValue(NixieTubeBlock.FACING, Direction.NORTH));
		BlockPos link = linkOnTop(helper, clock, nixie);
		helper.setTime(9500);

		helper.runAfterDelay(30, () -> {
			DisplayLinkBlockEntity be = helper.getBlockEntity(link, DisplayLinkBlockEntity.class);
			CompoundTag data = config("time_of_day");
			data.putInt("Cycle", 1);
			be.applySettings(data, 0);
			NixieTubeBlockEntity tube = helper.getBlockEntity(nixie, NixieTubeBlockEntity.class);
			long time = helper.getLevel().getDefaultClockTime() % 24000;
			int hours = (int) ((time / 1000 + 6) % 24);
			int minutes = (int) ((time % 1000) * 60 / 1000) / 5 * 5;
			String expected = (hours < 10 ? " " : "") + hours + ":" + (minutes < 10 ? "0" : "") + minutes;
			helper.assertTrue(tube.getFullText().getString().equals(expected), "nixie text '" + tube.getFullText().getString() + "', expected '" + expected + "'");
			helper.assertTrue(!tube.reactsToRedstone(), "the nixie still shows redstone");

			// 12 hour clock, with the suffix of Create's language file
			CompoundTag twelve = config("time_of_day");
			twelve.putInt("Cycle", 0);
			be.applySettings(twelve, 0);
			String text = tube.getFullText().getString();
			helper.assertTrue(text.matches("\\s?\\d{1,2}:\\d\\d [ap]m"), "12 hour text '" + text + "'");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void stopwatchStartsWhenTheLinkLosesItsSignal(GameTestHelper helper) {
		floor(helper);
		motorEast(helper, 16);
		BlockPos clock = new BlockPos(2, 1, 1);
		helper.setBlock(clock, KineticsExtraRegistry.CUCKOO_CLOCK.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		BlockPos sign = new BlockPos(2, 1, 4);
		helper.setBlock(sign, Blocks.OAK_SIGN);
		BlockPos link = linkOnTop(helper, clock, sign);
		helper.runAfterDelay(10, () -> {
			DisplayLinkBlockEntity be = helper.getBlockEntity(link, DisplayLinkBlockEntity.class);
			be.applySettings(config("stopwatch"), 0);
			helper.assertTrue(signLine(helper, sign, 0).equals(" 0:00"), "stopwatch '" + signLine(helper, sign, 0) + "'");
			helper.succeed();
		});
	}

	// ---- item count to a display board ----

	@GameTest(maxTicks = 200)
	public void itemCountGoesToADisplayBoard(GameTestHelper helper) {
		floor(helper);
		// a motor turns the two display boards through two shafts behind them
		BlockPos motor = new BlockPos(5, 3, 6);
		poweredCog(helper, motor);
		BlockPos controllerPos = new BlockPos(4, 3, 2);
		BlockPos otherPos = new BlockPos(3, 3, 2);
		for (BlockPos pos : List.of(controllerPos, otherPos))
			helper.setBlock(pos, DisplayRegistry.DISPLAY_BOARD.defaultBlockState().setValue(FlapDisplayBlock.HORIZONTAL_FACING, Direction.NORTH));
		helper.runAfterDelay(2, () -> helper.getBlockEntity(motor, CreativeMotorBlockEntity.class).setSetting(64));

		BlockPos chest = new BlockPos(6, 1, 2);
		BlockPos observer = chest.above();
		helper.setBlock(chest, Blocks.CHEST);
		helper.setBlock(observer, RedstoneRegistry.SMART_OBSERVER.defaultBlockState()
			.setValue(SmartObserverBlock.TARGET, AttachFace.FLOOR)
			.setValue(SmartObserverBlock.FACING, Direction.NORTH));
		helper.runAfterDelay(3, () -> {
			helper.getBlockEntity(chest, ChestBlockEntity.class).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
			helper.getBlockEntity(chest, ChestBlockEntity.class).setItem(5, new ItemStack(Items.DIAMOND, 3));
			helper.getBlockEntity(chest, ChestBlockEntity.class).setItem(9, new ItemStack(Items.COBBLESTONE, 6));
		});
		BlockPos link = observer.east();
		helper.setBlock(link, DisplayRegistry.DISPLAY_LINK.defaultBlockState().setValue(DisplayLinkBlock.FACING, Direction.EAST));
		// the link points at the left block of the board, not at its controller
		helper.getBlockEntity(link, DisplayLinkBlockEntity.class).target(helper.absolutePos(otherPos));

		helper.runAfterDelay(40, () -> {
			FlapDisplayBlockEntity controller = helper.getBlockEntity(controllerPos, FlapDisplayBlockEntity.class);
			helper.assertTrue(controller.isController && controller.xSize == 2 && controller.ySize == 1,
				"controller " + controller.isController + " size " + controller.xSize + "x" + controller.ySize);
			helper.assertTrue(!helper.getBlockEntity(otherPos, FlapDisplayBlockEntity.class).isController, "two controllers");
			helper.assertTrue(controller.isSpeedRequirementFulfilled(), "the board does not turn: " + controller.getSpeed());
			helper.assertTrue(controller.getController() == controller && helper.getBlockEntity(otherPos, FlapDisplayBlockEntity.class).getController() == controller, "controller lookup");

			DisplayLinkBlockEntity be = helper.getBlockEntity(link, DisplayLinkBlockEntity.class);
			helper.assertTrue(be.getSourcePosition().equals(helper.absolutePos(observer)), "source " + be.getSourcePosition());
			be.applySettings(config("count_items"), 0);
			helper.assertTrue(be.activeSource == DisplayRegistry.COUNT_ITEMS && be.activeTarget == DisplayRegistry.DISPLAY_BOARD_TARGET, "link " + be.activeSource + " " + be.activeTarget);

			FlapDisplaySection flaps = controller.getLines().get(0).getSections().get(0);
			helper.assertTrue(flaps.isSpinning(0) && flaps.isSpinning(1), "the flaps do not turn for the new text");
			String shown = lineText(controller, 0);
			helper.assertTrue(shown.trim().equals("73"), "board shows '" + shown + "'");
			helper.assertTrue(lineText(controller, 1).isBlank(), "second line written: " + lineText(controller, 1));
			helper.assertTrue(controller.getLines().get(0).isLayout("Number"), "layout");
		});

		// the flaps turn for a moment (they are flagged as soon as the text is set), and each shown character is a
		// text display on the controller's model
		boolean[] sawText = { false };
		helper.onEachTick(() -> {
			if (helper.getTick() > 41 && helper.getTick() < 60 && textDisplays(helper, controllerPos) >= 1)
				sawText[0] = true;
		});
		helper.runAfterDelay(65, () -> helper.assertTrue(sawText[0], "no text displayed while flipping"));
		helper.runAfterDelay(140, () -> {
			FlapDisplayBlockEntity controller = helper.getBlockEntity(controllerPos, FlapDisplayBlockEntity.class);
			FlapDisplaySection section = controller.getLines().get(0).getSections().get(0);
			for (int i = 0; i < section.flapCount(); i++)
				helper.assertTrue(!section.isSpinning(i), "flap " + i + " still flipping");
			helper.assertTrue(textDisplays(helper, controllerPos) == 2, "text displays: " + textDisplays(helper, controllerPos));
			helper.assertTrue(textDisplays(helper, otherPos) == 0, "text on a block that is not the controller");

			// the characters stand in reading order on the face of the board: seen from the north the first one is
			// the east-most, and all of them are in front (north) of the block centre
			TextDisplayElement seven = null, three = null;
			for (VirtualElement element : BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(controllerPos)).holder().getElements())
				if (element instanceof TextDisplayElement text) {
					if (text.getText().getString().equals("7"))
						seven = text;
					if (text.getText().getString().equals("3"))
						three = text;
				}
			helper.assertTrue(seven != null && three != null, "no 7 and 3");
			helper.assertTrue(seven.getTranslation().x() > three.getTranslation().x(), "reading order: " + seven.getTranslation() + " " + three.getTranslation());
			helper.assertTrue(seven.getTranslation().z() < -.3 && Math.abs(seven.getTranslation().z() - three.getTranslation().z()) < 1e-4, "depth " + seven.getTranslation());
			helper.assertTrue(three.getTranslation().y() > 0 && three.getTranslation().y() < .5, "height " + three.getTranslation());
			helper.succeed();
		});
	}

	/** A motor and two shafts that turn a small cogwheel right next to the controller of a board at (4, 3, 2). */
	private static void poweredCog(GameTestHelper helper, BlockPos motor) {
		helper.setBlock(motor, ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.NORTH));
		helper.setBlock(motor.north(), ModBlocks.SHAFT.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.Z));
		helper.setBlock(motor.north(2), ModBlocks.SHAFT.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.Z));
		helper.setBlock(motor.north(3), ModBlocks.SHAFT.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.Z));
		helper.setBlock(motor.north(4), ModBlocks.COGWHEEL.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.Z));
	}

	private static String lineText(FlapDisplayBlockEntity controller, int line) {
		StringBuilder builder = new StringBuilder();
		for (FlapDisplaySection section : controller.getLines().get(line).getSections())
			builder.append(section.getShownText());
		return builder.toString();
	}

	private static int textDisplays(GameTestHelper helper, BlockPos pos) {
		BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(pos));
		helper.assertTrue(attachment != null, "no model at " + pos);
		int count = 0;
		for (VirtualElement element : attachment.holder().getElements())
			if (element instanceof TextDisplayElement)
				count++;
		return count;
	}

	@GameTest(maxTicks = 120)
	public void displayBoardKeepsItsTextAndLabelsAndNeedsSpeed(GameTestHelper helper) {
		floor(helper);
		BlockPos motor = new BlockPos(5, 3, 6);
		poweredCog(helper, motor);
		BlockPos board = new BlockPos(4, 3, 2);
		helper.setBlock(board, DisplayRegistry.DISPLAY_BOARD.defaultBlockState().setValue(FlapDisplayBlock.HORIZONTAL_FACING, Direction.NORTH));
		helper.runAfterDelay(2, () -> helper.getBlockEntity(motor, CreativeMotorBlockEntity.class).setSetting(8));

		BlockPos scoreboardSource = new BlockPos(1, 3, 2);
		helper.setBlock(scoreboardSource, Blocks.TARGET);
		BlockPos link = new BlockPos(1, 4, 2);
		helper.setBlock(link, DisplayRegistry.DISPLAY_LINK.defaultBlockState().setValue(DisplayLinkBlock.FACING, Direction.UP));
		helper.getBlockEntity(link, DisplayLinkBlockEntity.class).target(helper.absolutePos(board));

		helper.runAfterDelay(20, () -> {
			FlapDisplayBlockEntity be = helper.getBlockEntity(board, FlapDisplayBlockEntity.class);
			helper.assertTrue(!be.isSpeedRequirementFulfilled(), "turns fast enough at " + be.getSpeed());
			CompoundTag data = config("redstone_power");
			data.putString("Label", "Pw");
			helper.getBlockEntity(link, DisplayLinkBlockEntity.class).applySettings(data, 1);
			helper.assertTrue(lineText(be, 1).isBlank(), "a board that is too slow was written: " + lineText(be, 1));
			helper.getBlockEntity(motor, CreativeMotorBlockEntity.class).setSetting(64);
		});
		helper.runAfterDelay(40, () -> {
			FlapDisplayBlockEntity be = helper.getBlockEntity(board, FlapDisplayBlockEntity.class);
			helper.assertTrue(be.isSpeedRequirementFulfilled(), "does not turn: " + be.getSpeed());
			helper.getBlockEntity(link, DisplayLinkBlockEntity.class).updateGatheredData();
			helper.assertTrue(lineText(be, 1).startsWith("PW 0"), "label and value '" + lineText(be, 1) + "'");

			// colour and glow ink, applied to the line that was hit
			be.setColour(1, net.minecraft.world.item.DyeColor.RED);
			be.setGlowing(1);
			helper.assertTrue(be.getLineColor(1) == (0xB13937 | 0xFF000000), "colour " + Integer.toHexString(be.getLineColor(1)));
			helper.assertTrue(be.isLineGlowing(1) && !be.isLineGlowing(0), "glow");

			// a name tag writes a line by hand, and the link leaves that line alone
			be.applyTextManually(0, Component.literal("Hi"));
			helper.assertTrue(lineText(be, 0).startsWith("HI"), "hand written '" + lineText(be, 0) + "'");
			helper.assertTrue(DisplayRegistry.DISPLAY_BOARD_TARGET.isReserved(0, be, new dev.createpolymer.redstone.displayLink.DisplayLinkContext(helper.getLevel(),
				helper.getBlockEntity(link, DisplayLinkBlockEntity.class))), "manual line not reserved");

			// saving keeps text, colours and flags
			var registries = helper.getLevel().registryAccess();
			TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
			be.saveWithFullMetadata(out);
			BlockEntity loaded = BlockEntity.loadStatic(be.getBlockPos(), be.getBlockState(), out.buildResult(), registries);
			helper.assertTrue(loaded instanceof FlapDisplayBlockEntity, "did not load");
			FlapDisplayBlockEntity copy = (FlapDisplayBlockEntity) loaded;
			helper.assertTrue(copy.isController && copy.xSize == 1 && copy.ySize == 1, "size");
			helper.assertTrue(lineText(copy, 0).startsWith("HI") && lineText(copy, 1).startsWith("PW 0"), "texts '" + lineText(copy, 0) + "' '" + lineText(copy, 1) + "'");
			helper.assertTrue(copy.getLineColor(1) == be.getLineColor(1) && copy.isLineGlowing(1) && copy.manualLines[0], "colour, glow or manual flag lost");
			helper.assertTrue(copy.getLines().get(1).isLayout(be.getLines().get(1).isLayout("Number") ? "Number" : "Progress")
				|| copy.getLines().get(1).getSections().size() == be.getLines().get(1).getSections().size(), "layout");
			helper.succeed();
		});
	}

	// ---- saving ----

	@GameTest(maxTicks = 60)
	public void linkConfigurationSurvivesSaving(GameTestHelper helper) {
		floor(helper);
		BlockPos source = new BlockPos(2, 1, 1);
		helper.setBlock(source, KineticsExtraRegistry.CUCKOO_CLOCK.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		BlockPos sign = new BlockPos(5, 1, 4);
		helper.setBlock(sign, Blocks.OAK_SIGN);
		BlockPos link = linkOnTop(helper, source, sign);
		helper.runAfterDelay(5, () -> {
			DisplayLinkBlockEntity be = helper.getBlockEntity(link, DisplayLinkBlockEntity.class);
			CompoundTag data = config("time_of_day");
			data.putInt("Cycle", 1);
			data.putString("Label", "Time");
			be.applySettings(data, 2);

			var registries = helper.getLevel().registryAccess();
			TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
			be.saveWithFullMetadata(out);
			BlockEntity loaded = BlockEntity.loadStatic(be.getBlockPos(), be.getBlockState(), out.buildResult(), registries);
			helper.assertTrue(loaded instanceof DisplayLinkBlockEntity, "the link did not load");
			DisplayLinkBlockEntity copy = (DisplayLinkBlockEntity) loaded;
			helper.assertTrue(copy.activeSource == DisplayRegistry.TIME_OF_DAY, "source " + copy.activeSource);
			helper.assertTrue(copy.targetLine == 2, "line " + copy.targetLine);
			helper.assertTrue(copy.getTargetPosition().equals(be.getTargetPosition()), "target " + copy.getTargetPosition() + " instead of " + be.getTargetPosition());
			helper.assertTrue(copy.getSourceConfig().getIntOr("Cycle", 0) == 1, "cycle");
			helper.assertTrue(copy.getSourceConfig().getStringOr("Label", "").equals("Time"), "label");
			helper.succeed();
		});
	}

	// ---- configuration screen ----

	@GameTest(maxTicks = 60)
	public void configurationGuiChoosesSourceOptionsAndLine(GameTestHelper helper) {
		floor(helper);
		BlockPos clock = new BlockPos(2, 1, 1);
		helper.setBlock(clock, KineticsExtraRegistry.CUCKOO_CLOCK.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		BlockPos sign = new BlockPos(5, 1, 4);
		helper.setBlock(sign, Blocks.OAK_SIGN);
		BlockPos linkPos = linkOnTop(helper, clock, sign);
		helper.runAfterDelay(3, () -> {
			DisplayLinkBlockEntity be = helper.getBlockEntity(linkPos, DisplayLinkBlockEntity.class);
			DisplayLinkGui gui = new DisplayLinkGui(player(helper), be);
			// the first source is time of day with a text and a 12/24 hour option; the stopwatch has a text only
			helper.assertTrue(gui.getGuiElement(3) != null && gui.getGuiElement(27 + 3) != null && gui.getGuiElement(9 + 3) != null && gui.getGuiElement(18 + 3) != null,
				"missing buttons");
			click(gui, 18 + 3, ClickType.MOUSE_LEFT);
			helper.assertTrue(be.activeSource == DisplayRegistry.TIME_OF_DAY, "source " + be.activeSource);
			helper.assertTrue(be.getSourceConfig().getIntOr("Cycle", 0) == 1, "cycle " + be.getSourceConfig());
			click(gui, 18 + 3, ClickType.MOUSE_RIGHT);
			helper.assertTrue(be.getSourceConfig().getIntOr("Cycle", -1) == 0, "cycle back " + be.getSourceConfig());

			click(gui, 27 + 3, ClickType.MOUSE_LEFT);
			click(gui, 27 + 3, ClickType.MOUSE_LEFT);
			helper.assertTrue(be.targetLine == 2, "line " + be.targetLine);
			click(gui, 27 + 3, ClickType.MOUSE_RIGHT);
			click(gui, 27 + 3, ClickType.MOUSE_RIGHT);
			click(gui, 27 + 3, ClickType.MOUSE_RIGHT);
			helper.assertTrue(be.targetLine == 3, "wrapped line " + be.targetLine);

			click(gui, 3, ClickType.MOUSE_LEFT);
			helper.assertTrue(be.activeSource == DisplayRegistry.STOPWATCH, "source " + be.activeSource);
			helper.assertTrue(be.getSourceConfig().getIntOr("Cycle", -1) == -1, "options of the other source stayed: " + be.getSourceConfig());
			helper.succeed();
		});
	}

	private static void click(DisplayLinkGui gui, int slot, ClickType type) {
		gui.getGuiElement(slot).getGuiCallback().click(slot, type, ContainerInput.PICKUP, gui);
	}

	@GameTest(maxTicks = 60)
	public void guiOfALinkWithoutTargetIsRefused(GameTestHelper helper) {
		floor(helper);
		BlockPos link = new BlockPos(2, 1, 1);
		helper.setBlock(link, DisplayRegistry.DISPLAY_LINK.defaultBlockState().setValue(DisplayLinkBlock.FACING, Direction.UP));
		helper.runAfterDelay(2, () -> {
			ServerPlayer player = player(helper);
			BlockPos abs = helper.absolutePos(link);
			InteractionResult result = helper.getLevel().getBlockState(abs).useWithoutItem(helper.getLevel(), player,
				new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
			helper.assertTrue(result.consumesAction(), "result " + result);
			helper.assertTrue(player.containerMenu == player.inventoryMenu, "a menu was opened for a link without a target");
			helper.succeed();
		});
	}

	// ---- placing a link with the item ----

	@GameTest(maxTicks = 60)
	public void linkItemSelectsATargetAndPlacesTheLink(GameTestHelper helper) {
		floor(helper);
		BlockPos sign = new BlockPos(5, 1, 5);
		BlockPos gauge = new BlockPos(2, 1, 2);
		helper.setBlock(sign, Blocks.OAK_SIGN);
		helper.setBlock(gauge, ModBlocks.SPEEDOMETER);
		helper.runAfterDelay(2, () -> {
			ServerPlayer player = player(helper);
			ItemStack stack = new ItemStack(DisplayRegistry.DISPLAY_LINK_ITEM);
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);

			// the first click selects the sign
			BlockPos signAbs = helper.absolutePos(sign);
			InteractionResult selected = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(signAbs), Direction.UP, signAbs, false)));
			helper.assertTrue(selected.consumesAction(), "selecting: " + selected);
			ClickToLinkData data = stack.get(DisplayRegistry.CLICK_TO_LINK_DATA);
			helper.assertTrue(data != null && data.selectedPos().equals(signAbs), "selection " + data);
			helper.assertTrue(helper.getLevel().getBlockState(signAbs.above()).isAir(), "placed on the first click");

			// the second click places the link on top of the speedometer
			BlockPos gaugeAbs = helper.absolutePos(gauge);
			InteractionResult placed = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(gaugeAbs).add(0, .5, 0), Direction.UP, gaugeAbs, false)));
			helper.assertTrue(placed.consumesAction(), "placing: " + placed);
			helper.assertTrue(stack.get(DisplayRegistry.CLICK_TO_LINK_DATA) == null, "selection kept");
			DisplayLinkBlockEntity link = helper.getBlockEntity(gauge.above(), DisplayLinkBlockEntity.class);
			helper.assertTrue(link.getTargetPosition().equals(signAbs), "target " + link.getTargetPosition() + " instead of " + signAbs);
			helper.assertTrue(link.getSourcePosition().equals(gaugeAbs), "source " + link.getSourcePosition());
			helper.assertTrue(helper.getBlockState(gauge.above()).getValue(DisplayLinkBlock.FACING) == Direction.UP, "facing");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void linkItemRefusesTargetsThatAreTooFarAndClearsWhenSneaking(GameTestHelper helper) {
		floor(helper);
		BlockPos target = new BlockPos(1, 1, 1);
		helper.setBlock(target, Blocks.OAK_SIGN);
		helper.runAfterDelay(2, () -> {
			ServerPlayer player = player(helper);
			ItemStack stack = new ItemStack(DisplayRegistry.DISPLAY_LINK_ITEM);
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			BlockPos abs = helper.absolutePos(target);
			// a selection from far away
			stack.set(DisplayRegistry.CLICK_TO_LINK_DATA, new ClickToLinkData(abs.offset(500, 0, 0), helper.getLevel().dimension().identifier()));
			InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));
			helper.assertTrue(result == InteractionResult.FAIL, "result " + result);
			helper.assertTrue(helper.getLevel().getBlockState(abs.above()).isAir(), "placed too far away");
			helper.assertTrue(stack.get(DisplayRegistry.CLICK_TO_LINK_DATA) != null, "selection lost");

			player.setShiftKeyDown(true);
			InteractionResult cleared = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));
			player.setShiftKeyDown(false);
			helper.assertTrue(cleared.consumesAction() && stack.get(DisplayRegistry.CLICK_TO_LINK_DATA) == null, "not cleared");
			helper.succeed();
		});
	}

	// ---- other sources ----

	@GameTest(maxTicks = 60)
	public void observerSourcesListAndSumItemsAndFluids(GameTestHelper helper) {
		floor(helper);
		BlockPos chest = new BlockPos(2, 1, 2);
		BlockPos observer = chest.above();
		helper.setBlock(chest, Blocks.CHEST);
		helper.setBlock(observer, RedstoneRegistry.SMART_OBSERVER.defaultBlockState()
			.setValue(SmartObserverBlock.TARGET, AttachFace.FLOOR)
			.setValue(SmartObserverBlock.FACING, Direction.NORTH));
		BlockPos sign = new BlockPos(5, 1, 5);
		helper.setBlock(sign, Blocks.OAK_SIGN);
		BlockPos link = observer.east();
		helper.setBlock(link, DisplayRegistry.DISPLAY_LINK.defaultBlockState().setValue(DisplayLinkBlock.FACING, Direction.EAST));
		helper.getBlockEntity(link, DisplayLinkBlockEntity.class).target(helper.absolutePos(sign));
		helper.runAfterDelay(3, () -> {
			helper.getBlockEntity(chest, ChestBlockEntity.class).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
			helper.getBlockEntity(chest, ChestBlockEntity.class).setItem(1, new ItemStack(Items.COBBLESTONE, 6));
			helper.getBlockEntity(chest, ChestBlockEntity.class).setItem(2, new ItemStack(Items.DIAMOND, 3));
		});
		helper.runAfterDelay(25, () -> {
			DisplayLinkBlockEntity be = helper.getBlockEntity(link, DisplayLinkBlockEntity.class);
			be.applySettings(config("count_items"), 0);
			helper.assertTrue(signLine(helper, sign, 0).equals("73"), "count '" + signLine(helper, sign, 0) + "'");

			// with a filter
			helper.getBlockEntity(observer, SmartObserverBlockEntity.class).getBehaviour(FilteringBehaviour.TYPE).setFilter(new ItemStack(Items.DIAMOND));
			be.updateGatheredData();
			helper.assertTrue(signLine(helper, sign, 0).equals("3"), "filtered count '" + signLine(helper, sign, 0) + "'");
			helper.getBlockEntity(observer, SmartObserverBlockEntity.class).getBehaviour(FilteringBehaviour.TYPE).setFilter(ItemStack.EMPTY);

			// the list: item name after the count, one item per line, biggest first
			CompoundTag list = config("list_items");
			list.putInt("Format", 1);
			be.applySettings(list, 0);
			helper.assertTrue(signLine(helper, sign, 0).trim().equals("70 Cobblestone"), "list line 0 '" + signLine(helper, sign, 0) + "'");
			helper.assertTrue(signLine(helper, sign, 1).trim().equals("3 Diamond"), "list line 1 '" + signLine(helper, sign, 1) + "'");

			// the shortened format
			list.putInt("Format", 0);
			be.applySettings(list, 0);
			helper.assertTrue(signLine(helper, sign, 0).trim().equals("70 Cobblestone"), "short list '" + signLine(helper, sign, 0) + "'");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void scoreboardSourceListsScoresOnASign(GameTestHelper helper) {
		floor(helper);
		BlockPos source = new BlockPos(2, 1, 2);
		helper.setBlock(source, Blocks.COMMAND_BLOCK);
		BlockPos sign = new BlockPos(5, 1, 5);
		helper.setBlock(sign, Blocks.OAK_SIGN);
		BlockPos link = linkOnTop(helper, source, sign);
		helper.runAfterDelay(3, () -> {
			var scoreboard = helper.getLevel().getScoreboard();
			String name = "display_test_scores";
			if (scoreboard.getObjective(name) == null)
				scoreboard.addObjective(name, net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY, Component.literal("test"),
					net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER, false, null);
			var objective = scoreboard.getObjective(name);
			scoreboard.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly("alice"), objective).set(5);
			scoreboard.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly("bob"), objective).set(12);

			DisplayLinkBlockEntity be = helper.getBlockEntity(link, DisplayLinkBlockEntity.class);
			helper.assertTrue(DisplaySource.getAll(helper.getLevel(), helper.absolutePos(source)).equals(List.of(DisplayRegistry.SCOREBOARD)), "source list");
			CompoundTag data = config("scoreboard");
			data.putString("Objective", name);
			data.putInt("Format", 1);
			be.applySettings(data, 0);
			helper.assertTrue(signLine(helper, sign, 0).trim().equals("bob 12"), "line 0 '" + signLine(helper, sign, 0) + "'");
			helper.assertTrue(signLine(helper, sign, 1).trim().equals("alice 5"), "line 1 '" + signLine(helper, sign, 1) + "'");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void redstonePowerSourceShowsTheSignalAsAProgressBar(GameTestHelper helper) {
		floor(helper);
		BlockPos source = new BlockPos(2, 1, 2);
		helper.setBlock(source, Blocks.TARGET);
		BlockPos sign = new BlockPos(5, 1, 5);
		helper.setBlock(sign, Blocks.OAK_SIGN);
		BlockPos link = linkOnTop(helper, source, sign);
		helper.runAfterDelay(3, () -> {
			DisplayLinkBlockEntity be = helper.getBlockEntity(link, DisplayLinkBlockEntity.class);
			be.applySettings(config("redstone_power"), 0);
			helper.assertTrue(signLine(helper, sign, 0).equals("0"), "off '" + signLine(helper, sign, 0) + "'");
			helper.setBlock(source.west(), Blocks.REDSTONE_BLOCK);
			helper.setBlock(source, Blocks.TARGET);
			CompoundTag bar = config("redstone_power");
			bar.putInt("Mode", 1);
			be.applySettings(bar, 0);
			String line = signLine(helper, sign, 0);
			helper.assertTrue(!line.isEmpty() && line.chars().allMatch(c -> c == '█' || c == '▒'), "bar '" + line + "'");
			helper.succeed();
		});
	}

	// ---- more sources and targets ----

	@GameTest(maxTicks = 60)
	public void fluidSourcesReadTheTankInFrontOfAnObserver(GameTestHelper helper) {
		floor(helper);
		BlockPos tank = new BlockPos(2, 1, 2);
		BlockPos observer = tank.above();
		helper.setBlock(tank, TanksRegistry.FLUID_TANK);
		helper.setBlock(observer, RedstoneRegistry.SMART_OBSERVER.defaultBlockState()
			.setValue(SmartObserverBlock.TARGET, AttachFace.FLOOR)
			.setValue(SmartObserverBlock.FACING, Direction.NORTH));
		BlockPos sign = new BlockPos(5, 1, 5);
		helper.setBlock(sign, Blocks.OAK_SIGN);
		BlockPos link = observer.east();
		helper.setBlock(link, DisplayRegistry.DISPLAY_LINK.defaultBlockState().setValue(DisplayLinkBlock.FACING, Direction.EAST));
		helper.getBlockEntity(link, DisplayLinkBlockEntity.class).target(helper.absolutePos(sign));
		helper.runAfterDelay(3, () -> FluidHandlerLookup.get(helper.getLevel(), helper.absolutePos(tank), null)
			.fill(new FluidStack(Fluids.WATER, 3000), FluidAction.EXECUTE));
		helper.runAfterDelay(10, () -> {
			DisplayLinkBlockEntity be = helper.getBlockEntity(link, DisplayLinkBlockEntity.class);
			be.applySettings(config("count_fluids"), 0);
			helper.assertTrue(signLine(helper, sign, 0).equals("3000 mB"), "amount '" + signLine(helper, sign, 0) + "'");
			CompoundTag list = config("list_fluids");
			list.putInt("Format", 1);
			be.applySettings(list, 0);
			helper.assertTrue(signLine(helper, sign, 0).startsWith("3000mBWater"), "list '" + signLine(helper, sign, 0) + "'");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void boilerStatusGoesToASign(GameTestHelper helper) {
		floor(helper);
		BlockPos tank = new BlockPos(2, 1, 2);
		helper.setBlock(tank, TanksRegistry.FLUID_TANK);
		BlockPos sign = new BlockPos(5, 1, 5);
		helper.setBlock(sign, Blocks.OAK_SIGN);
		BlockPos link = linkOnTop(helper, tank, sign);
		helper.runAfterDelay(5, () -> {
			helper.getBlockEntity(link, DisplayLinkBlockEntity.class).applySettings(config("boiler"), 0);
			helper.assertTrue(signLine(helper, sign, 0).equals("Boiler:  Idle"), "line 0 '" + signLine(helper, sign, 0) + "'");
			helper.assertTrue(signLine(helper, sign, 1).length() > 0, "no size bar");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void lecternReceivesPages(GameTestHelper helper) {
		floor(helper);
		BlockPos source = new BlockPos(2, 1, 2);
		helper.setBlock(source, Blocks.TARGET);
		BlockPos lectern = new BlockPos(5, 1, 5);
		helper.setBlock(lectern, Blocks.LECTERN);
		BlockPos link = linkOnTop(helper, source, lectern);
		helper.runAfterDelay(3, () -> {
			net.minecraft.world.level.block.entity.LecternBlockEntity be =
				helper.getBlockEntity(lectern, net.minecraft.world.level.block.entity.LecternBlockEntity.class);
			be.setBook(new ItemStack(Items.WRITABLE_BOOK));
			CompoundTag data = config("redstone_power");
			data.putString("Label", "Signal");
			helper.getBlockEntity(link, DisplayLinkBlockEntity.class).applySettings(data, 1);
			ItemStack book = be.getBook();
			helper.assertTrue(book.is(Items.WRITTEN_BOOK), "the book was not signed: " + book);
			var pages = book.get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT).pages();
			helper.assertTrue(pages.size() == 2, "pages " + pages.size());
			helper.assertTrue(pages.get(1).raw().getString().equals("Signal 0"), "page 1 '" + pages.get(1).raw().getString() + "'");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void playerDeathsAreTrackedOnAScoreboard(GameTestHelper helper) {
		floor(helper);
		BlockPos source = new BlockPos(2, 1, 2);
		helper.setBlock(source, Blocks.RESPAWN_ANCHOR);
		BlockPos sign = new BlockPos(5, 1, 5);
		helper.setBlock(sign, Blocks.OAK_SIGN);
		BlockPos link = linkOnTop(helper, source, sign);
		helper.runAfterDelay(3, () -> {
			helper.getBlockEntity(link, DisplayLinkBlockEntity.class).applySettings(config("death_count"), 0);
			helper.assertTrue(helper.getLevel().getScoreboard().getObjective("create_auto_deaths") != null, "no deaths objective");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void enchantingTableShowsItsCost(GameTestHelper helper) {
		floor(helper);
		BlockPos table = new BlockPos(3, 1, 3);
		helper.setBlock(table, Blocks.ENCHANTING_TABLE);
		for (int x = -2; x <= 2; x++)
			for (int z = -2; z <= 2; z++)
				if (Math.abs(x) == 2 || Math.abs(z) == 2)
					if (x != 0 && z != 0 || Math.abs(x) + Math.abs(z) == 2)
						helper.setBlock(table.offset(x, 0, z), Blocks.BOOKSHELF);
		BlockPos sign = new BlockPos(6, 1, 6);
		helper.setBlock(sign, Blocks.OAK_SIGN);
		BlockPos link = linkOnTop(helper, table, sign);
		helper.runAfterDelay(5, () -> {
			helper.getBlockEntity(link, DisplayLinkBlockEntity.class).applySettings(config("enchant_power"), 0);
			int cost = Integer.parseInt(signLine(helper, sign, 0));
			helper.assertTrue(cost > 0 && cost <= 30, "cost " + cost);
			helper.succeed();
		});
	}

	// ---- using items on a display board ----

	@GameTest(maxTicks = 80)
	public void boardTakesNameTagsDyesAndGlowInk(GameTestHelper helper) {
		floor(helper);
		BlockPos motor = new BlockPos(5, 3, 6);
		poweredCog(helper, motor);
		BlockPos board = new BlockPos(4, 3, 2);
		helper.setBlock(board, DisplayRegistry.DISPLAY_BOARD.defaultBlockState().setValue(FlapDisplayBlock.HORIZONTAL_FACING, Direction.NORTH));
		helper.runAfterDelay(2, () -> helper.getBlockEntity(motor, CreativeMotorBlockEntity.class).setSetting(64));
		helper.runAfterDelay(30, () -> {
			FlapDisplayBlockEntity be = helper.getBlockEntity(board, FlapDisplayBlockEntity.class);
			helper.assertTrue(be.isSpeedRequirementFulfilled(), "not running: " + be.getSpeed());
			ServerPlayer player = player(helper);
			BlockPos abs = helper.absolutePos(board);
			// the lower half of the block is the second line
			BlockHitResult lower = new BlockHitResult(new Vec3(abs.getX() + .5, abs.getY() + .2, abs.getZ() + .2), Direction.NORTH, abs, false);
			BlockHitResult upper = new BlockHitResult(new Vec3(abs.getX() + .5, abs.getY() + .9, abs.getZ() + .2), Direction.NORTH, abs, false);

			ItemStack tag = new ItemStack(Items.NAME_TAG);
			tag.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Ab1"));
			InteractionResult named = helper.getLevel().getBlockState(abs).useItemOn(tag, helper.getLevel(), player, InteractionHand.MAIN_HAND, lower);
			helper.assertTrue(named.consumesAction(), "name tag: " + named);
			helper.assertTrue(lineText(be, 1).startsWith("AB1") && lineText(be, 0).isBlank(), "texts '" + lineText(be, 0) + "' '" + lineText(be, 1) + "'");

			ItemStack dye = new ItemStack(Items.DYE.pick(net.minecraft.world.item.DyeColor.BLUE));
			helper.getLevel().getBlockState(abs).useItemOn(dye, helper.getLevel(), player, InteractionHand.MAIN_HAND, upper);
			helper.assertTrue(be.getLineColor(0) == (0x5391E1 | 0xFF000000), "dyed line 0: " + Integer.toHexString(be.getLineColor(0)));

			helper.getLevel().getBlockState(abs).useItemOn(new ItemStack(Items.GLOW_INK_SAC), helper.getLevel(), player, InteractionHand.MAIN_HAND, upper);
			helper.assertTrue(be.isLineGlowing(0) && !be.isLineGlowing(1), "glow");

			// an empty hand clears the line again
			helper.getLevel().getBlockState(abs).useItemOn(ItemStack.EMPTY, helper.getLevel(), player, InteractionHand.MAIN_HAND, lower);
			helper.assertTrue(lineText(be, 1).isBlank(), "line 1 not cleared '" + lineText(be, 1) + "'");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void boardsPlacedNextToEachOtherFormOneBoard(GameTestHelper helper) {
		floor(helper);
		BlockPos first = new BlockPos(4, 2, 2);
		helper.setBlock(first, DisplayRegistry.DISPLAY_BOARD.defaultBlockState().setValue(FlapDisplayBlock.HORIZONTAL_FACING, Direction.NORTH));
		helper.runAfterDelay(2, () -> {
			ServerPlayer player = player(helper);
			// grow the board upwards and to the left (as the player sees it, i.e. towards the west)
			place(helper, player, first, Direction.UP);
			place(helper, player, first, Direction.WEST);
			place(helper, player, first.above(), Direction.WEST);
		});
		helper.runAfterDelay(30, () -> {
			BlockPos controllerPos = first.above();
			FlapDisplayBlockEntity controller = helper.getBlockEntity(controllerPos, FlapDisplayBlockEntity.class);
			helper.assertTrue(controller.isController && controller.xSize == 2 && controller.ySize == 2,
				"controller " + controller.isController + " size " + controller.xSize + "x" + controller.ySize);
			helper.assertTrue(controller.getLines().size() == 4, "lines " + controller.getLines().size());
			for (BlockPos pos : List.of(first, first.west(), first.above().west()))
				helper.assertTrue(!helper.getBlockEntity(pos, FlapDisplayBlockEntity.class).isController, pos + " is a controller");
			helper.assertTrue(helper.getBlockEntity(first.west(), FlapDisplayBlockEntity.class).getController() == controller, "controller lookup");
			helper.assertTrue(helper.getBlockState(controllerPos).getValue(FlapDisplayBlock.DOWN) && !helper.getBlockState(controllerPos).getValue(FlapDisplayBlock.UP), "connections");
			helper.succeed();
		});
	}

	private static void place(GameTestHelper helper, ServerPlayer player, BlockPos on, Direction face) {
		ItemStack stack = new ItemStack(DisplayRegistry.DISPLAY_BOARD_ITEM);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockPos abs = helper.absolutePos(on);
		InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(abs).add(face.getStepX() * .5, face.getStepY() * .5, face.getStepZ() * .5), face, abs, false)));
		helper.assertTrue(result.consumesAction(), "placing on " + face + ": " + result);
	}
}
