package dev.createpolymer.contraptions.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.contraptions.AbstractContraptionEntity;
import dev.createpolymer.contraptions.AbstractContraptionEntity.ContraptionRotationState;
import dev.createpolymer.contraptions.Contraption;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.VirtualEntityUtils;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.mixin.EntityPassengersSetS2CPacketAccessor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.Vec3;

/**
 * What vanilla clients see of the entities sitting on the seats of a contraption.
 * <p>
 * On the server those entities are passengers of the contraption entity, which clients know as an invisible item
 * display (the carrier) at the contraption's anchor: a client would draw them, and a riding player's camera, at the
 * anchor. So every seated entity gets a <i>seat proxy</i>, an invisible item display of its own that follows the
 * rider's seat, and clients are told that the entity rides that proxy and that the carrier has no such passenger:
 * <ul>
 * <li>{@link #hideSeatedPassengers} rewrites the carrier's {@code ClientboundSetPassengersPacket}s (it is called from
 * {@code AbstractContraptionEntity.onEntityPacketSent}, which sees the ones sent by the entity tracker, the pairing
 * bundle and the virtual-passenger updates), so they only list the block displays and other non-seated passengers.</li>
 * <li>{@link #update} creates and removes proxies, and moves them to where Create puts the rider, every tick. A client
 * puts a passenger at {@code vehicle + passenger point of the vehicle - vehicle point of the passenger}; the display has
 * its passenger point at its feet, so the proxy stands at the rider's feet plus the rider's vehicle point. The rider's
 * eyes, and so a riding player's camera, end up where Create puts them.</li>
 * <li>A proxy is moved like the carrier (same teleport duration, so a client glides both the same way) towards the
 * seat's position for the rotation {@link #LEAD_TICKS} ahead, which cancels the lag of that glide just as the
 * block displays' predicted rotation does.</li>
 * </ul>
 * Some packets that tell a client about the carrier's passengers do not pass the carrier's
 * {@code onEntityPacketSent}: the one a player's own connection gets when it mounts or dismounts any vehicle, the
 * one Polymer sends straight to a connection that starts to track the carrier, and the one a seated entity sends
 * when a client starts to track it, which describes its vehicle. Those still list the rider on the carrier, so the
 * proxy's passengers are sent again after them: right away when a client starts to track a rider (a Polymer holder
 * attached to the rider tells when), on the next tick when a client starts to track the carrier or a player mounts
 * or dismounts, and every {@link #REFRESH_TICKS} ticks anyway.
 */
public class ContraptionSeats {
	/** Matches the carrier's teleport duration in {@link AbstractContraptionEntity#modifyRawTrackedData}. */
	public static final int TELEPORT_DURATION = 3;
	/** A client that glides towards each new position over d ticks shows the position sent d - 1 ticks ago. */
	public static final int LEAD_TICKS = TELEPORT_DURATION - 1;
	public static final int REFRESH_TICKS = 20;

	/** The proxy of one seated entity. */
	public final class SeatProxy {
		private final Entity rider;
		private final ItemDisplayElement element = new ItemDisplayElement();
		private final RiderWatch watch = new RiderWatch(this);

		private SeatProxy(Entity rider) {
			this.rider = rider;
			element.setTeleportDuration(TELEPORT_DURATION);
			element.setAlwaysSyncAbsolutePosition(true);
		}

		public Entity rider() {
			return rider;
		}

		/** The id clients know the proxy by. */
		public int entityId() {
			return element.getEntityId();
		}

		/** Where the proxy was last put (world coordinates). */
		public Vec3 position() {
			return element.getCurrentPos();
		}

		/** The packet that makes the rider the proxy's only passenger. */
		public ClientboundSetPassengersPacket passengersPacket() {
			return VirtualEntityUtils.createClientboundSetPassengersPacket(entityId(), new int[] { rider.getId() });
		}

		/** Whether a client knows the rider (and so can be told to seat it). */
		private boolean isKnownTo(ServerGamePacketListenerImpl connection) {
			return connection.getPlayer() == rider || watch.getWatchingPlayers().contains(connection);
		}
	}

	/**
	 * Attached to the rider, so Polymer tells when a client starts to track it: that client then received a packet
	 * that puts the rider on the carrier, which the proxy's passengers undo.
	 */
	private final class RiderWatch extends ElementHolder {
		private final SeatProxy proxy;

		private RiderWatch(SeatProxy proxy) {
			this.proxy = proxy;
		}

		@Override
		protected void startWatchingExtraPackets(ServerGamePacketListenerImpl player,
			Consumer<Packet<ClientGamePacketListener>> packetConsumer) {
			if (holder.getWatchingPlayers().contains(player))
				packetConsumer.accept(proxy.passengersPacket());
		}
	}

	private final ElementHolder holder;
	private final AbstractContraptionEntity entity;
	private final Map<UUID, SeatProxy> proxies = new LinkedHashMap<>();
	private final Set<ServerGamePacketListenerImpl> pendingConnections = Collections.newSetFromMap(new IdentityHashMap<>());
	private boolean pendingAll;
	private long ticks;
	/** What a client has as the vehicle: the way it positions the riders is read from this entity. */
	private Entity vehicleProbe;

	public ContraptionSeats(ElementHolder holder, AbstractContraptionEntity entity) {
		this.holder = holder;
		this.entity = entity;
	}

	public List<SeatProxy> getProxies() {
		return List.copyOf(proxies.values());
	}

	@Nullable
	public SeatProxy getProxy(Entity rider) {
		return proxies.get(rider.getUUID());
	}

	// ---- passengers ----

	/** The passengers of the contraption that sit on one of its seats, in passenger order. */
	public static List<Entity> seatedPassengers(AbstractContraptionEntity entity) {
		Contraption contraption = entity.getContraption();
		if (contraption == null)
			return List.of();
		List<Entity> seated = new ArrayList<>(2);
		for (Entity passenger : entity.getPassengers())
			if (contraption.getSeatOf(passenger.getUUID()) != null)
				seated.add(passenger);
		return seated;
	}

	/**
	 * Takes the entities that sit on a seat out of a passenger list of the contraption entity that is sent to clients:
	 * they ride the seat proxies there.
	 *
	 * @return whether the packet was changed
	 */
	public static boolean hideSeatedPassengers(AbstractContraptionEntity entity, ClientboundSetPassengersPacket packet) {
		if (packet.getVehicle() != entity.getId())
			return false;
		List<Entity> seated = seatedPassengers(entity);
		if (seated.isEmpty())
			return false;
		IntSet hidden = new IntOpenHashSet(seated.size());
		for (Entity passenger : seated)
			hidden.add(passenger.getId());
		int[] passengers = packet.getPassengers();
		int[] visible = withoutIds(passengers, hidden);
		if (visible.length == passengers.length)
			return false;
		((EntityPassengersSetS2CPacketAccessor) packet).setPassengers(visible);
		return true;
	}

	public static int[] withoutIds(int[] ids, IntSet removed) {
		IntArrayList kept = new IntArrayList(ids.length);
		for (int id : ids)
			if (!removed.contains(id))
				kept.add(id);
		return kept.toIntArray();
	}

	// ---- proxies ----

	/**
	 * Brings the proxies in line with the seated passengers and puts them where the riders are. Called every tick
	 * (by the visual); {@link #refresh} does the same when a passenger boards or leaves, so clients do not wait for the
	 * next tick.
	 */
	public void update() {
		ticks++;
		refresh();
	}

	/** Like {@link #update}, for a change that is not a tick. */
	public void refresh() {
		Contraption contraption = entity.getContraption();
		if (contraption == null || entity.isRemoved())
			return;

		List<Entity> riders = seatedPassengers(entity);
		boolean changed = false;
		for (var iterator = proxies.values().iterator(); iterator.hasNext();) {
			SeatProxy proxy = iterator.next();
			if (riders.contains(proxy.rider) && proxy.rider.isAlive())
				continue;
			iterator.remove();
			release(proxy);
			changed = true;
		}

		if (!riders.isEmpty()) {
			ContraptionRotationState state = entity.getPredictedRotationState(LEAD_TICKS);
			for (Entity rider : riders) {
				Vec3 target = proxyPosition(rider, state);
				if (target == null)
					continue;
				SeatProxy proxy = proxies.get(rider.getUUID());
				if (proxy != null) {
					proxy.element.setOverridePos(target);
					continue;
				}
				proxy = new SeatProxy(rider);
				proxy.element.setOverridePos(target);
				proxies.put(rider.getUUID(), proxy);
				holder.addElement(proxy.element);
				EntityAttachment.of(proxy.watch, rider);
				changed = true;
			}
		}

		boolean refresh = pendingAll || changed || ticks % REFRESH_TICKS == 0;
		if (!proxies.isEmpty() && (refresh || !pendingConnections.isEmpty()))
			sendPassengers(refresh);
		pendingAll = false;
		pendingConnections.clear();
	}

	private void sendPassengers(boolean all) {
		for (SeatProxy proxy : proxies.values()) {
			Packet<?> packet = null;
			for (ServerGamePacketListenerImpl connection : holder.getWatchingPlayers()) {
				if ((!all && !pendingConnections.contains(connection)) || !proxy.isKnownTo(connection))
					continue;
				if (packet == null)
					packet = proxy.passengersPacket();
				connection.send(packet);
			}
		}
	}

	/** Takes a proxy down: its rider is released on every client before the proxy goes. */
	private void release(SeatProxy proxy) {
		holder.sendPacket(VirtualEntityUtils.createClientboundSetPassengersPacket(proxy.entityId(), new int[0]));
		holder.removeElement(proxy.element);
		proxy.watch.destroy();
	}

	/**
	 * Where the proxy of a rider has to stand with the contraption turned as {@code state}: where a client puts the
	 * rider's feet is the proxy's position plus the proxy's passenger point minus the rider's vehicle point, and that
	 * is where Create puts the rider.
	 *
	 * @return null if the entity does not sit on a seat
	 */
	@Nullable
	public Vec3 proxyPosition(Entity rider, ContraptionRotationState state) {
		Vec3 feet = entity.getSeatedPosition(rider, state);
		if (feet == null)
			return null;
		Entity probe = vehicleProbe();
		if (probe == null)
			return feet;
		Vec3 passengerPoint = probe.getPassengerRidingPosition(rider).subtract(probe.position());
		return feet.add(rider.getVehicleAttachmentPoint(probe)).subtract(passengerPoint);
	}

	private Entity vehicleProbe() {
		if (vehicleProbe == null)
			vehicleProbe = EntityTypes.ITEM_DISPLAY.create(entity.level(), EntitySpawnReason.LOAD);
		return vehicleProbe;
	}

	// ---- events ----

	/** A client starts to track the contraption: the packets it is sent for that put the riders on the carrier. */
	public void watcherAdded(ServerGamePacketListenerImpl connection) {
		pendingConnections.add(connection);
	}

	/** Some vehicle's passengers were sent to a player directly (mounting, dismounting): send the proxies' again. */
	public void requestResync() {
		pendingAll = true;
	}

	/** Takes every proxy down, as the contraption goes. */
	public void clear() {
		for (SeatProxy proxy : proxies.values())
			proxy.watch.destroy();
		proxies.clear();
	}
}
