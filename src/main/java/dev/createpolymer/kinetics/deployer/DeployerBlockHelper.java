package dev.createpolymer.kinetics.deployer;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** The few methods of Create's BlockHelper the deployer needs. */
final class DeployerBlockHelper {
	private DeployerBlockHelper() {}

	static boolean extinguishFire(Level world, @Nullable Player player, BlockPos pos, Direction dir) {
		pos = pos.relative(dir);
		if (world.getBlockState(pos)
			.getBlock() == Blocks.FIRE) {
			world.levelEvent(player, LevelEvent.SOUND_EXTINGUISH_FIRE, pos, 0);
			world.removeBlock(pos, false);
			return true;
		} else {
			return false;
		}
	}

	static BlockState copyProperties(BlockState fromState, BlockState toState) {
		for (Property<?> property : fromState.getProperties()) {
			toState = copyProperty(property, fromState, toState);
		}
		return toState;
	}

	static <T extends Comparable<T>> BlockState copyProperty(Property<T> property, BlockState fromState,
		BlockState toState) {
		if (fromState.hasProperty(property) && toState.hasProperty(property)) {
			return toState.setValue(property, fromState.getValue(property));
		}
		return toState;
	}
}
