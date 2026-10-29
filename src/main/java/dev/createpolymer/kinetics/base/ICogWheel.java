package dev.createpolymer.kinetics.base;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Ported from Create's ICogWheel (MIT, The Create Team). */
public interface ICogWheel extends IRotate {

	static boolean isSmallCog(BlockState state) {
		return isSmallCog(state.getBlock());
	}

	static boolean isLargeCog(BlockState state) {
		return isLargeCog(state.getBlock());
	}

	static boolean isSmallCog(Block block) {
		return block instanceof ICogWheel cog && cog.isSmallCog();
	}

	static boolean isLargeCog(Block block) {
		return block instanceof ICogWheel cog && cog.isLargeCog();
	}

	static boolean isDedicatedCogWheel(Block block) {
		return block instanceof ICogWheel cog && cog.isDedicatedCogWheel();
	}

	static boolean isSmallCogItem(ItemStack test) {
		return test.getItem() instanceof BlockItem bi && isSmallCog(bi.getBlock());
	}

	static boolean isLargeCogItem(ItemStack test) {
		return test.getItem() instanceof BlockItem bi && isLargeCog(bi.getBlock());
	}

	default boolean isLargeCog() {
		return false;
	}

	default boolean isSmallCog() {
		return !isLargeCog();
	}

	default boolean isDedicatedCogWheel() {
		return false;
	}
}
