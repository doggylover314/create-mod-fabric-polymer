package dev.createpolymer.fluids.spout;

import static dev.createpolymer.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult.HOLD;
import static dev.createpolymer.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult.PASS;

import java.util.ArrayList;
import java.util.List;

import dev.createpolymer.api.behaviour.spouting.BlockSpoutingBehaviour;
import dev.createpolymer.api.equipment.goggles.IHaveGoggleInformation;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import dev.createpolymer.foundation.fluid.FluidHandlerLookup;
import dev.createpolymer.foundation.fluid.FluidHelper;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.kinetics.belt.behaviour.BeltProcessingBehaviour;
import dev.createpolymer.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult;
import dev.createpolymer.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import dev.createpolymer.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import dev.createpolymer.kinetics.belt.transport.TransportedItemStack;
import dev.createpolymer.registry.feature.TanksRegistry;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's SpoutBlockEntity (MIT, The Create Team). Sequenced assembly, advancements and the client
 * particles are left out; the splash is sent as vanilla particles.
 */
public class SpoutBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

	public static final int FILLING_TIME = 20;
	protected BeltProcessingBehaviour beltProcessing;

	public int processingTicks;
	public boolean sendSplash;
	public BlockSpoutingBehaviour customProcess;

	SmartFluidTankBehaviour tank;

	public SpoutBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		processingTicks = -1;
	}

	public static void registerCapabilities() {
		FluidHandlerLookup.registerBlockEntity(TanksRegistry.SPOUT_BLOCK_ENTITY,
			(be, side) -> {
				if (side != Direction.DOWN)
					return be.tank.getCapability();
				return null;
			});
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		tank = SmartFluidTankBehaviour.single(this, 1000);
		behaviours.add(tank);

		beltProcessing = new BeltProcessingBehaviour(this).whenItemEnters(this::onItemReceived)
			.whileItemHeld(this::whenItemHeld);
		behaviours.add(beltProcessing);
	}

	public SmartFluidTankBehaviour getTank() {
		return tank;
	}

	protected ProcessingResult onItemReceived(TransportedItemStack transported,
											  TransportedItemStackHandlerBehaviour handler) {
		if (!FillingBySpout.canItemBeFilled(level, transported.stack))
			return PASS;
		if (tank.isEmpty())
			return HOLD;
		if (FillingBySpout.getRequiredAmountForItem(level, transported.stack, getCurrentFluidInTank()) == -1)
			return PASS;
		return HOLD;
	}

	protected ProcessingResult whenItemHeld(TransportedItemStack transported,
											TransportedItemStackHandlerBehaviour handler) {
		if (processingTicks != -1 && processingTicks != 5)
			return HOLD;
		if (!FillingBySpout.canItemBeFilled(level, transported.stack))
			return PASS;
		if (tank.isEmpty())
			return HOLD;
		FluidStack fluid = getCurrentFluidInTank();
		int requiredAmountForItem = FillingBySpout.getRequiredAmountForItem(level, transported.stack, fluid.copy());
		if (requiredAmountForItem == -1)
			return PASS;
		if (requiredAmountForItem > fluid.getAmount())
			return HOLD;

		if (processingTicks == -1) {
			processingTicks = FILLING_TIME;
			notifyUpdate();
			level.playSound(null, worldPosition, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.75f,
				0.9f + 0.2f * (float) Math.random());
			return HOLD;
		}

		// Process finished
		ItemStack out = FillingBySpout.fillItem(level, requiredAmountForItem, transported.stack, fluid);
		if (!out.isEmpty()) {
			transported.clearFanProcessingData();
			List<TransportedItemStack> outList = new ArrayList<>();
			TransportedItemStack held = null;
			TransportedItemStack result = transported.copy();
			result.stack = out;
			if (!transported.stack.isEmpty())
				held = transported.copy();
			outList.add(result);
			handler.handleProcessingOnItem(transported, TransportedResult.convertToAndLeaveHeld(outList, held));
		}

		tank.getPrimaryHandler()
			.setFluid(fluid);
		sendSplash = true;
		notifyUpdate();
		return HOLD;
	}

	private FluidStack getCurrentFluidInTank() {
		return tank.getPrimaryHandler()
			.getFluid();
	}

	@Override
	protected void write(ValueOutput compound) {
		super.write(compound);

		compound.putInt("ProcessingTicks", processingTicks);
	}

	@Override
	protected void read(ValueInput compound) {
		super.read(compound);
		processingTicks = compound.getIntOr("ProcessingTicks", 0);
	}

	@Override
	public void tick() {
		super.tick();

		FluidStack currentFluidInTank = getCurrentFluidInTank();
		if (processingTicks == -1 && !level.isClientSide() && !currentFluidInTank.isEmpty()) {
			BlockPos filling = this.worldPosition.below(2);
			BlockSpoutingBehaviour behavior = BlockSpoutingBehaviour.get(this.level, filling);
			if (behavior != null && behavior.fillBlock(this.level, filling, this, currentFluidInTank.copy(), true) > 0) {
				processingTicks = FILLING_TIME;
				customProcess = behavior;
				notifyUpdate();
			}
		}

		if (processingTicks >= 0) {
			processingTicks--;
			if (processingTicks == 5 && customProcess != null) {
				int fillBlock = customProcess.fillBlock(level, worldPosition.below(2), this, currentFluidInTank.copy(), false);
				customProcess = null;
				if (fillBlock > 0) {
					tank.getPrimaryHandler()
						.setFluid(FluidHelper.copyStackWithAmount(currentFluidInTank,
							currentFluidInTank.getAmount() - fillBlock));
					sendSplash = true;
					notifyUpdate();
				}
			}
		}

		if (sendSplash) {
			sendSplash = false;
			spawnSplash(tank.getPrimaryTank()
				.getRenderedFluid());
		}
	}

	protected static int SPLASH_PARTICLE_COUNT = 20;

	protected void spawnSplash(FluidStack fluid) {
		if (fluid.isEmpty() || !(level instanceof ServerLevel serverLevel))
			return;
		Vec3 vec = VecHelper.getCenterOf(worldPosition);
		vec = vec.subtract(0, 2 - 5 / 16f, 0);
		ParticleOptions particle = FluidHelper.isLava(fluid.getFluid()) ? ParticleTypes.LAVA
			: FluidHelper.isWater(fluid.getFluid()) ? ParticleTypes.SPLASH : ParticleTypes.POOF;
		serverLevel.sendParticles(particle, vec.x, vec.y, vec.z, SPLASH_PARTICLE_COUNT, .125, .125, .125, .05);
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		return containedFluidTooltip(tooltip, isPlayerSneaking,
			FluidHandlerLookup.get(level, worldPosition, null));
	}

}
