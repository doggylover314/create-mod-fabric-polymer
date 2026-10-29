package dev.createpolymer.fluids.tank.storage;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.createpolymer.api.contraption.storage.fluid.MountedFluidStorageType;
import dev.createpolymer.api.contraption.storage.fluid.WrapperMountedFluidStorage;
import dev.createpolymer.fluids.tank.FluidTankBlockEntity;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.FluidTank;
import dev.createpolymer.registry.feature.ActorsRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Ported from Create's FluidTankMountedStorage (MIT, The Create Team). There is no client sync to mark dirty. */
public class FluidTankMountedStorage extends WrapperMountedFluidStorage<FluidTankMountedStorage.Handler> {
	public static final MapCodec<FluidTankMountedStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		ExtraCodecs.NON_NEGATIVE_INT.fieldOf("capacity").forGetter(FluidTankMountedStorage::getCapacity),
		FluidStack.OPTIONAL_CODEC.fieldOf("fluid").forGetter(FluidTankMountedStorage::getFluid)
	).apply(i, FluidTankMountedStorage::new));

	protected FluidTankMountedStorage(MountedFluidStorageType<?> type, int capacity, FluidStack stack) {
		super(type, new Handler(capacity, stack));
	}

	protected FluidTankMountedStorage(int capacity, FluidStack stack) {
		this(ActorsRegistry.FLUID_TANK_MOUNTED_STORAGE, capacity, stack);
	}

	@Override
	public void unmount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
		if (be instanceof FluidTankBlockEntity tank && tank.isController()) {
			FluidTank inventory = tank.getTankInventory();
			// capacity shouldn't change, leave it
			inventory.setFluid(this.wrapped.getFluid());
		}
	}

	public FluidStack getFluid() {
		return this.wrapped.getFluid();
	}

	public int getCapacity() {
		return this.wrapped.getCapacity();
	}

	public static FluidTankMountedStorage fromTank(FluidTankBlockEntity tank) {
		// tank has update callbacks, make an isolated copy
		FluidTank inventory = tank.getTankInventory();
		return new FluidTankMountedStorage(inventory.getCapacity(), inventory.getFluid().copy());
	}

	public static final class Handler extends FluidTank {
		public Handler(int capacity, FluidStack stack) {
			super(capacity);
			this.setFluid(stack);
		}
	}
}
