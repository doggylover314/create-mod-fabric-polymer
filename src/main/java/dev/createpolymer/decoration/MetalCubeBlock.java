package dev.createpolymer.decoration;

import dev.createpolymer.item.IWrenchable;
import net.minecraft.world.level.block.Block;

/** Industrial and weathered iron blocks: full cubes that the wrench picks up (Create's wrench_pickup tag). */
public class MetalCubeBlock extends TexturedCubeBlock implements IWrenchable {
	public MetalCubeBlock(Properties properties, Block fallback, String... models) {
		super(properties, fallback, models);
	}
}
