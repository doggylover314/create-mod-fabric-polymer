package dev.createpolymer.kinetics.chainConveyor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.registry.feature.ChainConveyorRegistry;
import dev.createpolymer.util.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Create's ChainConveyorConnectionHandler and ChainConveyorConnectionPacket. Create remembers the first conveyor a
 * player clicked on the client and sends the pair to the server; the server remembers the first conveyor of each
 * player here. Its highlights and previews are left out.
 */
public class ChainConveyorConnectionHandler {

	private record FirstSelection(BlockPos pos, ResourceKey<Level> dimension) {
	}

	private static final Map<UUID, FirstSelection> selections = new HashMap<>();

	/** The conveyor the player selected as the first of a connection, if any. */
	@Nullable
	public static BlockPos getSelection(Player player) {
		FirstSelection selection = selections.get(player.getUUID());
		return selection == null ? null : selection.pos();
	}

	public static void clearSelection(Player player) {
		selections.remove(player.getUUID());
	}

	/**
	 * Create's onRightClick: using a chain on anything but a chain conveyor, or while sneaking, clears the selection.
	 *
	 * @param hit the block the player points at, or null when they point at nothing
	 * @return true if the click was used up
	 */
	public static boolean onRightClick(ServerPlayer player, @Nullable BlockHitResult hit) {
		if (!isChain(player.getMainHandItem()))
			return false;
		if (!selections.containsKey(player.getUUID()))
			return false;
		boolean missed = false;
		if (hit != null && hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS)
			if (!(player.level().getBlockEntity(hit.getBlockPos()) instanceof ChainConveyorBlockEntity))
				missed = true;
		if (!player.isShiftKeyDown() && !missed)
			return false;
		selections.remove(player.getUUID());
		translate("chain_conveyor.selection_cleared").sendStatus(player);
		return true;
	}

	/** Create's onItemUsedOnBlock: a chain used on a chain conveyor selects it, or connects it to the selected one. */
	public static InteractionResult onItemUsedOnBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		ItemStack itemStack = player.getItemInHand(hand);
		if (!(level.getBlockState(pos).getBlock() instanceof ChainConveyorBlock))
			return InteractionResult.PASS;
		if (!isChain(itemStack))
			return InteractionResult.PASS;
		if (!player.mayBuild() || player instanceof net.fabricmc.fabric.api.entity.FakePlayer)
			return InteractionResult.PASS;
		if (level.isClientSide() || !(player instanceof ServerPlayer))
			return InteractionResult.SUCCESS;

		if (level.getBlockEntity(pos) instanceof ChainConveyorBlockEntity ccbe
			&& ccbe.connections.size() >= ChainConveyorRegistry.maxChainConveyorConnections) {
			translate("chain_conveyor.cannot_add_more_connections")
				.style(ChatFormatting.RED)
				.sendStatus(player);
			return InteractionResult.SUCCESS;
		}

		FirstSelection first = selections.get(player.getUUID());
		if (first == null || first.dimension() != level.dimension()) {
			selections.put(player.getUUID(), new FirstSelection(pos, level.dimension()));
			translate("chain_conveyor.select_second").sendStatus(player);
			return InteractionResult.SUCCESS;
		}

		boolean success = validateAndConnect(level, first.pos(), pos, player, itemStack);
		selections.remove(player.getUUID());

		if (!success) {
			level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1f, .5f);
			return InteractionResult.SUCCESS;
		}

		SoundType soundtype = Blocks.IRON_CHAIN.defaultBlockState()
			.getSoundType();
		level.playSound(null, pos, soundtype.getPlaceSound(), SoundSource.BLOCKS,
			(soundtype.getVolume() + 1.0F) / 2.0F, soundtype.getPitch() * 0.8F);
		return InteractionResult.SUCCESS;
	}

	public static boolean isChain(ItemStack itemStack) {
		return itemStack.is(Items.IRON_CHAIN); // Replace with tag? generic renderer?
	}

	/** Create's validateAndConnect, for the player's own click: checks the pair and connects it. */
	public static boolean validateAndConnect(LevelAccessor level, BlockPos firstPos, BlockPos pos, Player player,
		ItemStack chain) {
		if (player.isShiftKeyDown()) {
			translate("chain_conveyor.selection_cleared").sendStatus(player);
			return false;
		}

		if (pos.equals(firstPos))
			return false;

		String problem = validate(level, firstPos, pos, player, chain);
		if (problem != null)
			return fail(player, problem);

		if (!(player instanceof ServerPlayer serverPlayer))
			return false;
		connect(serverPlayer, firstPos, pos, chain, true);

		// Clear status message
		player.sendOverlayMessage(net.minecraft.network.chat.Component.empty());
		return true;
	}

	/** @return the lang key of what is wrong with a connection between the two conveyors, or null if there is nothing */
	@Nullable
	public static String validate(LevelAccessor level, BlockPos firstPos, BlockPos pos, Player player, ItemStack chain) {
		if (!pos.closerThan(firstPos, ChainConveyorRegistry.maxChainConveyorLength))
			return "chain_conveyor.too_far";
		if (pos.closerThan(firstPos, 2.5))
			return "chain_conveyor.too_close";

		Vec3 diff = Vec3.atLowerCornerOf(pos.subtract(firstPos));
		double horizontalDistance = diff.multiply(1, 0, 1)
			.length() - 1.5;

		if (horizontalDistance <= 0)
			return "chain_conveyor.cannot_connect_vertically";
		if (Math.abs(diff.y) / horizontalDistance > 1)
			return "chain_conveyor.too_steep";

		ChainConveyorBlock chainConveyorBlock = ChainConveyorRegistry.CHAIN_CONVEYOR;
		ChainConveyorBlockEntity sourceLift = chainConveyorBlock.getBlockEntity(level, firstPos);
		ChainConveyorBlockEntity targetLift = chainConveyorBlock.getBlockEntity(level, pos);

		if (sourceLift == null || targetLift == null)
			return "chain_conveyor.blocks_invalid";
		if (targetLift.connections.size() >= ChainConveyorRegistry.maxChainConveyorConnections)
			return "chain_conveyor.cannot_add_more_connections";
		if (targetLift.connections.contains(firstPos.subtract(pos)))
			return "chain_conveyor.already_connected";

		if (!player.isCreative()) {
			int chainCost = ChainConveyorBlockEntity.getChainCost(pos.subtract(firstPos));
			boolean hasEnough = ChainConveyorBlockEntity.getChainsFromInventory(player, chain, chainCost, true);
			if (!hasEnough)
				return "chain_conveyor.not_enough_chains";
		}

		return null;
	}

	private static boolean fail(Player player, String message) {
		translate(message)
			.style(ChatFormatting.RED)
			.sendStatus(player);
		return false;
	}

	/**
	 * Create's ChainConveyorConnectionPacket.applySettings: connects (paying for the chains) or disconnects (giving
	 * the chains back) the conveyor at {@code pos} and the one at {@code targetPos}.
	 */
	public static void connect(ServerPlayer player, BlockPos pos, BlockPos targetPos, ItemStack chain, boolean connect) {
		if (!(player.level().getBlockEntity(pos) instanceof ChainConveyorBlockEntity be))
			return;
		if (!be.getBlockPos()
			.closerThan(targetPos, ChainConveyorRegistry.maxChainConveyorLength + 1))
			return;
		if (!(be.getLevel()
			.getBlockEntity(targetPos) instanceof ChainConveyorBlockEntity clbe))
			return;

		if (connect && !player.isCreative()) {
			int chainCost = ChainConveyorBlockEntity.getChainCost(targetPos.subtract(be.getBlockPos()));
			boolean hasEnough = ChainConveyorBlockEntity.getChainsFromInventory(player, chain, chainCost, true);
			if (!hasEnough)
				return;
			ChainConveyorBlockEntity.getChainsFromInventory(player, chain, chainCost, false);
		}

		if (!connect) {
			if (!player.isCreative()) {
				int chainCost = ChainConveyorBlockEntity.getChainCost(targetPos.subtract(pos));
				while (chainCost > 0) {
					player.getInventory()
						.placeItemBackInInventory(new ItemStack(Items.IRON_CHAIN, Math.min(chainCost, 64)),
							Prediction.SERVER_ONLY);
					chainCost -= 64;
				}
			}
			be.chainDestroyed(targetPos.subtract(be.getBlockPos()), false, true);
			be.getLevel()
				.playSound(null, player.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS);
		}

		if (connect) {
			if (!clbe.addConnectionTo(be.getBlockPos()))
				return;
		} else
			clbe.removeConnectionTo(be.getBlockPos());

		if (connect) {
			if (!be.addConnectionTo(targetPos))
				clbe.removeConnectionTo(be.getBlockPos());
		} else
			be.removeConnectionTo(targetPos);
	}

	private static dev.createpolymer.util.LangBuilder translate(String key) {
		return Lang.builder(CreatePolymer.MOD_ID)
			.translate(key);
	}

}
