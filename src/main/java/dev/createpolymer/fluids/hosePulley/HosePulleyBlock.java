package dev.createpolymer.fluids.hosePulley;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.kinetics.base.HorizontalKineticBlock;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.fluids.pipes.FluidPipeBlock;
import dev.createpolymer.registry.feature.PipesRegistry;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class HosePulleyBlock extends HorizontalKineticBlock {

	public HosePulleyBlock(Properties properties) {
		super(properties);
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return state.getValue(HORIZONTAL_FACING)
			.getClockWise()
			.getAxis();
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction preferredHorizontalFacing = getPreferredHorizontalFacing(context);
		return this.defaultBlockState()
			.setValue(HORIZONTAL_FACING,
				preferredHorizontalFacing != null ? preferredHorizontalFacing.getCounterClockWise()
					: context.getHorizontalDirection()
						.getOpposite());
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return state.getValue(HORIZONTAL_FACING)
			.getClockWise() == face;
	}

	public static boolean hasPipeTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return state.getValue(HORIZONTAL_FACING)
			.getCounterClockWise() == face;
	}

	@Override
	public Direction getPreferredHorizontalFacing(BlockPlaceContext context) {
		Direction fromParent = super.getPreferredHorizontalFacing(context);
		if (fromParent != null)
			return fromParent;

		Direction prefferedSide = null;
		for (Direction facing : Iterate.horizontalDirections) {
			BlockPos pos = context.getClickedPos()
				.relative(facing);
			BlockState blockState = context.getLevel()
				.getBlockState(pos);
			if (FluidPipeBlock.canConnectTo(context.getLevel(), pos, blockState, facing))
				if (prefferedSide != null && prefferedSide.getAxis() != facing.getAxis()) {
					prefferedSide = null;
					break;
				} else
					prefferedSide = facing;
		}
		return prefferedSide == null ? null : prefferedSide.getOpposite();
	}

	@Override
	public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
		return PipesRegistry.HOSE_PULLEY_BLOCK_ENTITY;
	}

	// ---- Polymer ----

	@Override
	protected BlockState particleState() {
		return Blocks.COPPER_BLOCK.weathering().unaffected().defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/**
	 * Create's block model with the shaft and hose coil, and HosePulleyRenderer's hose: a magnet at the offset
	 * and rope pieces above it. The rope pieces sit still in the blocks under the pulley and are shown or hidden;
	 * only the magnet moves. (The coil's UV scrolling is not shown.)
	 */
	public static class Model extends BaseModel {
		private final Part body;
		private final Part shaft;
		private final Part coil;
		private final Part magnet;
		private final List<Part> ropes = new ArrayList<>();
		private int shownRopes = 0;
		private boolean shownFullMagnet = false;
		private float shownOffset = -1;

		public Model(BlockState state) {
			super(state);
			body = showVariant(addPart(ModelItems.block("hose_pulley/block")), "hose_pulley", state);
			shaft = addPart(ModelItems.block("shaft"));
			coil = addPart(ModelItems.block("hose_pulley/hose_coil"));
			magnet = addPart(ModelItems.block("hose_pulley/rope_half_magnet"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			if (!(state.getBlock() instanceof HosePulleyBlock))
				return;
			showVariant(body, "hose_pulley", state);
			Axis shaftAxis = state.getValue(HORIZONTAL_FACING)
				.getClockWise()
				.getAxis();
			shaft.orientation(Orientation.forAxis(shaftAxis))
				.spinAxis(shaftAxis)
				.apply(0);
			coil.orientation(Orientation.horizontalAngle(Direction.get(Direction.AxisDirection.POSITIVE, shaftAxis)))
				.apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof HosePulleyBlockEntity pulley) || !(state.getBlock() instanceof HosePulleyBlock))
				return;
			Axis shaftAxis = state.getValue(HORIZONTAL_FACING)
				.getClockWise()
				.getAxis();
			shaft.spin(time, pulley.getSpeed(), Orientation.cogOffset(false, shaftAxis, pos.getX(), pos.getY(), pos.getZ()));

			float offset = pulley.getInterpolatedOffset(1);
			if (offset == shownOffset)
				return;
			shownOffset = offset;

			boolean fullMagnet = offset > .25f;
			if (fullMagnet != shownFullMagnet) {
				shownFullMagnet = fullMagnet;
				magnet.setItem(ModelItems.block(fullMagnet ? "hose_pulley/pulley_magnet" : "hose_pulley/rope_half_magnet"));
			}
			magnet.translation(0, -offset, 0)
				.apply(1);

			int wantedRopes = (int) Math.floor(offset);
			while (ropes.size() < wantedRopes) {
				Part rope = addPart(ItemStack.EMPTY);
				rope.translation(0, -(ropes.size() + 1), 0)
					.apply(0);
				ropes.add(rope);
			}
			for (int i = Math.min(shownRopes, wantedRopes); i < Math.max(shownRopes, wantedRopes); i++)
				ropes.get(i)
					.setItem(i < wantedRopes ? ModelItems.block("hose_pulley/rope") : ItemStack.EMPTY);
			shownRopes = wantedRopes;
		}
	}
}
