package dev.createpolymer.equipment.armor;

import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import java.util.function.UnaryOperator;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;

/**
 * Ported from Create's BaseArmorItem (MIT, The Create Team). The armor texture is the equipment asset named by the
 * material, which vanilla clients load from the resource pack.
 */
public class BaseArmorItem extends SimplePolymerItem {
	protected final ArmorMaterial material;
	protected final ArmorType type;

	public BaseArmorItem(ArmorMaterial armorMaterial, ArmorType type, Properties properties) {
		this(armorMaterial, type, properties, UnaryOperator.identity());
	}

	/** {@code attributes} adjusts the armor values the material gives the item. */
	protected BaseArmorItem(ArmorMaterial armorMaterial, ArmorType type, Properties properties,
		UnaryOperator<ItemAttributeModifiers> attributes) {
		super(properties.humanoidArmor(armorMaterial, type)
			.attributes(attributes.apply(armorMaterial.createAttributes(type)))
			.stacksTo(1), Items.TRIAL_KEY, true);
		this.material = armorMaterial;
		this.type = type;
	}

	/** Armor without durability, like Create's backtanks. */
	protected BaseArmorItem(ArmorMaterial armorMaterial, ArmorType type, Properties properties, boolean unbreakable) {
		super(properties.attributes(armorMaterial.createAttributes(type))
			.enchantable(armorMaterial.enchantmentValue())
			.component(DataComponents.EQUIPPABLE, Equippable.builder(type.getSlot())
				.setEquipSound(armorMaterial.equipSound())
				.setAsset(armorMaterial.assetId())
				.setDamageOnHurt(false)
				.build())
			.stacksTo(1), Items.TRIAL_KEY, true);
		this.material = armorMaterial;
		this.type = type;
	}

	public ArmorMaterial getMaterial() {
		return material;
	}

	public ArmorType getType() {
		return type;
	}
}
