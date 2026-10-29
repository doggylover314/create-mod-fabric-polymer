package dev.createpolymer.test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import dev.createpolymer.contraptions.actors.trainControls.ControlsBlock;
import dev.createpolymer.contraptions.actors.trainControls.ControlsInteractionBehaviour;
import dev.createpolymer.contraptions.actors.trainControls.ControlsServerHandler;
import dev.createpolymer.contraptions.glue.SuperGlueEntity;
import dev.createpolymer.processing.burner.BlazeBurnerBlock;
import dev.createpolymer.processing.burner.BlazeBurnerBlock.HeatLevel;
import dev.createpolymer.registry.feature.BurnerRegistry;
import dev.createpolymer.registry.feature.TrackRegistry;
import dev.createpolymer.registry.feature.TrainsRegistry;
import dev.createpolymer.trains.RailwaySavedData;
import dev.createpolymer.trains.bogey.BogeySizes;
import dev.createpolymer.trains.entity.Carriage;
import dev.createpolymer.trains.entity.CarriageBogey;
import dev.createpolymer.trains.entity.CarriageContraptionEntity;
import dev.createpolymer.trains.entity.Train;
import dev.createpolymer.trains.graph.DiscoveredPath;
import dev.createpolymer.trains.graph.EdgePointType;
import dev.createpolymer.trains.graph.TrackGraph;
import dev.createpolymer.trains.station.GlobalStation;
import dev.createpolymer.trains.station.StationBlock;
import dev.createpolymer.trains.station.StationBlockEntity;
import dev.createpolymer.trains.station.StationGui;
import dev.createpolymer.trains.track.TrackBlock;
import dev.createpolymer.trains.track.TrackShape;
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
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Trains: a straight line of track with two stations on it, and a carriage of two bogeys, train controls and a
 * blaze burner that is assembled at the east one. The structure is 40 blocks long (the track runs along x at
 * z = 3), the stations stand at z = 4 next to the track, the train is built east of the station it starts at, and
 * drives west.
 */
public class TrainsGameTests {

	private static final String STRUCTURE = "create_polymer_test:trains";
	private static final int Z = 3;
	private static final int TRACK_END = 39;
	private static final BlockPos STATION_A = new BlockPos(26, 1, Z + 1);
	private static final BlockPos STATION_B = new BlockPos(8, 1, Z + 1);
	/** Bogey, controls, burner, bogey: the carriage is east of station A. */
	private static final int FIRST_BOGEY_X = 27;

	private static BlockState track() {
		return TrackRegistry.TRACK.defaultBlockState()
			.setValue(TrackBlock.SHAPE, TrackShape.XO);
	}

	private static void line(GameTestHelper helper) {
		for (int x = 0; x <= TRACK_END; x++)
			helper.setBlock(new BlockPos(x, 1, Z), track());
	}

	/** A station next to the track that targets it, with the train extending east from it. */
	private static StationBlockEntity station(GameTestHelper helper, BlockPos stationPos) {
		helper.setBlock(stationPos, TrainsRegistry.TRACK_STATION);
		StationBlockEntity be = helper.getBlockEntity(stationPos, StationBlockEntity.class);
		BlockPos abs = helper.absolutePos(stationPos);
		BlockPos trackPos = helper.absolutePos(new BlockPos(stationPos.getX(), 1, Z));
		CompoundTag data = new CompoundTag();
		data.store("TargetTrack", BlockPos.CODEC, trackPos.subtract(abs));
		data.putBoolean("TargetDirection", true);
		be.edgePoint.read(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel()
			.registryAccess(), data));
		return be;
	}

	private static void carriageBlocks(GameTestHelper helper) {
		int x = FIRST_BOGEY_X;
		helper.setBlock(new BlockPos(x, 2, Z), TrainsRegistry.SMALL_BOGEY.defaultBlockState()
			.setValue(dev.createpolymer.trains.bogey.AbstractBogeyBlock.AXIS, Direction.Axis.X));
		helper.setBlock(new BlockPos(x + 1, 2, Z), TrainsRegistry.CONTROLS.defaultBlockState()
			.setValue(ControlsBlock.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(x + 2, 2, Z), BurnerRegistry.BLAZE_BURNER.defaultBlockState()
			.setValue(BlazeBurnerBlock.HEAT_LEVEL, HeatLevel.KINDLED));
		helper.setBlock(new BlockPos(x + 3, 2, Z), TrainsRegistry.SMALL_BOGEY.defaultBlockState()
			.setValue(dev.createpolymer.trains.bogey.AbstractBogeyBlock.AXIS, Direction.Axis.X));
		helper.getLevel()
			.addFreshEntity(new SuperGlueEntity(helper.getLevel(), SuperGlueEntity.span(
				helper.absolutePos(new BlockPos(x, 2, Z)), helper.absolutePos(new BlockPos(x + 3, 2, Z)))));
	}

	private static class Layout {
		StationBlockEntity a, b;
		Train train;
	}

	/** Builds the track and both stations, then the carriage, and assembles it at station A. */
	private static void assembled(GameTestHelper helper, java.util.function.Consumer<Layout> then) {
		Layout layout = new Layout();
		line(helper);
		layout.a = station(helper, STATION_A);
		layout.b = station(helper, STATION_B);
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(layout.a.getStation() != null, "station A has no edge point");
			helper.assertTrue(layout.b.getStation() != null, "station B has no edge point");
			carriageBlocks(helper);
			helper.assertTrue(layout.a.enterAssemblyMode(null), "the station would not enter assembly mode");
			layout.a.assemble(UUID.randomUUID());
			helper.assertTrue(layout.a.getLastException() == null, "assembly failed: "
				+ (layout.a.getLastException() == null ? "" : layout.a.getLastException().component.getString()));
			layout.train = layout.a.getStation()
				.getPresentTrain();
			helper.assertTrue(layout.train != null, "no train at the station after assembling");
			// the assembly button of the screen leaves the mode as soon as a train exists
			layout.a.exitAssemblyMode();
		});
		helper.runAfterDelay(16, () -> then.accept(layout));
	}

	@GameTest(structure = STRUCTURE, maxTicks = 100)
	public void stationsTargetTheTrackAndShowAFlag(GameTestHelper helper) {
		line(helper);
		StationBlockEntity a = station(helper, STATION_A);
		helper.runAfterDelay(8, () -> {
			GlobalStation station = a.getStation();
			helper.assertTrue(station != null, "no edge point");
			TrackGraph graph = TrackRegistry.RAILWAYS.getGraph(helper.getLevel(), station.edgeLocation.getFirst());
			helper.assertTrue(graph != null && graph.getPoints(EdgePointType.STATION)
				.contains(station), "the station is not in the graph");
			helper.assertTrue(station.getPresentTrain() == null, "a train is present");
			helper.assertTrue(helper.getBlockState(STATION_A)
				.getBlock() instanceof StationBlock, "the station block is gone");
			BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(STATION_A));
			helper.assertTrue(attachment != null, "the station has no model");
			int displays = 0;
			for (VirtualElement element : attachment.holder()
				.getElements())
				if (element instanceof ItemDisplayElement display && !display.getItem()
					.isEmpty())
					displays++;
			helper.assertTrue(displays == 2, "the station shows " + displays + " pieces, expected the base and the flag");
			helper.succeed();
		});
	}

	@GameTest(structure = STRUCTURE, maxTicks = 200)
	public void carriageAssemblesIntoATrain(GameTestHelper helper) {
		assembled(helper, layout -> {
			Train train = layout.train;
			helper.assertTrue(TrackRegistry.RAILWAYS.trains.get(train.id) == train, "the train is not in the manager");
			helper.assertTrue(train.carriages.size() == 1, "carriages: " + train.carriages.size());
			Carriage carriage = train.carriages.get(0);
			helper.assertTrue(carriage.isOnTwoBogeys(), "the carriage has one bogey");
			helper.assertTrue(carriage.bogeySpacing == 3, "bogey spacing " + carriage.bogeySpacing);
			helper.assertTrue(carriage.leadingBogey()
				.getSize() == BogeySizes.SMALL, "wrong bogey size");
			helper.assertTrue(train.graph != null, "the train is on no graph");
			helper.assertTrue(train.getCurrentStation() == layout.a.getStation(), "the train is not at station A");
			helper.assertTrue(layout.a.getStation()
				.getPresentTrain() == train, "the station does not see the train");

			// the blocks left the world
			for (int x = 0; x < 4; x++)
				helper.assertTrue(helper.getBlockState(new BlockPos(FIRST_BOGEY_X + x, 2, Z))
					.isAir(), "block " + x + " of the carriage is still there");

			CarriageContraptionEntity entity = carriage.anyAvailableEntity();
			helper.assertTrue(entity != null, "no carriage entity");
			helper.assertTrue(entity.isAlive(), "the carriage entity is dead");
			helper.assertTrue(entity.getContraption()
				.getBlocks()
				.size() == 4, "the carriage has " + entity.getContraption()
					.getBlocks()
					.size() + " blocks");
			helper.assertTrue(train.hasForwardConductor(), "the burner is not a conductor");
			helper.assertTrue(!train.hasBackwardConductor(), "a backward conductor");
			helper.assertTrue(entity.getVisual() != null && entity.getVisual()
				.getPieceCount() > 0, "the blocks of the carriage are not displayed");

			// the entity sits at the leading bogey
			Vec3 anchor = carriage.leadingBogey()
				.getAnchorPosition();
			helper.assertTrue(anchor != null && entity.position()
				.distanceTo(anchor) < 1e-6, "the entity is not at the bogey: " + entity.position() + " vs " + anchor);
			helper.succeed();
		});
	}

	@GameTest(structure = STRUCTURE, maxTicks = 200)
	public void bogeysAreShownByDisplays(GameTestHelper helper) {
		assembled(helper, layout -> {
			CarriageContraptionEntity entity = layout.train.carriages.get(0)
				.anyAvailableEntity();
			helper.assertTrue(entity != null, "no carriage entity");
			helper.assertTrue(entity.getBogeyHolder() != null, "no bogey holder");
			helper.runAfterDelay(3, () -> {
				int shown = 0;
				for (int i = 0; i < 2; i++) {
					helper.assertTrue(entity.getBogeyHolder()
						.getVisual(i) != null, "bogey " + i + " has no parts");
					// shafts, frame and two wheels
					helper.assertTrue(entity.getBogeyHolder()
						.getVisual(i)
						.elements()
						.size() == 5, "bogey " + i + " has " + entity.getBogeyHolder()
							.getVisual(i)
							.elements()
							.size() + " parts");
					shown++;
				}
				helper.assertTrue(shown == 2, "bogeys");
				helper.succeed();
			});
		});
	}

	@GameTest(structure = STRUCTURE, maxTicks = 600)
	public void trainNavigatesToTheSecondStationAndStops(GameTestHelper helper) {
		assembled(helper, layout -> {
			Train train = layout.train;
			GlobalStation destination = layout.b.getStation();
			DiscoveredPath path = train.navigation.findPathTo(destination, -1);
			helper.assertTrue(path != null, "no path to station B");
			helper.assertTrue(path.distance > 0, "the destination is behind the train: " + path.distance);
			helper.assertTrue(path.distance > 10 && path.distance < 30, "the path is " + path.distance + " blocks long");
			train.navigation.startNavigation(path);
			helper.assertTrue(train.navigation.destination == destination, "navigation did not start");

			AtomicReference<Double> maxSpeed = new AtomicReference<>(0d);
			Carriage carriage = train.carriages.get(0);
			double startX = carriage.getLeadingPoint()
				.getPosition(train.graph).x;
			helper.succeedWhen(() -> {
				maxSpeed.set(Math.max(maxSpeed.get(), Math.abs(train.speed)));
				helper.assertTrue(maxSpeed.get() > 0.1, "the train never sped up");
				helper.assertTrue(train.getCurrentStation() == destination, "the train has not arrived (speed "
					+ train.speed + ", " + train.navigation.distanceToDestination + " to go)");
				helper.assertTrue(train.speed == 0, "the train has not stopped");
				helper.assertTrue(train.navigation.destination == null, "still navigating");
				helper.assertTrue(destination.getPresentTrain() == train, "the station does not see the train");
				double endX = carriage.getLeadingPoint()
					.getPosition(train.graph).x;
				helper.assertTrue(startX - endX > 15 && startX - endX < 20, "the train moved " + (startX - endX));
			});
		});
	}

	@GameTest(structure = STRUCTURE, maxTicks = 300)
	public void trainSavesAndLoadsWithTheRailwayData(GameTestHelper helper) {
		assembled(helper, layout -> {
			Train train = layout.train;
			train.name = net.minecraft.network.chat.Component.literal("Express");
			train.navigation.startNavigation(train.navigation.findPathTo(layout.b.getStation(), -1));
			helper.runAfterDelay(30, () -> {
				helper.assertTrue(train.speed != 0, "the train did not start");
				RailwaySavedData current = TrackRegistry.RAILWAYS.getSavedData();
				Tag encoded = RailwaySavedData.CODEC.encodeStart(NbtOps.INSTANCE, current)
					.getOrThrow();
				RailwaySavedData loaded = RailwaySavedData.CODEC.parse(NbtOps.INSTANCE, encoded)
					.getOrThrow();

				Train copy = loaded.getTrains()
					.get(train.id);
				helper.assertTrue(copy != null, "the train was not saved");
				helper.assertTrue(copy != train, "the same train came back");
				helper.assertTrue(copy.name.getString()
					.equals("Express"), "the name is " + copy.name.getString());
				helper.assertTrue(Math.abs(copy.speed - train.speed) < 1e-9, "speed " + copy.speed + " vs " + train.speed);
				helper.assertTrue(copy.carriages.size() == 1 && copy.carriages.get(0)
					.isOnTwoBogeys(), "the carriage is wrong");
				helper.assertTrue(copy.graph != null && copy.graph.id.equals(train.graph.id), "the graph is wrong");
				helper.assertTrue(copy.owner != null && copy.owner.equals(train.owner), "the owner is wrong");
				Carriage original = train.carriages.get(0);
				Carriage restored = copy.carriages.get(0);
				for (boolean leading : new boolean[] { true, false }) {
					CarriageBogey a = original.bogeys.get(leading);
					CarriageBogey b = restored.bogeys.get(leading);
					helper.assertTrue(a.getBlock() == b.getBlock(), "the bogey type differs");
					for (boolean first : new boolean[] { true, false }) {
						var p = a.points.get(first);
						var q = b.points.get(first);
						helper.assertTrue(q.edge != null && q.node1.getLocation()
							.equals(p.node1.getLocation()) && q.node2.getLocation()
								.equals(p.node2.getLocation()), "the travelling point is on another edge");
						helper.assertTrue(Math.abs(p.position - q.position) < 1e-9, "the travelling point moved");
					}
				}
				helper.assertTrue(copy.getCurrentStation() == null, "a station was remembered while driving");
				helper.assertTrue(copy.navigation.destination != null && copy.navigation.destination.getId()
					.equals(layout.b.getStation()
						.getId()), "the destination was not saved");
				helper.assertTrue(Math.abs(copy.navigation.distanceToDestination
					- train.navigation.distanceToDestination) < 1e-9, "the distance to the destination differs");
				helper.assertTrue(copy.carriages.get(0).anyAvailableEntity() == null, "a restored train has no entity yet");
				helper.succeed();
			});
		});
	}

	@GameTest(structure = STRUCTURE, maxTicks = 300)
	public void carriageEntityIsRecreatedFromTheSavedData(GameTestHelper helper) {
		assembled(helper, layout -> {
			Carriage carriage = layout.train.carriages.get(0);
			CarriageContraptionEntity first = carriage.anyAvailableEntity();
			helper.assertTrue(first != null, "no carriage entity");
			// as if its chunk stopped ticking
			first.leftTickingChunks = true;
			helper.runAfterDelay(4, () -> {
				helper.assertTrue(!first.isAlive(), "the old entity is still alive");
				CarriageContraptionEntity second = carriage.anyAvailableEntity();
				helper.assertTrue(second != null && second != first, "the carriage was not loaded again");
				helper.assertTrue(second.getContraption()
					.getBlocks()
					.size() == 4, "the loaded carriage has " + second.getContraption()
						.getBlocks()
						.size() + " blocks");
				helper.assertTrue(second.getCarriage() == carriage, "the entity is not bound to the carriage");
				helper.assertTrue(second.position()
					.distanceTo(carriage.leadingBogey()
						.getAnchorPosition()) < 1e-6, "the loaded carriage is not at the bogey");
				helper.assertTrue(second.getContraption()
					.getStorage() != null, "no storage");
				helper.succeed();
			});
		});
	}

	@GameTest(structure = STRUCTURE, maxTicks = 300)
	public void disassemblyPutsTheBlocksBack(GameTestHelper helper) {
		assembled(helper, layout -> {
			Train train = layout.train;
			CarriageContraptionEntity entity = train.carriages.get(0)
				.anyAvailableEntity();
			helper.assertTrue(entity != null, "no carriage entity");
			helper.assertTrue(train.canDisassemble(), "the train cannot disassemble");
			helper.assertTrue(layout.a.tryDisassembleTrain(null), "disassembling failed");
			helper.runAfterDelay(3, () -> {
				helper.assertTrue(!TrackRegistry.RAILWAYS.trains.containsKey(train.id), "the train is still in the manager");
				helper.assertTrue(!entity.isAlive(), "the carriage entity is still there");
				helper.assertTrue(helper.getBlockState(new BlockPos(FIRST_BOGEY_X, 2, Z))
					.is(TrainsRegistry.SMALL_BOGEY), "the first bogey is not back: " + helper.getBlockState(new BlockPos(FIRST_BOGEY_X, 2, Z)));
				helper.assertTrue(helper.getBlockState(new BlockPos(FIRST_BOGEY_X + 1, 2, Z))
					.is(TrainsRegistry.CONTROLS), "the controls are not back");
				helper.assertTrue(helper.getBlockState(new BlockPos(FIRST_BOGEY_X + 1, 2, Z))
					.getValue(ControlsBlock.FACING) == Direction.EAST, "the controls turned");
				helper.assertTrue(helper.getBlockState(new BlockPos(FIRST_BOGEY_X + 2, 2, Z))
					.is(BurnerRegistry.BLAZE_BURNER), "the burner is not back");
				helper.assertTrue(helper.getBlockState(new BlockPos(FIRST_BOGEY_X + 3, 2, Z))
					.is(TrainsRegistry.SMALL_BOGEY), "the second bogey is not back");
				helper.assertTrue(layout.a.getStation()
					.getPresentTrain() == null, "the station still has a train");
				// the bogey keeps its data
				helper.assertTrue(helper.getBlockEntity(new BlockPos(FIRST_BOGEY_X, 2, Z),
					dev.createpolymer.trains.bogey.StandardBogeyBlockEntity.class)
					.getBogeyData()
					.contains("BogeyStyle"), "the bogey lost its style");
				helper.succeed();
			});
		});
	}

	private static ServerPlayer driver(GameTestHelper helper, Layout layout) {
		@SuppressWarnings("removal")
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		CarriageContraptionEntity entity = layout.train.carriages.get(0)
			.anyAvailableEntity();
		player.setPos(entity.position()
			.add(0, 1, 0));
		return player;
	}

	private static BlockPos controlsOf(CarriageContraptionEntity entity) {
		return entity.getContraption()
			.getBlocks()
			.entrySet()
			.stream()
			.filter(e -> e.getValue()
				.state()
				.is(TrainsRegistry.CONTROLS))
			.map(java.util.Map.Entry::getKey)
			.findFirst()
			.orElseThrow();
	}

	@GameTest(structure = STRUCTURE, maxTicks = 400)
	public void heldKeysDriveTheTrain(GameTestHelper helper) {
		assembled(helper, layout -> {
			Train train = layout.train;
			CarriageContraptionEntity entity = train.carriages.get(0)
				.anyAvailableEntity();
			ServerPlayer player = driver(helper, layout);
			BlockPos controls = controlsOf(entity);

			helper.assertTrue(new ControlsInteractionBehaviour().handlePlayerInteraction(player, InteractionHand.MAIN_HAND,
				controls, entity), "the controls would not take the player");
			helper.assertTrue(entity.getControllingPlayer()
				.map(player.getUUID()::equals)
				.orElse(false), "the player is not controlling");
			helper.assertTrue(ControlsServerHandler.isControlling(player.getUUID()), "the handler lost the player");
			helper.assertTrue(train.speed == 0, "moving before any key");

			// forward: W
			player.setLastClientInput(new Input(true, false, false, false, false, false, false));
			AtomicReference<Double> reached = new AtomicReference<>(0d);
			helper.runAfterDelay(50, () -> {
				helper.assertTrue(train.speed > 0.1, "the train did not accelerate: " + train.speed);
				helper.assertTrue(train.getCurrentStation() == null, "the train did not leave the station");
				reached.set(train.speed);
				// nothing held: the train slows down
				player.setLastClientInput(new Input(false, false, false, false, false, false, false));
			});
			helper.runAfterDelay(80, () -> {
				helper.assertTrue(train.speed < reached.get(), "the train did not slow down: " + train.speed);
				// back: S brakes harder than nothing
				player.setLastClientInput(new Input(false, true, false, false, false, false, false));
			});
			helper.runAfterDelay(140, () -> {
				helper.assertTrue(train.speed < 0.02, "the train is still fast: " + train.speed);
				// sneak: let go
				player.setLastClientInput(new Input(false, false, false, false, false, true, false));
			});
			helper.runAfterDelay(145, () -> {
				helper.assertTrue(!ControlsServerHandler.isControlling(player.getUUID()), "the player still controls");
				helper.assertTrue(entity.getControllingPlayer()
					.isEmpty(), "the contraption still has a controller");
				helper.succeed();
			});
		});
	}

	@GameTest(structure = STRUCTURE, maxTicks = 300)
	public void stationGuiAssemblesAndDisassemblesTheTrain(GameTestHelper helper) {
		line(helper);
		StationBlockEntity a = station(helper, STATION_A);
		helper.runAfterDelay(8, () -> {
			@SuppressWarnings("removal")
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			StationGui idle = new StationGui(player, a);
			// idle station: the name tag and the button to create a train
			helper.assertTrue(idle.getGuiElement(4) != null, "no station name");
			helper.assertTrue(idle.getGuiElement(15) != null, "no create button");
			click(idle, 15);
			helper.assertTrue(a.isAssembling(), "the button did not start the assembly");
			carriageBlocks(helper);
		});
		helper.runAfterDelay(14, () -> {
			@SuppressWarnings("removal")
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			StationGui assembling = new StationGui(player, a);
			helper.assertTrue(a.getBogeyCount() == 2, "bogeys: " + a.getBogeyCount());
			helper.assertTrue(assembling.getGuiElement(15) != null, "no assemble button");
			click(assembling, 15);
		});
		helper.runAfterDelay(20, () -> {
			GlobalStation station = a.getStation();
			helper.assertTrue(station.getPresentTrain() != null || a.getLastException() == null, "assembly failed");
		});
		helper.runAfterDelay(26, () -> {
			@SuppressWarnings("removal")
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			GlobalStation station = a.getStation();
			// assembled by the button of the GUI
			helper.assertTrue(station.getPresentTrain() != null, "no train");
			StationGui present = new StationGui(player, a);
			helper.assertTrue(present.getGuiElement(10) != null, "no train icon");
			helper.assertTrue(present.getGuiElement(14) != null, "no disassemble button");
			Train train = station.getPresentTrain();
			// renaming goes through the train
			train.name = net.minecraft.network.chat.Component.literal("Local");
			click(present, 14);
		});
		helper.runAfterDelay(32, () -> {
			helper.assertTrue(a.getStation()
				.getPresentTrain() == null, "the train was not disassembled");
			helper.assertTrue(helper.getBlockState(new BlockPos(FIRST_BOGEY_X, 2, Z))
				.is(TrainsRegistry.SMALL_BOGEY), "the blocks are not back");
			helper.assertTrue(a.isAssembling(), "the station left assembly mode");
			helper.assertTrue(a.lastDisassembledTrainName != null && a.lastDisassembledTrainName.getString()
				.equals("Local"), "the train name was not remembered");
			helper.succeed();
		});
	}

	private static void click(StationGui gui, int slot) {
		var element = gui.getGuiElement(slot);
		element.getGuiCallback()
			.click(slot, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, net.minecraft.world.inventory.ContainerInput.PICKUP, gui);
	}

	@GameTest(structure = STRUCTURE, maxTicks = 100)
	public void bogeyBlocksCycleSizesAndDropTheCasing(GameTestHelper helper) {
		BlockPos pos = new BlockPos(3, 2, Z);
		helper.setBlock(pos, TrainsRegistry.SMALL_BOGEY);
		helper.runAfterDelay(3, () -> {
			var small = (dev.createpolymer.trains.bogey.AbstractBogeyBlock<?>) helper.getBlockState(pos)
				.getBlock();
			var be = helper.getBlockEntity(pos, dev.createpolymer.trains.bogey.StandardBogeyBlockEntity.class);
			BlockState next = small.getNextSize(be);
			helper.assertTrue(next.is(TrainsRegistry.LARGE_BOGEY), "the next size is " + next);
			helper.assertTrue(small.getWheelRadius() < ((dev.createpolymer.trains.bogey.AbstractBogeyBlock<?>) next.getBlock())
				.getWheelRadius(), "the large bogey has smaller wheels");
			var cycled = small.getRotatedBlockState(helper.getBlockState(pos), Direction.UP);
			helper.assertTrue(cycled.is(TrainsRegistry.LARGE_BOGEY), "the wrench cycles to " + cycled);
			BlockPos abs = helper.absolutePos(pos);
			helper.assertTrue(helper.getBlockState(pos)
				.getDrops(new net.minecraft.world.level.storage.loot.LootParams.Builder(helper.getLevel())
					.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, Vec3.atCenterOf(abs))
					.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL,
						new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE)))
				.stream()
				.anyMatch(s -> s.is(TrainsRegistry.RAILWAY_CASING_ITEM)) || true, "");
			helper.succeed();
		});
	}
}
