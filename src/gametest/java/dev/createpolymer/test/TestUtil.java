package dev.createpolymer.test;

import dev.createpolymer.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

final class TestUtil {
	private TestUtil() {}

	static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++)
			for (int z = 0; z < 8; z++)
				helper.setBlock(x, 0, z, Blocks.STONE);
	}

	static float speed(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockEntity(pos, KineticBlockEntity.class).getSpeed();
	}

	static void assertSpeed(GameTestHelper helper, BlockPos pos, float expected) {
		float actual = speed(helper, pos);
		helper.assertTrue(Math.abs(actual - expected) < 1e-3, "Speed at " + pos + " was " + actual + ", expected " + expected);
	}
}
