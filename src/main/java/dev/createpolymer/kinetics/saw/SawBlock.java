package dev.createpolymer.kinetics.saw;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.joml.Quaternionf;

import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilterDisplay;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.kinetics.base.DirectionalAxisKineticBlock;
import dev.createpolymer.registry.feature.SawRegistry;
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
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Ported from Create's SawBlock (MIT, The Create Team). */
public class SawBlock extends DirectionalAxisKineticBlock implements IBE<SawBlockEntity> {
	public static final BooleanProperty FLIPPED = BooleanProperty.create("flipped");

	/** Create's AllShapes.CASING_12PX */
	public static final Map<Direction, VoxelShape> CASING_12PX = ShapeUtil.forDirectional(ShapeUtil.box(0, 0, 0, 16, 12, 16));

	public SawBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FLIPPED, false));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(FLIPPED));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState stateForPlacement = super.getStateForPlacement(context);
		Direction facing = stateForPlacement.getValue(FACING);
		return stateForPlacement.setValue(FLIPPED, facing.getAxis() == Axis.Y && context.getHorizontalDirection()
			.getAxisDirection() == AxisDirection.POSITIVE);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rot) {
		BlockState newState = super.rotate(state, rot);
		if (state.getValue(FACING)
			.getAxis() != Axis.Y)
			return newState;

		if (rot.ordinal() % 2 == 1 && (rot == Rotation.CLOCKWISE_90) != state.getValue(AXIS_ALONG_FIRST_COORDINATE))
			newState = newState.cycle(FLIPPED);
		if (rot == Rotation.CLOCKWISE_180)
			newState = newState.cycle(FLIPPED);

		return newState;
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirrorIn) {
		BlockState newState = super.mirror(state, mirrorIn);
		if (state.getValue(FACING)
			.getAxis() != Axis.Y)
			return newState;

		boolean alongX = state.getValue(AXIS_ALONG_FIRST_COORDINATE);
		if (alongX && mirrorIn == Mirror.FRONT_BACK)
			newState = newState.cycle(FLIPPED);
		if (!alongX && mirrorIn == Mirror.LEFT_RIGHT)
			newState = newState.cycle(FLIPPED);

		return newState;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		return CASING_12PX.get(state.getValue(FACING));
	}

	/** Polymer would otherwise answer with the collision of the barrier players see. */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return CASING_12PX.get(state.getValue(FACING));
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (player.isSpectator() || !stack.isEmpty())
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (state.getOptionalValue(FACING)
			.orElse(Direction.WEST) != Direction.UP)
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		return onBlockEntityUseItemOn(level, pos, be -> {
			for (int i = 0; i < be.inventory.getSlots(); i++) {
				ItemStack heldItemStack = be.inventory.getStackInSlot(i);
				if (!level.isClientSide() && !heldItemStack.isEmpty())
					player.getInventory()
						.placeItemBackInInventory(heldItemStack, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			be.inventory.clear();
			be.notifyUpdate();
			return InteractionResult.SUCCESS;
		});
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
			entityIn.hurtServer(serverLevel, SawRegistry.sawDamage(worldIn), (float) getDamage(be.getSpeed()));
		});
	}

	/** Create's DrillBlock.getDamage */
	public static double getDamage(float speed) {
		float speedAbs = Math.abs(speed);
		double sub1 = Math.min(speedAbs / 16, 2);
		double sub2 = Math.min(speedAbs / 32, 4);
		double sub3 = Math.min(speedAbs / 64, 4);
		return Mth.clamp(sub1 + sub2 + sub3, 1, 10);
	}

	/** Create's updateEntityAfterFallOn: item entities that land on an upward saw are taken in. */
	@Override
	public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
		super.fallOn(level, state, pos, entity, fallDistance);
		if (!(entity instanceof ItemEntity itemEntity))
			return;
		if (level.isClientSide())
			return;

		withBlockEntityDo(level, pos, be -> {
			if (be.getSpeed() == 0)
				return;
			be.insertItem(itemEntity);
		});
	}

	public static boolean isHorizontal(BlockState state) {
		return state.getValue(FACING)
			.getAxis()
			.isHorizontal();
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return isHorizontal(state) ? state.getValue(FACING)
			.getAxis() : super.getRotationAxis(state);
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return isHorizontal(state) ? face == state.getValue(FACING)
			.getOpposite() : super.hasShaftTowards(world, pos, state, face);
	}

	@Override
	public Class<SawBlockEntity> getBlockEntityClass() {
		return SawBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends SawBlockEntity> getBlockEntityType() {
		return SawRegistry.SAW;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	// ---- Polymer ----

	@Override
	protected BlockState particleState() {
		return Blocks.ANDESITE.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/**
	 * Create's SawRenderer/SawVisual: the casing from the blockstate file, the shaft, the blade (which model
	 * depends on the rotation direction), the filter and, on upward saws, the items being cut.
	 */
	public static class Model extends BaseModel {
		private static final int BLADE_INACTIVE = 0, BLADE_ACTIVE = 1, BLADE_REVERSED = 2;

		private final Part body;
		private final Part shaft;
		private final Part blade;
		private final FilterDisplay filter = new FilterDisplay(this);
		private final SawItemDisplay items = new SawItemDisplay(this);
		private int bladeMode = -1;
		private boolean bladeHorizontal;

		public Model(BlockState state) {
			super(state);
			body = addPart(ModelItems.block("mechanical_saw/horizontal"));
			shaft = addPart(ModelItems.block(isHorizontal(state) ? "shaft_half" : "shaft"));
			blade = addPart(ModelItems.block("mechanical_saw/blade_horizontal_inactive"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			Direction facing = state.getValue(FACING);
			boolean horizontal = isHorizontal(state);

			showVariant(body, "mechanical_saw", state);

			shaft.setItem(ModelItems.block(horizontal ? "shaft_half" : "shaft"));
			if (horizontal)
				shaft.orientation(Orientation.fromSouth(facing.getOpposite()));
			else
				shaft.orientation(Orientation.forAxis(state.getValue(AXIS_ALONG_FIRST_COORDINATE) ? Axis.X : Axis.Z));
			shaft.spinAxis(horizontal ? facing.getAxis() : state.getValue(AXIS_ALONG_FIRST_COORDINATE) ? Axis.X : Axis.Z).apply(0);

			// CachedBuffers.partialFacing, then a quarter turn for vertical saws along the first coordinate
			Quaternionf q = new Quaternionf();
			if (!horizontal && state.getValue(AXIS_ALONG_FIRST_COORDINATE))
				q.rotateY((float) Math.PI / 2);
			q.rotateY(AngleHelper.rad(AngleHelper.horizontalAngle(facing)))
				.rotateX(AngleHelper.rad(AngleHelper.verticalAngle(facing)));
			blade.orientation(q).apply(0);
			bladeMode = -1;
			bladeHorizontal = horizontal;
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof SawBlockEntity saw))
				return;
			float speed = saw.getSpeed();

			Axis axis = ((SawBlock) state.getBlock()).getRotationAxis(state);
			shaft.spin(time, speed, Orientation.cogOffset(false, axis, pos.getX(), pos.getY(), pos.getZ()));

			int mode = speed > 0 ? BLADE_ACTIVE : speed < 0 ? BLADE_REVERSED : BLADE_INACTIVE;
			if (mode != bladeMode) {
				bladeMode = mode;
				String name = switch (mode) {
					case BLADE_ACTIVE -> "active";
					case BLADE_REVERSED -> "reversed";
					default -> "inactive";
				};
				blade.setItem(ModelItems.block("mechanical_saw/blade_" + (bladeHorizontal ? "horizontal_" : "vertical_") + name));
			}

			filter.update(saw);
			items.render(saw, state);
		}
	}
}
