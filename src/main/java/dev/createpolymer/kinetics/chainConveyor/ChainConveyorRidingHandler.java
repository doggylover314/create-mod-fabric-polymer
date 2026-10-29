package dev.createpolymer.kinetics.chainConveyor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.kinetics.chainConveyor.ChainConveyorBlockEntity.ConnectionStats;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;

/**
 * Create's ChainConveyorRidingHandler. Create moves the riding player on the client, which tells the server it is
 * hanging (see {@link ServerChainConveyorHandler}). Here the server moves the player: they sit on an invisible armor
 * stand that follows the chain, and sneaking gets them off like any other vehicle. The ride ends when the player
 * lets go of the wrench, the chain is gone or the stand is.
 */
public class ChainConveyorRidingHandler {

	/** Tag of the seats, to remove the ones a crash left behind. */
	public static final String SEAT_TAG = "create_chain_seat";

	private static class Ride {
		final ServerPlayer player;
		final ArmorStand seat;
		BlockPos ridingChainConveyor;
		float chainPosition;
		@Nullable
		BlockPos ridingConnection;
		boolean flipped;

		Ride(ServerPlayer player, ArmorStand seat) {
			this.player = player;
			this.seat = seat;
		}
	}

	private static final Map<UUID, Ride> rides = new HashMap<>();
	private static final Set<UUID> seats = new HashSet<>();

	public static boolean isRiding(ServerPlayer player) {
		return rides.containsKey(player.getUUID());
	}

	/** The position on the chain the player rides on, if they ride. */
	@Nullable
	public static BlockPos getRidingChainConveyor(ServerPlayer player) {
		Ride ride = rides.get(player.getUUID());
		return ride == null ? null : ride.ridingChainConveyor;
	}

	/** Seats of the rides that are going on: other seats with the tag are strays. */
	public static boolean isSeat(ArmorStand stand) {
		return seats.contains(stand.getUUID());
	}

	public static void embark(ServerPlayer player, BlockPos lift, float position, @Nullable BlockPos connection) {
		if (!(player.level() instanceof ServerLevel level))
			return;
		if (!(level.getBlockEntity(lift) instanceof ChainConveyorBlockEntity clbe))
			return;
		if (rides.containsKey(player.getUUID()))
			stopRiding(player);

		ArmorStand seat = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.TRIGGERED);
		if (seat == null)
			return;
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("Marker", true);
		tag.putBoolean("Invisible", true);
		tag.putBoolean("NoGravity", true);
		tag.putBoolean("Invulnerable", true);
		seat.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
		seat.addTag(SEAT_TAG);
		seat.setPos(player.position());
		seats.add(seat.getUUID());
		level.addFreshEntity(seat);
		if (!player.startRiding(seat, true, false)) {
			seats.remove(seat.getUUID());
			seat.discard();
			return;
		}

		Ride ride = new Ride(player, seat);
		ride.ridingChainConveyor = lift;
		ride.chainPosition = position;
		ride.ridingConnection = connection;
		ride.flipped = clbe.getSpeed() < 0;
		rides.put(player.getUUID(), ride);

		player.sendOverlayMessage(Component.translatable("mount.onboard", Component.keybind("key.sneak")));
		level.playSound(null, player.blockPosition(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 1f, 0.5f);
		ServerChainConveyorHandler.handleTTLPacket(player);
		moveSeat(ride, clbe);
	}

	public static void stopRiding(ServerPlayer player) {
		Ride ride = rides.remove(player.getUUID());
		if (ride == null)
			return;
		end(ride);
	}

	private static void end(Ride ride) {
		ServerChainConveyorHandler.handleStopRidingPacket(ride.player);
		if (ride.player.getVehicle() == ride.seat)
			ride.player.stopRiding();
		seats.remove(ride.seat.getUUID());
		ride.seat.discard();
		ride.player.level()
			.playSound(null, ride.player.blockPosition(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.75f, 0.35f);
	}

	public static void tick() {
		for (Iterator<Ride> iterator = rides.values()
			.iterator(); iterator.hasNext(); ) {
			Ride ride = iterator.next();
			if (!tick(ride)) {
				iterator.remove();
				end(ride);
			}
		}
	}

	/** Create's clientTick. @return false if the ride is over */
	private static boolean tick(Ride ride) {
		ServerPlayer player = ride.player;
		if (player.isRemoved() || ride.seat.isRemoved() || player.getVehicle() != ride.seat)
			return false;
		if (player.level() != ride.seat.level())
			return false;
		if (!player.isHolding(stack -> stack.is(ModItems.WRENCH)))
			return false;
		BlockEntity blockEntity = player.level()
			.getBlockEntity(ride.ridingChainConveyor);
		if (player.isShiftKeyDown() || !(blockEntity instanceof ChainConveyorBlockEntity clbe))
			return false;
		if (ride.ridingConnection != null && !clbe.connections.contains(ride.ridingConnection))
			return false;

		clbe.prepareStats();

		updateTargetPosition(ride, clbe);

		blockEntity = player.level()
			.getBlockEntity(ride.ridingChainConveyor);
		if (!(blockEntity instanceof ChainConveyorBlockEntity))
			return true;

		clbe = (ChainConveyorBlockEntity) blockEntity;
		clbe.prepareStats();
		if (ride.ridingConnection != null && !clbe.connections.contains(ride.ridingConnection))
			return false;

		moveSeat(ride, clbe);
		ServerChainConveyorHandler.handleTTLPacket(player);
		return true;
	}

	/** Puts the seat where the player's head has to be: half a block under the chain. */
	private static void moveSeat(Ride ride, ChainConveyorBlockEntity clbe) {
		ServerPlayer player = ride.player;
		Vec3 targetPosition;

		if (ride.ridingConnection != null) {
			ConnectionStats stats = clbe.connectionStats.get(ride.ridingConnection);
			targetPosition = stats.start()
				.add((stats.end()
					.subtract(stats.start())).normalize()
					.scale(Math.min(stats.chainLength(), ride.chainPosition)));
		} else {
			targetPosition = Vec3.atBottomCenterOf(ride.ridingChainConveyor)
				.add(VecHelper.rotate(new Vec3(0, 0.25, 1), ride.chainPosition, Axis.Y));
		}

		float chainYOffset = 0.5f * player.getScale();
		Vec3 playerPosition = targetPosition.subtract(0, player.getBoundingBox()
			.getYsize() + chainYOffset, 0);
		Vec3 seatOffset = player.position()
			.subtract(ride.seat.position());
		Vec3 seatPosition = playerPosition.subtract(seatOffset);
		ride.seat.setPos(seatPosition);
		player.setDeltaMovement(Vec3.ZERO);
	}

	private static void updateTargetPosition(Ride ride, ChainConveyorBlockEntity clbe) {
		float serverSpeed = 1f;
		float speed = clbe.getSpeed() / 360f;
		float radius = 1.5f;
		float distancePerTick = Math.abs(speed);
		float degreesPerTick = (speed / (Mth.PI * radius)) * 360f;

		if (ride.ridingConnection != null) {
			ConnectionStats stats = clbe.connectionStats.get(ride.ridingConnection);

			if (ride.flipped != clbe.getSpeed() < 0) {
				ride.flipped = clbe.getSpeed() < 0;
				ride.ridingChainConveyor = clbe.getBlockPos()
					.offset(ride.ridingConnection);
				ride.chainPosition = stats.chainLength() - ride.chainPosition;
				ride.ridingConnection = ride.ridingConnection.multiply(-1);
				return;
			}

			ride.chainPosition += serverSpeed * distancePerTick;
			ride.chainPosition = Math.min(stats.chainLength(), ride.chainPosition);
			if (ride.chainPosition < stats.chainLength())
				return;

			// transfer to other
			if (clbe.getLevel()
				.getBlockEntity(clbe.getBlockPos()
					.offset(ride.ridingConnection)) instanceof ChainConveyorBlockEntity clbe2) {
				ride.chainPosition = clbe.wrapAngle(stats.tangentAngle() + 180 + 2 * 35 * (clbe.reversed ? -1 : 1));
				ride.ridingChainConveyor = clbe2.getBlockPos();
				ride.ridingConnection = null;
			}

			return;
		}

		float prevChainPosition = ride.chainPosition;
		ride.chainPosition += serverSpeed * degreesPerTick;
		ride.chainPosition = clbe.wrapAngle(ride.chainPosition);

		BlockPos nearestLooking = BlockPos.ZERO;
		double bestDiff = Double.MAX_VALUE;
		for (BlockPos connection : clbe.connections) {
			double diff = Vec3.atLowerCornerOf(connection)
				.normalize()
				.distanceToSqr(ride.player.getLookAngle()
					.normalize());
			if (diff > bestDiff)
				continue;
			nearestLooking = connection;
			bestDiff = diff;
		}

		if (nearestLooking == BlockPos.ZERO)
			return;

		float offBranchAngle = clbe.connectionStats.get(nearestLooking)
			.tangentAngle();
		if (!clbe.loopThresholdCrossed(ride.chainPosition, prevChainPosition, offBranchAngle))
			return;

		ride.chainPosition = 0;
		ride.ridingConnection = nearestLooking;
	}

}
