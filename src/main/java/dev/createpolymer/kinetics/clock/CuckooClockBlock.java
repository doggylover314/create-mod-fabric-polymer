package dev.createpolymer.kinetics.clock;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.kinetics.base.HorizontalKineticBlock;
import dev.createpolymer.registry.feature.KineticsExtraRegistry;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Ported from Create's CuckooClockBlock and CuckooClockRenderer (MIT, The Create Team). */
public class CuckooClockBlock extends HorizontalKineticBlock implements IBE<CuckooClockBlockEntity> {
	public static final VoxelShape SHAPE = ShapeUtil.box(1, 0, 1, 15, 19, 15);

	private boolean mysterious;

	public static CuckooClockBlock regular(Properties properties) {
		return new CuckooClockBlock(false, properties);
	}

	public static CuckooClockBlock mysterious(Properties properties) {
		return new CuckooClockBlock(true, properties);
	}

	protected CuckooClockBlock(boolean mysterious, Properties properties) {
		super(properties);
		this.mysterious = mysterious;
	}

	@Override
	protected VoxelShape getShape(BlockState p_220053_1_, BlockGetter p_220053_2_, BlockPos p_220053_3_,
		CollisionContext p_220053_4_) {
		return SHAPE;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction preferred = getPreferredHorizontalFacing(context);
		if (preferred != null)
			return defaultBlockState().setValue(HORIZONTAL_FACING, preferred.getOpposite());
		return this.defaultBlockState().setValue(HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face == state.getValue(HORIZONTAL_FACING).getOpposite();
	}

	public static boolean containsSurprise(BlockState state) {
		Block block = state.getBlock();
		return block instanceof CuckooClockBlock && ((CuckooClockBlock) block).mysterious;
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return state.getValue(HORIZONTAL_FACING).getAxis();
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	public Class<CuckooClockBlockEntity> getBlockEntityClass() {
		return CuckooClockBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends CuckooClockBlockEntity> getBlockEntityType() {
		return KineticsExtraRegistry.CUCKOO_CLOCK_BE;
	}

	@Override
	protected BlockState particleState() {
		return Blocks.SPRUCE_PLANKS.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/** Create's CuckooClockRenderer: hands, doors and the figure are moved as separate parts. */
	public static class Model extends BaseModel {
		private final Part base;
		private final Part shaft;
		private final Part hourHand;
		private final Part minuteHand;
		private final Part leftDoor;
		private final Part rightDoor;
		private final Part pig;
		private final Part creeper;

		private float lastHour = Float.NaN;
		private float lastMinute = Float.NaN;
		private float lastDoorAngle = Float.NaN;
		private CuckooClockBlockEntity.@Nullable Animation lastFigure;
		private float lastOffset = Float.NaN;

		public Model(BlockState state) {
			super(state);
			base = addPart(ModelItems.block("cuckoo_clock/block"));
			shaft = addPart(ModelItems.block("shaft_half"));
			hourHand = addPart(ModelItems.block("cuckoo_clock/hour_hand"));
			minuteHand = addPart(ModelItems.block("cuckoo_clock/minute_hand"));
			leftDoor = addPart(ModelItems.block("cuckoo_clock/left_door"));
			rightDoor = addPart(ModelItems.block("cuckoo_clock/right_door"));
			pig = addPart(ModelItems.block("cuckoo_clock/pig"));
			creeper = addPart(ModelItems.block("cuckoo_clock/creeper"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			Direction facing = state.getValue(HORIZONTAL_FACING);
			base.orientation(Orientation.forHorizontal(facing)).apply(0);
			shaft.orientation(Orientation.fromSouth(facing.getOpposite())).spinAxis(facing.getAxis()).apply(0);
			lastHour = lastMinute = lastDoorAngle = Float.NaN;
			lastFigure = null;
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof CuckooClockBlockEntity clock))
				return;
			Direction facing = state.getValue(HORIZONTAL_FACING);
			shaft.spin(time, clock.getSpeed(), Orientation.cogOffset(false, facing.getAxis(), pos.getX(), pos.getY(), pos.getZ()));

			// Hands: only resend when they moved by about a degree
			float hourAngle = clock.hourHand.getValue();
			float minuteAngle = clock.minuteHand.getValue();
			if (Float.isNaN(lastHour) || Math.abs(AngleHelper.getShortestAngleDiff(lastHour, hourAngle)) >= 1) {
				lastHour = hourAngle;
				hourHand.setMatrix(rotateHand(hourAngle, facing), 3);
			}
			if (Float.isNaN(lastMinute) || Math.abs(AngleHelper.getShortestAngleDiff(lastMinute, minuteAngle)) >= 1) {
				lastMinute = minuteAngle;
				minuteHand.setMatrix(rotateHand(minuteAngle, facing), 3);
			}

			// Doors
			float angle = 0;
			CuckooClockBlockEntity.Animation animation = clock.animationType;
			if (animation != CuckooClockBlockEntity.Animation.NONE) {
				float value = clock.animationProgress.getValue();
				int step = animation == CuckooClockBlockEntity.Animation.SURPRISE ? 3 : 15;
				for (int phase = 30; phase <= 60; phase += step) {
					float local = value - phase;
					if (local < -step / 3)
						continue;
					else if (local < 0)
						angle = Mth.lerp(((value - (phase - 5)) / 5), 0, 135);
					else if (local < step / 3)
						angle = 135;
					else if (local < 2 * step / 3)
						angle = Mth.lerp(((value - (phase + 5)) / 5), 135, 0);
				}
			}
			if (lastDoorAngle != angle || Float.isNaN(lastDoorAngle)) {
				lastDoorAngle = angle;
				leftDoor.setMatrix(rotateDoor(angle, true, facing), 1);
				rightDoor.setMatrix(rotateDoor(angle, false, facing), 1);
			}

			// Figure
			if (animation != CuckooClockBlockEntity.Animation.NONE) {
				float offset = -(angle / 135) * 1 / 2f + 10 / 16f;
				Matrix4f m = new Matrix4f()
					.rotate(Orientation.horizontalAngle(facing.getCounterClockWise()))
					.translate(offset, 0, 0);
				Part shown = animation == CuckooClockBlockEntity.Animation.PIG ? pig : creeper;
				Part hidden = shown == pig ? creeper : pig;
				if (lastFigure != animation)
					hidden.setMatrix(new Matrix4f().scale(0), 0);
				if (lastFigure != animation || lastOffset != offset) {
					lastOffset = offset;
					shown.setMatrix(m, 1);
				}
			} else if (lastFigure != animation) {
				pig.setMatrix(new Matrix4f().scale(0), 0);
				creeper.setMatrix(new Matrix4f().scale(0), 0);
			}
			lastFigure = animation;
		}

		private static Matrix4f rotateHand(float angle, Direction facing) {
			float pivotX = 2 / 16f - .5f;
			float pivotY = 6 / 16f - .5f;
			float pivotZ = 8 / 16f - .5f;
			return new Matrix4f()
				.rotate(Orientation.horizontalAngle(facing.getCounterClockWise()))
				.translate(pivotX, pivotY, pivotZ)
				.rotateX(AngleHelper.rad(angle))
				.translate(-pivotX, -pivotY, -pivotZ);
		}

		private static Matrix4f rotateDoor(float angle, boolean left, Direction facing) {
			float pivotX = 2 / 16f - .5f;
			float pivotY = -.5f;
			float pivotZ = (left ? 6 : 10) / 16f - .5f;
			return new Matrix4f()
				.rotate(Orientation.horizontalAngle(facing.getCounterClockWise()))
				.translate(pivotX, pivotY, pivotZ)
				.rotateY(AngleHelper.rad(angle) * (left ? -1 : 1))
				.translate(-pivotX, -pivotY, -pivotZ);
		}
	}
}
