package dev.createpolymer.logistics.funnel;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class FunnelItem extends PolymerBlockItem {

	public FunnelItem(Block p_i48527_1_, Properties p_i48527_2_) {
		super(p_i48527_1_, p_i48527_2_, Items.TRIAL_KEY, true);
	}

	/**
	 * Create's funnelItemAlwaysPlacesWhenUsed (RightClickBlock, useBlock = false): with a funnel in hand, a click on
	 * any block places the funnel instead of using the block. It runs before the other use-block handlers.
	 */
	public static void registerEvents() {
		Identifier phase = CreatePolymer.id("funnel_item");
		UseBlockCallback.EVENT.addPhaseOrdering(phase, Event.DEFAULT_PHASE);
		UseBlockCallback.EVENT.register(phase, FunnelItem::funnelItemAlwaysPlacesWhenUsed);
	}

	public static InteractionResult funnelItemAlwaysPlacesWhenUsed(Player player, Level level, InteractionHand hand,
		BlockHitResult hit) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide() || player.isSpectator() || !(stack.getItem() instanceof FunnelItem))
			return InteractionResult.PASS;
		if (player.getCooldowns()
			.isOnCooldown(stack))
			return InteractionResult.PASS;
		UseOnContext context = new UseOnContext(player, hand, hit);
		if (player.hasInfiniteMaterials()) {
			int count = stack.getCount();
			InteractionResult result = stack.useOn(context);
			stack.setCount(count);
			return result;
		}
		return stack.useOn(context);
	}

	@Override
	protected @Nullable BlockState getPlacementState(BlockPlaceContext ctx) {
		Level world = ctx.getLevel();
		BlockPos pos = ctx.getClickedPos();
		BlockState state = super.getPlacementState(ctx);
		if (state == null)
			return state;
		if (!(state.getBlock() instanceof FunnelBlock))
			return state;
		if (state.getValue(FunnelBlock.FACING)
			.getAxis()
			.isVertical())
			return state;

		Direction direction = state.getValue(FunnelBlock.FACING);
		FunnelBlock block = (FunnelBlock) getBlock();
		Block beltFunnelBlock = block.getEquivalentBeltFunnel(world, pos, state)
			.getBlock();
		BlockState equivalentBeltFunnel = beltFunnelBlock.getStateForPlacement(ctx)
			.setValue(BeltFunnelBlock.HORIZONTAL_FACING, direction);
		if (BeltFunnelBlock.isOnValidBelt(equivalentBeltFunnel, world, pos))
			return equivalentBeltFunnel;

		return state;
	}

}
