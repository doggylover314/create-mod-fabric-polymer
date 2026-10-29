package dev.createpolymer.trains.signal;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.api.contraption.transformable.TransformableBlockEntity;
import dev.createpolymer.contraptions.StructureTransform;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.trains.graph.EdgePointType;
import dev.createpolymer.trains.signal.SignalBlock.SignalType;
import dev.createpolymer.trains.track.TrackTargetingBehaviour;
import dev.createpolymer.util.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class SignalBlockEntity extends SmartBlockEntity implements TransformableBlockEntity {

	public static enum OverlayState {
		RENDER, SKIP, DUAL
	}

	public static enum SignalState {
		RED, YELLOW, GREEN, INVALID;

		public boolean isRedLight(float renderTime) {
			return this == RED || this == INVALID && renderTime % 40 < 3;
		}

		public boolean isYellowLight(float renderTime) {
			return this == YELLOW;
		}

		public boolean isGreenLight(float renderTime) {
			return this == GREEN;
		}
	}

	public TrackTargetingBehaviour<SignalBoundary> edgePoint;

	private SignalState state;
	private OverlayState overlay;
	private int switchToRedAfterTrainEntered;
	private boolean lastReportedPower;

	public SignalBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		this.state = SignalState.INVALID;
		this.overlay = OverlayState.SKIP;
		this.lastReportedPower = false;
	}

	@Override
	protected void write(ValueOutput tag) {
		super.write(tag);
		NBTHelper.writeEnum(tag, "State", state);
		NBTHelper.writeEnum(tag, "Overlay", overlay);
		tag.putBoolean("Power", lastReportedPower);
	}

	@Override
	protected void read(ValueInput tag) {
		super.read(tag);
		state = NBTHelper.readEnum(tag, "State", SignalState.class);
		overlay = NBTHelper.readEnum(tag, "Overlay", OverlayState.class);
		lastReportedPower = tag.getBooleanOr("Power", false);
	}

	@Nullable
	public SignalBoundary getSignal() {
		return edgePoint.getEdgePoint();
	}

	public boolean isPowered() {
		return state == SignalState.RED;
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		edgePoint = new TrackTargetingBehaviour<>(this, EdgePointType.SIGNAL);
		behaviours.add(edgePoint);
	}

	@Override
	public void tick() {
		super.tick();
		if (level.isClientSide())
			return;

		SignalBoundary boundary = getSignal();
		if (boundary == null) {
			enterState(SignalState.INVALID);
			setOverlay(OverlayState.RENDER);
			return;
		}

		BlockState blockState = getBlockState();

		blockState.getOptionalValue(SignalBlock.POWERED)
			.ifPresent(powered -> {
				if (lastReportedPower == powered)
					return;
				lastReportedPower = powered;
				boundary.updateBlockEntityPower(this);
				notifyUpdate();
			});

		blockState.getOptionalValue(SignalBlock.TYPE)
			.ifPresent(stateType -> {
				SignalType targetType = boundary.getTypeFor(worldPosition);
				if (stateType != targetType) {
					level.setBlock(worldPosition, blockState.setValue(SignalBlock.TYPE, targetType), Block.UPDATE_ALL);
					refreshBlockState();
				}
			});

		enterState(boundary.getStateFor(worldPosition));
		setOverlay(boundary.getOverlayFor(worldPosition));
	}

	public boolean getReportedPower() {
		return lastReportedPower;
	}

	public SignalState getState() {
		return state;
	}

	public OverlayState getOverlay() {
		return overlay;
	}

	public void setOverlay(OverlayState state) {
		if (this.overlay == state)
			return;
		this.overlay = state;
		notifyUpdate();
	}

	public void enterState(SignalState state) {
		if (switchToRedAfterTrainEntered > 0)
			switchToRedAfterTrainEntered--;
		if (this.state == state)
			return;
		if (state == SignalState.RED && switchToRedAfterTrainEntered > 0)
			return;
		this.state = state;
		switchToRedAfterTrainEntered = state == SignalState.GREEN || state == SignalState.YELLOW ? 15 : 0;
		notifyUpdate();
	}

	@Override
	public void transform(BlockEntity be, StructureTransform transform) {
		edgePoint.transform(be, transform);
	}

}
