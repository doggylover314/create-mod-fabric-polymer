package dev.createpolymer.contraptions.minecart;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.contraptions.minecart.capability.CapabilityMinecartController;
import dev.createpolymer.contraptions.minecart.capability.MinecartController;
import dev.createpolymer.registry.feature.MinecartRegistry;
import dev.createpolymer.util.Couple;
import dev.createpolymer.util.Iterate;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class CouplingHandler {

	/**
	 * Create cancels EntityMountEvent for anything but contraptions boarding a cart that is coupled through a
	 * contraption. Players are stopped here, from the interaction callback; other entities are put off the cart by
	 * its controller.
	 */
	public static boolean mayMount(Entity cart, Entity mounting) {
		MinecartController controller = MinecartController.get(cart);
		if (controller != MinecartController.EMPTY) {
			if (mounting instanceof dev.createpolymer.contraptions.AbstractContraptionEntity)
				return true;
			return !controller.isCoupledThroughContraption();
		}
		return true;
	}

	public static void forEachLoadedCoupling(Level world, Consumer<Couple<MinecartController>> consumer) {
		if (world == null)
			return;
		Set<UUID> cartsWithCoupling = CapabilityMinecartController.loadedMinecartsWithCoupling.get(world);
		if (cartsWithCoupling == null)
			return;

		for (UUID id : new java.util.ArrayList<>(cartsWithCoupling)) {
			MinecartController controller = CapabilityMinecartController.getIfPresent(world, id);
			if (controller == null)
				continue;
			if (!controller.isLeadingCoupling())
				continue;
			UUID coupledCart = controller.getCoupledCart(true);
			MinecartController coupledController = CapabilityMinecartController.getIfPresent(world, coupledCart);
			if (coupledController == null)
				continue;
			if (!controller.isPresent() || !coupledController.isPresent())
				continue;
			consumer.accept(Couple.create(controller, coupledController));
		}
	}

	public static boolean tryToCoupleCarts(@Nullable Player player, Level world, int cartId1, int cartId2) {
		Entity entity1 = world.getEntity(cartId1);
		Entity entity2 = world.getEntity(cartId2);

		if (!(entity1 instanceof AbstractMinecart cart1))
			return false;
		if (!(entity2 instanceof AbstractMinecart cart2))
			return false;

		String tooMany = "two_couplings_max";
		String unloaded = "unloaded";
		String noLoops = "no_loops";
		String tooFar = "too_far";

		int distanceTo = (int) entity1.position()
			.distanceTo(entity2.position());
		boolean contraptionCoupling = player == null;

		if (distanceTo < 2) {
			if (contraptionCoupling)
				return false; // dont allow train contraptions with <2 distance
			distanceTo = 2;
		}

		if (distanceTo > MinecartRegistry.maxCartCouplingLength) {
			status(player, tooFar);
			return false;
		}

		UUID mainID = cart1.getUUID();
		UUID connectedID = cart2.getUUID();
		MinecartController mainController = CapabilityMinecartController.getIfPresent(world, mainID);
		MinecartController connectedController = CapabilityMinecartController.getIfPresent(world, connectedID);

		if (mainController == null || connectedController == null) {
			status(player, unloaded);
			return false;
		}
		if (mainController.isFullyCoupled() || connectedController.isFullyCoupled()) {
			status(player, tooMany);
			return false;
		}

		if (mainController.isLeadingCoupling() && mainController.getCoupledCart(true)
			.equals(connectedID) || connectedController.isLeadingCoupling()
				&& connectedController.getCoupledCart(true)
					.equals(mainID))
			return false;

		for (boolean main : Iterate.trueAndFalse) {
			MinecartController current = main ? mainController : connectedController;
			boolean forward = current.isLeadingCoupling();
			int safetyCount = 1000;

			while (true) {
				if (safetyCount-- <= 0) {
					CreatePolymer.LOGGER.warn("Infinite loop in coupling iteration");
					return false;
				}

				current = getNextInCouplingChain(world, current, forward);
				if (current == null) {
					status(player, unloaded);
					return false;
				}
				if (current == connectedController) {
					status(player, noLoops);
					return false;
				}
				if (current == MinecartController.EMPTY)
					break;
			}
		}

		if (!contraptionCoupling) {
			for (InteractionHand hand : InteractionHand.values()) {
				if (player.isCreative())
					break;
				ItemStack heldItem = player.getItemInHand(hand);
				if (!heldItem.is(MinecartRegistry.MINECART_COUPLING))
					continue;
				heldItem.shrink(1);
				break;
			}
		}

		mainController.prepareForCoupling(true);
		connectedController.prepareForCoupling(false);

		mainController.coupleWith(true, connectedID, distanceTo, contraptionCoupling);
		connectedController.coupleWith(false, mainID, distanceTo, contraptionCoupling);
		return true;
	}

	@Nullable
	/**
	 * MinecartController.EMPTY if none connected, null if not yet loaded
	 */
	public static MinecartController getNextInCouplingChain(Level world, MinecartController controller,
		boolean forward) {
		UUID coupledCart = controller.getCoupledCart(forward);
		if (coupledCart == null)
			return MinecartController.EMPTY;
		return CapabilityMinecartController.getIfPresent(world, coupledCart);
	}

	public static void status(Player player, String key) {
		if (player == null)
			return;
		player.sendOverlayMessage(Component.translatable("create.minecart_coupling." + key));
	}

}
