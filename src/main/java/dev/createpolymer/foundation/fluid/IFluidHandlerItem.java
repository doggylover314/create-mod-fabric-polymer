package dev.createpolymer.foundation.fluid;

import net.minecraft.world.item.ItemStack;

/** A fluid handler for an item; {@link #getContainer()} is the item after the last executed operation. */
public interface IFluidHandlerItem extends IFluidHandler {
	ItemStack getContainer();
}
