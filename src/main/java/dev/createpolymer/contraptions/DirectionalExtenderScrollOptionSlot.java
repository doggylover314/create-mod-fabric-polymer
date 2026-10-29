package dev.createpolymer.contraptions;

import java.util.function.BiPredicate;

import org.joml.Matrix4f;

import dev.createpolymer.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import dev.createpolymer.util.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public class DirectionalExtenderScrollOptionSlot extends CenteredSideValueBoxTransform {

	public DirectionalExtenderScrollOptionSlot(BiPredicate<BlockState, Direction> allowedDirections) {
		super(allowedDirections);
	}

	@Override
	public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
		return super.getLocalOffset(level, pos, state)
				.add(Vec3.atLowerCornerOf(state.getValue(BlockStateProperties.FACING).getUnitVec3i()).scale(-2 / 16f));
	}

	@Override
	public void rotate(LevelAccessor level, BlockPos pos, BlockState state, Matrix4f ms) {
		if (!getSide().getAxis().isHorizontal())
			ms.rotateY((float) Math.toRadians(AngleHelper.horizontalAngle(state.getValue(BlockStateProperties.FACING)) + 180));
		super.rotate(level, pos, state, ms);
	}
}
