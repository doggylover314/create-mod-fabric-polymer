package dev.createpolymer.equipment.armor;

import net.minecraft.world.item.equipment.ArmorType;

/** Create's cardboard armour piece. Its texture is the cardboard equipment asset of {@link AllArmorMaterials}. */
public class CardboardArmorItem extends BaseArmorItem {
	public CardboardArmorItem(ArmorType type, Properties properties) {
		super(AllArmorMaterials.CARDBOARD, type, properties);
	}
}
