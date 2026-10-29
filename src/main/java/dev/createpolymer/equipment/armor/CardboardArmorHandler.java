package dev.createpolymer.equipment.armor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.entity.EntityTypeTest;

/**
 * Ported from Create's CardboardArmorHandler (MIT, The Create Team): a crouching player in full cardboard armour is
 * dropped as target by the mobs, every 16 ticks, and the hurt-by and anger memory of the mobs forgets them. Not ported:
 * the hitbox change and the visibility modifier (they need hooks into entity sizing and visibility that Fabric does
 * not provide), and the stop of the running target goals (the goal selector is protected in 26.3).
 */
public final class CardboardArmorHandler {
	private CardboardArmorHandler() {}

	/** Server tick: the mobs that were after a stealthed entity lose it. */
	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 16 != 0)
			return;
		for (ServerLevel level : server.getAllLevels()) {
			List<Mob> mobs = new ArrayList<>();
			level.getEntities(EntityTypeTest.forClass(Mob.class), mob -> true, mobs);
			for (Mob mob : mobs)
				mobMayLoseTarget(mob, level);
		}
	}

	private static void mobMayLoseTarget(Mob mob, ServerLevel level) {
		if (testForStealth(mob.getTarget())) {
			mob.setTarget(null);
		}

		if (mob instanceof NeutralMob nMob) {
			if (testForStealth(EntityReference.getLivingEntity(nMob.getPersistentAngerTarget(), level)))
				nMob.stopBeingAngry();
		}

		if (testForStealth(mob.getLastHurtByMob())) {
			mob.setLastHurtByMob(null);
			mob.setLastHurtByPlayer((Player) null, 0);
		}
	}

	public static boolean testForStealth(Entity entityIn) {
		if (!(entityIn instanceof LivingEntity entity))
			return false;
		if (entity.getPose() != Pose.CROUCHING)
			return false;
		if (entity instanceof Player player && player.getAbilities().flying)
			return false;
		return isCardboardArmor(entity.getItemBySlot(EquipmentSlot.HEAD))
			&& isCardboardArmor(entity.getItemBySlot(EquipmentSlot.CHEST))
			&& isCardboardArmor(entity.getItemBySlot(EquipmentSlot.LEGS))
			&& isCardboardArmor(entity.getItemBySlot(EquipmentSlot.FEET));
	}

	public static boolean isCardboardArmor(ItemStack stack) {
		return stack.getItem() instanceof CardboardArmorItem;
	}
}
