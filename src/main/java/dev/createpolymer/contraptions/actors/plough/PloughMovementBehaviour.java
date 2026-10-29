package dev.createpolymer.contraptions.actors.plough;

import dev.createpolymer.contraptions.actors.plough.PloughBlock.PloughFakePlayer;
import dev.createpolymer.contraptions.behaviour.MovementContext;
import dev.createpolymer.kinetics.base.BlockBreakingMovementBehaviour;
import dev.createpolymer.registry.feature.ActorsRegistry;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

/** Ported from Create's PloughMovementBehaviour (MIT, The Create Team). Create's track checks and advancement are left out. */
public class PloughMovementBehaviour extends BlockBreakingMovementBehaviour {

	@Override
	public boolean isActive(MovementContext context) {
		return super.isActive(context)
			&& !VecHelper.isVecPointingTowards(context.relativeMotion, context.state.getValue(PloughBlock.FACING)
				.getOpposite());
	}

	@Override
	public void visitNewPosition(MovementContext context, BlockPos pos) {
		super.visitNewPosition(context, pos);
		Level world = context.world;
		if (world.isClientSide())
			return;
		BlockPos below = pos.below();
		if (!world.isLoaded(below))
			return;

		Vec3 vec = VecHelper.getCenterOf(pos);
		PloughFakePlayer player = getPlayer(context);

		if (player == null)
			return;

		BlockHitResult ray =
			world.clip(new ClipContext(vec, vec.add(0, -1, 0), Block.OUTLINE, Fluid.NONE, player));
		if (ray.getType() != Type.BLOCK)
			return;

		UseOnContext ctx = new UseOnContext(player, InteractionHand.MAIN_HAND, ray);
		player.getMainHandItem()
			.useOn(ctx);
	}

	@Override
	protected void throwEntity(MovementContext context, Entity entity) {
		super.throwEntity(context, entity);
		// Create awards an advancement when an anvil is plowed away; advancements are not ported
	}

	@Override
	public Vec3 getActiveAreaOffset(MovementContext context) {
		return Vec3.atLowerCornerOf(context.state.getValue(PloughBlock.FACING)
			.getUnitVec3i())
			.scale(.45);
	}

	@Override
	protected boolean throwsEntities(Level level) {
		return true;
	}

	@Override
	public boolean canBreak(Level world, BlockPos breakingPos, BlockState state) {
		if (state.isAir())
			return false;
		if (world.getBlockState(breakingPos.below())
			.getBlock() instanceof FarmlandBlock)
			return false;
		if (state.getBlock() instanceof LiquidBlock)
			return false;
		if (state.getBlock() instanceof BubbleColumnBlock)
			return false;
		if (state.is(ActorsRegistry.PLOUGH_BLACKLIST))
			return false;
		if (state.is(ActorsRegistry.PLOUGH_WHITELIST))
			return true;
		return state.getCollisionShape(world, breakingPos)
			.isEmpty();
	}

	@Override
	protected void onBlockBroken(MovementContext context, BlockPos pos, BlockState brokenState) {
		super.onBlockBroken(context, pos, brokenState);

		if (brokenState.getBlock() == Blocks.SNOW && context.world instanceof ServerLevel world) {
			brokenState.getDrops(new LootParams.Builder(world).withParameter(LootContextParams.BLOCK_STATE, brokenState)
				.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
				.withParameter(LootContextParams.THIS_ENTITY, getPlayer(context))
				.withParameter(LootContextParams.TOOL, new ItemStack(Items.IRON_SHOVEL)))
				.forEach(s -> collectOrDropItem(context, s));
		}
	}

	@Override
	public void stopMoving(MovementContext context) {
		super.stopMoving(context);
		if (context.temporaryData instanceof PloughFakePlayer)
			((PloughFakePlayer) context.temporaryData).discard();
	}

	private PloughFakePlayer getPlayer(MovementContext context) {
		if (!(context.temporaryData instanceof PloughFakePlayer) && context.world instanceof ServerLevel serverLevel) {
			PloughFakePlayer player = new PloughFakePlayer(serverLevel);
			ItemStack heldItem = new ItemStack(Items.DIAMOND_HOE);
			heldItem.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
			player.setItemInHand(InteractionHand.MAIN_HAND, heldItem);
			context.temporaryData = player;
		}
		return (PloughFakePlayer) context.temporaryData;
	}

}
