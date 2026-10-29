package dev.createpolymer.logistics.itemHatch;

import org.joml.Matrix4f;

import dev.createpolymer.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Ported from Create's HatchFilterSlot (MIT, The Create Team). */
public class HatchFilterSlot extends ValueBoxTransform {

	@Override
	public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
		return VecHelper.rotateCentered(VecHelper.voxelSpace(8, 5.15, 9.5), angle(state), Direction.Axis.Y);
	}

	@Override
	public float getScale() {
		return super.getScale() * 0.965f;
	}

	@Override
	public boolean testHit(LevelAccessor level, BlockPos pos, BlockState state, Vec3 localHit) {
		return localHit.distanceTo(getLocalOffset(level, pos, state).subtract(0, 0.125, 0)) < scale / 2;
	}

	@Override
	public void rotate(LevelAccessor level, BlockPos pos, BlockState state, Matrix4f ms) {
		ms.rotateY((float) Math.toRadians(angle(state)));
		ms.rotateX((float) Math.toRadians(-45));
	}

	private float angle(BlockState state) {
		return AngleHelper.horizontalAngle(state.getOptionalValue(ItemHatchBlock.FACING).orElse(Direction.NORTH));
	}
}
