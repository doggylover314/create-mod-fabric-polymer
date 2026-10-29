package dev.createpolymer.fluids.tank.storage.creative;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.api.contraption.storage.fluid.MountedFluidStorageType;
import dev.createpolymer.fluids.tank.CreativeFluidTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CreativeFluidTankMountedStorageType extends MountedFluidStorageType<CreativeFluidTankMountedStorage> {
	public CreativeFluidTankMountedStorageType() {
		super(CreativeFluidTankMountedStorage.CODEC);
	}

	@Override
	@Nullable
	public CreativeFluidTankMountedStorage mount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
		if (be instanceof CreativeFluidTankBlockEntity tank) {
			return CreativeFluidTankMountedStorage.fromTank(tank);
		}

		return null;
	}
}
