package dev.createpolymer.equipment.tool;

import dev.createpolymer.equipment.armor.AllArmorMaterials;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ToolMaterial;

/** Create's AllToolMaterials: the cardboard sword's material. Tools made of it have no durability, like Create's. */
public final class AllToolMaterials {
	private AllToolMaterials() {}

	// Create's CARDBOARD: 0 uses, speed 1, damage bonus 2, enchantability 1
	public static final ToolMaterial CARDBOARD = new ToolMaterial(BlockTags.INCORRECT_FOR_WOODEN_TOOL, 0, 1, 2, 1,
		AllArmorMaterials.CARDBOARD_REPAIR);
}
