package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import dev.createpolymer.contraptions.ControlledContraptionEntity;
import dev.createpolymer.contraptions.actors.seat.SeatBlock;
import dev.createpolymer.contraptions.actors.seat.SeatEntity;
import dev.createpolymer.contraptions.bearing.BearingBlock;
import dev.createpolymer.contraptions.glue.SuperGlueEntity;
import dev.createpolymer.contraptions.piston.MechanicalPistonBlock;
import dev.createpolymer.contraptions.render.ContraptionSeats;
import dev.createpolymer.contraptions.render.ContraptionSeats.SeatProxy;
import dev.createpolymer.kinetics.base.DirectionalAxisKineticBlock;
import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.motor.CreativeMotorBlockEntity;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.feature.ActorsRegistry;
import dev.createpolymer.registry.feature.BearingRegistry;
import dev.createpolymer.registry.feature.ContraptionRegistry;
import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What vanilla clients are told about entities sitting on the seats of a moving contraption: a seat proxy each,
 * standing where the rider is, with the rider as its passenger; and about the ones sitting on a seat on the ground.
 */
public class SeatRidingGameTests {
	private static final BlockPos PISTON = new BlockPos(2, 3, 2);
	private static final BlockPos SEAT = new BlockPos(2, 5, 2);
	private static final BlockPos BEARING = new BlockPos(3, 1, 3);
	private static final BlockPos ARM_SEAT = new BlockPos(5, 2, 3);
	private static final double EPSILON = 1e-6;
	/** The turn is a float calculation in Create, a double one in the rotation state: a few tenths of a millimetre apart. */
	private static final double ROTATION_TOLERANCE = 2e-3;

	// ---- setups ----

	private static SeatBlock seat() {
		return ActorsRegistry.SEATS.get(DyeColor.RED);
	}

	/** A piston facing up at (2,3,2) with two poles under it; stone and a seat on its head, a pig on the seat. */
	private static Pig pistonWithSeatedPig(GameTestHelper helper) {
		floor(helper);
		for (int y = 1; y <= 2; y++)
			helper.setBlock(new BlockPos(2, y, 2), ContraptionRegistry.PISTON_EXTENSION_POLE.defaultBlockState()
				.setValue(MechanicalPistonBlock.FACING, Direction.UP));
		helper.setBlock(PISTON, ContraptionRegistry.MECHANICAL_PISTON.defaultBlockState()
			.setValue(MechanicalPistonBlock.FACING, Direction.UP)
			.setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, false));
		helper.setBlock(new BlockPos(2, 4, 2), Blocks.STONE);
		helper.setBlock(SEAT, seat().defaultBlockState());
		Pig pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, 2, 5, 2);
		helper.runAfterDelay(1, () -> SeatBlock.sitDown(helper.getLevel(), helper.absolutePos(SEAT), pig));
		return pig;
	}

	private static void powerPiston(GameTestHelper helper, int rpm) {
		BlockPos motor = PISTON.north();
		helper.setBlock(motor, ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.SOUTH));
		helper.runAfterDelay(2, () -> helper.getBlockEntity(motor, CreativeMotorBlockEntity.class).setSetting(rpm));
	}

	/** A bearing facing up with an arm to its east: stone, stone and a seat with a pig on it, glued together. */
	private static Pig bearingWithSeatedPig(GameTestHelper helper) {
		floor(helper);
		BlockPos motor = BEARING.below();
		helper.setBlock(motor, ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
		helper.setBlock(BEARING, BearingRegistry.MECHANICAL_BEARING.defaultBlockState().setValue(BearingBlock.FACING, Direction.UP));
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(4, 2, 3), Blocks.STONE);
		helper.setBlock(ARM_SEAT, seat().defaultBlockState());
		helper.getLevel().addFreshEntity(new SuperGlueEntity(helper.getLevel(),
			SuperGlueEntity.span(helper.absolutePos(new BlockPos(3, 2, 3)), helper.absolutePos(ARM_SEAT))));
		Pig pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, ARM_SEAT.above());
		helper.runAfterDelay(1, () -> SeatBlock.sitDown(helper.getLevel(), helper.absolutePos(ARM_SEAT), pig));
		helper.runAfterDelay(2, () -> helper.getBlockEntity(motor, CreativeMotorBlockEntity.class).setSetting(32));
		return pig;
	}

	/** Tests run side by side: only the contraption entities anchored inside this test's box count. */
	private static ControlledContraptionEntity contraptionOf(GameTestHelper helper, Entity rider) {
		AABB bounds = helper.getBounds().inflate(1);
		List<ControlledContraptionEntity> list = helper.getLevel().getEntitiesOfClass(ControlledContraptionEntity.class, bounds.inflate(16),
			e -> bounds.contains(e.position()));
		return list.isEmpty() ? null : list.get(0);
	}

	// ---- a client, as far as passengers go ----

	/**
	 * A player the server sends packets to; they are collected instead of encoded, and applied to a model of what a
	 * client does with them: which entities it knows and which vehicle each of them is on.
	 */
	private static final class Client {
		private final ServerPlayer player;
		private final EmbeddedChannel channel;
		/** The ids of the passengers of each vehicle the client knows. */
		private final Map<Integer, IntList> passengers = new HashMap<>();
		private final Map<Integer, Integer> vehicleOf = new HashMap<>();
		private final Set<Integer> known = new HashSet<>();
		private final List<Packet<?>> log = new ArrayList<>();

		private Client(GameTestHelper helper, Vec3 at) {
			CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
			player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
				@Override
				public GameType gameMode() {
					return GameType.SURVIVAL;
				}
			};
			Connection connection = new Connection(PacketFlow.SERVERBOUND);
			channel = new EmbeddedChannel(connection);
			helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
			player.teleportTo(helper.getLevel(), at.x, at.y, at.z, Set.of(), 0, 0, false);
			drain();
			log.clear();
		}

		/** Takes in what has been sent since the last call. */
		private void drain() {
			// what the server does whenever a player moves: pair it with the entities that come into range
			player.level().getChunkSource().move(player);
			// the connection writes through its event loop, and flushes when the tick is over
			channel.runPendingTasks();
			channel.flush();
			Object message;
			while ((message = channel.readOutbound()) != null)
				if (message instanceof Packet<?> packet)
					apply(packet);
		}

		private void apply(Packet<?> packet) {
			if (packet instanceof ClientboundChunkBatchFinishedPacket) {
				// a client tells the server how many chunks per tick it takes: without that no chunk, and so no entity
				// in it, is tracked after the first batch
				player.connection.handleChunkBatchReceived(new ServerboundChunkBatchReceivedPacket(64f));
				return;
			}
			if (packet instanceof ClientboundBundlePacket bundle) {
				for (Packet<?> sub : bundle.subPackets())
					apply(sub);
				return;
			}
			log.add(packet);
			if (packet instanceof ClientboundAddEntityPacket add) {
				known.add(add.getId());
			} else if (packet instanceof ClientboundRemoveEntitiesPacket remove) {
				for (int id : remove.entityIds()) {
					// Entity.setRemoved: it stops riding and its passengers stop riding it
					leave(id);
					IntList riders = passengers.remove(id);
					if (riders != null)
						for (int rider : riders)
							vehicleOf.remove(rider);
					known.remove(id);
				}
			} else if (packet instanceof ClientboundSetPassengersPacket set) {
				// ClientPacketListener.handleSetEntityPassengersPacket
				if (!known.contains(set.getVehicle()) && set.getVehicle() != player.getId())
					return;
				IntList old = passengers.remove(set.getVehicle());
				if (old != null)
					for (int rider : old)
						vehicleOf.remove(rider);
				IntList now = new IntArrayList();
				for (int id : set.getPassengers()) {
					if (!known.contains(id) && id != player.getId())
						continue;
					leave(id);
					vehicleOf.put(id, set.getVehicle());
					now.add(id);
				}
				passengers.put(set.getVehicle(), now);
			}
		}

		private void leave(int rider) {
			Integer vehicle = vehicleOf.remove(rider);
			if (vehicle != null && passengers.containsKey(vehicle))
				passengers.get(vehicle).rem(rider);
		}

		/** Where a test that waits for the client to catch up is, for failure messages. */
		private String state(Entity carrier, Entity rider, Integer proxy) {
			return "client knows carrier " + (carrier != null && known.contains(carrier.getId())) + ", rider " + known.contains(rider.getId())
				+ ", proxy " + (proxy != null && known.contains(proxy)) + "; rider is on " + vehicleOf(rider) + ", proxy " + proxy
				+ ", carrier " + (carrier == null ? null : carrier.getId()) + "; log " + log.size();
		}

		/** What the client was sent about an entity, for failure messages. */
		private String history(int id) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < log.size(); i++) {
				Packet<?> p = log.get(i);
				if (p instanceof ClientboundAddEntityPacket a && a.getId() == id)
					sb.append(i).append(":add ");
				else if (p instanceof ClientboundRemoveEntitiesPacket r && r.entityIds().contains(id))
					sb.append(i).append(":remove ");
				else if (p instanceof ClientboundSetPassengersPacket s && (s.getVehicle() == id || IntList.of(s.getPassengers()).contains(id)))
					sb.append(i).append(":passengers(").append(s.getVehicle()).append("<-").append(java.util.Arrays.toString(s.getPassengers())).append(") ");
			}
			sb.append("of ").append(log.size()).append(", removes: ");
			for (int i = 0; i < log.size(); i++)
				if (log.get(i) instanceof ClientboundRemoveEntitiesPacket r)
					sb.append(i).append(r.entityIds()).append(' ');
			sb.append(" connected=").append(player.connection.isAcceptingMessages());
			return sb.toString();
		}

		/** The id of the vehicle the client has the entity on, or -1. */
		private int vehicleOf(Entity entity) {
			return vehicleOf.getOrDefault(entity.getId(), -1);
		}

		private void leaveWorld(GameTestHelper helper) {
			player.connection.disconnect(net.minecraft.network.chat.Component.literal("test over"));
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
	}

	/** Where a vanilla client puts a passenger of an entity at {@code vehiclePos} that is an item display. */
	private static Vec3 clientRiderPosition(GameTestHelper helper, Vec3 vehiclePos, Entity rider) {
		Entity display = EntityTypes.ITEM_DISPLAY.create(helper.getLevel(), EntitySpawnReason.LOAD);
		display.setPos(vehiclePos);
		return display.getPassengerRidingPosition(rider).subtract(rider.getVehicleAttachmentPoint(display));
	}

	private static void assertSame(GameTestHelper helper, Vec3 expected, Vec3 actual, String what) {
		assertSame(helper, expected, actual, what, EPSILON);
	}

	private static void assertSame(GameTestHelper helper, Vec3 expected, Vec3 actual, String what, double tolerance) {
		helper.assertTrue(expected.distanceTo(actual) <= tolerance, what + ": expected " + expected + " but was " + actual);
	}

	// ---- the position of the proxy ----

	@GameTest(maxTicks = 300)
	public void theProxyOfASeatedPigStandsWhereAClientPutsThePigOnItsSeat(GameTestHelper helper) {
		Pig pig = pistonWithSeatedPig(helper);
		powerPiston(helper, -16);
		double[] lastY = { Double.NaN };
		int[] samples = { 0 };
		helper.onEachTick(() -> {
			ControlledContraptionEntity entity = contraptionOf(helper, pig);
			if (entity == null || pig.getVehicle() != entity || entity.getVisual() == null)
				return;
			ContraptionSeats seats = entity.getVisual().getSeats();
			seats.refresh();
			SeatProxy proxy = seats.getProxy(pig);
			helper.assertTrue(proxy != null, "no proxy for the pig, proxies: " + seats.getProxies().size());
			helper.assertTrue(proxy.rider() == pig, "the proxy has another rider");
			helper.assertTrue(seats.getProxies().size() == 1, "proxies " + seats.getProxies().size());

			// Create puts the pig here (the piston moves the contraption in between, so ask again)
			entity.positionRider(pig);
			assertSame(helper, pig.position(), clientRiderPosition(helper, proxy.position(), pig), "client position of the pig");
			assertSame(helper, pig.getEyePosition(), clientRiderPosition(helper, proxy.position(), pig).add(0, pig.getEyeHeight(), 0),
				"eyes of the pig");
			// the seat is 2 blocks above the piston (stone, then the seat): the pig is lifted along with it
			if (!Double.isNaN(lastY[0]) && proxy.position().y < lastY[0] - EPSILON)
				helper.fail("the proxy sinks");
			if (samples[0] == 0)
				lastY[0] = proxy.position().y;
			samples[0]++;
			if (samples[0] == 5) {
				helper.assertTrue(proxy.position().y > lastY[0] + .05, "the proxy does not rise: " + lastY[0] + " -> " + proxy.position().y);
				helper.assertTrue(Math.abs(proxy.position().x - (helper.absolutePos(SEAT).getX() + .5)) < EPSILON,
					"the proxy is not above the seat: " + proxy.position());
				helper.succeed();
			}
		});
	}

	@GameTest(maxTicks = 300, padding = 24)
	public void theProxyOfAPigOnATurningArmFollowsTheSeatAroundTheAxis(GameTestHelper helper) {
		Pig pig = bearingWithSeatedPig(helper);
		int[] samples = { 0 };
		helper.onEachTick(() -> {
			ControlledContraptionEntity entity = contraptionOf(helper, pig);
			if (entity == null || pig.getVehicle() != entity || entity.getVisual() == null)
				return;
			ContraptionSeats seats = entity.getVisual().getSeats();
			seats.refresh();
			SeatProxy proxy = seats.getProxy(pig);
			helper.assertTrue(proxy != null, "no proxy for the pig");

			// the proxy is put for the turn two ticks ahead: the display glides 3 ticks behind
			Vec3 ahead = seats.proxyPosition(pig, entity.getPredictedRotationState(ContraptionSeats.LEAD_TICKS));
			assertSame(helper, ahead, proxy.position(), "proxy position");

			// ... and for the turn now, a client puts the pig where Create puts it: this also checks that the rotation
			// state turns the seat the way the entity does
			Vec3 now = seats.proxyPosition(pig, entity.getRotationState());
			entity.positionRider(pig);
			assertSame(helper, pig.position(), clientRiderPosition(helper, now, pig), "client position of the pig", ROTATION_TOLERANCE);

			float angle = Math.abs(entity.getAngle(1));
			if (angle < 20)
				return;
			// the seat is two blocks from the axis
			Vec3 axis = Vec3.atBottomCenterOf(helper.absolutePos(BEARING));
			double radius = Math.hypot(pig.getX() - axis.x, pig.getZ() - axis.z);
			helper.assertTrue(Math.abs(radius - 2) < 1e-3, "the pig is " + radius + " from the axis, turned " + angle);
			helper.assertTrue(Math.abs(Math.hypot(proxy.position().x - axis.x, proxy.position().z - axis.z) - 2) < .3,
				"the proxy is not around the axis: " + proxy.position() + " " + axis);
			Vec3 start = Vec3.atBottomCenterOf(helper.absolutePos(ARM_SEAT));
			helper.assertTrue(Math.hypot(pig.getX() - start.x, pig.getZ() - start.z) > .5,
				"the pig has not moved along: " + pig.position());
			if (++samples[0] == 3)
				helper.succeed();
		});
	}

	// ---- what a client is told ----

	@GameTest(maxTicks = 400)
	public void aTrackingClientHasThePigOnTheProxyAndNotOnTheCarrier(GameTestHelper helper) {
		Pig pig = pistonWithSeatedPig(helper);
		powerPiston(helper, -16);
		Client[] client = { null };
		int[] ticksOnProxy = { 0 };
		int[] ticksElsewhere = { 0 };
		helper.runAfterDelay(3, () -> client[0] = new Client(helper, helper.absoluteVec(new Vec3(2.5, 6, 5))));
		helper.onEachTick(() -> {
			if (client[0] == null)
				return;
			client[0].drain();
			ControlledContraptionEntity entity = contraptionOf(helper, pig);
			if (entity == null || pig.getVehicle() != entity || entity.getVisual() == null) {
				if (helper.getTick() > 150)
					helper.fail("the pig does not ride the contraption: " + entity + " " + pig.getVehicle());
				return;
			}
			SeatProxy proxy = entity.getVisual().getSeats().getProxy(pig);
			if (proxy == null)
				return;
			int vehicle = client[0].vehicleOf(pig);
			if (vehicle == proxy.entityId())
				ticksOnProxy[0]++;
			else
				ticksElsewhere[0]++;
			if (ticksOnProxy[0] + ticksElsewhere[0] < 20)
				return;
			boolean knowsProxy = client[0].known.contains(proxy.entityId());
			boolean knowsCarrier = client[0].known.contains(entity.getId());
			boolean knowsPig = client[0].known.contains(pig.getId());
			helper.assertTrue(knowsProxy && knowsCarrier && knowsPig,
				"the client does not know proxy/carrier/pig: " + knowsProxy + knowsCarrier + knowsPig);
			helper.assertTrue(client[0].vehicleOf(pig) == proxy.entityId(), "the pig rides " + client[0].vehicleOf(pig));
			helper.assertTrue(!client[0].passengers.getOrDefault(entity.getId(), new IntArrayList()).contains(pig.getId()),
				"the pig is a passenger of the carrier");
			// The lists the tracker sends leave the pig out. Two packets are sent past it and list the pig on the
			// carrier: the one a tracking connection gets from Polymer when it starts to track the carrier, and the
			// one the pig's own pairing sends about its vehicle. Each is followed by the proxy's passengers, so the
			// pig is on the carrier for one tick at most.
			int carrierListsWithPig = 0;
			int carrierLists = 0;
			for (Packet<?> packet : client[0].log) {
				if (packet instanceof ClientboundSetPassengersPacket set && set.getVehicle() == entity.getId()) {
					carrierLists++;
					if (IntList.of(set.getPassengers()).contains(pig.getId()))
						carrierListsWithPig++;
				}
			}
			helper.assertTrue(carrierLists >= 1, "the client was not told about the carrier's passengers");
			helper.assertTrue(carrierListsWithPig <= 2, carrierListsWithPig + " of " + carrierLists + " lists of the carrier's passengers have the pig");
			helper.assertTrue(ticksElsewhere[0] <= 2, "the pig was off the proxy for " + ticksElsewhere[0] + " ticks");
			client[0].leaveWorld(helper);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 300)
	public void thePassengerListOfTheCarrierIsSentWithoutTheSeatedPig(GameTestHelper helper) {
		Pig pig = pistonWithSeatedPig(helper);
		powerPiston(helper, -16);
		helper.onEachTick(() -> {
			ControlledContraptionEntity entity = contraptionOf(helper, pig);
			if (entity == null || pig.getVehicle() != entity || entity.getVisual() == null)
				return;
			ContraptionSeats seats = entity.getVisual().getSeats();
			seats.refresh();
			helper.assertTrue(seats.getProxy(pig) != null, "no proxy");
			ClientboundSetPassengersPacket packet = new ClientboundSetPassengersPacket(entity);
			boolean before = IntList.of(packet.getPassengers()).contains(pig.getId());
			int piecesAndPig = packet.getPassengers().length;
			List<Packet<?>> sent = new ArrayList<>();
			entity.onEntityPacketSent(sent::add, packet);
			helper.assertTrue(before, "the unrewritten packet does not list the pig");
			helper.assertTrue(sent.size() == 1 && sent.get(0) instanceof ClientboundSetPassengersPacket, "the packet is not passed on");
			ClientboundSetPassengersPacket out = (ClientboundSetPassengersPacket) sent.get(0);
			helper.assertTrue(out.getVehicle() == entity.getId(), "vehicle changed");
			helper.assertTrue(!IntList.of(out.getPassengers()).contains(pig.getId()), "the pig is still a passenger of the carrier");
			helper.assertTrue(out.getPassengers().length == piecesAndPig - 1, "other passengers were dropped: " + out.getPassengers().length
				+ " of " + piecesAndPig);
			// the block displays stay on the carrier
			helper.assertTrue(out.getPassengers().length >= entity.getVisual().getPieceCount(), "the block displays are not passengers");
			// ... also on the wire
			net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
			ClientboundSetPassengersPacket.STREAM_CODEC.encode(buf, out);
			ClientboundSetPassengersPacket decoded = ClientboundSetPassengersPacket.STREAM_CODEC.decode(buf);
			helper.assertTrue(decoded.getVehicle() == entity.getId() && java.util.Arrays.equals(decoded.getPassengers(), out.getPassengers()),
				"the packet on the wire differs: " + java.util.Arrays.toString(decoded.getPassengers()) + " " + java.util.Arrays.toString(out.getPassengers()));
			// the server still has the pig on the contraption
			helper.assertTrue(entity.getPassengers().contains(pig), "the server lost the passenger");
			// a packet about another vehicle is left alone
			ClientboundSetPassengersPacket other = new ClientboundSetPassengersPacket(pig);
			List<Packet<?>> sentOther = new ArrayList<>();
			entity.onEntityPacketSent(sentOther::add, other);
			helper.assertTrue(sentOther.size() == 1 && sentOther.get(0) == other, "other packets are not passed on as they are");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 20)
	public void withoutIdsKeepsTheOrderOfTheOthers(GameTestHelper helper) {
		int[] kept = ContraptionSeats.withoutIds(new int[] { 5, 7, 9, 7, 11 }, new it.unimi.dsi.fastutil.ints.IntOpenHashSet(new int[] { 7 }));
		helper.assertTrue(java.util.Arrays.equals(kept, new int[] { 5, 9, 11 }), java.util.Arrays.toString(kept));
		helper.assertTrue(ContraptionSeats.withoutIds(new int[0], new it.unimi.dsi.fastutil.ints.IntOpenHashSet(new int[] { 7 })).length == 0, "empty");
		helper.succeed();
	}

	// ---- leaving ----

	@GameTest(maxTicks = 400)
	public void aPigThatLeavesItsSeatIsTakenOffTheProxyAndTheProxyGoes(GameTestHelper helper) {
		Pig pig = pistonWithSeatedPig(helper);
		powerPiston(helper, -16);
		Client[] client = { null };
		int[] proxyId = { -1 };
		int[] stage = { 0 };
		helper.runAfterDelay(3, () -> client[0] = new Client(helper, helper.absoluteVec(new Vec3(2.5, 6, 5))));
		helper.onEachTick(() -> {
			if (client[0] == null)
				return;
			client[0].drain();
			ControlledContraptionEntity entity = contraptionOf(helper, pig);
			if (stage[0] == 0) {
				if (entity == null || pig.getVehicle() != entity || entity.getVisual() == null)
					return;
				SeatProxy proxy = entity.getVisual().getSeats().getProxy(pig);
				if (proxy == null || client[0].vehicleOf(pig) != proxy.entityId()) {
					if (helper.getTick() > 150)
						helper.fail("the client does not get the pig on the proxy: " + client[0].state(entity, pig, proxy == null ? null : proxy.entityId()));
					return;
				}
				proxyId[0] = proxy.entityId();
				pig.stopRiding();
				// away from the seat, which would take it in again
				pig.teleportTo(helper.absoluteVec(new Vec3(6.5, 1, 6.5)).x, helper.absoluteVec(new Vec3(6.5, 1, 6.5)).y,
					helper.absoluteVec(new Vec3(6.5, 1, 6.5)).z);
				helper.assertTrue(entity.getVisual().getSeats().getProxy(pig) == null, "the proxy lives on");
				helper.assertTrue(entity.getVisual().getSeats().getProxies().isEmpty(), "proxies are left");
				stage[0] = 1;
				return;
			}
			// the packets of the dismount reached the client during the same tick
			client[0].drain();
			helper.assertTrue(client[0].vehicleOf(pig) == -1, "the client still has the pig on " + client[0].vehicleOf(pig) + ", proxy "
				+ proxyId[0] + ", carrier " + (entity == null ? null : entity.getId()) + ", pig " + pig.getId() + ": " + client[0].history(pig.getId())
				+ client[0].history(proxyId[0]));
			helper.assertTrue(!client[0].known.contains(proxyId[0]), "the client still knows the proxy");
			int clear = -1;
			int remove = -1;
			for (int i = 0; i < client[0].log.size(); i++) {
				Packet<?> p = client[0].log.get(i);
				if (p instanceof ClientboundSetPassengersPacket s && s.getVehicle() == proxyId[0] && s.getPassengers().length == 0 && clear < 0)
					clear = i;
				if (p instanceof ClientboundRemoveEntitiesPacket r && r.entityIds().contains(proxyId[0]) && remove < 0)
					remove = i;
			}
			helper.assertTrue(clear >= 0 && remove > clear, "the proxy is not emptied before it is removed: " + clear + " " + remove);
			client[0].leaveWorld(helper);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 400)
	public void whenTheContraptionIsPlacedBackThePigSitsOnTheSeatAndTheProxyIsGone(GameTestHelper helper) {
		Pig pig = pistonWithSeatedPig(helper);
		powerPiston(helper, -64);
		Client[] client = { null };
		int[] proxyId = { -1 };
		helper.runAfterDelay(3, () -> client[0] = new Client(helper, helper.absoluteVec(new Vec3(2.5, 6, 5))));
		helper.onEachTick(() -> {
			if (client[0] == null)
				return;
			client[0].drain();
			ControlledContraptionEntity entity = contraptionOf(helper, pig);
			if (entity != null && entity.getVisual() != null && pig.getVehicle() == entity) {
				SeatProxy proxy = entity.getVisual().getSeats().getProxy(pig);
				if (proxy != null)
					proxyId[0] = proxy.entityId();
				return;
			}
			if (proxyId[0] == -1 || entity != null)
				return;
			// the contraption has gone: the pig sits on a seat at the end of the track
			helper.assertTrue(pig.getVehicle() instanceof SeatEntity, "the pig does not sit on the placed seat: " + pig.getVehicle());
			helper.assertTrue(!client[0].known.contains(proxyId[0]), "the client still knows the proxy: " + client[0].history(proxyId[0])
				+ " vehicle " + pig.getVehicle() + " contraptions " + helper.getLevel().getEntitiesOfClass(ControlledContraptionEntity.class,
					helper.getBounds().inflate(64)).stream().map(c -> c.getId() + "@" + c.position() + " removed=" + c.isRemoved()).toList());
			for (int vehicle : client[0].passengers.keySet())
				helper.assertTrue(vehicle != proxyId[0] || client[0].passengers.get(vehicle).isEmpty(), "the proxy still has passengers");
			helper.assertTrue(client[0].vehicleOf(pig) == -1 || client[0].vehicleOf(pig) == pig.getVehicle().getId(),
				"the client has the pig on " + client[0].vehicleOf(pig) + " but it sits on " + pig.getVehicle().getId());
			client[0].leaveWorld(helper);
			helper.succeed();
		});
	}

	// ---- a riding player ----

	@GameTest(maxTicks = 300)
	public void aPlayerOnAContraptionSeatHasItsEyesWhereCreatePutsThem(GameTestHelper helper) {
		Pig pig = pistonWithSeatedPig(helper);
		powerPiston(helper, -16);
		Client[] client = { null };
		boolean[] seated = { false };
		int[] samples = { 0 };
		int[] mountMark = { 0 };
		helper.runAfterDelay(3, () -> client[0] = new Client(helper, helper.absoluteVec(new Vec3(2.5, 6, 5))));
		helper.onEachTick(() -> {
			if (client[0] == null)
				return;
			client[0].drain();
			ControlledContraptionEntity entity = contraptionOf(helper, pig);
			if (entity == null || entity.getVisual() == null || (!seated[0] && pig.getVehicle() != entity))
				return;
			ServerPlayer player = client[0].player;
			if (!seated[0]) {
				// the pig makes room
				pig.stopRiding();
				client[0].drain();
				mountMark[0] = client[0].log.size();
				helper.assertTrue(entity.handlePlayerInteraction(player, entity.getContraption().getSeats().get(0), Direction.UP, InteractionHand.MAIN_HAND),
					"the interaction was not handled");
				helper.assertTrue(player.getVehicle() == entity, "the player does not ride the contraption: " + player.getVehicle());
				seated[0] = true;
				return;
			}
			ContraptionSeats seats = entity.getVisual().getSeats();
			seats.refresh();
			SeatProxy proxy = seats.getProxy(player);
			helper.assertTrue(proxy != null, "no proxy for the player");
			entity.positionRider(player);
			Vec3 feet = clientRiderPosition(helper, proxy.position(), player);
			assertSame(helper, player.position(), feet, "client position of the player");
			assertSame(helper, player.getEyePosition(), feet.add(0, player.getEyeHeight(), 0), "eyes of the player");
			// the camera is on the seat: the seat's cushion is the top half of a block, one block above the stone
			Vec3 seatTop = Vec3.atBottomCenterOf(helper.absolutePos(SEAT)).add(0, .5, 0);
			helper.assertTrue(Math.abs(feet.x - seatTop.x) < EPSILON && Math.abs(feet.z - seatTop.z) < EPSILON, "feet off the seat: " + feet);
			helper.assertTrue(Math.abs(player.getEyePosition().y - (player.getY() + player.getEyeHeight())) < EPSILON, "eye height");
			client[0].drain();
			if (++samples[0] < 5)
				return;
			helper.assertTrue(client[0].vehicleOf(player) == proxy.entityId(),
				"the player's client has it on " + client[0].vehicleOf(player) + ", the proxy is " + proxy.entityId());
			helper.assertTrue(!client[0].passengers.getOrDefault(entity.getId(), new IntArrayList()).contains(player.getId()),
				"the client has the player on the carrier");

			// the passenger lists of the carrier since the player boarded: the one the server sends the player itself when
			// it boards lists it, the one the tracker sends all clients does not
			int lists = 0;
			int listsWithPlayer = 0;
			for (int i = mountMark[0]; i < client[0].log.size(); i++)
				if (client[0].log.get(i) instanceof ClientboundSetPassengersPacket set && set.getVehicle() == entity.getId()) {
					lists++;
					if (IntList.of(set.getPassengers()).contains(player.getId()))
						listsWithPlayer++;
				}
			helper.assertTrue(lists >= 2, "the carrier's passengers were sent " + lists + " times since the player boarded");
			helper.assertTrue(listsWithPlayer == 1, listsWithPlayer + " of " + lists + " lists have the player on the carrier");

			// getting off: the player's client is released from the proxy before the server sends it the carrier's
			// passengers, which have no player to release
			int mark = client[0].log.size();
			int proxyId = proxy.entityId();
			player.stopRiding();
			client[0].drain();
			helper.assertTrue(client[0].vehicleOf(player) == -1, "the client still has the player on " + client[0].vehicleOf(player));
			int cleared = -1;
			int carrierList = -1;
			for (int i = mark; i < client[0].log.size(); i++) {
				Packet<?> packet = client[0].log.get(i);
				if (packet instanceof ClientboundSetPassengersPacket set && set.getVehicle() == proxyId && set.getPassengers().length == 0 && cleared < 0)
					cleared = i;
				if (packet instanceof ClientboundSetPassengersPacket set && set.getVehicle() == entity.getId() && carrierList < 0)
					carrierList = i;
			}
			helper.assertTrue(cleared >= 0, "the proxy's passengers were not cleared");
			helper.assertTrue(carrierList < 0 || cleared < carrierList, "the player is released only after the carrier's passengers were sent: "
				+ cleared + " " + carrierList);
			client[0].leaveWorld(helper);
			helper.succeed();
		});
	}

	// ---- seats on the ground ----

	@GameTest(maxTicks = 100)
	public void aPigOnASeatOnTheGroundRidesTheSeatEntityWhereAClientPutsIt(GameTestHelper helper) {
		floor(helper);
		BlockPos seatPos = new BlockPos(3, 1, 3);
		helper.setBlock(seatPos, seat().defaultBlockState());
		Pig pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, seatPos.above());
		Client[] client = { null };
		SeatEntity[] seatEntity = { null };
		helper.runAfterDelay(1, () -> {
			SeatBlock.sitDown(helper.getLevel(), helper.absolutePos(seatPos), pig);
			client[0] = new Client(helper, helper.absoluteVec(new Vec3(3.5, 2, 6)));
		});
		helper.onEachTick(() -> {
			if (client[0] == null)
				return;
			client[0].drain();
			if (!(pig.getVehicle() instanceof SeatEntity seatEntityNow))
				return;
			seatEntity[0] = seatEntityNow;
			if (client[0].vehicleOf(pig) != seatEntityNow.getId())
				return;
			pig.getVehicle().positionRider(pig);
			assertSame(helper, pig.position(), clientRiderPosition(helper, seatEntityNow.position(), pig), "client position of the pig");
			Vec3 floorCentre = Vec3.atBottomCenterOf(helper.absolutePos(seatPos));
			helper.assertTrue(Math.abs(pig.getX() - floorCentre.x) < EPSILON && Math.abs(pig.getZ() - floorCentre.z) < EPSILON,
				"the pig is off the seat: " + pig.position());
			helper.assertTrue(pig.getY() > floorCentre.y && pig.getY() < floorCentre.y + .5, "pig height " + (pig.getY() - floorCentre.y));
			client[0].leaveWorld(helper);
			helper.succeed();
		});
	}
}
