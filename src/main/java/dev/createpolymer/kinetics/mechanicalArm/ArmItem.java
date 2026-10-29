package dev.createpolymer.kinetics.mechanicalArm;

import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import dev.createpolymer.registry.feature.ArmRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ported from Create's ArmItem (MIT, The Create Team).
 * <p>
 * Create collects the selected interaction points on the client and sends them when the arm is placed. Here they
 * are kept in the item's {@link ArmRegistry#ARM_POINTS} component (see {@link ArmInteractionPointHandler}) and
 * ArmBlock.setPlacedBy hands them to the new block entity.
 */
public class ArmItem extends PolymerBlockItem {

	public ArmItem(Block block, Properties properties) {
		super(block, properties, Items.TRIAL_KEY, true);
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		Level world = ctx.getLevel();
		BlockPos pos = ctx.getClickedPos();
		if (ArmInteractionPoint.isInteractable(world, pos, world.getBlockState(pos)))
			return InteractionResult.SUCCESS;
		return super.useOn(ctx);
	}

	@Override
	public InteractionResult place(BlockPlaceContext context) {
		InteractionResult result = super.place(context);
		// the selection belongs to the arm that was just placed, not to the rest of the stack
		ItemStack stack = context.getItemInHand();
		if (result.consumesAction() && !stack.isEmpty())
			stack.remove(ArmRegistry.ARM_POINTS);
		return result;
	}

	/** Create's canAttackBlock: creative players cannot break the blocks they are selecting. */
	@Override
	public boolean canDestroyBlock(ItemStack itemStack, BlockState state, Level world, BlockPos pos, LivingEntity user) {
		if (user instanceof Player player && player.getAbilities().instabuild)
			return !ArmInteractionPoint.isInteractable(world, pos, state);
		return true;
	}

}
