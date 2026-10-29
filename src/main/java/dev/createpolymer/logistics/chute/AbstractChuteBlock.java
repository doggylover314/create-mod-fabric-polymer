package dev.createpolymer.logistics.chute;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.item.ItemHelper;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import dev.createpolymer.logistics.box.PackageEntity;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.visual.PolymerModelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class AbstractChuteBlock extends Block implements IWrenchable, IBE<ChuteBlockEntity>, PolymerModelBlock {

	public AbstractChuteBlock(Properties p_i48440_1_) {
		super(p_i48440_1_);
	}

	public static boolean isChute(BlockState state) {
		return state.getBlock() instanceof AbstractChuteBlock;
	}

	public static boolean isOpenChute(BlockState state) {
		return isChute(state) && ((AbstractChuteBlock) state.getBlock()).isOpen(state);
	}

	public static boolean isTransparentChute(BlockState state) {
		return isChute(state) && ((AbstractChuteBlock) state.getBlock()).isTransparent(state);
	}

	@Nullable
	public static Direction getChuteFacing(BlockState state) {
		return !isChute(state) ? null : ((AbstractChuteBlock) state.getBlock()).getFacing(state);
	}

	public Direction getFacing(BlockState state) {
		return Direction.DOWN;
	}

	public boolean isOpen(BlockState state) {
		return true;
	}

	public boolean isTransparent(BlockState state) {
		return false;
	}

	/**
	 * Create calls this from Block.updateEntityAfterFallOn, which Minecraft 26.3 no longer has: the chute's block
	 * entity looks for item entities resting on top of it instead.
	 */
	public static void updateEntityAfterFallOn(Level level, Entity entityIn) {
		ItemStack stack = ItemHelper.fromItemEntity(entityIn);
		if (stack.isEmpty())
			return;
		if (level.isClientSide())
			return;
		if (!entityIn.isAlive())
			return;
		BlockPos pos = BlockPos.containing(entityIn.position()
			.add(0, 0.5f, 0))
			.below();
		DirectBeltInputBehaviour input = BlockEntityBehaviour.get(level, pos, DirectBeltInputBehaviour.TYPE);
		if (input == null)
			return;
		if (!input.canInsertFromSide(Direction.UP))
			return;
		if (!PackageEntity.centerPackage(entityIn, Vec3.atBottomCenterOf(pos.above())))
			return;
		ItemStack remainder = input.handleInsertion(stack, Direction.UP, false);
		if (remainder.isEmpty()) {
			entityIn.discard();
		} else if (remainder.getCount() < stack.getCount() && entityIn instanceof ItemEntity)
			((ItemEntity) entityIn).setItem(remainder);
	}

	@Override
	protected void onPlace(BlockState state, Level world, BlockPos pos, BlockState p_220082_4_, boolean p_220082_5_) {
		withBlockEntityDo(world, pos, ChuteBlockEntity::onAdded);
		updateDiagonalNeighbour(state, world, pos);
	}

	protected void updateDiagonalNeighbour(BlockState state, Level world, BlockPos pos) {
		if (!isChute(state))
			return;
		AbstractChuteBlock block = (AbstractChuteBlock) state.getBlock();
		Direction facing = block.getFacing(state);
		BlockPos toUpdate = pos.below();
		if (facing.getAxis()
			.isHorizontal())
			toUpdate = toUpdate.relative(facing.getOpposite());

		BlockState stateToUpdate = world.getBlockState(toUpdate);
		if (isChute(stateToUpdate) && !world.getBlockTicks()
			.hasScheduledTick(toUpdate, stateToUpdate.getBlock()))
			world.scheduleTick(toUpdate, stateToUpdate.getBlock(), 1);
	}

	/** Create's onRemove, after IBE.onRemove (the block entity cleans up in preRemoveSideEffects). */
	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean isMoving) {
		super.affectNeighborsAfterRemoval(state, world, pos, isMoving);

		if (world.getBlockState(pos).is(state.getBlock()))
			return;

		updateDiagonalNeighbour(state, world, pos);

		for (Direction direction : Iterate.horizontalDirections) {
			BlockPos toUpdate = pos.above()
				.relative(direction);
			BlockState stateToUpdate = world.getBlockState(toUpdate);
			if (isChute(stateToUpdate) && !world.getBlockTicks()
				.hasScheduledTick(toUpdate, stateToUpdate.getBlock()))
				world.scheduleTick(toUpdate, stateToUpdate.getBlock(), 1);
		}
	}

	@Override
	protected void tick(BlockState pState, ServerLevel pLevel, BlockPos pPos, RandomSource pRandom) {
		BlockState updated = updateChuteState(pState, pLevel.getBlockState(pPos.above()), pLevel, pPos);
		if (pState != updated)
			pLevel.setBlockAndUpdate(pPos, updated);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
		Direction direction, BlockPos neighbourPos, BlockState above, RandomSource random) {
		if (direction != Direction.UP)
			return state;
		return updateChuteState(state, above, level, pos);
	}

	/**
	 * 26.3 does not say which neighbour changed, so the block below is assumed to be the one; the block entity only
	 * flags its air flow for a rebuild, which is cheap.
	 */
	@Override
	protected void neighborChanged(BlockState p_220069_1_, Level world, BlockPos pos, Block p_220069_4_,
		@Nullable Orientation orientation, boolean p_220069_6_) {
		withBlockEntityDo(world, pos, ChuteBlockEntity::blockBelowChanged);
	}

	public abstract BlockState updateChuteState(BlockState state, BlockState above, BlockGetter world, BlockPos pos);

	@Override
	protected VoxelShape getShape(BlockState p_220053_1_, BlockGetter p_220053_2_, BlockPos p_220053_3_,
		CollisionContext p_220053_4_) {
		return ChuteShapes.getShape(p_220053_1_);
	}

	/** Polymer would otherwise answer with the collision of the barrier players see. */
	@Override
	protected VoxelShape getCollisionShape(BlockState p_220071_1_, BlockGetter p_220071_2_, BlockPos p_220071_3_,
		CollisionContext p_220071_4_) {
		return ChuteShapes.getCollisionShape(p_220071_1_);
	}

	@Override
	public Class<ChuteBlockEntity> getBlockEntityClass() {
		return ChuteBlockEntity.class;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (!stack.isEmpty())
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (level.isClientSide())
			return InteractionResult.SUCCESS;

		return onBlockEntityUseItemOn(level, pos, be -> {
			if (be.item.isEmpty())
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			player.getInventory()
				.placeItemBackInInventory(be.item, Prediction.SERVER_ONLY);
			be.setItem(ItemStack.EMPTY);
			return InteractionResult.SUCCESS;
		});
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.NETHERITE_BLOCK.defaultBlockState();
	}

}
