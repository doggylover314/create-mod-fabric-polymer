package dev.createpolymer.api.packager.unpacking;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.api.registry.SimpleRegistry;
import dev.createpolymer.impl.unpacking.DefaultUnpackingHandler;
import dev.createpolymer.logistics.stockTicker.PackageOrderWithCrafts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Interface for custom handling of box unpacking into storage.
 */
public interface UnpackingHandler {
	SimpleRegistry<Block, UnpackingHandler> REGISTRY = SimpleRegistry.create();

	/**
	 * The default unpacking handler, simply inserting all items into storage.
	 */
	UnpackingHandler DEFAULT = DefaultUnpackingHandler.INSTANCE;

	/**
	 * Unpack the given items into storage.
	 *
	 * @param items        the list of non-empty item stacks to unpack. May be freely modified
	 * @param orderContext the order context, if present
	 * @param simulate     true if the unpacking should only be simulated
	 * @return true if all items have been unpacked successfully
	 */
	boolean unpack(Level level, BlockPos pos, BlockState state, Direction side, List<ItemStack> items,
		@Nullable PackageOrderWithCrafts orderContext, boolean simulate);
}
