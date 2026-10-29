package dev.createpolymer.kinetics.gauge;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.kinetics.base.DirectionalAxisKineticBlock;
import dev.createpolymer.kinetics.base.IRotate;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Ported from Create's GaugeBlock and GaugeVisual (MIT, The Create Team): speedometer and stressometer. */
public class GaugeBlock extends DirectionalAxisKineticBlock {
	private static final VoxelShape UP = Shapes.or(ShapeUtil.box(1, 0, 0, 15, 2, 16), ShapeUtil.box(2, 2, 1, 14, 14, 15));
	private static final Map<Direction, VoxelShape> SHAPE_FALSE = ShapeUtil.forDirectional(UP);
	private static final Map<Direction, VoxelShape> SHAPE_TRUE = ShapeUtil.forDirectional(ShapeUtil.rotateY90(UP));

	private final boolean stress;

	public GaugeBlock(Properties properties, boolean stress) {
		super(properties);
		this.stress = stress;
	}

	@Override
	public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
		return stress ? ModBlockEntities.STRESSOMETER : ModBlockEntities.SPEEDOMETER;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Level world = context.getLevel();
		Direction face = context.getClickedFace();
		BlockPos placedOnPos = context.getClickedPos().relative(face.getOpposite());
		BlockState placedOnState = world.getBlockState(placedOnPos);

		if (placedOnState.getBlock() instanceof IRotate rotate && rotate.hasShaftTowards(world, placedOnPos, placedOnState, face)) {
			BlockState toPlace = defaultBlockState();
			Direction horizontalFacing = context.getHorizontalDirection();
			Direction nearestLookingDirection = context.getNearestLookingDirection();
			boolean lookPositive = nearestLookingDirection.getAxisDirection() == AxisDirection.POSITIVE;
			if (face.getAxis() == Axis.X) {
				toPlace = toPlace.setValue(FACING, lookPositive ? Direction.NORTH : Direction.SOUTH).setValue(AXIS_ALONG_FIRST_COORDINATE, true);
			} else if (face.getAxis() == Axis.Y) {
				toPlace = toPlace.setValue(FACING, horizontalFacing.getOpposite())
					.setValue(AXIS_ALONG_FIRST_COORDINATE, horizontalFacing.getAxis() == Axis.X);
			} else {
				toPlace = toPlace.setValue(FACING, lookPositive ? Direction.WEST : Direction.EAST).setValue(AXIS_ALONG_FIRST_COORDINATE, false);
			}
			return toPlace;
		}
		return super.getStateForPlacement(context);
	}

	@Override
	protected Direction getFacingForPlacement(BlockPlaceContext context) {
		return context.getClickedFace();
	}

	@Override
	protected boolean getAxisAlignmentForPlacement(BlockPlaceContext context) {
		return context.getHorizontalDirection().getAxis() != Axis.X;
	}

	/** Create's shouldRenderHeadOnFace, without the neighbour occlusion check. */
	public boolean shouldRenderHeadOnFace(BlockState state, Direction face) {
		if (face.getAxis().isVertical())
			return false;
		if (face == state.getValue(FACING).getOpposite())
			return false;
		if (face.getAxis() == getRotationAxis(state))
			return false;
		return getRotationAxis(state) != Axis.Y || face == state.getValue(FACING);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		Direction facing = state.getValue(FACING);
		boolean along = state.getValue(AXIS_ALONG_FIRST_COORDINATE);
		if (facing.getAxis() == Axis.X)
			along = !along; // Create swaps the X-facing shapes
		return (along ? SHAPE_TRUE : SHAPE_FALSE).get(facing);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		if (level.getBlockEntity(pos) instanceof GaugeBlockEntity gauge)
			return Mth.ceil(Mth.clamp(gauge.dialTarget * 14, 0, 15));
		return 0;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	protected BlockState particleState() {
		return Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state, this);
	}

	public static class Model extends BaseModel {
		private static final float DIAL_PIVOT = 5.75f / 16 - .5f;
		private final GaugeBlock block;
		private final Part base;
		private final Part shaft;
		private final Map<Direction, Part[]> faces = new EnumMap<>(Direction.class);
		private float lastProgress = Float.NaN;

		public Model(BlockState state, GaugeBlock block) {
			super(state);
			this.block = block;
			base = addPart(ModelItems.block("shaft"));
			shaft = addPart(ModelItems.block("shaft"));
			for (Direction d : Direction.Plane.HORIZONTAL)
				faces.put(d, new Part[] {
					addPart(ModelItems.block("gauge/dial")),
					addPart(ModelItems.block(block.stress ? "gauge/stressometer/head" : "gauge/speedometer/head")) });
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(base, block.stress ? "stressometer" : "speedometer", state);
			Axis axis = block.getRotationAxis(state);
			shaft.orientation(Orientation.forAxis(axis)).spinAxis(axis).apply(0);
			lastProgress = Float.NaN;
			setDials(0, 0);
		}

		private void setDials(float progress, int interpolation) {
			for (var e : faces.entrySet()) {
				Direction face = e.getKey();
				boolean visible = block.shouldRenderHeadOnFace(state, face);
				float yaw = (float) Math.toRadians(-face.toYRot() - 90);
				Matrix4f head = new Matrix4f().rotateY(yaw);
				Matrix4f dial = new Matrix4f(head)
					.translate(0, DIAL_PIVOT, DIAL_PIVOT)
					.rotateX((float) (-Math.PI / 2 * progress))
					.translate(0, -DIAL_PIVOT, -DIAL_PIVOT);
				if (!visible) {
					head.scale(0);
					dial.scale(0);
				}
				e.getValue()[0].setMatrix(dial, interpolation);
				e.getValue()[1].setMatrix(head, interpolation);
			}
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof GaugeBlockEntity gauge))
				return;
			Axis axis = block.getRotationAxis(state);
			shaft.spin(time, gauge.getSpeed(), Orientation.cogOffset(false, axis, pos.getX(), pos.getY(), pos.getZ()));
			float progress = gauge.dialState;
			if (Float.isNaN(lastProgress) || Math.abs(progress - lastProgress) > 1e-3) {
				lastProgress = progress;
				setDials(progress, 1);
			}
		}
	}
}
