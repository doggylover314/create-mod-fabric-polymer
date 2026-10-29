package dev.createpolymer.kinetics.steamEngine;

import org.joml.Matrix4f;

import dev.createpolymer.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.Pointing;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class SteamEngineValueBox extends ValueBoxTransform.Sided {

	@Override
	protected boolean isSideActive(BlockState state, Direction side) {
		Direction engineFacing = SteamEngineBlock.getFacing(state);
		if (engineFacing.getAxis() == side.getAxis())
			return false;

		float roll = 0;
		for (Pointing p : Pointing.values())
			if (p.getCombinedDirection(engineFacing) == side)
				roll = p.getXRotation();
		if (engineFacing == Direction.UP)
			roll += 180;

		boolean recessed = roll % 180 == 0;
		if (engineFacing.getAxis() == Axis.Y)
			recessed ^= state.getValue(SteamEngineBlock.FACING)
				.getAxis() == Axis.X;

		return !recessed;
	}

	@Override
	public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
		Direction side = getSide();
		Direction engineFacing = SteamEngineBlock.getFacing(state);

		float roll = 0;
		for (Pointing p : Pointing.values())
			if (p.getCombinedDirection(engineFacing) == side)
				roll = p.getXRotation();
		if (engineFacing == Direction.UP)
			roll += 180;

		float horizontalAngle = AngleHelper.horizontalAngle(engineFacing);
		float verticalAngle = AngleHelper.verticalAngle(engineFacing);
		Vec3 local = VecHelper.voxelSpace(8, 14.5, 9);

		local = VecHelper.rotateCentered(local, roll, Axis.Z);
		local = VecHelper.rotateCentered(local, horizontalAngle, Axis.Y);
		local = VecHelper.rotateCentered(local, verticalAngle, Axis.X);

		return local;
	}

	@Override
	public void rotate(LevelAccessor level, BlockPos pos, BlockState state, Matrix4f ms) {
		Direction facing = SteamEngineBlock.getFacing(state);

		if (facing.getAxis() == Axis.Y) {
			super.rotate(level, pos, state, ms);
			return;
		}

		float roll = 0;
		for (Pointing p : Pointing.values())
			if (p.getCombinedDirection(facing) == getSide())
				roll = p.getXRotation();

		float yRot = AngleHelper.horizontalAngle(facing) + (facing == Direction.DOWN ? 180 : 0);
		ms.rotateY((float) Math.toRadians(yRot))
			.rotateX((float) Math.toRadians(facing == Direction.DOWN ? -90 : 90))
			.rotateY((float) Math.toRadians(roll));
	}

	@Override
	protected Vec3 getSouthLocation() {
		return Vec3.ZERO;
	}

}
