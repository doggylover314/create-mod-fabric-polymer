package dev.createpolymer.kinetics.flywheel;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.kinetics.base.RotatedPillarKineticBlock;
import dev.createpolymer.registry.feature.KineticsExtraRegistry;
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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Ported from Create's FlywheelBlock (MIT, The Create Team). */
public class FlywheelBlock extends RotatedPillarKineticBlock implements IBE<FlywheelBlockEntity> {
	// AllShapes.LARGE_GEAR: the large gear slab plus a six voxel pole along the axis
	public static final Map<Axis, VoxelShape> SHAPE = ShapeUtil.forAxis(
		Shapes.or(ShapeUtil.box(0, 6, 0, 16, 10, 16), ShapeUtil.box(5, 0, 5, 11, 16, 11)));

	public FlywheelBlock(Properties properties) {
		super(properties);
	}

	@Override
	public Class<FlywheelBlockEntity> getBlockEntityClass() {
		return FlywheelBlockEntity.class;
	}

	@Override
	protected VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		return SHAPE.get(pState.getValue(AXIS));
	}

	@Override
	public BlockEntityType<? extends FlywheelBlockEntity> getBlockEntityType() {
		return KineticsExtraRegistry.FLYWHEEL_BE;
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face.getAxis() == getRotationAxis(state);
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return state.getValue(AXIS);
	}

	@Override
	protected BlockState particleState() {
		return Blocks.GOLD_BLOCK.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/** Create's FlywheelVisual: the shaft turns with the network, the wheel with its own smoothed angle. */
	public static class Model extends BaseModel {
		private final Part shaft;
		private final Part wheel;

		public Model(BlockState state) {
			super(state);
			shaft = addPart(ModelItems.block("shaft"));
			wheel = addPart(ModelItems.block("flywheel/polymer_wheel"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			Axis axis = state.getValue(AXIS);
			shaft.orientation(Orientation.forAxis(axis)).spinAxis(axis).apply(0);
			wheel.orientation(Orientation.forAxis(axis)).spinAxis(axis).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof FlywheelBlockEntity flywheel))
				return;
			Axis axis = state.getValue(AXIS);
			shaft.spin(time, flywheel.getSpeed(), Orientation.cogOffset(false, axis, pos.getX(), pos.getY(), pos.getZ()));
			// angle of the next tick; the client interpolates to it
			wheel.angle(flywheel.getAngle() + flywheel.getVisualSpeed() * 3 / 10f).apply(1);
		}
	}
}
