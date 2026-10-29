package dev.createpolymer.fluids;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.fluids.effect.OpenPipeEffectHandler;
import dev.createpolymer.fluids.pipes.VanillaFluidTargets;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.FluidTank;
import dev.createpolymer.foundation.fluid.IFluidHandler;
import dev.createpolymer.util.BlockFace;
import dev.createpolymer.util.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

public class OpenEndedPipe extends FlowSource {

	private Level world;
	private BlockPos pos;
	private AABB aoe;

	private OpenEndFluidHandler fluidHandler;
	private BlockPos outputPos;
	private boolean wasPulling;

	private final ICapabilityProvider<IFluidHandler> fluidHandlerProvider = ICapabilityProvider.of(() -> fluidHandler);

	public OpenEndedPipe(BlockFace face) {
		super(face);
		fluidHandler = new OpenEndFluidHandler();
		outputPos = face.getConnectedPos();
		pos = face.getPos();
		aoe = new AABB(outputPos).expandTowards(0, -1, 0);
		if (face.getFace() == Direction.DOWN)
			aoe = aoe.expandTowards(0, -1, 0);
	}

	public Level getWorld() {
		return world;
	}

	public BlockPos getPos() {
		return pos;
	}

	public BlockPos getOutputPos() {
		return outputPos;
	}

	public AABB getAOE() {
		return aoe;
	}

	@Override
	public void manageSource(Level world, BlockEntity networkBE) {
		this.world = world;
	}

	@Override
	@Nullable
	public ICapabilityProvider<IFluidHandler> provideHandler() {
		return fluidHandlerProvider;
	}

	@Override
	public boolean isEndpoint() {
		return true;
	}

	public void serializeNBT(ValueOutput compound) {
		fluidHandler.write(compound);
		compound.putBoolean("Pulling", wasPulling);
		NBTHelper.writeEnum(compound, "Face", location.getFace());
	}

	public static OpenEndedPipe fromNBT(ValueInput compound, BlockPos blockEntityPos) {
		Direction face = NBTHelper.readEnum(compound, "Face", Direction.class);
		OpenEndedPipe oep = new OpenEndedPipe(new BlockFace(blockEntityPos, face));

		oep.fluidHandler.read(compound);
		oep.wasPulling = compound.getBooleanOr("Pulling", false);
		return oep;
	}

	private FluidStack removeFluidFromSpace(boolean simulate) {
		FluidStack empty = FluidStack.EMPTY;
		if (world == null)
			return empty;
		if (!world.isLoaded(outputPos))
			return empty;

		BlockState state = world.getBlockState(outputPos);
		FluidState fluidState = state.getFluidState();
		boolean waterlog = state.hasProperty(WATERLOGGED);

		FluidStack drainBlock = VanillaFluidTargets.drainBlock(world, outputPos, state, simulate);
		if (!drainBlock.isEmpty()) {
				return drainBlock;
		}

		if (!waterlog && !state.canBeReplaced())
			return empty;
		if (fluidState.isEmpty() || !fluidState.isSource())
			return empty;

		FluidStack stack = new FluidStack(fluidState.getType(), 1000);

		if (simulate)
			return stack;

		if (waterlog) {
			world.setBlock(outputPos, state.setValue(WATERLOGGED, false), Block.UPDATE_ALL);
			world.scheduleTick(outputPos, Fluids.WATER, 1);
		} else {
			var newState = fluidState.createLegacyBlock()
				.setValue(LiquidBlock.LEVEL, 14);

			var newFluidState = newState.getFluidState();

			if (newFluidState.getType() instanceof FlowingFluid flowing && world instanceof ServerLevel serverLevel) {
				var potentiallyFilled = getNewLiquid(flowing, serverLevel, outputPos, newState);

				// Check if we'd immediately become the same fluid again.
				if (fluidState.equals(potentiallyFilled)) {
					// If so, no need to update the block state.
					return stack;
				}
			}

			world.setBlock(outputPos, newState, Block.UPDATE_ALL);
		}

		return stack;
	}

	private boolean provideFluidToSpace(FluidStack fluid, boolean simulate) {
		if (world == null)
			return false;
		if (!world.isLoaded(outputPos))
			return false;

		BlockState state = world.getBlockState(outputPos);
		FluidState fluidState = state.getFluidState();
		boolean waterlog = state.hasProperty(WATERLOGGED);

		if (!waterlog && !state.canBeReplaced())
			return false;
		if (fluid.isEmpty())
			return false;
		if (!(fluid.getFluid() instanceof FlowingFluid))
			return false;
		if (!PipeHelper.hasBlockState(fluid.getFluid()))
			return true;

		if (!fluidState.isEmpty() && PipeHelper.convertToStill(fluidState.getType()) != fluid.getFluid()) {
			FluidReactions.handlePipeSpillCollision(world, outputPos, fluid.getFluid(), fluidState);
			return false;
		}

		if (fluidState.isSource())
			return false;
		if (waterlog && fluid.getFluid() != Fluids.WATER)
			return false;
		if (simulate)
			return true;

		if (!FluidConfig.pipesPlaceFluidSourceBlocks)
			return true;

		if (world.environmentAttributes()
			.getValue(EnvironmentAttributes.WATER_EVAPORATES, outputPos) && PipeHelper.isTag(fluid, FluidTags.WATER)) {
			int i = outputPos.getX();
			int j = outputPos.getY();
			int k = outputPos.getZ();
			world.playSound(null, i, j, k, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F,
				2.6F + (world.getRandom().nextFloat() - world.getRandom().nextFloat()) * 0.8F);
			return true;
		}

		if (waterlog) {
			world.setBlock(outputPos, state.setValue(WATERLOGGED, true), Block.UPDATE_ALL);
			world.scheduleTick(outputPos, Fluids.WATER, 1);
			return true;
		}

		world.setBlock(outputPos, fluid.getFluid()
			.defaultFluidState()
			.createLegacyBlock(), Block.UPDATE_ALL);
		return true;
	}

	private static final Method GET_NEW_LIQUID = findGetNewLiquid();

	/** FlowingFluid#getNewLiquid is protected (Create reaches it with an accessor mixin). */
	private static Method findGetNewLiquid() {
		try {
			Method method = FlowingFluid.class.getDeclaredMethod("getNewLiquid", ServerLevel.class, BlockPos.class, BlockState.class);
			method.setAccessible(true);
			return method;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	@Nullable
	private static FluidState getNewLiquid(FlowingFluid fluid, ServerLevel level, BlockPos pos, BlockState state) {
		if (GET_NEW_LIQUID == null)
			return null;
		try {
			return (FluidState) GET_NEW_LIQUID.invoke(fluid, level, pos, state);
		} catch (IllegalAccessException | InvocationTargetException e) {
			return null;
		}
	}

	private class OpenEndFluidHandler extends FluidTank {

		public OpenEndFluidHandler() {
			super(1000);
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			// Never allow being filled when a source is attached
			if (world == null)
				return 0;
			if (!world.isLoaded(outputPos))
				return 0;
			if (resource.isEmpty())
				return 0;
			if (!provideFluidToSpace(resource, true))
				return 0;

			FluidStack containedFluidStack = getFluid();
			boolean hasBlockState = PipeHelper.hasBlockState(containedFluidStack.getFluid());

			if (!containedFluidStack.isEmpty() && !FluidStack.isSameFluidSameComponents(containedFluidStack, resource))
				setFluid(FluidStack.EMPTY);
			if (wasPulling)
				wasPulling = false;

			OpenPipeEffectHandler effectHandler = OpenPipeEffectHandler.REGISTRY.get(resource.getFluid());
			if (effectHandler != null && !hasBlockState)
				resource = PipeHelper.copyStackWithAmount(resource, 1);

			int fill = super.fill(resource, action);
			if (action.simulate())
				return fill;

			if (effectHandler != null && !resource.isEmpty()) {
				// resource should be copied before giving it to the handler.
				// if hasBlockState is false, it was already copied above.
				FluidStack exposed = hasBlockState ? resource.copy() : resource;
				effectHandler.apply(world, aoe, exposed);
			}

			if (getFluidAmount() == 1000 || !hasBlockState)
				if (provideFluidToSpace(containedFluidStack, false))
					setFluid(FluidStack.EMPTY);
			return fill;
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			return drainInner(resource.getAmount(), resource, action);
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			return drainInner(maxDrain, null, action);
		}

		private FluidStack drainInner(int amount, @Nullable FluidStack filter, FluidAction action) {
			FluidStack empty = FluidStack.EMPTY;
			boolean filterPresent = filter != null;

			if (world == null)
				return empty;
			if (!world.isLoaded(outputPos))
				return empty;
			if (amount == 0)
				return empty;
			if (amount > 1000) {
				amount = 1000;
				if (filterPresent)
					filter = PipeHelper.copyStackWithAmount(filter, amount);
			}

			if (!wasPulling)
				wasPulling = true;

			FluidStack drainedFromInternal = filterPresent ? super.drain(filter, action) : super.drain(amount, action);
			if (!drainedFromInternal.isEmpty())
				return drainedFromInternal;

			FluidStack drainedFromWorld = removeFluidFromSpace(action.simulate());
			if (drainedFromWorld.isEmpty())
				return FluidStack.EMPTY;
			if (filterPresent && !FluidStack.isSameFluidSameComponents(drainedFromWorld, filter))
				return FluidStack.EMPTY;

			int remainder = drainedFromWorld.getAmount() - amount;
			drainedFromWorld.setAmount(amount);

			if (!action.simulate() && remainder > 0) {
				if (!getFluid().isEmpty() && !FluidStack.isSameFluidSameComponents(getFluid(), drainedFromWorld))
					setFluid(FluidStack.EMPTY);
				super.fill(PipeHelper.copyStackWithAmount(drainedFromWorld, remainder), FluidAction.EXECUTE);
			}
			return drainedFromWorld;
		}

	}
}
