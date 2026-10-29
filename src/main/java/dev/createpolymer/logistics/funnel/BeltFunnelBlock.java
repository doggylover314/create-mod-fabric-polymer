package dev.createpolymer.logistics.funnel;

import java.util.function.Supplier;

import dev.createpolymer.foundation.block.ProperWaterloggedBlock;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.kinetics.belt.BeltBlock;
import dev.createpolymer.kinetics.belt.BeltSlope;
import dev.createpolymer.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import dev.createpolymer.util.Lang;
import dev.createpolymer.util.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BeltFunnelBlock extends AbstractHorizontalFunnelBlock {

	private Supplier<? extends FunnelBlock> parent;

	public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);

	public enum Shape implements StringRepresentable {
		RETRACTED(FunnelShapes.BELT_FUNNEL_RETRACTED),
		EXTENDED(FunnelShapes.BELT_FUNNEL_EXTENDED),
		PUSHING(FunnelShapes.BELT_FUNNEL_PERPENDICULAR),
		PULLING(FunnelShapes.BELT_FUNNEL_PERPENDICULAR);

		VoxelShaper shaper;

		private Shape(VoxelShaper shaper) {
			this.shaper = shaper;
		}

		@Override
		public String getSerializedName() {
			return Lang.asId(name());
		}
	}

	public BeltFunnelBlock(Supplier<? extends FunnelBlock> parent, Properties p_i48377_1_) {
		super(p_i48377_1_);
		this.parent = parent;
		registerDefaultState(defaultBlockState().setValue(SHAPE, Shape.RETRACTED));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> p_206840_1_) {
		super.createBlockStateDefinition(p_206840_1_.add(SHAPE));
	}

	public boolean isOfSameType(FunnelBlock otherFunnel) {
		return parent.get() == otherFunnel;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter p_220053_2_, BlockPos p_220053_3_,
		CollisionContext p_220053_4_) {
		return state.getValue(SHAPE).shaper.get(state.getValue(HORIZONTAL_FACING));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState p_220071_1_, BlockGetter p_220071_2_, BlockPos p_220071_3_,
		CollisionContext p_220071_4_) {
		if (p_220071_4_ instanceof EntityCollisionContext
			&& ((EntityCollisionContext) p_220071_4_).getEntity() instanceof ItemEntity
			&& (p_220071_1_.getValue(SHAPE) == Shape.PULLING || p_220071_1_.getValue(SHAPE) == Shape.PUSHING))
			return FunnelShapes.FUNNEL_COLLISION.get(getFacing(p_220071_1_));
		return getShape(p_220071_1_, p_220071_2_, p_220071_3_, p_220071_4_);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		BlockState stateForPlacement = super.getStateForPlacement(ctx);
		BlockPos pos = ctx.getClickedPos();
		Level world = ctx.getLevel();
		Direction facing = ctx.getClickedFace()
			.getAxis()
			.isHorizontal() ? ctx.getClickedFace() : ctx.getHorizontalDirection();

		BlockState state = stateForPlacement.setValue(HORIZONTAL_FACING, facing);
		boolean sneaking = ctx.getPlayer() != null && ctx.getPlayer()
			.isShiftKeyDown();
		return state.setValue(SHAPE, getShapeForPosition(world, pos, facing, !sneaking));
	}

	public static Shape getShapeForPosition(BlockGetter world, BlockPos pos, Direction facing, boolean extracting) {
		BlockPos posBelow = pos.below();
		BlockState stateBelow = world.getBlockState(posBelow);
		Shape perpendicularState = extracting ? Shape.PUSHING : Shape.PULLING;
		if (!(stateBelow.getBlock() instanceof BeltBlock))
			return perpendicularState;
		Direction movementFacing = stateBelow.getValue(BeltBlock.HORIZONTAL_FACING);
		return movementFacing.getAxis() != facing.getAxis() ? perpendicularState : Shape.RETRACTED;
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(parent.get());
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader world, ScheduledTickAccess ticks, BlockPos pos,
		Direction direction, BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
		updateWater(world, ticks, state, pos);
		if (!isOnValidBelt(state, world, pos)) {
			BlockState parentState = ProperWaterloggedBlock.withWater(world, parent.get()
				.defaultBlockState(), pos);
			if (state.getOptionalValue(POWERED)
				.orElse(false))
				parentState = parentState.setValue(POWERED, true);
			if (state.getValue(SHAPE) == Shape.PUSHING)
				parentState = parentState.setValue(FunnelBlock.EXTRACTING, true);
			return parentState.setValue(FunnelBlock.FACING, state.getValue(HORIZONTAL_FACING));
		}
		Shape updatedShape =
			getShapeForPosition(world, pos, state.getValue(HORIZONTAL_FACING), state.getValue(SHAPE) == Shape.PUSHING);
		Shape currentShape = state.getValue(SHAPE);
		if (updatedShape == currentShape)
			return state;

		// Don't revert wrenched states
		if (updatedShape == Shape.PUSHING && currentShape == Shape.PULLING)
			return state;
		if (updatedShape == Shape.RETRACTED && currentShape == Shape.EXTENDED)
			return state;

		return state.setValue(SHAPE, updatedShape);
	}

	public static boolean isOnValidBelt(BlockState state, LevelReader world, BlockPos pos) {
		BlockState stateBelow = world.getBlockState(pos.below());
		if ((stateBelow.getBlock() instanceof BeltBlock))
			return BeltBlock.canTransportObjects(stateBelow);
		DirectBeltInputBehaviour directBeltInputBehaviour =
			BlockEntityBehaviour.get(world, pos.below(), DirectBeltInputBehaviour.TYPE);
		if (directBeltInputBehaviour == null)
			return false;
		return directBeltInputBehaviour.canSupportBeltFunnels();
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		Level world = context.getLevel();
		if (world.isClientSide())
			return InteractionResult.SUCCESS;

		Shape shape = state.getValue(SHAPE);
		Shape newShape = shape;
		if (shape == Shape.PULLING)
			newShape = Shape.PUSHING;
		else if (shape == Shape.PUSHING)
			newShape = Shape.PULLING;
		else if (shape == Shape.EXTENDED)
			newShape = Shape.RETRACTED;
		else if (shape == Shape.RETRACTED) {
			BlockState belt = world.getBlockState(context.getClickedPos()
				.below());
			if (belt.getBlock() instanceof BeltBlock && belt.getValue(BeltBlock.SLOPE) != BeltSlope.HORIZONTAL)
				newShape = Shape.RETRACTED;
			else
				newShape = Shape.EXTENDED;
		}

		if (newShape == shape)
			return InteractionResult.SUCCESS;

		world.setBlockAndUpdate(context.getClickedPos(), state.setValue(SHAPE, newShape));
		return InteractionResult.SUCCESS;
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return parent.get()
			.particleState();
	}

}
