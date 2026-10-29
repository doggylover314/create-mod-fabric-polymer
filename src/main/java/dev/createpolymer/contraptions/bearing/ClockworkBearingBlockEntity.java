package dev.createpolymer.contraptions.bearing;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import dev.createpolymer.contraptions.AbstractContraptionEntity;
import dev.createpolymer.contraptions.AssemblyException;
import dev.createpolymer.contraptions.ControlledContraptionEntity;
import dev.createpolymer.contraptions.IDisplayAssemblyExceptions;
import dev.createpolymer.contraptions.bearing.ClockworkContraption.HandType;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import dev.createpolymer.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class ClockworkBearingBlockEntity extends KineticBlockEntity
	implements IBearingBlockEntity, IDisplayAssemblyExceptions {

	protected ControlledContraptionEntity hourHand;
	protected ControlledContraptionEntity minuteHand;
	protected float hourAngle;
	protected float minuteAngle;

	protected boolean running;
	protected boolean assembleNextTick;
	protected AssemblyException lastException;
	protected ScrollOptionBehaviour<ClockHands> operationMode;

	public ClockworkBearingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		setLazyTickRate(3);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		operationMode = new ScrollOptionBehaviour<>(ClockHands.class,
			Component.translatable("create.contraptions.clockwork.clock_hands"), this, getMovementModeSlot());
		behaviours.add(operationMode);
	}

	@Override
	public boolean isWoodenTop() {
		return false;
	}

	@Override
	public void tick() {
		super.tick();

		if (assembleNextTick) {
			assembleNextTick = false;
			if (running) {
				boolean canDisassemble = true;
				if (speed == 0 && (canDisassemble || hourHand == null || hourHand.getContraption()
					.getBlocks()
					.isEmpty())) {
					if (hourHand != null)
						hourHand.getContraption()
							.stop(level);
					if (minuteHand != null)
						minuteHand.getContraption()
							.stop(level);
					disassemble();
				}
				return;
			} else
				assemble();
			return;
		}

		if (!running)
			return;

		if (!(hourHand != null && hourHand.isStalled())) {
			float newAngle = hourAngle + getHourArmSpeed();
			hourAngle = (float) (newAngle % 360);
		}

		if (!(minuteHand != null && minuteHand.isStalled())) {
			float newAngle = minuteAngle + getMinuteArmSpeed();
			minuteAngle = (float) (newAngle % 360);
		}

		applyRotations();
	}

	@Override
	public AssemblyException getLastAssemblyException() {
		return lastException;
	}

	protected void applyRotations() {
		BlockState blockState = getBlockState();
		Axis axis = Axis.X;

		if (blockState.hasProperty(BlockStateProperties.FACING))
			axis = blockState.getValue(BlockStateProperties.FACING)
				.getAxis();

		if (hourHand != null) {
			hourHand.setAngle(hourAngle);
			hourHand.setRotationAxis(axis);
		}
		if (minuteHand != null) {
			minuteHand.setAngle(minuteAngle);
			minuteHand.setRotationAxis(axis);
		}
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		if (hourHand != null)
			sendData();
	}

	public float getHourArmSpeed() {
		float speed = getAngularSpeed() / 2f;

		if (speed != 0) {
			ClockHands mode = ClockHands.values()[operationMode.getValue()];
			float hourTarget = mode == ClockHands.HOUR_FIRST ? getHourTarget(false)
				: mode == ClockHands.MINUTE_FIRST ? getMinuteTarget() : getHourTarget(true);
			float shortestAngleDiff = AngleHelper.getShortestAngleDiff(hourAngle, hourTarget);
			if (shortestAngleDiff < 0) {
				speed = Math.max(speed, shortestAngleDiff);
			} else {
				speed = Math.min(-speed, shortestAngleDiff);
			}
		}

		return speed;
	}

	public float getMinuteArmSpeed() {
		float speed = getAngularSpeed();

		if (speed != 0) {
			ClockHands mode = ClockHands.values()[operationMode.getValue()];
			float minuteTarget = mode == ClockHands.MINUTE_FIRST ? getHourTarget(false) : getMinuteTarget();
			float shortestAngleDiff = AngleHelper.getShortestAngleDiff(minuteAngle, minuteTarget);
			if (shortestAngleDiff < 0) {
				speed = Math.max(speed, shortestAngleDiff);
			} else {
				speed = Math.min(-speed, shortestAngleDiff);
			}
		}

		return speed;
	}

	/**
	 * Create reads the day time and speeds it up 24 times in dimensions that are not natural (Nether, End). 26.3 has no
	 * "natural" flag: a dimension with a default clock plays the part of a natural one, any other shows the overworld's
	 * clock at 24 times its speed.
	 */
	protected int getDayTime() {
		boolean isNatural = level.dimensionType()
			.defaultClock()
			.isPresent();
		long time = isNatural ? level.getDefaultClockTime() : level.getOverworldClockTime() * 24;
		return (int) (time % 24000);
	}

	protected float getHourTarget(boolean cycle24) {
		int dayTime = getDayTime();
		int hours = (dayTime / 1000 + 6) % 24;
		int offset = getBlockState().getValue(ClockworkBearingBlock.FACING)
			.getAxisDirection()
			.getStep();
		float hourTarget = (float) (offset * -360 / (cycle24 ? 24f : 12f) * (hours % (cycle24 ? 24 : 12)));
		return hourTarget;
	}

	protected float getMinuteTarget() {
		int dayTime = getDayTime();
		int minutes = (dayTime % 1000) * 60 / 1000;
		int offset = getBlockState().getValue(ClockworkBearingBlock.FACING)
			.getAxisDirection()
			.getStep();
		float minuteTarget = (float) (offset * -360 / 60f * (minutes));
		return minuteTarget;
	}

	public float getAngularSpeed() {
		float speed = -Math.abs(getSpeed() * 3 / 10f);
		return speed;
	}

	public void assemble() {
		if (!(level.getBlockState(worldPosition)
			.getBlock() instanceof ClockworkBearingBlock))
			return;

		Direction direction = getBlockState().getValue(BlockStateProperties.FACING);

		// Collect Construct
		Pair<ClockworkContraption, ClockworkContraption> contraption;
		try {
			contraption = ClockworkContraption.assembleClockworkAt(level, worldPosition, direction);
			lastException = null;
		} catch (AssemblyException e) {
			lastException = e;
			sendData();
			return;
		}
		if (contraption == null)
			return;
		if (contraption.getLeft() == null)
			return;
		if (contraption.getLeft()
			.getBlocks()
			.isEmpty())
			return;
		BlockPos anchor = worldPosition.relative(direction);

		contraption.getLeft()
			.removeBlocksFromWorld(level, BlockPos.ZERO);
		hourHand = ControlledContraptionEntity.create(level, this, contraption.getLeft());
		hourHand.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
		hourHand.setRotationAxis(direction.getAxis());
		level.addFreshEntity(hourHand);

		if (contraption.getRight() != null) {
			anchor = worldPosition.relative(direction, contraption.getRight().offset + 1);
			contraption.getRight()
				.removeBlocksFromWorld(level, BlockPos.ZERO);
			minuteHand = ControlledContraptionEntity.create(level, this, contraption.getRight());
			minuteHand.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
			minuteHand.setRotationAxis(direction.getAxis());
			level.addFreshEntity(minuteHand);
		}

		// Run
		running = true;
		hourAngle = 0;
		minuteAngle = 0;
		sendData();
	}

	public void disassemble() {
		if (!running && hourHand == null && minuteHand == null)
			return;

		hourAngle = 0;
		minuteAngle = 0;
		applyRotations();

		if (hourHand != null) {
			hourHand.disassemble();
		}
		if (minuteHand != null)
			minuteHand.disassemble();

		hourHand = null;
		minuteHand = null;
		running = false;
		sendData();
	}

	@Override
	public void attach(ControlledContraptionEntity contraption) {
		if (!(contraption.getContraption() instanceof ClockworkContraption cc))
			return;

		setChanged();
		Direction facing = getBlockState().getValue(BlockStateProperties.FACING);
		BlockPos anchor = worldPosition.relative(facing, cc.offset + 1);
		if (cc.handType == HandType.HOUR) {
			this.hourHand = contraption;
			hourHand.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
		} else {
			this.minuteHand = contraption;
			minuteHand.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
		}
		if (!level.isClientSide()) {
			this.running = true;
			sendData();
		}
	}

	@Override
	public void write(ValueOutput compound) {
		compound.putBoolean("Running", running);
		compound.putFloat("HourAngle", hourAngle);
		compound.putFloat("MinuteAngle", minuteAngle);
		AssemblyException.write(compound, lastException);
		super.write(compound);
	}

	@Override
	protected void read(ValueInput compound) {
		running = compound.getBooleanOr("Running", false);
		hourAngle = compound.getFloatOr("HourAngle", 0);
		minuteAngle = compound.getFloatOr("MinuteAngle", 0);
		lastException = AssemblyException.read(compound);
		super.read(compound);
	}

	@Override
	public void onSpeedChanged(float prevSpeed) {
		super.onSpeedChanged(prevSpeed);
		assembleNextTick = true;
	}

	@Override
	public boolean isValid() {
		return !isRemoved();
	}

	@Override
	public float getInterpolatedAngle(float partialTicks) {
		if (hourHand == null || hourHand.isStalled())
			partialTicks = 0;
		return Mth.lerp(partialTicks, hourAngle, hourAngle + getHourArmSpeed());
	}

	@Override
	public void onStall() {
		if (!level.isClientSide())
			sendData();
	}

	@Override
	public void remove() {
		if (!level.isClientSide())
			disassemble();
		super.remove();
	}

	@Override
	public boolean isAttachedTo(AbstractContraptionEntity contraption) {
		if (!(contraption.getContraption() instanceof ClockworkContraption cc))
			return false;
		if (cc.handType == HandType.HOUR)
			return this.hourHand == contraption;
		else
			return this.minuteHand == contraption;
	}

	public boolean isRunning() {
		return running;
	}

	public float getHourAngle() {
		return hourAngle;
	}

	public float getMinuteAngle() {
		return minuteAngle;
	}

	public static enum ClockHands implements INamedIconOptions {

		HOUR_FIRST(Items.CLOCK), MINUTE_FIRST(Items.COMPASS), HOUR_FIRST_24(Items.RECOVERY_COMPASS),

		;

		private String translationKey;
		private Item icon;

		private ClockHands(Item icon) {
			this.icon = icon;
			translationKey = "create.contraptions.clockwork." + Lang.asId(name());
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

	@Override
	public BlockPos getBlockPosition() {
		return worldPosition;
	}

	@Override
	public void setAngle(float forcedAngle) {
		hourAngle = forcedAngle;
	}
}
