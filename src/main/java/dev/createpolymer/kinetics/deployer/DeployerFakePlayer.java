package dev.createpolymer.kinetics.deployer;

import java.util.UUID;

import org.apache.commons.lang3.tuple.Pair;
import org.jspecify.annotations.Nullable;

import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's DeployerFakePlayer (MIT, The Create Team), on top of Fabric's FakePlayer.
 * <p>
 * Create listens to NeoForge events for the following; here they are handled in {@link DeployerHandler} instead,
 * around the deployer's attack and use actions: entities the deployer kills put their drops (not their experience)
 * into its inventory, and attacked mobs do not retaliate according to {@link #ignoreDeployerAttacks}.
 */
public class DeployerFakePlayer extends FakePlayer {

	/** Create's CKinetics.DeployerAggroSetting. */
	public enum DeployerAggroSetting {
		ALL, CREEPERS, NONE
	}

	/** Create's config "ignoreDeployerAttacks", with its default. */
	public static DeployerAggroSetting ignoreDeployerAttacks = DeployerAggroSetting.CREEPERS;

	public static final UUID fallbackID = UUID.fromString("9e2faded-cafe-4ec2-c314-dad129ae971d");
	Pair<BlockPos, Float> blockBreakingProgress;
	ItemStack spawnedItemEffects;
	private UUID owner;
	@Nullable
	private GameProfile ownerProfile;

	public DeployerFakePlayer(ServerLevel world, @Nullable UUID owner) {
		// The profile keeps the fallback id: ServerPlayer registers its advancements and statistics under the profile
		// id when it is created, and the owner's own entries must not be taken over by a fake player.
		super(world, new GameProfile(fallbackID, "Deployer"));
		this.owner = owner;
		if (owner != null) {
			// Helps fake players get past claims and protection by other mods (Create credits Mekanism for this)
			String name = world.getServer()
				.services()
				.nameToIdCache()
				.get(owner)
				.map(NameAndId::name)
				.orElse("Deployer");
			ownerProfile = new GameProfile(owner, name);
		}
		// the eye height is only taken from the dimensions of the entity type at construction
		refreshDimensions();
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("create.block.deployer.damage_source_name");
	}

	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		return super.getDefaultDimensions(pose).withEyeHeight(0);
	}

	@Override
	public Vec3 position() {
		return new Vec3(getX(), getY(), getZ());
	}

	@Override
	public float getCurrentItemAttackStrengthDelay() {
		return 1 / 64f;
	}

	@Override
	public boolean canEat(boolean ignoreHunger) {
		return false;
	}

	@Override
	public boolean canBeAffected(MobEffectInstance pEffectInstance) {
		return false;
	}

	@Override
	public UUID getUUID() {
		return owner == null ? super.getUUID() : owner;
	}

	@Override
	public GameProfile getGameProfile() {
		return ownerProfile == null ? super.getGameProfile() : ownerProfile;
	}

	@Override
	protected boolean doesEmitEquipEvent(EquipmentSlot slot) {
		return false;
	}

	@Override
	public void remove(RemovalReason reason) {
		if (blockBreakingProgress != null && !level().isClientSide())
			level().destroyBlockProgress(getId(), blockBreakingProgress.getKey(), -1);
		super.remove(reason);
	}

	/**
	 * Create's entitiesDontRetaliate (LivingChangeTargetEvent): mobs that were attacked by a deployer forget about it.
	 */
	static void makeEntityIgnoreAttack(LivingEntity entity) {
		if (!(entity instanceof Mob mob))
			return;

		switch (ignoreDeployerAttacks) {
			case ALL -> forget(mob);
			case CREEPERS -> {
				if (mob instanceof Creeper)
					forget(mob);
			}
			case NONE -> {
			}
		}
	}

	private static void forget(Mob mob) {
		mob.setTarget(null);
		mob.setLastHurtByMob(null);
	}
}
