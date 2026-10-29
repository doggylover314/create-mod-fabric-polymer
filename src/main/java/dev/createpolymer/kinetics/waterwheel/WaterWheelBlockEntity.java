package dev.createpolymer.kinetics.waterwheel;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import dev.createpolymer.kinetics.base.GeneratingKineticBlockEntity;
import dev.createpolymer.kinetics.base.IRotate;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.Vec3i;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's WaterWheelBlockEntity (MIT, The Create Team).
 * Custom wheel materials and the large water wheel are not ported yet.
 */
public class WaterWheelBlockEntity extends GeneratingKineticBlockEntity {

	public static final Map<Axis, Set<BlockPos>> SMALL_OFFSETS = new EnumMap<>(Axis.class);

	static {
		for (Axis axis : Axis.values()) {
			HashSet<BlockPos> offsets = new HashSet<>();
			for (Direction d : Direction.values())
				if (d.getAxis() != axis)
					offsets.add(BlockPos.ZERO.relative(d));
			SMALL_OFFSETS.put(axis, offsets);
		}
	}

	public int flowScore;

	public WaterWheelBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.WATER_WHEEL, pos, state);
		setLazyTickRate(60);
	}

	protected Set<BlockPos> getOffsetsToCheck() {
		return SMALL_OFFSETS.get(getAxis());
	}

	protected Axis getAxis() {
		BlockState blockState = getBlockState();
		if (blockState.getBlock() instanceof IRotate irotate)
			return irotate.getRotationAxis(blockState);
		return Axis.X;
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		// Water can change flow direction without notifying neighbours
		determineAndApplyFlowScore();
	}

	public void determineAndApplyFlowScore() {
		Vec3i normal = Direction.get(AxisDirection.POSITIVE, getAxis()).getUnitVec3i();
		Vec3 wheelPlane = Vec3.atLowerCornerOf(new Vec3i(1, 1, 1).subtract(normal));

		int flowScore = 0;
		for (BlockPos blockPos : getOffsetsToCheck()) {
			BlockPos targetPos = blockPos.offset(worldPosition);
			Vec3 flowAtPos = getFlowVectorAtPosition(targetPos).multiply(wheelPlane);
			if (flowAtPos.lengthSqr() == 0)
				continue;

			flowAtPos = flowAtPos.normalize();
			Vec3 radial = Vec3.atLowerCornerOf(blockPos).normalize();
			Vec3 positiveMotion = VecHelper.rotate(radial, 90, getAxis());
			double dot = flowAtPos.dot(positiveMotion);
			if (Math.abs(dot) > .5)
				flowScore += (int) Math.signum(dot);
		}

		setFlowScoreAndUpdate(flowScore);
	}

	public Vec3 getFlowVectorAtPosition(BlockPos pos) {
		FluidState fluid = level.getFluidState(pos);
		Vec3 vec = fluid.getFlow(level, pos);
		BlockState blockState = level.getBlockState(pos);
		if (blockState.getBlock() == Blocks.BUBBLE_COLUMN)
			vec = new Vec3(0, blockState.getValue(BubbleColumnBlock.DRAG_DOWN) ? -1 : 1, 0);
		return vec;
	}

	public boolean hasLavaNearby() {
		for (BlockPos blockPos : getOffsetsToCheck())
			if (level.getFluidState(blockPos.offset(worldPosition)).is(FluidTags.LAVA))
				return true;
		return false;
	}

	public void setFlowScoreAndUpdate(int score) {
		if (flowScore == score)
			return;
		flowScore = score;
		updateGeneratedRotation();
		setChanged();
	}

	@Override
	protected void read(ValueInput compound) {
		super.read(compound);
		flowScore = compound.getIntOr("FlowScore", 0);
	}

	@Override
	protected void write(ValueOutput compound) {
		super.write(compound);
		compound.putInt("FlowScore", flowScore);
	}

	@Override
	public float getGeneratedSpeed() {
		return Mth.clamp(flowScore, -1, 1) * 8;
	}
}
