package dev.createpolymer.equipment.potatoCannon;

import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.equipment.EquipmentSounds;
import dev.createpolymer.equipment.potatoCannon.AllPotatoProjectileRenderModes.StuckToEntity;
import dev.createpolymer.registry.feature.EquipmentRegistry;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's PotatoProjectileEntity (MIT, The Create Team). The projectile is a Polymer entity: clients see
 * an item display holding the ammo item (a marker entity stands in for the real one), which render modes tilt and spin
 * each tick the way Create's renderer does.
 */
public class PotatoProjectileEntity extends AbstractHurtingProjectile implements PolymerEntity {

	protected @Nullable PotatoCannonProjectileType type;
	protected ItemStack stack = ItemStack.EMPTY;

	protected @Nullable Entity stuckEntity;
	protected Vec3 stuckOffset = Vec3.ZERO;
	protected @Nullable PotatoProjectileRenderMode stuckRenderer;
	protected double stuckFallSpeed;

	protected float additionalDamageMult = 1;
	protected float additionalKnockback = 0;
	protected float recoveryChance = 0;

	private final ElementHolder holder = new ElementHolder();
	private final ItemDisplayElement display = new ItemDisplayElement();

	public PotatoProjectileEntity(EntityType<? extends AbstractHurtingProjectile> type, Level level) {
		super(type, level);
		// Create's projectile is not accelerated by its own thrust, only by gravity and drag
		this.accelerationPower = 0;
		display.setItemDisplayContext(ItemDisplayContext.GROUND);
		display.setInterpolationDuration(1);
		display.setTeleportDuration(1);
		display.setViewRange(1f);
		holder.addElement(display);
		EntityAttachment.ofTicking(holder, this);
	}

	public void setItem(ItemStack stack) {
		this.stack = stack;
		display.setItem(stack);
		type = PotatoCannonProjectileType.getTypeForItem(level().registryAccess(), stack.getItem())
			.orElseGet(() -> level().registryAccess()
				.lookupOrThrow(EquipmentRegistry.POTATO_PROJECTILE_TYPE)
				.getOrThrow(AllPotatoProjectileTypes.FALLBACK))
			.value();
	}

	public void setEnchantmentEffectsFromCannon(ItemStack cannon) {
		int recovery = EquipmentRegistry.enchantmentLevel(level(), cannon, EquipmentRegistry.POTATO_RECOVERY);
		int power = EquipmentRegistry.enchantmentLevel(level(), cannon, net.minecraft.world.item.enchantment.Enchantments.POWER);
		int punch = EquipmentRegistry.enchantmentLevel(level(), cannon, net.minecraft.world.item.enchantment.Enchantments.PUNCH);

		if (recovery > 0)
			recoveryChance = .125f + recovery * .125f;
		additionalDamageMult = 1 + power * .2f;
		additionalKnockback = punch * .5f;
	}

	public ItemStack getItem() {
		return stack;
	}

	public PotatoCannonProjectileType getProjectileType() {
		if (type == null)
			setItem(stack);
		return type;
	}

	@Override
	public void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setItem(input.read("Item", ItemStack.OPTIONAL_CODEC)
			.orElse(ItemStack.EMPTY));
		additionalDamageMult = input.getFloatOr("AdditionalDamage", 1);
		additionalKnockback = input.getFloatOr("AdditionalKnockback", 0);
		recoveryChance = input.getFloatOr("Recovery", 0);
	}

	@Override
	public void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.store("Item", ItemStack.OPTIONAL_CODEC, stack);
		output.putFloat("AdditionalDamage", additionalDamageMult);
		output.putFloat("AdditionalKnockback", additionalKnockback);
		output.putFloat("Recovery", recoveryChance);
	}

	@Nullable
	public Entity getStuckEntity() {
		if (stuckEntity == null)
			return null;
		if (!stuckEntity.isAlive())
			return null;
		return stuckEntity;
	}

	public void setStuckEntity(Entity stuckEntity) {
		this.stuckEntity = stuckEntity;
		this.stuckOffset = position().subtract(stuckEntity.position());
		this.stuckRenderer = new StuckToEntity(stuckOffset);
		this.stuckFallSpeed = 0.0;
		setDeltaMovement(Vec3.ZERO);
	}

	public PotatoProjectileRenderMode getRenderMode() {
		if (getStuckEntity() != null && stuckRenderer != null)
			return stuckRenderer;

		return getProjectileType().renderMode();
	}

	@Override
	public void tick() {
		PotatoCannonProjectileType type = getProjectileType();
		Entity stuckEntity = getStuckEntity();
		if (stuckEntity != null) {
			if (getY() < stuckEntity.getY() - 0.1) {
				pop(position());
				kill((ServerLevel) level());
			} else {
				stuckFallSpeed += 0.007 * type.gravityMultiplier();
				stuckOffset = stuckOffset.add(0, -stuckFallSpeed, 0);
				Vec3 pos = stuckEntity.position()
					.add(stuckOffset);
				setPos(pos.x, pos.y, pos.z);
			}
		} else {
			setDeltaMovement(getDeltaMovement().add(0, -0.05 * type.gravityMultiplier(), 0)
				.scale(type.drag()));
		}

		super.tick();

		if (!level().isClientSide() && isAlive())
			updateDisplay();
	}

	private void updateDisplay() {
		PotatoProjectileRenderMode mode = getRenderMode();
		display.setBillboardMode(mode.billboard());
		Quaternionf rotation = mode.rotation(this);
		display.setLeftRotation(rotation);
		display.startInterpolationIfDirty();
	}

	@Override
	protected float getInertia() {
		return 1;
	}

	@Override
	protected net.minecraft.core.particles.ParticleOptions getTrailParticle() {
		// Create's air particle trail is sent to clients through the level's particle system, which has no server side
		return null;
	}

	@Override
	protected boolean shouldBurn() {
		return false;
	}

	@Override
	protected void onHitEntity(EntityHitResult ray) {
		super.onHitEntity(ray);

		if (getStuckEntity() != null)
			return;
		if (!(level() instanceof ServerLevel serverLevel))
			return;

		PotatoCannonProjectileType type = getProjectileType();
		Vec3 hit = ray.getLocation();
		Entity target = ray.getEntity();
		float damage = type.damage() * additionalDamageMult;
		float knockback = type.knockback() + additionalKnockback;
		Entity owner = this.getOwner();

		if (!target.isAlive())
			return;
		if (owner instanceof LivingEntity livingEntity)
			livingEntity.setLastHurtMob(target);

		if (target instanceof PotatoProjectileEntity ppe) {
			if (tickCount < 10 && target.tickCount < 10)
				return;
		}

		pop(hit);

		if (target instanceof WitherBoss && ((WitherBoss) target).isPowered())
			return;
		if (type.preEntityHit(stack, ray))
			return;

		boolean targetIsEnderman = target.getType() == EntityTypes.ENDERMAN;
		int k = target.getRemainingFireTicks();
		if (this.isOnFire() && !targetIsEnderman)
			target.igniteForSeconds(5);

		DamageSource damageSource = causePotatoDamage();
		if (!target.hurtServer(serverLevel, damageSource, damage)) {
			target.setRemainingFireTicks(k);
			kill(serverLevel);
			return;
		}

		if (targetIsEnderman)
			return;

		if (!type.onEntityHit(stack, ray)) {
			if (random.nextDouble() <= recoveryChance) {
				recoverItem();
			} else {
				spawnAtLocation(serverLevel, type.dropStack());
			}
		}

		if (!(target instanceof LivingEntity livingentity)) {
			playHitSound(level(), position());
			kill(serverLevel);
			return;
		}

		if (type.reloadTicks() < 10)
			livingentity.setInvulnerableTime(type.reloadTicks() + 10);

		if (knockback > 0) {
			Vec3 appliedMotion = this.getDeltaMovement()
				.multiply(1.0D, 0.0D, 1.0D)
				.normalize();
			if (appliedMotion.lengthSqr() > 0.0D)
				livingentity.knockback(knockback * 0.6, -appliedMotion.x, -appliedMotion.z, damageSource, damage);
		}

		if (owner instanceof LivingEntity) {
			EnchantmentHelper.doPostAttackEffects(serverLevel, livingentity, damageSource);
		}

		if (livingentity != owner && livingentity instanceof Player && owner instanceof ServerPlayer
			&& !this.isSilent()) {
			((ServerPlayer) owner).connection
				.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.PLAY_ARROW_HIT_SOUND, 0.0F));
		}

		if (type.sticky() && target.isAlive()) {
			setStuckEntity(target);
		} else {
			kill(serverLevel);
		}

	}

	private void recoverItem() {
		if (!stack.isEmpty() && level() instanceof ServerLevel serverLevel)
			spawnAtLocation(serverLevel, stack.copyWithCount(1));
	}

	public static void playHitSound(Level world, Vec3 location) {
		EquipmentSounds.potatoHit(world, net.minecraft.core.BlockPos.containing(location));
	}

	public static void playLaunchSound(Level world, Vec3 location, float pitch) {
		EquipmentSounds.fwoomp(world, location, 1, pitch);
	}

	@Override
	protected void onHitBlock(BlockHitResult ray) {
		if (!(level() instanceof ServerLevel serverLevel))
			return;
		PotatoCannonProjectileType type = getProjectileType();
		Vec3 hit = ray.getLocation();
		pop(hit);
		if (!type.onBlockHit(level(), stack, ray)) {
			if (random.nextDouble() <= recoveryChance) {
				recoverItem();
			} else {
				spawnAtLocation(serverLevel, type.dropStack());
			}
		}

		super.onHitBlock(ray);
		kill(serverLevel);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amt) {
		if (source.is(DamageTypeTags.IS_FIRE))
			return false;
		if (this.isInvulnerableToBase(source))
			return false;
		pop(position());
		kill(level);
		return true;
	}

	private void pop(Vec3 hit) {
		if (level() instanceof ServerLevel serverLevel) {
			if (!stack.isEmpty()) {
				serverLevel.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(stack)),
					hit.x, hit.y, hit.z, 7, .25, .25, .25, 0.05);
			}
			playHitSound(level(), position());
		}
	}

	private DamageSource causePotatoDamage() {
		Holder<net.minecraft.world.damagesource.DamageType> holder = level().registryAccess()
			.lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
			.getOrThrow(EquipmentRegistry.POTATO_CANNON_DAMAGE);
		return new DamageSource(holder, this, getOwner());
	}

	public static EntityType.Builder<PotatoProjectileEntity> build(EntityType.Builder<PotatoProjectileEntity> builder) {
		return builder.sized(.25f, .25f);
	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext context) {
		return EntityTypes.MARKER;
	}

	@Override
	public void remove(RemovalReason reason) {
		super.remove(reason);
		holder.destroy();
	}
}
