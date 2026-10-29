package dev.createpolymer.redstone.link;

import dev.createpolymer.logistics.factoryBoard.FactoryPanelSupportBehaviour;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class RedstoneLinkBlockEntity extends SmartBlockEntity {

	private boolean receivedSignalChanged;
	private int receivedSignal;
	private int transmittedSignal;
	private LinkBehaviour link;
	private boolean transmitter;

	public FactoryPanelSupportBehaviour panelSupport;

	public RedstoneLinkBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/**
	 * Create adds the link in addBehavioursDeferred, after the "Transmitter" flag has been read from the saved data.
	 * Here the link is made while the block entity is constructed, so the mode comes from the block state, which
	 * is the same thing {@link #tick()} checks the saved flag against.
	 */
	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		transmitter = isTransmitterBlock();
		behaviours.add(panelSupport = new FactoryPanelSupportBehaviour(this,
			() -> link != null && link.isListening(), () -> receivedSignal > 0,
			() -> ((RedstoneLinkBlock) getBlockState().getBlock()).updateTransmittedSignal(getBlockState(), level, worldPosition)));
		createLink();
		behaviours.add(link);
	}

	protected void createLink() {
		Pair<ValueBoxTransform, ValueBoxTransform> slots =
			ValueBoxTransform.Dual.makeSlots(RedstoneLinkFrequencySlot::new);
		link = transmitter ? LinkBehaviour.transmitter(this, slots, this::getSignal)
			: LinkBehaviour.receiver(this, slots, this::setSignal);
	}

	public int getSignal() {
		return transmittedSignal;
	}

	public void setSignal(int power) {
		if (receivedSignal != power)
			receivedSignalChanged = true;
		receivedSignal = power;
	}

	public void transmit(int strength) {
		transmittedSignal = strength;
		if (link != null)
			link.notifySignalChange();
	}

	@Override
	public void write(ValueOutput compound) {
		compound.putBoolean("Transmitter", transmitter);
		compound.putInt("Receive", getReceivedSignal());
		compound.putBoolean("ReceivedChanged", receivedSignalChanged);
		compound.putInt("Transmit", transmittedSignal);
		super.write(compound);
	}

	@Override
	protected void read(ValueInput compound) {
		super.read(compound);

		receivedSignal = compound.getIntOr("Receive", 0);
		receivedSignalChanged = compound.getBooleanOr("ReceivedChanged", false);
		if (level == null || !link.newPosition)
			transmittedSignal = compound.getIntOr("Transmit", 0);
	}

	@Override
	public void tick() {
		super.tick();

		if (isTransmitterBlock() != transmitter) {
			transmitter = isTransmitterBlock();
			LinkBehaviour prevlink = link;
			removeBehaviour(LinkBehaviour.TYPE);
			createLink();
			link.copyItemsFrom(prevlink);
			attachBehaviourLate(link);
		}

		if (transmitter)
			return;
		if (level.isClientSide())
			return;

		BlockState blockState = getBlockState();
		if (!(blockState.getBlock() instanceof RedstoneLinkBlock))
			return;

		if ((getReceivedSignal() > 0) != blockState.getValue(RedstoneLinkBlock.POWERED)) {
			receivedSignalChanged = true;
			level.setBlockAndUpdate(worldPosition, blockState.cycle(RedstoneLinkBlock.POWERED));
		}

		if (receivedSignalChanged) {
			updateSelfAndAttached(blockState);
		}
	}

	@Override
	public void remove() {
		super.remove();

		updateSelfAndAttached(getBlockState());
	}

	public void updateSelfAndAttached(BlockState blockState) {
		Direction attachedFace = blockState.getValue(RedstoneLinkBlock.FACING)
			.getOpposite();
		BlockPos attachedPos = worldPosition.relative(attachedFace);
		level.updateNeighborsAt(worldPosition, level.getBlockState(worldPosition)
			.getBlock());
		level.updateNeighborsAt(attachedPos, level.getBlockState(attachedPos)
			.getBlock());
		receivedSignalChanged = false;
		panelSupport.notifyPanels();
	}

	protected Boolean isTransmitterBlock() {
		return !getBlockState().getValue(RedstoneLinkBlock.RECEIVER);
	}

	public int getReceivedSignal() {
		return receivedSignal;
	}

	public LinkBehaviour getLink() {
		return link;
	}

}
