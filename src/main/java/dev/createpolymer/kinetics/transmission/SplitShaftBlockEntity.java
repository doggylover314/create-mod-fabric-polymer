package dev.createpolymer.kinetics.transmission;

import dev.createpolymer.kinetics.base.DirectionalShaftHalvesBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Ported from Create's SplitShaftBlockEntity, ClutchBlockEntity and GearshiftBlockEntity (MIT, The Create Team). */
public class SplitShaftBlockEntity extends DirectionalShaftHalvesBlockEntity {
	private final boolean clutch;

	public SplitShaftBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, boolean clutch) {
		super(type, pos, state);
		this.clutch = clutch;
	}

	/** Speed factor between the source side and {@code face}: clutch disconnects, gearshift reverses. */
	public float getRotationSpeedModifier(Direction face) {
		if (hasSource() && face != getSourceFacing() && getBlockState().getValue(BlockStateProperties.POWERED))
			return clutch ? 0 : -1;
		return 1;
	}
}
