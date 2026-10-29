package dev.createpolymer.logistics.packagePort;

import dev.createpolymer.registry.feature.PackagesRegistry;
import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Create's PackagePortItem. Create's updateCustomBlockEntityTag asks the client to send the selected target once the
 * port stands. (26.3 applies block entity tags in a static method, so this hooks the placement instead.)
 */
public class PackagePortItem extends PolymerBlockItem {

	public PackagePortItem(Block pBlock, Properties pProperties) {
		super(pBlock, pProperties, Items.TRIAL_KEY, true);
	}

	@Override
	public InteractionResult place(BlockPlaceContext ctx) {
		ItemStack stack = ctx.getItemInHand();
		PackagePortTarget selection = PackagePortTargetSelectionHandler.getSelection(stack);
		InteractionResult result = super.place(ctx);
		if (!result.consumesAction())
			return result;

		stack.remove(PackagesRegistry.PACKAGE_PORT_TARGET);
		Player player = ctx.getPlayer();
		Level world = ctx.getLevel();
		if (player != null && !world.isClientSide())
			PackagePortTargetSelectionHandler.flushSettings(player, world, ctx.getClickedPos(), selection);
		return result;
	}

}
