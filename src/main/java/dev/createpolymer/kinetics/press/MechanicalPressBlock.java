package dev.createpolymer.kinetics.press;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.kinetics.base.HorizontalKineticBlock;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.processing.basin.BasinBlockEntity;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Ported from Create's MechanicalPressBlock (MIT, The Create Team). */
public class MechanicalPressBlock extends HorizontalKineticBlock {
	public static final VoxelShape PROCESSOR_SHAPE = Shapes.join(Shapes.block(), ShapeUtil.box(4, 0, 4, 12, 16, 12), BooleanOp.ONLY_FIRST);
	public static final VoxelShape CASING_14PX = ShapeUtil.box(0, 2, 0, 16, 16, 16);

	public MechanicalPressBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
		return ModBlockEntities.MECHANICAL_PRESS;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		if (context instanceof EntityCollisionContext ecc && ecc.getEntity() instanceof Player)
			return CASING_14PX;
		return PROCESSOR_SHAPE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader worldIn, BlockPos pos) {
		return !(worldIn.getBlockEntity(pos.below()) instanceof BasinBlockEntity);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction preferredSide = getPreferredHorizontalFacing(context);
		if (preferredSide != null)
			return defaultBlockState().setValue(HORIZONTAL_FACING, preferredSide);
		return super.getStateForPlacement(context);
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return state.getValue(HORIZONTAL_FACING).getAxis();
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face.getAxis() == state.getValue(HORIZONTAL_FACING).getAxis();
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	protected BlockState particleState() {
		return Blocks.ANDESITE.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	public static class Model extends BaseModel {
		private final Part body;
		private final Part head;
		private final Part shaft;

		public Model(BlockState state) {
			super(state);
			body = addPart(ModelItems.block("mechanical_press/block"));
			head = addPart(ModelItems.block("mechanical_press/head"));
			shaft = addPart(ModelItems.block("shaft"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			Direction facing = state.getValue(HORIZONTAL_FACING);
			body.orientation(Orientation.forHorizontal(facing)).apply(0);
			head.orientation(Orientation.horizontalAngle(facing)).apply(0);
			shaft.orientation(Orientation.forAxis(facing.getAxis())).spinAxis(facing.getAxis()).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof MechanicalPressBlockEntity press))
				return;
			Axis axis = state.getValue(HORIZONTAL_FACING).getAxis();
			shaft.spin(time, press.getSpeed(), Orientation.cogOffset(false, axis, pos.getX(), pos.getY(), pos.getZ()));
			float offset = press.getRenderedHeadOffset(0) * press.mode.headOffset;
			head.translation(0, -offset, 0).apply(1);
		}
	}
}
