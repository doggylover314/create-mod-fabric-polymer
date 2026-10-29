package dev.createpolymer.fluids;

import java.util.Optional;
import java.util.function.Predicate;

import com.mojang.serialization.Codec;

import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.util.BlockFace;
import dev.createpolymer.util.Couple;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class PipeConnection {

	public Direction side;

	// Layer I
	Couple<Float> pressure; // [inbound, outward]
	Optional<FlowSource> source;
	Optional<FlowSource> previousSource;

	// Layer II
	Optional<Flow> flow;

	// Layer III
	Optional<FluidNetwork> network; // not serialized

	public PipeConnection(Direction side) {
		this.side = side;
		pressure = Couple.create(() -> 0f);
		flow = Optional.empty();
		previousSource = Optional.empty();
		source = Optional.empty();
		network = Optional.empty();
	}

	public FluidStack getProvidedFluid() {
		FluidStack empty = FluidStack.EMPTY;
		if (!hasFlow())
			return empty;
		Flow flow = this.flow.get();
		if (!flow.inbound)
			return empty;
		if (!flow.complete)
			return empty;
		return flow.fluid;
	}

	public boolean flipFlowsIfPressureReversed() {
		if (!hasFlow())
			return false;
		boolean singlePressure = comparePressure() != 0 && (getInboundPressure() == 0 || getOutwardPressure() == 0);
		Flow flow = this.flow.get();
		if (!singlePressure || comparePressure() < 0 == flow.inbound)
			return false;
		flow.inbound = !flow.inbound;
		if (!flow.complete)
			this.flow = Optional.empty();
		return true;
	}

	public void manageSource(Level world, BlockPos pos, BlockEntity blockEntity) {
		if (!source.isPresent() && !determineSource(world, pos))
			return;
		FlowSource flowSource = source.get();
		flowSource.manageSource(world, blockEntity);
	}

	public boolean manageFlows(Level world, BlockPos pos, FluidStack internalFluid,
		Predicate<FluidStack> extractionPredicate) {

		// Only keep network if still valid
		Optional<FluidNetwork> retainedNetwork = network;
		network = Optional.empty();

		// chunk border
		if (!source.isPresent() && !determineSource(world, pos))
			return false;
		FlowSource flowSource = source.get();

		if (!hasFlow()) {
			if (!hasPressure())
				return false;

			// Try starting a new flow
			boolean prioritizeInbound = comparePressure() < 0;
			for (boolean trueFalse : Iterate.trueAndFalse) {
				boolean inbound = prioritizeInbound == trueFalse;
				if (pressure.get(inbound) == 0)
					continue;
				if (tryStartingNewFlow(inbound, inbound ? flowSource.provideFluid(extractionPredicate) : internalFluid))
					return true;
			}
			return false;
		}

		// Manage existing flow
		Flow flow = this.flow.get();
		FluidStack provided = flow.inbound ? flowSource.provideFluid(extractionPredicate) : internalFluid;
		if (!hasPressure() || provided.isEmpty() || !FluidStack.isSameFluidSameComponents(provided, flow.fluid)) {
			this.flow = Optional.empty();
			return true;
		}

		// Overwrite existing flow
		if (flow.inbound != comparePressure() < 0) {
			boolean inbound = !flow.inbound;
			if (inbound && !provided.isEmpty() || !inbound && !internalFluid.isEmpty()) {
				FluidPropagator.resetAffectedFluidNetworks(world, pos, side);
				tryStartingNewFlow(inbound, inbound ? flowSource.provideFluid(extractionPredicate) : internalFluid);
				return true;
			}
		}

		flowSource.whileFlowPresent(world, flow.inbound);

		if (!flowSource.isEndpoint())
			return false;
		if (!flow.inbound)
			return false;

		// Layer III
		network = retainedNetwork;
		if (!hasNetwork())
			network = Optional.of(new FluidNetwork(world, new BlockFace(pos, side), flowSource::provideHandler));
		network.get()
			.tick();

		return false;
	}

	private boolean tryStartingNewFlow(boolean inbound, FluidStack providedFluid) {
		if (providedFluid.isEmpty())
			return false;
		Flow flow = new Flow(inbound, providedFluid);
		this.flow = Optional.of(flow);
		return true;
	}

	public boolean determineSource(Level world, BlockPos pos) {
		BlockPos relative = pos.relative(side);
		// cannot use world.isLoaded because it always returns true on client
		if (world.getChunk(relative.getX() >> 4, relative.getZ() >> 4, ChunkStatus.FULL, false) == null)
			return false;

		BlockFace location = new BlockFace(pos, side);
		if (FluidPropagator.isOpenEnd(world, pos, side)) {
			if (previousSource.orElse(null) instanceof OpenEndedPipe)
				source = previousSource;
			else
				source = Optional.of(new OpenEndedPipe(location));
			return true;
		}

		if (FluidPropagator.hasFluidCapability(world, location.getConnectedPos(), side.getOpposite())) {
			source = Optional.of(new FlowSource.FluidHandler(location));
			return true;
		}

		FluidTransportBehaviour behaviour =
			BlockEntityBehaviour.get(world, relative, FluidTransportBehaviour.TYPE);
		source = Optional.of(behaviour == null ? new FlowSource.Blocked(location) : new FlowSource.OtherPipe(location));
		return true;
	}

	public void tickFlowProgress(Level world, BlockPos pos) {
		if (!hasFlow())
			return;
		Flow flow = this.flow.get();
		if (flow.fluid.isEmpty())
			return;

		float flowSpeed = 1 / 32f + Mth.clamp(pressure.get(flow.inbound) / 128f, 0, 1) * 31 / 32f;
		flow.progress.setValue(Math.min(flow.progress.getValue() + flowSpeed, 1));
		if (flow.progress.getValue() >= 1)
			flow.complete = true;
	}

	public void serializeNBT(ValueOutput output) {
		ValueOutput connectionData = output.child(side.getName());

		if (hasPressure()) {
			connectionData.putFloat("InboundPressure", getInboundPressure());
			connectionData.putFloat("OutwardPressure", getOutwardPressure());
		}

		if (hasOpenEnd())
			((OpenEndedPipe) source.get()).serializeNBT(connectionData.child("OpenEnd"));

		if (hasFlow()) {
			ValueOutput flowData = connectionData.child("Flow");
			Flow flow = this.flow.get();
			flowData.store("Fluid", FluidStack.OPTIONAL_CODEC, flow.fluid);
			flowData.putBoolean("In", flow.inbound);
			if (!flow.complete)
				flowData.putFloat("Progress", flow.progress.getValue());
		}

	}

	private boolean hasOpenEnd() {
		return source.orElse(null) instanceof OpenEndedPipe;
	}

	public void deserializeNBT(ValueInput input, BlockPos blockEntityPos) {
		Optional<ValueInput> connection = input.child(side.getName());
		if (connection.isEmpty()) {
			pressure.replace(f -> 0f);
			source = Optional.empty();
			flow = Optional.empty();
			return;
		}
		ValueInput connectionData = connection.get();

		if (connectionData.getFloatOr("InboundPressure", 0) != 0 || connectionData.getFloatOr("OutwardPressure", 0) != 0)
			pressure = Couple.create(connectionData.getFloatOr("InboundPressure", 0),
				connectionData.getFloatOr("OutwardPressure", 0));
		else
			pressure.replace(f -> 0f);

		source = Optional.empty();
		Optional<ValueInput> openEnd = connectionData.child("OpenEnd");
		if (openEnd.isPresent())
			source = Optional.of(OpenEndedPipe.fromNBT(openEnd.get(), blockEntityPos));

		Optional<ValueInput> flowTag = connectionData.child("Flow");
		if (flowTag.isPresent()) {
			ValueInput flowData = flowTag.get();

			FluidStack fluid = flowData.read("Fluid", FluidStack.OPTIONAL_CODEC)
				.orElse(FluidStack.EMPTY);
			boolean inbound = flowData.getBooleanOr("In", false);
			if (flow.isEmpty())
				flow = Optional.of(new Flow(inbound, fluid));
			Flow flow = this.flow.get();

			flow.fluid = fluid;
			flow.inbound = inbound;
			Optional<Float> progress = flowData.read("Progress", Codec.FLOAT);
			flow.complete = progress.isEmpty();

			if (!flow.complete)
				flow.progress.setValue(progress.get());
			else {
				if (flow.progress.getValue() == 0)
					flow.progress.startWithValue(1);
				flow.progress.setValue(1);
			}

		} else
			flow = Optional.empty();

	}

	/**
	 * @return zero if outward == inbound <br>
	 *         positive if outward {@literal >} inbound <br>
	 *         negative if outward {@literal <} inbound
	 */
	public float comparePressure() {
		return getOutwardPressure() - getInboundPressure();
	}

	public void wipePressure() {
		this.pressure.replace(f -> 0f);
		if (this.source.isPresent())
			this.previousSource = this.source;
		this.source = Optional.empty();
		resetNetwork();
	}

	public FluidStack provideOutboundFlow() {
		if (!hasFlow())
			return FluidStack.EMPTY;
		Flow flow = this.flow.get();
		if (!flow.complete || flow.inbound)
			return FluidStack.EMPTY;
		return flow.fluid;
	}

	public void addPressure(boolean inbound, float pressure) {
		this.pressure = this.pressure.mapWithContext((f, in) -> in == inbound ? f + pressure : f);
	}

	public Couple<Float> getPressure() {
		return pressure;
	}

	public boolean hasPressure() {
		return getInboundPressure() != 0 || getOutwardPressure() != 0;
	}

	private float getOutwardPressure() {
		return pressure.getSecond();
	}

	private float getInboundPressure() {
		return pressure.getFirst();
	}

	public boolean hasFlow() {
		return flow.isPresent();
	}

	public boolean hasNetwork() {
		return network.isPresent();
	}

	public void resetNetwork() {
		network.ifPresent(FluidNetwork::reset);
	}

	public class Flow {

		public boolean complete;
		public boolean inbound;
		public LerpedFloat progress;
		public FluidStack fluid;

		public Flow(boolean inbound, FluidStack fluid) {
			this.inbound = inbound;
			this.fluid = fluid;
			this.progress = LerpedFloat.linear()
				.startWithValue(0);
			this.complete = false;
		}

	}

	public static final float RIM_RADIUS = 1 / 4f + 1 / 64f;

}
