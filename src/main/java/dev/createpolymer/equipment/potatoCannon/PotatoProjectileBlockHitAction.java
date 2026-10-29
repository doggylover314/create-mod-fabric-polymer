package dev.createpolymer.equipment.potatoCannon;

import java.util.function.Function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import dev.createpolymer.registry.feature.EquipmentRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.BlockHitResult;

/** Ported from Create's PotatoProjectileBlockHitAction (MIT, The Create Team). */
public interface PotatoProjectileBlockHitAction {
	Codec<PotatoProjectileBlockHitAction> CODEC = EquipmentRegistry.POTATO_PROJECTILE_BLOCK_HIT_ACTION.byNameCodec()
		.dispatch(PotatoProjectileBlockHitAction::codec, Function.identity());

	boolean execute(LevelAccessor level, ItemStack projectile, BlockHitResult ray);

	MapCodec<? extends PotatoProjectileBlockHitAction> codec();
}
