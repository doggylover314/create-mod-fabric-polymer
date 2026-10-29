package dev.createpolymer.equipment.blueprint;

import dev.createpolymer.registry.feature.ToolsRegistry;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Create's crafting blueprint item: placed on a wall, floor or ceiling it becomes a {@link BlueprintEntity}.
 * Create's {@code assignCompleteRecipe} (which the recipe-book packet of a Create client calls) has no use without
 * that client.
 */
public class BlueprintItem extends SimplePolymerItem {

	public BlueprintItem(Properties properties) {
		super(properties, Items.TRIAL_KEY, true);
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		Direction face = ctx.getClickedFace();
		Player player = ctx.getPlayer();
		ItemStack stack = ctx.getItemInHand();
		BlockPos pos = ctx.getClickedPos()
			.relative(face);

		if (player != null && !player.mayUseItemAt(pos, face, stack))
			return InteractionResult.FAIL;

		Level world = ctx.getLevel();
		BlueprintEntity hangingentity = new BlueprintEntity(ToolsRegistry.CRAFTING_BLUEPRINT_ENTITY, world, pos, face, face.getAxis()
			.isHorizontal() ? Direction.DOWN : ctx.getHorizontalDirection());

		if (!hangingentity.survives())
			return InteractionResult.CONSUME;
		if (!world.isClientSide()) {
			hangingentity.playPlacementSound();
			world.addFreshEntity(hangingentity);
		}

		stack.shrink(1);
		return InteractionResult.SUCCESS;
	}

}
