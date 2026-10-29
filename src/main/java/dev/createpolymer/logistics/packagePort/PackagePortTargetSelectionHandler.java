package dev.createpolymer.logistics.packagePort;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.registry.feature.PackagesRegistry;
import dev.createpolymer.util.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Create's PackagePortTargetSelectionHandler and PackagePortPlacementPacket without the client half. Create picks the
 * target on the client (clicking a chain conveyor or train station with a package port in hand) and sends it to the
 * server once the port stands. Here the pick is stored in the port's item ({@link PackagesRegistry#PACKAGE_PORT_TARGET},
 * with {@code relativePos} still holding the absolute position of the target block) by whatever handles the click on
 * the target, and {@link PackagePortItem} hands it to {@link #flushSettings} when the port was placed.
 */
public class PackagePortTargetSelectionHandler {

	/** Stores the picked target in the item; the position of its target block is absolute until the port is placed. */
	public static void select(ItemStack portItem, PackagePortTarget target) {
		portItem.set(PackagesRegistry.PACKAGE_PORT_TARGET, target);
	}

	@Nullable
	public static PackagePortTarget getSelection(ItemStack portItem) {
		return portItem.get(PackagesRegistry.PACKAGE_PORT_TARGET);
	}

	/**
	 * Create: flushSettings and PackagePortPlacementPacket.handle. {@code pos} is where the port was just placed.
	 */
	public static void flushSettings(Player player, Level world, BlockPos pos, @Nullable PackagePortTarget selection) {
		if (selection == null) {
			player.sendOverlayMessage(
				translate("gui.package_port.not_targeting_anything").withStyle(ChatFormatting.WHITE));
			return;
		}
		if (!world.isLoaded(pos))
			return;
		BlockEntity blockEntity = world.getBlockEntity(pos);
		if (!(blockEntity instanceof PackagePortBlockEntity ppbe))
			return;

		selection.relativePos = selection.relativePos.subtract(pos);
		if (!selection.canSupport(ppbe))
			return;

		Vec3 targetLocation = selection.getExactTargetLocation(ppbe, world, pos);
		String problem = validateDiff(targetLocation, pos, false);
		if (targetLocation == Vec3.ZERO || problem != null) {
			if (problem != null)
				player.sendOverlayMessage(translate(problem).withStyle(ChatFormatting.RED));
			return;
		}

		selection.setup(ppbe, world, pos);
		ppbe.target = selection;
		ppbe.notifyUpdate();
		ppbe.use(player);
	}

	/**
	 * @param isPostbox postboxes may be placed above their target; frogports reach down only
	 * @return a lang key naming what is wrong with the placement, or null if it is fine
	 */
	@Nullable
	public static String validateDiff(Vec3 target, BlockPos placedPos, boolean isPostbox) {
		Vec3 source = Vec3.atBottomCenterOf(placedPos);
		Vec3 diff = target.subtract(source);
		if (diff.y < 0 && !isPostbox)
			return "package_port.cannot_reach_down";
		if (diff.length() > PackagesRegistry.packagePortRange)
			return "package_port.too_far";
		return null;
	}

	private static net.minecraft.network.chat.MutableComponent translate(String key) {
		return Lang.builder(CreatePolymer.MOD_ID)
			.translate(key)
			.component();
	}

}
