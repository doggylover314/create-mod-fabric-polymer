package dev.createpolymer.equipment.armor;

import java.util.Map;

import dev.createpolymer.CreatePolymer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Ported from Create's AllArmorMaterials (MIT, The Create Team). 26.3 armor materials are plain records that name an
 * equipment asset, an {@code assets/<namespace>/equipment/<name>.json} file of the resource pack.
 */
public class AllArmorMaterials {
	public static final ResourceKey<EquipmentAsset> COPPER_DIVING_ASSET = asset("copper_diving");
	public static final ResourceKey<EquipmentAsset> NETHERITE_DIVING_ASSET = asset("netherite_diving");
	public static final ResourceKey<EquipmentAsset> CARDBOARD_ASSET = asset("cardboard");

	/** The items that repair cardboard armor and the cardboard sword (Create's CARDBOARD ingredient). */
	public static final TagKey<Item> CARDBOARD_REPAIR = TagKey.create(Registries.ITEM, CreatePolymer.id("repairs_cardboard_armor"));

	// Create's cardboard: { 1, 1, 1, 1, 2 } for helmet, chestplate, leggings, boots, body; durability 4, enchantability 4
	public static final ArmorMaterial CARDBOARD = new ArmorMaterial(
		4,
		ArmorMaterials.makeDefense(1, 1, 1, 1, 2),
		4,
		SoundEvents.ARMOR_EQUIP_LEATHER,
		0.0F,
		0.0F,
		CARDBOARD_REPAIR,
		CARDBOARD_ASSET
	);

	// Create's copper material: { 2, 4, 3, 1, 4 } for helmet, chestplate, leggings, boots, body
	public static final ArmorMaterial COPPER = new ArmorMaterial(
		7,
		ArmorMaterials.makeDefense(1, 3, 4, 2, 4),
		7,
		SoundEvents.ARMOR_EQUIP_GOLD,
		0.0F,
		0.0F,
		ItemTags.REPAIRS_COPPER_ARMOR,
		COPPER_DIVING_ASSET
	);

	/** Vanilla's netherite stats with Create's netherite diving look. */
	public static final ArmorMaterial NETHERITE = new ArmorMaterial(
		ArmorMaterials.NETHERITE.durability(),
		Map.copyOf(ArmorMaterials.NETHERITE.defense()),
		ArmorMaterials.NETHERITE.enchantmentValue(),
		ArmorMaterials.NETHERITE.equipSound(),
		ArmorMaterials.NETHERITE.toughness(),
		ArmorMaterials.NETHERITE.knockbackResistance(),
		ArmorMaterials.NETHERITE.repairIngredient(),
		NETHERITE_DIVING_ASSET
	);

	private static ResourceKey<EquipmentAsset> asset(String name) {
		return ResourceKey.create(EquipmentAssets.ROOT_ID, CreatePolymer.id(name));
	}
}
