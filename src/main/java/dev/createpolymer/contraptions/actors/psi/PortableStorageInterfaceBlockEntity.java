package dev.createpolymer.contraptions.actors.psi;

import org.apache.commons.lang3.tuple.MutablePair;

import dev.createpolymer.api.behaviour.movement.MovementBehaviour;
import dev.createpolymer.contraptions.AbstractContraptionEntity;
import dev.createpolymer.contraptions.Contraption;
import dev.createpolymer.contraptions.behaviour.MovementContext;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.registry.feature.ActorsRegistry;
import dev.createpolymer.util.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * Ported from Create's PortableStorageInterfaceBlockEntity (MIT, The Create Team). The animation values are kept for
 * the display model, which reads them each tick.
 */
public abstract class PortableStorageInterfaceBlockEntity extends SmartBlockEntity {

	public static final int ANIMATION = 4;
	protected int transferTimer;
	protected float distance;
	protected LerpedFloat connectionAnimation;
	protected boolean powered;
	protected Entity connectedEntity;

	public int keepAlive = 0;

	public PortableStorageInterfaceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		transferTimer = 0;
		connectionAnimation = LerpedFloat.linear()
			.startWithValue(0);
		powered = false;
	}

	public void startTransferringTo(Contraption contraption, float distance) {
		if (connectedEntity == contraption.entity)
			return;
		this.distance = Math.min(2, distance);
		connectedEntity = contraption.entity;
		startConnecting();
		notifyUpdate();
	}

	protected void stopTransferring() {
		connectedEntity = null;
		level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
	}

	public boolean canTransfer() {
		if (connectedEntity != null && !connectedEntity.isAlive())
			stopTransferring();
		return connectedEntity != null && isConnected();
	}

	@Override
	public void initialize() {
		super.initialize();
		powered = level.hasNeighborSignal(worldPosition);
		if (!powered)
			notifyContraptions();
	}

	protected abstract void invalidateCapability();

	@Override
	public void tick() {
		super.tick();
		boolean wasConnected = isConnected();
		int timeUnit = getTransferTimeout();
		int animation = ANIMATION;

		if (keepAlive > 0) {
			keepAlive--;
			if (keepAlive == 0 && !level.isClientSide()) {
				stopTransferring();
				transferTimer = ANIMATION - 1;
				sendData();
				return;
			}
		}

		transferTimer = Math.min(transferTimer, ANIMATION * 2 + timeUnit);

		boolean timerCanDecrement = transferTimer > ANIMATION || transferTimer > 0 && keepAlive == 0;

		if (timerCanDecrement) {
			transferTimer--;
			if (transferTimer == ANIMATION - 1)
				sendData();
			if (transferTimer <= 0 || powered)
				stopTransferring();
		}

		boolean isConnected = isConnected();
		if (wasConnected != isConnected)
			setChanged();

		float progress = 0;
		if (isConnected)
			progress = 1;
		else if (transferTimer >= timeUnit + animation)
			progress = Mth.lerp((transferTimer - timeUnit - animation) / (float) animation, 1, 0);
		else if (transferTimer < animation)
			progress = Mth.lerp(transferTimer / (float) animation, 0, 1);
		connectionAnimation.setValue(progress);
	}

	@Override
	public void invalidate() {
		super.invalidate();
		invalidateCapability();
	}

	@Override
	protected void read(ValueInput compound) {
		super.read(compound);
		transferTimer = compound.getIntOr("Timer", 0);
		distance = compound.getFloatOr("Distance", 0);
		powered = compound.getBooleanOr("Powered", false);
	}

	@Override
	protected void write(ValueOutput compound) {
		super.write(compound);
		compound.putInt("Timer", transferTimer);
		compound.putFloat("Distance", distance);
		compound.putBoolean("Powered", powered);
	}

	public void neighbourChanged() {
		boolean isBlockPowered = level.hasNeighborSignal(worldPosition);
		if (isBlockPowered == powered)
			return;
		powered = isBlockPowered;
		if (!powered)
			notifyContraptions();
		if (powered)
			stopTransferring();
		sendData();
	}

	private void notifyContraptions() {
		level.getEntitiesOfClass(AbstractContraptionEntity.class, new AABB(worldPosition).inflate(3))
			.forEach(PortableStorageInterfaceBlockEntity::refreshPSIs);
	}

	/** Create's AbstractContraptionEntity.refreshPSIs: the interfaces of the contraption look for this one again. */
	public static void refreshPSIs(AbstractContraptionEntity contraptionEntity) {
		Contraption contraption = contraptionEntity.getContraption();
		if (contraption == null)
			return;
		for (MutablePair<StructureBlockInfo, MovementContext> pair : contraption.getActors()) {
			MovementContext context = pair.right;
			StructureBlockInfo blockInfo = pair.left;
			MovementBehaviour actor = MovementBehaviour.REGISTRY.get(blockInfo.state());
			if (actor instanceof PortableStorageInterfaceMovement && context != null && actor.isActive(context))
				if (context.position != null)
					actor.visitNewPosition(context, BlockPos.containing(context.position));
		}
	}

	public boolean isPowered() {
		return powered;
	}

	public boolean isTransferring() {
		return transferTimer > ANIMATION;
	}

	boolean isConnected() {
		int timeUnit = getTransferTimeout();
		return transferTimer >= ANIMATION && transferTimer <= timeUnit + ANIMATION;
	}

	float getExtensionDistance(float partialTicks) {
		return (float) (Math.pow(connectionAnimation.getValue(partialTicks), 2) * distance / 2);
	}

	float getConnectionDistance() {
		return distance;
	}

	public void startConnecting() {
		transferTimer = 6 + ANIMATION * 2;
	}

	public void onContentTransferred() {
		int timeUnit = getTransferTimeout();
		transferTimer = timeUnit + ANIMATION;
		sendData();
	}

	protected Integer getTransferTimeout() {
		return ActorsRegistry.psiTimeout;
	}

}
