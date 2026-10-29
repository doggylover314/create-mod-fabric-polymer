package dev.createpolymer.contraptions.gantry;

import dev.createpolymer.contraptions.AssemblyException;
import dev.createpolymer.contraptions.ContraptionCollider;
import dev.createpolymer.contraptions.IDisplayAssemblyExceptions;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.gantry.GantryShaftBlock;
import dev.createpolymer.kinetics.gantry.GantryShaftBlockEntity;
import dev.createpolymer.registry.feature.PulleyRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class GantryCarriageBlockEntity extends KineticBlockEntity implements IDisplayAssemblyExceptions {

	boolean assembleNextTick;
	protected AssemblyException lastException;

	public GantryCarriageBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
		super(typeIn, pos, state);
	}

	@Override
	public void onSpeedChanged(float previousSpeed) {
		super.onSpeedChanged(previousSpeed);
	}

	public void checkValidGantryShaft() {
		if (shouldAssemble())
			queueAssembly();
	}

	@Override
	public void initialize() {
		super.initialize();
		if (!getBlockState().canSurvive(level, worldPosition))
			level.destroyBlock(worldPosition, true);
	}

	public void queueAssembly() {
		assembleNextTick = true;
	}

	@Override
	public void tick() {
		super.tick();

		if (assembleNextTick) {
			tryAssemble();
			assembleNextTick = false;
		}
	}

	@Override
	public AssemblyException getLastAssemblyException() {
		return lastException;
	}

	private void tryAssemble() {
		BlockState blockState = getBlockState();
		if (!(blockState.getBlock() instanceof GantryCarriageBlock))
			return;

		Direction direction = blockState.getValue(GantryCarriageBlock.FACING);
		GantryContraption contraption = new GantryContraption(direction);

		BlockEntity blockEntity = level.getBlockEntity(worldPosition.relative(direction.getOpposite()));
		if (!(blockEntity instanceof GantryShaftBlockEntity shaftBE))
			return;
		BlockState shaftState = shaftBE.getBlockState();
		if (!(shaftState.getBlock() instanceof GantryShaftBlock))
			return;

		float pinionMovementSpeed = shaftBE.getPinionMovementSpeed();
		Direction shaftOrientation = shaftState.getValue(GantryShaftBlock.FACING);
		Direction movementDirection = shaftOrientation;
		if (pinionMovementSpeed < 0)
			movementDirection = movementDirection.getOpposite();

		try {
			lastException = null;
			if (!contraption.assemble(level, worldPosition))
				return;

			sendData();
		} catch (AssemblyException e) {
			lastException = e;
			sendData();
			return;
		}
		if (ContraptionCollider.isCollidingWithWorld(level, contraption, worldPosition.relative(movementDirection),
			movementDirection))
			return;

		contraption.removeBlocksFromWorld(level, BlockPos.ZERO);
		GantryContraptionEntity movedContraption =
			GantryContraptionEntity.create(level, contraption, shaftOrientation);
		BlockPos anchor = worldPosition;
		movedContraption.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
		PulleyRegistry.playContraptionAssemble(level, worldPosition);
		level.addFreshEntity(movedContraption);

		// Create limits the movement with a sequenced gearshift's "turn distance" instruction here; the sequence
		// context does not travel along the kinetic network in this port
	}

	@Override
	protected void write(ValueOutput compound) {
		AssemblyException.write(compound, lastException);
		super.write(compound);
	}

	@Override
	protected void read(ValueInput compound) {
		lastException = AssemblyException.read(compound);
		super.read(compound);
	}

	@Override
	public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo, BlockPos diff,
		boolean connectedViaAxes, boolean connectedViaCogs) {
		float defaultModifier =
			super.propagateRotationTo(target, stateFrom, stateTo, diff, connectedViaAxes, connectedViaCogs);

		if (connectedViaAxes)
			return defaultModifier;
		if (!(stateTo.getBlock() instanceof GantryShaftBlock))
			return defaultModifier;
		if (!stateTo.getValue(GantryShaftBlock.POWERED))
			return defaultModifier;

		Direction direction = Direction.getApproximateNearest(diff.getX(), diff.getY(), diff.getZ());
		if (stateFrom.getValue(GantryCarriageBlock.FACING) != direction.getOpposite())
			return defaultModifier;
		return getGantryPinionModifier(stateTo.getValue(GantryShaftBlock.FACING),
			stateFrom.getValue(GantryCarriageBlock.FACING));
	}

	public static float getGantryPinionModifier(Direction shaft, Direction pinionDirection) {
		Axis shaftAxis = shaft.getAxis();
		float directionModifier = shaft.getAxisDirection()
			.getStep();
		if (shaftAxis == Axis.Y)
			if (pinionDirection == Direction.NORTH || pinionDirection == Direction.EAST)
				return -directionModifier;
		if (shaftAxis == Axis.X)
			if (pinionDirection == Direction.DOWN || pinionDirection == Direction.SOUTH)
				return -directionModifier;
		if (shaftAxis == Axis.Z)
			if (pinionDirection == Direction.UP || pinionDirection == Direction.WEST)
				return -directionModifier;
		return directionModifier;
	}

	private boolean shouldAssemble() {
		BlockState blockState = getBlockState();
		if (!(blockState.getBlock() instanceof GantryCarriageBlock))
			return false;
		Direction facing = blockState.getValue(GantryCarriageBlock.FACING)
			.getOpposite();
		BlockState shaftState = level.getBlockState(worldPosition.relative(facing));
		if (!(shaftState.getBlock() instanceof GantryShaftBlock))
			return false;
		if (shaftState.getValue(GantryShaftBlock.POWERED))
			return false;
		BlockEntity be = level.getBlockEntity(worldPosition.relative(facing));
		return be instanceof GantryShaftBlockEntity && ((GantryShaftBlockEntity) be).canAssembleOn();
	}
}
