package dev.createpolymer.fluids.tank;

import static java.lang.Math.abs;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.api.connectivity.ConnectivityHandler;
import dev.createpolymer.fluids.tank.FluidTankBlock.Shape;
import dev.createpolymer.foundation.blockEntity.IMultiBlockEntityContainer;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.fluid.FluidHandlerLookup;
import dev.createpolymer.foundation.fluid.FluidHelper;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.FluidTank;
import dev.createpolymer.foundation.fluid.IFluidHandler;
import dev.createpolymer.foundation.fluid.IFluidHandler.FluidAction;
import dev.createpolymer.foundation.fluid.IFluidTank;
import dev.createpolymer.foundation.fluid.SmartFluidTank;
import dev.createpolymer.registry.feature.TanksRegistry;
import dev.createpolymer.util.LerpedFloat;
import dev.createpolymer.util.LerpedFloat.Chaser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Ported from Create's FluidTankBlockEntity (MIT, The Create Team).
 * <p>
 * Differences: the client side of Create (render bounding box, fluid level sync) is gone; the level of the fluid shown
 * is chased on the server for the display entities, and the luminosity of a tank is a block state property
 * ({@link FluidTankBlock#LUMINOSITY}) because vanilla blocks emit light by state only.
 */
public class FluidTankBlockEntity extends SmartBlockEntity implements IMultiBlockEntityContainer.Fluid {

	private static final int MAX_SIZE = 3;

	/** Create's config fluids.fluidTankCapacity (default): buckets per tank block. */
	public static final int FLUID_TANK_CAPACITY = 8;
	/** Create's config fluids.fluidTankMaxHeight (default): the tallest a tank gets. */
	public static final int FLUID_TANK_MAX_HEIGHT = 32;

	protected IFluidHandler fluidCapability;
	protected boolean forceFluidLevelUpdate;
	protected FluidTank tankInventory;
	protected BlockPos controller;
	protected BlockPos lastKnownPos;
	protected boolean updateConnectivity;
	protected boolean updateCapability;
	protected boolean window;
	protected int luminosity;
	protected int width;
	protected int height;

	public BoilerData boiler;

	private static final int SYNC_RATE = 8;
	protected int syncCooldown;
	protected boolean queuedSync;

	// The level of the fluid as shown by the display entities
	private LerpedFloat fluidLevel;

	public FluidTankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		tankInventory = createInventory();
		forceFluidLevelUpdate = true;
		updateConnectivity = false;
		updateCapability = false;
		window = true;
		height = 1;
		width = 1;
		boiler = new BoilerData();
		refreshCapability();
	}

	public static void registerCapabilities() {
		FluidHandlerLookup.registerBlockEntity(TanksRegistry.FLUID_TANK_BLOCK_ENTITY,
			(be, side) -> {
				if (be.fluidCapability == null)
					be.refreshCapability();
				return be.fluidCapability;
			});
	}

	protected SmartFluidTank createInventory() {
		return new SmartFluidTank(getCapacityMultiplier(), this::onFluidStackChanged);
	}

	protected void updateConnectivity() {
		updateConnectivity = false;
		if (level.isClientSide())
			return;
		if (!isController())
			return;
		ConnectivityHandler.formMulti(this);
	}

	@Override
	public void tick() {
		super.tick();
		if (syncCooldown > 0) {
			syncCooldown--;
			if (syncCooldown == 0 && queuedSync)
				sendData();
		}

		if (lastKnownPos == null)
			lastKnownPos = getBlockPos();
		else if (!lastKnownPos.equals(worldPosition) && worldPosition != null) {
			onPositionChanged();
			return;
		}

		if (updateCapability) {
			updateCapability = false;
			refreshCapability();
		}
		if (updateConnectivity)
			updateConnectivity();
		if (fluidLevel != null)
			fluidLevel.tickChaser();
		if (isController())
			boiler.tick(this);
	}

	@Override
	public BlockPos getLastKnownPos() {
		return lastKnownPos;
	}

	@Override
	public boolean isController() {
		return controller == null || worldPosition.getX() == controller.getX()
			&& worldPosition.getY() == controller.getY() && worldPosition.getZ() == controller.getZ();
	}

	@Override
	public void initialize() {
		super.initialize();
		sendData();
	}

	private void onPositionChanged() {
		removeController(true);
		lastKnownPos = worldPosition;
	}

	protected void onFluidStackChanged(FluidStack newFluidStack) {
		if (!hasLevel())
			return;

		int luminosity = window ? (int) (FluidHelper.getLightLevel(newFluidStack) / 1.2f) : 0;
		boolean reversed = FluidHelper.isLighterThanAir(newFluidStack);
		int maxY = (int) ((getFillState() * height) + 1);

		for (int yOffset = 0; yOffset < height; yOffset++) {
			boolean isBright = reversed ? (height - yOffset <= maxY) : (yOffset < maxY);
			int actualLuminosity = isBright ? luminosity : luminosity > 0 ? 1 : 0;

			for (int xOffset = 0; xOffset < width; xOffset++) {
				for (int zOffset = 0; zOffset < width; zOffset++) {
					BlockPos pos = this.worldPosition.offset(xOffset, yOffset, zOffset);
					FluidTankBlockEntity tankAt = ConnectivityHandler.partAt(getType(), level, pos);
					if (tankAt == null)
						continue;
					level.updateNeighbourForOutputSignal(pos, tankAt.getBlockState()
						.getBlock());
					if (tankAt.luminosity == actualLuminosity)
						continue;
					tankAt.setLuminosity(actualLuminosity);
				}
			}
		}

		if (!level.isClientSide()) {
			setChanged();
			sendData();
		}

		if (fluidLevel == null)
			fluidLevel = LerpedFloat.linear()
				.startWithValue(getFillState());
		fluidLevel.chase(getFillState(), .5f, Chaser.EXP);
	}

	protected void setLuminosity(int luminosity) {
		if (level.isClientSide())
			return;
		if (this.luminosity == luminosity)
			return;
		this.luminosity = luminosity;
		BlockState state = getBlockState();
		if (FluidTankBlock.isTank(state) && state.getValue(FluidTankBlock.LUMINOSITY) != luminosity)
			level.setBlock(worldPosition, state.setValue(FluidTankBlock.LUMINOSITY, luminosity),
				Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE | Block.UPDATE_KNOWN_SHAPE);
		sendData();
	}

	@SuppressWarnings("unchecked")
	@Override
	public FluidTankBlockEntity getControllerBE() {
		if (isController() || !hasLevel())
			return this;
		BlockEntity blockEntity = level.getBlockEntity(controller);
		if (blockEntity instanceof FluidTankBlockEntity)
			return (FluidTankBlockEntity) blockEntity;
		return null;
	}

	public void applyFluidTankSize(int blocks) {
		tankInventory.setCapacity(blocks * getCapacityMultiplier());
		int overflow = tankInventory.getFluidAmount() - tankInventory.getCapacity();
		if (overflow > 0)
			tankInventory.drain(overflow, FluidAction.EXECUTE);
		forceFluidLevelUpdate = true;
	}

	public void removeController(boolean keepFluids) {
		if (level.isClientSide())
			return;
		updateConnectivity = true;
		if (!keepFluids)
			applyFluidTankSize(1);
		controller = null;
		width = 1;
		height = 1;
		boiler.clear();
		onFluidStackChanged(tankInventory.getFluid());

		BlockState state = getBlockState();
		if (FluidTankBlock.isTank(state)) {
			state = state.setValue(FluidTankBlock.BOTTOM, true);
			state = state.setValue(FluidTankBlock.TOP, true);
			state = state.setValue(FluidTankBlock.SHAPE, window ? Shape.WINDOW : Shape.PLAIN);
			getLevel().setBlock(worldPosition, state, Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE | Block.UPDATE_KNOWN_SHAPE);
		}

		refreshCapability();
		setChanged();
		sendData();
	}

	public void toggleWindows() {
		FluidTankBlockEntity be = getControllerBE();
		if (be == null)
			return;
		if (be.boiler.isActive())
			return;
		be.setWindows(!be.window);
	}

	public void updateBoilerTemperature() {
		FluidTankBlockEntity be = getControllerBE();
		if (be == null)
			return;
		if (!be.boiler.isActive())
			return;
		be.boiler.needsHeatLevelUpdate = true;
	}

	public void sendDataImmediately() {
		syncCooldown = 0;
		queuedSync = false;
		sendData();
	}

	@Override
	public void sendData() {
		if (syncCooldown > 0) {
			queuedSync = true;
			return;
		}
		super.sendData();
		queuedSync = false;
		syncCooldown = SYNC_RATE;
	}

	public void setWindows(boolean window) {
		this.window = window;
		for (int yOffset = 0; yOffset < height; yOffset++) {
			for (int xOffset = 0; xOffset < width; xOffset++) {
				for (int zOffset = 0; zOffset < width; zOffset++) {

					BlockPos pos = this.worldPosition.offset(xOffset, yOffset, zOffset);
					BlockState blockState = level.getBlockState(pos);
					if (!FluidTankBlock.isTank(blockState))
						continue;

					Shape shape = Shape.PLAIN;
					if (window) {
						// SIZE 1: Every tank has a window
						if (width == 1)
							shape = Shape.WINDOW;
						// SIZE 2: Every tank has a corner window
						if (width == 2)
							shape = xOffset == 0 ? zOffset == 0 ? Shape.WINDOW_NW : Shape.WINDOW_SW
								: zOffset == 0 ? Shape.WINDOW_NE : Shape.WINDOW_SE;
						// SIZE 3: Tanks in the center have a window
						if (width == 3 && abs(abs(xOffset) - abs(zOffset)) == 1)
							shape = Shape.WINDOW;
					}

					level.setBlock(pos, blockState.setValue(FluidTankBlock.SHAPE, shape), Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE | Block.UPDATE_KNOWN_SHAPE);
				}
			}
		}
		// the tank only gives light through its windows
		onFluidStackChanged(tankInventory.getFluid());
		sendData();
	}

	public void updateBoilerState() {
		if (!isController())
			return;

		boolean wasBoiler = boiler.isActive();
		boolean changed = boiler.evaluate(this);

		if (wasBoiler != boiler.isActive()) {
			if (boiler.isActive())
				setWindows(false);

			for (int yOffset = 0; yOffset < height; yOffset++)
				for (int xOffset = 0; xOffset < width; xOffset++)
					for (int zOffset = 0; zOffset < width; zOffset++)
						if (level.getBlockEntity(
							worldPosition.offset(xOffset, yOffset, zOffset)) instanceof FluidTankBlockEntity fbe)
							fbe.refreshCapability();
		}

		if (changed) {
			notifyUpdate();
		}
	}

	@Override
	public void setController(BlockPos controller) {
		if (level.isClientSide())
			return;
		if (controller.equals(this.controller))
			return;
		this.controller = controller;
		refreshCapability();
		setChanged();
		sendData();
	}

	void refreshCapability() {
		fluidCapability = handlerForCapability();
	}

	private IFluidHandler handlerForCapability() {
		return isController() ? (boiler.isActive() ? boiler.createHandler() : tankInventory)
				: ((getControllerBE() != null) ? getControllerBE().handlerForCapability() : new FluidTank(0));
	}

	@Override
	public BlockPos getController() {
		return isController() ? worldPosition : controller;
	}

	@Nullable
	public FluidTankBlockEntity getOtherFluidTankBlockEntity(Direction direction) {
		BlockEntity otherBE = level.getBlockEntity(worldPosition.relative(direction));
		if (otherBE instanceof FluidTankBlockEntity)
			return (FluidTankBlockEntity) otherBE;
		return null;
	}

	/** The boiler lines of Create's goggle tooltip; false if this tank is not used as a boiler. */
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		FluidTankBlockEntity controllerBE = getControllerBE();
		if (controllerBE == null)
			return false;
		return controllerBE.boiler.addToGoggleTooltip(tooltip, isPlayerSneaking, controllerBE.getTotalTankSize());
	}

	@Override
	protected void read(ValueInput compound) {
		super.read(compound);

		updateConnectivity = compound.getBooleanOr("Uninitialized", false);
		luminosity = compound.getIntOr("Luminosity", 0);

		lastKnownPos = compound.read("LastKnownPos", BlockPos.CODEC)
			.orElse(null);

		controller = compound.read("Controller", BlockPos.CODEC)
			.orElse(null);

		if (isController()) {
			window = compound.getBooleanOr("Window", true);
			width = compound.getIntOr("Size", 1);
			height = compound.getIntOr("Height", 1);
			tankInventory.setCapacity(getTotalTankSize() * getCapacityMultiplier());

			tankInventory.read(compound.childOrEmpty("TankContent"));
			if (tankInventory.getSpace() < 0)
				tankInventory.drain(-tankInventory.getSpace(), FluidAction.EXECUTE);
		}

		boiler.read(compound.childOrEmpty("Boiler"), width * width * height);

		fluidLevel = LerpedFloat.linear()
			.startWithValue(getFillState());

		updateCapability = true;
	}

	public float getFillState() {
		return (float) tankInventory.getFluidAmount() / tankInventory.getCapacity();
	}

	@Override
	public void write(ValueOutput compound) {
		if (updateConnectivity)
			compound.putBoolean("Uninitialized", true);
		boiler.write(compound.child("Boiler"));
		if (lastKnownPos != null)
			compound.store("LastKnownPos", BlockPos.CODEC, lastKnownPos);
		if (!isController())
			compound.store("Controller", BlockPos.CODEC, controller);
		if (isController()) {
			compound.putBoolean("Window", window);
			tankInventory.write(compound.child("TankContent"));
			compound.putInt("Size", width);
			compound.putInt("Height", height);
		}
		compound.putInt("Luminosity", luminosity);
		super.write(compound);
	}

	public FluidTank getTankInventory() {
		return tankInventory;
	}

	public int getTotalTankSize() {
		return width * width * height;
	}

	public static int getMaxSize() {
		return MAX_SIZE;
	}

	public static int getCapacityMultiplier() {
		return FLUID_TANK_CAPACITY * 1000;
	}

	public static int getMaxHeight() {
		return FLUID_TANK_MAX_HEIGHT;
	}

	public LerpedFloat getFluidLevel() {
		return fluidLevel;
	}

	public void setFluidLevel(LerpedFloat fluidLevel) {
		this.fluidLevel = fluidLevel;
	}

	public boolean hasWindow() {
		return window;
	}

	@Override
	public void preventConnectivityUpdate() {
		updateConnectivity = false;
	}

	@Override
	public void notifyMultiUpdated() {
		BlockState state = this.getBlockState();
		if (FluidTankBlock.isTank(state)) { // safety
			state = state.setValue(FluidTankBlock.BOTTOM, getController().getY() == getBlockPos().getY());
			state = state.setValue(FluidTankBlock.TOP, getController().getY() + height - 1 == getBlockPos().getY());
			level.setBlock(getBlockPos(), state, Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE);
		}
		if (isController())
			setWindows(window);
		onFluidStackChanged(tankInventory.getFluid());
		updateBoilerState();
		setChanged();
	}

	@Override
	public void setExtraData(@Nullable Object data) {
		if (data instanceof Boolean)
			window = (boolean) data;
	}

	@Override
	@Nullable
	public Object getExtraData() {
		return window;
	}

	@Override
	public Object modifyExtraData(Object data) {
		if (data instanceof Boolean windows) {
			windows |= window;
			return windows;
		}
		return data;
	}

	@Override
	public Direction.Axis getMainConnectionAxis() {
		return Direction.Axis.Y;
	}

	@Override
	public int getMaxLength(Direction.Axis longAxis, int width) {
		if (longAxis == Direction.Axis.Y)
			return getMaxHeight();
		return getMaxWidth();
	}

	@Override
	public int getMaxWidth() {
		return MAX_SIZE;
	}

	@Override
	public int getHeight() {
		return height;
	}

	@Override
	public void setHeight(int height) {
		this.height = height;
	}

	@Override
	public int getWidth() {
		return width;
	}

	@Override
	public void setWidth(int width) {
		this.width = width;
	}

	@Override
	public boolean hasTank() {
		return true;
	}

	@Override
	public int getTankSize(int tank) {
		return getCapacityMultiplier();
	}

	@Override
	public void setTankSize(int tank, int blocks) {
		applyFluidTankSize(blocks);
	}

	@Override
	public IFluidTank getTank(int tank) {
		return tankInventory;
	}

	@Override
	public FluidStack getFluid(int tank) {
		return tankInventory.getFluid()
			.copy();
	}

	/** Block broken or replaced: split the multiblock (Create's FluidTankBlock.onRemove). */
	@Override
	public void destroy() {
		super.destroy();
		level.removeBlockEntity(worldPosition);
		ConnectivityHandler.splitMulti(this);
	}
}
