package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import java.util.ArrayList;
import java.util.List;

import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.createpolymer.redstone.RedstoneClientStates;
import dev.createpolymer.redstone.analogLever.AnalogLeverBlock;
import dev.createpolymer.redstone.analogLever.AnalogLeverBlockEntity;
import dev.createpolymer.redstone.contact.RedstoneContactBlock;
import dev.createpolymer.redstone.deskBell.DeskBellBlock;
import dev.createpolymer.redstone.diodes.BrassDiodeBlock;
import dev.createpolymer.redstone.diodes.PoweredLatchBlock;
import dev.createpolymer.redstone.diodes.PulseRepeaterBlockEntity;
import dev.createpolymer.redstone.diodes.ToggleLatchBlock;
import dev.createpolymer.redstone.link.LinkBehaviour;
import dev.createpolymer.redstone.link.LinkHandler;
import dev.createpolymer.redstone.link.RedstoneLinkBlock;
import dev.createpolymer.redstone.link.RedstoneLinkBlockEntity;
import dev.createpolymer.redstone.nixieTube.DoubleFaceAttachedBlock.DoubleAttachFace;
import dev.createpolymer.redstone.nixieTube.NixieTubeBlock;
import dev.createpolymer.redstone.nixieTube.NixieTubeBlockEntity;
import dev.createpolymer.redstone.smartObserver.SmartObserverBlock;
import dev.createpolymer.redstone.smartObserver.SmartObserverBlockEntity;
import dev.createpolymer.redstone.thresholdSwitch.ThresholdSwitchBlock;
import dev.createpolymer.redstone.thresholdSwitch.ThresholdSwitchBlockEntity;
import dev.createpolymer.redstone.thresholdSwitch.ThresholdSwitchGui;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.RedstoneRegistry;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import eu.pb4.sgui.api.ClickType;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class RedstoneGameTests {

	private static ServerPlayer player(GameTestHelper helper) {
		return FakePlayer.get(helper.getLevel());
	}

	private static BlockHitResult hit(GameTestHelper helper, BlockPos relative, Direction face) {
		BlockPos abs = helper.absolutePos(relative);
		return new BlockHitResult(Vec3.atCenterOf(abs), face, abs, false);
	}

	private static BlockState diode(BrassDiodeBlock block) {
		return block.defaultBlockState().setValue(BrassDiodeBlock.FACING, Direction.NORTH);
	}

	/** What the diode at pos puts out towards its front (the asking neighbour is behind it, to the south). */
	private static int outputOf(GameTestHelper helper, BlockPos pos) {
		return helper.getLevel().getSignal(helper.absolutePos(pos), Direction.NORTH);
	}

	/** Remembers on which ticks a condition went true and false. */
	private static class Edges {
		final List<Long> rising = new ArrayList<>();
		final List<Long> falling = new ArrayList<>();
		private boolean last;

		void sample(long tick, boolean value) {
			if (value && !last)
				rising.add(tick);
			if (!value && last)
				falling.add(tick);
			last = value;
		}
	}

	// ---- pulse repeater, extender and timer ----

	@GameTest(maxTicks = 80)
	public void pulseRepeaterWaitsThenEmitsAShortPulse(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		helper.setBlock(pos, diode(RedstoneRegistry.PULSE_REPEATER));
		Edges edges = new Edges();
		long[] placed = { -1 };
		helper.runAfterDelay(2, () -> helper.getBlockEntity(pos, PulseRepeaterBlockEntity.class)
			.getBehaviour(ScrollValueBehaviour.TYPE)
			.setValue(10));
		helper.runAfterDelay(4, () -> {
			helper.setBlock(pos.north(), Blocks.REDSTONE_BLOCK);
			placed[0] = helper.getTick();
		});
		helper.onEachTick(() -> {
			if (placed[0] >= 0)
				edges.sample(helper.getTick(), helper.getBlockState(pos).getValue(BrassDiodeBlock.POWERING));
		});
		helper.runAfterDelay(30, () -> {
			// the input is on, the pulse is on its way
			helper.assertTrue(helper.getBlockState(pos).getValue(BrassDiodeBlock.POWERED), "the input is not seen");
		});
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(edges.rising.size() == 1, "pulses: " + edges.rising);
			long delay = edges.rising.get(0) - placed[0];
			helper.assertTrue(delay >= 8 && delay <= 16, "the pulse started after " + delay + " ticks");
			helper.assertTrue(edges.falling.size() == 1, "the pulse did not end");
			long length = edges.falling.get(0) - edges.rising.get(0);
			helper.assertTrue(length >= 1 && length <= 4, "the pulse lasted " + length + " ticks");
			// a repeater pulse is only repeated after the input went off
			helper.assertTrue(outputOf(helper, pos) == 0, "the output is still on");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void pulseRepeaterDelayFollowsTheValue(GameTestHelper helper) {
		floor(helper);
		BlockPos shortOne = new BlockPos(2, 1, 3);
		BlockPos longOne = new BlockPos(5, 1, 3);
		helper.setBlock(shortOne, diode(RedstoneRegistry.PULSE_REPEATER));
		helper.setBlock(longOne, diode(RedstoneRegistry.PULSE_REPEATER));
		Edges shortEdges = new Edges();
		Edges longEdges = new Edges();
		helper.runAfterDelay(2, () -> {
			helper.getBlockEntity(shortOne, PulseRepeaterBlockEntity.class).getBehaviour(ScrollValueBehaviour.TYPE).setValue(5);
			helper.getBlockEntity(longOne, PulseRepeaterBlockEntity.class).getBehaviour(ScrollValueBehaviour.TYPE).setValue(25);
		});
		helper.runAfterDelay(4, () -> {
			helper.setBlock(shortOne.north(), Blocks.REDSTONE_BLOCK);
			helper.setBlock(longOne.north(), Blocks.REDSTONE_BLOCK);
		});
		helper.onEachTick(() -> {
			shortEdges.sample(helper.getTick(), helper.getBlockState(shortOne).getValue(BrassDiodeBlock.POWERING));
			longEdges.sample(helper.getTick(), helper.getBlockState(longOne).getValue(BrassDiodeBlock.POWERING));
		});
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(shortEdges.rising.size() == 1 && longEdges.rising.size() == 1,
				"pulses: " + shortEdges.rising + " " + longEdges.rising);
			long difference = longEdges.rising.get(0) - shortEdges.rising.get(0);
			helper.assertTrue(difference >= 17 && difference <= 23, "the long one came " + difference + " ticks after the short one");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void pulseExtenderHoldsTheSignalForTheConfiguredTime(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		helper.setBlock(pos, diode(RedstoneRegistry.PULSE_EXTENDER));
		Edges edges = new Edges();
		long[] removed = { -1 };
		helper.runAfterDelay(2, () -> helper.getBlockEntity(pos, dev.createpolymer.redstone.diodes.PulseExtenderBlockEntity.class)
			.getBehaviour(ScrollValueBehaviour.TYPE)
			.setValue(20));
		helper.runAfterDelay(4, () -> helper.setBlock(pos.north(), Blocks.REDSTONE_BLOCK));
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(outputOf(helper, pos) == 15, "no output while the input is on");
			helper.setBlock(pos.north(), Blocks.AIR);
			removed[0] = helper.getTick();
		});
		helper.onEachTick(() -> edges.sample(helper.getTick(), helper.getBlockState(pos).getValue(BrassDiodeBlock.POWERING)));
		helper.runAfterDelay(16, () -> helper.assertTrue(outputOf(helper, pos) == 15, "the signal ended with the input"));
		helper.runAfterDelay(80, () -> {
			helper.assertTrue(edges.rising.size() == 1 && edges.falling.size() == 1, "edges " + edges.rising + " " + edges.falling);
			long extra = edges.falling.get(0) - removed[0];
			helper.assertTrue(extra >= 18 && extra <= 28, "the output went off " + extra + " ticks after the input");
			helper.assertTrue(outputOf(helper, pos) == 0, "the output stayed on");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 120)
	public void pulseTimerPulsesAndPausesWhilePowered(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		helper.setBlock(pos, diode(RedstoneRegistry.PULSE_TIMER));
		Edges edges = new Edges();
		helper.runAfterDelay(2, () -> helper.getBlockEntity(pos, dev.createpolymer.redstone.diodes.PulseTimerBlockEntity.class)
			.getBehaviour(ScrollValueBehaviour.TYPE)
			.setValue(8));
		helper.onEachTick(() -> edges.sample(helper.getTick(), helper.getBlockState(pos).getValue(BrassDiodeBlock.POWERING)));
		helper.runAfterDelay(60, () -> {
			// one pulse about every 7 ticks
			helper.assertTrue(edges.rising.size() >= 6 && edges.rising.size() <= 10, "pulses: " + edges.rising);
			helper.setBlock(pos.north(), Blocks.REDSTONE_BLOCK);
		});
		helper.runAfterDelay(70, () -> {
			int before = edges.rising.size();
			helper.assertTrue(!helper.getBlockState(pos).getValue(BrassDiodeBlock.POWERING), "pulsing while powered");
			helper.runAfterDelay(30, () -> {
				helper.assertTrue(edges.rising.size() == before, "the timer kept pulsing while its input was on");
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = 40)
	public void pulseDiodesCanBeInverted(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		helper.setBlock(pos, diode(RedstoneRegistry.PULSE_REPEATER));
		helper.runAfterDelay(3, () -> {
			var result = helper.getBlockState(pos).useItemOn(ItemStack.EMPTY, helper.getLevel(), player(helper), InteractionHand.MAIN_HAND, hit(helper, pos, Direction.UP));
			helper.assertTrue(result == InteractionResult.SUCCESS, "result " + result);
			helper.assertTrue(helper.getBlockState(pos).getValue(BrassDiodeBlock.INVERTED), "not inverted");
			// inverted and idle: the output is on
			helper.assertTrue(outputOf(helper, pos) == 15, "an inverted idle diode should output");
			helper.succeed();
		});
	}

	// ---- latches ----

	@GameTest(maxTicks = 80)
	public void poweredLatchIsSetByTheBackAndResetBySide(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		helper.setBlock(pos, RedstoneRegistry.POWERED_LATCH.defaultBlockState().setValue(PoweredLatchBlock.FACING, Direction.NORTH));
		helper.runAfterDelay(2, () -> helper.setBlock(pos.north(), Blocks.REDSTONE_BLOCK));
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(helper.getBlockState(pos).getValue(ToggleLatchBlock.POWERING), "the back signal did not set the latch");
			helper.assertTrue(outputOf(helper, pos) == 15, "no output");
			helper.setBlock(pos.north(), Blocks.AIR);
		});
		helper.runAfterDelay(16, () -> {
			helper.assertTrue(helper.getBlockState(pos).getValue(ToggleLatchBlock.POWERING), "the latch let go without a reset");
			helper.setBlock(pos.east(), Blocks.REDSTONE_BLOCK);
		});
		helper.runAfterDelay(24, () -> {
			helper.assertTrue(!helper.getBlockState(pos).getValue(ToggleLatchBlock.POWERING), "the side signal did not reset the latch");
			helper.assertTrue(helper.getBlockState(pos).getValue(PoweredLatchBlock.POWERED_SIDE), "side not seen");
			helper.setBlock(pos.east(), Blocks.AIR);
		});
		helper.runAfterDelay(32, () -> {
			helper.assertTrue(!helper.getBlockState(pos).getValue(ToggleLatchBlock.POWERING), "the latch came back on by itself");
			helper.assertTrue(outputOf(helper, pos) == 0, "output after the reset");
			// and the other side works as well
			helper.setBlock(pos.north(), Blocks.REDSTONE_BLOCK);
		});
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(helper.getBlockState(pos).getValue(ToggleLatchBlock.POWERING), "set again");
			helper.setBlock(pos.west(), Blocks.REDSTONE_BLOCK);
		});
		helper.runAfterDelay(48, () -> {
			// both at once: the side wins
			helper.assertTrue(!helper.getBlockState(pos).getValue(ToggleLatchBlock.POWERING), "the west side did not reset the latch");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void poweredToggleLatchTogglesOnEachPulse(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		helper.setBlock(pos, RedstoneRegistry.POWERED_TOGGLE_LATCH.defaultBlockState().setValue(ToggleLatchBlock.FACING, Direction.NORTH));
		helper.runAfterDelay(2, () -> helper.setBlock(pos.north(), Blocks.REDSTONE_BLOCK));
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(helper.getBlockState(pos).getValue(ToggleLatchBlock.POWERING), "first pulse: not on");
			helper.setBlock(pos.north(), Blocks.AIR);
		});
		helper.runAfterDelay(14, () -> {
			helper.assertTrue(helper.getBlockState(pos).getValue(ToggleLatchBlock.POWERING), "the toggle latch let go with the input");
			helper.setBlock(pos.north(), Blocks.REDSTONE_BLOCK);
		});
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(!helper.getBlockState(pos).getValue(ToggleLatchBlock.POWERING), "second pulse: not off");
			helper.assertTrue(outputOf(helper, pos) == 0, "output after toggling off");
			helper.setBlock(pos.north(), Blocks.AIR);
		});
		helper.runAfterDelay(30, () -> {
			// by hand
			var result = helper.getBlockState(pos).useItemOn(ItemStack.EMPTY, helper.getLevel(), player(helper), InteractionHand.MAIN_HAND, hit(helper, pos, Direction.UP));
			helper.assertTrue(result == InteractionResult.SUCCESS, "result " + result);
			helper.assertTrue(helper.getBlockState(pos).getValue(ToggleLatchBlock.POWERING), "toggling by hand did nothing");
			helper.assertTrue(outputOf(helper, pos) == 15, "no output after toggling by hand");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void diodesOnlyConnectDustAlongTheirAxis(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		BlockState state = diode(RedstoneRegistry.PULSE_REPEATER);
		helper.setBlock(pos, state);
		helper.runAfterDelay(2, () -> {
			var level = helper.getLevel();
			BlockPos abs = helper.absolutePos(pos);
			helper.assertTrue(state.shouldRedstoneWireConnectTo(level, abs, Direction.NORTH), "no connection in front");
			helper.assertTrue(state.shouldRedstoneWireConnectTo(level, abs, Direction.SOUTH), "no connection behind");
			helper.assertTrue(!state.shouldRedstoneWireConnectTo(level, abs, Direction.EAST), "dust connects at the side");
			helper.succeed();
		});
	}

	// ---- redstone link ----

	private static BlockState link(boolean receiver) {
		return RedstoneRegistry.REDSTONE_LINK.defaultBlockState()
			.setValue(RedstoneLinkBlock.FACING, Direction.UP)
			.setValue(RedstoneLinkBlock.RECEIVER, receiver);
	}

	private static int received(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockEntity(pos, RedstoneLinkBlockEntity.class).getReceivedSignal();
	}

	@GameTest(maxTicks = 100)
	public void linkedReceiversOnlyListenToTheirFrequency(GameTestHelper helper) {
		floor(helper);
		BlockPos transmitter = new BlockPos(1, 1, 1);
		BlockPos matching = new BlockPos(6, 1, 6);
		BlockPos otherSecond = new BlockPos(6, 1, 1);
		BlockPos otherFirst = new BlockPos(1, 1, 6);
		helper.setBlock(transmitter, link(false));
		helper.setBlock(matching, link(true));
		helper.setBlock(otherSecond, link(true));
		helper.setBlock(otherFirst, link(true));
		helper.runAfterDelay(2, () -> {
			setFrequency(helper, transmitter, Items.IRON_INGOT, Items.GOLD_INGOT);
			setFrequency(helper, matching, Items.IRON_INGOT, Items.GOLD_INGOT);
			setFrequency(helper, otherSecond, Items.IRON_INGOT, Items.COPPER_INGOT);
			setFrequency(helper, otherFirst, Items.DIAMOND, Items.GOLD_INGOT);
		});
		helper.runAfterDelay(4, () -> helper.setBlock(transmitter.north(), Blocks.REDSTONE_BLOCK));
		helper.runAfterDelay(16, () -> {
			helper.assertTrue(received(helper, matching) == 15, "the link with the same frequency received " + received(helper, matching));
			helper.assertTrue(helper.getBlockState(matching).getValue(RedstoneLinkBlock.POWERED), "the receiver is not powered");
			helper.assertTrue(helper.getLevel().getSignal(helper.absolutePos(matching), Direction.UP) == 15, "the receiver puts out nothing");
			helper.assertTrue(received(helper, otherSecond) == 0, "a link with another second frequency received a signal");
			helper.assertTrue(received(helper, otherFirst) == 0, "a link with another first frequency received a signal");
			helper.assertTrue(!helper.getBlockState(otherSecond).getValue(RedstoneLinkBlock.POWERED), "wrong receiver powered");
			helper.setBlock(transmitter.north(), Blocks.AIR);
		});
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(received(helper, matching) == 0, "the receiver kept its signal");
			helper.assertTrue(!helper.getBlockState(matching).getValue(RedstoneLinkBlock.POWERED), "the receiver stayed powered");
			// changing the frequency of the receiver takes it out of the network, putting the signal back brings it in only if equal again
			helper.setBlock(transmitter.north(), Blocks.REDSTONE_BLOCK);
		});
		helper.runAfterDelay(42, () -> {
			helper.assertTrue(received(helper, matching) == 15, "no signal the second time");
			setFrequency(helper, matching, Items.IRON_INGOT, Items.COPPER_INGOT);
		});
		helper.runAfterDelay(52, () -> {
			helper.assertTrue(received(helper, matching) == 0, "the receiver kept listening after its frequency changed");
			helper.assertTrue(received(helper, otherSecond) == 0, "no transmitter for this frequency");
			helper.succeed();
		});
	}

	private static void setFrequency(GameTestHelper helper, BlockPos pos, net.minecraft.world.item.Item first, net.minecraft.world.item.Item second) {
		LinkBehaviour behaviour = helper.getBlockEntity(pos, RedstoneLinkBlockEntity.class).getLink();
		behaviour.setFrequency(true, new ItemStack(first));
		behaviour.setFrequency(false, new ItemStack(second));
	}

	@GameTest(maxTicks = 60)
	public void linkFrequenciesAreSetByClickingTheSlots(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		helper.setBlock(pos, link(false));
		helper.runAfterDelay(2, () -> {
			ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
			BlockPos abs = helper.absolutePos(pos);
			LinkBehaviour behaviour = helper.getBlockEntity(pos, RedstoneLinkBlockEntity.class).getLink();
			var state = helper.getBlockState(pos);
			Vec3 firstSlot = Vec3.atLowerCornerOf(abs).add(behaviour.getSlot(true).getLocalOffset(helper.getLevel(), abs, state));
			Vec3 secondSlot = Vec3.atLowerCornerOf(abs).add(behaviour.getSlot(false).getLocalOffset(helper.getLevel(), abs, state));
			helper.assertTrue(firstSlot.distanceTo(secondSlot) > .2, "the two slots are in the same place");

			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLD_INGOT, 5));
			var result = LinkHandler.onBlockActivated(player, helper.getLevel(), InteractionHand.MAIN_HAND,
				new BlockHitResult(firstSlot, Direction.UP, abs, false));
			helper.assertTrue(result == InteractionResult.SUCCESS, "result " + result);
			helper.assertTrue(behaviour.getFrequencyStack(true).is(Items.GOLD_INGOT) && behaviour.getFrequencyStack(true).getCount() == 1, "first: " + behaviour.getFrequencyStack(true));
			helper.assertTrue(behaviour.getFrequencyStack(false).isEmpty(), "second: " + behaviour.getFrequencyStack(false));
			helper.assertTrue(player.getMainHandItem().getCount() == 5, "the item was used up");

			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.REDSTONE));
			LinkHandler.onBlockActivated(player, helper.getLevel(), InteractionHand.MAIN_HAND, new BlockHitResult(secondSlot, Direction.UP, abs, false));
			helper.assertTrue(behaviour.getFrequencyStack(false).is(Items.REDSTONE), "second: " + behaviour.getFrequencyStack(false));
			helper.assertTrue(behaviour.getFrequencyStack(true).is(Items.GOLD_INGOT), "first changed");

			// an empty hand clears the slot
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			LinkHandler.onBlockActivated(player, helper.getLevel(), InteractionHand.MAIN_HAND, new BlockHitResult(firstSlot, Direction.UP, abs, false));
			helper.assertTrue(behaviour.getFrequencyStack(true).isEmpty(), "first not cleared");

			// the frequency items are shown by the model, one tick later
			helper.runAfterDelay(3, () -> {
				int shown = 0;
				BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), abs);
				helper.assertTrue(attachment != null, "the link has no model");
				for (VirtualElement element : attachment.holder().getElements())
					if (element instanceof ItemDisplayElement display && display.getItem().is(Items.REDSTONE))
						shown++;
				helper.assertTrue(shown == 1, "frequency items shown: " + shown);
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = 60)
	public void linkMissesWhenClickedAwayFromItsSlots(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		helper.setBlock(pos, link(false));
		helper.runAfterDelay(2, () -> {
			ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
			BlockPos abs = helper.absolutePos(pos);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLD_INGOT));
			var result = LinkHandler.onBlockActivated(player, helper.getLevel(), InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atLowerCornerOf(abs).add(.05, .1, .95), Direction.UP, abs, false));
			helper.assertTrue(result == InteractionResult.PASS, "result " + result);
			helper.assertTrue(helper.getBlockEntity(pos, RedstoneLinkBlockEntity.class).getLink().getFrequencyStack(true).isEmpty(), "set by a click far from the slot");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void linkSwitchesModeWithSneakUseAndWrench(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		helper.setBlock(pos, link(false));
		helper.runAfterDelay(3, () -> {
			ServerPlayer player = player(helper);
			player.setShiftKeyDown(true);
			var result = helper.getBlockState(pos).useWithoutItem(helper.getLevel(), player, hit(helper, pos, Direction.UP));
			player.setShiftKeyDown(false);
			helper.assertTrue(result == InteractionResult.SUCCESS, "result " + result);
			helper.assertTrue(helper.getBlockState(pos).getValue(RedstoneLinkBlock.RECEIVER), "not a receiver");

			ItemStack wrench = new ItemStack(ModItems.WRENCH);
			player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
			wrench.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(helper, pos, Direction.NORTH)));
			helper.assertTrue(!helper.getBlockState(pos).getValue(RedstoneLinkBlock.RECEIVER), "the wrench did not switch the mode back");
			helper.succeed();
		});
		helper.runAfterDelay(8, () -> {
			// the link was rebuilt as a transmitter, with its frequency items
			helper.assertTrue(helper.getBlockEntity(pos, RedstoneLinkBlockEntity.class).getLink() != null, "no link behaviour");
		});
	}

	@GameTest(maxTicks = 60)
	public void linkSavesItsFrequencies(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		helper.setBlock(pos, link(true));
		helper.runAfterDelay(2, () -> {
			setFrequency(helper, pos, Items.IRON_INGOT, Items.GOLD_INGOT);
			RedstoneLinkBlockEntity original = helper.getBlockEntity(pos, RedstoneLinkBlockEntity.class);
			var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
				net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
			original.saveCustomOnly(output);
			var copy = new RedstoneLinkBlockEntity(RedstoneRegistry.REDSTONE_LINK_BLOCK_ENTITY, helper.absolutePos(pos), helper.getBlockState(pos));
			copy.setLevel(helper.getLevel());
			copy.loadCustomOnly(net.minecraft.world.level.storage.TagValueInput.create(
				net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), output.buildResult()));
			helper.assertTrue(copy.getLink().getFrequencyStack(true).is(Items.IRON_INGOT), "first: " + copy.getLink().getFrequencyStack(true));
			helper.assertTrue(copy.getLink().getFrequencyStack(false).is(Items.GOLD_INGOT), "second: " + copy.getLink().getFrequencyStack(false));
			helper.succeed();
		});
	}

	// ---- analog lever ----

	private static BlockState lever() {
		return RedstoneRegistry.ANALOG_LEVER.defaultBlockState()
			.setValue(AnalogLeverBlock.FACE, AttachFace.FLOOR)
			.setValue(AnalogLeverBlock.FACING, Direction.NORTH);
	}

	@GameTest(maxTicks = 80)
	public void analogLeverOutputsTheChosenLevel(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		BlockPos wire = new BlockPos(4, 1, 3);
		helper.setBlock(pos, lever());
		helper.setBlock(wire, Blocks.REDSTONE_WIRE);
		helper.runAfterDelay(3, () -> {
			ServerPlayer player = player(helper);
			for (int i = 0; i < 5; i++) {
				var result = helper.getBlockState(pos).useWithoutItem(helper.getLevel(), player, hit(helper, pos, Direction.UP));
				helper.assertTrue(result == InteractionResult.SUCCESS, "result " + result);
			}
			AnalogLeverBlockEntity be = helper.getBlockEntity(pos, AnalogLeverBlockEntity.class);
			helper.assertTrue(be.getState() == 5, "level " + be.getState());
			helper.assertTrue(helper.getLevel().getSignal(helper.absolutePos(pos), Direction.EAST) == 5, "signal " + helper.getLevel().getSignal(helper.absolutePos(pos), Direction.EAST));
		});
		helper.runAfterDelay(25, () -> {
			// the neighbours were told after the delay
			int power = helper.getBlockState(wire).getValue(RedstoneWireBlock.POWER);
			helper.assertTrue(power == 5, "the dust next to the lever has power " + power);

			// sneaking lowers it, even with something in the hand
			ServerPlayer player = player(helper);
			player.setShiftKeyDown(true);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
			var result = UseBlockCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit(helper, pos, Direction.UP));
			player.setShiftKeyDown(false);
			helper.assertTrue(result == InteractionResult.SUCCESS, "result " + result);
			helper.assertTrue(helper.getBlockEntity(pos, AnalogLeverBlockEntity.class).getState() == 4, "lowered to " + helper.getBlockEntity(pos, AnalogLeverBlockEntity.class).getState());
			helper.assertTrue(player.getMainHandItem().getCount() == 1, "the item was used");
		});
		helper.runAfterDelay(50, () -> {
			int power = helper.getBlockState(wire).getValue(RedstoneWireBlock.POWER);
			helper.assertTrue(power == 4, "the dust next to the lever has power " + power);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void analogLeverStaysWithinZeroAndFifteen(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		helper.setBlock(pos, lever());
		helper.runAfterDelay(3, () -> {
			ServerPlayer player = player(helper);
			for (int i = 0; i < 20; i++)
				helper.getBlockState(pos).useWithoutItem(helper.getLevel(), player, hit(helper, pos, Direction.UP));
			AnalogLeverBlockEntity be = helper.getBlockEntity(pos, AnalogLeverBlockEntity.class);
			helper.assertTrue(be.getState() == 15, "top " + be.getState());
			player.setShiftKeyDown(true);
			for (int i = 0; i < 20; i++)
				helper.getBlockState(pos).useWithoutItem(helper.getLevel(), player, hit(helper, pos, Direction.UP));
			player.setShiftKeyDown(false);
			helper.assertTrue(be.getState() == 0, "bottom " + be.getState());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void analogLeverSavesItsLevelAndShowsTheHandle(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		helper.setBlock(pos, lever());
		helper.runAfterDelay(3, () -> {
			ServerPlayer player = player(helper);
			for (int i = 0; i < 7; i++)
				helper.getBlockState(pos).useWithoutItem(helper.getLevel(), player, hit(helper, pos, Direction.UP));
			AnalogLeverBlockEntity be = helper.getBlockEntity(pos, AnalogLeverBlockEntity.class);
			var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
				net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
			be.saveCustomOnly(output);
			var copy = new AnalogLeverBlockEntity(RedstoneRegistry.ANALOG_LEVER_BLOCK_ENTITY, helper.absolutePos(pos), helper.getBlockState(pos));
			copy.loadCustomOnly(net.minecraft.world.level.storage.TagValueInput.create(
				net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), output.buildResult()));
			helper.assertTrue(copy.getState() == 7, "loaded level " + copy.getState());
		});
		helper.runAfterDelay(10, () -> {
			BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(pos));
			helper.assertTrue(attachment != null, "no model");
			int parts = 0;
			for (VirtualElement element : attachment.holder().getElements())
				if (element instanceof ItemDisplayElement)
					parts++;
			helper.assertTrue(parts == 2, "parts " + parts);
			helper.succeed();
		});
	}

	// ---- nixie tube ----

	private static List<String> texts(GameTestHelper helper, BlockPos pos) {
		BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(pos));
		helper.assertTrue(attachment != null, "no model at " + pos);
		List<String> texts = new ArrayList<>();
		for (VirtualElement element : attachment.holder().getElements())
			if (element instanceof TextDisplayElement text)
				texts.add(text.getText().getString());
		return texts;
	}

	@GameTest(maxTicks = 80)
	public void nixieTubeShowsTheSignalStrength(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		helper.setBlock(pos, RedstoneRegistry.ORANGE_NIXIE_TUBE.defaultBlockState()
			.setValue(NixieTubeBlock.FACE, DoubleAttachFace.FLOOR)
			.setValue(NixieTubeBlock.FACING, Direction.EAST));
		helper.runAfterDelay(3, () -> {
			NixieTubeBlockEntity be = helper.getBlockEntity(pos, NixieTubeBlockEntity.class);
			helper.assertTrue(be.getDisplayedStrings().getFirst().equals("0") && be.getDisplayedStrings().getSecond().equals("0"), "idle " + be.getDisplayedStrings());
			helper.setBlock(pos.north(), Blocks.REDSTONE_BLOCK);
		});
		helper.runAfterDelay(10, () -> {
			NixieTubeBlockEntity be = helper.getBlockEntity(pos, NixieTubeBlockEntity.class);
			helper.assertTrue(be.getRedstoneStrength() == 15, "strength " + be.getRedstoneStrength());
			helper.assertTrue(be.getDisplayedStrings().getFirst().equals("1") && be.getDisplayedStrings().getSecond().equals("5"), "text " + be.getDisplayedStrings());
		});
		helper.runAfterDelay(14, () -> {
			// two tubes, each with a bright digit in front and a dark one behind
			List<String> texts = texts(helper, pos);
			helper.assertTrue(texts.stream().filter("1"::equals).count() == 2 && texts.stream().filter("5"::equals).count() == 2, "digits shown: " + texts);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void nixieTubeRowsShowANameTagAndTakeDye(GameTestHelper helper) {
		floor(helper);
		// a floor tube facing north has its row along z, starting at the south end
		BlockPos first = new BlockPos(3, 1, 4);
		BlockPos second = new BlockPos(3, 1, 3);
		BlockPos third = new BlockPos(3, 1, 2);
		for (BlockPos pos : List.of(first, second, third))
			helper.setBlock(pos, RedstoneRegistry.ORANGE_NIXIE_TUBE.defaultBlockState()
				.setValue(NixieTubeBlock.FACE, DoubleAttachFace.FLOOR)
				.setValue(NixieTubeBlock.FACING, Direction.NORTH));
		helper.runAfterDelay(3, () -> {
			ServerPlayer player = player(helper);
			ItemStack tag = new ItemStack(Items.NAME_TAG);
			tag.set(DataComponents.CUSTOM_NAME, Component.literal("HELLO!"));
			var result = helper.getBlockState(second).useItemOn(tag, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit(helper, second, Direction.UP));
			helper.assertTrue(result == InteractionResult.SUCCESS, "result " + result);
		});
		helper.runAfterDelay(8, () -> {
			String all = "";
			for (BlockPos pos : List.of(first, second, third)) {
				NixieTubeBlockEntity be = helper.getBlockEntity(pos, NixieTubeBlockEntity.class);
				all += be.getDisplayedStrings().getFirst() + be.getDisplayedStrings().getSecond();
			}
			helper.assertTrue(all.length() == 6 && (all.equals("HELLO!")), "row text " + all);
			helper.assertTrue(helper.getBlockEntity(first, NixieTubeBlockEntity.class).getFullText().getString().equals("HELLO!"), "full text");

			// dye turns the whole row
			ServerPlayer player = player(helper);
			ItemStack dye = new ItemStack(Items.DYE.blue());
			var result = helper.getBlockState(first).useItemOn(dye, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit(helper, first, Direction.UP));
			helper.assertTrue(result == InteractionResult.SUCCESS, "dye result " + result);
		});
		helper.runAfterDelay(14, () -> {
			for (BlockPos pos : List.of(first, second, third)) {
				helper.assertTrue(helper.getBlockState(pos).is(RedstoneRegistry.NIXIE_TUBES.get(DyeColor.BLUE)), "not blue at " + pos + ": " + helper.getBlockState(pos));
				// the text stayed in the block entity
				helper.assertTrue(helper.getBlockEntity(pos, NixieTubeBlockEntity.class).getFullText().getString().equals("HELLO!"), "text lost at " + pos);
			}
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void nixieTubeRowsReadLeftToRight(GameTestHelper helper) {
		floor(helper);
		// tubes standing on the floor, facing north: the row runs along x
		BlockState state = RedstoneRegistry.ORANGE_NIXIE_TUBE.defaultBlockState()
			.setValue(NixieTubeBlock.FACE, DoubleAttachFace.FLOOR)
			.setValue(NixieTubeBlock.FACING, Direction.NORTH);
		Direction left = NixieTubeBlock.getLeftNixieDirection(state);
		helper.assertTrue(left.getAxis() == Direction.Axis.Z || left.getAxis() == Direction.Axis.X, "left " + left);
		List<BlockPos> row = new ArrayList<>();
		BlockPos start = new BlockPos(3, 1, 3);
		for (int i = 0; i < 2; i++) {
			BlockPos pos = start.relative(left.getOpposite(), i);
			row.add(pos);
			helper.setBlock(pos, state);
		}
		helper.runAfterDelay(3, () -> {
			ItemStack tag = new ItemStack(Items.NAME_TAG);
			tag.set(DataComponents.CUSTOM_NAME, Component.literal("ABCD"));
			helper.getBlockState(row.get(1)).useItemOn(tag, helper.getLevel(), player(helper), InteractionHand.MAIN_HAND, hit(helper, row.get(1), Direction.UP));
		});
		helper.runAfterDelay(8, () -> {
			NixieTubeBlockEntity a = helper.getBlockEntity(row.get(0), NixieTubeBlockEntity.class);
			NixieTubeBlockEntity b = helper.getBlockEntity(row.get(1), NixieTubeBlockEntity.class);
			helper.assertTrue((a.getDisplayedStrings().getFirst() + a.getDisplayedStrings().getSecond()).equals("AB"), "first tube " + a.getDisplayedStrings());
			helper.assertTrue((b.getDisplayedStrings().getFirst() + b.getDisplayedStrings().getSecond()).equals("CD"), "second tube " + b.getDisplayedStrings());
			// an empty hand clears the text and the signal is shown again
			var result = helper.getBlockState(row.get(0)).useItemOn(ItemStack.EMPTY, helper.getLevel(), player(helper), InteractionHand.MAIN_HAND, hit(helper, row.get(0), Direction.UP));
			helper.assertTrue(result == InteractionResult.SUCCESS, "result " + result);
			helper.assertTrue(a.reactsToRedstone(), "still has the text");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void everyDyeHasANixieTubeAndTheyDropTheOrangeOne(GameTestHelper helper) {
		helper.assertTrue(RedstoneRegistry.NIXIE_TUBES.size() == 16, "colours " + RedstoneRegistry.NIXIE_TUBES.size());
		for (DyeColor color : DyeColor.values()) {
			NixieTubeBlock block = RedstoneRegistry.NIXIE_TUBES.get(color);
			helper.assertTrue(block != null && block.getColor() == color, "block for " + color);
			ItemStack clone = block.defaultBlockState().getCloneItemStack(helper.getLevel(), BlockPos.ZERO, false);
			helper.assertTrue(clone.is(RedstoneRegistry.ORANGE_NIXIE_TUBE_ITEM), "pick block of " + color + ": " + clone);
		}
		helper.succeed();
	}

	// ---- smart observer ----

	@GameTest(maxTicks = 140)
	public void smartObserverDetectsAFilteredItemInAnInventory(GameTestHelper helper) {
		floor(helper);
		BlockPos chest = new BlockPos(3, 1, 3);
		BlockPos observer = new BlockPos(3, 2, 3);
		helper.setBlock(chest, Blocks.CHEST);
		helper.setBlock(observer, RedstoneRegistry.SMART_OBSERVER.defaultBlockState()
			.setValue(SmartObserverBlock.TARGET, AttachFace.FLOOR)
			.setValue(SmartObserverBlock.FACING, Direction.NORTH));
		helper.runAfterDelay(3, () -> {
			helper.getBlockEntity(observer, SmartObserverBlockEntity.class).getBehaviour(FilteringBehaviour.TYPE).setFilter(new ItemStack(Items.DIAMOND));
			helper.getBlockEntity(chest, ChestBlockEntity.class).setItem(4, new ItemStack(Items.DIRT, 10));
		});
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(!helper.getBlockState(observer).getValue(SmartObserverBlock.POWERED), "powered by an item the filter does not match");
			helper.getBlockEntity(chest, ChestBlockEntity.class).setItem(7, new ItemStack(Items.DIAMOND, 2));
		});
		helper.runAfterDelay(45, () -> {
			helper.assertTrue(helper.getBlockState(observer).getValue(SmartObserverBlock.POWERED), "the diamonds were not detected");
			// it powers its neighbours, except on the side that looks at the inventory
			helper.assertTrue(helper.getLevel().getSignal(helper.absolutePos(observer), Direction.EAST) == 15, "no output to the side");
			helper.assertTrue(helper.getLevel().getSignal(helper.absolutePos(observer), Direction.UP) == 0
				|| helper.getLevel().getSignal(helper.absolutePos(observer), Direction.UP) == 15, "up");
			helper.getBlockEntity(chest, ChestBlockEntity.class).setItem(7, ItemStack.EMPTY);
		});
		helper.runAfterDelay(100, () -> {
			helper.assertTrue(!helper.getBlockState(observer).getValue(SmartObserverBlock.POWERED), "still powered after the diamonds were taken");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void smartObserverWithoutFilterDetectsAnything(GameTestHelper helper) {
		floor(helper);
		BlockPos chest = new BlockPos(3, 1, 3);
		BlockPos observer = new BlockPos(3, 2, 3);
		helper.setBlock(chest, Blocks.CHEST);
		helper.setBlock(observer, RedstoneRegistry.SMART_OBSERVER.defaultBlockState()
			.setValue(SmartObserverBlock.TARGET, AttachFace.FLOOR)
			.setValue(SmartObserverBlock.FACING, Direction.NORTH));
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(!helper.getBlockState(observer).getValue(SmartObserverBlock.POWERED), "powered by an empty chest");
			helper.getBlockEntity(chest, ChestBlockEntity.class).setItem(0, new ItemStack(Items.COAL));
		});
		helper.runAfterDelay(45, () -> {
			helper.assertTrue(helper.getBlockState(observer).getValue(SmartObserverBlock.POWERED), "not powered by a chest with something in it");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void smartObserverMatchesTheBlockInFrontWithTheFilter(GameTestHelper helper) {
		floor(helper);
		BlockPos target = new BlockPos(3, 1, 3);
		BlockPos observer = new BlockPos(3, 2, 3);
		helper.setBlock(target, Blocks.DIAMOND_BLOCK);
		helper.setBlock(observer, RedstoneRegistry.SMART_OBSERVER.defaultBlockState()
			.setValue(SmartObserverBlock.TARGET, AttachFace.FLOOR)
			.setValue(SmartObserverBlock.FACING, Direction.NORTH));
		helper.runAfterDelay(3, () -> helper.getBlockEntity(observer, SmartObserverBlockEntity.class).getBehaviour(FilteringBehaviour.TYPE).setFilter(new ItemStack(Items.DIAMOND_BLOCK)));
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(helper.getBlockState(observer).getValue(SmartObserverBlock.POWERED), "the block in front matches the filter but nothing happened");
			helper.succeed();
		});
	}

	// ---- threshold switch ----

	private static BlockState switchState() {
		return RedstoneRegistry.THRESHOLD_SWITCH.defaultBlockState()
			.setValue(ThresholdSwitchBlock.TARGET, AttachFace.FLOOR)
			.setValue(ThresholdSwitchBlock.FACING, Direction.NORTH);
	}

	@GameTest(maxTicks = 220)
	public void thresholdSwitchPowersAboveTheThresholdAndKeepsItUntilBelow(GameTestHelper helper) {
		floor(helper);
		BlockPos chest = new BlockPos(3, 1, 3);
		BlockPos switchPos = new BlockPos(3, 2, 3);
		BlockPos lamp = new BlockPos(4, 2, 3);
		helper.setBlock(chest, Blocks.CHEST);
		helper.setBlock(switchPos, switchState());
		helper.setBlock(lamp, Blocks.REDSTONE_LAMP);
		helper.runAfterDelay(3, () -> {
			ThresholdSwitchBlockEntity be = helper.getBlockEntity(switchPos, ThresholdSwitchBlockEntity.class);
			be.onWhenAbove = 100;
			be.offWhenBelow = 40;
			ChestBlockEntity inventory = helper.getBlockEntity(chest, ChestBlockEntity.class);
			inventory.setItem(0, new ItemStack(Items.STONE, 30));
		});
		helper.runAfterDelay(40, () -> {
			ThresholdSwitchBlockEntity be = helper.getBlockEntity(switchPos, ThresholdSwitchBlockEntity.class);
			helper.assertTrue(be.currentLevel == 30, "level " + be.currentLevel);
			helper.assertTrue(be.currentMaxLevel == 27 * 64, "capacity " + be.currentMaxLevel);
			helper.assertTrue(!be.isPowered(), "powered below the threshold");
			helper.assertTrue(helper.getBlockState(switchPos).getValue(ThresholdSwitchBlock.LEVEL) == 1, "display level " + helper.getBlockState(switchPos).getValue(ThresholdSwitchBlock.LEVEL));
			helper.assertTrue(!helper.getBlockState(lamp).getValue(RedstoneLampBlock.LIT), "lamp lit");
			ChestBlockEntity inventory = helper.getBlockEntity(chest, ChestBlockEntity.class);
			inventory.setItem(1, new ItemStack(Items.STONE, 64));
			inventory.setItem(2, new ItemStack(Items.STONE, 30));
		});
		helper.runAfterDelay(80, () -> {
			ThresholdSwitchBlockEntity be = helper.getBlockEntity(switchPos, ThresholdSwitchBlockEntity.class);
			helper.assertTrue(be.currentLevel == 124, "level " + be.currentLevel);
			helper.assertTrue(be.isPowered(), "not powered above the threshold");
			helper.assertTrue(helper.getLevel().getSignal(helper.absolutePos(switchPos), Direction.WEST) == 15, "no signal towards the lamp");
			helper.assertTrue(helper.getLevel().getSignal(helper.absolutePos(switchPos), Direction.UP) == 0, "signal into the container");
			helper.assertTrue(helper.getBlockState(lamp).getValue(RedstoneLampBlock.LIT), "the lamp is not lit");
			// between the two thresholds the switch keeps its state
			ChestBlockEntity inventory = helper.getBlockEntity(chest, ChestBlockEntity.class);
			inventory.setItem(1, ItemStack.EMPTY);
		});
		helper.runAfterDelay(120, () -> {
			ThresholdSwitchBlockEntity be = helper.getBlockEntity(switchPos, ThresholdSwitchBlockEntity.class);
			helper.assertTrue(be.currentLevel == 60, "level " + be.currentLevel);
			helper.assertTrue(be.isPowered(), "switched off before reaching the lower threshold");
			helper.getBlockEntity(chest, ChestBlockEntity.class).setItem(2, ItemStack.EMPTY);
		});
		helper.runAfterDelay(160, () -> {
			ThresholdSwitchBlockEntity be = helper.getBlockEntity(switchPos, ThresholdSwitchBlockEntity.class);
			helper.assertTrue(be.currentLevel == 30, "level " + be.currentLevel);
			helper.assertTrue(!be.isPowered(), "still powered below the lower threshold");
			helper.assertTrue(!helper.getBlockState(lamp).getValue(RedstoneLampBlock.LIT), "the lamp is still lit");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 120)
	public void thresholdSwitchCanBeInvertedAndOnlyCountsItsFilter(GameTestHelper helper) {
		floor(helper);
		BlockPos chest = new BlockPos(3, 1, 3);
		BlockPos switchPos = new BlockPos(3, 2, 3);
		helper.setBlock(chest, Blocks.CHEST);
		helper.setBlock(switchPos, switchState());
		helper.runAfterDelay(3, () -> {
			ThresholdSwitchBlockEntity be = helper.getBlockEntity(switchPos, ThresholdSwitchBlockEntity.class);
			be.onWhenAbove = 20;
			be.offWhenBelow = 10;
			be.getBehaviour(FilteringBehaviour.TYPE).setFilter(new ItemStack(Items.GOLD_INGOT));
			ChestBlockEntity inventory = helper.getBlockEntity(chest, ChestBlockEntity.class);
			inventory.setItem(0, new ItemStack(Items.IRON_INGOT, 64));
			inventory.setItem(1, new ItemStack(Items.GOLD_INGOT, 5));
		});
		helper.runAfterDelay(40, () -> {
			ThresholdSwitchBlockEntity be = helper.getBlockEntity(switchPos, ThresholdSwitchBlockEntity.class);
			helper.assertTrue(be.currentLevel == 5, "counted " + be.currentLevel);
			helper.assertTrue(!be.isPowered(), "powered");
			be.setInverted(true);
		});
		helper.runAfterDelay(50, () -> {
			ThresholdSwitchBlockEntity be = helper.getBlockEntity(switchPos, ThresholdSwitchBlockEntity.class);
			helper.assertTrue(be.isPowered(), "an inverted switch below the threshold should output");
			helper.assertTrue(helper.getLevel().getSignal(helper.absolutePos(switchPos), Direction.EAST) == 15, "no output");
			helper.getBlockEntity(chest, ChestBlockEntity.class).setItem(2, new ItemStack(Items.GOLD_INGOT, 20));
		});
		helper.runAfterDelay(90, () -> {
			ThresholdSwitchBlockEntity be = helper.getBlockEntity(switchPos, ThresholdSwitchBlockEntity.class);
			helper.assertTrue(be.currentLevel == 25, "counted " + be.currentLevel);
			helper.assertTrue(!be.isPowered(), "an inverted switch above the threshold should be off");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void thresholdSwitchGuiEditsTheThresholds(GameTestHelper helper) {
		floor(helper);
		BlockPos chest = new BlockPos(3, 1, 3);
		BlockPos switchPos = new BlockPos(3, 2, 3);
		helper.setBlock(chest, Blocks.CHEST);
		helper.setBlock(switchPos, switchState());
		helper.runAfterDelay(12, () -> {
			ThresholdSwitchBlockEntity be = helper.getBlockEntity(switchPos, ThresholdSwitchBlockEntity.class);
			ServerPlayer player = player(helper);
			ThresholdSwitchGui gui = new ThresholdSwitchGui(player, be);
			helper.assertTrue(be.onWhenAbove == 128 && be.offWhenBelow == 64, "defaults " + be.onWhenAbove + " " + be.offWhenBelow);
			// row 1: upper threshold: -10, -1, value, +1, +10; row 2: lower threshold
			click(gui, 9 + 6, ClickType.MOUSE_LEFT);
			helper.assertTrue(be.onWhenAbove == 138, "upper " + be.onWhenAbove);
			click(gui, 9 + 3, ClickType.MOUSE_LEFT);
			helper.assertTrue(be.onWhenAbove == 137, "upper " + be.onWhenAbove);
			click(gui, 18 + 2, ClickType.MOUSE_LEFT);
			helper.assertTrue(be.offWhenBelow == 54, "lower " + be.offWhenBelow);
			// the lower threshold pushes the upper one up when it runs into it
			for (int i = 0; i < 20; i++)
				click(gui, 18 + 6, ClickType.MOUSE_LEFT);
			helper.assertTrue(be.offWhenBelow < be.onWhenAbove, "thresholds " + be.offWhenBelow + " " + be.onWhenAbove);
			// stacks as unit
			click(gui, 7, ClickType.MOUSE_LEFT);
			helper.assertTrue(be.inStacks, "not counting stacks");
			helper.assertTrue(be.onWhenAbove % 64 == 0 && be.offWhenBelow % 64 == 0, "not multiples of a stack: " + be.offWhenBelow + " " + be.onWhenAbove);
			// invert
			click(gui, 3 * 9 + 1, ClickType.MOUSE_LEFT);
			helper.assertTrue(be.isInverted(), "not inverted");
			helper.succeed();
		});
	}

	private static void click(ThresholdSwitchGui gui, int slot, ClickType type) {
		gui.getGuiElement(slot).getGuiCallback().click(slot, type, ContainerInput.PICKUP, gui);
	}

	// ---- rose quartz lamp, contacts, desk bell ----

	@GameTest(maxTicks = 60)
	public void roseQuartzLampStaysLitAfterTheSignalEnds(GameTestHelper helper) {
		floor(helper);
		BlockPos lamp = new BlockPos(3, 1, 3);
		helper.setBlock(lamp, RedstoneRegistry.ROSE_QUARTZ_LAMP);
		helper.runAfterDelay(3, () -> helper.setBlock(lamp.north(), Blocks.REDSTONE_BLOCK));
		helper.runAfterDelay(10, () -> {
			var state = helper.getBlockState(lamp);
			helper.assertTrue(state.getValue(dev.createpolymer.redstone.RoseQuartzLampBlock.POWERING), "not lit");
			helper.assertTrue(state.getLightEmission() == 15, "light " + state.getLightEmission());
			helper.setBlock(lamp.north(), Blocks.AIR);
		});
		helper.runAfterDelay(20, () -> {
			var state = helper.getBlockState(lamp);
			helper.assertTrue(state.getValue(dev.createpolymer.redstone.RoseQuartzLampBlock.POWERING), "went dark with the signal");
			helper.assertTrue(!state.getValue(dev.createpolymer.redstone.RoseQuartzLampBlock.POWERED), "still sees the signal");
			// a wrench turns it off
			ItemStack wrench = new ItemStack(ModItems.WRENCH);
			ServerPlayer player = player(helper);
			player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
			wrench.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(helper, lamp, Direction.NORTH)));
			helper.assertTrue(!helper.getBlockState(lamp).getValue(dev.createpolymer.redstone.RoseQuartzLampBlock.POWERING), "the wrench did not toggle the lamp");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void roseQuartzLampsFocusTheSignalOnTheLastOne(GameTestHelper helper) {
		floor(helper);
		BlockPos a = new BlockPos(2, 1, 3);
		BlockPos b = new BlockPos(3, 1, 3);
		helper.setBlock(a, RedstoneRegistry.ROSE_QUARTZ_LAMP);
		helper.setBlock(b, RedstoneRegistry.ROSE_QUARTZ_LAMP);
		helper.runAfterDelay(3, () -> helper.setBlock(a.west(), Blocks.REDSTONE_BLOCK));
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(helper.getBlockState(a).getValue(dev.createpolymer.redstone.RoseQuartzLampBlock.POWERING), "a not lit");
			helper.assertTrue(!helper.getBlockState(b).getValue(dev.createpolymer.redstone.RoseQuartzLampBlock.POWERING), "b lit");
			helper.setBlock(b.east(), Blocks.REDSTONE_BLOCK);
		});
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(!helper.getBlockState(a).getValue(dev.createpolymer.redstone.RoseQuartzLampBlock.POWERING), "a still lit");
			helper.assertTrue(helper.getBlockState(b).getValue(dev.createpolymer.redstone.RoseQuartzLampBlock.POWERING), "b not lit");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void redstoneContactsFacingEachOtherPowerEachOther(GameTestHelper helper) {
		floor(helper);
		BlockPos a = new BlockPos(2, 1, 3);
		BlockPos b = new BlockPos(3, 1, 3);
		helper.setBlock(a, RedstoneRegistry.REDSTONE_CONTACT.defaultBlockState().setValue(RedstoneContactBlock.FACING, Direction.EAST));
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(!helper.getBlockState(a).getValue(RedstoneContactBlock.POWERED), "powered alone");
			helper.setBlock(b, RedstoneRegistry.REDSTONE_CONTACT.defaultBlockState().setValue(RedstoneContactBlock.FACING, Direction.WEST));
		});
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(helper.getBlockState(a).getValue(RedstoneContactBlock.POWERED), "a not powered");
			helper.assertTrue(helper.getBlockState(b).getValue(RedstoneContactBlock.POWERED), "b not powered");
			// the signal goes out on every side but the one facing the other contact
			helper.assertTrue(helper.getLevel().getSignal(helper.absolutePos(a), Direction.UP) == 15, "no signal upwards");
			helper.assertTrue(helper.getLevel().getSignal(helper.absolutePos(a), Direction.EAST) == 15, "no signal to the back");
			helper.assertTrue(helper.getLevel().getSignal(helper.absolutePos(a), Direction.WEST) == 0, "signal into the other contact");
			helper.setBlock(b, Blocks.AIR);
		});
		helper.runAfterDelay(14, () -> {
			helper.assertTrue(!helper.getBlockState(a).getValue(RedstoneContactBlock.POWERED), "a stayed powered");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void redstoneContactsFacingAwayDoNotPower(GameTestHelper helper) {
		floor(helper);
		BlockPos a = new BlockPos(2, 1, 3);
		BlockPos b = new BlockPos(3, 1, 3);
		helper.setBlock(a, RedstoneRegistry.REDSTONE_CONTACT.defaultBlockState().setValue(RedstoneContactBlock.FACING, Direction.WEST));
		helper.setBlock(b, RedstoneRegistry.REDSTONE_CONTACT.defaultBlockState().setValue(RedstoneContactBlock.FACING, Direction.EAST));
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(!helper.getBlockState(a).getValue(RedstoneContactBlock.POWERED) && !helper.getBlockState(b).getValue(RedstoneContactBlock.POWERED), "powered back to back");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void deskBellPowersForAMomentWhenUsed(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		helper.setBlock(pos, RedstoneRegistry.DESK_BELL.defaultBlockState().setValue(DeskBellBlock.FACING, Direction.UP));
		helper.runAfterDelay(3, () -> {
			var result = helper.getBlockState(pos).useWithoutItem(helper.getLevel(), player(helper), hit(helper, pos, Direction.UP));
			helper.assertTrue(result == InteractionResult.SUCCESS, "result " + result);
			helper.assertTrue(helper.getBlockState(pos).getValue(DeskBellBlock.POWERED), "not powered");
			helper.assertTrue(helper.getLevel().getSignal(helper.absolutePos(pos), Direction.EAST) == 15, "no signal");
		});
		helper.runAfterDelay(15, () -> helper.assertTrue(helper.getBlockState(pos).getValue(DeskBellBlock.POWERED), "released too early"));
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(!helper.getBlockState(pos).getValue(DeskBellBlock.POWERED), "still powered");
			helper.succeed();
		});
	}

	// ---- wrench ----

	@GameTest(maxTicks = 40)
	public void wrenchTurnsDiodesAndPicksThemUp(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		helper.setBlock(pos, diode(RedstoneRegistry.PULSE_TIMER));
		helper.runAfterDelay(3, () -> {
			ItemStack wrench = new ItemStack(ModItems.WRENCH);
			ServerPlayer player = player(helper);
			player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
			wrench.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(helper, pos, Direction.UP)));
			helper.assertTrue(helper.getBlockState(pos).getValue(BrassDiodeBlock.FACING) == Direction.EAST, "facing " + helper.getBlockState(pos).getValue(BrassDiodeBlock.FACING));
			helper.succeed();
		});
	}

	// ---- what the client sees ----

	@GameTest(maxTicks = 20)
	public void thinBlocksAreNotShownAsBarriers(GameTestHelper helper) {
		for (Direction side : Direction.values()) {
			BlockState plate = RedstoneClientStates.plate(side, false);
			helper.assertTrue(!plate.is(Blocks.BARRIER), "no plate for " + side);
			VoxelShape shape = plate.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
			helper.assertTrue(!shape.isEmpty(), "plate for " + side + " has no collision");
			Direction.Axis axis = side.getAxis();
			double thickness = shape.max(axis) - shape.min(axis);
			helper.assertTrue(thickness > 0.05 && thickness <= 0.25, "plate for " + side + " is " + thickness + " thick");
			boolean positive = side.getAxisDirection() == Direction.AxisDirection.POSITIVE;
			helper.assertTrue(positive ? shape.max(axis) > .99 : shape.min(axis) < .01, "the plate for " + side + " is not against that face: " + shape.bounds());
			// the other two axes are covered completely
			for (Direction.Axis other : Direction.Axis.values())
				if (other != axis)
					helper.assertTrue(shape.max(other) - shape.min(other) > .99, "plate for " + side + " does not span " + other);
			helper.assertTrue(!RedstoneClientStates.plate(side, true).is(Blocks.BARRIER), "no waterlogged plate for " + side);
		}
		helper.assertTrue(!RedstoneClientStates.slab(false, false).is(Blocks.BARRIER), "bottom half");
		helper.assertTrue(!RedstoneClientStates.slab(true, true).is(Blocks.BARRIER), "top half, waterlogged");
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void redstoneBlocksUseTheClientShapeThatFitsThem(GameTestHelper helper) {
		BlockState lever = lever();
		var polymer = (eu.pb4.polymer.core.api.block.PolymerBlock) RedstoneRegistry.ANALOG_LEVER;
		BlockState client = polymer.getPolymerBlockState(lever, null);
		helper.assertTrue(client.equals(RedstoneClientStates.plate(Direction.DOWN, false)), "a floor lever is a plate on the floor: " + client);
		BlockState wallLever = lever.setValue(AnalogLeverBlock.FACE, AttachFace.WALL).setValue(AnalogLeverBlock.FACING, Direction.EAST);
		helper.assertTrue(polymer.getPolymerBlockState(wallLever, null).equals(RedstoneClientStates.plate(Direction.WEST, false)),
			"a wall lever facing east hangs on the west block");

		var diode = (eu.pb4.polymer.core.api.block.PolymerBlock) RedstoneRegistry.PULSE_REPEATER;
		helper.assertTrue(diode.getPolymerBlockState(diode(RedstoneRegistry.PULSE_REPEATER), null).equals(RedstoneClientStates.plate(Direction.DOWN, false)), "diode");

		var link = (eu.pb4.polymer.core.api.block.PolymerBlock) RedstoneRegistry.REDSTONE_LINK;
		helper.assertTrue(link.getPolymerBlockState(link(false).setValue(RedstoneLinkBlock.FACING, Direction.NORTH), null)
			.equals(RedstoneClientStates.plate(Direction.SOUTH, false)), "link");

		// the observer and the switch are full blocks
		var observer = (eu.pb4.polymer.core.api.block.PolymerBlock) RedstoneRegistry.SMART_OBSERVER;
		helper.assertTrue(observer.getPolymerBlockState(RedstoneRegistry.SMART_OBSERVER.defaultBlockState(), null).is(Blocks.BARRIER), "observer");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void everyBlockHasItsItemAndAModel(GameTestHelper helper) {
		floor(helper);
		for (net.minecraft.world.level.block.Block block : List.of(RedstoneRegistry.ROSE_QUARTZ_LAMP, RedstoneRegistry.REDSTONE_CONTACT,
			RedstoneRegistry.DESK_BELL, RedstoneRegistry.PULSE_REPEATER, RedstoneRegistry.POWERED_LATCH, RedstoneRegistry.POWERED_TOGGLE_LATCH,
			RedstoneRegistry.SMART_OBSERVER, RedstoneRegistry.THRESHOLD_SWITCH, RedstoneRegistry.ORANGE_NIXIE_TUBE,
			RedstoneRegistry.REDSTONE_LINK, RedstoneRegistry.ANALOG_LEVER)) {
			helper.assertTrue(block.asItem() != Items.AIR, "no item for " + block);
		}
		// every block that is drawn by display entities gets a holder with at least one element
		List<net.minecraft.world.level.block.Block> drawn = List.of(RedstoneRegistry.REDSTONE_CONTACT, RedstoneRegistry.DESK_BELL,
			RedstoneRegistry.PULSE_REPEATER, RedstoneRegistry.POWERED_LATCH, RedstoneRegistry.POWERED_TOGGLE_LATCH,
			RedstoneRegistry.SMART_OBSERVER, RedstoneRegistry.THRESHOLD_SWITCH, RedstoneRegistry.ORANGE_NIXIE_TUBE);
		for (int i = 0; i < drawn.size(); i++)
			helper.setBlock(new BlockPos(1 + i % 6, 1, 1 + i / 6 * 3), drawn.get(i).defaultBlockState());
		helper.runAfterDelay(3, () -> {
			for (int i = 0; i < drawn.size(); i++) {
				BlockPos p = new BlockPos(1 + i % 6, 1, 1 + i / 6 * 3);
				BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(p));
				helper.assertTrue(attachment != null && !attachment.holder().getElements().isEmpty(), "no model for " + drawn.get(i));
			}
			helper.succeed();
		});
	}

}
