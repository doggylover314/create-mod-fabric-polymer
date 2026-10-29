package dev.createpolymer.kinetics.chainConveyor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.logistics.box.PackageItem;
import dev.createpolymer.logistics.packagePort.PackagePortTarget;
import dev.createpolymer.logistics.packagePort.PackagePortTargetSelectionHandler;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.ChainConveyorRegistry;
import dev.createpolymer.registry.feature.PackagesRegistry;
import dev.createpolymer.util.Lang;
import dev.createpolymer.util.WorldAttached;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Create's ChainConveyorInteractionHandler. Create finds the chain point a player points at every client tick, with
 * the shapes of the chains, and sends what the player does with it to the server. The server has the shapes too, so
 * it looks for the point when a player uses an item (see {@link #onUse}).
 */
public class ChainConveyorInteractionHandler {

	/** The shapes of the chain conveyors of a level, by the position of the conveyor. */
	public static WorldAttached<Map<BlockPos, List<ChainConveyorShape>>> loadedChains =
		new WorldAttached<>($ -> new HashMap<>());

	/** What a player points at: a point on a chain conveyor or on one of its chains. */
	public record Selection(BlockPos lift, float chainPosition, @Nullable BlockPos connection, Vec3 bakedPosition,
		ChainConveyorShape shape) {
	}

	/** Create's clientTick: the chain point the player is pointing at, nearer than whatever block they point at. */
	@Nullable
	public static Selection select(Player player, boolean dismantling) {
		Level level = player.level();
		double range = player.blockInteractionRange() + 1;

		Vec3 from = player.getEyePosition();
		Vec3 to = from.add(player.getViewVector(1)
			.scale(range));
		HitResult hitResult = level.clip(new ClipContext(from, from.add(player.getViewVector(1)
			.scale(range - 1)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

		double bestDiff = Float.MAX_VALUE;
		if (hitResult.getType() != HitResult.Type.MISS)
			bestDiff = hitResult.getLocation()
				.distanceToSqr(from);

		BlockPos bestLift = null;
		ChainConveyorShape bestShape = null;
		float selectedChainPosition = 0;
		BlockPos selectedConnection = null;

		for (Entry<BlockPos, List<ChainConveyorShape>> entry : loadedChains.get(level)
			.entrySet()) {
			BlockPos liftPos = entry.getKey();
			double maxDistance = range + ChainConveyorRegistry.maxChainConveyorLength + 2;
			if (liftPos.distToCenterSqr(from) > maxDistance * maxDistance)
				continue;
			for (ChainConveyorShape chainConveyorShape : entry.getValue()) {
				if (chainConveyorShape instanceof ChainConveyorShape.ChainConveyorBB && dismantling)
					continue;
				Vec3 liftVec = Vec3.atLowerCornerOf(liftPos);
				Vec3 intersect = chainConveyorShape.intersect(from.subtract(liftVec), to.subtract(liftVec));
				if (intersect == null)
					continue;

				double distanceToSqr = intersect.add(liftVec)
					.distanceToSqr(from);
				if (distanceToSqr > bestDiff)
					continue;
				bestDiff = distanceToSqr;
				bestLift = liftPos;
				bestShape = chainConveyorShape;
				selectedChainPosition = chainConveyorShape.getChainPosition(intersect);
				selectedConnection = null;
				if (chainConveyorShape instanceof ChainConveyorShape.ChainConveyorOBB obb)
					selectedConnection = obb.connection;
			}
		}

		if (bestLift == null)
			return null;
		return new Selection(bestLift, selectedChainPosition, selectedConnection,
			bestShape.getVec(bestLift, selectedChainPosition), bestShape);
	}

	private static boolean isWrench(ItemStack stack) {
		return stack.is(ModItems.WRENCH);
	}

	private static boolean isActive(Player player) {
		ItemStack mainHandItem = player.getMainHandItem();
		return player.isHolding(ChainConveyorInteractionHandler::isWrench)
			|| mainHandItem.is(PackagesRegistry.PACKAGE_FROGPORT_ITEM) || PackageItem.isPackage(mainHandItem);
	}

	/**
	 * Create's onUse: what a click does when the player points at a chain: a wrench rides the chain (and takes it down
	 * when sneaking), a frogport picks it as its target, a package is put on it.
	 */
	public static InteractionResult onUse(ServerPlayer player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || !isActive(player))
			return InteractionResult.PASS;

		boolean isWrench = player.isHolding(ChainConveyorInteractionHandler::isWrench);
		boolean dismantling = isWrench && player.isShiftKeyDown();
		Selection selection = select(player, dismantling);
		if (selection == null)
			return InteractionResult.PASS;

		ItemStack mainHandItem = player.getMainHandItem();

		if (isWrench) {
			if (!player.isShiftKeyDown()) {
				ChainConveyorRidingHandler.embark(player, selection.lift(), selection.chainPosition(),
					selection.connection());
				return InteractionResult.SUCCESS;
			}

			ItemStack usedItem = isWrench(mainHandItem) ? mainHandItem : player.getOffhandItem();
			ChainConveyorConnectionHandler.connect(player, selection.lift(), selection.lift()
				.offset(selection.connection()), usedItem, false);
			return InteractionResult.SUCCESS;
		}

		if (mainHandItem.is(PackagesRegistry.PACKAGE_FROGPORT_ITEM)) {
			PackagePortTargetSelectionHandler.select(mainHandItem, new PackagePortTarget.ChainConveyorFrogportTarget(
				selection.lift(), selection.chainPosition(), selection.connection(), false));
			player.sendOverlayMessage(Lang.builder(CreatePolymer.MOD_ID)
				.translate("package_port.valid")
				.style(ChatFormatting.GREEN)
				.component());
			return InteractionResult.SUCCESS;
		}

		if (PackageItem.isPackage(mainHandItem)) {
			ChainPackageInteractionHandler.put(player, selection.lift(), selection.connection(),
				selection.chainPosition());
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.SUCCESS;
	}

}
