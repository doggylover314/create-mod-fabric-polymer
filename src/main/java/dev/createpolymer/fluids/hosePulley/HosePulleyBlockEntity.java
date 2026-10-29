package dev.createpolymer.fluids.hosePulley;

import java.util.List;

import dev.createpolymer.fluids.transfer.FluidDrainingBehaviour;
import dev.createpolymer.fluids.transfer.FluidFillingBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.fluid.FluidHandlerLookup;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.FluidTank;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.registry.feature.PipesRegistry;
import dev.createpolymer.util.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class HosePulleyBlockEntity extends KineticBlockEntity {

	LerpedFloat offset;
	boolean isMoving;

	private FluidTank internalTank;
	private FluidDrainingBehaviour drainer;
	private FluidFillingBehaviour filler;
	private HosePulleyFluidHandler handler;
	private boolean infinite;

	public HosePulleyBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
		super(typeIn, pos, state);
		offset = LerpedFloat.linear()
			.startWithValue(0);
		isMoving = true;
		internalTank = new FluidTank(1500) {
			@Override
			protected void onContentsChanged() {
				onTankContentsChanged(getFluid());
			}
		};
		handler = new HosePulleyFluidHandler(internalTank, filler, drainer,
			() -> worldPosition.below((int) Math.ceil(offset.getValue())), () -> !this.isMoving);
	}

	public static void registerCapabilities() {
		FluidHandlerLookup.registerBlockEntity(PipesRegistry.HOSE_PULLEY_BLOCK_ENTITY, (be, context) -> {
			if (context == null || HosePulleyBlock.hasPipeTowards(be.level, be.worldPosition, be.getBlockState(), context))
				return be.handler;
			return null;
		});
	}

	@Override
	public void sendData() {
		infinite = filler.isInfinite() || drainer.isInfinite();
		super.sendData();
	}

	/** Whether the hose pulley has found an infinite source (Create shows a hint in the goggle tooltip). */
	public boolean isInfinite() {
		return infinite;
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		drainer = new FluidDrainingBehaviour(this);
		filler = new FluidFillingBehaviour(this);
		behaviours.add(drainer);
		behaviours.add(filler);
		super.addBehaviours(behaviours);
	}

	protected void onTankContentsChanged(FluidStack contents) {}

	@Override
	public void onSpeedChanged(float previousSpeed) {
		isMoving = true;
		if (getSpeed() == 0) {
			offset.forceNextSync();
			offset.setValue(Math.round(offset.getValue()));
			isMoving = false;
		}

		if (isMoving) {
			float newOffset = offset.getValue() + getMovementSpeed();
			if (newOffset < 0)
				isMoving = false;
			if (!level.getBlockState(worldPosition.below((int) Math.ceil(newOffset)))
				.canBeReplaced()) {
				isMoving = false;
			}
			if (isMoving) {
				drainer.reset();
				filler.reset();
			}
		}

		super.onSpeedChanged(previousSpeed);
	}

	@Override
	public void tick() {
		super.tick();
		float newOffset = offset.getValue() + getMovementSpeed();
		if (newOffset < 0) {
			newOffset = 0;
			isMoving = false;
		}
		if (!level.getBlockState(worldPosition.below((int) Math.ceil(newOffset)))
			.canBeReplaced()) {
			newOffset = (int) newOffset;
			isMoving = false;
		}
		if (getSpeed() == 0)
			isMoving = false;

		offset.setValue(newOffset);
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		if (level.isClientSide())
			return;
		if (isMoving)
			return;

		int ceil = (int) Math.ceil(offset.getValue() + getMovementSpeed());
		if (getMovementSpeed() > 0 && level.getBlockState(worldPosition.below(ceil))
			.canBeReplaced()) {
			isMoving = true;
			drainer.reset();
			filler.reset();
			return;
		}

		sendData();
	}

	@Override
	protected void write(ValueOutput compound) {
		compound.store("Offset", CompoundTag.CODEC, offset.writeNBT());
		internalTank.write(compound.child("Tank"));
		super.write(compound);
	}

	@Override
	protected void read(ValueInput compound) {
		compound.read("Offset", CompoundTag.CODEC)
			.ifPresent(tag -> offset.readNBT(tag, false));
		compound.child("Tank")
			.ifPresent(internalTank::read);
		super.read(compound);
	}

	public float getMovementSpeed() {
		return convertToLinear(getSpeed());
	}

	public float getInterpolatedOffset(float pt) {
		return Math.max(offset.getValue(pt), 3 / 16f);
	}

	public FluidTank getInternalTank() {
		return internalTank;
	}

	public FluidDrainingBehaviour getDrainer() {
		return drainer;
	}

	public FluidFillingBehaviour getFiller() {
		return filler;
	}

	public HosePulleyFluidHandler getHandler() {
		return handler;
	}
}
