package dev.createpolymer.fluids;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

/**
 * Bucket of a {@link VirtualFluid}. It is a real BucketItem so that Fabric's fluid storage for items recognises it
 * (tanks, spouts and item drains fill and empty it), but a virtual fluid has no block, so it cannot be poured out
 * into the world.
 */
public class FluidBucketItem extends BucketItem implements PolymerItem {

	public FluidBucketItem(Fluid content, Properties properties) {
		super(content, properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		return InteractionResult.PASS;
	}

	@Override
	public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
		return Items.TRIAL_KEY;
	}
}
