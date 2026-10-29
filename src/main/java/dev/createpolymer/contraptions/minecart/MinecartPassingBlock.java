package dev.createpolymer.contraptions.minecart;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * NeoForge's {@code onMinecartPass} block hook, which vanilla rails do not have. Rail blocks implementing this are
 * told about each cart on them once per tick, before the cart moves; see {@code CapabilityMinecartController}.
 */
public interface MinecartPassingBlock {

	void onMinecartPass(BlockState state, Level world, BlockPos pos, AbstractMinecart cart);

}
