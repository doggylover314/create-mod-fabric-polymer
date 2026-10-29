package dev.createpolymer.test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.createpolymer.contraptions.glue.SuperGlueEntity;
import dev.createpolymer.logistics.filter.FilterItemStack;
import dev.createpolymer.foundation.fluid.FluidHandlerLookup;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.IFluidHandler.FluidAction;
import dev.createpolymer.processing.burner.BlazeBurnerBlock;
import dev.createpolymer.processing.burner.BlazeBurnerBlock.HeatLevel;
import dev.createpolymer.registry.feature.BurnerRegistry;
import dev.createpolymer.registry.feature.SignalsRegistry;
import dev.createpolymer.registry.feature.TanksRegistry;
import dev.createpolymer.registry.feature.TrackRegistry;
import dev.createpolymer.registry.feature.TrainsRegistry;
import dev.createpolymer.trains.RailwaySavedData;
import dev.createpolymer.trains.entity.Train;
import dev.createpolymer.trains.graph.DiscoveredPath;
import dev.createpolymer.trains.graph.EdgePointType;
import dev.createpolymer.trains.graph.TrackGraph;
import dev.createpolymer.trains.observer.TrackObserver;
import dev.createpolymer.trains.observer.TrackObserverBlock;
import dev.createpolymer.trains.observer.TrackObserverBlockEntity;
import dev.createpolymer.trains.signal.SignalBlock;
import dev.createpolymer.trains.signal.SignalBlock.SignalType;
import dev.createpolymer.trains.signal.SignalBlockEntity;
import dev.createpolymer.trains.signal.SignalBlockEntity.SignalState;
import dev.createpolymer.trains.signal.SignalBoundary;
import dev.createpolymer.trains.station.StationBlockEntity;
import dev.createpolymer.trains.track.TrackBlock;
import dev.createpolymer.trains.track.TrackShape;
import dev.createpolymer.trains.track.TrackTargetingBlockItem;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Signals and track observers on the line of the train tests: the track runs along x at z = 3, the stations, signals
 * and observers stand next to it at z = 4 and target it. Trains are built east of their station and drive west.
 */
public class SignalGameTests {

	private static final String STRUCTURE = "create_polymer_test:trains";
	private static final int Z = 3;
	private static final int TRACK_END = 39;
	private static final BlockPos STATION_A = new BlockPos(26, 1, Z + 1);
	private static final BlockPos STATION_B = new BlockPos(8, 1, Z + 1);

	/** Which side of the track the signals govern: the one trains driving west come from. */
	private static final boolean WESTBOUND = true;

	private static BlockState track() {
		return TrackRegistry.TRACK.defaultBlockState()
			.setValue(TrackBlock.SHAPE, TrackShape.XO);
	}

	private static void line(GameTestHelper helper) {
		for (int x = 0; x <= TRACK_END; x++)
			helper.setBlock(new BlockPos(x, 1, Z), track());
	}

	private static void target(GameTestHelper helper, dev.createpolymer.trains.track.TrackTargetingBehaviour<?> behaviour,
		BlockPos pos, boolean front) {
		BlockPos abs = helper.absolutePos(pos);
		BlockPos trackPos = helper.absolutePos(new BlockPos(pos.getX(), 1, Z));
		CompoundTag data = new CompoundTag();
		data.store("TargetTrack", BlockPos.CODEC, trackPos.subtract(abs));
		data.putBoolean("TargetDirection", front);
		behaviour.read(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel()
			.registryAccess(), data));
	}

	private static StationBlockEntity station(GameTestHelper helper, BlockPos stationPos) {
		helper.setBlock(stationPos, TrainsRegistry.TRACK_STATION);
		StationBlockEntity be = helper.getBlockEntity(stationPos, StationBlockEntity.class);
		target(helper, be.edgePoint, stationPos, true);
		return be;
	}

	private static SignalBlockEntity signal(GameTestHelper helper, int x, boolean front) {
		BlockPos pos = new BlockPos(x, 1, Z + 1);
		helper.setBlock(pos, SignalsRegistry.TRACK_SIGNAL);
		SignalBlockEntity be = helper.getBlockEntity(pos, SignalBlockEntity.class);
		target(helper, be.edgePoint, pos, front);
		return be;
	}

	private static TrackObserverBlockEntity observer(GameTestHelper helper, int x, ItemStack filter) {
		BlockPos pos = new BlockPos(x, 1, Z + 1);
		helper.setBlock(pos, SignalsRegistry.TRACK_OBSERVER);
		TrackObserverBlockEntity be = helper.getBlockEntity(pos, TrackObserverBlockEntity.class);
		target(helper, be.edgePoint, pos, true);
		if (!filter.isEmpty())
			be.getBehaviour(dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour.TYPE)
				.setFilter(filter);
		return be;
	}

	private enum Cargo {
		NONE, APPLES, WATER
	}

	/** Bogey, controls, burner, bogey east of the station, with a barrel of apples or a tank of water on the controls. */
	private static void carriageBlocks(GameTestHelper helper, int x, Cargo cargo) {
		helper.setBlock(new BlockPos(x, 2, Z), TrainsRegistry.SMALL_BOGEY.defaultBlockState()
			.setValue(dev.createpolymer.trains.bogey.AbstractBogeyBlock.AXIS, Direction.Axis.X));
		helper.setBlock(new BlockPos(x + 1, 2, Z), TrainsRegistry.CONTROLS.defaultBlockState()
			.setValue(dev.createpolymer.contraptions.actors.trainControls.ControlsBlock.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(x + 2, 2, Z), BurnerRegistry.BLAZE_BURNER.defaultBlockState()
			.setValue(BlazeBurnerBlock.HEAT_LEVEL, HeatLevel.KINDLED));
		helper.setBlock(new BlockPos(x + 3, 2, Z), TrainsRegistry.SMALL_BOGEY.defaultBlockState()
			.setValue(dev.createpolymer.trains.bogey.AbstractBogeyBlock.AXIS, Direction.Axis.X));
		BlockPos glueEnd = new BlockPos(x + 3, 2, Z);
		BlockPos above = new BlockPos(x + 1, 3, Z);
		if (cargo == Cargo.APPLES) {
			helper.setBlock(above, Blocks.BARREL);
			helper.getBlockEntity(above, BarrelBlockEntity.class)
				.setItem(0, new ItemStack(Items.APPLE, 3));
			glueEnd = new BlockPos(x + 3, 3, Z);
		} else if (cargo == Cargo.WATER) {
			helper.setBlock(above, TanksRegistry.FLUID_TANK);
			FluidHandlerLookup.get(helper.getLevel(), helper.absolutePos(above), null)
				.fill(new FluidStack(Fluids.WATER, 2000), FluidAction.EXECUTE);
			glueEnd = new BlockPos(x + 3, 3, Z);
		}
		helper.getLevel()
			.addFreshEntity(new SuperGlueEntity(helper.getLevel(), SuperGlueEntity.span(
				helper.absolutePos(new BlockPos(x, 2, Z)), helper.absolutePos(glueEnd))));
	}

	/** Builds a carriage east of the station and assembles it there. */
	private static Train assemble(GameTestHelper helper, StationBlockEntity station, Cargo cargo) {
		carriageBlocks(helper, station.getBlockPos()
			.getX() - helper.absolutePos(BlockPos.ZERO)
				.getX() + 1, cargo);
		helper.assertTrue(station.enterAssemblyMode(null), "the station would not enter assembly mode");
		station.assemble(UUID.randomUUID());
		helper.assertTrue(station.getLastException() == null, "assembly failed: "
			+ (station.getLastException() == null ? "" : station.getLastException().component.getString()));
		Train train = station.getStation()
			.getPresentTrain();
		helper.assertTrue(train != null, "no train at the station after assembling");
		station.exitAssemblyMode();
		return train;
	}

	/** The block entity's position inside the test box. */
	private static BlockPos rel(GameTestHelper helper, net.minecraft.world.level.block.entity.BlockEntity be) {
		return be.getBlockPos()
			.subtract(helper.absolutePos(BlockPos.ZERO));
	}

	private static double leadingX(Train train) {
		return train.carriages.get(0)
			.getLeadingPoint()
			.getPosition(train.graph).x;
	}

	private static double absX(GameTestHelper helper, int x) {
		return helper.absolutePos(new BlockPos(x, 1, Z))
			.getX() + .5;
	}

	@GameTest(structure = STRUCTURE, maxTicks = 200)
	public void signalOnTheTrackCreatesABoundaryAndGroups(GameTestHelper helper) {
		line(helper);
		SignalBlockEntity be = signal(helper, 16, WESTBOUND);
		helper.runAfterDelay(8, () -> {
			SignalBoundary signal = be.getSignal();
			helper.assertTrue(signal != null, "no edge point");
			TrackGraph graph = TrackRegistry.RAILWAYS.getGraph(helper.getLevel(), signal.edgeLocation.getFirst());
			helper.assertTrue(graph != null && graph.getPoints(EdgePointType.SIGNAL)
				.contains(signal), "the signal is not in the graph");
			helper.assertTrue(signal.getType() == EdgePointType.SIGNAL, "wrong type");
			helper.assertTrue(signal.blockEntities.get(WESTBOUND)
				.containsKey(be.getBlockPos()) && signal.blockEntities.get(!WESTBOUND)
					.isEmpty(), "the signal block is not on its side");
			helper.assertTrue(signal.groups.getFirst() != null && signal.groups.getSecond() != null
				&& !signal.groups.getFirst()
					.equals(signal.groups.getSecond()), "the sides did not get two groups");
			helper.assertTrue(TrackRegistry.RAILWAYS.signalEdgeGroups.containsKey(signal.groups.getFirst())
				&& TrackRegistry.RAILWAYS.signalEdgeGroups.containsKey(signal.groups.getSecond()),
				"the groups are not registered");
			helper.assertTrue(be.getState() == SignalState.GREEN, "state " + be.getState());
			helper.assertTrue(!be.isPowered(), "a green signal reports power");

			// breaking the block takes the signal off the graph and its groups with it
			helper.setBlock(rel(helper, be), Blocks.AIR);
			helper.runAfterDelay(4, () -> {
				helper.assertTrue(graph.getPoints(EdgePointType.SIGNAL)
					.isEmpty(), "the signal is still in the graph");
				helper.assertTrue(!TrackRegistry.RAILWAYS.signalEdgeGroups.containsKey(signal.groups.getFirst())
					&& !TrackRegistry.RAILWAYS.signalEdgeGroups.containsKey(signal.groups.getSecond()),
					"the groups of the signal are still there");
				helper.succeed();
			});
		});
	}

	@GameTest(structure = STRUCTURE, maxTicks = 200)
	public void signalShowsItsLampAndTheBoxOfItsType(GameTestHelper helper) {
		line(helper);
		SignalBlockEntity be = signal(helper, 16, WESTBOUND);
		BlockPos redstone = new BlockPos(16, 1, Z + 2);
		helper.runAfterDelay(8, () -> {
			BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), be.getBlockPos());
			helper.assertTrue(attachment != null, "the signal has no model");
			int displays = 0;
			for (VirtualElement element : attachment.holder()
				.getElements())
				if (element instanceof ItemDisplayElement display && !display.getItem()
					.isEmpty())
					displays++;
			helper.assertTrue(displays == 2, "the signal shows " + displays + " pieces, expected the box and the lamp");
			ItemDisplayElement lamp = (ItemDisplayElement) attachment.holder()
				.getElements()
				.get(1);
			helper.assertTrue(lamp.getBrightness() == null, "a green signal glows");
			helper.setBlock(redstone, Blocks.REDSTONE_BLOCK);
		});
		helper.runAfterDelay(20, () -> {
			BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), be.getBlockPos());
			ItemDisplayElement lamp = (ItemDisplayElement) attachment.holder()
				.getElements()
				.get(1);
			helper.assertTrue(be.getState() == SignalState.RED, "state " + be.getState());
			helper.assertTrue(lamp.getBrightness() != null, "a red signal does not glow");
			helper.succeed();
		});
	}

	@GameTest(structure = STRUCTURE, maxTicks = 300)
	public void redstonePowerForcesTheSignalRed(GameTestHelper helper) {
		line(helper);
		SignalBlockEntity be = signal(helper, 16, WESTBOUND);
		BlockPos redstone = new BlockPos(16, 1, Z + 2);
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(be.getState() == SignalState.GREEN, "state " + be.getState());
			helper.setBlock(redstone, Blocks.REDSTONE_BLOCK);
		});
		helper.runAfterDelay(20, () -> {
			SignalBoundary signal = be.getSignal();
			helper.assertTrue(helper.getBlockState(rel(helper, be))
				.getValue(SignalBlock.POWERED), "the block is not powered");
			helper.assertTrue(be.getReportedPower(), "the block entity did not report the power");
			helper.assertTrue(signal.isForcedRed(WESTBOUND) && !signal.isForcedRed(!WESTBOUND),
				"the signal is not forced red on its side");
			helper.assertTrue(be.getState() == SignalState.RED, "state " + be.getState());
			helper.assertTrue(helper.getBlockState(rel(helper, be))
				.getAnalogOutputSignal(helper.getLevel(), be.getBlockPos(), Direction.UP) == 15,
				"a red signal gives no comparator output");
			helper.setBlock(redstone, Blocks.AIR);
		});
		helper.runAfterDelay(45, () -> {
			helper.assertTrue(!helper.getBlockState(rel(helper, be))
				.getValue(SignalBlock.POWERED), "the block is still powered");
			helper.assertTrue(!be.getSignal()
				.isForcedRed(WESTBOUND), "the signal is still forced red");
			helper.assertTrue(be.getState() == SignalState.GREEN, "state " + be.getState());
			helper.succeed();
		});
	}

	@GameTest(structure = STRUCTURE, maxTicks = 300)
	public void signalTypeIsKeptByTheBoundaryAndCycledByTheWrench(GameTestHelper helper) {
		line(helper);
		java.util.concurrent.atomic.AtomicReference<ItemStack> entryBox = new java.util.concurrent.atomic.AtomicReference<>();
		SignalBlockEntity be = signal(helper, 16, WESTBOUND);
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(helper.getBlockState(rel(helper, be))
				.getValue(SignalBlock.TYPE) == SignalType.ENTRY_SIGNAL, "not an entry signal");
			entryBox.set(((ItemDisplayElement) BlockBoundAttachment.get(helper.getLevel(), be.getBlockPos())
				.holder()
				.getElements()
				.get(0)).getItem());
			@SuppressWarnings("removal")
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(be.getBlockPos()), Direction.UP,
				be.getBlockPos(), false);
			UseOnContext context = new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND,
				new ItemStack(dev.createpolymer.registry.ModItems.WRENCH), hit);
			InteractionResult result = ((SignalBlock) helper.getBlockState(rel(helper, be))
				.getBlock()).onWrenched(helper.getBlockState(rel(helper, be)), context);
			helper.assertTrue(result == InteractionResult.SUCCESS, "wrench result " + result);
			helper.assertTrue(be.getSignal()
				.getTypeFor(be.getBlockPos()) == SignalType.CROSS_SIGNAL,
				"the boundary did not change its type");
		});
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(helper.getBlockState(rel(helper, be))
				.getValue(SignalBlock.TYPE) == SignalType.CROSS_SIGNAL, "the block did not follow the boundary");
			helper.assertTrue(be.getState() == SignalState.GREEN, "a free cross signal is " + be.getState());
			// the box of a cross signal is another model
			BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), be.getBlockPos());
			ItemDisplayElement box = (ItemDisplayElement) attachment.holder()
				.getElements()
				.get(0);
			helper.assertTrue(!box.getItem()
				.isEmpty() && !ItemStack.isSameItemSameComponents(box.getItem(), entryBox.get()), "the box is the same");
			helper.succeed();
		});
	}

	@GameTest(structure = STRUCTURE, maxTicks = 300)
	public void crossSignalFollowsTheSignalsBehindIt(GameTestHelper helper) {
		line(helper);
		SignalBlockEntity cross = signal(helper, 20, WESTBOUND);
		SignalBlockEntity entry = signal(helper, 12, WESTBOUND);
		BlockPos redstone = new BlockPos(12, 1, Z + 2);
		helper.runAfterDelay(8, () -> {
			cross.getSignal()
				.cycleSignalType(cross.getBlockPos());
			helper.setBlock(redstone, Blocks.REDSTONE_BLOCK);
		});
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(entry.getState() == SignalState.RED, "the entry signal is " + entry.getState());
			helper.assertTrue(cross.getSignal()
				.types.get(WESTBOUND) == SignalType.CROSS_SIGNAL, "not a cross signal");
			helper.assertTrue(cross.getState() == SignalState.RED, "the cross signal is " + cross.getState()
				+ " in front of a red signal");
			helper.setBlock(redstone, Blocks.AIR);
		});
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(entry.getState() == SignalState.GREEN, "the entry signal is " + entry.getState());
			helper.assertTrue(cross.getState() == SignalState.GREEN, "the cross signal is " + cross.getState()
				+ " in front of a green signal");
			helper.succeed();
		});
	}

	@GameTest(structure = STRUCTURE, maxTicks = 700)
	public void redSignalStopsATrainBeforeIt(GameTestHelper helper) {
		line(helper);
		StationBlockEntity a = station(helper, STATION_A);
		StationBlockEntity b = station(helper, STATION_B);
		SignalBlockEntity signal = signal(helper, 16, WESTBOUND);
		BlockPos redstone = new BlockPos(16, 1, Z + 2);
		helper.setBlock(redstone, Blocks.REDSTONE_BLOCK);
		java.util.concurrent.atomic.AtomicReference<Train> trainRef = new java.util.concurrent.atomic.AtomicReference<>();
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(signal.getSignal() != null, "no signal edge point");
			trainRef.set(assemble(helper, a, Cargo.NONE));
		});
		helper.runAfterDelay(16, () -> {
			Train train = trainRef.get();
			DiscoveredPath path = train.navigation.findPathTo(b.getStation(), -1);
			helper.assertTrue(path != null, "no path to station B");
			train.navigation.startNavigation(path);
		});
		helper.runAfterDelay(260, () -> {
			Train train = trainRef.get();
			helper.assertTrue(train.navigation.waitingForSignal != null, "the train does not wait for the signal");
			helper.assertTrue(signal.getSignal().id.equals(train.navigation.waitingForSignal.getFirst()),
				"the train waits for another signal");
			helper.assertTrue(Math.abs(train.speed) < 0.01, "the train is still moving: " + train.speed);
			helper.assertTrue(leadingX(train) > absX(helper, 16) - .5, "the train is past the signal: " + leadingX(train)
				+ " vs " + absX(helper, 16));
			helper.assertTrue(leadingX(train) < absX(helper, 26) - 3, "the train never left the station: " + leadingX(train));
			helper.assertTrue(train.getCurrentStation() == null, "the train sits at a station");
			helper.assertTrue(signal.getState() == SignalState.RED, "state " + signal.getState());
			// let it go
			helper.setBlock(redstone, Blocks.AIR);
			helper.succeedWhen(() -> {
				helper.assertTrue(train.getCurrentStation() == b.getStation(), "the train has not arrived (x "
					+ leadingX(train) + ", speed " + train.speed + ")");
				helper.assertTrue(train.speed == 0, "the train has not stopped");
			});
		});
	}

	@GameTest(structure = STRUCTURE, maxTicks = 900)
	public void secondTrainWaitsAtTheSignalWhileTheBlockIsOccupied(GameTestHelper helper) {
		line(helper);
		StationBlockEntity a = station(helper, STATION_A);
		StationBlockEntity b = station(helper, STATION_B);
		SignalBlockEntity signal = signal(helper, 18, WESTBOUND);
		java.util.concurrent.atomic.AtomicReference<Train> waitingRef = new java.util.concurrent.atomic.AtomicReference<>();
		java.util.concurrent.atomic.AtomicReference<Train> arrivingRef = new java.util.concurrent.atomic.AtomicReference<>();
		helper.runAfterDelay(10, () -> {
			waitingRef.set(assemble(helper, b, Cargo.NONE));
			arrivingRef.set(assemble(helper, a, Cargo.NONE));
			helper.assertTrue(waitingRef.get() != arrivingRef.get(), "one train");
		});
		helper.runAfterDelay(16, () -> {
			DiscoveredPath path = arrivingRef.get().navigation.findPathTo(b.getStation(), -1);
			helper.assertTrue(path != null, "no path to station B");
			arrivingRef.get().navigation.startNavigation(path);
		});
		helper.runAfterDelay(260, () -> {
			Train waiting = waitingRef.get();
			Train arriving = arrivingRef.get();
			helper.assertTrue(arriving.navigation.waitingForSignal != null, "the second train does not wait");
			helper.assertTrue(signal.getSignal().id.equals(arriving.navigation.waitingForSignal.getFirst()),
				"it waits for another signal");
			helper.assertTrue(Math.abs(arriving.speed) < 0.01, "the second train still moves: " + arriving.speed);
			helper.assertTrue(leadingX(arriving) > absX(helper, 18) - .5, "the second train is past the signal: "
				+ (leadingX(arriving) - absX(helper, 18)));
			helper.assertTrue(signal.getState() == SignalState.RED, "state " + signal.getState());
			helper.assertTrue(b.getStation()
				.getPresentTrain() == waiting, "the first train left");
			// the first train goes away: the block is free
			helper.assertTrue(b.tryDisassembleTrain(null), "disassembling failed");
			helper.succeedWhen(() -> {
				helper.assertTrue(arriving.getCurrentStation() == b.getStation(), "the second train has not arrived (x "
					+ leadingX(arriving) + ", speed " + arriving.speed + ")");
				helper.assertTrue(arriving.speed == 0, "the second train has not stopped");
			});
		});
	}

	@GameTest(structure = STRUCTURE, maxTicks = 700)
	public void observerGivesRedstoneWhileATrainPassesAndItsFilterWorks(GameTestHelper helper) {
		line(helper);
		StationBlockEntity a = station(helper, STATION_A);
		StationBlockEntity b = station(helper, STATION_B);
		TrackObserverBlockEntity any = observer(helper, 21, ItemStack.EMPTY);
		TrackObserverBlockEntity apples = observer(helper, 17, new ItemStack(Items.APPLE));
		TrackObserverBlockEntity gold = observer(helper, 13, new ItemStack(Items.GOLD_INGOT));
		AtomicBoolean seenAny = new AtomicBoolean(), seenApples = new AtomicBoolean(), seenGold = new AtomicBoolean();
		java.util.concurrent.atomic.AtomicReference<Train> trainRef = new java.util.concurrent.atomic.AtomicReference<>();
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(any.getObserver() != null && apples.getObserver() != null && gold.getObserver() != null,
				"an observer has no edge point");
			helper.assertTrue(!any.isBlockPowered(), "powered without a train");
			helper.assertTrue(any.getObserver()
				.getFilter()
				.isEmpty() && !apples.getObserver()
					.getFilter()
					.isEmpty(), "the filters did not reach the edge points");
			trainRef.set(assemble(helper, a, Cargo.APPLES));
		});
		helper.runAfterDelay(16, () -> {
			Train train = trainRef.get();
			DiscoveredPath path = train.navigation.findPathTo(b.getStation(), -1);
			helper.assertTrue(path != null, "no path to station B");
			train.navigation.startNavigation(path);
			helper.succeedWhen(() -> {
				seenAny.compareAndSet(false, any.isBlockPowered());
				seenApples.compareAndSet(false, apples.isBlockPowered());
				seenGold.compareAndSet(false, gold.isBlockPowered());
				if (any.isBlockPowered())
					helper.assertTrue(any.getBlockState()
						.getSignal(helper.getLevel(), any.getBlockPos(), Direction.UP) == 15,
						"a powered observer gives no signal");
				helper.assertTrue(train.getCurrentStation() == b.getStation(), "the train has not arrived (x "
					+ leadingX(train) + ")");
				helper.assertTrue(seenAny.get(), "the observer without a filter never turned on");
				helper.assertTrue(seenApples.get(), "the observer with a matching filter never turned on");
				helper.assertTrue(!seenGold.get(), "the observer with another filter turned on");
				helper.assertTrue(!any.isBlockPowered() && !apples.isBlockPowered(), "an observer stayed on");
				helper.assertTrue(any.getObserver()
					.getCurrentTrain() == null, "an observer remembers a train that left");
			});
		});
	}

	@GameTest(structure = STRUCTURE, maxTicks = 700)
	public void observerFiltersForTheFluidsOfTheCargo(GameTestHelper helper) {
		line(helper);
		StationBlockEntity a = station(helper, STATION_A);
		StationBlockEntity b = station(helper, STATION_B);
		TrackObserverBlockEntity water = observer(helper, 17, new ItemStack(Items.WATER_BUCKET));
		TrackObserverBlockEntity lava = observer(helper, 13, new ItemStack(Items.LAVA_BUCKET));
		AtomicBoolean seenWater = new AtomicBoolean(), seenLava = new AtomicBoolean();
		java.util.concurrent.atomic.AtomicReference<Train> trainRef = new java.util.concurrent.atomic.AtomicReference<>();
		helper.runAfterDelay(10, () -> trainRef.set(assemble(helper, a, Cargo.WATER)));
		helper.runAfterDelay(16, () -> {
			Train train = trainRef.get();
			DiscoveredPath path = train.navigation.findPathTo(b.getStation(), -1);
			helper.assertTrue(path != null, "no path to station B");
			train.navigation.startNavigation(path);
			helper.succeedWhen(() -> {
				seenWater.compareAndSet(false, water.isBlockPowered());
				seenLava.compareAndSet(false, lava.isBlockPowered());
				helper.assertTrue(train.getCurrentStation() == b.getStation(), "the train has not arrived (x "
					+ leadingX(train) + ")");
				helper.assertTrue(seenWater.get(), "the observer filtering for water never turned on");
				helper.assertTrue(!seenLava.get(), "the observer filtering for lava turned on");
			});
		});
	}

	@GameTest(structure = STRUCTURE, maxTicks = 100)
	public void fluidFiltersMatchLikeCreates(GameTestHelper helper) {
		var level = helper.getLevel();
		FluidStack water = new FluidStack(Fluids.WATER, 1000);
		FluidStack lava = new FluidStack(Fluids.LAVA, 1000);
		helper.assertTrue(TrackObserver.testFluid(level, FilterItemStack.empty(), water), "an empty filter must match");
		FilterItemStack bucket = FilterItemStack.of(new ItemStack(Items.WATER_BUCKET));
		helper.assertTrue(TrackObserver.testFluid(level, bucket, water), "a water bucket does not match water");
		helper.assertTrue(!TrackObserver.testFluid(level, bucket, lava), "a water bucket matches lava");
		helper.assertTrue(!TrackObserver.testFluid(level, bucket, FluidStack.EMPTY), "a filter matches no fluid");
		helper.assertTrue(!TrackObserver.testFluid(level, FilterItemStack.of(new ItemStack(Items.APPLE)), water),
			"an apple matches water");
		helper.succeed();
	}

	@GameTest(structure = STRUCTURE, maxTicks = 100)
	public void observerShowsAPoweredModelAndItsFilter(GameTestHelper helper) {
		line(helper);
		TrackObserverBlockEntity be = observer(helper, 16, new ItemStack(Items.APPLE));
		helper.runAfterDelay(8, () -> {
			BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), be.getBlockPos());
			helper.assertTrue(attachment != null, "the observer has no model");
			var elements = attachment.holder()
				.getElements();
			int displays = 0;
			for (VirtualElement element : elements)
				if (element instanceof ItemDisplayElement display && !display.getItem()
					.isEmpty())
					displays++;
			helper.assertTrue(displays == 2, "the observer shows " + displays + " pieces, expected the box and the filter");
			ItemStack off = ((ItemDisplayElement) elements.get(0)).getItem();
			helper.setBlock(rel(helper, be), helper.getBlockState(rel(helper, be))
				.setValue(TrackObserverBlock.POWERED, true));
			// the block entity turns the state off again as no train is there, so look right away
			ItemStack on = ((ItemDisplayElement) attachment.holder()
				.getElements()
				.get(0)).getItem();
			helper.assertTrue(!ItemStack.isSameItemSameComponents(on, off), "the box does not change when powered");
			helper.succeed();
		});
	}

	@GameTest(structure = STRUCTURE, maxTicks = 300)
	public void signalsAndObserversSurviveSavingAndLoading(GameTestHelper helper) {
		line(helper);
		SignalBlockEntity signal = signal(helper, 16, WESTBOUND);
		BlockPos redstone = new BlockPos(16, 1, Z + 2);
		TrackObserverBlockEntity observer = observer(helper, 24, new ItemStack(Items.APPLE));
		helper.runAfterDelay(10, () -> {
			signal.getSignal()
				.cycleSignalType(signal.getBlockPos());
			helper.setBlock(redstone, Blocks.REDSTONE_BLOCK);
		});
		helper.runAfterDelay(30, () -> {
			SignalBoundary original = signal.getSignal();
			TrackObserver originalObserver = observer.getObserver();
			helper.assertTrue(original != null && originalObserver != null, "no edge points");
			helper.assertTrue(original.cachedStates.get(WESTBOUND) == SignalState.RED, "not red");

			RailwaySavedData current = TrackRegistry.RAILWAYS.getSavedData();
			Tag encoded = RailwaySavedData.CODEC.encodeStart(NbtOps.INSTANCE, current)
				.getOrThrow();
			RailwaySavedData loaded = RailwaySavedData.CODEC.parse(NbtOps.INSTANCE, encoded)
				.getOrThrow();

			TrackGraph graph = loaded.getTrackNetworks()
				.get(TrackRegistry.RAILWAYS.getGraph(helper.getLevel(), original.edgeLocation.getFirst()).id);
			helper.assertTrue(graph != null, "the graph was not saved");
			SignalBoundary copy = graph.getPoint(EdgePointType.SIGNAL, original.id);
			helper.assertTrue(copy != null && copy != original, "the signal was not saved");
			helper.assertTrue(copy.blockEntities.get(WESTBOUND)
				.get(signal.getBlockPos()) == Boolean.TRUE && copy.blockEntities.get(!WESTBOUND)
					.isEmpty(), "the signal block or its power was not saved");
			helper.assertTrue(copy.types.get(WESTBOUND) == SignalType.CROSS_SIGNAL, "the signal type was not saved");
			helper.assertTrue(copy.isForcedRed(WESTBOUND), "the redstone input was not saved");
			helper.assertTrue(copy.cachedStates.get(WESTBOUND) == SignalState.RED, "the state was not saved");
			helper.assertTrue(original.groups.getFirst()
				.equals(copy.groups.getFirst()) && original.groups.getSecond()
					.equals(copy.groups.getSecond()), "the groups differ");
			helper.assertTrue(loaded.getSignalBlocks()
				.containsKey(copy.groups.getFirst()) && loaded.getSignalBlocks()
					.containsKey(copy.groups.getSecond()), "the groups were not saved");
			helper.assertTrue(copy.edgeLocation.getFirst()
				.equals(original.edgeLocation.getFirst()) && Math.abs(copy.position - original.position) < 1e-9,
				"the signal moved");

			TrackObserver observerCopy = graph.getPoint(EdgePointType.OBSERVER, originalObserver.id);
			helper.assertTrue(observerCopy != null && observerCopy != originalObserver, "the observer was not saved");
			helper.assertTrue(observerCopy.getBlockEntityPos()
				.equals(observer.getBlockPos()), "the observer lost its block");
			FilterItemStack filter = observerCopy.getFilter();
			helper.assertTrue(!filter.isEmpty() && filter.item()
				.is(Items.APPLE), "the observer lost its filter");

			// and the block entities keep what they show
			CompoundTag tag = signal.saveWithoutMetadata(helper.getLevel()
				.registryAccess());
			helper.assertTrue(tag.contains("Power") && tag.contains("State"), "the signal block entity saved nothing");
			helper.succeed();
		});
	}

	@GameTest(structure = STRUCTURE, maxTicks = 200)
	public void signalItemPlacesASignalTargetingTheSelectedTrack(GameTestHelper helper) {
		line(helper);
		for (int x = 0; x < 8; x++)
			helper.setBlock(new BlockPos(x + 14, 0, Z + 2), Blocks.STONE);
		helper.runAfterDelay(8, () -> placeWithTheItem(helper));
	}

	private void placeWithTheItem(GameTestHelper helper) {
		@SuppressWarnings("removal")
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setPos(helper.absoluteVec(new Vec3(16.5, 1, Z + 3.5)));
		player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
			helper.absoluteVec(new Vec3(15.5, 1.5, Z + 0.5)));
		ItemStack stack = new ItemStack(SignalsRegistry.TRACK_SIGNAL_ITEM);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		helper.assertTrue(stack.getItem() instanceof TrackTargetingBlockItem, "not a targeting item");

		BlockPos trackPos = helper.absolutePos(new BlockPos(16, 1, Z));
		BlockHitResult onTrack = new BlockHitResult(Vec3.atCenterOf(trackPos), Direction.UP, trackPos, false);
		InteractionResult first = stack.useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, stack,
			onTrack));
		helper.assertTrue(first.consumesAction(), "selecting the track failed: " + first);
		helper.assertTrue(stack.has(TrackRegistry.TRACK_TARGETING_ITEM_SELECTED_POS), "the track was not remembered");

		BlockPos floor = helper.absolutePos(new BlockPos(16, 0, Z + 2));
		BlockHitResult onFloor = new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false);
		InteractionResult second = stack.useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, stack,
			onFloor));
		helper.assertTrue(second.consumesAction(), "placing the signal failed: " + second);
		BlockPos placed = new BlockPos(16, 1, Z + 2);
		helper.assertTrue(helper.getBlockState(placed)
			.is(SignalsRegistry.TRACK_SIGNAL), "no signal was placed");
		SignalBlockEntity be = helper.getBlockEntity(placed, SignalBlockEntity.class);
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(be.edgePoint.getGlobalPosition()
				.equals(trackPos), "the signal targets " + be.edgePoint.getGlobalPosition() + " instead of " + trackPos);
			helper.assertTrue(be.getSignal() != null, "the placed signal has no edge point");
			helper.succeed();
		});
	}
}
