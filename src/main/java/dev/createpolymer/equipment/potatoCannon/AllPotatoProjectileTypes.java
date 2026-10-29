package dev.createpolymer.equipment.potatoCannon;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.registry.feature.EquipmentRegistry;
import net.minecraft.resources.ResourceKey;

/**
 * Ported from Create's AllPotatoProjectileTypes (MIT, The Create Team). Create generates the types from code; here
 * they are the JSON files in {@code data/create/create/potato_projectile_type}, written by
 * {@code tools/asset-scripts/make_equipment_assets.py} from the same values.
 */
public class AllPotatoProjectileTypes {
	public static final ResourceKey<PotatoCannonProjectileType> FALLBACK =
		ResourceKey.create(EquipmentRegistry.POTATO_PROJECTILE_TYPE, CreatePolymer.id("fallback"));
}
