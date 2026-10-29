package dev.createpolymer.kinetics.speedController;

import java.util.List;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.Config;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.createpolymer.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.createpolymer.kinetics.RotationPropagator;
import dev.createpolymer.kinetics.base.ICogWheel;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import dev.createpolymer.util.Lang;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Ported from Create's SpeedControllerBlockEntity (MIT, The Create Team). The ComputerCraft peripheral is not ported. */
public class SpeedControllerBlockEntity extends KineticBlockEntity {

	public static final int DEFAULT_SPEED = 16;
	public ScrollValueBehaviour targetSpeed;

	/** Create only sets this on the client, from the cogwheel above. Here the server keeps it up to date for the model. */
	boolean hasBracket;

	public SpeedControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		hasBracket = false;
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		updateBracket();
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		int max = Config.maxRotationSpeed;

		targetSpeed = new KineticScrollValueBehaviour(Lang.builder(CreatePolymer.MOD_ID).translate("kinetics.speed_controller.rotation_speed").component(),
			this, new ControllerValueBoxTransform());
		targetSpeed.between(-max, max);
		targetSpeed.value = DEFAULT_SPEED;
		targetSpeed.withCallback(i -> this.updateTargetRotation());
		behaviours.add(targetSpeed);
	}

	private void updateTargetRotation() {
		if (hasNetwork())
			getOrCreateNetwork().remove(this);
		RotationPropagator.handleRemoved(level, worldPosition, this);
		removeSource();
		attachKinetics();
	}

	public static float getConveyedSpeed(KineticBlockEntity cogWheel, KineticBlockEntity speedControllerIn,
		boolean targetingController) {
		if (!(speedControllerIn instanceof SpeedControllerBlockEntity))
			return 0;

		float speed = speedControllerIn.getTheoreticalSpeed();
		float wheelSpeed = cogWheel.getTheoreticalSpeed();
		float desiredOutputSpeed = getDesiredOutputSpeed(cogWheel, speedControllerIn, targetingController);

		float compareSpeed = targetingController ? speed : wheelSpeed;
		if (desiredOutputSpeed >= 0 && compareSpeed >= 0)
			return Math.max(desiredOutputSpeed, compareSpeed);
		if (desiredOutputSpeed < 0 && compareSpeed < 0)
			return Math.min(desiredOutputSpeed, compareSpeed);

		return desiredOutputSpeed;
	}

	public static float getDesiredOutputSpeed(KineticBlockEntity cogWheel, KineticBlockEntity speedControllerIn,
		boolean targetingController) {
		SpeedControllerBlockEntity speedController = (SpeedControllerBlockEntity) speedControllerIn;
		float targetSpeed = speedController.targetSpeed.getValue();
		float speed = speedControllerIn.getTheoreticalSpeed();
		float wheelSpeed = cogWheel.getTheoreticalSpeed();

		if (targetSpeed == 0)
			return 0;
		if (targetingController && wheelSpeed == 0)
			return 0;
		if (!speedController.hasSource()) {
			if (targetingController)
				return targetSpeed;
			return 0;
		}

		boolean wheelPowersController = speedController.source.equals(cogWheel.getBlockPos());

		if (wheelPowersController) {
			if (targetingController)
				return targetSpeed;
			return wheelSpeed;
		}

		if (targetingController)
			return speed;
		return targetSpeed;
	}

	public void updateBracket() {
		hasBracket = isCogwheelPresent();
	}

	public boolean hasBracket() {
		return hasBracket;
	}

	private boolean isCogwheelPresent() {
		BlockState stateAbove = level.getBlockState(worldPosition.above());
		return ICogWheel.isDedicatedCogWheel(stateAbove.getBlock()) && ICogWheel.isLargeCog(stateAbove)
			&& stateAbove.getValue(BlockStateProperties.AXIS).isHorizontal();
	}

	private class ControllerValueBoxTransform extends ValueBoxTransform.Sided {

		@Override
		protected Vec3 getSouthLocation() {
			return VecHelper.voxelSpace(8, 11f, 15.5f);
		}

		@Override
		protected boolean isSideActive(BlockState state, Direction direction) {
			if (direction.getAxis().isVertical())
				return false;
			return state.getValue(SpeedControllerBlock.HORIZONTAL_AXIS) != direction.getAxis();
		}

		@Override
		public float getScale() {
			return 0.5f;
		}
	}
}
