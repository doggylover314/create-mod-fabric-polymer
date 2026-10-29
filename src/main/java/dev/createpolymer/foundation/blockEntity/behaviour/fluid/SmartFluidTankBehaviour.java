package dev.createpolymer.foundation.blockEntity.behaviour.fluid;

import java.util.function.Consumer;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BehaviourType;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.fluid.CombinedTankWrapper;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.IFluidHandler;
import dev.createpolymer.foundation.fluid.SmartFluidTank;
import dev.createpolymer.util.LerpedFloat;
import dev.createpolymer.util.LerpedFloat.Chaser;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class SmartFluidTankBehaviour extends BlockEntityBehaviour {

	public static final BehaviourType<SmartFluidTankBehaviour>

	TYPE = new BehaviourType<>(), INPUT = new BehaviourType<>("Input"), OUTPUT = new BehaviourType<>("Output");

	private static final int SYNC_RATE = 8;

	protected int syncCooldown;
	protected boolean queuedSync;
	protected TankSegment[] tanks;
	protected IFluidHandler capability;
	protected boolean extractionAllowed;
	protected boolean insertionAllowed;
	protected Runnable fluidUpdateCallback;

	private BehaviourType<SmartFluidTankBehaviour> behaviourType;

	public static SmartFluidTankBehaviour single(SmartBlockEntity be, int capacity) {
		return new SmartFluidTankBehaviour(TYPE, be, 1, capacity, false);
	}

	public SmartFluidTankBehaviour(BehaviourType<SmartFluidTankBehaviour> type, SmartBlockEntity be, int tanks,
		int tankCapacity, boolean enforceVariety) {
		super(be);
		insertionAllowed = true;
		extractionAllowed = true;
		behaviourType = type;
		this.tanks = new TankSegment[tanks];
		IFluidHandler[] handlers = new IFluidHandler[tanks];
		for (int i = 0; i < tanks; i++) {
			TankSegment tankSegment = new TankSegment(tankCapacity);
			this.tanks[i] = tankSegment;
			handlers[i] = tankSegment.tank;
		}
		capability = new InternalFluidHandler(handlers, enforceVariety);
		fluidUpdateCallback = () -> {
		};
	}

	public SmartFluidTankBehaviour whenFluidUpdates(Runnable fluidUpdateCallback) {
		this.fluidUpdateCallback = fluidUpdateCallback;
		return this;
	}

	public SmartFluidTankBehaviour allowInsertion() {
		insertionAllowed = true;
		return this;
	}

	public SmartFluidTankBehaviour allowExtraction() {
		extractionAllowed = true;
		return this;
	}

	public SmartFluidTankBehaviour forbidInsertion() {
		insertionAllowed = false;
		return this;
	}

	public SmartFluidTankBehaviour forbidExtraction() {
		extractionAllowed = false;
		return this;
	}

	@Override
	public void initialize() {
		super.initialize();
		if (getWorld().isClientSide())
			return;
		forEach(ts -> {
			ts.fluidLevel.forceNextSync();
			ts.onFluidStackChanged();
		});
	}

	@Override
	public void tick() {
		super.tick();

		if (syncCooldown > 0) {
			syncCooldown--;
			if (syncCooldown == 0 && queuedSync)
				updateFluids();
		}

		forEach(be -> {
			LerpedFloat fluidLevel = be.getFluidLevel();
			if (fluidLevel != null)
				fluidLevel.tickChaser();
		});
	}

	public void sendDataImmediately() {
		syncCooldown = 0;
		queuedSync = false;
		updateFluids();
	}

	public void sendDataLazily() {
		if (syncCooldown > 0) {
			queuedSync = true;
			return;
		}
		updateFluids();
		queuedSync = false;
		syncCooldown = SYNC_RATE;
	}

	protected void updateFluids() {
		fluidUpdateCallback.run();
		blockEntity.sendData();
		blockEntity.setChanged();
	}

	public SmartFluidTank getPrimaryHandler() {
		return getPrimaryTank().tank;
	}

	public TankSegment getPrimaryTank() {
		return tanks[0];
	}

	public TankSegment[] getTanks() {
		return tanks;
	}

	public boolean isEmpty() {
		for (TankSegment tankSegment : tanks)
			if (!tankSegment.tank.isEmpty())
				return false;
		return true;
	}

	public void forEach(Consumer<TankSegment> action) {
		for (TankSegment tankSegment : tanks)
			action.accept(tankSegment);
	}

	public IFluidHandler getCapability() {
		return capability;
	}

	@Override
	public void write(ValueOutput nbt) {
		super.write(nbt);
		ValueOutput.ValueOutputList tanksNBT = nbt.childrenList(getType().getName() + "Tanks");
		forEach(ts -> ts.write(tanksNBT.addChild()));
	}

	@Override
	public void read(ValueInput nbt) {
		super.read(nbt);
		int[] index = { 0 };
		nbt.childrenListOrEmpty(getType().getName() + "Tanks").stream().forEach(c -> {
			if (index[0] >= tanks.length)
				return;
			tanks[index[0]].read(c);
			index[0]++;
		});
	}

	public class InternalFluidHandler extends CombinedTankWrapper {

		public InternalFluidHandler(IFluidHandler[] handlers, boolean enforceVariety) {
			super(handlers);
			if (enforceVariety)
				enforceVariety();
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			if (!insertionAllowed)
				return 0;
			return super.fill(resource, action);
		}

		public int forceFill(FluidStack resource, FluidAction action) {
			return super.fill(resource, action);
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			if (!extractionAllowed)
				return FluidStack.EMPTY;
			return super.drain(resource, action);
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			if (!extractionAllowed)
				return FluidStack.EMPTY;
			return super.drain(maxDrain, action);
		}

	}

	public class TankSegment {

		protected SmartFluidTank tank;
		protected LerpedFloat fluidLevel;
		protected FluidStack renderedFluid;

		public TankSegment(int capacity) {
			tank = new SmartFluidTank(capacity, f -> onFluidStackChanged());
			fluidLevel = LerpedFloat.linear()
				.startWithValue(0)
				.chase(0, .25, Chaser.EXP);
			renderedFluid = FluidStack.EMPTY;
		}

		public void onFluidStackChanged() {
			if (!blockEntity.hasLevel())
				return;
			fluidLevel.chase(tank.getFluidAmount() / (float) tank.getCapacity(), .25, Chaser.EXP);
			if (!getWorld().isClientSide())
				sendDataLazily();
			if (!tank.getFluid()
				.isEmpty())
				renderedFluid = tank.getFluid().copy();
		}

		public FluidStack getRenderedFluid() {
			return renderedFluid;
		}

		public LerpedFloat getFluidLevel() {
			return fluidLevel;
		}

		public float getTotalUnits(float partialTicks) {
			return fluidLevel.getValue(partialTicks) * tank.getCapacity();
		}

		public void write(ValueOutput compound) {
			tank.write(compound.child("TankContent"));
		}

		public void read(ValueInput compound) {
			tank.read(compound.childOrEmpty("TankContent"));
			fluidLevel.startWithValue(tank.getCapacity() == 0 ? 0 : tank.getFluidAmount() / (float) tank.getCapacity());
			if (!tank.getFluid()
				.isEmpty())
				renderedFluid = tank.getFluid().copy();
		}

		public boolean isEmpty(float partialTicks) {
			FluidStack renderedFluid = getRenderedFluid();
			if (renderedFluid.isEmpty())
				return true;
			float units = getTotalUnits(partialTicks);
			if (units < 1)
				return true;
			return false;
		}

	}

	@Override
	public BehaviourType<?> getType() {
		return behaviourType;
	}
}
