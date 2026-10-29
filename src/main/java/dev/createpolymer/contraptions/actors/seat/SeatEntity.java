package dev.createpolymer.contraptions.actors.seat;

import dev.createpolymer.contraptions.AbstractContraptionEntity;
import dev.createpolymer.logistics.box.PackageEntity;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's SeatEntity (MIT, The Create Team). The entity players and mobs sit on when they use a seat
 * block.
 * <p>
 * Vanilla clients see it as an invisible item display, which does not hide a rider and has no height of its own: a
 * client puts the rider's attachment point at the display's position. Create's seat entity is 0.25 x 0.35 blocks and
 * puts the rider 1/16 block higher than that, so this entity stands {@link #RIDING_OFFSET} above the block floor,
 * where a client puts the rider the same way the server does.
 */
public class SeatEntity extends Entity implements PolymerEntity {
	/** Height of the entity above the bottom of the seat block: its 0.35 passenger point plus 1/16. */
	public static final double RIDING_OFFSET = 1 / 16.0 + 0.35;

	public SeatEntity(EntityType<?> entityType, Level level) {
		super(entityType, level);
	}

	public SeatEntity(Level level) {
		this(dev.createpolymer.registry.feature.ActorsRegistry.SEAT, level);
		noPhysics = true;
	}

	public static EntityType.Builder<?> build(EntityType.Builder<?> builder) {
		@SuppressWarnings("unchecked")
		EntityType.Builder<SeatEntity> entityBuilder = (EntityType.Builder<SeatEntity>) builder;
		return entityBuilder.sized(0.25f, 0.35f)
			.clientTrackingRange(8);
	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext context) {
		return EntityTypes.ITEM_DISPLAY;
	}

	/** Positions the seat for a block: the middle of the block, {@link #RIDING_OFFSET} above its floor. */
	public void setPosForBlock(net.minecraft.core.BlockPos pos) {
		setPos(pos.getX() + .5, pos.getY() + RIDING_OFFSET, pos.getZ() + .5);
	}

	@Override
	public void setPos(double x, double y, double z) {
		super.setPos(x, y, z);
		AABB bb = getBoundingBox();
		Vec3 diff = new Vec3(x, y, z).subtract(bb.getCenter());
		setBoundingBox(bb.move(diff));
	}

	@Override
	protected void positionRider(Entity pEntity, Entity.MoveFunction pCallback) {
		if (!this.hasPassenger(pEntity))
			return;
		pCallback.accept(pEntity, this.getX(),
			this.getY() - pEntity.getVehicleAttachmentPoint(this).y + getCustomEntitySeatOffset(pEntity), this.getZ());
	}

	@Override
	public void onPassengerTurned(Entity entity) {
		entity.setYHeadRot(entity.getYRot());
	}

	public static double getCustomEntitySeatOffset(Entity entity) {
		if (entity instanceof PackageEntity)
			return 3 / 32f;
		return AbstractContraptionEntity.getCustomEntitySeatOffset(entity);
	}

	@Override
	public void setDeltaMovement(Vec3 vec) {
	}

	@Override
	public void tick() {
		if (level().isClientSide())
			return;
		boolean blockPresent = level().getBlockState(blockPosition())
			.getBlock() instanceof SeatBlock;
		if (isVehicle() && blockPresent)
			return;
		this.discard();
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return !(passenger instanceof FakePlayer) && super.canAddPassenger(passenger);
	}

	@Override
	protected boolean canRide(Entity entity) {
		// Fake Players (tested with deployers) have a BUNCH of weird issues, don't let
		// them ride seats
		return !(entity instanceof FakePlayer);
	}

	@Override
	protected void removePassenger(Entity entity) {
		super.removePassenger(entity);
		if (entity instanceof TamableAnimal ta)
			ta.setInSittingPose(false);
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity pLivingEntity) {
		// Create: above the entity's box (its floor + 0.35) plus half a block; this entity stands higher
		return new Vec3(getX(), Math.floor(getY()) + 0.35 + 0.5, getZ());
	}

	@Override
	public boolean hurtServer(net.minecraft.server.level.ServerLevel level, net.minecraft.world.damagesource.DamageSource source,
		float amount) {
		return false;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
