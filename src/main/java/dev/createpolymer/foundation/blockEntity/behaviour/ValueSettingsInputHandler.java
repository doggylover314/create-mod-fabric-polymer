package dev.createpolymer.foundation.blockEntity.behaviour;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.SidedFilteringBehaviour;
import dev.createpolymer.foundation.gui.ValueSettingsGui;
import dev.createpolymer.equipment.clipboard.ClipboardBlockItem;
import dev.createpolymer.registry.ModItems;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Create's right-click handling for value boxes. A vanilla client has no scroll wheel and cannot hold the use key
 * to tell a quick click from a long press, so:
 * <ul>
 * <li>a quick click with an item (other than the wrench) calls {@code onShortInteract}, which is how filters are set,</li>
 * <li>an empty hand or the wrench opens the value settings GUI, if the behaviour accepts value settings,</li>
 * <li>behaviours that do not accept value settings always get {@code onShortInteract}.</li>
 * </ul>
 */
public class ValueSettingsInputHandler {

	private static boolean registered;

	/** Registers the handler with Fabric's {@link UseBlockCallback}. Call once during mod initialisation. */
	public static void init() {
		if (registered)
			return;
		registered = true;
		UseBlockCallback.EVENT.register(ValueSettingsInputHandler::onBlockActivated);
	}

	public static InteractionResult onBlockActivated(Player player, Level world, InteractionHand hand,
		BlockHitResult ray) {
		if (world.isClientSide())
			return InteractionResult.PASS;
		BlockPos pos = ray.getBlockPos();

		if (!canInteract(player))
			return InteractionResult.PASS;
		if (!(world.getBlockEntity(pos) instanceof SmartBlockEntity sbe))
			return InteractionResult.PASS;

		for (BlockEntityBehaviour behaviour : sbe.getAllBehaviours()) {
			if (!(behaviour instanceof ValueSettingsBehaviour valueSettingsBehaviour))
				continue;
			if (valueSettingsBehaviour.bypassesInput(player.getMainHandItem()))
				continue;
			if (!valueSettingsBehaviour.mayInteract(player))
				continue;

			if (behaviour instanceof SidedFilteringBehaviour sided && sided.get(ray.getDirection()) == null)
				continue;

			if (!valueSettingsBehaviour.isActive())
				continue;
			if (valueSettingsBehaviour.onlyVisibleWithWrench()
				&& !player.getItemInHand(hand).is(ModItems.WRENCH))
				continue;
			if (valueSettingsBehaviour.getSlotPositioning()instanceof ValueBoxTransform.Sided sidedSlot) {
				if (!sidedSlot.isSideActive(sbe.getBlockState(), ray.getDirection()))
					continue;
				sidedSlot.fromSide(ray.getDirection());
			}

			boolean fakePlayer = player instanceof FakePlayer;
			if (!valueSettingsBehaviour.testHit(ray.getLocation()) && !fakePlayer)
				continue;

			ItemStack held = player.getItemInHand(hand);
			if (!valueSettingsBehaviour.acceptsValueSettings() || fakePlayer
				|| (!held.isEmpty() && !held.is(ModItems.WRENCH))) {
				valueSettingsBehaviour.onShortInteract(player, hand, ray.getDirection(), ray);
				return InteractionResult.SUCCESS;
			}

			if (player instanceof ServerPlayer serverPlayer)
				ValueSettingsGui.open(serverPlayer, behaviour, ray);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	public static boolean canInteract(Player player) {
		if (player != null && player.getMainHandItem()
			.getItem() instanceof ClipboardBlockItem)
			return false;
		return player != null && !player.isSpectator() && !player.isShiftKeyDown() && !isAdventure(player);
	}

	private static boolean isAdventure(Player player) {
		return !player.mayBuild() && !player.isSpectator();
	}
}
