package dev.createpolymer.fluids.tank.storage.creative;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.createpolymer.api.contraption.storage.fluid.MountedFluidStorageType;
import dev.createpolymer.api.contraption.storage.fluid.WrapperMountedFluidStorage;
import dev.createpolymer.fluids.tank.CreativeFluidTankBlockEntity;
import dev.createpolymer.fluids.tank.CreativeFluidTankBlockEntity.CreativeSmartFluidTank;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.FluidTank;
import dev.createpolymer.registry.feature.ActorsRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ported from Create's CreativeFluidTankMountedStorage (MIT, The Create Team). The codec of the tank is Create's
 * {@code CreativeSmartFluidTank.CODEC}, kept here because the block entity class does not have one.
 */
public class CreativeFluidTankMountedStorage extends WrapperMountedFluidStorage<CreativeSmartFluidTank> {
	private static final Codec<CreativeSmartFluidTank> TANK_CODEC = RecordCodecBuilder.create(i -> i.group(
		FluidStack.OPTIONAL_CODEC.fieldOf("fluid").forGetter(FluidTank::getFluid),
		ExtraCodecs.NON_NEGATIVE_INT.fieldOf("capacity").forGetter(FluidTank::getCapacity)
	).apply(i, (fluid, capacity) -> {
		CreativeSmartFluidTank tank = new CreativeSmartFluidTank(capacity, $ -> {
		});
		tank.setFluid(fluid);
		return tank;
	}));

	public static final MapCodec<CreativeFluidTankMountedStorage> CODEC = TANK_CODEC.xmap(
		CreativeFluidTankMountedStorage::new, storage -> storage.wrapped
	).fieldOf("value");

	protected CreativeFluidTankMountedStorage(MountedFluidStorageType<?> type, CreativeSmartFluidTank tank) {
		super(type, tank);
	}

	protected CreativeFluidTankMountedStorage(CreativeSmartFluidTank tank) {
		this(ActorsRegistry.CREATIVE_FLUID_TANK_MOUNTED_STORAGE, tank);
	}

	@Override
	public void unmount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
		// no need to do anything, supplied stack can't change while mounted
	}

	public static CreativeFluidTankMountedStorage fromTank(CreativeFluidTankBlockEntity tank) {
		// make an isolated copy
		FluidTank inv = tank.getTankInventory();
		CreativeSmartFluidTank copy = new CreativeSmartFluidTank(inv.getCapacity(), $ -> {
		});
		copy.setContainedFluid(inv.getFluid());
		return new CreativeFluidTankMountedStorage(copy);
	}
}
