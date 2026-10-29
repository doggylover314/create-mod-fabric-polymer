package dev.createpolymer.kinetics.crusher;

import static dev.createpolymer.kinetics.crusher.CrushingWheelControllerBlock.VALID;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.base.RotatedPillarKineticBlock;
import dev.createpolymer.registry.feature.CrushingRegistry;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Ported from Create's CrushingWheelBlock (MIT, The Create Team). */
public class CrushingWheelBlock extends RotatedPillarKineticBlock implements IBE<CrushingWheelBlockEntity> {

	public CrushingWheelBlock(Properties properties) {
		super(properties);
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return state.getValue(AXIS);
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel worldIn, BlockPos pos, boolean isMoving) {
		for (Direction d : Iterate.directions) {
			if (d.getAxis() == state.getValue(AXIS))
				continue;
			if (worldIn.getBlockState(pos.relative(d)).is(CrushingRegistry.CRUSHING_WHEEL_CONTROLLER))
				worldIn.removeBlock(pos.relative(d), isMoving);
		}

		super.affectNeighborsAfterRemoval(state, worldIn, pos, isMoving);
	}

	public void updateControllers(BlockState state, Level world, BlockPos pos, Direction side) {
		if (side.getAxis() == state.getValue(AXIS))
			return;
		if (world == null)
			return;

		BlockPos controllerPos = pos.relative(side);
		BlockPos otherWheelPos = pos.relative(side, 2);

		boolean controllerExists = world.getBlockState(controllerPos).is(CrushingRegistry.CRUSHING_WHEEL_CONTROLLER);
		boolean controllerIsValid = controllerExists && world.getBlockState(controllerPos)
			.getValue(VALID);
		Direction controllerOldDirection = controllerExists ? world.getBlockState(controllerPos)
			.getValue(CrushingWheelControllerBlock.FACING) : null;

		boolean controllerShouldExist = false;
		boolean controllerShouldBeValid = false;
		Direction controllerNewDirection = Direction.DOWN;

		BlockState otherState = world.getBlockState(otherWheelPos);
		if (otherState.is(CrushingRegistry.CRUSHING_WHEEL)) {
			controllerShouldExist = true;

			CrushingWheelBlockEntity be = getBlockEntity(world, pos);
			CrushingWheelBlockEntity otherBE = getBlockEntity(world, otherWheelPos);

			if (be != null && otherBE != null && (be.getSpeed() > 0) != (otherBE.getSpeed() > 0)
				&& be.getSpeed() != 0 && otherBE.getSpeed() != 0) {
				Axis wheelAxis = state.getValue(AXIS);
				Axis sideAxis = side.getAxis();
				int controllerADO = Math.round(Math.signum(be.getSpeed())) * side.getAxisDirection()
					.getStep();
				Vec3 controllerDirVec = new Vec3(wheelAxis == Axis.X ? 1 : 0, wheelAxis == Axis.Y ? 1 : 0,
					wheelAxis == Axis.Z ? 1 : 0).cross(
						new Vec3(sideAxis == Axis.X ? 1 : 0, sideAxis == Axis.Y ? 1 : 0, sideAxis == Axis.Z ? 1 : 0));

				controllerNewDirection = Direction.getApproximateNearest(controllerDirVec.x * controllerADO,
					controllerDirVec.y * controllerADO, controllerDirVec.z * controllerADO);

				controllerShouldBeValid = true;
			}
			if (otherState.getValue(AXIS) != state.getValue(AXIS))
				controllerShouldExist = false;
		}

		if (!controllerShouldExist) {
			if (controllerExists)
				world.setBlockAndUpdate(controllerPos, Blocks.AIR.defaultBlockState());
			return;
		}

		if (!controllerExists) {
			if (!world.getBlockState(controllerPos)
				.canBeReplaced())
				return;
			world.setBlockAndUpdate(controllerPos, CrushingRegistry.CRUSHING_WHEEL_CONTROLLER.defaultBlockState()
				.setValue(VALID, controllerShouldBeValid)
				.setValue(CrushingWheelControllerBlock.FACING, controllerNewDirection));
		} else if (controllerIsValid != controllerShouldBeValid || controllerOldDirection != controllerNewDirection) {
			world.setBlockAndUpdate(controllerPos, world.getBlockState(controllerPos)
				.setValue(VALID, controllerShouldBeValid)
				.setValue(CrushingWheelControllerBlock.FACING, controllerNewDirection));
		}

		CrushingRegistry.CRUSHING_WHEEL_CONTROLLER
			.updateSpeed(world.getBlockState(controllerPos), world, controllerPos);

	}

	@Override
	protected void entityInside(BlockState state, Level worldIn, BlockPos pos, Entity entityIn,
								InsideBlockEffectApplier effectApplier, boolean isPrecise) {
		if (entityIn.getY() < pos.getY() + 1.25f || !entityIn.onGround())
			return;

		float speed = getBlockEntityOptional(worldIn, pos).map(CrushingWheelBlockEntity::getSpeed)
			.orElse(0f);

		double x = 0;
		double z = 0;

		if (state.getValue(AXIS) == Axis.X) {
			z = speed / 20f;
			x += (pos.getX() + .5f - entityIn.getX()) * .1f;
		}
		if (state.getValue(AXIS) == Axis.Z) {
			x = speed / -20f;
			z += (pos.getZ() + .5f - entityIn.getZ()) * .1f;
		}
		entityIn.setDeltaMovement(entityIn.getDeltaMovement()
			.add(x, 0, z));
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader worldIn, BlockPos pos) {
		for (Direction direction : Iterate.directions) {
			BlockPos neighbourPos = pos.relative(direction);
			BlockState neighbourState = worldIn.getBlockState(neighbourPos);
			Axis stateAxis = state.getValue(AXIS);
			if (neighbourState.is(CrushingRegistry.CRUSHING_WHEEL_CONTROLLER) && direction.getAxis() != stateAxis)
				return false;
			if (!neighbourState.is(CrushingRegistry.CRUSHING_WHEEL))
				continue;
			if (neighbourState.getValue(AXIS) != stateAxis || stateAxis != direction.getAxis())
				return false;
		}

		return true;
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face.getAxis() == state.getValue(AXIS);
	}

	@Override
	public Class<CrushingWheelBlockEntity> getBlockEntityClass() {
		return CrushingWheelBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends CrushingWheelBlockEntity> getBlockEntityType() {
		return CrushingRegistry.CRUSHING_WHEEL_BLOCK_ENTITY;
	}

	@Override
	protected BlockState particleState() {
		return Blocks.ANDESITE.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/** The wheel is two blocks wide: its display reaches into the neighbouring blocks. */
	public static class Model extends BaseModel {
		private final Part wheel;

		public Model(BlockState state) {
			super(state);
			wheel = addPart(ModelItems.block("crushing_wheel/polymer_wheel"));
			wheel.element.setViewRange(1.5f);
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			Axis axis = state.getValue(AXIS);
			wheel.orientation(Orientation.forAxis(axis)).spinAxis(axis).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof KineticBlockEntity kbe))
				return;
			Axis axis = state.getValue(AXIS);
			wheel.spin(time, kbe.getSpeed(), Orientation.cogOffset(false, axis, pos.getX(), pos.getY(), pos.getZ()));
		}
	}

}
