package dev.createpolymer.logistics.depot;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.registry.feature.EjectorRegistry;
import dev.createpolymer.util.Lang;
import dev.createpolymer.util.VecHelper;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create's EjectorTargetHandler and EjectorPlacementPacket without the client half. Create keeps the selected
 * target in a static field of the client and sends the distance to the server when the ejector is placed. Here
 * the selection is kept in the item (see {@link EjectorRegistry#EJECTOR_TARGET}) and applied by
 * {@link EjectorItem} when the ejector is placed.
 */
public class EjectorTargetHandler {

	public static void init() {
		AttackBlockCallback.EVENT.register(EjectorTargetHandler::leftClickingBlocksDeselectsThem);
	}

	public static @Nullable GlobalPos getSelection(ItemStack stack) {
		return stack.get(EjectorRegistry.EJECTOR_TARGET);
	}

	/** Create: rightClickingBlocksSelectsThem (sneaking, with an ejector in hand). */
	public static void rightClickingBlocksSelectsThem(Player player, ItemStack stack, Level world, BlockPos pos) {
		if (player == null || player.isSpectator() || !player.isShiftKeyDown())
			return;

		stack.set(EjectorRegistry.EJECTOR_TARGET, GlobalPos.of(world.dimension(), pos.immutable()));
		player.sendOverlayMessage(translate("weighted_ejector.target_set").withStyle(ChatFormatting.GOLD));
	}

	/** Create: leftClickingBlocksDeselectsThem. */
	private static InteractionResult leftClickingBlocksDeselectsThem(Player player, Level world, InteractionHand hand,
		BlockPos pos, Direction direction) {
		ItemStack stack = player.getItemInHand(hand);
		if (!stack.is(EjectorRegistry.WEIGHTED_EJECTOR_ITEM))
			return InteractionResult.PASS;
		if (!player.isShiftKeyDown())
			return InteractionResult.PASS;
		GlobalPos selection = getSelection(stack);
		if (selection == null || !selection.dimension().equals(world.dimension()) || !selection.pos().equals(pos))
			return InteractionResult.PASS;
		stack.remove(EjectorRegistry.EJECTOR_TARGET);
		return InteractionResult.SUCCESS;
	}

	/**
	 * Create: flushSettings and EjectorPlacementPacket.handle. {@code pos} is where the ejector was just placed;
	 * the selection is used up.
	 */
	public static void flushSettings(Player player, Level world, BlockPos pos, @Nullable GlobalPos selection) {
		BlockPos currentSelection = selection != null && selection.dimension().equals(world.dimension())
			? selection.pos() : null;

		int h = 0;
		int v = 0;

		String key = "weighted_ejector.target_not_valid";
		ChatFormatting colour = ChatFormatting.WHITE;

		if (selection == null)
			key = "weighted_ejector.no_target";

		Direction validTargetDirection = getValidTargetDirection(pos, currentSelection);
		if (validTargetDirection == null) {
			player.sendOverlayMessage(translate(key).withStyle(colour));
			return;
		}

		key = "weighted_ejector.targeting";
		colour = ChatFormatting.GREEN;

		player.sendOverlayMessage(
			translate(key, currentSelection.getX(), currentSelection.getY(), currentSelection.getZ())
				.withStyle(colour));

		BlockPos diff = pos.subtract(currentSelection);
		h = Math.abs(diff.getX() + diff.getZ());
		v = -diff.getY();

		applyPlacement(world, pos, h, v, validTargetDirection);
	}

	/** Create: EjectorPlacementPacket.handle */
	public static void applyPlacement(Level world, BlockPos pos, int h, int v, Direction facing) {
		if (!world.isLoaded(pos))
			return;
		BlockEntity blockEntity = world.getBlockEntity(pos);
		BlockState state = world.getBlockState(pos);
		if (blockEntity instanceof EjectorBlockEntity)
			((EjectorBlockEntity) blockEntity).setTarget(h, v);
		if (state.is(EjectorRegistry.WEIGHTED_EJECTOR))
			world.setBlockAndUpdate(pos, state.setValue(EjectorBlock.HORIZONTAL_FACING, facing));
	}

	public static @Nullable Direction getValidTargetDirection(BlockPos pos, @Nullable BlockPos currentSelection) {
		if (currentSelection == null)
			return null;
		if (VecHelper.onSameAxis(pos, currentSelection, Axis.Y))
			return null;

		int xDiff = currentSelection.getX() - pos.getX();
		int zDiff = currentSelection.getZ() - pos.getZ();
		int max = EjectorRegistry.maxEjectorDistance;

		if (Math.abs(xDiff) > max || Math.abs(zDiff) > max)
			return null;

		if (xDiff == 0)
			return Direction.get(zDiff < 0 ? AxisDirection.NEGATIVE : AxisDirection.POSITIVE, Axis.Z);
		if (zDiff == 0)
			return Direction.get(xDiff < 0 ? AxisDirection.NEGATIVE : AxisDirection.POSITIVE, Axis.X);

		return null;
	}

	private static MutableComponent translate(String key, Object... args) {
		return Lang.builder(CreatePolymer.MOD_ID).translate(key, args).component();
	}

}
