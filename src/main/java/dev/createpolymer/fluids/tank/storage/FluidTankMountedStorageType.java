package dev.createpolymer.fluids.tank.storage;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.api.contraption.storage.fluid.MountedFluidStorageType;
import dev.createpolymer.fluids.tank.FluidTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class FluidTankMountedStorageType extends MountedFluidStorageType<FluidTankMountedStorage> {
	public FluidTankMountedStorageType() {
		super(FluidTankMountedStorage.CODEC);
	}

	@Override
	@Nullable
	public FluidTankMountedStorage mount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
		if (be instanceof FluidTankBlockEntity tank && tank.isController()) {
			return FluidTankMountedStorage.fromTank(tank);
		}

		return null;
	}
}
