package dev.createpolymer.equipment.zapper;

import java.util.function.Predicate;

import dev.createpolymer.registry.feature.EquipmentRegistry;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's ShootableGadgetItemMethods (MIT, The Create Team). The packets that animate the gadget on
 * clients are gone; {@link #shouldSwap} no longer makes the player start using the item, because a vanilla client
 * would slow the player down for as long as an item is in use.
 */
public class ShootableGadgetItemMethods {

	public static void applyCooldown(Player player, ItemStack item, InteractionHand hand, Predicate<ItemStack> predicate,
		int cooldown) {
		if (cooldown <= 0)
			return;

		boolean gunInOtherHand =
			predicate.test(player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND));
		player.getCooldowns()
			.addCooldown(item, gunInOtherHand ? cooldown * 2 / 3 : cooldown);
	}

	public static boolean shouldSwap(Player player, ItemStack item, InteractionHand hand, Predicate<ItemStack> predicate) {
		boolean isSwap = item.has(EquipmentRegistry.SHAPER_SWAP);
		boolean mainHand = hand == InteractionHand.MAIN_HAND;
		boolean gunInOtherHand = predicate.test(player.getItemInHand(mainHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND));

		// Pass To Offhand
		if (mainHand && isSwap && gunInOtherHand)
			return true;
		if (mainHand && !isSwap && gunInOtherHand)
			item.set(EquipmentRegistry.SHAPER_SWAP, true);
		if (!mainHand && isSwap)
			item.remove(EquipmentRegistry.SHAPER_SWAP);
		if (!mainHand && gunInOtherHand)
			player.getItemInHand(InteractionHand.MAIN_HAND)
				.remove(EquipmentRegistry.SHAPER_SWAP);
		return false;
	}

	public static Vec3 getGunBarrelVec(Player player, boolean mainHand, Vec3 rightHandForward) {
		Vec3 start = player.position()
			.add(0, player.getEyeHeight(), 0);
		float yaw = (float) ((player.getYRot()) / -180 * Math.PI);
		float pitch = (float) ((player.getXRot()) / -180 * Math.PI);
		int flip = mainHand == (player.getMainArm() == HumanoidArm.RIGHT) ? -1 : 1;
		Vec3 barrelPosNoTransform = new Vec3(flip * rightHandForward.x, rightHandForward.y, rightHandForward.z);
		Vec3 barrelPos = start.add(barrelPosNoTransform.xRot(pitch)
			.yRot(yaw));
		return barrelPos;
	}

}
