package dev.createpolymer.contraptions.actors.psi;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.api.behaviour.movement.MovementBehaviour;
import dev.createpolymer.contraptions.behaviour.MovementContext;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's PortableStorageInterfaceMovement (MIT, The Create Team). Create animates the extension on the
 * client and for train carriages; neither exists here.
 */
public class PortableStorageInterfaceMovement implements MovementBehaviour {

	static final String _workingPos_ = "WorkingPos";

	@Override
	public Vec3 getActiveAreaOffset(MovementContext context) {
		return Vec3.atLowerCornerOf(context.state.getValue(PortableStorageInterfaceBlock.FACING)
			.getUnitVec3i())
			.scale(1.85f);
	}

	@Override
	public void visitNewPosition(MovementContext context, BlockPos pos) {
		if (!findInterface(context, pos))
			context.data.remove(_workingPos_);
	}

	@Override
	public void tick(MovementContext context) {
		if (!context.data.contains(_workingPos_)) {
			if (context.stall)
				cancelStall(context);
			return;
		}

		BlockPos pos = context.data.read(_workingPos_, BlockPos.CODEC)
			.orElse(BlockPos.ZERO);
		Vec3 target = VecHelper.getCenterOf(pos);

		if (!context.stall && context.position.closerThan(target, target.distanceTo(context.position.add(context.motion))))
			context.stall = true;

		Optional<Direction> currentFacingIfValid = getCurrentFacingIfValid(context);
		if (!currentFacingIfValid.isPresent()) {
			reset(context);
			return;
		}

		PortableStorageInterfaceBlockEntity stationaryInterface =
			getStationaryInterfaceAt(context.world, pos, context.state, currentFacingIfValid.get());
		if (stationaryInterface == null) {
			reset(context);
			return;
		}

		if (stationaryInterface.connectedEntity == null)
			stationaryInterface.startTransferringTo(context.contraption, stationaryInterface.distance);

		boolean timerBelow = stationaryInterface.transferTimer <= PortableStorageInterfaceBlockEntity.ANIMATION;
		stationaryInterface.keepAlive = 2;
		if (context.stall && timerBelow) {
			context.stall = false;
		}
	}

	protected boolean findInterface(MovementContext context, BlockPos pos) {
		Optional<Direction> currentFacingIfValid = getCurrentFacingIfValid(context);
		if (!currentFacingIfValid.isPresent())
			return false;

		Direction currentFacing = currentFacingIfValid.get();
		PortableStorageInterfaceBlockEntity psi =
			findStationaryInterface(context.world, pos, context.state, currentFacing);

		if (psi == null)
			return false;
		if (psi.isPowered())
			return false;

		context.data.store(_workingPos_, BlockPos.CODEC, psi.getBlockPos());
		Vec3 diff = VecHelper.getCenterOf(psi.getBlockPos())
			.subtract(context.position);
		diff = VecHelper.project(diff, Vec3.atLowerCornerOf(currentFacing.getUnitVec3i()));
		float distance = (float) (diff.length() + 1.85f - 1);
		psi.startTransferringTo(context.contraption, distance);

		return true;
	}

	@Override
	public void stopMoving(MovementContext context) {
//		reset(context);
	}

	@Override
	public void cancelStall(MovementContext context) {
		reset(context);
	}

	public void reset(MovementContext context) {
		context.data.remove(_workingPos_);
		context.stall = false;
	}

	private @Nullable PortableStorageInterfaceBlockEntity findStationaryInterface(Level world, BlockPos pos,
		BlockState state, Direction facing) {
		for (int i = 0; i < 2; i++) {
			PortableStorageInterfaceBlockEntity interfaceAt =
				getStationaryInterfaceAt(world, pos.relative(facing, i), state, facing);
			if (interfaceAt == null)
				continue;
			return interfaceAt;
		}
		return null;
	}

	private @Nullable PortableStorageInterfaceBlockEntity getStationaryInterfaceAt(Level world, BlockPos pos,
		BlockState state, Direction facing) {
		BlockEntity blockEntity = world.getBlockEntity(pos);
		if (!(blockEntity instanceof PortableStorageInterfaceBlockEntity psi))
			return null;
		BlockState blockState = world.getBlockState(pos);
		if (blockState.getBlock() != state.getBlock())
			return null;
		if (blockState.getValue(PortableStorageInterfaceBlock.FACING) != facing.getOpposite())
			return null;
		if (psi.isPowered())
			return null;
		return psi;
	}

	private Optional<Direction> getCurrentFacingIfValid(MovementContext context) {
		Vec3 directionVec = Vec3.atLowerCornerOf(context.state.getValue(PortableStorageInterfaceBlock.FACING)
			.getUnitVec3i());
		directionVec = context.rotation.apply(directionVec);
		Direction facingFromVector = Direction.getApproximateNearest(directionVec.x, directionVec.y, directionVec.z);
		if (directionVec.distanceTo(Vec3.atLowerCornerOf(facingFromVector.getUnitVec3i())) > 1 / 2f)
			return Optional.empty();
		return Optional.of(facingFromVector);
	}

}
