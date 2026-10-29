package dev.createpolymer.kinetics.saw;

import org.joml.Matrix4f;

import dev.createpolymer.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Ported from Create's SawFilterSlot (MIT, The Create Team). */
public class SawFilterSlot extends ValueBoxTransform {

	@Override
	public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
		if (state.getValue(SawBlock.FACING) != Direction.UP)
			return null;
		int offset = state.getValue(SawBlock.FLIPPED) ? -3 : 3;
		Vec3 x = VecHelper.voxelSpace(8, 12.5f, 8 + offset);
		Vec3 z = VecHelper.voxelSpace(8 + offset, 12.5f, 8);
		return state.getValue(SawBlock.AXIS_ALONG_FIRST_COORDINATE) ? z : x;
	}

	@Override
	public void rotate(LevelAccessor level, BlockPos pos, BlockState state, Matrix4f ms) {
		int yRot = (state.getValue(SawBlock.AXIS_ALONG_FIRST_COORDINATE) ? 90 : 0)
			+ (state.getValue(SawBlock.FLIPPED) ? 0 : 180);
		ms.rotateY((float) Math.toRadians(yRot))
			.rotateX((float) Math.toRadians(90));
	}

}
