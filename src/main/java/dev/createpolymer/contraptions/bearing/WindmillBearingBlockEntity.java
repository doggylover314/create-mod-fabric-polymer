package dev.createpolymer.contraptions.bearing;

import java.util.List;

import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import dev.createpolymer.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import dev.createpolymer.registry.feature.BearingRegistry;
import dev.createpolymer.util.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class WindmillBearingBlockEntity extends MechanicalBearingBlockEntity {

	protected ScrollOptionBehaviour<RotationDirection> movementDirection;
	protected float lastGeneratedSpeed;

	protected boolean queuedReassembly;

	public WindmillBearingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void updateGeneratedRotation() {
		super.updateGeneratedRotation();
		lastGeneratedSpeed = getGeneratedSpeed();
		queuedReassembly = false;
	}

	@Override
	public void onSpeedChanged(float prevSpeed) {
		boolean cancelAssembly = assembleNextTick;
		super.onSpeedChanged(prevSpeed);
		assembleNextTick = cancelAssembly;
	}

	@Override
	public void tick() {
		super.tick();
		if (level.isClientSide())
			return;
		if (!queuedReassembly)
			return;
		queuedReassembly = false;
		if (!running)
			assembleNextTick = true;
	}

	public void disassembleForMovement() {
		if (!running)
			return;
		disassemble();
		queuedReassembly = true;
	}

	@Override
	public float getGeneratedSpeed() {
		if (!running)
			return 0;
		if (movedContraption == null)
			return lastGeneratedSpeed;
		int sails = ((BearingContraption) movedContraption.getContraption()).getSailBlocks()
			/ BearingRegistry.windmillSailsPerRPM;
		return Mth.clamp(sails, 1, 16) * getAngleSpeedDirection();
	}

	@Override
	protected boolean isWindmill() {
		return true;
	}

	protected float getAngleSpeedDirection() {
		RotationDirection rotationDirection = RotationDirection.values()[movementDirection.getValue()];
		return (rotationDirection == RotationDirection.CLOCKWISE ? 1 : -1);
	}

	@Override
	public void write(ValueOutput compound) {
		compound.putFloat("LastGenerated", lastGeneratedSpeed);
		compound.putBoolean("QueueAssembly", queuedReassembly);
		super.write(compound);
	}

	@Override
	protected void read(ValueInput compound) {
		if (!wasMoved)
			lastGeneratedSpeed = compound.getFloatOr("LastGenerated", 0);
		queuedReassembly = compound.getBooleanOr("QueueAssembly", false);
		super.read(compound);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		behaviours.remove(movementMode);
		movementDirection = new ScrollOptionBehaviour<>(RotationDirection.class,
			Component.translatable("create.contraptions.windmill.rotation_direction"), this, getMovementModeSlot());
		movementDirection.withCallback($ -> onDirectionChanged());
		behaviours.add(movementDirection);
	}

	private void onDirectionChanged() {
		if (!running)
			return;
		if (!level.isClientSide())
			updateGeneratedRotation();
	}

	@Override
	public boolean isWoodenTop() {
		return true;
	}

	public static enum RotationDirection implements INamedIconOptions {

		CLOCKWISE(Items.CLOCK), COUNTER_CLOCKWISE(Items.COMPASS),

		;

		private String translationKey;
		private Item icon;

		private RotationDirection(Item icon) {
			this.icon = icon;
			translationKey = "create.generic." + Lang.asId(name());
		}

		@Override
		public Item getIconItem() {
			return icon;
		}

		@Override
		public String getTranslationKey() {
			return translationKey;
		}

	}

}
