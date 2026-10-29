package dev.createpolymer.processing.burner;

import dev.createpolymer.processing.burner.BlazeBurnerBlockEntity.FuelType;
import dev.createpolymer.registry.feature.BurnerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's BlazeBurnerHandler (MIT, The Create Team).
 * Fabric has no projectile impact event, so the blaze burner blocks call these from
 * {@code BlockBehaviour.onProjectileHit}: they only see projectiles that hit the burner itself, not splash potions
 * that land on a neighbouring block. A thrown egg that is eaten can still hatch a chicken, as vanilla does that
 * after the block hook.
 */
public class BlazeBurnerHandler {

	public static void thrownEggsGetEatenByBurner(Level world, BlockHitResult hit, Projectile projectile) {
		if (!(projectile instanceof ThrownEgg))
			return;

		BlockEntity blockEntity = world.getBlockEntity(hit.getBlockPos());
		if (!(blockEntity instanceof BlazeBurnerBlockEntity heater))
			return;

		projectile.setDeltaMovement(Vec3.ZERO);
		projectile.discard();

		if (world.isClientSide())
			return;

		if (!heater.isCreative()) {
			if (heater.activeFuel != FuelType.SPECIAL) {
				heater.activeFuel = FuelType.NORMAL;
				heater.remainingBurnTime =
					Mth.clamp(heater.remainingBurnTime + 80, 0, BlazeBurnerBlockEntity.MAX_HEAT_CAPACITY);
				heater.updateBlockState();
				heater.notifyUpdate();
			}
		}

		// Create's blaze_munch sound event
		world.playSound(null, heater.getBlockPos(), SoundEvents.GENERIC_EAT.value(), SoundSource.BLOCKS, .5f, 1f);
	}

	public static void splashExtinguishesBurner(Level world, BlockHitResult result, Projectile projectile) {
		if (world.isClientSide())
			return;
		if (!(projectile instanceof AbstractThrownPotion entity))
			return;

		PotionContents potionContents = entity.getItem().get(DataComponents.POTION_CONTENTS);
		if (potionContents != null && potionContents.is(Potions.WATER) && !potionContents.hasEffects()) {
			Direction face = result.getDirection();
			BlockPos pos = result.getBlockPos()
				.relative(face);

			extinguishLitBurners(world, pos, face);
			extinguishLitBurners(world, pos.relative(face.getOpposite()), face);

			for (Direction face1 : Direction.Plane.HORIZONTAL) {
				extinguishLitBurners(world, pos.relative(face1), face1);
			}
		}
	}

	private static void extinguishLitBurners(Level world, BlockPos pos, Direction direction) {
		BlockState state = world.getBlockState(pos);
		if (state.is(BurnerRegistry.LIT_BLAZE_BURNER)) {
			world.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F,
				2.6F + (world.getRandom().nextFloat() - world.getRandom().nextFloat()) * 0.8F);
			world.setBlockAndUpdate(pos, BurnerRegistry.BLAZE_BURNER.defaultBlockState());
		}
	}

}
