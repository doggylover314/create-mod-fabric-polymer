package dev.createpolymer.kinetics.gearbox;

import dev.createpolymer.kinetics.base.IRotate;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.util.Iterate;
import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Create's vertical gearbox: places the gearbox with a horizontal axis. The axis follows the shafts that already
 * reach the gearbox from the sides; without them it runs along the way the player faces.
 */
public class VerticalGearboxItem extends PolymerBlockItem {

	public VerticalGearboxItem(Properties builder) {
		super(ModBlocks.GEARBOX, builder, Items.TRIAL_KEY, true);
	}

	/**
	 * Create sets the axis after the block is placed (updateCustomBlockEntityTag); the axis is only read from the
	 * neighbours and the player, so it is set on the state that is placed.
	 */
	@Override
	protected boolean placeBlock(BlockPlaceContext context, BlockState placementState) {
		return super.placeBlock(context, placementState.setValue(BlockStateProperties.AXIS, verticalAxis(context)));
	}

	private static Axis verticalAxis(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		Axis prefferedAxis = null;
		for (Direction side : Iterate.horizontalDirections) {
			BlockState blockState = context.getLevel().getBlockState(pos.relative(side));
			if (blockState.getBlock() instanceof IRotate rotate
				&& rotate.hasShaftTowards(context.getLevel(), pos.relative(side), blockState, side.getOpposite())) {
				if (prefferedAxis != null && prefferedAxis != side.getAxis()) {
					prefferedAxis = null;
					break;
				} else {
					prefferedAxis = side.getAxis();
				}
			}
		}

		Direction facing = context.getPlayer() != null ? context.getPlayer().getDirection() : context.getHorizontalDirection();
		return prefferedAxis == null ? facing.getClockWise().getAxis()
			: prefferedAxis == Axis.X ? Axis.Z : Axis.X;
	}
}
