package dev.createpolymer.decoration.girder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.decoration.DecorationPlacement;
import dev.createpolymer.equipment.extendoGrip.ExtendoGripItem;
import dev.createpolymer.registry.feature.DoorsRegistry;
import dev.createpolymer.util.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's GirderPlacementHelper: right-clicking a girder with a girder places the next one along the
 * girder in the direction you point at. Catnip's PlacementHelpers/PlacementOffset are not ported, so
 * {@link #getOffset} answers with a {@link Offset} that {@link #place} puts into the world.
 */
public class GirderPlacementHelper {

	/** Catnip's PlacementOffset: where to place and how to fix up the new block's state. */
	public record Offset(BlockPos pos, UnaryOperator<BlockState> transform) {}

	public boolean matchesItem(ItemStack stack) {
		return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() == DoorsRegistry.METAL_GIRDER;
	}

	public Predicate<BlockState> getStatePredicate() {
		return state -> state.is(DoorsRegistry.METAL_GIRDER) || state.is(DoorsRegistry.METAL_GIRDER_ENCASED_SHAFT);
	}

	private boolean canExtendToward(BlockState state, Direction side) {
		Axis axis = side.getAxis();
		if (state.getBlock() instanceof GirderBlock) {
			boolean x = state.getValue(GirderBlock.X);
			boolean z = state.getValue(GirderBlock.Z);
			if (!x && !z)
				return axis == Axis.Y;
			if (x && z)
				return true;
			return axis == (x ? Axis.X : Axis.Z);
		}

		if (state.getBlock() instanceof GirderEncasedShaftBlock)
			return axis != Axis.Y && axis != state.getValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS);

		return false;
	}

	private int attachedPoles(Level world, BlockPos pos, Direction direction) {
		BlockPos checkPos = pos.relative(direction);
		BlockState state = world.getBlockState(checkPos);
		int count = 0;
		while (canExtendToward(state, direction)) {
			count++;
			checkPos = checkPos.relative(direction);
			state = world.getBlockState(checkPos);
		}
		return count;
	}

	private BlockState withAxis(BlockState state, Axis axis) {
		if (state.getBlock() instanceof GirderBlock)
			return state.setValue(GirderBlock.X, axis == Axis.X)
				.setValue(GirderBlock.Z, axis == Axis.Z)
				.setValue(GirderBlock.AXIS, axis);
		if (state.getBlock() instanceof GirderEncasedShaftBlock && axis.isHorizontal())
			return state.setValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS, axis == Axis.X ? Axis.Z : Axis.X);
		return state;
	}

	/** Catnip's IPlacementHelper.orderedByDistance: the neighbours nearest to the hit location first. */
	public static List<Direction> orderedByDistance(BlockPos pos, Vec3 hit, Predicate<Direction> filter) {
		Vec3 center = Vec3.atCenterOf(pos);
		List<Direction> directions = new ArrayList<>();
		for (Direction d : Iterate.directions)
			if (filter.test(d))
				directions.add(d);
		directions.sort(Comparator.comparingDouble(
			d -> center.add(Vec3.atLowerCornerOf(d.getUnitVec3i()).scale(.5)).distanceToSqr(hit)));
		return directions;
	}

	public @Nullable Offset getOffset(@Nullable Player player, Level world, BlockState state, BlockPos pos,
		BlockHitResult ray) {
		List<Direction> directions = orderedByDistance(pos, ray.getLocation(), dir -> canExtendToward(state, dir));
		for (Direction dir : directions) {
			int range = DecorationPlacement.PLACEMENT_ASSIST_RANGE;
			if (player != null) {
				AttributeInstance reach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
				if (reach != null && reach.hasModifier(ExtendoGripItem.singleRangeAttributeModifier.id()))
					range += 4;
			}
			int poles = attachedPoles(world, pos, dir);
			if (poles >= range)
				continue;

			BlockPos newPos = pos.relative(dir, poles + 1);
			BlockState newState = world.getBlockState(newPos);

			if (!newState.canBeReplaced())
				continue;

			return new Offset(newPos,
				bState -> Block.updateFromNeighbourShapes(withAxis(bState, dir.getAxis()), world, newPos));
		}

		return null;
	}

	/** Create's {@code getOffset(...).placeInWorld(...)}; passes the click on to the normal placement if there is no spot. */
	public InteractionResult place(Player player, Level level, BlockState state, BlockPos pos, BlockHitResult ray,
		InteractionHand hand, BlockItem item) {
		Offset offset = getOffset(player, level, state, pos, ray);
		if (offset == null)
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		return DecorationPlacement.placeInWorld(level, item, player, hand, offset.pos(), offset.transform());
	}

}
