package dev.createpolymer.kinetics.drill;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.registry.feature.CrushingRegistry;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ported from Create's DrillBlock (MIT, The Create Team).
 * The placement helper (extending a row of drills) is client-assisted in Create and is not ported.
 */
public class DrillBlock extends DirectionalKineticBlock implements IBE<DrillBlockEntity>, SimpleWaterloggedBlock {
	/** Create's AllShapes.CASING_12PX */
	public static final Map<Direction, VoxelShape> CASING_12PX = ShapeUtil.forDirectional(ShapeUtil.box(0, 0, 0, 16, 12, 16));

	public DrillBlock(Properties properties) {
		super(properties);
		registerDefaultState(super.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false));
	}

	@Override
	protected void entityInside(BlockState state, Level worldIn, BlockPos pos, Entity entityIn,
								InsideBlockEffectApplier effectApplier, boolean isPrecise) {
		if (entityIn instanceof ItemEntity)
			return;
		if (!(worldIn instanceof ServerLevel serverLevel))
			return;
		if (!new AABB(pos).deflate(.1f)
			.intersects(entityIn.getBoundingBox()))
			return;
		withBlockEntityDo(worldIn, pos, be -> {
			if (be.getSpeed() == 0)
				return;
			entityIn.hurtServer(serverLevel, CrushingRegistry.drillDamage(worldIn), (float) getDamage(be.getSpeed()));
		});
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		return CASING_12PX.get(state.getValue(FACING));
	}

	@Override
	protected void neighborChanged(BlockState state, Level worldIn, BlockPos pos, Block blockIn,
								   net.minecraft.world.level.redstone.@Nullable Orientation orientation, boolean isMoving) {
		withBlockEntityDo(worldIn, pos, DrillBlockEntity::destroyNextTick);
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return state.getValue(FACING)
			.getAxis();
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face == state.getValue(FACING)
			.getOpposite();
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState();
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(BlockStateProperties.WATERLOGGED);
		super.createBlockStateDefinition(builder);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
									 Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (state.getValue(BlockStateProperties.WATERLOGGED))
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		return state;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
		return super.getStateForPlacement(context).setValue(BlockStateProperties.WATERLOGGED, fluidState.getType() == Fluids.WATER);
	}

	public static double getDamage(float speed) {
		float speedAbs = Math.abs(speed);
		double sub1 = Math.min(speedAbs / 16, 2);
		double sub2 = Math.min(speedAbs / 32, 4);
		double sub3 = Math.min(speedAbs / 64, 4);
		return Mth.clamp(sub1 + sub2 + sub3, 1, 10);
	}

	@Override
	public Class<DrillBlockEntity> getBlockEntityClass() {
		return DrillBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends DrillBlockEntity> getBlockEntityType() {
		return CrushingRegistry.DRILL_BLOCK_ENTITY;
	}

	@Override
	protected BlockState particleState() {
		return Blocks.ANDESITE.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/** Casing with the spinning head along the facing axis (Create's DrillRenderer). */
	public static class Model extends BaseModel {
		private final Part casing;
		private final Part head;

		public Model(BlockState state) {
			super(state);
			casing = addPart(ModelItems.block("mechanical_drill/block"));
			head = addPart(ModelItems.block("mechanical_drill/head"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			Direction facing = state.getValue(FACING);
			casing.orientation(Orientation.forFacing(facing)).apply(0);
			head.orientation(Orientation.fromSouth(facing)).spinAxis(facing.getAxis()).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof KineticBlockEntity kbe))
				return;
			Axis axis = state.getValue(FACING).getAxis();
			head.spin(time, kbe.getSpeed(),
				Orientation.cogOffset(false, axis, pos.getX(), pos.getY(), pos.getZ()));
		}
	}

}
