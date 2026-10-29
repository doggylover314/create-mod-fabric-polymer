package dev.createpolymer.decoration.girder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.registry.feature.DoorsRegistry;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * What Create's GirderWrenchBehavior does when the wrench is used on a girder: toggles the connection to the girder
 * above or below. The outline it shows on the client is not ported.
 */
public class GirderWrenchBehavior {

	@Nullable
	private static Pair<Direction, Action> getDirectionAndAction(BlockHitResult result, Level world, BlockPos pos) {
		List<Pair<Direction, Action>> validDirections = getValidDirections(world, pos);

		if (validDirections.isEmpty())
			return null;

		List<Direction> directions = GirderPlacementHelper.orderedByDistance(pos, result.getLocation(),
			dir -> validDirections.stream()
				.anyMatch(pair -> pair.getFirst() == dir));

		if (directions.isEmpty())
			return null;

		Direction dir = directions.get(0);
		return validDirections.stream()
			.filter(pair -> pair.getFirst() == dir)
			.findFirst()
			.orElseGet(() -> Pair.of(dir, Action.SINGLE));
	}

	public static List<Pair<Direction, Action>> getValidDirections(BlockGetter level, BlockPos pos) {
		BlockState blockState = level.getBlockState(pos);

		if (!blockState.is(DoorsRegistry.METAL_GIRDER))
			return Collections.emptyList();

		return Arrays.stream(Iterate.directions)
			.<Pair<Direction, Action>>mapMulti((direction, consumer) -> {
				BlockState other = level.getBlockState(pos.relative(direction));

				if (!blockState.getValue(GirderBlock.X) && !blockState.getValue(GirderBlock.Z))
					return;

				// up and down
				if (direction.getAxis() == Direction.Axis.Y) {
					// no other girder in target dir
					if (!other.is(DoorsRegistry.METAL_GIRDER)) {
						if (!blockState.getValue(GirderBlock.X) ^ !blockState.getValue(GirderBlock.Z))
							consumer.accept(Pair.of(direction, Action.SINGLE));
						return;
					}
					// this girder is a pole or cross
					if (blockState.getValue(GirderBlock.X) == blockState.getValue(GirderBlock.Z))
						return;
					// other girder is a pole or cross
					if (other.getValue(GirderBlock.X) == other.getValue(GirderBlock.Z))
						return;
					// toggle up/down connection for both
					consumer.accept(Pair.of(direction, Action.PAIR));

					return;
				}
			})
			.toList();
	}

	public static boolean handleClick(Level level, BlockPos pos, BlockState state, BlockHitResult result) {
		Pair<Direction, Action> dirPair = getDirectionAndAction(result, level, pos);
		if (dirPair == null)
			return false;
		if (level.isClientSide())
			return true;
		if (!state.getValue(GirderBlock.X) && !state.getValue(GirderBlock.Z))
			return false;

		Direction dir = dirPair.getFirst();

		BlockPos otherPos = pos.relative(dir);
		BlockState other = level.getBlockState(otherPos);

		if (dir == Direction.UP) {
			level.setBlock(pos, postProcess(state.cycle(GirderBlock.TOP)), 2 | 16);
			if (dirPair.getSecond() == Action.PAIR && other.is(DoorsRegistry.METAL_GIRDER))
				level.setBlock(otherPos, postProcess(other.cycle(GirderBlock.BOTTOM)), 2 | 16);
			return true;
		}

		if (dir == Direction.DOWN) {
			level.setBlock(pos, postProcess(state.cycle(GirderBlock.BOTTOM)), 2 | 16);
			if (dirPair.getSecond() == Action.PAIR && other.is(DoorsRegistry.METAL_GIRDER))
				level.setBlock(otherPos, postProcess(other.cycle(GirderBlock.TOP)), 2 | 16);
			return true;
		}

		return true;
	}

	private static BlockState postProcess(BlockState newState) {
		if (newState.getValue(GirderBlock.TOP) && newState.getValue(GirderBlock.BOTTOM))
			return newState;
		if (newState.getValue(GirderBlock.AXIS) != Axis.Y)
			return newState;
		return newState.setValue(GirderBlock.AXIS, newState.getValue(GirderBlock.X) ? Axis.X : Axis.Z);
	}

	public enum Action {
		SINGLE, PAIR, HORIZONTAL
	}

}
