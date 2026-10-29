package dev.createpolymer.equipment.armor;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import dev.createpolymer.registry.feature.EquipmentRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Ported from Create's NetheriteDivingHandler (MIT, The Create Team). Create sets a "fire immune" flag on the entity
 * when it wears the full netherite diving set (netherite diving helmet, a netherite backtank that still has air,
 * netherite leggings and boots), which its mixins turn into fire immunity. A vanilla client only needs the fire
 * resistance effect for the same result, so the wearer gets a hidden, endless one while the set is complete.
 * The slots are checked every tick instead of on equipment changes, so an emptied backtank ends it right away.
 */
public final class NetheriteDivingHandler {

	/** Entities that currently carry the effect */
	private static final Set<LivingEntity> FIRE_IMMUNE = Collections.newSetFromMap(new WeakHashMap<>());

	/** Called for every player each server tick. */
	public static void tick(LivingEntity entity) {
		boolean fullSet = isFullSet(entity);
		if (fullSet == FIRE_IMMUNE.contains(entity))
			return;
		setFireImmune(entity, fullSet);
	}

	public static boolean isFullSet(LivingEntity entity) {
		ItemStack backtank = entity.getItemBySlot(EquipmentSlot.CHEST);
		return isNetheriteDivingHelmet(entity.getItemBySlot(EquipmentSlot.HEAD))
			&& isNetheriteBacktank(backtank) && BacktankUtil.hasAirRemaining(backtank)
			&& isNetheriteArmor(entity.getItemBySlot(EquipmentSlot.LEGS))
			&& isNetheriteArmor(entity.getItemBySlot(EquipmentSlot.FEET));
	}

	public static boolean isNetheriteDivingHelmet(ItemStack stack) {
		return stack.getItem() instanceof DivingHelmetItem && isNetheriteArmor(stack);
	}

	public static boolean isNetheriteBacktank(ItemStack stack) {
		return stack.is(EquipmentRegistry.PRESSURIZED_AIR_SOURCES) && isNetheriteArmor(stack);
	}

	public static boolean isNetheriteArmor(ItemStack stack) {
		return stack.has(DataComponents.EQUIPPABLE) && stack.has(DataComponents.DAMAGE_RESISTANT);
	}

	public static void setFireImmune(LivingEntity entity, boolean fireImmune) {
		if (fireImmune) {
			FIRE_IMMUNE.add(entity);
			entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, true, false, false));
		} else {
			FIRE_IMMUNE.remove(entity);
			MobEffectInstance effect = entity.getEffect(MobEffects.FIRE_RESISTANCE);
			if (effect != null && effect.isInfiniteDuration())
				entity.removeEffect(MobEffects.FIRE_RESISTANCE);
		}
	}
}
