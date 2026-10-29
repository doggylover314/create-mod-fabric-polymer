package dev.createpolymer.logistics.box;

import java.lang.ref.WeakReference;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import dev.createpolymer.logistics.chute.AbstractChuteBlock;
import dev.createpolymer.registry.feature.PackagesRegistry;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.VecHelper;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.InteractionElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Create's PackageEntity: a package lying in the world. It is a plain entity here (Create's is a LivingEntity), so
 * it can be a Polymer entity: players receive no entity for it at all, only an item display with the package's
 * model and an interaction entity for its hitbox, both owned by the {@link Model} attached to it. Clicks on the
 * hitbox are redirected to this entity, so right-click picks the package up and hitting it breaks it open.
 * Physics (gravity, friction, being pushed, resting on other packages) run here on the server.
 */
public class PackageEntity extends Entity implements PolymerEntity {

	private @Nullable Entity originalEntity;
	public ItemStack box;

	public int insertionDelay;

	public WeakReference<Player> tossedBy = new WeakReference<>(null);

	private @Nullable Model model;

	public PackageEntity(EntityType<?> entityTypeIn, Level worldIn) {
		super(entityTypeIn, worldIn);
		box = ItemStack.EMPTY;
		setYRot(this.random.nextFloat() * 360.0F);
		yRotO = getYRot();
		insertionDelay = 30;
	}

	public PackageEntity(Level worldIn, double x, double y, double z) {
		this(PackagesRegistry.PACKAGE, worldIn);
		this.setPos(x, y, z);
		this.refreshDimensions();
	}

	public static PackageEntity fromDroppedItem(Level world, Entity originalEntity, ItemStack itemstack) {
		PackageEntity packageEntity = PackagesRegistry.PACKAGE.create(world, EntitySpawnReason.LOAD);

		Vec3 position = originalEntity.position();
		packageEntity.setPos(position);
		packageEntity.setBox(itemstack);
		packageEntity.setDeltaMovement(originalEntity.getDeltaMovement()
			.scale(1.5f));
		packageEntity.originalEntity = originalEntity;

		if (world != null && !world.isClientSide())
			if (AbstractChuteBlock.isChute(world.getBlockState(BlockPos.containing(position.x, position.y + .5f, position.z))))
				packageEntity.setYRot(((int) packageEntity.getYRot()) / 90 * 90);

		return packageEntity;
	}

	public static PackageEntity fromItemStack(Level world, Vec3 position, ItemStack itemstack) {
		PackageEntity packageEntity = PackagesRegistry.PACKAGE.create(world, EntitySpawnReason.LOAD);
		packageEntity.setPos(position);
		packageEntity.setBox(itemstack);
		return packageEntity;
	}

	@Override
	public @Nullable ItemStack getPickResult() {
		return box.copy();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	/** Create's LivingEntity travel(): gravity and friction. */
	@Override
	protected double getDefaultGravity() {
		return 0.08;
	}

	public String getAddress() {
		return box.get(PackagesRegistry.PACKAGE_ADDRESS);
	}

	@Override
	public void tick() {
		if (firstTick) {
			verifyInitialEntity();
			originalEntity = null;
		}

		insertionDelay = Math.min(insertionDelay + 1, 30);
		super.tick();

		if (level().isClientSide() || isRemoved())
			return;
		if (!PackageItem.isPackage(box)) {
			discard();
			return;
		}

		if (model == null && level() instanceof ServerLevel) {
			model = new Model(this);
			EntityAttachment.ofTicking(model, this);
		}

		applyGravity();
		move(MoverType.SELF, getDeltaMovement());
		applyEffectsFromBlocks();
		if (isRemoved())
			return;

		float friction = 0.91f;
		if (onGround())
			friction *= level().getBlockState(getBlockPosBelowThatAffectsMyMovement())
				.getBlock()
				.getFriction();
		setDeltaMovement(getDeltaMovement().multiply(friction, 0.98, friction));

		// Packages are not living entities, so nothing else pushes them apart
		for (PackageEntity other : level().getEntitiesOfClass(PackageEntity.class, getBoundingBox(), e -> e != this))
			push(other);
	}

	/*
	 * Forge created package entities even when an ItemEntity is spawned as 'fake'.
	 * See: GiveCommand#giveItem. This method discards the package if it originated
	 * from such a fake item
	 */
	protected void verifyInitialEntity() {
		if (!(originalEntity instanceof ItemEntity itemEntity))
			return;
		if (itemEntity.getAge() < 5999 || !itemEntity.hasPickUpDelay()) // See: ItemEntity#makeFakeItem
			return;
		discard();
	}

	@Override
	public EntityDimensions getDimensions(Pose pose) {
		if (box == null)
			return super.getDimensions(pose);
		return EntityDimensions.fixed(PackageItem.getWidth(box), PackageItem.getHeight(box));
	}

	public ItemStack getBox() {
		return box;
	}

	public static boolean centerPackage(Entity entity, Vec3 target) {
		if (!(entity instanceof PackageEntity packageEntity))
			return true;
		return packageEntity.decreaseInsertionTimer(target);
	}

	public boolean decreaseInsertionTimer(@Nullable Vec3 targetSpot) {
		if (targetSpot != null) {
			setDeltaMovement(getDeltaMovement().scale(.75f)
				.multiply(1, .25f, 1));
			Vec3 pos = position().add(targetSpot.subtract(position())
				.scale(.2f));
			setPos(pos.x, pos.y, pos.z);
			float yawTarget = ((int) getYRot()) / 90 * 90;
			setYRot(AngleHelper.angleLerp(.5f, getYRot(), yawTarget));
		}
		insertionDelay = Math.max(insertionDelay - 3, 0);
		return insertionDelay == 0;
	}

	public void setBox(ItemStack box) {
		this.box = box.copy();
		refreshDimensions();
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean isPushable() {
		return true;
	}

	@Override
	public boolean canCollideWith(Entity pEntity) {
		return pEntity instanceof PackageEntity && pEntity.getBoundingBox().maxY < getBoundingBox().minY + .125f;
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return false;
	}

	@Override
	public InteractionResult interact(Player pPlayer, InteractionHand pHand, Vec3 location) {
		if (!pPlayer.getItemInHand(pHand)
			.isEmpty())
			return super.interact(pPlayer, pHand, location);
		if (pPlayer.level().isClientSide())
			return InteractionResult.SUCCESS;
		pPlayer.setItemInHand(pHand, box);
		level().playSound(null, blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, .2f,
			.75f + level().getRandom().nextFloat());
		remove(RemovalReason.DISCARDED);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void push(Entity entityIn) {
		boolean isOtherPackage = entityIn instanceof PackageEntity;

		if (!isOtherPackage && tossedBy.get() != null)
			tossedBy = new WeakReference<>(null); // no nudging

		if (isOtherPackage) {
			if (entityIn.getBoundingBox().minY < this.getBoundingBox().maxY)
				super.push(entityIn);
		} else if (entityIn.getBoundingBox().minY <= this.getBoundingBox().minY) {
			super.push(entityIn);
		}
	}

	@Override
	protected void onInsideBlock(BlockState state) {
		super.onInsideBlock(state);
		if (!isAlive() || !(level() instanceof ServerLevel serverLevel))
			return;
		if (state.getBlock() == Blocks.WATER || (state.hasProperty(BlockStateProperties.WATERLOGGED)
			&& state.getValue(BlockStateProperties.WATERLOGGED))) {
			destroy(serverLevel, serverLevel.damageSources().drown());
			remove(RemovalReason.KILLED);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (!this.isAlive())
			return false;

		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			this.remove(RemovalReason.KILLED);
			return false;
		}

		if (!box.canBeHurtBy(source))
			return false;

		if (source.equals(level.damageSources().inWall()) && (isPassenger() || insertionDelay < 20))
			return false;

		if (source.is(DamageTypeTags.IS_FALL))
			return false;

		if (this.isInvulnerableToBase(source))
			return false;

		if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			this.destroy(level, source);
			this.remove(RemovalReason.KILLED);
			return false;
		}

		if (source.is(DamageTypeTags.IS_FIRE)) {
			if (this.isOnFire()) {
				this.destroy(level, source);
				this.remove(RemovalReason.KILLED);
			} else {
				this.igniteForSeconds(5);
			}
			return false;
		}

		boolean wasShot = source.getDirectEntity() instanceof AbstractArrow;
		boolean shotCanPierce = wasShot && ((AbstractArrow) source.getDirectEntity()).getPierceLevel() > 0;

		if (source.getEntity() instanceof Player && !((Player) source.getEntity()).getAbilities().mayBuild)
			return false;

		this.destroy(level, source);
		this.remove(RemovalReason.KILLED);
		return shotCanPierce;
	}

	private void destroy(ServerLevel level, DamageSource source) {
		Vec3 center = getBoundingBox().getCenter();
		for (int i = 0; i < 5; i++) {
			Vec3 motion = VecHelper.offsetRandomly(Vec3.ZERO, level.getRandom(), .125f);
			Vec3 pos = center.add(motion.scale(4));
			level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.WOOL.pick(DyeColor.BROWN)), pos.x, pos.y, pos.z, 0,
				motion.x, motion.y, motion.z, 1);
		}
		PackageSounds.packagePop(level, blockPosition());
		dropAllDeathLoot(level, source);
	}

	protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
		ItemStackHandler contents = PackageItem.getContents(box);
		for (int i = 0; i < contents.getSlots(); i++) {
			ItemStack itemstack = contents.getStackInSlot(i);

			EntityType<?> entitytype = SpawnEggItem.getType(itemstack);
			if (entitytype != null) {
				Entity entity = entitytype.spawn(level, itemstack, null, blockPosition(),
					EntitySpawnReason.SPAWN_ITEM_USE, false, false);
				if (entity != null)
					itemstack.shrink(1);
			}

			if (itemstack.isEmpty())
				continue;
			ItemEntity entityIn = new ItemEntity(level, getX(), getY(), getZ(), itemstack);
			level.addFreshEntity(entityIn);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		box = input.read("Box", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		refreshDimensions();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.store("Box", ItemStack.OPTIONAL_CODEC, box);
	}

	@Override
	public boolean fireImmune() {
		return !box.canBeHurtBy(damageSources().inFire()) || super.fireImmune();
	}

	@Override
	protected MovementEmission getMovementEmission() {
		return MovementEmission.NONE;
	}

	public @Nullable Model getModel() {
		return model;
	}

	// ---- Polymer ----

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext context) {
		return EntityTypes.MARKER;
	}

	/** Players only see the display and interaction entities of the {@link Model}, never this entity itself. */
	@Override
	public boolean sendPacketsTo(ServerPlayer player) {
		return false;
	}

	/**
	 * What players see: the package's item model, and an invisible hitbox of the package's size that hands
	 * clicks to the entity. The model is drawn from the item's model with the item display context NONE, so its
	 * block-space coordinates map straight onto the entity's position.
	 */
	public static class Model extends ElementHolder {
		private final PackageEntity entity;
		private final ItemDisplayElement display;
		private final InteractionElement hitbox;
		private ItemStack shown = ItemStack.EMPTY;
		private float shownYaw = Float.NaN;
		private float shownWidth, shownHeight;

		public Model(PackageEntity entity) {
			this.entity = entity;
			display = new ItemDisplayElement(ItemStack.EMPTY);
			display.setItemDisplayContext(ItemDisplayContext.NONE);
			display.setViewRange(1f);
			display.setTeleportDuration(2);
			display.setInterpolationDuration(0);
			addElement(display);

			hitbox = new InteractionElement(VirtualElement.InteractionHandler.redirect(entity));
			addElement(hitbox);
			sync();
		}

		/** The stack the display shows: the package item without its (potentially huge) data. */
		public ItemStack getShownItem() {
			return shown;
		}

		@Override
		protected void onTick() {
			sync();
		}

		private void sync() {
			ItemStack box = entity.box;
			if (!ItemStack.isSameItem(shown, box)) {
				shown = box.isEmpty() ? ItemStack.EMPTY : new ItemStack(box.getItem());
				display.setItem(shown);
			}

			float yaw = entity.getYRot();
			if (yaw != shownYaw) {
				shownYaw = yaw;
				// Create: rotateCentered(-rad(yaw + 90), UP), with the model's feet on the entity's feet
				Matrix4f m = new Matrix4f().translate(0, .5f, 0)
					.rotate(new Quaternionf().rotationY((float) Math.toRadians(-(yaw + 90))));
				display.setTransformation(m);
				display.startInterpolation();
			}

			float width = PackageItem.getWidth(box);
			float height = PackageItem.getHeight(box);
			if (width != shownWidth || height != shownHeight) {
				shownWidth = width;
				shownHeight = height;
				hitbox.setSize(width, height);
			}
		}
	}
}
