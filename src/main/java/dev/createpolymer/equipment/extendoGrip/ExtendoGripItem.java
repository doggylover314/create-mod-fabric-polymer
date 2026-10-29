package dev.createpolymer.equipment.extendoGrip;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.equipment.EquipmentConfig;
import dev.createpolymer.equipment.armor.BacktankUtil;
import dev.createpolymer.registry.feature.EquipmentRegistry;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Ported from Create's ExtendoGripItem (MIT, The Create Team). Holding one grants the reach of the 26.3 interaction
 * range attributes; the server syncs them to the player's client, which then does its own long range picking. That
 * replaces Create's client event and packet for entities beyond the vanilla range, so the entity range is raised
 * along with the block range.
 */
public class ExtendoGripItem extends SimplePolymerItem {
	public static final int MAX_DAMAGE = 200;

	public static final AttributeModifier singleRangeAttributeModifier =
		new AttributeModifier(CreatePolymer.id("single_range_attribute_modifier"), 3,
			AttributeModifier.Operation.ADD_VALUE);
	public static final AttributeModifier doubleRangeAttributeModifier =
		new AttributeModifier(CreatePolymer.id("double_range_attribute_modifier"), 5,
			AttributeModifier.Operation.ADD_VALUE);

	private static final Holder<Attribute>[] RANGE_ATTRIBUTES =
		new Holder[] { Attributes.BLOCK_INTERACTION_RANGE, Attributes.ENTITY_INTERACTION_RANGE };

	public ExtendoGripItem(Properties properties) {
		super(properties.durability(MAX_DAMAGE), Items.TRIAL_KEY, true);
	}

	/** Create's holdingExtendoGripIncreasesRange, run for every player each server tick. */
	public static void holdingExtendoGripIncreasesRange(Player player) {
		boolean inOff = isExtendoGrip(player.getOffhandItem());
		boolean inMain = isExtendoGrip(player.getMainHandItem());
		boolean holdingDualExtendo = inOff && inMain;
		boolean holdingExtendo = inOff ^ inMain;

		setModifier(player, singleRangeAttributeModifier, holdingExtendo);
		setModifier(player, doubleRangeAttributeModifier, holdingDualExtendo);
	}

	private static void setModifier(Player player, AttributeModifier modifier, boolean present) {
		for (Holder<Attribute> attribute : RANGE_ATTRIBUTES) {
			AttributeInstance instance = player.getAttribute(attribute);
			if (instance == null || instance.hasModifier(modifier.id()) == present)
				continue;
			if (present)
				instance.addTransientModifier(modifier);
			else
				instance.removeModifier(modifier);
		}
	}

	public static void consumeDurabilityOnBlockBreak(Player player) {
		findAndDamageExtendoGrip(player);
	}

	public static void damageOnAttack(Player player, Entity target) {
		findAndDamageExtendoGrip(player);
	}

	private static void findAndDamageExtendoGrip(Player player) {
		if (player == null)
			return;
		if (player.level().isClientSide())
			return;
		EquipmentSlot equipmentSlot = EquipmentSlot.MAINHAND;
		ItemStack extendo = player.getMainHandItem();
		if (!isExtendoGrip(extendo)) {
			extendo = player.getOffhandItem();
			equipmentSlot = EquipmentSlot.OFFHAND;
		}
		if (!isExtendoGrip(extendo))
			return;
		if (!BacktankUtil.canAbsorbDamage(player, maxUses()))
			extendo.hurtAndBreak(1, player, equipmentSlot);
	}

	private static int maxUses() {
		return EquipmentConfig.maxExtendoGripActions;
	}

	/** Attacks by extendo grip have more knockback (Create's attacksByExtendoGripHaveMoreKnockback) */
	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		target.knockback(2, attacker.getX() - target.getX(), attacker.getZ() - target.getZ(),
			target.damageSources()
				.generic(),
			0);
	}

	public static boolean isExtendoGrip(ItemStack stack) {
		return stack.is(EquipmentRegistry.EXTENDO_GRIP);
	}

	public static boolean isHoldingExtendoGrip(Player player) {
		boolean inOff = isExtendoGrip(player.getOffhandItem());
		boolean inMain = isExtendoGrip(player.getMainHandItem());
		boolean holdingGrip = inOff || inMain;
		return holdingGrip;
	}

	public static void tick(ServerPlayer player) {
		holdingExtendoGripIncreasesRange(player);
	}
}
