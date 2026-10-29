package dev.createpolymer.contraptions.minecart.capability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.contraptions.minecart.CouplingHandler;
import dev.createpolymer.contraptions.minecart.CouplingPhysics;
import dev.createpolymer.contraptions.minecart.MinecartPassingBlock;
import dev.createpolymer.registry.feature.MinecartRegistry;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.WorldAttached;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Create's CapabilityMinecartController: keeps the controllers of all loaded minecarts, hooked up through Fabric's
 * lifecycle events instead of NeoForge's entity events.
 */
public class CapabilityMinecartController {

	/* Global map of loaded carts */

	public static WorldAttached<Map<UUID, MinecartController>> loadedMinecartsByUUID;
	public static WorldAttached<Set<UUID>> loadedMinecartsWithCoupling;
	static WorldAttached<List<AbstractMinecart>> queuedAdditions;
	static WorldAttached<List<UUID>> queuedUnloads;

	static {
		loadedMinecartsByUUID = new WorldAttached<>($ -> new HashMap<>());
		loadedMinecartsWithCoupling = new WorldAttached<>($ -> new HashSet<>());
		queuedAdditions = new WorldAttached<>($ -> ObjectLists.synchronize(new ObjectArrayList<>()));
		queuedUnloads = new WorldAttached<>($ -> ObjectLists.synchronize(new ObjectArrayList<>()));
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register(CapabilityMinecartController::attach);
		ServerEntityEvents.ENTITY_UNLOAD.register(CapabilityMinecartController::onEntityLeave);
		// Create's EntityTickEvent.Pre for carts
		ServerTickEvents.START_LEVEL_TICK.register(CapabilityMinecartController::entityTick);
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			tick(level);
			CouplingPhysics.tick(level);
		});
	}

	public static void tick(Level world) {
		Map<UUID, MinecartController> carts = loadedMinecartsByUUID.get(world);
		List<AbstractMinecart> queued = queuedAdditions.get(world);
		List<UUID> queuedRemovals = queuedUnloads.get(world);
		Set<UUID> cartsWithCoupling = loadedMinecartsWithCoupling.get(world);
		Set<UUID> keySet = carts.keySet();

		for (UUID removal : queuedRemovals) {
			keySet.remove(removal);
			cartsWithCoupling.remove(removal);
		}

		for (AbstractMinecart cart : queued) {
			UUID uniqueID = cart.getUUID();

			cartsWithCoupling.remove(uniqueID);

			MinecartController controller = MinecartController.get(cart);
			if (controller != MinecartController.EMPTY) {
				carts.put(uniqueID, controller);
				if (controller.isLeadingCoupling())
					cartsWithCoupling.add(uniqueID);
			}
		}

		queuedRemovals.clear();
		queued.clear();

		List<UUID> toRemove = new ArrayList<>();

		for (Entry<UUID, MinecartController> entry : carts.entrySet()) {
			MinecartController controller = entry.getValue();
			if (controller != null && controller.isPresent())
				continue;
			toRemove.add(entry.getKey());
		}

		for (UUID uuid : toRemove) {
			keySet.remove(uuid);
			cartsWithCoupling.remove(uuid);
		}
	}

	/**
	 * Runs before the level's entities tick: the controller of each cart holds stalled carts in place, and the rail
	 * under the cart is told that the cart passes (NeoForge's {@code onMinecartPass}, which vanilla does not have).
	 */
	public static void entityTick(ServerLevel level) {
		Map<UUID, MinecartController> carts = loadedMinecartsByUUID.get(level);
		if (carts.isEmpty())
			return;
		for (MinecartController controller : new ArrayList<>(carts.values())) {
			if (controller == null || !controller.isPresent())
				continue;
			AbstractMinecart cart = controller.cart();
			if (cart.level() != level)
				continue;
			controller.tick();
			passRail(level, cart);
		}
	}

	private static void passRail(ServerLevel level, AbstractMinecart cart) {
		BlockPos railPos = cart.getCurrentBlockPosOrRailBelow();
		BlockState state = level.getBlockState(railPos);
		if (state.getBlock() instanceof MinecartPassingBlock rail && state.is(BlockTags.RAILS))
			rail.onMinecartPass(state, level, railPos, cart);
	}

	protected static void onCartRemoved(Level world, AbstractMinecart entity) {
		Map<UUID, MinecartController> carts = loadedMinecartsByUUID.get(world);
		List<UUID> unloads = queuedUnloads.get(world);
		UUID uniqueID = entity.getUUID();
		if (!carts.containsKey(uniqueID) || unloads.contains(uniqueID))
			return;
		if (world.isClientSide())
			return;
		// Create's attachment is removed with the cart; forgetting the controller makes this run once per cart
		MinecartController controller = carts.remove(uniqueID);
		loadedMinecartsWithCoupling.get(world)
			.remove(uniqueID);
		handleKilledMinecart(world, controller, entity.position());
	}

	protected static void handleKilledMinecart(Level world, MinecartController controller, Vec3 removedPos) {
		if (controller == null)
			return;
		for (boolean forward : Iterate.trueAndFalse) {
			MinecartController next = CouplingHandler.getNextInCouplingChain(world, controller, forward);
			if (next == null || next == MinecartController.EMPTY)
				continue;

			next.removeConnection(!forward);
			if (controller.hasContraptionCoupling(forward))
				continue;
			AbstractMinecart cart = next.cart();
			if (cart == null)
				continue;

			Vec3 itemPos = cart.position()
				.add(removedPos)
				.scale(.5f);
			ItemEntity itemEntity = new ItemEntity(world, itemPos.x, itemPos.y, itemPos.z,
				new ItemStack(MinecartRegistry.MINECART_COUPLING));
			itemEntity.setDefaultPickUpDelay();
			world.addFreshEntity(itemEntity);
		}
	}

	@Nullable
	public static MinecartController getIfPresent(Level world, UUID cartId) {
		Map<UUID, MinecartController> carts = loadedMinecartsByUUID.get(world);
		if (carts == null)
			return null;
		if (!carts.containsKey(cartId))
			return null;
		return carts.get(cartId);
	}

	/* Attachment management */

	/**
	 * Create attaches a controller to carts that join a level for the first time and reads it back for carts loaded
	 * from disk. Here every cart gets one when it first loads (carts from before this mod could not be coupled in
	 * Create), and a controller read from a save is bound to its cart.
	 */
	public static void attach(Entity entity, ServerLevel level) {
		if (!(entity instanceof AbstractMinecart abstractMinecart))
			return;

		MinecartController controller = abstractMinecart.getAttached(MinecartRegistry.MINECART_CONTROLLER);
		if (controller == null) {
			controller = new MinecartController(abstractMinecart);
			abstractMinecart.setAttached(MinecartRegistry.MINECART_CONTROLLER, controller);
		} else
			controller.bind(abstractMinecart);
		queuedAdditions.get(level)
			.add(abstractMinecart);
	}

	/** A cart leaves the level: chunk unloads keep couplings, deaths dissolve them. */
	public static void onEntityLeave(Entity entity, ServerLevel level) {
		if (!(entity instanceof AbstractMinecart abstractMinecart))
			return;
		Entity.RemovalReason reason = abstractMinecart.getRemovalReason();
		if (reason != null && reason.shouldDestroy()) {
			onCartRemoved(level, abstractMinecart);
			return;
		}
		queuedUnloads.get(level)
			.add(abstractMinecart.getUUID());
	}
}
