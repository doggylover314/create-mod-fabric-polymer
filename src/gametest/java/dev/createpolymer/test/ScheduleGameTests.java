package dev.createpolymer.test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import dev.createpolymer.api.behaviour.interaction.ConductorBlockInteractionBehavior;
import dev.createpolymer.contraptions.actors.trainControls.ControlsBlock;
import dev.createpolymer.contraptions.glue.SuperGlueEntity;
import dev.createpolymer.foundation.item.handler.ItemHandlerHelper;
import dev.createpolymer.processing.burner.BlazeBurnerBlock;
import dev.createpolymer.processing.burner.BlazeBurnerBlock.HeatLevel;
import dev.createpolymer.redstone.displayLink.DisplayLinkBlock;
import dev.createpolymer.redstone.displayLink.DisplayLinkBlockEntity;
import dev.createpolymer.redstone.displayLink.DisplayLinkContext;
import dev.createpolymer.redstone.displayLink.target.DisplayTargetStats;
import dev.createpolymer.registry.feature.ActorsRegistry;
import dev.createpolymer.registry.feature.BurnerRegistry;
import dev.createpolymer.registry.feature.DisplayRegistry;
import dev.createpolymer.registry.feature.SchedulesRegistry;
import dev.createpolymer.registry.feature.TrackRegistry;
import dev.createpolymer.registry.feature.TrainsRegistry;
import dev.createpolymer.trains.RailwaySavedData;
import dev.createpolymer.trains.bogey.AbstractBogeyBlock;
import dev.createpolymer.trains.display.GlobalTrainDisplayData;
import dev.createpolymer.trains.display.GlobalTrainDisplayData.TrainDeparturePrediction;
import dev.createpolymer.trains.entity.CarriageContraptionEntity;
import dev.createpolymer.trains.entity.Train;
import dev.createpolymer.trains.schedule.Schedule;
import dev.createpolymer.trains.schedule.ScheduleConditionsGui;
import dev.createpolymer.trains.schedule.ScheduleConfigBuilder;
import dev.createpolymer.trains.schedule.ScheduleEditor;
import dev.createpolymer.trains.schedule.ScheduleEntry;
import dev.createpolymer.trains.schedule.ScheduleGui;
import dev.createpolymer.trains.schedule.ScheduleInputGui;
import dev.createpolymer.trains.schedule.ScheduleItem;
import dev.createpolymer.trains.schedule.ScheduleItemEntityInteraction;
import dev.createpolymer.trains.schedule.ScheduleItemGui;
import dev.createpolymer.trains.schedule.ScheduleRuntime.State;
import dev.createpolymer.trains.schedule.condition.CargoThresholdCondition.Ops;
import dev.createpolymer.trains.schedule.condition.FluidThresholdCondition;
import dev.createpolymer.trains.schedule.condition.IdleCargoCondition;
import dev.createpolymer.trains.schedule.condition.ItemThresholdCondition;
import dev.createpolymer.trains.schedule.condition.PlayerPassengerCondition;
import dev.createpolymer.trains.schedule.condition.RedstoneLinkCondition;
import dev.createpolymer.trains.schedule.condition.ScheduleWaitCondition;
import dev.createpolymer.trains.schedule.condition.ScheduledDelay;
import dev.createpolymer.trains.schedule.condition.StationPoweredCondition;
import dev.createpolymer.trains.schedule.condition.StationUnloadedCondition;
import dev.createpolymer.trains.schedule.condition.TimeOfDayCondition;
import dev.createpolymer.trains.schedule.condition.TimedWaitCondition.TimeUnit;
import dev.createpolymer.trains.schedule.destination.ChangeThrottleInstruction;
import dev.createpolymer.trains.schedule.destination.ChangeTitleInstruction;
import dev.createpolymer.trains.schedule.destination.DeliverPackagesInstruction;
import dev.createpolymer.trains.schedule.destination.DestinationInstruction;
import dev.createpolymer.trains.schedule.destination.FetchPackagesInstruction;
import dev.createpolymer.trains.schedule.destination.ScheduleInstruction;
import dev.createpolymer.trains.station.GlobalStation;
import dev.createpolymer.trains.station.StationBlockEntity;
import dev.createpolymer.trains.station.StationGui;
import dev.createpolymer.trains.track.TrackBlock;
import dev.createpolymer.trains.track.TrackShape;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.SguiUtils;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * Train schedules: the same line of track as the train tests with two stations, Alpha (east) and Bravo (west), and a
 * train that is assembled at Alpha. Trains that have to come back are built with controls and a blaze burner at both
 * ends. The structure is 40 blocks long, the track runs along x at z = 3.
 */
public class ScheduleGameTests {

	private static final String STRUCTURE = "create_polymer_test:trains";
	private static final int Z = 3;
	private static final int TRACK_END = 39;
	private static final BlockPos STATION_A = new BlockPos(26, 1, Z + 1);
	private static final BlockPos STATION_B = new BlockPos(8, 1, Z + 1);
	private static final int FIRST_BOGEY_X = 27;

	// ---- the world ----

	private static BlockState track() {
		return TrackRegistry.TRACK.defaultBlockState()
			.setValue(TrackBlock.SHAPE, TrackShape.XO);
	}

	private static void line(GameTestHelper helper) {
		for (int x = 0; x <= TRACK_END; x++)
			helper.setBlock(new BlockPos(x, 1, Z), track());
	}

	/**
	 * A station next to the track that targets it. Trains stop at a station only when they drive in the direction it
	 * faces: {@code westbound} stations are for trains that drive towards x = 0.
	 */
	private static StationBlockEntity station(GameTestHelper helper, BlockPos stationPos, boolean westbound) {
		helper.setBlock(stationPos, TrainsRegistry.TRACK_STATION);
		StationBlockEntity be = helper.getBlockEntity(stationPos, StationBlockEntity.class);
		BlockPos abs = helper.absolutePos(stationPos);
		BlockPos trackPos = helper.absolutePos(new BlockPos(stationPos.getX(), 1, Z));
		CompoundTag data = new CompoundTag();
		data.store("TargetTrack", BlockPos.CODEC, trackPos.subtract(abs));
		data.putBoolean("TargetDirection", westbound);
		be.edgePoint.read(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel()
			.registryAccess(), data));
		return be;
	}

	private static BlockState bogey() {
		return TrainsRegistry.SMALL_BOGEY.defaultBlockState()
			.setValue(AbstractBogeyBlock.AXIS, Direction.Axis.X);
	}

	private enum Carriage {
		/** Bogey, controls, blaze burner, bogey: it can only drive west. */
		SINGLE_ENDED,
		/**
		 * Controls and a burner at both ends, so it can drive either way, and a chest on the burner. It stands west of
		 * Alpha, which is for trains that drive east, so it can drive back to Alpha from Bravo.
		 */
		DOUBLE_ENDED,
		/** Bogey, controls, a seat, bogey. */
		SEATED
	}

	private static void carriageBlocks(GameTestHelper helper, Carriage kind, int x) {
		helper.setBlock(new BlockPos(x, 2, Z), bogey());
		helper.setBlock(new BlockPos(x + 1, 2, Z), TrainsRegistry.CONTROLS.defaultBlockState()
			.setValue(ControlsBlock.FACING, Direction.EAST));
		int end;
		switch (kind) {
		case SINGLE_ENDED -> {
			helper.setBlock(new BlockPos(x + 2, 2, Z), BurnerRegistry.BLAZE_BURNER.defaultBlockState()
				.setValue(BlazeBurnerBlock.HEAT_LEVEL, HeatLevel.KINDLED));
			end = x + 3;
		}
		case DOUBLE_ENDED -> {
			helper.setBlock(new BlockPos(x + 2, 2, Z), BurnerRegistry.BLAZE_BURNER.defaultBlockState()
				.setValue(BlazeBurnerBlock.HEAT_LEVEL, HeatLevel.KINDLED));
			helper.setBlock(new BlockPos(x + 3, 2, Z), TrainsRegistry.CONTROLS.defaultBlockState()
				.setValue(ControlsBlock.FACING, Direction.WEST));
			helper.setBlock(new BlockPos(x + 2, 3, Z), Blocks.CHEST);
			end = x + 4;
		}
		default -> {
			helper.setBlock(new BlockPos(x + 2, 2, Z), ActorsRegistry.SEATS.get(DyeColor.RED));
			end = x + 3;
		}
		}
		helper.setBlock(new BlockPos(end, 2, Z), bogey());
		helper.getLevel()
			.addFreshEntity(new SuperGlueEntity(helper.getLevel(), SuperGlueEntity.span(
				helper.absolutePos(new BlockPos(x, 2, Z)),
				helper.absolutePos(new BlockPos(end, kind == Carriage.DOUBLE_ENDED ? 3 : 2, Z)))));
	}

	private static class Layout {
		StationBlockEntity a, b;
		Train train;
		CarriageContraptionEntity entity;
		/** The x of the first block of the carriage. */
		int x;
	}

	private static void assembled(GameTestHelper helper, Carriage kind, Consumer<Layout> then) {
		assembled(helper, kind, ItemStack.EMPTY, then);
	}

	/**
	 * Builds the track and both stations, then the carriage, and assembles it at Alpha. {@code autoSchedule} is put on
	 * Alpha before the train is there.
	 */
	private static void assembled(GameTestHelper helper, Carriage kind, ItemStack autoSchedule, Consumer<Layout> then) {
		Layout layout = new Layout();
		line(helper);
		boolean doubleEnded = kind == Carriage.DOUBLE_ENDED;
		layout.x = doubleEnded ? STATION_A.getX() - 5 : FIRST_BOGEY_X;
		layout.a = station(helper, STATION_A, !doubleEnded);
		layout.b = station(helper, STATION_B, true);
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(layout.a.getStation() != null, "station A has no edge point");
			helper.assertTrue(layout.b.getStation() != null, "station B has no edge point");
			layout.a.updateName("Alpha");
			layout.b.updateName("Bravo");
			if (!autoSchedule.isEmpty())
				layout.a.setAutoSchedule(autoSchedule);
			carriageBlocks(helper, kind, layout.x);
			helper.assertTrue(layout.a.enterAssemblyMode(null), "the station would not enter assembly mode");
			layout.a.assemble(UUID.randomUUID());
			helper.assertTrue(layout.a.getLastException() == null, "assembly failed: "
				+ (layout.a.getLastException() == null ? "" : layout.a.getLastException().component.getString()));
			layout.train = layout.a.getStation()
				.getPresentTrain();
			helper.assertTrue(layout.train != null, "no train at the station after assembling");
			layout.a.exitAssemblyMode();
		});
		helper.runAfterDelay(16, () -> {
			layout.entity = layout.train.carriages.get(0)
				.anyAvailableEntity();
			helper.assertTrue(layout.entity != null, "no carriage entity");
			then.accept(layout);
		});
	}

	// ---- schedules ----

	private static DestinationInstruction destination(String filter) {
		DestinationInstruction destination = new DestinationInstruction();
		destination.getData()
			.putString("Text", filter);
		return destination;
	}

	private static ScheduledDelay delay(int value, TimeUnit unit) {
		ScheduledDelay delay = new ScheduledDelay();
		delay.getData()
			.putInt("Value", value);
		delay.getData()
			.putInt("TimeUnit", unit.ordinal());
		return delay;
	}

	private static ScheduleEntry stop(ScheduleInstruction instruction, ScheduleWaitCondition... conditions) {
		ScheduleEntry entry = new ScheduleEntry();
		entry.instruction = instruction;
		if (conditions.length > 0)
			entry.conditions.add(new ArrayList<>(List.of(conditions)));
		return entry;
	}

	private static Schedule schedule(boolean cyclic, ScheduleEntry... entries) {
		return new Schedule(new ArrayList<>(List.of(entries)), cyclic, 0);
	}

	private static ItemStack itemOf(GameTestHelper helper, Schedule schedule) {
		ItemStack stack = new ItemStack(SchedulesRegistry.SCHEDULE);
		stack.set(SchedulesRegistry.TRAIN_SCHEDULE, schedule.write(helper.getLevel()
			.registryAccess()));
		return stack;
	}

	@SuppressWarnings("removal")
	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setShiftKeyDown(false);
		return player;
	}

	// ---- driving ----

	@GameTest(structure = STRUCTURE, maxTicks = 3000)
	public void scheduleWithTwoStopsDrivesTheTrainBackAndForth(GameTestHelper helper) {
		assembled(helper, Carriage.DOUBLE_ENDED, layout -> {
			Train train = layout.train;
			helper.assertTrue(train.doubleEnded, "the train is not double ended");
			helper.assertTrue(train.hasForwardConductor() && train.hasBackwardConductor(), "conductors");
			helper.assertTrue(train.runtime.paused && train.runtime.getSchedule() == null, "a fresh train has a schedule");

			train.runtime.setSchedule(schedule(true, stop(destination("Bravo"), delay(1, TimeUnit.SECONDS)),
				stop(destination("Alpha"), delay(1, TimeUnit.SECONDS))), false);
			helper.assertTrue(!train.runtime.paused, "the schedule is paused");
			helper.assertTrue(!train.runtime.isAutoSchedule, "an auto-schedule");

			GlobalStation alpha = layout.a.getStation();
			GlobalStation bravo = layout.b.getStation();
			// the stations the train stood at, in order
			List<GlobalStation> visited = new ArrayList<>();
			helper.succeedWhen(() -> {
				GlobalStation at = train.getCurrentStation();
				if (at != null && (visited.isEmpty() || visited.get(visited.size() - 1) != at))
					visited.add(at);
				helper.assertTrue(visited.size() >= 4, "the train went to " + visited.stream()
					.map(s -> s.name)
					.toList() + " (at " + (at == null ? "no station" : at.name) + ", entry " + train.runtime.currentEntry
					+ ", " + train.runtime.state + ", status " + train.status.navigation + ")");
				helper.assertTrue(visited.get(0) == alpha && visited.get(1) == bravo && visited.get(2) == alpha
					&& visited.get(3) == bravo, "the order was " + visited.stream()
						.map(s -> s.name)
						.toList());
			});
		});
	}

	@GameTest(structure = STRUCTURE, maxTicks = 400)
	public void delayConditionWaitsTheRightNumberOfTicks(GameTestHelper helper) {
		assembled(helper, Carriage.SINGLE_ENDED, layout -> {
			Train train = layout.train;
			// a stop where the train already is: it waits right away; the schedule is not a loop, so it ends there
			train.runtime.setSchedule(schedule(false, stop(destination("Alpha"), delay(2, TimeUnit.SECONDS))), false);
			long[] arrived = { -1 };
			long[] left = { -1 };
			helper.succeedWhen(() -> {
				if (arrived[0] == -1 && train.runtime.state == State.POST_TRANSIT)
					arrived[0] = helper.getTick();
				if (arrived[0] != -1 && left[0] == -1 && train.runtime.state == State.PRE_TRANSIT)
					left[0] = helper.getTick();
				helper.assertTrue(left[0] != -1, "still waiting");
				long waited = left[0] - arrived[0];
				helper.assertTrue(waited >= 40 && waited <= 43, "the train waited " + waited + " ticks for a delay of 40");
				helper.assertTrue(train.runtime.currentEntry == 1, "the entry is " + train.runtime.currentEntry);
			});
		});
	}

	@GameTest(structure = STRUCTURE, maxTicks = 500)
	public void itemThresholdConditionWaitsForTheCargo(GameTestHelper helper) {
		assembled(helper, Carriage.DOUBLE_ENDED, layout -> {
			Train train = layout.train;
			var storage = train.carriages.get(0).storage;
			helper.assertTrue(storage.getAllItems()
				.getSlots() >= 27, "the chest is not in the cargo: " + storage.getAllItems()
					.getSlots() + " slots");

			ItemThresholdCondition condition = new ItemThresholdCondition();
			condition.setItem(0, new ItemStack(Items.DIAMOND));
			condition.getData()
				.putInt("Operator", Ops.GREATER.ordinal());
			condition.getData()
				.putString("Threshold", "2");
			train.runtime.setSchedule(schedule(false, stop(destination("Alpha"), condition)), false);

			// 2 diamonds are not more than 2 and gold does not count
			ItemHandlerHelper.insertItemStacked(storage.getAllItems(), new ItemStack(Items.DIAMOND, 2), false);
			ItemHandlerHelper.insertItemStacked(storage.getAllItems(), new ItemStack(Items.GOLD_INGOT, 40), false);
			helper.runAfterDelay(70, () -> {
				helper.assertTrue(train.runtime.state == State.POST_TRANSIT, "the train left with 2 diamonds: " + train.runtime.state);
				helper.assertTrue(train.runtime.getWaitingStatus(helper.getLevel())
					.getString()
					.contains("2"), "status: " + train.runtime.getWaitingStatus(helper.getLevel())
						.getString());
				ItemHandlerHelper.insertItemStacked(storage.getAllItems(), new ItemStack(Items.DIAMOND, 1), false);
			});
			helper.succeedWhen(() -> {
				helper.assertTrue(helper.getTick() > 71, "too early");
				helper.assertTrue(train.runtime.currentEntry == 1 || train.runtime.completed, "the train is still waiting for 3 diamonds");
			});
		});
	}

	@GameTest(structure = STRUCTURE, maxTicks = 500)
	public void stationPoweredConditionWaitsForRedstone(GameTestHelper helper) {
		assembled(helper, Carriage.SINGLE_ENDED, layout -> {
			Train train = layout.train;
			train.runtime.setSchedule(schedule(false, stop(destination("Alpha"), new StationPoweredCondition())), false);
			helper.runAfterDelay(40, () -> {
				helper.assertTrue(train.runtime.state == State.POST_TRANSIT, "the train left an unpowered station");
				helper.setBlock(STATION_A.south(), Blocks.REDSTONE_BLOCK);
			});
			helper.succeedWhen(() -> {
				helper.assertTrue(helper.getTick() > 41, "too early");
				helper.assertTrue(train.runtime.completed, "the train is still waiting for redstone");
			});
		});
	}

	@GameTest(structure = STRUCTURE, maxTicks = 600)
	public void timeOfDayPlayersAndIdleConditionsComplete(GameTestHelper helper) {
		assembled(helper, Carriage.SINGLE_ENDED, layout -> {
			Train train = layout.train;
			helper.setTime(6000);
			TimeOfDayCondition time = new TimeOfDayCondition();
			time.getData()
				.putInt("Hour", 14);
			time.getData()
				.putInt("Minute", 0);
			time.getData()
				.putInt("Rotation", 0);
			PlayerPassengerCondition nobody = new PlayerPassengerCondition();
			nobody.getData()
				.putInt("Count", 0);
			nobody.getData()
				.putInt("Exact", 0);
			ScheduleEntry entry = stop(destination("Alpha"), time, nobody);
			entry.conditions.add(new ArrayList<>(List.of(delay(10, TimeUnit.MINUTES))));
			train.runtime.setSchedule(schedule(false, entry), false);

			helper.runAfterDelay(30, () -> {
				helper.assertTrue(train.runtime.state == State.POST_TRANSIT, "the train left before it was 14:00");
				helper.assertTrue(train.runtime.getWaitingStatus(helper.getLevel())
					.getString()
					.contains("2"), "status: " + train.runtime.getWaitingStatus(helper.getLevel())
						.getString());
				// 14:00 is 8000 ticks into the day
				helper.setTime(8000);
			});
			helper.succeedWhen(() -> {
				helper.assertTrue(helper.getTick() > 31, "too early");
				helper.assertTrue(train.runtime.completed, "the train is still waiting (" + train.runtime.conditionProgress + ")");
			});
		});
	}

	@GameTest(structure = STRUCTURE, maxTicks = 400)
	public void idleCargoConditionCountsTheTicksWithoutChange(GameTestHelper helper) {
		assembled(helper, Carriage.SINGLE_ENDED, layout -> {
			Train train = layout.train;
			// the first group never ends, the second one is the cargo staying still for 2 seconds
			TimeOfDayCondition never = new TimeOfDayCondition();
			never.getData()
				.putInt("Rotation", 1);
			ScheduleEntry entry = stop(destination("Alpha"), never);
			IdleCargoCondition idle = new IdleCargoCondition();
			idle.getData()
				.putInt("Value", 2);
			entry.conditions.add(new ArrayList<>(List.of(idle)));
			helper.setTime(1000);
			train.runtime.setSchedule(schedule(false, entry), false);
			long[] arrived = { -1 };
			long[] left = { -1 };
			helper.succeedWhen(() -> {
				if (arrived[0] == -1 && train.runtime.state == State.POST_TRANSIT)
					arrived[0] = helper.getTick();
				if (arrived[0] != -1 && left[0] == -1 && train.runtime.state == State.PRE_TRANSIT)
					left[0] = helper.getTick();
				helper.assertTrue(left[0] != -1, "still waiting");
				long waited = left[0] - arrived[0];
				helper.assertTrue(waited >= 40 && waited <= 46, "the train waited " + waited + " ticks for 2 seconds of idle cargo");
			});
		});
	}

	@GameTest(structure = STRUCTURE, maxTicks = 400)
	public void redstoneLinkConditionFollowsTheFrequencies(GameTestHelper helper) {
		assembled(helper, Carriage.SINGLE_ENDED, layout -> {
			Train train = layout.train;
			RedstoneLinkCondition powered = new RedstoneLinkCondition();
			powered.setItem(0, new ItemStack(Items.REDSTONE));
			powered.setItem(1, new ItemStack(Items.GOLD_NUGGET));
			RedstoneLinkCondition unpowered = new RedstoneLinkCondition();
			unpowered.setItem(0, new ItemStack(Items.REDSTONE));
			unpowered.setItem(1, new ItemStack(Items.GOLD_NUGGET));
			unpowered.getData()
				.putInt("Inverted", 1);
			helper.assertTrue(powered.slotsTargeted() == 2, "two frequencies");
			helper.assertTrue(!powered.tickCompletion(helper.getLevel(), train, new CompoundTag()), "no link is on, but the condition is met");
			helper.assertTrue(unpowered.tickCompletion(helper.getLevel(), train, new CompoundTag()), "no link is on, the condition for none is not met");

			// a train waits for the link to be on
			train.runtime.setSchedule(schedule(false, stop(destination("Alpha"), powered)), false);
			helper.runAfterDelay(50, () -> {
				helper.assertTrue(train.runtime.state == State.POST_TRANSIT, "the train left without a link");
				helper.assertTrue(train.runtime.getWaitingStatus(helper.getLevel())
					.getString()
					.length() > 0, "no status");
				helper.succeed();
			});
		});
	}

	@GameTest(structure = STRUCTURE, maxTicks = 100)
	public void autoScheduleSurvivesSavingTheStation(GameTestHelper helper) {
		line(helper);
		StationBlockEntity a = station(helper, STATION_A, true);
		helper.runAfterDelay(8, () -> {
			a.setAutoSchedule(itemOf(helper, schedule(true, stop(destination("Bravo"), delay(1, TimeUnit.SECONDS)))));
			var registries = helper.getLevel()
				.registryAccess();
			TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
			a.saveWithFullMetadata(out);
			BlockEntity loaded = BlockEntity.loadStatic(a.getBlockPos(), a.getBlockState(), out.buildResult(), registries);
			helper.assertTrue(loaded instanceof StationBlockEntity, "did not load");
			ItemStack kept = ((StationBlockEntity) loaded).getAutoSchedule();
			helper.assertTrue(kept.is(SchedulesRegistry.SCHEDULE), "the schedule was lost: " + kept);
			Schedule schedule = ScheduleItem.getSchedule(registries, kept);
			helper.assertTrue(schedule != null && schedule.entries.size() == 1, "the schedule changed");
			// only schedules rest on a station
			helper.assertTrue(a.getAutoSchedule()
				.is(SchedulesRegistry.SCHEDULE), "the station does not hold it");
			helper.succeed();
		});
	}

	@GameTest(structure = STRUCTURE, maxTicks = 300)
	public void throttleAndTitleInstructionsApply(GameTestHelper helper) {
		assembled(helper, Carriage.SINGLE_ENDED, layout -> {
			Train train = layout.train;
			ChangeThrottleInstruction throttle = new ChangeThrottleInstruction();
			throttle.getData()
				.putInt("Value", 50);
			ChangeTitleInstruction title = new ChangeTitleInstruction();
			title.getData()
				.putString("Text", "Express");
			helper.assertTrue(!throttle.supportsConditions() && !title.supportsConditions(), "these wait for nothing");
			train.runtime.setSchedule(schedule(false, stop(throttle), stop(title),
				stop(destination("Alpha"), delay(1, TimeUnit.SECONDS))), false);
			helper.succeedWhen(() -> {
				helper.assertTrue(train.runtime.currentEntry == 3 || train.runtime.completed, "entry " + train.runtime.currentEntry);
				helper.assertTrue(Math.abs(train.throttle - .5) < 1e-6, "throttle " + train.throttle);
				helper.assertTrue(train.runtime.currentTitle.equals("Express"), "title " + train.runtime.currentTitle);
			});
		});
	}

	@GameTest(structure = STRUCTURE, maxTicks = 400)
	public void trainWithNoMatchingStationReportsAndWaits(GameTestHelper helper) {
		assembled(helper, Carriage.SINGLE_ENDED, layout -> {
			Train train = layout.train;
			train.runtime.setSchedule(schedule(true, stop(destination("Nowhere"), delay(1, TimeUnit.SECONDS))), false);
			helper.runAfterDelay(60, () -> {
				helper.assertTrue(train.runtime.state == State.PRE_TRANSIT && train.runtime.currentEntry == 0, "state " + train.runtime.state);
				helper.assertTrue(train.navigation.destination == null, "the train drives somewhere");
				helper.assertTrue(train.status.navigation, "no message that the destination does not exist");
				helper.assertTrue(train.speed == 0, "the train moves");
				helper.succeed();
			});
		});
	}

	// ---- the item ----

	@GameTest(structure = STRUCTURE, maxTicks = 100)
	public void scheduleItemRoundTripsThroughItsDataComponent(GameTestHelper helper) {
		var registries = helper.getLevel()
			.registryAccess();
		DestinationInstruction first = destination("Central *");
		ItemThresholdCondition items = new ItemThresholdCondition();
		items.setItem(0, new ItemStack(Items.IRON_INGOT));
		items.getData()
			.putInt("Operator", Ops.LESS.ordinal());
		items.getData()
			.putInt("Measure", 1);
		items.getData()
			.putString("Threshold", "12");
		FluidThresholdCondition fluids = new FluidThresholdCondition();
		fluids.setItem(0, new ItemStack(Items.WATER_BUCKET));
		RedstoneLinkCondition link = new RedstoneLinkCondition();
		link.setItem(0, new ItemStack(Items.REDSTONE));
		link.setItem(1, new ItemStack(Items.GOLD_NUGGET));
		link.getData()
			.putInt("Inverted", 1);
		TimeOfDayCondition time = new TimeOfDayCondition();
		time.getData()
			.putInt("Hour", 17);
		time.getData()
			.putInt("Minute", 30);
		time.getData()
			.putInt("Rotation", 2);
		PlayerPassengerCondition players = new PlayerPassengerCondition();
		players.getData()
			.putInt("Count", 3);
		players.getData()
			.putInt("Exact", 1);
		ChangeThrottleInstruction throttle = new ChangeThrottleInstruction();
		throttle.getData()
			.putInt("Value", 75);
		ChangeTitleInstruction title = new ChangeTitleInstruction();
		title.getData()
			.putString("Text", "Night Line");
		FetchPackagesInstruction fetch = new FetchPackagesInstruction();
		fetch.getData()
			.putString("Text", "Post *");

		ScheduleEntry firstEntry = stop(first, items, fluids);
		firstEntry.conditions.add(new ArrayList<>(List.of(link, delay(3, TimeUnit.MINUTES))));
		Schedule schedule = schedule(false, firstEntry, stop(title), stop(throttle),
			stop(new DeliverPackagesInstruction(), time, players, new IdleCargoCondition()),
			stop(fetch, new StationPoweredCondition(), new StationUnloadedCondition()));
		schedule.savedProgress = 2;

		ItemStack stack = itemOf(helper, schedule);
		// through the codec the item is saved with
		Tag saved = ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack)
			.getOrThrow();
		ItemStack loaded = ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), saved)
			.getOrThrow();
		helper.assertTrue(loaded.is(SchedulesRegistry.SCHEDULE), "the item came back as " + loaded);
		helper.assertTrue(ItemStack.isSameItemSameComponents(stack, loaded), "the components differ");

		Schedule read = ScheduleItem.getSchedule(registries, loaded);
		helper.assertTrue(read != null, "no schedule on the item");
		helper.assertTrue(!read.cyclic && read.savedProgress == 2 && read.entries.size() == 5,
			"cyclic " + read.cyclic + ", progress " + read.savedProgress + ", entries " + read.entries.size());
		helper.assertTrue(read.write(registries)
			.equals(schedule.write(registries)), "the schedule changed: " + read.write(registries));

		ScheduleEntry entry = read.entries.get(0);
		helper.assertTrue(entry.instruction instanceof DestinationInstruction d && d.getFilter()
			.equals("Central *"), "destination");
		helper.assertTrue(entry.conditions.size() == 2 && entry.conditions.get(0)
			.size() == 2 && entry.conditions.get(1)
				.size() == 2, "conditions " + entry.conditions);
		ItemThresholdCondition readItems = (ItemThresholdCondition) entry.conditions.get(0)
			.get(0);
		helper.assertTrue(readItems.getItem(0)
			.is(Items.IRON_INGOT) && readItems.getOperator() == Ops.LESS && readItems.getThreshold() == 12
			&& readItems.getData()
				.getIntOr("Measure", 0) == 1, "item threshold");
		helper.assertTrue(((FluidThresholdCondition) entry.conditions.get(0)
			.get(1)).getItem(0)
				.is(Items.WATER_BUCKET), "fluid threshold");
		RedstoneLinkCondition readLink = (RedstoneLinkCondition) entry.conditions.get(1)
			.get(0);
		helper.assertTrue(readLink.getItem(0)
			.is(Items.REDSTONE) && readLink.getItem(1)
				.is(Items.GOLD_NUGGET) && readLink.lowActivation(), "redstone link");
		helper.assertTrue(((ScheduledDelay) entry.conditions.get(1)
			.get(1)).totalWaitTicks() == 3 * 20 * 60, "delay");
		helper.assertTrue(read.entries.get(1).instruction instanceof ChangeTitleInstruction t && t.getScheduleTitle()
			.equals("Night Line"), "title");
		helper.assertTrue(read.entries.get(2).instruction instanceof ChangeThrottleInstruction t
			&& Math.abs(t.getThrottle() - .75) < 1e-6, "throttle");
		helper.assertTrue(read.entries.get(2).conditions.isEmpty(), "conditions on an instruction that waits for nothing");
		ScheduleEntry delivery = read.entries.get(3);
		helper.assertTrue(delivery.instruction instanceof DeliverPackagesInstruction && delivery.conditions.get(0)
			.size() == 3, "delivery");
		helper.assertTrue(delivery.conditions.get(0)
			.get(0) instanceof TimeOfDayCondition, "time of day");
		helper.assertTrue(((PlayerPassengerCondition) delivery.conditions.get(0)
			.get(1)).getTarget() == 3, "players");
		helper.assertTrue(read.entries.get(4).instruction instanceof FetchPackagesInstruction f && f.getFilter()
			.equals("Post *"), "fetch");

		// the tooltip lists the destinations, a clone is a copy
		ScheduleEntry copy = firstEntry.clone(registries);
		helper.assertTrue(copy != firstEntry && copy.write(registries)
			.equals(firstEntry.write(registries)), "clone");
		helper.succeed();
	}

	@GameTest(structure = STRUCTURE, maxTicks = 100)
	public void conditionsAndInstructionsDescribeThemselves(GameTestHelper helper) {
		for (var type : Schedule.INSTRUCTION_TYPES) {
			ScheduleInstruction instruction = type.getSecond()
				.get();
			helper.assertTrue(instruction.getId()
				.equals(type.getFirst()), "id of " + type.getFirst());
			helper.assertTrue(!instruction.getTitleAs("instruction")
				.isEmpty(), "no title for " + type.getFirst());
			helper.assertTrue(instruction.getSummary()
				.getSecond() != null, "no summary for " + type.getFirst());
			ScheduleConfigBuilder builder = new ScheduleConfigBuilder();
			instruction.initConfigurationWidgets(builder);
			helper.assertTrue(builder.getWidgets()
				.size() <= 6, "too many options for " + type.getFirst());
		}
		for (var type : Schedule.CONDITION_TYPES) {
			ScheduleWaitCondition condition = type.getSecond()
				.get();
			helper.assertTrue(condition.getId()
				.equals(type.getFirst()), "id of " + type.getFirst());
			helper.assertTrue(!condition.getTitleAs("condition")
				.isEmpty(), "no title for " + type.getFirst());
			ScheduleConfigBuilder builder = new ScheduleConfigBuilder();
			condition.initConfigurationWidgets(builder);
			helper.assertTrue(builder.getWidgets()
				.size() <= 6, "too many options for " + type.getFirst());
		}
		helper.assertTrue(Schedule.INSTRUCTION_TYPES.size() == 5 && Schedule.CONDITION_TYPES.size() == 9, "types");
		helper.succeed();
	}

	// ---- the station ----

	@GameTest(structure = STRUCTURE, maxTicks = 600)
	public void autoScheduleOfTheStationIsAppliedAtAssembly(GameTestHelper helper) {
		Schedule schedule = schedule(true, stop(destination("Bravo"), delay(1, TimeUnit.SECONDS)));
		ItemStack item = itemOf(helper, schedule);
		assembled(helper, Carriage.SINGLE_ENDED, item, layout -> {
			Train train = layout.train;
			helper.assertTrue(train.runtime.getSchedule() != null, "the train got no schedule");
			helper.assertTrue(train.runtime.isAutoSchedule, "the schedule is not an auto-schedule");
			helper.assertTrue(layout.a.trainHasAutoSchedule() || layout.a.getAutoSchedule()
				.is(SchedulesRegistry.SCHEDULE), "the station lost its schedule");
			helper.assertTrue(layout.a.getAutoSchedule()
				.is(SchedulesRegistry.SCHEDULE), "the station does not keep the schedule");
			// the train drives to Bravo
			helper.succeedWhen(() -> {
				helper.assertTrue(train.getCurrentStation() == layout.b.getStation(), "the train has not arrived at Bravo (entry "
					+ train.runtime.currentEntry + ", " + train.runtime.state + ")");
				// taking an auto-schedule off a train gives nothing back, the station keeps it
				helper.assertTrue(train.runtime.returnSchedule(helper.getLevel()
					.registryAccess())
					.isEmpty(), "an auto-schedule was returned");
				helper.assertTrue(train.runtime.getSchedule() == null && train.runtime.paused, "the schedule was not discarded");
				helper.assertTrue(layout.a.getAutoSchedule()
					.is(SchedulesRegistry.SCHEDULE), "the station lost the schedule");
			});
		});
	}

	@GameTest(structure = STRUCTURE, maxTicks = 200)
	public void scheduleGoesIntoTheSlotOfTheStationScreenAndComesOut(GameTestHelper helper) {
		line(helper);
		StationBlockEntity a = station(helper, STATION_A, true);
		helper.runAfterDelay(8, () -> {
			ServerPlayer player = player(helper);
			StationGui gui = new StationGui(player, a);
			Slot slot = gui.getCustomSlot(25);
			helper.assertTrue(slot != null, "no slot for the schedule");
			helper.assertTrue(!slot.mayPlace(new ItemStack(Items.STICK)), "the slot takes anything");

			ItemStack schedule = itemOf(helper, schedule(true, stop(destination("Bravo"), delay(1, TimeUnit.SECONDS))));
			helper.assertTrue(slot.mayPlace(schedule), "the slot does not take a schedule");
			slot.setByPlayer(schedule.copy());
			helper.assertTrue(a.getAutoSchedule()
				.is(SchedulesRegistry.SCHEDULE), "the station has no schedule after the slot was filled");
			helper.assertTrue(ScheduleItem.getSchedule(helper.getLevel()
				.registryAccess(), a.getAutoSchedule()) != null, "the schedule was lost");
			helper.assertTrue(slot.getItem()
				.is(SchedulesRegistry.SCHEDULE) && slot.getMaxStackSize() == 1, "the slot shows another item");

			ItemStack taken = slot.remove(1);
			helper.assertTrue(taken.is(SchedulesRegistry.SCHEDULE) && a.getAutoSchedule()
				.isEmpty(), "the schedule did not come out");

			// the slot stays when the screen refreshes
			gui.onTick();
			helper.assertTrue(gui.getCustomSlot(25) == slot, "the slot was replaced");
			helper.succeed();
		});
	}

	@GameTest(structure = STRUCTURE, maxTicks = 300)
	public void stationSummaryListsTheDepartures(GameTestHelper helper) {
		assembled(helper, Carriage.DOUBLE_ENDED, layout -> {
			Train train = layout.train;
			train.name = Component.literal("Summary Express");
			// other tests have trains with schedules too: these names are for this one
			layout.a.updateName("Summary Home");
			layout.b.updateName("Summary Stop");
			train.runtime.setSchedule(schedule(true, stop(destination("Summary Stop"), delay(10, TimeUnit.SECONDS)),
				stop(destination("Summary Home"), delay(10, TimeUnit.SECONDS))), false);
			helper.runAfterDelay(30, () -> {
				GlobalTrainDisplayData.refresh();
				// trains of other tests are on the manager as well
				var bravo = GlobalTrainDisplayData.prepare("Summary Stop", 50)
					.stream()
					.filter(p -> p.train == train)
					.toList();
				helper.assertTrue(bravo.size() == 1, "predictions for Bravo: " + bravo.size());
				helper.assertTrue(GlobalTrainDisplayData.prepare("Summary *", 50)
					.stream()
					.filter(p -> p.train == train)
					.count() == 2, "predictions for the wildcard");
				TrainDeparturePrediction prediction = bravo.get(0);
				helper.assertTrue(prediction.ticks >= -1 && prediction.destination.equals("Summary Stop"), "prediction " + prediction.ticks);
				helper.assertTrue(prediction.scheduleTitle.getString()
					.equals("Summary Home"), "the title of the schedule is " + prediction.scheduleTitle.getString());

				// a display link on the station
				BlockPos linkPos = STATION_A.above();
				helper.setBlock(linkPos, DisplayRegistry.DISPLAY_LINK.defaultBlockState()
					.setValue(DisplayLinkBlock.FACING, Direction.UP));
				DisplayLinkBlockEntity link = helper.getBlockEntity(linkPos, DisplayLinkBlockEntity.class);
				helper.assertTrue(dev.createpolymer.api.behaviour.display.DisplaySource.getAll(helper.getLevel(), helper.absolutePos(STATION_A))
					.contains(SchedulesRegistry.STATION_SUMMARY), "the station offers no departure board");
				CompoundTag config = new CompoundTag();
				config.putString("Id", "create:station_summary");
				config.putString("Filter", "Summary Stop");
				link.applySettings(config, 0);
				var rows = SchedulesRegistry.STATION_SUMMARY.provideFlapDisplayText(
					new DisplayLinkContext(helper.getLevel(), link), new DisplayTargetStats(3, 40, DisplayRegistry.DISPLAY_BOARD_TARGET));
				helper.assertTrue(rows.size() == 3, "rows " + rows.size());
				// other trains may be listed as well: the world of the tests is kept between runs
				boolean listed = rows.stream()
					.anyMatch(row -> row.size() == 4 && row.get(2)
						.getString()
						.equals("Summary Express") && row.get(3)
							.getString()
							.equals("Summary Home"));
				helper.assertTrue(listed, "the train is not on the board: " + rows);

				// nothing to list: the lines stay empty, with a column for the platform if the filter has a wildcard
				link.getSourceConfig()
					.putString("Filter", "No Such Place");
				var empty = SchedulesRegistry.STATION_SUMMARY.provideFlapDisplayText(
					new DisplayLinkContext(helper.getLevel(), link), new DisplayTargetStats(3, 40, DisplayRegistry.DISPLAY_BOARD_TARGET));
				helper.assertTrue(empty.size() == 3 && empty.stream()
					.allMatch(row -> row.size() == 4 && row.get(1)
						.getString()
						.equals(" . ")), "empty board: " + empty);
				link.getSourceConfig()
					.putString("Filter", "No Such *");
				var emptyWithPlatform = SchedulesRegistry.STATION_SUMMARY.provideFlapDisplayText(
					new DisplayLinkContext(helper.getLevel(), link), new DisplayTargetStats(3, 40, DisplayRegistry.DISPLAY_BOARD_TARGET));
				helper.assertTrue(emptyWithPlatform.size() == 3 && emptyWithPlatform.stream()
					.allMatch(row -> row.size() == 5), "empty board with platforms: " + emptyWithPlatform);
				helper.succeed();
			});
		});
	}

	// ---- conductors ----

	private static BlockPos localPosOf(CarriageContraptionEntity entity, net.minecraft.world.level.block.Block block) {
		return entity.getContraption()
			.getBlocks()
			.entrySet()
			.stream()
			.filter(e -> e.getValue()
				.state()
				.is(block))
			.map(java.util.Map.Entry::getKey)
			.findFirst()
			.orElseThrow();
	}

	@GameTest(structure = STRUCTURE, maxTicks = 200)
	public void blazeBurnerConductorTakesAndReturnsTheSchedule(GameTestHelper helper) {
		assembled(helper, Carriage.SINGLE_ENDED, layout -> {
			Train train = layout.train;
			ServerPlayer player = player(helper);
			BlockPos burner = localPosOf(layout.entity, BurnerRegistry.BLAZE_BURNER);
			var behaviour = new ConductorBlockInteractionBehavior.BlazeBurner();
			ItemStack item = itemOf(helper, schedule(true, stop(destination("Bravo"), delay(5, TimeUnit.SECONDS))));

			// a schedule item that was never edited has nothing to hand over
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SchedulesRegistry.SCHEDULE));
			helper.assertTrue(!behaviour.handlePlayerInteraction(player, InteractionHand.MAIN_HAND, burner, layout.entity), "handled");
			helper.assertTrue(train.runtime.getSchedule() == null, "a blank schedule was applied");

			// a schedule without stops is refused
			player.setItemInHand(InteractionHand.MAIN_HAND, itemOf(helper, schedule(true)));
			helper.assertTrue(behaviour.handlePlayerInteraction(player, InteractionHand.MAIN_HAND, burner, layout.entity), "not handled");
			helper.assertTrue(train.runtime.getSchedule() == null, "an empty schedule was applied");
			helper.assertTrue(player.getMainHandItem()
				.is(SchedulesRegistry.SCHEDULE), "the empty schedule was taken");

			// the real one is handed over
			player.setItemInHand(InteractionHand.MAIN_HAND, item);
			helper.assertTrue(behaviour.handlePlayerInteraction(player, InteractionHand.MAIN_HAND, burner, layout.entity), "not handled");
			helper.assertTrue(train.runtime.getSchedule() != null && !train.runtime.isAutoSchedule, "the train got no schedule");
			helper.assertTrue(player.getMainHandItem()
				.isEmpty(), "the hand still holds the schedule");

			// another one is not accepted
			player.setItemInHand(InteractionHand.MAIN_HAND, itemOf(helper, schedule(true, stop(destination("Alpha")))));
			behaviour.handlePlayerInteraction(player, InteractionHand.MAIN_HAND, burner, layout.entity);
			helper.assertTrue(player.getMainHandItem()
				.is(SchedulesRegistry.SCHEDULE) && ((DestinationInstruction) train.runtime.getSchedule().entries.get(0).instruction)
					.getFilter()
					.equals("Bravo"), "the schedule was replaced");

			// a train that was driven by hand continues with a click of the empty hand
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			train.runtime.paused = true;
			behaviour.handlePlayerInteraction(player, InteractionHand.MAIN_HAND, burner, layout.entity);
			helper.assertTrue(!train.runtime.paused && train.runtime.getSchedule() != null, "the schedule did not continue");
			helper.assertTrue(player.getMainHandItem()
				.isEmpty(), "the schedule was taken instead of continued");

			// then it is taken back, with the stop it was at
			train.runtime.currentEntry = 0;
			helper.assertTrue(behaviour.handlePlayerInteraction(player, InteractionHand.MAIN_HAND, burner, layout.entity), "not handled");
			helper.assertTrue(train.runtime.getSchedule() == null, "the train still has the schedule");
			ItemStack back = player.getMainHandItem();
			helper.assertTrue(back.is(SchedulesRegistry.SCHEDULE), "the player got " + back);
			Schedule returned = ScheduleItem.getSchedule(helper.getLevel()
				.registryAccess(), back);
			helper.assertTrue(returned != null && returned.cyclic && returned.entries.size() == 1, "the returned schedule is wrong");
			helper.succeed();
		});
	}

	@GameTest(structure = STRUCTURE, maxTicks = 200)
	public void seatedConductorTakesAndReturnsTheSchedule(GameTestHelper helper) {
		assembled(helper, Carriage.SEATED, layout -> {
			Train train = layout.train;
			ServerPlayer player = player(helper);
			Villager conductor = EntityTypes.VILLAGER.create(helper.getLevel(), EntitySpawnReason.COMMAND);
			conductor.setPos(layout.entity.position());
			helper.getLevel()
				.addFreshEntity(conductor);
			layout.entity.addSittingPassenger(conductor, 0);
			helper.assertTrue(conductor.getRootVehicle() == layout.entity, "the villager does not sit");

			ItemStack item = itemOf(helper, schedule(true, stop(destination("Bravo"), delay(5, TimeUnit.SECONDS))));
			player.setItemInHand(InteractionHand.MAIN_HAND, item);
			InteractionResult result = ScheduleItemEntityInteraction.interactWithConductor(player, helper.getLevel(),
				InteractionHand.MAIN_HAND, conductor, null);
			helper.assertTrue(result.consumesAction(), "the click was not handled: " + result);
			helper.assertTrue(train.runtime.getSchedule() != null && !train.runtime.isAutoSchedule, "the train got no schedule");
			helper.assertTrue(player.getMainHandItem()
				.isEmpty(), "the hand still holds the schedule");

			// a click on something that is not on a train is none of its business
			helper.assertTrue(ScheduleItemEntityInteraction.interactWithConductor(player, helper.getLevel(), InteractionHand.MAIN_HAND,
				player, null) == InteractionResult.PASS, "a click on the player was handled");

			// the cooldown of the schedule item is over, then the empty hand takes it back
			player.getCooldowns()
				.removeCooldown(player.getCooldowns()
					.getCooldownGroup(new ItemStack(SchedulesRegistry.SCHEDULE)));
			result = ScheduleItemEntityInteraction.interactWithConductor(player, helper.getLevel(), InteractionHand.MAIN_HAND,
				conductor, null);
			helper.assertTrue(result.consumesAction(), "the click was not handled: " + result);
			helper.assertTrue(train.runtime.getSchedule() == null, "the train still has the schedule");
			helper.assertTrue(player.getInventory()
				.contains(s -> s.is(SchedulesRegistry.SCHEDULE)), "the player did not get the schedule back");
			helper.succeed();
		});
	}

	// ---- saving ----

	@GameTest(structure = STRUCTURE, maxTicks = 300)
	public void runtimeSurvivesSaveAndLoad(GameTestHelper helper) {
		assembled(helper, Carriage.SINGLE_ENDED, layout -> {
			Train train = layout.train;
			ItemThresholdCondition items = new ItemThresholdCondition();
			items.setItem(0, new ItemStack(Items.EMERALD));
			train.runtime.setSchedule(schedule(true, stop(destination("Bravo"), delay(1, TimeUnit.SECONDS)),
				stop(destination("Alpha"), delay(30, TimeUnit.SECONDS), delay(1, TimeUnit.MINUTES))), false);
			train.runtime.schedule.entries.get(1).conditions.add(new ArrayList<>(List.of(items)));
			train.runtime.schedule.savedProgress = 1;
			train.runtime.setSchedule(train.runtime.schedule, false);
			helper.assertTrue(train.runtime.currentEntry == 1, "the saved progress was not used");
			helper.runAfterDelay(30, () -> {
				helper.assertTrue(train.runtime.state == State.POST_TRANSIT, "the train does not wait: " + train.runtime.state);
				helper.assertTrue(train.runtime.conditionContext.get(0)
					.getIntOr("Time", 0) > 0, "no time passed");

				var registries = helper.getLevel()
					.registryAccess();
				RailwaySavedData current = TrackRegistry.RAILWAYS.getSavedData();
				Tag encoded = RailwaySavedData.CODEC.encodeStart(NbtOps.INSTANCE, current)
					.getOrThrow();
				RailwaySavedData loaded = RailwaySavedData.CODEC.parse(NbtOps.INSTANCE, encoded)
					.getOrThrow();
				Train copy = loaded.getTrains()
					.get(train.id);
				helper.assertTrue(copy != null && copy != train, "the train was not saved");
				var a = train.runtime;
				var b = copy.runtime;
				helper.assertTrue(b.getSchedule() != null && b.getSchedule()
					.write(registries)
					.equals(a.getSchedule()
						.write(registries)), "the schedule differs");
				helper.assertTrue(b.state == a.state && b.currentEntry == a.currentEntry && b.paused == a.paused
					&& b.completed == a.completed && b.isAutoSchedule == a.isAutoSchedule, "the state differs");
				helper.assertTrue(b.conditionProgress.equals(a.conditionProgress), "progress " + b.conditionProgress + " vs " + a.conditionProgress);
				helper.assertTrue(b.conditionContext.equals(a.conditionContext), "context " + b.conditionContext + " vs " + a.conditionContext);
				helper.assertTrue(b.predictionTicks.equals(a.predictionTicks), "predictions");
				// the loaded schedule goes on: the conditions are the loaded ones
				helper.assertTrue(b.getSchedule().entries.get(1).conditions.size() == 2, "conditions");
				helper.assertTrue(((ItemThresholdCondition) b.getSchedule().entries.get(1).conditions.get(1)
					.get(0)).getItem(0)
						.is(Items.EMERALD), "item of the condition");
				helper.succeed();
			});
		});
	}

	// ---- the editor ----

	private static void click(eu.pb4.sgui.api.gui.SimpleGui gui, int slot, ClickType type) {
		var element = gui.getGuiElement(slot);
		if (element == null)
			throw new AssertionError("nothing in slot " + slot + " of " + gui.getClass().getSimpleName());
		element.getGuiCallback()
			.click(slot, type, ContainerInput.PICKUP, gui);
	}

	@GameTest(structure = STRUCTURE, maxTicks = 100)
	public void editorListsAndRearrangesTheStops(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		ItemStack stack = itemOf(helper, schedule(true, stop(destination("One"), delay(5, TimeUnit.SECONDS)),
			stop(destination("Two"), delay(5, TimeUnit.SECONDS)), stop(destination("Three"), delay(5, TimeUnit.SECONDS))));
		player.getInventory()
			.setItem(player.getInventory()
				.getSelectedSlot(), stack);
		helper.assertTrue(player.getInventory()
			.getSelectedItem() == stack, "the item is not selected");
		var registries = helper.getLevel()
			.registryAccess();

		ScheduleEditor editor = new ScheduleEditor(player, stack);
		ScheduleGui gui = new ScheduleGui(editor);
		helper.assertTrue(gui.stillValid(), "the gui is not valid with the item in hand");
		// the screens can be sent to a player
		helper.assertTrue(gui.open(), "the list did not open");
		ScheduleInputGui opened = ScheduleInputGui.forInstruction(editor, editor.schedule.entries.get(0));
		helper.assertTrue(opened.open(), "the instruction editor did not open");
		helper.assertTrue(new ScheduleConditionsGui(editor, editor.schedule.entries.get(0)).open(), "the conditions did not open");
		helper.assertTrue(gui.open(), "the list did not open again");
		for (int row = 0; row < 3; row++) {
			helper.assertTrue(gui.getGuiElement(row * 9) != null, "no stop in row " + row);
			helper.assertTrue(gui.getGuiElement(row * 9 + 1) != null, "no condition in row " + row);
		}
		helper.assertTrue(gui.getGuiElement(3 * 9) == null, "a fourth row");
		helper.assertTrue(gui.getGuiElement(0 * 9 + 6) == null, "the first stop can move up");
		helper.assertTrue(gui.getGuiElement(2 * 9 + 7) == null, "the last stop can move down");

		List<String> names = new ArrayList<>();
		java.util.function.Supplier<List<String>> filters = () -> {
			names.clear();
			ScheduleItem.getSchedule(registries, stack).entries.forEach(
				e -> names.add(((DestinationInstruction) e.instruction).getFilter()));
			return names;
		};

		// move the first stop down: Two, One, Three
		click(gui, 0 * 9 + 7, ClickType.MOUSE_LEFT);
		helper.assertTrue(filters.get().equals(List.of("Two", "One", "Three")), "after moving down: " + filters.get());
		// move the third up: Two, Three, One
		click(gui, 2 * 9 + 6, ClickType.MOUSE_LEFT);
		helper.assertTrue(filters.get().equals(List.of("Two", "Three", "One")), "after moving up: " + filters.get());
		// copy the second
		click(gui, 1 * 9 + 5, ClickType.MOUSE_LEFT);
		helper.assertTrue(filters.get().equals(List.of("Two", "Three", "Three", "One")), "after copying: " + filters.get());
		// remove the first
		click(gui, 0 * 9 + 8, ClickType.MOUSE_LEFT);
		helper.assertTrue(filters.get().equals(List.of("Three", "Three", "One")), "after removing: " + filters.get());
		// the loop button
		click(gui, 50, ClickType.MOUSE_LEFT);
		helper.assertTrue(!ScheduleItem.getSchedule(registries, stack).cyclic, "the loop is still on");
		// skip: the second stop is where the train starts
		click(gui, 52, ClickType.MOUSE_LEFT);
		helper.assertTrue(ScheduleItem.getSchedule(registries, stack).savedProgress == 1, "progress");
		click(gui, 51, ClickType.MOUSE_LEFT);
		helper.assertTrue(ScheduleItem.getSchedule(registries, stack).savedProgress == 0, "progress after reset");
		// removing every stop takes the schedule off the item
		for (int i = 0; i < 3; i++)
			click(gui, 8, ClickType.MOUSE_LEFT);
		helper.assertTrue(!stack.has(SchedulesRegistry.TRAIN_SCHEDULE), "the empty schedule is still on the item");

		// the gui is gone when the item is
		player.getInventory()
			.setItem(player.getInventory()
				.getSelectedSlot(), ItemStack.EMPTY);
		helper.assertTrue(!gui.stillValid(), "the gui stays without the item");
		helper.succeed();
	}

	@GameTest(structure = STRUCTURE, maxTicks = 100)
	public void editorChangesInstructionsAndConditions(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		ItemStack stack = new ItemStack(SchedulesRegistry.SCHEDULE);
		player.getInventory()
			.setItem(player.getInventory()
				.getSelectedSlot(), stack);
		var registries = helper.getLevel()
			.registryAccess();
		ScheduleEditor editor = new ScheduleEditor(player, stack);

		// a new stop: a destination that waits 5 seconds
		ScheduleEntry entry = editor.newEntry();
		editor.schedule.entries.add(entry);
		editor.save();
		helper.assertTrue(stack.has(SchedulesRegistry.TRAIN_SCHEDULE), "the new stop was not saved");
		helper.assertTrue(entry.instruction instanceof DestinationInstruction && entry.conditions.size() == 1
			&& entry.conditions.get(0).get(0) instanceof ScheduledDelay, "the new stop is wrong");

		// the text of the destination, through the option the instruction describes
		ScheduleInputGui instruction = ScheduleInputGui.forInstruction(editor, entry);
		ScheduleConfigBuilder options = new ScheduleConfigBuilder();
		entry.instruction.initConfigurationWidgets(options);
		helper.assertTrue(options.getWidgets()
			.size() == 1 && options.getWidgets()
				.get(0) instanceof ScheduleConfigBuilder.Text, "the destination has one text");
		instruction.setText(options.getWidgets()
			.get(0), "Harbour ****");
		helper.assertTrue(((DestinationInstruction) entry.instruction).getFilter()
			.equals("Harbour ***"), "the filter is '" + ((DestinationInstruction) entry.instruction).getFilter() + "'");
		helper.assertTrue(((DestinationInstruction) ScheduleItem.getSchedule(registries, stack).entries.get(0).instruction).getFilter()
			.equals("Harbour ***"), "the text was not saved");

		// the type: the next is the delivery of packages, which waits as well, and so on up to the throttle
		click(instruction, 10, ClickType.MOUSE_LEFT);
		helper.assertTrue(entry.instruction instanceof DeliverPackagesInstruction, "next type " + entry.instruction.getId());
		click(instruction, 10, ClickType.MOUSE_LEFT);
		click(instruction, 10, ClickType.MOUSE_LEFT);
		click(instruction, 10, ClickType.MOUSE_LEFT);
		helper.assertTrue(entry.instruction instanceof ChangeThrottleInstruction, "the fourth type is " + entry.instruction.getId());
		helper.assertTrue(ScheduleItem.getSchedule(registries, stack).entries.get(0).conditions.isEmpty(), "a throttle waits");
		// the first click of the screen is on the screen the type change made: go back
		click(instruction, 10, ClickType.MOUSE_RIGHT);
		helper.assertTrue(entry.instruction instanceof ChangeTitleInstruction, "the previous type is " + entry.instruction.getId());
		// the title stays editable: a new gui for the entry
		for (int i = 0; i < 2; i++)
			click(instruction, 10, ClickType.MOUSE_LEFT);
		helper.assertTrue(entry.instruction instanceof DestinationInstruction, "the types wrap around: " + entry.instruction.getId());
		helper.assertTrue(!entry.conditions.isEmpty(), "a destination without conditions");

		// the text of the destination is typed in an anvil
		click(instruction, 12, ClickType.MOUSE_LEFT);
		helper.assertTrue(SguiUtils.getCurrentGui(player) instanceof AnvilInputGui, "no anvil for the text");
		AnvilInputGui anvil = (AnvilInputGui) SguiUtils.getCurrentGui(player);
		// a new destination from changing the type has no text yet
		helper.assertTrue(anvil.getInput()
			.isEmpty(), "the anvil starts with '" + anvil.getInput() + "'");
		anvil.receiveInput("Typed *");
		click(anvil, 2, ClickType.MOUSE_LEFT);
		helper.assertTrue(((DestinationInstruction) entry.instruction).getFilter()
			.equals("Typed *"), "the typed filter is '" + ((DestinationInstruction) entry.instruction).getFilter() + "'");

		// the conditions
		List<ScheduleWaitCondition> column = entry.conditions.get(0);
		ScheduleInputGui condition = ScheduleInputGui.forCondition(editor, entry, column, column.get(0));
		click(condition, 10, ClickType.MOUSE_LEFT);
		helper.assertTrue(column.get(0) instanceof TimeOfDayCondition, "next condition " + column.get(0).getId());
		click(condition, 10, ClickType.MOUSE_LEFT);
		click(condition, 10, ClickType.MOUSE_LEFT);
		helper.assertTrue(column.get(0) instanceof ItemThresholdCondition, "the third condition is " + column.get(0).getId());
		// the options of the threshold: operator, number, measure
		click(condition, 12, ClickType.MOUSE_LEFT);
		helper.assertTrue(((ItemThresholdCondition) column.get(0)).getOperator() == Ops.LESS, "operator");
		click(condition, 12, ClickType.MOUSE_RIGHT);
		click(condition, 12, ClickType.MOUSE_RIGHT);
		helper.assertTrue(((ItemThresholdCondition) column.get(0)).getOperator() == Ops.EQUAL, "operator wraps");
		ScheduleConfigBuilder thresholdOptions = new ScheduleConfigBuilder();
		column.get(0)
			.initConfigurationWidgets(thresholdOptions);
		condition.setText(thresholdOptions.getWidgets()
			.get(1), "37");
		helper.assertTrue(((ItemThresholdCondition) column.get(0)).getThreshold() == 37, "threshold");
		condition.setText(thresholdOptions.getWidgets()
			.get(1), "many");
		helper.assertTrue(((ItemThresholdCondition) column.get(0)).getThreshold() == 37, "a word is not a threshold");
		click(condition, 14, ClickType.MOUSE_LEFT);
		helper.assertTrue(((ItemThresholdCondition) column.get(0)).getData()
			.getIntOr("Measure", 0) == 1, "measure");

		// the reference item, by clicking the ghost slot with an item
		ScheduleItemGui picker = new ScheduleItemGui(editor, column.get(0), () -> {});
		player.containerMenu.setCarried(new ItemStack(Items.COPPER_INGOT, 5));
		picker.onClick(0, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, null);
		player.containerMenu.setCarried(ItemStack.EMPTY);
		helper.assertTrue(column.get(0)
			.getItem(0)
			.is(Items.COPPER_INGOT) && column.get(0)
				.getItem(0)
				.getCount() == 1, "reference item " + column.get(0)
					.getItem(0));
		ScheduleEntry saved = ScheduleItem.getSchedule(registries, stack).entries.get(0);
		ItemThresholdCondition savedCondition = (ItemThresholdCondition) saved.conditions.get(0)
			.get(0);
		helper.assertTrue(savedCondition.getItem(0)
			.is(Items.COPPER_INGOT) && savedCondition.getThreshold() == 37 && savedCondition.getOperator() == Ops.EQUAL,
			"the item does not hold the condition");

		// the conditions screen: a second condition in the group, an alternative group, removing
		ScheduleConditionsGui conditions = new ScheduleConditionsGui(editor, entry);
		helper.assertTrue(conditions.getGuiElement(1) != null && conditions.getGuiElement(2) == null, "one condition shown");
		column.add(new ScheduledDelay());
		entry.conditions.add(new ArrayList<>(List.of(new IdleCargoCondition())));
		conditions = new ScheduleConditionsGui(editor, entry);
		helper.assertTrue(conditions.getGuiElement(2) != null && conditions.getGuiElement(9 + 1) != null, "conditions shown");
		click(conditions, 2, ClickType.MOUSE_RIGHT);
		helper.assertTrue(column.size() == 1, "the second condition was not removed");
		click(conditions, 9 + 8, ClickType.MOUSE_LEFT);
		helper.assertTrue(entry.conditions.size() == 1, "the group was not removed");
		click(conditions, 1, ClickType.MOUSE_RIGHT);
		helper.assertTrue(column.size() == 1, "the last condition of the last group was removed");
		helper.assertTrue(ScheduleItem.getSchedule(registries, stack).entries.get(0).conditions.size() == 1, "not saved");

		// the redstone link takes two items
		ScheduleInputGui linkGui = ScheduleInputGui.forCondition(editor, entry, column, column.get(0));
		while (!(column.get(0) instanceof RedstoneLinkCondition))
			click(linkGui, 10, ClickType.MOUSE_LEFT);
		helper.assertTrue(linkGui.getGuiElement(20) != null && linkGui.getGuiElement(21) != null && linkGui.getGuiElement(22) == null,
			"two item buttons");
		helper.succeed();
	}
}
