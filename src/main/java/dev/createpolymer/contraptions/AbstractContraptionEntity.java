package dev.createpolymer.contraptions;

import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.tuple.MutablePair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import dev.createpolymer.api.behaviour.movement.MovementBehaviour;
import dev.createpolymer.contraptions.behaviour.MovementContext;
import dev.createpolymer.contraptions.render.ContraptionSeats;
import dev.createpolymer.contraptions.render.ContraptionVisual;
import dev.createpolymer.foundation.collision.Matrix3d;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.VecHelper;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.data.DisplayEntityData;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's AbstractContraptionEntity. Vanilla clients see this entity as an invisible item display (a
 * "carrier") that the {@link ContraptionVisual} hangs the contraption's blocks on; see PolymerEntity below. Entities
 * sitting on its seats are passengers of this entity for the server, but ride seat proxies for clients, see
 * {@link ContraptionSeats}.
 */
public abstract class AbstractContraptionEntity extends Entity implements PolymerEntity {

	public final Map<Entity, MutableInt> collidingEntities;

	protected Contraption contraption;
	protected boolean initialized;
	protected boolean prevPosInvalid;
	private boolean skipActorStop;
	private boolean stalled;
	private UUID controlledBy;

	/** Where passengers that left a seat should land (Create keeps this in the passenger's persistent data). */
	private final Map<UUID, Vec3> dismountLocations = new HashMap<>();

	private ContraptionVisual visual;
	private EntityAttachment visualAttachment;

	/*
	 * staleTicks are a band-aid to prevent a frame or two of missing blocks between
	 * contraption discard and block placement on disassembly
	 */
	public int staleTicks = 3;

	public AbstractContraptionEntity(EntityType<?> entityTypeIn, Level worldIn) {
		super(entityTypeIn, worldIn);
		prevPosInvalid = true;
		collidingEntities = new IdentityHashMap<>();
	}

	protected void setContraption(Contraption contraption) {
		this.contraption = contraption;
		if (contraption == null)
			return;
		if (level().isClientSide())
			return;
		contraption.onEntityCreated(this);
	}

	// ---- Polymer: what vanilla clients see ----

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext context) {
		return EntityTypes.ITEM_DISPLAY;
	}

	@Override
	public void modifyRawTrackedData(List<SynchedEntityData.DataValue<?>> data, ServerPlayer player, boolean initial) {
		// the carrier is moved by position packets: let the client glide between them
		data.add(SynchedEntityData.DataValue.create(DisplayEntityData.TELEPORTATION_DURATION, 3));
	}

	/**
	 * Clients are not told that seated entities ride the carrier: they ride seat proxies (see {@link ContraptionSeats}),
	 * so the carrier's passenger lists are sent without them. This sees the lists the entity tracker sends (when the
	 * passengers change, when a client starts to track the entity, and the ones that update the block displays);
	 * the ones other code sends straight to a connection are made good by the proxies' own passenger packets.
	 */
	@Override
	public void onEntityPacketSent(Consumer<Packet<?>> consumer, Packet<?> packet) {
		if (packet instanceof ClientboundSetPassengersPacket passengers)
			ContraptionSeats.hideSeatedPassengers(this, passengers);
		consumer.accept(packet);
	}

	public ContraptionVisual getVisual() {
		return visual;
	}

	private void tickVisual() {
		if (contraption == null || !(level() instanceof ServerLevel))
			return;
		if (visual == null) {
			visual = new ContraptionVisual(this);
			visualAttachment = EntityAttachment.ofTicking(visual, this);
		}
	}

	@Override
	public void onRemoval(RemovalReason reason) {
		super.onRemoval(reason);
		if (visual != null) {
			visual.destroy();
			visual = null;
		}
	}

	@Override
	public void move(MoverType pType, Vec3 pPos) {
		if (pType == MoverType.SHULKER)
			return;
		if (pType == MoverType.SHULKER_BOX)
			return;
		if (pType == MoverType.PISTON)
			return;
		super.move(pType, pPos);
	}

	public boolean supportsTerrainCollision() {
		return contraption instanceof TranslatingContraption;
	}

	protected void contraptionInitialize() {
		contraption.onEntityInitialize(level(), this);
		initialized = true;
	}

	public boolean collisionEnabled() {
		return true;
	}

	public void registerColliding(Entity collidingEntity) {
		collidingEntities.put(collidingEntity, new MutableInt());
	}

	public void addSittingPassenger(Entity passenger, int seatIndex) {
		for (Entity entity : getPassengers()) {
			BlockPos seatOf = contraption.getSeatOf(entity.getUUID());
			if (seatOf != null && seatOf.equals(contraption.getSeats()
				.get(seatIndex))) {
				if (entity instanceof Player)
					return;
				if (!(passenger instanceof Player))
					return;
				entity.stopRiding();
			}
		}
		passenger.startRiding(this, true, true);
		if (passenger instanceof TamableAnimal ta)
			ta.setInSittingPose(true);
		if (level().isClientSide())
			return;
		contraption.getSeatMapping()
			.put(passenger.getUUID(), seatIndex);
		if (visual != null)
			visual.seatsChanged();
	}

	@Override
	protected void removePassenger(Entity passenger) {
		Vec3 transformedVector = getPassengerPosition(passenger, 1);
		super.removePassenger(passenger);
		if (passenger instanceof TamableAnimal ta)
			ta.setInSittingPose(false);
		if (level().isClientSide())
			return;
		if (transformedVector != null)
			dismountLocations.put(passenger.getUUID(), transformedVector);
		contraption.getSeatMapping()
			.remove(passenger.getUUID());
		if (visual != null)
			visual.seatsChanged();
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity entityLiving) {
		Vec3 position = super.getDismountLocationForPassenger(entityLiving);
		Vec3 stored = dismountLocations.remove(entityLiving.getUUID());
		if (stored == null)
			return position;
		entityLiving.setOnGround(false);
		return stored;
	}

	@Override
	protected void positionRider(Entity passenger, MoveFunction callback) {
		if (!hasPassenger(passenger))
			return;
		Vec3 position = getSeatedPosition(passenger, v -> toGlobalVector(v, 1));
		if (position == null)
			return;
		callback.accept(passenger, position.x, position.y, position.z);
	}

	/**
	 * Where a passenger's feet are put (see positionRider) with the contraption turned as {@code state}; null if the
	 * passenger has no place on the contraption.
	 */
	public Vec3 getSeatedPosition(Entity passenger, ContraptionRotationState state) {
		return getSeatedPosition(passenger, v -> toGlobalVector(v, state));
	}

	private Vec3 getSeatedPosition(Entity passenger, UnaryOperator<Vec3> toGlobal) {
		Vec3 transformedVector = getPassengerPosition(passenger, toGlobal);
		if (transformedVector == null)
			return null;

		float offset = -1 / 8f;
		if (passenger instanceof AbstractContraptionEntity)
			offset = 0.0f;
		return new Vec3(transformedVector.x, transformedVector.y + getCustomEntitySeatOffset(passenger) + offset,
			transformedVector.z);
	}

	/** Create's SeatEntity.getCustomEntitySeatOffset: some mobs sit lower or higher than their hitbox suggests. */
	public static double getCustomEntitySeatOffset(Entity entity) {
		String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
		return switch (id) {
			case "parrot", "cat" -> 1 / 12f;
			case "skeleton", "spider" -> 1 / 8f;
			case "wolf" -> 1 / 16f;
			case "frog" -> 1.5 / 16f;
			default -> 0;
		};
	}

	public Vec3 getPassengerPosition(Entity passenger, float partialTicks) {
		return getPassengerPosition(passenger, v -> toGlobalVector(v, partialTicks));
	}

	private Vec3 getPassengerPosition(Entity passenger, UnaryOperator<Vec3> toGlobal) {
		if (contraption == null)
			return null;

		UUID id = passenger.getUUID();
		if (passenger instanceof OrientedContraptionEntity) {
			BlockPos localPos = contraption.getBearingPosOf(id);
			if (localPos != null)
				return toGlobal.apply(VecHelper.getCenterOf(localPos))
					.add(VecHelper.getCenterOf(BlockPos.ZERO))
					.subtract(.5f, 1, .5f);
		}

		AABB bb = passenger.getBoundingBox();
		double ySize = bb.getYsize();
		BlockPos seat = contraption.getSeatOf(id);
		if (seat == null)
			return null;

		Vec3 transformedVector = toGlobal.apply(Vec3.atLowerCornerOf(seat)
			.add(.5,
				-passenger.getVehicleAttachmentPoint(this).y + ySize + .125
					- getCustomEntitySeatOffset(passenger),
				.5)).add(VecHelper.getCenterOf(BlockPos.ZERO))
				.subtract(0.5, ySize, 0.5);
		return transformedVector;
	}

	@Override
	protected boolean canAddPassenger(@NotNull Entity pPassenger) {
		if (pPassenger instanceof OrientedContraptionEntity)
			return true;
		return contraption.getSeatMapping()
			.size() < contraption.getSeats()
			.size();
	}

	public Component getContraptionName() {
		return getName();
	}

	public Optional<UUID> getControllingPlayer() {
		return Optional.ofNullable(controlledBy);
	}

	public void setControllingPlayer(@Nullable UUID playerId) {
		controlledBy = playerId;
	}

	public boolean startControlling(BlockPos controlsLocalPos, Player player) {
		return false;
	}

	public boolean control(BlockPos controlsLocalPos, Collection<Integer> heldControls, Player player) {
		return true;
	}

	public void stopControlling(BlockPos controlsLocalPos) {
		setControllingPlayer(null);
	}

	/**
	 * Create's player-to-contraption interaction packet lands here; on this port the interaction proxies of the
	 * {@link ContraptionVisual} call it.
	 */
	public boolean handlePlayerInteraction(Player player, BlockPos localPos, Direction side,
										   InteractionHand interactionHand) {
		int indexOfSeat = contraption.getSeats()
			.indexOf(localPos);
		if (indexOfSeat == -1 || player.getItemInHand(interactionHand).is(ModItems.WRENCH)) {
			if (contraption.getInteractors().containsKey(localPos))
				return contraption.getInteractors().get(localPos)
					.handlePlayerInteraction(player, interactionHand, localPos, this);
			return contraption.getStorage().handlePlayerStorageInteraction(contraption, player, localPos);
		}
		if (player.isPassenger())
			return false;

		// Eject potential existing passenger
		Entity toDismount = null;
		for (Entry<UUID, Integer> entry : contraption.getSeatMapping()
			.entrySet()) {
			if (entry.getValue() != indexOfSeat)
				continue;
			for (Entity entity : getPassengers()) {
				if (!entry.getKey()
					.equals(entity.getUUID()))
					continue;
				if (entity instanceof Player)
					return false;
				toDismount = entity;
			}
		}

		if (toDismount != null && !level().isClientSide()) {
			Vec3 transformedVector = getPassengerPosition(toDismount, 1);
			toDismount.stopRiding();
			if (transformedVector != null)
				toDismount.teleportTo(transformedVector.x, transformedVector.y, transformedVector.z);
		}

		if (level().isClientSide())
			return true;
		addSittingPassenger(player, indexOfSeat);
		return true;
	}

	public boolean canInteractWithBlock(Player player, BlockPos localPos, double distance) {
		return canInteractWithBlock(player, Vec3.atCenterOf(localPos), distance);
	}

	public boolean canInteractWithBlock(Player player, Vec3 localPos, double distance) {
		BlockPos pos = BlockPos.containing(toGlobalVector(localPos, 0));
		return player.isWithinBlockInteractionRange(pos, distance);
	}

	public Vec3 toGlobalVector(Vec3 localVec, float partialTicks) {
		return toGlobalVector(localVec, partialTicks, false);
	}

	public Vec3 toGlobalVector(Vec3 localVec, float partialTicks, boolean prevAnchor) {
		Vec3 anchor = prevAnchor ? getPrevAnchorVec() : getAnchorVec();
		Vec3 rotationOffset = VecHelper.getCenterOf(BlockPos.ZERO);
		localVec = localVec.subtract(rotationOffset);
		localVec = applyRotation(localVec, partialTicks);
		localVec = localVec.add(rotationOffset)
			.add(anchor);
		return localVec;
	}

	/** Like {@link #toGlobalVector(Vec3, float)} for the contraption turned as {@code state}. */
	public Vec3 toGlobalVector(Vec3 localVec, ContraptionRotationState state) {
		Vec3 rotationOffset = VecHelper.getCenterOf(BlockPos.ZERO);
		return state.applyTo(localVec.subtract(rotationOffset))
			.add(rotationOffset)
			.add(getAnchorVec());
	}

	public Vec3 toLocalVector(Vec3 localVec, float partialTicks) {
		return toLocalVector(localVec, partialTicks, false);
	}

	public Vec3 toLocalVector(Vec3 globalVec, float partialTicks, boolean prevAnchor) {
		Vec3 anchor = prevAnchor ? getPrevAnchorVec() : getAnchorVec();
		Vec3 rotationOffset = VecHelper.getCenterOf(BlockPos.ZERO);
		globalVec = globalVec.subtract(anchor)
			.subtract(rotationOffset);
		globalVec = reverseRotation(globalVec, partialTicks);
		globalVec = globalVec.add(rotationOffset);
		return globalVec;
	}

	@Override
	public void tick() {
		if (contraption == null) {
			discard();
			return;
		}

		collidingEntities.entrySet()
			.removeIf(e -> e.getValue()
				.incrementAndGet() > 3);

		xo = getX();
		yo = getY();
		zo = getZ();
		prevPosInvalid = false;

		if (!initialized)
			contraptionInitialize();

		contraption.tickStorage(this);
		tickContraption();
		super.tick();

		for (Entity entity : getPassengers()) {
			if (entity instanceof Player)
				continue;
			if (entity.isAlwaysTicking())
				continue;
			positionRider(entity);
		}

		tickVisual();
	}

	public void alignPassenger(Entity passenger) {
		Vec3 motion = getContactPointMotion(passenger.getEyePosition());
		if (Mth.equal(motion.length(), 0))
			return;
		if (passenger instanceof ArmorStand)
			return;
		if (!(passenger instanceof LivingEntity living))
			return;
		float prevAngle = living.getYRot();
		float angle = AngleHelper.deg(-Mth.atan2(motion.x, motion.z));
		angle = AngleHelper.angleLerp(0.4f, prevAngle, angle);
		living.setYRot(angle);
	}

	public void setBlock(BlockPos localPos, StructureBlockInfo newInfo) {
		StructureBlockInfo old = contraption.blocks.put(localPos, newInfo);
		if (old != null && old.state() != newInfo.state())
			contraption.resetClientContraption();
		contraption.invalidateColliders();
	}

	protected abstract void tickContraption();

	public abstract Vec3 applyRotation(Vec3 localPos, float partialTicks);

	public abstract Vec3 reverseRotation(Vec3 localPos, float partialTicks);

	/**
	 * Predicts the rotation {@code ticks} ticks from now, so the display glides towards where the contraption is
	 * going instead of lagging behind. Static contraptions return their current state.
	 */
	public ContraptionRotationState getPredictedRotationState(int ticks) {
		return getRotationState();
	}

	public void tickActors() {
		boolean stalledPreviously = contraption.stalled;

		if (!level().isClientSide())
			contraption.stalled = false;

		skipActorStop = true;
		for (MutablePair<StructureBlockInfo, MovementContext> pair : contraption.getActors()) {
			MovementContext context = pair.right;
			StructureBlockInfo blockInfo = pair.left;
			MovementBehaviour actor = MovementBehaviour.REGISTRY.get(blockInfo.state());

			if (actor == null)
				continue;

			Vec3 oldMotion = context.motion;
			Vec3 actorPosition = toGlobalVector(VecHelper.getCenterOf(blockInfo.pos())
				.add(actor.getActiveAreaOffset(context)), 1);
			BlockPos gridPosition = BlockPos.containing(actorPosition);
			boolean newPosVisited =
				!context.stall && shouldActorTrigger(context, blockInfo, actor, actorPosition, gridPosition);

			context.rotation = v -> applyRotation(v, 1);
			context.position = actorPosition;
			if (!isActorActive(context, actor) && !actor.mustTickWhileDisabled())
				continue;
			if (newPosVisited && !context.stall) {
				actor.visitNewPosition(context, gridPosition);
				if (!isAlive())
					break;
				context.firstMovement = false;
			}
			if (!oldMotion.equals(context.motion)) {
				actor.onSpeedChanged(context, oldMotion, context.motion);
				if (!isAlive())
					break;
			}
			actor.tick(context);
			if (!isAlive())
				break;
			contraption.stalled |= context.stall;
		}
		if (!isAlive()) {
			contraption.stop(level());
			return;
		}
		skipActorStop = false;

		for (Entity entity : getPassengers()) {
			if (!(entity instanceof OrientedContraptionEntity orientedCE))
				continue;
			if (!contraption.stabilizedSubContraptions.containsKey(entity.getUUID()))
				continue;
			if (orientedCE.contraption != null && orientedCE.contraption.stalled) {
				contraption.stalled = true;
				break;
			}
		}

		if (!stalledPreviously && contraption.stalled)
			onContraptionStalled();
		stalled = contraption.stalled;
	}

	protected boolean isActorActive(MovementContext context, MovementBehaviour actor) {
		return actor.isActive(context);
	}

	protected void onContraptionStalled() {
	}

	protected boolean shouldActorTrigger(MovementContext context, StructureBlockInfo blockInfo, MovementBehaviour actor,
										 Vec3 actorPosition, BlockPos gridPosition) {
		Vec3 previousPosition = context.position;
		if (previousPosition == null)
			return false;

		context.motion = actorPosition.subtract(previousPosition);

		Vec3 relativeMotion = context.motion;
		relativeMotion = reverseRotation(relativeMotion, 1);
		context.relativeMotion = relativeMotion;

		return !BlockPos.containing(previousPosition)
			.equals(gridPosition)
			|| (context.relativeMotion.length() > 0) && context.firstMovement;
	}

	public void move(double x, double y, double z) {
		setPos(getX() + x, getY() + y, getZ() + z);
	}

	public Vec3 getAnchorVec() {
		return position();
	}

	public Vec3 getPrevAnchorVec() {
		return getPrevPositionVec();
	}

	public float getYawOffset() {
		return 0;
	}

	@Override
	public void setPos(double x, double y, double z) {
		super.setPos(x, y, z);
		if (contraption == null)
			return;
		AABB cbox = contraption.bounds;
		if (cbox == null)
			return;
		Vec3 actualVec = getAnchorVec();
		setBoundingBox(cbox.move(actualVec));
	}

	public static float yawFromVector(Vec3 vec) {
		return (float) ((3 * Math.PI / 2 + Math.atan2(vec.z, vec.x)) / Math.PI * 180);
	}

	public static float pitchFromVector(Vec3 vec) {
		return (float) ((Math.acos(vec.y)) / Math.PI * 180);
	}

	public static EntityType.Builder<?> build(EntityType.Builder<?> builder) {
		@SuppressWarnings("unchecked")
		EntityType.Builder<AbstractContraptionEntity> entityBuilder =
			(EntityType.Builder<AbstractContraptionEntity>) builder;
		return entityBuilder.sized(1, 1)
			.clientTrackingRange(10)
			.updateInterval(1)
			.dontTrackDeltas()
			.fireImmune();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	protected final void addAdditionalSaveData(ValueOutput output) {
		CompoundTag compound = new CompoundTag();
		writeAdditional(compound, registryAccess(), false);
		output.store("ContraptionData", CompoundTag.CODEC, compound);
	}

	protected void writeAdditional(CompoundTag compound, HolderLookup.Provider registries, boolean spawnPacket) {
		if (contraption != null)
			compound.put("Contraption", contraption.writeNBT(registries, spawnPacket));
		compound.putBoolean("Stalled", isStalled());
		compound.putBoolean("Initialized", initialized);
	}

	@Override
	protected final void readAdditionalSaveData(ValueInput input) {
		input.read("ContraptionData", CompoundTag.CODEC)
			.ifPresent(compound -> readAdditional(compound, false));
	}

	protected void readAdditional(CompoundTag compound, boolean spawnData) {
		if (compound.isEmpty())
			return;

		initialized = compound.getBooleanOr("Initialized", false);
		contraption = Contraption.fromNBT(level(), compound.getCompoundOrEmpty("Contraption"), spawnData);
		contraption.entity = this;
		stalled = compound.getBooleanOr("Stalled", false);
	}

	public void disassemble() {
		if (!isAlive())
			return;
		if (contraption == null)
			return;

		StructureTransform transform = makeStructureTransform();

		contraption.stop(level());

		contraption.addBlocksToWorld(level(), transform);
		contraption.addPassengersToWorld(level(), transform, getPassengers());

		for (Entity entity : getPassengers()) {
			if (!(entity instanceof OrientedContraptionEntity))
				continue;
			UUID id = entity.getUUID();
			if (!contraption.stabilizedSubContraptions.containsKey(id))
				continue;
			BlockPos transformed = transform.apply(contraption.stabilizedSubContraptions.get(id)
				.getConnectedPos());
			entity.setPos(transformed.getX(), transformed.getY(), transformed.getZ());
			((AbstractContraptionEntity) entity).disassemble();
		}

		skipActorStop = true;
		discard();

		ejectPassengers();
		moveCollidedEntitiesOnDisassembly(transform);
		level().playSound(null, blockPosition(), SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, .5f, 1.2f);
	}

	private void moveCollidedEntitiesOnDisassembly(StructureTransform transform) {
		for (Entity entity : collidingEntities.keySet()) {
			Vec3 localVec = toLocalVector(entity.position(), 0);
			Vec3 transformed = transform.apply(localVec);
			entity.teleportTo(transformed.x, transformed.y + 1 / 16f, transformed.z);
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		if (!level().isClientSide() && !isRemoved() && contraption != null && !skipActorStop)
			contraption.stop(level());
		super.remove(reason);
	}

	protected abstract StructureTransform makeStructureTransform();

	@Override
	public void kill(ServerLevel level) {
		ejectPassengers();
		super.kill(level);
	}

	@Override
	protected void onBelowWorld() {
		ejectPassengers();
		super.onBelowWorld();
	}

	@Override
	protected void doWaterSplashEffect() {
	}

	public Contraption getContraption() {
		return contraption;
	}

	public boolean isStalled() {
		return stalled;
	}

	protected abstract float getStalledAngle();

	@Override
	// Make sure nothing can move contraptions out of the way
	public void setDeltaMovement(Vec3 motionIn) {
	}

	@Override
	public PushReaction getPistonPushReaction() {
		return PushReaction.IGNORE_ENTITY;
	}

	public void setContraptionMotion(Vec3 vec) {
		super.setDeltaMovement(vec);
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	public Vec3 getPrevPositionVec() {
		return prevPosInvalid ? position() : new Vec3(xo, yo, zo);
	}

	public abstract ContraptionRotationState getRotationState();

	public Vec3 getContactPointMotion(Vec3 globalContactPoint) {
		if (prevPosInvalid)
			return Vec3.ZERO;

		Vec3 contactPoint = toGlobalVector(toLocalVector(globalContactPoint, 0, true), 1, true);
		Vec3 contraptionLocalMovement = contactPoint.subtract(globalContactPoint);
		Vec3 contraptionAnchorMovement = position().subtract(getPrevPositionVec());
		return contraptionLocalMovement.add(contraptionAnchorMovement);
	}

	public boolean canCollideWith(Entity e) {
		if (e instanceof Player && e.isSpectator())
			return false;
		if (e.noPhysics)
			return false;
		if (e instanceof HangingEntity)
			return false;
		if (e instanceof AbstractMinecart)
			return !contraption.isMountedOnCart();
		if (e instanceof dev.createpolymer.contraptions.glue.SuperGlueEntity)
			return false;
		if (e instanceof Projectile)
			return false;
		if (e.getVehicle() != null)
			return false;

		Entity riding = this.getVehicle();
		while (riding != null) {
			if (riding == e)
				return false;
			riding = riding.getVehicle();
		}

		return e.getPistonPushReaction() == PushReaction.PUSH_PULL;
	}

	@Override
	public boolean hasExactlyOnePlayerPassenger() {
		return false;
	}

	public static class ContraptionRotationState {
		public static final ContraptionRotationState NONE = new ContraptionRotationState();

		public float xRotation = 0;
		public float yRotation = 0;
		public float zRotation = 0;
		public float secondYRotation = 0;

		Matrix3d matrix;

		public Matrix3d asMatrix() {
			if (matrix != null)
				return matrix;

			matrix = new Matrix3d().asIdentity();
			if (xRotation != 0)
				matrix.multiply(new Matrix3d().asXRotation(AngleHelper.rad(-xRotation)));
			if (yRotation != 0)
				matrix.multiply(new Matrix3d().asYRotation(AngleHelper.rad(-yRotation)));
			if (zRotation != 0)
				matrix.multiply(new Matrix3d().asZRotation(AngleHelper.rad(-zRotation)));
			return matrix;
		}

		/** Contraption-local direction to world direction (the inverse of what the collider does to entities). */
		public Vec3 applyTo(Vec3 localVec) {
			return VecHelper.rotate(asMatrix().transformTransposed(localVec), getYawOffset(), net.minecraft.core.Direction.Axis.Y);
		}

		public boolean hasVerticalRotation() {
			return xRotation != 0 || zRotation != 0;
		}

		public float getYawOffset() {
			return secondYRotation;
		}

	}

	@Override
	public void igniteForTicks(int ticks) {
		// Contraptions no longer catch fire
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	// Contraptions shouldn't activate pressure plates and tripwires
	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	public boolean isReadyForRender() {
		return initialized;
	}

	public boolean isAliveOrStale() {
		return isAlive() || staleTicks > 0;
	}

	public boolean isPrevPosInvalid() {
		return prevPosInvalid;
	}
}
