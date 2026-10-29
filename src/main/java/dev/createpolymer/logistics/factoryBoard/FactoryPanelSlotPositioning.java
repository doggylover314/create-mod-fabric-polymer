package dev.createpolymer.logistics.factoryBoard;

import org.joml.Matrix4f;

import dev.createpolymer.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.createpolymer.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

class FactoryPanelSlotPositioning extends ValueBoxTransform {

	public PanelSlot slot;

	public FactoryPanelSlotPositioning(PanelSlot slot) {
		this.slot = slot;
	}

	@Override
	public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
		return getCenterOfSlot(state, slot);
	}

	public static Vec3 getCenterOfSlot(BlockState state, PanelSlot slot) {
		Vec3 vec = new Vec3(.25 + slot.xOffset * .5, 1.5 / 16f, .25 + slot.yOffset * .5);
		vec = VecHelper.rotateCentered(vec, 180, Axis.Y);
		vec = VecHelper.rotateCentered(vec, Mth.RAD_TO_DEG * FactoryPanelBlock.getXRot(state) + 90, Axis.X);
		vec = VecHelper.rotateCentered(vec, Mth.RAD_TO_DEG * FactoryPanelBlock.getYRot(state), Axis.Y);
		return vec;
	}

	@Override
	public boolean testHit(LevelAccessor level, BlockPos pos, BlockState state, Vec3 localHit) {
		Vec3 offset = getLocalOffset(level, pos, state);
		if (offset == null)
			return false;
		return localHit.distanceTo(offset) < scale / 2;
	}

	@Override
	public float getScale() {
		return super.getScale();
	}

	@Override
	public void rotate(LevelAccessor level, BlockPos pos, BlockState state, Matrix4f ms) {
		ms.rotateY(FactoryPanelBlock.getYRot(state) + Mth.PI)
			.rotateX(-FactoryPanelBlock.getXRot(state));
	}

}
