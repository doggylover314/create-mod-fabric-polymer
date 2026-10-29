package dev.createpolymer.kinetics.saw;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.contraptions.behaviour.MovementContext;
import dev.createpolymer.foundation.item.handler.ItemHandlerHelper;
import dev.createpolymer.kinetics.base.BlockBreakingMovementBehaviour;
import dev.createpolymer.registry.feature.SawRegistry;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's SawMovementBehaviour (MIT, The Create Team). Dynamic Trees support is not ported.
 */
public class SawMovementBehaviour extends BlockBreakingMovementBehaviour {

	@Override
	public boolean isActive(MovementContext context) {
		return super.isActive(context)
			&& !VecHelper.isVecPointingTowards(context.relativeMotion, context.state.getValue(SawBlock.FACING)
				.getOpposite());
	}

	@Override
	public Vec3 getActiveAreaOffset(MovementContext context) {
		return Vec3.atLowerCornerOf(context.state.getValue(SawBlock.FACING)
			.getUnitVec3i())
			.scale(.65f);
	}

	@Override
	public void visitNewPosition(MovementContext context, BlockPos pos) {
		super.visitNewPosition(context, pos);
		Vec3 facingVec = Vec3.atLowerCornerOf(context.state.getValue(SawBlock.FACING)
			.getUnitVec3i());
		facingVec = context.rotation.apply(facingVec);

		Direction closestToFacing = Direction.getApproximateNearest(facingVec.x, facingVec.y, facingVec.z);
		if (closestToFacing.getAxis()
			.isVertical() && context.data.contains("BreakingPos")) {
			context.data.remove("BreakingPos");
			context.stall = false;
		}
	}

	@Override
	public boolean canBreak(Level world, BlockPos breakingPos, BlockState state) {
		return super.canBreak(world, breakingPos, state) && SawBlockEntity.isSawable(state);
	}

	@Override
	protected void onBlockBroken(MovementContext context, BlockPos pos, BlockState brokenState) {
		if (brokenState.is(BlockTags.LEAVES))
			return;

		TreeCutter.findTree(context.world, pos, brokenState)
			.destroyBlocks(context.world, null, (dropPos, stack) -> dropItemFromCutTree(context, dropPos, stack));
	}

	public void dropItemFromCutTree(MovementContext context, BlockPos pos, ItemStack stack) {
		ItemStack remainder = ItemHandlerHelper.insertItem(context.contraption.getStorage().getAllItems(), stack, false);
		if (remainder.isEmpty())
			return;

		Level world = context.world;
		Vec3 dropPos = VecHelper.getCenterOf(pos);
		float distance = context.position == null ? 1 : (float) dropPos.distanceTo(context.position);
		ItemEntity entity = new ItemEntity(world, dropPos.x, dropPos.y, dropPos.z, remainder);
		entity.setDeltaMovement(context.relativeMotion.scale(distance / 20f));
		world.addFreshEntity(entity);
	}

	@Override
	protected @Nullable DamageSource getDamageSource(Level level) {
		return SawRegistry.sawDamage(level);
	}
}
