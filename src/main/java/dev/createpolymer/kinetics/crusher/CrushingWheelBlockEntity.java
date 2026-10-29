package dev.createpolymer.kinetics.crusher;

import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.util.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ported from Create's CrushingWheelBlockEntity (MIT, The Create Team).
 * Create zeroes the item velocity of crushed mobs' drops with a LivingDropsEvent handler; Fabric has no such event.
 */
public class CrushingWheelBlockEntity extends KineticBlockEntity {
	public CrushingWheelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		setLazyTickRate(20);
	}

	@Override
	public void onSpeedChanged(float prevSpeed) {
		super.onSpeedChanged(prevSpeed);
		fixControllers();
	}

	public void fixControllers() {
		if (level == null || level.isClientSide())
			return;
		for (Direction d : Iterate.directions)
			((CrushingWheelBlock) getBlockState().getBlock()).updateControllers(getBlockState(), getLevel(), getBlockPos(),
				d);
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		fixControllers();
	}
}
