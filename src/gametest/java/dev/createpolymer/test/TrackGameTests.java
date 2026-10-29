package dev.createpolymer.test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import dev.createpolymer.logistics.depot.DepotBlockEntity;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.feature.TrackRegistry;
import dev.createpolymer.trains.RailwaySavedData;
import dev.createpolymer.trains.graph.EdgePointType;
import dev.createpolymer.trains.graph.TrackEdge;
import dev.createpolymer.trains.graph.TrackGraph;
import dev.createpolymer.trains.graph.TrackNode;
import dev.createpolymer.trains.graph.TrackNodeLocation;
import dev.createpolymer.trains.station.GlobalStation;
import dev.createpolymer.trains.track.BezierConnection;
import dev.createpolymer.trains.track.CurvedTrackInteraction;
import dev.createpolymer.trains.track.TrackBlock;
import dev.createpolymer.trains.track.TrackBlockEntity;
import dev.createpolymer.trains.track.TrackShape;
import dev.createpolymer.trains.track.TrackTargetingBehaviour;
import dev.createpolymer.trains.track.TrackTargetingBlockItem;
import dev.createpolymer.trains.track.TrackTargetingBlockItem.OverlapResult;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.elements.InteractionElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class TrackGameTests {

	private static BlockState track(TrackShape shape) {
		return TrackRegistry.TRACK.defaultBlockState()
			.setValue(TrackBlock.SHAPE, shape);
	}

	/** A track along the x axis at the given z, from x = 0 to 6. */
	private static void line(GameTestHelper helper, int z) {
		for (int x = 0; x <= 6; x++)
			helper.setBlock(new BlockPos(x, 1, z), track(TrackShape.XO));
	}

	/** The location of the node at the given point of the test area (a track's edge, in block coordinates). */
	private static TrackNodeLocation nodeAt(GameTestHelper helper, double x, double y, double z) {
		Vec3 base = Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO));
		return new TrackNodeLocation(base.add(x, y, z)).in(helper.getLevel());
	}

	private static TrackGraph graphAt(GameTestHelper helper, double x, double y, double z) {
		return TrackRegistry.RAILWAYS.getGraph(helper.getLevel(), nodeAt(helper, x, y, z));
	}

	private static TrackNode nodeIn(TrackGraph graph, TrackNodeLocation location) {
		return graph.locateNode(location);
	}

	private static int connectionCount(TrackGraph graph) {
		int count = 0;
		for (TrackNodeLocation location : graph.getNodes())
			count += graph.getConnectionsFrom(graph.locateNode(location))
				.size();
		return count;
	}

	/** The nodes a straight run from x to x + length has: both ends, and one at every multiple of 16 in between. */
	private static int expectedNodes(GameTestHelper helper, int length) {
		int start = helper.absolutePos(BlockPos.ZERO)
			.getX();
		int nodes = 2;
		for (int x = start + 1; x < start + length; x++)
			if (Math.floorMod(x, 16) == 0)
				nodes++;
		return nodes;
	}

	@SuppressWarnings("removal")
	private static ServerPlayer player(GameTestHelper helper, GameType mode) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(mode);
		player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(3, 1, 3))));
		return player;
	}

	private static InteractionResult click(GameTestHelper helper, Player player, BlockPos pos) {
		BlockPos abs = helper.absolutePos(pos);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs)
			.add(0, .1, 0), Direction.UP, abs, false);
		return player.getMainHandItem()
			.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
	}

	// The two tracks of an S-curve with one block of offset, joined by the player with the track item
	private static final BlockPos S_START = new BlockPos(0, 1, 1);
	private static final BlockPos S_END = new BlockPos(5, 1, 2);

	private static void sCurve(GameTestHelper helper, Player player) {
		helper.setBlock(S_START, track(TrackShape.XO));
		helper.setBlock(S_END, track(TrackShape.XO));
		InteractionResult selected = click(helper, player, S_START);
		helper.assertTrue(selected.consumesAction(), "selecting the first track did " + selected);
		helper.assertTrue(player.getMainHandItem()
			.has(TrackRegistry.TRACK_CONNECTING_FROM), "the item did not remember the first track");
		InteractionResult placed = click(helper, player, S_END);
		helper.assertTrue(placed.consumesAction(), "connecting the second track did " + placed);
	}

	private static ServerPlayer creativeWithTracks(GameTestHelper helper) {
		ServerPlayer player = player(helper, GameType.CREATIVE);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TrackRegistry.TRACK_ITEM, 16));
		return player;
	}

	@GameTest(maxTicks = 60)
	public void straightTracksFormOneGraphWithTwoEndNodes(GameTestHelper helper) {
		line(helper, 3);
		helper.runAfterDelay(6, () -> {
			TrackGraph graph = graphAt(helper, 0, 1, 3.5);
			helper.assertTrue(graph != null, "no graph at the start of the line");
			helper.assertTrue(graph == graphAt(helper, 7, 1, 3.5), "the two ends are in different graphs");
			int expected = expectedNodes(helper, 7);
			helper.assertTrue(graph.getNodes()
				.size() == expected, "the graph has " + graph.getNodes()
					.size() + " nodes, expected " + expected);

			TrackNode start = nodeIn(graph, nodeAt(helper, 0, 1, 3.5));
			helper.assertTrue(start != null, "no node at the start");
			helper.assertTrue(graph.getConnectionsFrom(start)
				.size() == 1, "an end node should have one connection");
			helper.assertTrue(connectionCount(graph) == 2 * (expected - 1), "wrong number of connections "
				+ connectionCount(graph));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void separateLinesAreSeparateGraphs(GameTestHelper helper) {
		line(helper, 1);
		line(helper, 5);
		helper.runAfterDelay(6, () -> {
			TrackGraph one = graphAt(helper, 0, 1, 1.5);
			TrackGraph two = graphAt(helper, 0, 1, 5.5);
			helper.assertTrue(one != null && two != null, "missing graph");
			helper.assertTrue(one != two, "unconnected lines share a graph");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void closingAGapMergesGraphs(GameTestHelper helper) {
		for (int x = 0; x <= 2; x++)
			helper.setBlock(new BlockPos(x, 1, 3), track(TrackShape.XO));
		for (int x = 4; x <= 6; x++)
			helper.setBlock(new BlockPos(x, 1, 3), track(TrackShape.XO));
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(graphAt(helper, 0, 1, 3.5) != graphAt(helper, 7, 1, 3.5), "the halves are one graph");
			helper.setBlock(new BlockPos(3, 1, 3), track(TrackShape.XO));
		});
		helper.runAfterDelay(12, () -> {
			TrackGraph graph = graphAt(helper, 0, 1, 3.5);
			helper.assertTrue(graph != null && graph == graphAt(helper, 7, 1, 3.5), "the halves did not merge");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void breakingAMiddleTrackSplitsTheGraph(GameTestHelper helper) {
		line(helper, 3);
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(graphAt(helper, 0, 1, 3.5) == graphAt(helper, 7, 1, 3.5), "the line is not one graph");
			helper.getLevel()
				.destroyBlock(helper.absolutePos(new BlockPos(3, 1, 3)), false);
		});
		helper.runAfterDelay(10, () -> {
			TrackGraph left = graphAt(helper, 0, 1, 3.5);
			TrackGraph right = graphAt(helper, 7, 1, 3.5);
			helper.assertTrue(left != null && right != null, "a half has no graph");
			helper.assertTrue(left != right, "the graph did not split");
			helper.assertTrue(left == graphAt(helper, 3, 1, 3.5), "the cut end belongs to the left half");
			helper.assertTrue(right == graphAt(helper, 4, 1, 3.5), "the cut end belongs to the right half");
			helper.assertTrue(graphAt(helper, 3.5, 1, 3.5) == null, "there is still a node in the gap");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void curvePlacedByThePlayerJoinsTheGraph(GameTestHelper helper) {
		ServerPlayer player = creativeWithTracks(helper);
		sCurve(helper, player);

		helper.runAfterDelay(8, () -> {
			BlockPos start = helper.absolutePos(S_START);
			BlockPos end = helper.absolutePos(S_END);
			// the end blocks that were placed hold the curve, which is stored on both
			BlockEntityPair pair = curveEnds(helper, start, end);
			helper.assertTrue(pair != null, "no block entity holds a curve");
			BezierConnection bc = pair.primary.getConnections()
				.get(pair.secondary.getBlockPos());
			helper.assertTrue(bc != null && bc.isPrimary(), "the first end holds no primary curve");
			helper.assertTrue(pair.secondary.getConnections()
				.get(pair.primary.getBlockPos()) != null, "the second end does not know the curve");
			helper.assertTrue(bc.getSegmentCount() >= 4, "curve is too short: " + bc.getSegmentCount());

			TrackGraph graph = TrackRegistry.RAILWAYS.getGraph(helper.getLevel(),
				new TrackNodeLocation(bc.starts.getFirst()).in(helper.getLevel()));
			helper.assertTrue(graph != null, "the curve is in no graph");
			TrackNode from = graph.locateNode(new TrackNodeLocation(bc.starts.getFirst()).in(helper.getLevel()));
			TrackNode to = graph.locateNode(new TrackNodeLocation(bc.starts.getSecond()).in(helper.getLevel()));
			helper.assertTrue(from != null && to != null, "the curve ends are no nodes");
			TrackEdge edge = graph.getConnectionsFrom(from)
				.get(to);
			helper.assertTrue(edge != null && edge.isTurn(), "no curved edge between the ends");
			helper.assertTrue(Math.abs(edge.getLength() - bc.getLength()) < 1e-6, "edge length differs from the curve");
			// both straight ends and the curve are one graph: two outer end nodes plus the curve's two nodes
			helper.assertTrue(graph.getNodes()
				.size() >= 4, "the graph has " + graph.getNodes()
					.size() + " nodes");
			helper.succeed();
		});
	}

	private record BlockEntityPair(TrackBlockEntity primary, TrackBlockEntity secondary) {
	}

	/** Finds the two block entities that hold the curve, anywhere around the clicked tracks. */
	private static BlockEntityPair curveEnds(GameTestHelper helper, BlockPos clickedStart, BlockPos clickedEnd) {
		ServerLevel level = helper.getLevel();
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		for (int x = -2; x <= 10; x++)
			for (int z = -2; z <= 10; z++) {
				BlockPos pos = base.offset(x, 1, z);
				if (level.getBlockEntity(pos) instanceof TrackBlockEntity tbe)
					for (Map.Entry<BlockPos, BezierConnection> entry : tbe.getConnections()
						.entrySet())
						if (entry.getValue()
							.isPrimary() && level.getBlockEntity(entry.getKey()) instanceof TrackBlockEntity other)
							return new BlockEntityPair(tbe, other);
			}
		return null;
	}

	@GameTest(maxTicks = 60)
	public void survivalPlayerPaysForTheCurve(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TrackRegistry.TRACK_ITEM, 64));
		sCurve(helper, player);
		ItemStack inHand = player.getMainHandItem();
		helper.assertTrue(inHand.getCount() < 64, "no tracks were used");
		helper.assertTrue(!inHand.has(TrackRegistry.TRACK_CONNECTING_FROM), "the selection stayed on the item");
		int used = 64 - inHand.getCount();
		helper.assertTrue(used >= 2 && used < 20, "unexpected cost of the curve: " + used);
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void curveNeedsEnoughTracks(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TrackRegistry.TRACK_ITEM, 1));
		helper.setBlock(S_START, track(TrackShape.XO));
		helper.setBlock(S_END, track(TrackShape.XO));
		helper.assertTrue(click(helper, player, S_START).consumesAction(), "could not select the first track");
		InteractionResult result = click(helper, player, S_END);
		helper.assertTrue(result == InteractionResult.FAIL, "connecting with one track did " + result);
		helper.assertTrue(player.getMainHandItem()
			.getCount() == 1, "the track was used up");
		helper.assertTrue(curveEnds(helper, helper.absolutePos(S_START), helper.absolutePos(S_END)) == null,
			"a curve was placed anyway");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void graphSurvivesSaveAndLoad(GameTestHelper helper) {
		ServerPlayer player = creativeWithTracks(helper);
		sCurve(helper, player);
		line(helper, 5);
		helper.runAfterDelay(10, () -> {
			TrackGraph straight = graphAt(helper, 0, 1, 5.5);
			helper.assertTrue(straight != null, "no graph for the line");
			BlockEntityPair pair = curveEnds(helper, BlockPos.ZERO, BlockPos.ZERO);
			helper.assertTrue(pair != null, "no curve");
			TrackGraph curved = TrackRegistry.RAILWAYS.getGraph(helper.getLevel(),
				new TrackNodeLocation(pair.primary.getConnections()
					.get(pair.secondary.getBlockPos()).starts.getFirst()).in(helper.getLevel()));
			helper.assertTrue(curved != null && curved != straight, "the curve's graph is missing");

			// through the codec of the saved data, as the world save does
			RailwaySavedData current = TrackRegistry.RAILWAYS.getSavedData();
			Tag encoded = RailwaySavedData.CODEC.encodeStart(NbtOps.INSTANCE, current)
				.getOrThrow();
			RailwaySavedData loaded = RailwaySavedData.CODEC.parse(NbtOps.INSTANCE, encoded)
				.getOrThrow();

			for (TrackGraph original : List.of(straight, curved)) {
				TrackGraph copy = loaded.getTrackNetworks()
					.get(original.id);
				helper.assertTrue(copy != null, "graph " + original.id + " was not saved");
				helper.assertTrue(copy.getNodes()
					.equals(original.getNodes()), "the nodes differ: " + copy.getNodes()
						.size() + " vs " + original.getNodes()
							.size());
				helper.assertTrue(connectionCount(copy) == connectionCount(original), "the connections differ");
				for (TrackNodeLocation location : original.getNodes()) {
					TrackNode node = original.locateNode(location);
					TrackNode copied = copy.locateNode(location);
					helper.assertTrue(copied != null && copied.getNormal()
						.distanceTo(node.getNormal()) < 1e-6, "the node normal differs at " + location);
					for (Map.Entry<TrackNode, TrackEdge> entry : original.getConnectionsFrom(node)
						.entrySet()) {
						TrackNode otherCopy = copy.locateNode(entry.getKey()
							.getLocation());
						TrackEdge copiedEdge = copy.getConnectionsFrom(copied)
							.get(otherCopy);
						helper.assertTrue(copiedEdge != null, "an edge is missing");
						helper.assertTrue(copiedEdge.isTurn() == entry.getValue()
							.isTurn(), "the curve flag differs");
						helper.assertTrue(Math.abs(copiedEdge.getLength() - entry.getValue()
							.getLength()) < 1e-6, "the edge length differs");
					}
				}
			}

			// the block entities keep their curves too
			CompoundTag blockEntityData = pair.primary.saveWithFullMetadata(helper.getLevel()
				.registryAccess());
			helper.assertTrue(blockEntityData.contains("Connections"), "the curve is not saved with the block entity");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void breakingACurveSplitsTheGraph(GameTestHelper helper) {
		ServerPlayer player = creativeWithTracks(helper);
		sCurve(helper, player);
		AtomicReference<BlockEntityPair> pair = new AtomicReference<>();
		helper.runAfterDelay(8, () -> {
			pair.set(curveEnds(helper, BlockPos.ZERO, BlockPos.ZERO));
			helper.assertTrue(pair.get() != null, "no curve");
			TrackBlockEntity primary = pair.get().primary;
			CurvedTrackInteraction.onAttack(player, helper.getLevel(), primary.getBlockPos(),
				pair.get().secondary.getBlockPos());
		});
		helper.runAfterDelay(14, () -> {
			helper.assertTrue(pair.get().primary.getConnections()
				.isEmpty(), "the curve is still there");
			helper.assertTrue(pair.get().secondary.getConnections()
				.isEmpty() || pair.get().secondary.isRemoved(), "the other end still has it");
			// the graph that held the curve is gone: the straight pieces are alone
			TrackGraph graph = graphAt(helper, 0, 1, 1.5);
			TrackGraph other = graphAt(helper, 6, 1, 2.5);
			helper.assertTrue(graph != other || graph == null, "the pieces are still one graph");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void curveIsShownByDisplays(GameTestHelper helper) {
		ServerPlayer player = creativeWithTracks(helper);
		sCurve(helper, player);
		helper.runAfterDelay(10, () -> {
			BlockEntityPair pair = curveEnds(helper, BlockPos.ZERO, BlockPos.ZERO);
			helper.assertTrue(pair != null, "no curve");
			BezierConnection bc = pair.primary.getConnections()
				.get(pair.secondary.getBlockPos());
			BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), pair.primary.getBlockPos());
			helper.assertTrue(attachment != null, "the track has no model");
			int displays = 0, boxes = 0;
			for (VirtualElement element : attachment.holder()
				.getElements()) {
				if (element instanceof ItemDisplayElement display && !display.getItem()
					.isEmpty())
					displays++;
				if (element instanceof InteractionElement)
					boxes++;
			}
			// the track piece itself, and a tie plus two rails for every segment
			helper.assertTrue(displays == 1 + 3 * bc.getSegmentCount(), "the track shows " + displays
				+ " pieces for " + bc.getSegmentCount() + " segments");
			helper.assertTrue(boxes > 0, "the curve cannot be pointed at");

			// the other end draws none of it
			BlockBoundAttachment secondary =
				BlockBoundAttachment.get(helper.getLevel(), pair.secondary.getBlockPos());
			int others = 0;
			for (VirtualElement element : secondary.holder()
				.getElements())
				if (element instanceof ItemDisplayElement display && !display.getItem()
					.isEmpty())
					others++;
			helper.assertTrue(others == 1, "the secondary end shows " + others + " pieces");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void trackTargetingFindsThePositionOnTheEdge(GameTestHelper helper) {
		line(helper, 3);
		BlockPos depotPos = new BlockPos(2, 1, 4);
		helper.setBlock(depotPos, ModBlocks.DEPOT);
		helper.runAfterDelay(6, () -> {
			BlockPos trackPos = helper.absolutePos(new BlockPos(2, 1, 3));
			AtomicReference<OverlapResult> result = new AtomicReference<>();
			TrackTargetingBlockItem.withGraphLocation(helper.getLevel(), trackPos, true, null, EdgePointType.STATION,
				(overlap, location) -> result.set(overlap));
			helper.assertTrue(result.get() == OverlapResult.VALID, "targeting the track gave " + result.get());

			// a block next to the track that targets it: the depot stands in for a station here
			BlockPos abs = helper.absolutePos(depotPos);
			DepotBlockEntity depot = helper.getBlockEntity(depotPos, DepotBlockEntity.class);
			TrackTargetingBehaviour<GlobalStation> behaviour =
				new TrackTargetingBehaviour<>(depot, EdgePointType.STATION);
			CompoundTag data = new CompoundTag();
			data.store("TargetTrack", BlockPos.CODEC, trackPos.subtract(abs));
			data.putBoolean("TargetDirection", true);
			behaviour.read(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel()
				.registryAccess(), data));
			depot.attachBehaviourLate(behaviour);
		});
		helper.runAfterDelay(14, () -> {
			DepotBlockEntity depot = helper.getBlockEntity(depotPos, DepotBlockEntity.class);
			TrackTargetingBehaviour<?> behaviour = depot.getBehaviour(TrackTargetingBehaviour.TYPE);
			helper.assertTrue(behaviour != null && behaviour.getEdgePoint() != null, "no edge point was made");
			GlobalStation station = (GlobalStation) behaviour.getEdgePoint();
			TrackGraph graph = graphAt(helper, 0, 1, 3.5);
			helper.assertTrue(graph.getPoints(EdgePointType.STATION)
				.contains(station), "the station is not in the graph");

			// on the edge from one end to the other the point is where the targeted track is: 2.5 blocks from the
			// start, 4.5 from the end
			TrackNode start = nodeIn(graph, nodeAt(helper, 0, 1, 3.5));
			TrackNode end = nodeIn(graph, nodeAt(helper, 7, 1, 3.5));
			TrackEdge edge = graph.getConnectionsFrom(start)
				.get(end);
			if (edge == null) {
				// a node in between at a chunk border: the station is on whichever edge holds it
				for (TrackNodeLocation location : graph.getNodes())
					for (TrackEdge candidate : graph.getConnectionsFrom(graph.locateNode(location))
						.values())
						if (candidate.getEdgeData()
							.getPoints()
							.contains(station))
							edge = candidate;
			}
			helper.assertTrue(edge != null && edge.getEdgeData()
				.getPoints()
				.contains(station), "the station is not on the edge");
			double fromStart = station.getLocationOn(edge);
			helper.assertTrue(Math.abs(fromStart - 2.5) < 1e-6 || Math.abs(fromStart - 4.5) < 1e-6
				|| expectedNodes(helper, 7) > 2, "the station is " + fromStart + " blocks from the start");

			// the spot is taken now, and the same targeting is refused
			AtomicReference<OverlapResult> second = new AtomicReference<>();
			TrackTargetingBlockItem.withGraphLocation(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 3)), true,
				null, EdgePointType.STATION, (overlap, location) -> second.set(overlap));
			helper.assertTrue(second.get() == OverlapResult.OCCUPIED, "a second station got " + second.get());

			// and the point is saved with the graph
			RailwaySavedData loaded = RailwaySavedData.CODEC
				.parse(NbtOps.INSTANCE, RailwaySavedData.CODEC.encodeStart(NbtOps.INSTANCE, TrackRegistry.RAILWAYS.getSavedData())
					.getOrThrow())
				.getOrThrow();
			TrackGraph copy = loaded.getTrackNetworks()
				.get(graph.id);
			helper.assertTrue(copy != null && copy.getPoints(EdgePointType.STATION)
				.size() == 1, "the station was not saved");
			GlobalStation copiedStation = copy.getPoints(EdgePointType.STATION)
				.iterator()
				.next();
			helper.assertTrue(copiedStation.getId()
				.equals(station.getId()) && Math.abs(copiedStation.position - station.position) < 1e-6,
				"the saved station differs");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void removedGraphLeavesNoStaleNodes(GameTestHelper helper) {
		line(helper, 3);
		helper.runAfterDelay(6, () -> {
			for (int x = 0; x <= 6; x++)
				helper.getLevel()
					.destroyBlock(helper.absolutePos(new BlockPos(x, 1, 3)), false);
			Set<TrackGraph> found = new HashSet<>();
			for (double x = 0; x <= 7; x += .5) {
				TrackGraph graph = graphAt(helper, x, 1, 3.5);
				if (graph != null)
					found.add(graph);
			}
			helper.assertTrue(found.isEmpty(), "nodes of removed tracks are still in " + found.size() + " graphs");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void aTrackCannotSitOnATrack(GameTestHelper helper) {
		TestUtil.floor(helper);
		helper.setBlock(new BlockPos(2, 1, 2), track(TrackShape.ZO));
		BlockPos above = helper.absolutePos(new BlockPos(2, 2, 2));
		helper.assertTrue(!track(TrackShape.ZO).canSurvive(helper.getLevel(), above), "a track can sit on a track");
		helper.assertTrue(track(TrackShape.ZO).canSurvive(helper.getLevel(), helper.absolutePos(new BlockPos(4, 1, 4))),
			"a track cannot sit on the floor");
		helper.succeed();
	}
}
