package dev.createpolymer.logistics.depot;

import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.foundation.item.ItemHelper;
import dev.createpolymer.kinetics.base.HorizontalKineticBlock;
import dev.createpolymer.logistics.chute.ProperWaterloggedBlock;
import dev.createpolymer.logistics.depot.EjectorBlockEntity.State;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.EjectorRegistry;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.VecHelper;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class EjectorBlock extends HorizontalKineticBlock implements IBE<EjectorBlockEntity>, ProperWaterloggedBlock {

	public EjectorBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
		super.createBlockStateDefinition(pBuilder.add(WATERLOGGED));
	}

	@Override
	protected FluidState getFluidState(BlockState pState) {
		return fluidState(pState);
	}

	@Override
	protected BlockState updateShape(BlockState pState, LevelReader level, ScheduledTickAccess ticks, BlockPos pCurrentPos,
		Direction pDirection, BlockPos pNeighborPos, BlockState pNeighborState, RandomSource random) {
		updateWater(level, ticks, pState, pCurrentPos);
		return pState;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext pContext) {
		return withWater(super.getStateForPlacement(pContext), pContext);
	}

	@Override
	protected VoxelShape getShape(BlockState p_220053_1_, BlockGetter p_220053_2_, BlockPos p_220053_3_,
		CollisionContext p_220053_4_) {
		return DepotBlock.SHAPE;
	}

	/** Polymer would otherwise answer with the collision of the barrier players see. */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return DepotBlock.SHAPE;
	}

	@Override
	protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block p_220069_4_,
		net.minecraft.world.level.redstone.@Nullable Orientation orientation, boolean p_220069_6_) {
		withBlockEntityDo(world, pos, EjectorBlockEntity::updateSignal);
	}

	/**
	 * Create's Block.updateEntityAfterFallOn: an entity rests on the ejector. 26.3 has no such hook, so the
	 * block entity calls this for the entities it finds on top of it every tick.
	 */
	public void updateEntityAfterFallOn(BlockGetter worldIn, Entity entityIn) {
		BlockPos position = entityIn.getOnPosLegacy();
		if (!worldIn.getBlockState(position).is(EjectorRegistry.WEIGHTED_EJECTOR))
			return;
		if (!entityIn.isAlive())
			return;
		if (entityIn.isSuppressingBounce())
			return;
		if (!ItemHelper.fromItemEntity(entityIn).isEmpty()) {
			SharedDepotBlockMethods.onLanded(worldIn, entityIn);
			return;
		}
		if (!entityIn.onGround())
			return;

		Optional<EjectorBlockEntity> teProvider = getBlockEntityOptional(worldIn, position);
		if (!teProvider.isPresent())
			return;

		EjectorBlockEntity ejectorBlockEntity = teProvider.get();
		if (ejectorBlockEntity.getState() == State.RETRACTING)
			return;
		if (ejectorBlockEntity.powered)
			return;
		if (ejectorBlockEntity.launcher.getHorizontalDistance() == 0)
			return;

		// Players are not moved by the server: they are put in the middle of the plate when they are launched
		if (!(entityIn instanceof ServerPlayer)) {
			entityIn.setOnGround(false);
			Vec3 center = VecHelper.getCenterOf(position)
				.add(0, 7 / 16f, 0);
			Vec3 positionVec = entityIn.position();
			double diff = center.distanceTo(positionVec);
			entityIn.setDeltaMovement(0, -0.125, 0);
			Vec3 vec = center.add(positionVec)
				.scale(.5f);
			if (diff > 4 / 16f) {
				entityIn.setPos(vec.x, vec.y, vec.z);
				return;
			}
		}

		ejectorBlockEntity.activate();
		ejectorBlockEntity.notifyUpdate();
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (stack.is(ModItems.WRENCH))
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		return SharedDepotBlockMethods.onUse(stack, state, level, pos, player, hand, hitResult);
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return state.getValue(HORIZONTAL_FACING)
			.getClockWise()
			.getAxis();
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return getRotationAxis(state) == face.getAxis();
	}

	@Override
	public Class<EjectorBlockEntity> getBlockEntityClass() {
		return EjectorBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends EjectorBlockEntity> getBlockEntityType() {
		return EjectorRegistry.WEIGHTED_EJECTOR_BLOCK_ENTITY;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState blockState, Level worldIn, BlockPos pos, Direction direction) {
		return SharedDepotBlockMethods.getComparatorInputOverride(blockState, worldIn, pos);
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

	public static class Model extends BaseModel {
		private final Part body;
		private final Part shaft;
		private final Part plate;
		private final DepotRenderer items;
		private final EjectorRenderer flying;
		private float lastAngle = Float.NaN;

		public Model(BlockState state) {
			super(state);
			body = addPart(ModelItems.block("weighted_ejector/block"));
			shaft = addPart(ModelItems.block("shaft"));
			plate = addPart(ModelItems.block("weighted_ejector/top"));
			items = new DepotRenderer(this);
			flying = new EjectorRenderer(this);
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			Direction facing = state.getValue(HORIZONTAL_FACING);
			Axis axis = facing.getClockWise().getAxis();
			showVariant(body, "weighted_ejector", state);
			shaft.orientation(Orientation.forAxis(axis)).spinAxis(axis).apply(0);
			lastAngle = Float.NaN;
			setLidAngle(facing, 0);
		}

		private void setLidAngle(Direction facing, float angle) {
			if (angle == lastAngle)
				return;
			boolean fresh = Float.isNaN(lastAngle);
			lastAngle = angle;
			Matrix4f lid = EjectorRenderer.applyLidAngle(facing, angle);
			plate.setMatrix(lid, fresh ? 0 : 1);
			// The held item rides on the plate (Create: DepotRenderer.renderItemsOf inside the lid transform)
			items.setTransform(new Matrix4f(lid)
				.rotateY((float) Math.toRadians(-180 - AngleHelper.horizontalAngle(facing))));
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof EjectorBlockEntity ejector))
				return;
			Direction facing = state.getValue(HORIZONTAL_FACING);
			Axis axis = facing.getClockWise().getAxis();
			shaft.spin(time, ejector.getSpeed(), Orientation.cogOffset(false, axis, pos.getX(), pos.getY(), pos.getZ()));
			setLidAngle(facing, ejector.getLidProgress(1) * 70);
			items.render(ejector.depotBehaviour, ejector.getDataVersion());
			flying.render(ejector);
		}
	}
}
