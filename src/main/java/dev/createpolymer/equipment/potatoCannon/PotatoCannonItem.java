package dev.createpolymer.equipment.potatoCannon;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.equipment.EquipmentConfig;
import dev.createpolymer.equipment.armor.BacktankUtil;
import dev.createpolymer.equipment.zapper.ShootableGadgetItemMethods;
import dev.createpolymer.registry.feature.EquipmentRegistry;
import dev.createpolymer.util.VecHelper;
import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's PotatoCannonItem (MIT, The Create Team). Ammo is looked up in the potato projectile type
 * registry. The arm pose, the recoil animation and the ammo tooltip are client side in Create and are not ported;
 * the cannon's durability bar is vanilla's.
 */
public class PotatoCannonItem extends ProjectileWeaponItem implements PolymerItem {
	private static final Predicate<ItemStack> AMMO_PREDICATE = s -> {
		var access = EquipmentRegistry.registryAccess();
		return access != null && PotatoCannonProjectileType.getTypeForItem(access, s.getItem())
			.isPresent();
	};

	public PotatoCannonItem(Properties properties) {
		super(properties.durability(100)
			.enchantable(1));
	}

	@Nullable
	public static Ammo getAmmo(Player player, ItemStack heldStack) {
		ItemStack ammoStack = player.getProjectile(heldStack);
		if (ammoStack.isEmpty()) {
			return null;
		}

		return PotatoCannonProjectileType.getTypeForItem(player.level().registryAccess(), ammoStack.getItem())
			.map(r -> new Ammo(ammoStack, r.value()))
			.orElse(null);
	}

	@Override
	protected void shootProjectile(LivingEntity shooter, Projectile projectile, int index, float velocity, float inaccuracy, float angle, @Nullable LivingEntity target) {
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getPlayer() == null)
			return InteractionResult.PASS;
		return use(context.getLevel(), context.getPlayer(), context.getHand());
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack heldStack = player.getItemInHand(hand);
		if (ShootableGadgetItemMethods.shouldSwap(player, heldStack, hand, s -> s.getItem() instanceof PotatoCannonItem)) {
			return InteractionResult.FAIL;
		}

		Ammo ammo = getAmmo(player, heldStack);
		if (ammo == null) {
			return InteractionResult.PASS;
		}
		ItemStack ammoStack = ammo.stack();
		PotatoCannonProjectileType projectileType = ammo.type();

		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}

		Vec3 barrelPos = ShootableGadgetItemMethods.getGunBarrelVec(player, hand == InteractionHand.MAIN_HAND,
			new Vec3(.75f, -0.15f, 1.5f));
		Vec3 correction =
			ShootableGadgetItemMethods.getGunBarrelVec(player, hand == InteractionHand.MAIN_HAND, new Vec3(-.05f, 0, 0))
				.subtract(player.position()
					.add(0, player.getEyeHeight(), 0));

		Vec3 lookVec = player.getLookAngle();
		Vec3 motion = lookVec.add(correction)
			.normalize()
			.scale(2)
			.scale(projectileType.velocityMultiplier());

		float soundPitch = projectileType.soundPitch() + (level.getRandom().nextFloat() - .5f) / 4f;

		boolean spray = projectileType.split() > 1;
		Vec3 sprayBase = VecHelper.rotate(new Vec3(0, 0.1, 0), 360 * level.getRandom().nextFloat(), Axis.Z);
		float sprayChange = 360f / projectileType.split();

		ItemStack ammoStackCopy = ammoStack.copy();

		for (int i = 0; i < projectileType.split(); i++) {
			PotatoProjectileEntity projectile = EquipmentRegistry.POTATO_PROJECTILE.create(level, EntitySpawnReason.TRIGGERED);
			projectile.setItem(ammoStackCopy);
			projectile.setEnchantmentEffectsFromCannon(heldStack);

			Vec3 splitMotion = motion;
			if (spray) {
				float imperfection = 40 * (level.getRandom().nextFloat() - 0.5f);
				Vec3 sprayOffset = VecHelper.rotate(sprayBase, i * sprayChange + imperfection, Axis.Z);
				splitMotion = splitMotion.add(VecHelper.lookAt(sprayOffset, motion));
			}

			if (i != 0)
				projectile.recoveryChance = 0;

			projectile.setPos(barrelPos.x, barrelPos.y, barrelPos.z);
			projectile.setDeltaMovement(splitMotion);
			projectile.setOwner(player);
			level.addFreshEntity(projectile);
		}

		if (!player.isCreative()) {
			ammoStack.shrink(1);
			if (ammoStack.isEmpty())
				player.getInventory().removeItem(ammoStack);
		}

		if (!BacktankUtil.canAbsorbDamage(player, maxUses()))
			heldStack.hurtAndBreak(1, player, hand);

		ShootableGadgetItemMethods.applyCooldown(player, heldStack, hand, s -> s.getItem() instanceof PotatoCannonItem, projectileType.reloadTicks());
		PotatoProjectileEntity.playLaunchSound(level, barrelPos, soundPitch);
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean canDestroyBlock(ItemStack itemStack, BlockState state, Level level, BlockPos pos, LivingEntity user) {
		return false;
	}

	@Override
	public Predicate<ItemStack> getAllSupportedProjectiles() {
		return AMMO_PREDICATE;
	}

	@Override
	public int getDefaultProjectileRange() {
		return 15;
	}

	private static int maxUses() {
		return EquipmentConfig.maxPotatoCannonShots;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.NONE;
	}

	@Override
	public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
		return Items.TRIAL_KEY;
	}

	public record Ammo(ItemStack stack, PotatoCannonProjectileType type) {
	}
}
