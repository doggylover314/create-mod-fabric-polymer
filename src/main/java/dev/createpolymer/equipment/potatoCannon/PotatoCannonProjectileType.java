package dev.createpolymer.equipment.potatoCannon;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.createpolymer.equipment.potatoCannon.AllPotatoProjectileRenderModes.Billboard;
import dev.createpolymer.equipment.potatoCannon.PotatoProjectileEntityHitAction.Type;
import dev.createpolymer.registry.feature.EquipmentRegistry;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.codec.RegistryCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Ported from Create's PotatoCannonProjectileType (MIT, The Create Team). The types are a data pack registry under
 * {@code data/<namespace>/create/potato_projectile_type}, as in Create, so packs can add ammo.
 */
public record PotatoCannonProjectileType(HolderSet<Item> items, int reloadTicks, int damage, int split, float knockback,
										 float drag, float velocityMultiplier, float gravityMultiplier,
										 float soundPitch, boolean sticky, Optional<ItemStackTemplate> dropStackTemplate,
										 PotatoProjectileRenderMode renderMode,
										 Optional<PotatoProjectileEntityHitAction> preEntityHit,
										 Optional<PotatoProjectileEntityHitAction> onEntityHit,
										 Optional<PotatoProjectileBlockHitAction> onBlockHit) {
	public static final Codec<PotatoCannonProjectileType> CODEC = RecordCodecBuilder.create(i -> i.group(
		RegistryCodecs.holderSet(Registries.ITEM).fieldOf("items").forGetter(PotatoCannonProjectileType::items),
		Codec.INT.optionalFieldOf("reload_ticks", 10).forGetter(PotatoCannonProjectileType::reloadTicks),
		Codec.INT.optionalFieldOf("damage", 1).forGetter(PotatoCannonProjectileType::damage),
		Codec.INT.optionalFieldOf("split", 1).forGetter(PotatoCannonProjectileType::split),
		Codec.FLOAT.optionalFieldOf("knockback", 1f).forGetter(PotatoCannonProjectileType::knockback),
		Codec.FLOAT.optionalFieldOf("drag", .99f).forGetter(PotatoCannonProjectileType::drag),
		Codec.FLOAT.optionalFieldOf("velocity_multiplier", 1f).forGetter(PotatoCannonProjectileType::velocityMultiplier),
		Codec.FLOAT.optionalFieldOf("gravity_multiplier", 1f).forGetter(PotatoCannonProjectileType::gravityMultiplier),
		Codec.FLOAT.optionalFieldOf("sound_pitch", 1f).forGetter(PotatoCannonProjectileType::soundPitch),
		Codec.BOOL.optionalFieldOf("sticky", false).forGetter(PotatoCannonProjectileType::sticky),
		ItemStackTemplate.CODEC.optionalFieldOf("drop_stack").forGetter(PotatoCannonProjectileType::dropStackTemplate),
		PotatoProjectileRenderMode.CODEC.optionalFieldOf("render_mode", Billboard.INSTANCE).forGetter(PotatoCannonProjectileType::renderMode),
		PotatoProjectileEntityHitAction.CODEC.optionalFieldOf("pre_entity_hit").forGetter(p -> p.preEntityHit),
		PotatoProjectileEntityHitAction.CODEC.optionalFieldOf("on_entity_hit").forGetter(p -> p.onEntityHit),
		PotatoProjectileBlockHitAction.CODEC.optionalFieldOf("on_block_hit").forGetter(p -> p.onBlockHit)
	).apply(i, PotatoCannonProjectileType::new));

	public static Optional<Reference<PotatoCannonProjectileType>> getTypeForItem(RegistryAccess registryAccess, Item item) {
		// Cache this if it causes performance issues, but it probably won't
		return registryAccess.lookupOrThrow(EquipmentRegistry.POTATO_PROJECTILE_TYPE)
			.listElements()
			.filter(ref -> ref.value().items.contains(item.builtInRegistryHolder()))
			.findFirst();
	}

	public boolean preEntityHit(ItemStack stack, EntityHitResult ray) {
		return preEntityHit.map(i -> i.execute(stack, ray, Type.PRE_HIT)).orElse(false);
	}

	public boolean onEntityHit(ItemStack stack, EntityHitResult ray) {
		return onEntityHit.map(i -> i.execute(stack, ray, Type.ON_HIT)).orElse(false);
	}

	public boolean onBlockHit(LevelAccessor level, ItemStack stack, BlockHitResult ray) {
		return onBlockHit.map(i -> i.execute(level, stack, ray)).orElse(false);
	}

	/** A new stack each time, so it's not mutated and lost */
	public ItemStack dropStack() {
		return dropStackTemplate.map(ItemStackTemplate::create)
			.orElse(ItemStack.EMPTY);
	}
}
