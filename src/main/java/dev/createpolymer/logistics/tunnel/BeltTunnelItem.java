package dev.createpolymer.logistics.tunnel;

import dev.createpolymer.kinetics.belt.BeltBlockEntity;
import dev.createpolymer.kinetics.belt.BeltBlockEntity.CasingType;
import dev.createpolymer.kinetics.belt.BeltHelper;
import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

public class BeltTunnelItem extends PolymerBlockItem {

	public BeltTunnelItem(BeltTunnelBlock block, Properties properties) {
		super(block, properties, Items.TRIAL_KEY, true);
	}

	@Override
	protected boolean canPlace(BlockPlaceContext ctx, BlockState state) {
		Player playerentity = ctx.getPlayer();
		CollisionContext iselectioncontext =
			playerentity == null ? CollisionContext.empty() : CollisionContext.of(playerentity);
		Level world = ctx.getLevel();
		BlockPos pos = ctx.getClickedPos();
		return (!this.mustSurvive() || ((BeltTunnelBlock) getBlock()).isValidPositionForPlacement(state, world, pos))
			&& world.isUnobstructed(state, pos, iselectioncontext);
	}

	/** Create covers the belt below with a matching casing when it has none (BlockItem.updateCustomBlockEntityTag). */
	@Override
	public InteractionResult place(BlockPlaceContext context) {
		InteractionResult result = super.place(context);
		Level world = context.getLevel();
		if (result.consumesAction() && !world.isClientSide()) {
			BlockPos pos = context.getClickedPos();
			BlockState state = world.getBlockState(pos);
			BeltBlockEntity belt = BeltHelper.getSegmentBE(world, pos.below());
			if (belt != null && belt.casing == CasingType.NONE && state.getBlock() instanceof BeltTunnelBlock)
				belt.setCasingType(state.getBlock() instanceof BrassTunnelBlock ? CasingType.BRASS : CasingType.ANDESITE);
		}
		return result;
	}

}
