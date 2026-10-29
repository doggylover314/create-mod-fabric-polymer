package dev.createpolymer.equipment.armor;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import dev.createpolymer.CreatePolymer;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;

/**
 * Ported from Create's DivingBootsItem (MIT, The Create Team). Create adds to the entity's motion every tick, which
 * vanilla clients (who move the player themselves) would ignore. The heavy boots use attribute modifiers instead,
 * which the server syncs to the client: while the wearer is in water the gravity attribute is raised, so they sink
 * quickly, and the water movement efficiency makes them walk on the seabed at their normal speed.
 */
public class DivingBootsItem extends BaseArmorItem {
	public static final EquipmentSlot SLOT = EquipmentSlot.FEET;
	public static final ArmorType TYPE = ArmorType.BOOTS;

	private static final AttributeModifier HEAVY_GRAVITY =
		new AttributeModifier(CreatePolymer.id("diving_boots_gravity"), .72, AttributeModifier.Operation.ADD_VALUE);
	private static final AttributeModifier HEAVY_WALKING =
		new AttributeModifier(CreatePolymer.id("diving_boots_water_movement"), 1, AttributeModifier.Operation.ADD_VALUE);

	/** Entities that currently carry the heavy boots modifiers */
	private static final Set<LivingEntity> AFFECTED = Collections.newSetFromMap(new WeakHashMap<>());

	public DivingBootsItem(ArmorMaterial material, Properties properties) {
		super(material, TYPE, properties);
	}

	public static boolean isWornBy(Entity entity) {
		return !getWornItem(entity).isEmpty();
	}

	public static ItemStack getWornItem(Entity entity) {
		if (!(entity instanceof LivingEntity livingEntity)) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = livingEntity.getItemBySlot(SLOT);
		if (!(stack.getItem() instanceof DivingBootsItem)) {
			return ItemStack.EMPTY;
		}
		return stack;
	}

	/** Called for every player each server tick. */
	public static void accelerateDescentUnderwater(LivingEntity entity) {
		boolean affects = affects(entity);
		if (affects == AFFECTED.contains(entity))
			return;
		if (affects) {
			AFFECTED.add(entity);
			addModifier(entity, Attributes.GRAVITY, HEAVY_GRAVITY);
			addModifier(entity, Attributes.WATER_MOVEMENT_EFFICIENCY, HEAVY_WALKING);
		} else {
			remove(entity);
		}
	}

	private static void remove(LivingEntity entity) {
		AFFECTED.remove(entity);
		removeModifier(entity, Attributes.GRAVITY, HEAVY_GRAVITY);
		removeModifier(entity, Attributes.WATER_MOVEMENT_EFFICIENCY, HEAVY_WALKING);
	}

	private static void addModifier(LivingEntity entity, Holder<Attribute> attribute, AttributeModifier modifier) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance != null)
			instance.addOrUpdateTransientModifier(modifier);
	}

	private static void removeModifier(LivingEntity entity, Holder<Attribute> attribute, AttributeModifier modifier) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance != null)
			instance.removeModifier(modifier);
	}

	protected static boolean affects(LivingEntity entity) {
		if (!isWornBy(entity))
			return false;
		if (!entity.isInWater())
			return false;
		if (entity.getPose() == Pose.SWIMMING)
			return false;
		if (entity instanceof Player playerEntity) {
			if (playerEntity.getAbilities().flying)
				return false;
		}
		return true;
	}
}
