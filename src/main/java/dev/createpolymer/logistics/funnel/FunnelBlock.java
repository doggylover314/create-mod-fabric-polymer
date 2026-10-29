package dev.createpolymer.logistics.funnel;

import dev.createpolymer.foundation.block.ProperWaterloggedBlock;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.foundation.item.ItemHelper;
import dev.createpolymer.logistics.box.PackageEntity;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class FunnelBlock extends AbstractDirectionalFunnelBlock {

	public static final BooleanProperty EXTRACTING = BooleanProperty.create("extracting");

	public FunnelBlock(Properties p_i48415_1_) {
		super(p_i48415_1_);
		registerDefaultState(defaultBlockState().setValue(EXTRACTING, false));
	}

	public abstract BlockState getEquivalentBeltFunnel(BlockGetter world, BlockPos pos, BlockState state);

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = super.getStateForPlacement(context);

		boolean sneak = context.getPlayer() != null && context.getPlayer()
			.isShiftKeyDown();
		state = state.setValue(EXTRACTING, !sneak);

		for (Direction direction : context.getNearestLookingDirections()) {
			BlockState blockstate = state.setValue(FACING, direction.getOpposite());
			if (blockstate.canSurvive(context.getLevel(), context.getClickedPos()))
				return blockstate.setValue(POWERED, state.getValue(POWERED));
		}

		return state;
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(EXTRACTING));
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		// Create also keeps a mechanical arm from inserting itself here; arms are not ported yet
		boolean shouldntInsertItem = !canInsertIntoFunnel(state);

		if (stack.is(ModItems.WRENCH))
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		if (hitResult.getDirection() == getFunnelFacing(state) && !shouldntInsertItem) {
			if (!level.isClientSide())
				withBlockEntityDo(level, pos, be -> {
					ItemStack toInsert = stack.copy();
					ItemStack remainder = tryInsert(level, pos, toInsert, false);
					if (!ItemStack.matches(remainder, toInsert) || remainder.getCount() != stack.getCount())
						player.setItemInHand(hand, remainder);
				});
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		Level world = context.getLevel();
		if (!world.isClientSide())
			world.setBlockAndUpdate(context.getClickedPos(), state.cycle(EXTRACTING));
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void entityInside(BlockState state, Level worldIn, BlockPos pos, Entity entityIn,
		InsideBlockEffectApplier effectApplier, boolean isPrecise) {
		if (worldIn.isClientSide())
			return;
		ItemStack stack = ItemHelper.fromItemEntity(entityIn);
		if (stack.isEmpty())
			return;
		if (!canInsertIntoFunnel(state))
			return;

		Direction direction = getFunnelFacing(state);
		Vec3 openPos = VecHelper.getCenterOf(pos)
			.add(Vec3.atLowerCornerOf(direction.getUnitVec3i())
				.scale(entityIn instanceof ItemEntity ? -.25f : -.125f));
		Vec3 diff = entityIn.position()
			.subtract(openPos);
		double projectedDiff = direction.getAxis()
			.choose(diff.x, diff.y, diff.z);
		if (projectedDiff < 0 == (direction.getAxisDirection() == AxisDirection.POSITIVE))
			return;
		float yOffset = direction == Direction.UP ? 0.25f : direction == Direction.DOWN ? -0.5f : -0.5f;
		FilteringBehaviour filter = BlockEntityBehaviour.get(worldIn, pos, FilteringBehaviour.TYPE);
		if (filter.test(stack) && !PackageEntity.centerPackage(entityIn, openPos.add(0, yOffset, 0)))
			return;

		ItemStack remainder = tryInsert(worldIn, pos, stack, false);
		if (remainder.isEmpty())
			entityIn.discard();
		if (remainder.getCount() < stack.getCount() && entityIn instanceof ItemEntity)
			((ItemEntity) entityIn).setItem(remainder);
	}

	protected boolean canInsertIntoFunnel(BlockState state) {
		return !state.getValue(POWERED) && !state.getValue(EXTRACTING);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		Direction facing = state.getValue(FACING);
		return facing == Direction.DOWN ? FunnelShapes.FUNNEL_CEILING
			: facing == Direction.UP ? FunnelShapes.FUNNEL_FLOOR : FunnelShapes.FUNNEL_WALL.get(facing);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		if (context instanceof EntityCollisionContext
			&& ((EntityCollisionContext) context).getEntity() instanceof ItemEntity && getFacing(state).getAxis()
				.isHorizontal())
			return FunnelShapes.FUNNEL_COLLISION.get(getFacing(state));
		return getShape(state, world, pos, context);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader world, ScheduledTickAccess ticks, BlockPos pos,
		Direction direction, BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
		updateWater(world, ticks, state, pos);
		if (getFacing(state).getAxis()
			.isVertical() || direction != Direction.DOWN)
			return state;
		BlockState equivalentFunnel =
			ProperWaterloggedBlock.withWater(world, getEquivalentBeltFunnel(null, null, state), pos);
		if (BeltFunnelBlock.isOnValidBelt(equivalentFunnel, world, pos))
			return equivalentFunnel.setValue(BeltFunnelBlock.SHAPE,
				BeltFunnelBlock.getShapeForPosition(world, pos, getFacing(state), state.getValue(EXTRACTING)));
		return state;
	}

}
