package dev.createpolymer.kinetics.steamEngine;

import java.lang.ref.WeakReference;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.fluids.tank.FluidTankBlockEntity;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import dev.createpolymer.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import dev.createpolymer.kinetics.base.GeneratingKineticBlockEntity;
import dev.createpolymer.kinetics.base.IRotate;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.Lang;
import dev.createpolymer.util.VecHelper;
import dev.createpolymer.visual.Orientation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class SteamEngineBlockEntity extends SmartBlockEntity {

	/** Create's WindmillBearingBlockEntity.RotationDirection, the only setting of an engine. */
	public enum RotationDirection implements INamedIconOptions {
		CLOCKWISE(Items.CLOCK), COUNTER_CLOCKWISE(Items.COMPASS);

		private final String translationKey;
		private final Item icon;

		RotationDirection(Item icon) {
			this.icon = icon;
			this.translationKey = "create.generic." + Lang.asId(name());
		}

		@Override
		public String getTranslationKey() {
			return translationKey;
		}

		@Override
		public Item getIconItem() {
			return icon;
		}
	}

	protected ScrollOptionBehaviour<RotationDirection> movementDirection;

	public WeakReference<PoweredShaftBlockEntity> target;
	public WeakReference<FluidTankBlockEntity> source;

	float prevAngle = 0;

	public SteamEngineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		source = new WeakReference<>(null);
		target = new WeakReference<>(null);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		movementDirection = new ScrollOptionBehaviour<>(RotationDirection.class,
			Component.translatable("create.contraptions.windmill.rotation_direction"), this, new SteamEngineValueBox());
		movementDirection.onlyActiveWhen(() -> {
			PoweredShaftBlockEntity shaft = getShaft();
			return shaft == null || !shaft.hasSource();
		});
		movementDirection.withCallback($ -> onDirectionChanged());
		behaviours.add(movementDirection);
	}

	private void onDirectionChanged() {
	}

	@Override
	public void tick() {
		super.tick();
		FluidTankBlockEntity tank = getTank();
		PoweredShaftBlockEntity shaft = getShaft();

		if (tank == null || shaft == null || !isValid()) {
			if (level.isClientSide())
				return;
			if (shaft == null)
				return;
			if (!shaft.getBlockPos()
				.subtract(worldPosition)
				.equals(shaft.enginePos))
				return;
			if (shaft.engineEfficiency == 0)
				return;
			Direction facing = SteamEngineBlock.getFacing(getBlockState());
			if (level.isLoaded(worldPosition.relative(facing.getOpposite())))
				shaft.update(worldPosition, 0, 0);
			return;
		}

		boolean verticalTarget = false;
		BlockState shaftState = shaft.getBlockState();
		Axis targetAxis = Axis.X;
		if (shaftState.getBlock() instanceof IRotate ir)
			targetAxis = ir.getRotationAxis(shaftState);
		verticalTarget = targetAxis == Axis.Y;

		BlockState blockState = getBlockState();
		if (!(blockState.getBlock() instanceof SteamEngineBlock))
			return;
		Direction facing = SteamEngineBlock.getFacing(blockState);
		if (facing.getAxis() == Axis.Y)
			facing = blockState.getValue(SteamEngineBlock.FACING);

		float efficiency = Mth.clamp(tank.boiler.getEngineEfficiency(tank.getTotalTankSize()), 0, 1);

		int conveyedSpeedLevel =
			efficiency == 0 ? 1 : verticalTarget ? 1 : (int) GeneratingKineticBlockEntity.convertToDirection(1, facing);
		if (targetAxis == Axis.Z)
			conveyedSpeedLevel *= -1;
		if (movementDirection.get() == RotationDirection.COUNTER_CLOCKWISE)
			conveyedSpeedLevel *= -1;

		float shaftSpeed = shaft.getTheoreticalSpeed();
		if (shaft.hasSource() && shaftSpeed != 0 && conveyedSpeedLevel != 0
			&& (shaftSpeed > 0) != (conveyedSpeedLevel > 0)) {
			movementDirection.setValue(1 - movementDirection.get()
				.ordinal());
			conveyedSpeedLevel *= -1;
		}

		shaft.update(worldPosition, conveyedSpeedLevel, efficiency);

		if (level instanceof ServerLevel serverLevel)
			spawnParticles(serverLevel);
	}

	@Override
	public void remove() {
		PoweredShaftBlockEntity shaft = getShaft();
		if (shaft != null)
			shaft.remove(worldPosition);
		super.remove();
	}

	public PoweredShaftBlockEntity getShaft() {
		PoweredShaftBlockEntity shaft = target.get();
		if (shaft == null || shaft.isRemoved() || !shaft.canBePoweredBy(worldPosition)) {
			if (shaft != null)
				target = new WeakReference<>(null);
			Direction facing = SteamEngineBlock.getFacing(getBlockState());
			BlockEntity anyShaftAt = level.getBlockEntity(worldPosition.relative(facing, 2));
			if (anyShaftAt instanceof PoweredShaftBlockEntity ps && ps.canBePoweredBy(worldPosition))
				target = new WeakReference<>(shaft = ps);
		}
		return shaft;
	}

	public FluidTankBlockEntity getTank() {
		FluidTankBlockEntity tank = source.get();
		if (tank == null || tank.isRemoved()) {
			if (tank != null)
				source = new WeakReference<>(null);
			Direction facing = SteamEngineBlock.getFacing(getBlockState());
			BlockEntity be = level.getBlockEntity(worldPosition.relative(facing.getOpposite()));
			if (be instanceof FluidTankBlockEntity tankBe)
				source = new WeakReference<>(tank = tankBe);
		}
		if (tank == null)
			return null;
		return tank.getControllerBE();
	}

	public boolean isValid() {
		Direction dir = SteamEngineBlock.getConnectedDirection(getBlockState()).getOpposite();

		Level level = getLevel();
		if (level == null)
			return false;

		return level.getBlockState(getBlockPos().relative(dir)).getBlock() instanceof dev.createpolymer.fluids.tank.FluidTankBlock;
	}

	/** A puff of steam each time the piston reaches the end of its stroke (Create does this on the client). */
	private void spawnParticles(ServerLevel level) {
		Float targetAngle = getTargetAngle();
		PoweredShaftBlockEntity ste = target.get();
		if (ste == null)
			return;
		if (!ste.isPoweredBy(worldPosition) || ste.engineEfficiency == 0)
			return;
		if (targetAngle == null)
			return;

		float angle = AngleHelper.deg(targetAngle);
		angle += (angle < 0) ? -180 + 75 : 360 - 75;
		angle %= 360;

		PoweredShaftBlockEntity shaft = getShaft();
		if (shaft == null || shaft.getSpeed() == 0)
			return;

		if (angle >= 0 && !(prevAngle > 180 && angle < 180)) {
			prevAngle = angle;
			return;
		}
		if (angle < 0 && !(prevAngle < -180 && angle > -180)) {
			prevAngle = angle;
			return;
		}

		Direction facing = SteamEngineBlock.getFacing(getBlockState());

		Vec3 offset = VecHelper.rotate(new Vec3(0, 0, 1).add(VecHelper.offsetRandomly(Vec3.ZERO, level.getRandom(), 1)
			.multiply(1, 1, 0)
			.normalize()
			.scale(.5f)), AngleHelper.verticalAngle(facing), Axis.X);
		offset = VecHelper.rotate(offset, AngleHelper.horizontalAngle(facing), Axis.Y);
		Vec3 v = offset.scale(.5f)
			.add(Vec3.atCenterOf(worldPosition));
		Vec3 m = offset.subtract(Vec3.atLowerCornerOf(facing.getUnitVec3i())
			.scale(.75f));
		SteamJetParticle.spawn(level, v.x, v.y, v.z, m.x, m.y, m.z);

		prevAngle = angle;
	}

	@Nullable
	public Float getTargetAngle() {
		return getTargetAngle(level.getGameTime());
	}

	/**
	 * The angle of the shaft as the piston sees it, at the given game time (Create: the render time of the
	 * client). Null when the engine has no shaft to move or the shaft is along the engine.
	 */
	@Nullable
	public Float getTargetAngle(long time) {
		float angle = 0;
		BlockState blockState = getBlockState();
		if (!(blockState.getBlock() instanceof SteamEngineBlock))
			return null;

		Direction facing = SteamEngineBlock.getFacing(blockState);
		PoweredShaftBlockEntity shaft = getShaft();
		Axis facingAxis = facing.getAxis();
		Axis axis = Axis.Y;

		if (shaft == null)
			return null;

		axis = ((IRotate) shaft.getBlockState().getBlock()).getRotationAxis(shaft.getBlockState());
		angle = getAngleForBe(shaft, shaft.getBlockPos(), axis, time);

		if (axis == facingAxis)
			return null;
		if (axis.isHorizontal() && (facingAxis == Axis.X ^ facing.getAxisDirection() == AxisDirection.POSITIVE))
			angle *= -1;
		if (axis == Axis.X && facing == Direction.DOWN)
			angle *= -1;
		return angle;
	}

	/** Create's KineticBlockEntityRenderer.getAngleForBe, for a game time. */
	public static float getAngleForBe(KineticBlockEntity be, BlockPos pos, Axis axis, long time) {
		float offset = Orientation.cogOffset(false, axis, pos.getX(), pos.getY(), pos.getZ()) + be.getRotationAngleOffset(axis);
		return ((time * be.getSpeed() * 3f / 10 + offset) % 360) / 180 * (float) Math.PI;
	}

}
