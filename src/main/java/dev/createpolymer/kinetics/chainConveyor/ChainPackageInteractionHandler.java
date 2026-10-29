package dev.createpolymer.kinetics.chainConveyor;

import java.util.List;
import java.util.Map.Entry;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.registry.feature.ChainConveyorRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Create's ChainPackageInteractionHandler and ChainPackageInteractionPacket: taking packages off a chain by clicking
 * them, and putting the package in the player's hand on a chain.
 */
public class ChainPackageInteractionHandler {

	/** Create's onUse: takes the package the player is looking at off its chain. */
	public static InteractionResult onUse(ServerPlayer player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND)
			return InteractionResult.PASS;

		double range = player.blockInteractionRange() + 1;
		Vec3 from = player.getEyePosition();
		Vec3 to = from.add(player.getViewVector(1)
			.scale(range));

		double maxDistance = range + ChainConveyorRegistry.maxChainConveyorLength + 2;
		double best = Double.MAX_VALUE;
		ChainConveyorBlockEntity bestBe = null;
		BlockPos bestConnection = null;
		ChainConveyorPackage bestPackage = null;

		for (BlockPos liftPos : ChainConveyorInteractionHandler.loadedChains.get(player.level())
			.keySet()) {
			if (liftPos.distToCenterSqr(from) > maxDistance * maxDistance)
				continue;
			if (!(player.level().getBlockEntity(liftPos) instanceof ChainConveyorBlockEntity ccbe))
				continue;

			for (ChainConveyorPackage pckg : ccbe.getLoopingPackages()) {
				double distance = distanceTo(pckg, from, to);
				if (distance >= best)
					continue;
				best = distance;
				bestBe = ccbe;
				bestConnection = null;
				bestPackage = pckg;
			}

			for (Entry<BlockPos, List<ChainConveyorPackage>> entry : ccbe.getTravellingPackages()
				.entrySet()) {
				for (ChainConveyorPackage pckg : entry.getValue()) {
					double distance = distanceTo(pckg, from, to);
					if (distance >= best)
						continue;
					best = distance;
					bestBe = ccbe;
					bestConnection = entry.getKey();
					bestPackage = pckg;
				}
			}
		}

		if (bestPackage == null)
			return InteractionResult.PASS;
		take(player, bestBe, bestConnection, bestPackage);
		return InteractionResult.SUCCESS;
	}

	/** @return the squared distance at which the line crosses the package, or MAX_VALUE if it does not */
	private static double distanceTo(ChainConveyorPackage pckg, Vec3 from, Vec3 to) {
		if (pckg.worldPosition == null)
			return Double.MAX_VALUE;
		ChainConveyorPackage.ChainConveyorPackagePhysicsData physicsData = pckg.existingPhysicsData();
		Vec3 targetPos = physicsData != null && physicsData.targetPos != null ? physicsData.targetPos
			: pckg.worldPosition.subtract(0, 9 / 16f, 0);
		AABB bounds = new AABB(targetPos, targetPos).move(0, -.25, 0)
			.expandTowards(0, 0.5, 0)
			.inflate(0.45);
		return bounds.clip(from, to)
			.map(hit -> hit.distanceToSqr(from))
			.orElse(Double.MAX_VALUE);
	}

	/** Create's ChainPackageInteractionPacket with removingPackage set: the player takes the package off the chain. */
	public static void take(Player player, ChainConveyorBlockEntity be, @Nullable BlockPos selectedConnection,
		ChainConveyorPackage best) {
		List<ChainConveyorPackage> list = selectedConnection == null ? be.loopingPackages
			: be.travellingPackages.get(selectedConnection);

		if (list == null || list.isEmpty() || !list.contains(best))
			return;

		if (player.getMainHandItem()
			.isEmpty()) {
			player.setItemInHand(InteractionHand.MAIN_HAND, best.item.copy());
		} else {
			player.getInventory()
				.placeItemBackInInventory(best.item.copy(), net.minecraft.util.Prediction.SERVER_ONLY);
		}

		list.remove(best);
		be.notifyUpdate();
	}

	/**
	 * Create's ChainPackageInteractionPacket without removingPackage: the package in the player's hand goes on the
	 * chain at the position they pointed at.
	 */
	public static void put(ServerPlayer player, BlockPos lift, @Nullable BlockPos selectedConnection,
		float chainPosition) {
		if (!(player.level().getBlockEntity(lift) instanceof ChainConveyorBlockEntity be))
			return;
		if (selectedConnection == null)
			selectedConnection = BlockPos.ZERO;

		ChainConveyorPackage chainConveyorPackage =
			new ChainConveyorPackage(chainPosition, player.getMainHandItem().copyWithCount(1));
		if (!be.canAcceptPackagesFor(selectedConnection)) {
			return;
		}

		if (!player.isCreative()) {
			player.getMainHandItem().shrink(1);
			if (player.getMainHandItem().isEmpty()) {
				player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			}
		}

		if (selectedConnection.equals(BlockPos.ZERO)) {
			be.addLoopingPackage(chainConveyorPackage);
		} else {
			be.addTravellingPackage(chainConveyorPackage, selectedConnection);
		}
	}

}
