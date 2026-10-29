package dev.createpolymer.logistics.depot;

import dev.createpolymer.registry.feature.EjectorRegistry;
import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class EjectorItem extends PolymerBlockItem {

	public EjectorItem(Block p_i48527_1_, Properties p_i48527_2_) {
		super(p_i48527_1_, p_i48527_2_, Items.TRIAL_KEY, true);
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		Player player = ctx.getPlayer();
		if (player != null && player.isShiftKeyDown()) {
			// Create selects the target on the client (EjectorTargetHandler); here the item remembers it
			if (!ctx.getLevel().isClientSide())
				EjectorTargetHandler.rightClickingBlocksSelectsThem(player, ctx.getItemInHand(), ctx.getLevel(),
					ctx.getClickedPos());
			return InteractionResult.SUCCESS;
		}
		return super.useOn(ctx);
	}

	/**
	 * Create's updateCustomBlockEntityTag asks the client to send the selected target once the ejector stands.
	 * (26.3 applies block entity tags in a static method, so this hooks the placement instead.)
	 */
	@Override
	public InteractionResult place(BlockPlaceContext ctx) {
		ItemStack stack = ctx.getItemInHand();
		GlobalPos selection = EjectorTargetHandler.getSelection(stack);
		InteractionResult result = super.place(ctx);
		if (!result.consumesAction())
			return result;

		stack.remove(EjectorRegistry.EJECTOR_TARGET);
		Player player = ctx.getPlayer();
		Level world = ctx.getLevel();
		if (player != null && !world.isClientSide())
			EjectorTargetHandler.flushSettings(player, world, ctx.getClickedPos(), selection);
		return result;
	}

	@Override
	public boolean canDestroyBlock(ItemStack itemStack, BlockState state, Level level, BlockPos pos, LivingEntity user) {
		return !user.isShiftKeyDown();
	}

}
