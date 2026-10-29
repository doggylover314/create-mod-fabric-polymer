package dev.createpolymer.contraptions.minecart;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import dev.createpolymer.contraptions.minecart.capability.MinecartController;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.MinecartRegistry;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.VecHelper;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Create picks the two carts to couple on the client (the first click highlights a cart, the second sends a packet).
 * Here the first cart a player clicked is remembered on the server and highlighted with particles; clicking another
 * cart couples the two. The highlight ends when the player puts the coupling away.
 */
public class MinecartCouplingItem extends SimplePolymerItem {

	/** The cart each player selected: player uuid to cart uuid. */
	private static final Map<UUID, UUID> SELECTED = new HashMap<>();
	private static final RandomSource r = RandomSource.create();

	public MinecartCouplingItem(Properties properties) {
		super(properties, Items.TRIAL_KEY, true);
	}

	public static void init() {
		UseEntityCallback.EVENT.register(MinecartCouplingItem::handleInteractionWithMinecart);
		ServerTickEvents.END_SERVER_TICK.register(MinecartCouplingItem::tickSelections);
	}

	public static InteractionResult handleInteractionWithMinecart(Player player, Level level, InteractionHand hand,
		Entity interacted, EntityHitResult hitResult) {
		if (level.isClientSide() || player.isSpectator())
			return InteractionResult.PASS;
		if (!(interacted instanceof AbstractMinecart minecart))
			return InteractionResult.PASS;
		MinecartController controller = MinecartController.get(minecart);
		if (controller == MinecartController.EMPTY || !controller.isPresent())
			return InteractionResult.PASS;

		ItemStack heldItem = player.getItemInHand(hand);
		if (heldItem.is(MinecartRegistry.MINECART_COUPLING)) {
			if (!onCouplingInteractOnMinecart(minecart, player, controller))
				return InteractionResult.PASS;
		} else if (heldItem.is(ModItems.WRENCH)) {
			if (!onWrenchInteractOnMinecart(minecart, player, controller))
				return InteractionResult.PASS;
		} else {
			// Create cancels EntityMountEvent for carts coupled through a contraption
			if (minecart.isRideable() && !CouplingHandler.mayMount(minecart, player))
				return InteractionResult.FAIL;
			return InteractionResult.PASS;
		}

		return InteractionResult.SUCCESS;
	}

	protected static boolean onCouplingInteractOnMinecart(AbstractMinecart minecart, Player player,
		MinecartController controller) {
		if (controller.isFullyCoupled()) {
			CouplingHandler.status(player, "two_couplings_max");
			return true;
		}
		if (player.level() instanceof ServerLevel level)
			cartClicked(player, level, minecart);
		return true;
	}

	private static boolean onWrenchInteractOnMinecart(AbstractMinecart minecart, Player player,
		MinecartController controller) {
		int couplings = (controller.isConnectedToCoupling() ? 1 : 0) + (controller.isLeadingCoupling() ? 1 : 0);
		if (couplings == 0)
			return false;

		for (boolean forward : Iterate.trueAndFalse) {
			if (controller.hasContraptionCoupling(forward))
				couplings--;
		}

		CouplingHandler.status(player, "removed");
		controller.decouple();
		if (!player.isCreative())
			player.getInventory()
				.placeItemBackInInventory(new ItemStack(MinecartRegistry.MINECART_COUPLING, couplings), Prediction.SERVER_ONLY);
		return true;
	}

	/** Create's CouplingHandlerClient.onCartClicked. */
	private static void cartClicked(Player player, ServerLevel level, AbstractMinecart interacted) {
		AbstractMinecart selectedCart = null;
		UUID selected = SELECTED.get(player.getUUID());
		if (selected != null && level.getEntity(selected) instanceof AbstractMinecart cart && cart.isAlive())
			selectedCart = cart;

		if (selectedCart == null || selectedCart == interacted) {
			SELECTED.put(player.getUUID(), interacted.getUUID());
			spawnSelectionParticles(interacted.getBoundingBox(), level, true);
			return;
		}
		spawnSelectionParticles(interacted.getBoundingBox(), level, true);
		SELECTED.remove(player.getUUID());
		CouplingHandler.tryToCoupleCarts(player, level, selectedCart.getId(), interacted.getId());
	}

	/** Create's CouplingHandlerClient.tick: keep the selected cart highlighted while the coupling is held. */
	private static void tickSelections(MinecraftServer server) {
		if (SELECTED.isEmpty())
			return;
		for (Iterator<Map.Entry<UUID, UUID>> iterator = SELECTED.entrySet()
			.iterator(); iterator.hasNext();) {
			Map.Entry<UUID, UUID> entry = iterator.next();
			ServerPlayer player = server.getPlayerList()
				.getPlayer(entry.getKey());
			if (player == null || !(player.level().getEntity(entry.getValue()) instanceof AbstractMinecart cart)
				|| !cart.isAlive() || !(player.getMainHandItem().is(MinecartRegistry.MINECART_COUPLING)
					|| player.getOffhandItem().is(MinecartRegistry.MINECART_COUPLING))) {
				iterator.remove();
				continue;
			}
			spawnSelectionParticles(cart.getBoundingBox(), player.level(), false);
		}
	}

	public static UUID getSelectedCart(Player player) {
		return SELECTED.get(player.getUUID());
	}

	private static void spawnSelectionParticles(AABB aabb, ServerLevel world, boolean highlight) {
		Vec3 center = aabb.getCenter();
		int amount = highlight ? 100 : 2;
		ParticleOptions particleData = highlight ? ParticleTypes.END_ROD : new DustParticleOptions(0xFFFFFF, 1);
		for (int i = 0; i < amount; i++) {
			Vec3 v = VecHelper.offsetRandomly(Vec3.ZERO, r, 1);
			double yOffset = v.y;
			v = v.multiply(1, 0, 1)
				.normalize()
				.add(0, yOffset / 8f, 0)
				.add(center);
			world.sendParticles(particleData, v.x, v.y, v.z, 1, 0, 0, 0, 0);
		}
	}

}
