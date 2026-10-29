package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import dev.createpolymer.fluids.FluidPropagator;
import dev.createpolymer.fluids.FluidReactions;
import dev.createpolymer.fluids.FluidTransportBehaviour;
import dev.createpolymer.fluids.hosePulley.HosePulleyBlock;
import dev.createpolymer.fluids.hosePulley.HosePulleyBlockEntity;
import dev.createpolymer.fluids.pipes.EncasedPipeBlock;
import dev.createpolymer.fluids.pipes.FluidPipeBlock;
import dev.createpolymer.fluids.pipes.GlassFluidPipeBlock;
import dev.createpolymer.fluids.pipes.SmartFluidPipeBlock;
import dev.createpolymer.fluids.pipes.SmartFluidPipeBlockEntity;
import dev.createpolymer.fluids.pipes.VanillaFluidTargets;
import dev.createpolymer.fluids.pipes.valve.FluidValveBlock;
import dev.createpolymer.fluids.pump.PumpBlock;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.foundation.fluid.FluidHandlerLookup;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.IFluidHandler;
import dev.createpolymer.kinetics.base.DirectionalAxisKineticBlock;
import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.base.RotatedPillarKineticBlock;
import dev.createpolymer.kinetics.motor.CreativeMotorBlockEntity;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.feature.PipesRegistry;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.material.Fluids;

public class PipesGameTests {

	private static BlockState waterCauldron() {
		return Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3);
	}

	private static void motor(GameTestHelper helper, BlockPos pos, Direction facing, int rpm) {
		helper.setBlock(pos, ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, facing));
		helper.runAfterDelay(2, () -> helper.getBlockEntity(pos, CreativeMotorBlockEntity.class).setSetting(rpm));
	}

	private static void pipe(GameTestHelper helper, BlockPos pos, Axis axis) {
		helper.setBlock(pos, PipesRegistry.FLUID_PIPE.getAxisState(axis));
	}

	/**
	 * A pump at {@code pos} facing {@code facing}, turned by a motor through a cogwheel on top of it
	 * (the motor sits one block further along the pump's axis).
	 */
	private static void pump(GameTestHelper helper, BlockPos pos, Direction facing, int rpm) {
		helper.setBlock(pos, PipesRegistry.MECHANICAL_PUMP.defaultBlockState().setValue(PumpBlock.FACING, facing));
		helper.setBlock(pos.above(), ModBlocks.COGWHEEL.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, facing.getAxis()));
		motor(helper, pos.above().relative(facing), facing.getOpposite(), rpm);
	}

	private static float pumpSpeed(GameTestHelper helper, BlockPos pos) {
		return TestUtil.speed(helper, pos);
	}

	private static FluidTransportBehaviour transport(GameTestHelper helper, BlockPos pos) {
		return BlockEntityBehaviour.get(helper.getLevel(), helper.absolutePos(pos), FluidTransportBehaviour.TYPE);
	}

	/** Water cauldron, a powered pump, {@code pipes} pipes and an open end, west to east along y = 1, z = 1. */
	private static BlockPos pumpLine(GameTestHelper helper, int pipes) {
		floor(helper);
		helper.setBlock(new BlockPos(1, 1, 1), waterCauldron());
		pump(helper, new BlockPos(2, 1, 1), Direction.EAST, 256);
		for (int i = 0; i < pipes; i++)
			pipe(helper, new BlockPos(3 + i, 1, 1), Axis.X);
		return new BlockPos(3 + pipes, 1, 1);
	}

	@GameTest(maxTicks = 40)
	public void pipesConnectToEachOtherAndToPumps(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(2, 1, 2), PipesRegistry.FLUID_PIPE.defaultBlockState());
		helper.setBlock(new BlockPos(3, 1, 2), PipesRegistry.FLUID_PIPE.defaultBlockState());
		helper.setBlock(new BlockPos(3, 1, 3), PipesRegistry.FLUID_PIPE.defaultBlockState());
		helper.setBlock(new BlockPos(4, 1, 2), PipesRegistry.MECHANICAL_PUMP.defaultBlockState().setValue(PumpBlock.FACING, Direction.EAST));
		helper.succeedWhen(() -> {
			BlockState first = helper.getBlockState(new BlockPos(2, 1, 2));
			BlockState corner = helper.getBlockState(new BlockPos(3, 1, 2));
			helper.assertTrue(FluidPipeBlock.isOpenAt(first, Direction.EAST) && FluidPipeBlock.isOpenAt(first, Direction.WEST),
				"end pipe should be a straight piece along x: " + first);
			helper.assertTrue(FluidPipeBlock.isOpenAt(corner, Direction.WEST) && FluidPipeBlock.isOpenAt(corner, Direction.SOUTH)
				&& FluidPipeBlock.isOpenAt(corner, Direction.EAST), "middle pipe should open to west, south and the pump: " + corner);
			helper.assertTrue(!FluidPipeBlock.isOpenAt(corner, Direction.NORTH) && !FluidPipeBlock.isOpenAt(corner, Direction.UP), "no other connections");
		});
	}

	@GameTest(maxTicks = 300)
	public void pumpMovesWaterFromCauldronToOpenEnd(GameTestHelper helper) {
		BlockPos end = pumpLine(helper, 3);
		helper.succeedWhen(() -> {
			helper.assertTrue(Math.abs(pumpSpeed(helper, new BlockPos(2, 1, 1))) > 0, "pump is not turning");
			helper.assertBlockPresent(Blocks.CAULDRON, new BlockPos(1, 1, 1));
			BlockState placed = helper.getBlockState(end);
			helper.assertTrue(placed.getFluidState().is(Fluids.WATER) && placed.getFluidState().isSource(),
				"the open end should have placed a water source, found " + placed);
		});
	}

	@GameTest(maxTicks = 200)
	public void nothingFlowsWithoutAPump(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(1, 1, 1), waterCauldron());
		pipe(helper, new BlockPos(2, 1, 1), Axis.X);
		pipe(helper, new BlockPos(3, 1, 1), Axis.X);
		helper.runAfterDelay(150, () -> {
			helper.assertBlockPresent(Blocks.WATER_CAULDRON, new BlockPos(1, 1, 1));
			helper.assertBlockPresent(Blocks.AIR, new BlockPos(4, 1, 1));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 400)
	public void closedValveBlocksTheFlowUntilItIsTurnedOpen(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(1, 1, 1), waterCauldron());
		pump(helper, new BlockPos(2, 1, 1), Direction.EAST, 256);
		BlockPos valve = new BlockPos(3, 1, 1);
		// facing up with axis_along_first=false: pipe along x, shaft along z
		helper.setBlock(valve, PipesRegistry.FLUID_VALVE.defaultBlockState()
			.setValue(DirectionalKineticBlock.FACING, Direction.UP)
			.setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, false));
		pipe(helper, new BlockPos(4, 1, 1), Axis.X);
		BlockPos end = new BlockPos(5, 1, 1);
		helper.runAfterDelay(2, () -> helper.assertTrue(FluidValveBlock.getPipeAxis(helper.getBlockState(valve)) == Axis.X
			&& PipesRegistry.FLUID_VALVE.getRotationAxis(helper.getBlockState(valve)) == Axis.Z, "valve axes"));
		helper.runAfterDelay(100, () -> {
			helper.assertBlockPresent(Blocks.WATER_CAULDRON, new BlockPos(1, 1, 1));
			helper.assertBlockPresent(Blocks.AIR, end);
			helper.assertBlockProperty(valve, FluidValveBlock.ENABLED, false);
			// a motor turning the valve in the positive direction opens it
			motor(helper, new BlockPos(3, 1, 0), Direction.SOUTH, 64);
		});
		helper.runAfterDelay(110, () -> helper.succeedWhen(() -> {
			helper.assertBlockProperty(valve, FluidValveBlock.ENABLED, true);
			BlockState placed = helper.getBlockState(end);
			helper.assertTrue(placed.getFluidState().is(Fluids.WATER) && placed.getFluidState().isSource(),
				"water should reach the open end once the valve is open, found " + placed);
		}));
	}

	@GameTest(maxTicks = 300)
	public void glassAndEncasedPipesCarryFluidToo(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(1, 1, 1), waterCauldron());
		pump(helper, new BlockPos(2, 1, 1), Direction.EAST, 256);
		helper.setBlock(new BlockPos(3, 1, 1), PipesRegistry.GLASS_FLUID_PIPE.defaultBlockState().setValue(GlassFluidPipeBlock.AXIS, Axis.X));
		helper.setBlock(new BlockPos(4, 1, 1), PipesRegistry.ENCASED_FLUID_PIPE.defaultBlockState()
			.setValue(EncasedPipeBlock.FACING_TO_PROPERTY_MAP.get(Direction.EAST), true)
			.setValue(EncasedPipeBlock.FACING_TO_PROPERTY_MAP.get(Direction.WEST), true));
		pipe(helper, new BlockPos(5, 1, 1), Axis.X);
		helper.succeedWhen(() -> {
			BlockState placed = helper.getBlockState(new BlockPos(6, 1, 1));
			helper.assertTrue(placed.getFluidState().is(Fluids.WATER), "water should reach the open end, found " + placed);
		});
	}

	@GameTest(maxTicks = 300)
	public void smartPipeOnlyPassesTheFilteredFluid(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(1, 1, 1), waterCauldron());
		pump(helper, new BlockPos(2, 1, 1), Direction.EAST, 256);
		BlockPos smart = new BlockPos(3, 1, 1);
		helper.setBlock(smart, PipesRegistry.SMART_FLUID_PIPE.defaultBlockState()
			.setValue(SmartFluidPipeBlock.FACE, AttachFace.FLOOR)
			.setValue(SmartFluidPipeBlock.FACING, Direction.EAST));
		pipe(helper, new BlockPos(4, 1, 1), Axis.X);
		BlockPos end = new BlockPos(5, 1, 1);
		helper.runAfterDelay(2, () -> helper.getBlockEntity(smart, SmartFluidPipeBlockEntity.class)
			.getBehaviour(FilteringBehaviour.TYPE)
			.setFilter(new ItemStack(Items.LAVA_BUCKET)));
		helper.runAfterDelay(100, () -> {
			helper.assertBlockPresent(Blocks.WATER_CAULDRON, new BlockPos(1, 1, 1));
			helper.assertBlockPresent(Blocks.AIR, end);
			helper.getBlockEntity(smart, SmartFluidPipeBlockEntity.class)
				.getBehaviour(FilteringBehaviour.TYPE)
				.setFilter(new ItemStack(Items.WATER_BUCKET));
		});
		helper.runAfterDelay(110, () -> helper.succeedWhen(() -> {
			BlockState placed = helper.getBlockState(end);
			helper.assertTrue(placed.getFluidState().is(Fluids.WATER), "water should pass the water filter, found " + placed);
		}));
	}

	@GameTest(maxTicks = 300)
	public void waterMeetingLavaAtAnOpenEndMakesObsidian(GameTestHelper helper) {
		BlockPos end = pumpLine(helper, 2);
		helper.setBlock(end, Blocks.LAVA);
		helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.OBSIDIAN, end));
	}

	@GameTest(maxTicks = 20)
	public void fluidReactionResults(GameTestHelper helper) {
		helper.assertTrue(FluidReactions.getFlowCollisionResult(Fluids.WATER, Fluids.LAVA).is(Blocks.COBBLESTONE), "pipes crossing water and lava");
		helper.assertTrue(FluidReactions.getSpillCollisionResult(Fluids.WATER, Fluids.LAVA).is(Blocks.OBSIDIAN), "water on lava source");
		helper.assertTrue(FluidReactions.getSpillCollisionResult(Fluids.WATER, Fluids.FLOWING_LAVA).is(Blocks.COBBLESTONE), "water on flowing lava");
		helper.assertTrue(FluidReactions.getSpillCollisionResult(Fluids.LAVA, Fluids.WATER).is(Blocks.STONE), "lava on water source");
		helper.assertTrue(FluidReactions.getSpillCollisionResult(Fluids.LAVA, Fluids.FLOWING_WATER).is(Blocks.COBBLESTONE), "lava on flowing water");
		helper.assertTrue(FluidReactions.getSpillCollisionResult(Fluids.WATER, Fluids.WATER) == null, "water on water");
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void cauldronsAreDrainedByOpenPipeEnds(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, waterCauldron());
		helper.setBlock(new BlockPos(4, 1, 2), Blocks.LAVA_CAULDRON);
		BlockState state = helper.getBlockState(pos);
		helper.assertTrue(VanillaFluidTargets.canProvideFluidWithoutCapability(state), "cauldron is a source");
		FluidStack simulated = VanillaFluidTargets.drainBlock(helper.getLevel(), helper.absolutePos(pos), state, true);
		helper.assertTrue(simulated.is(Fluids.WATER) && simulated.getAmount() == 1000, "water: " + simulated);
		helper.assertBlockPresent(Blocks.WATER_CAULDRON, pos);
		VanillaFluidTargets.drainBlock(helper.getLevel(), helper.absolutePos(pos), state, false);
		helper.assertBlockPresent(Blocks.CAULDRON, pos);
		FluidStack lava = VanillaFluidTargets.drainBlock(helper.getLevel(), helper.absolutePos(new BlockPos(4, 1, 2)), Blocks.LAVA_CAULDRON.defaultBlockState(), false);
		helper.assertTrue(lava.is(Fluids.LAVA), "lava: " + lava);
		helper.succeed();
	}

	@GameTest(maxTicks = 300)
	public void pumpFillsAFluidHandler(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(1, 1, 1), waterCauldron());
		pump(helper, new BlockPos(2, 1, 1), Direction.EAST, 256);
		pipe(helper, new BlockPos(3, 1, 1), Axis.X);
		// the hose pulley takes fluid from the west, through its pipe side; it stores up to 1500 mB
		BlockPos pulley = new BlockPos(4, 1, 1);
		helper.setBlock(pulley, PipesRegistry.HOSE_PULLEY.defaultBlockState().setValue(HosePulleyBlock.HORIZONTAL_FACING, Direction.NORTH));
		helper.succeedWhen(() -> {
			IFluidHandler handler = FluidHandlerLookup.get(helper.getLevel(), helper.absolutePos(pulley), Direction.WEST);
			helper.assertTrue(handler != null, "hose pulley should expose a fluid handler on its pipe side");
			helper.assertTrue(FluidHandlerLookup.get(helper.getLevel(), helper.absolutePos(pulley), Direction.EAST) == null, "but not on the shaft side");
			FluidStack stored = helper.getBlockEntity(pulley, HosePulleyBlockEntity.class).getInternalTank().getFluid();
			helper.assertTrue(stored.is(Fluids.WATER) && stored.getAmount() >= 1000, "the pulley should hold the water: " + stored);
			helper.assertBlockPresent(Blocks.CAULDRON, new BlockPos(1, 1, 1));
		});
	}

	@GameTest(maxTicks = 400)
	public void hosePulleyDrainsASmallPool(GameTestHelper helper) {
		floor(helper);
		// a row of three: taking out an end block cannot be refilled by the infinite water rule
		for (int x = 2; x <= 4; x++)
			helper.setBlock(new BlockPos(x, 1, 2), Blocks.WATER);
		// pulley facing south: shaft side west (motor, turning positive so the hose is lowered), pipe side east
		BlockPos pulley = new BlockPos(3, 5, 2);
		helper.setBlock(pulley, PipesRegistry.HOSE_PULLEY.defaultBlockState().setValue(HosePulleyBlock.HORIZONTAL_FACING, Direction.SOUTH));
		motor(helper, new BlockPos(2, 5, 2), Direction.EAST, 256);
		pump(helper, new BlockPos(4, 5, 2), Direction.EAST, 256);
		pipe(helper, new BlockPos(5, 5, 2), Axis.X);
		BlockPos end = new BlockPos(6, 5, 2);
		helper.succeedWhen(() -> {
			HosePulleyBlockEntity be = helper.getBlockEntity(pulley, HosePulleyBlockEntity.class);
			helper.assertTrue(be.getInterpolatedOffset(1) >= 4, "the hose should have been lowered to the pool: " + be.getInterpolatedOffset(1));
			int sources = 0;
			for (int x = 2; x <= 4; x++)
				if (helper.getBlockState(new BlockPos(x, 1, 2)).getFluidState().isSource())
					sources++;
			// the pulley buffers up to a bucket and a half, so it can take a second source while the first is placed
			helper.assertTrue(sources < 3, "the pool should be drained, " + sources + " of 3 sources left");
			BlockState placed = helper.getBlockState(end);
			helper.assertTrue(placed.getFluidState().is(Fluids.WATER), "the drained water should be placed at the open end, found " + placed);
		});
	}

	@GameTest(maxTicks = 40)
	public void pipeNetworkSurvivesSaveAndLoad(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		pipe(helper, pos, Axis.X);
		helper.runAfterDelay(5, () -> {
			FluidTransportBehaviour pipe = transport(helper, pos);
			helper.assertTrue(pipe != null && pipe.interfaces.size() == 2, "a straight pipe has two connections");
			pipe.addPressure(Direction.EAST, true, 5);
			var be = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
			var output = net.minecraft.world.level.storage.TagValueOutput.createWithoutContext(net.minecraft.util.ProblemReporter.DISCARDING);
			be.saveCustomOnly(output);
			var input = net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,
				helper.getLevel().registryAccess(), output.buildResult());
			pipe.getConnection(Direction.EAST).wipePressure();
			be.loadCustomOnly(input);
			helper.assertTrue(transport(helper, pos).getConnection(Direction.EAST).hasPressure(), "pressure is saved");
			helper.assertTrue(FluidPropagator.getStraightPipeAxis(helper.getBlockState(pos)) == Axis.X, "axis");
			helper.succeed();
		});
	}

	private static int shownItems(GameTestHelper helper, BlockPos pos) {
		BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(pos));
		helper.assertTrue(attachment != null, "no model at " + pos);
		int shown = 0;
		for (VirtualElement element : attachment.holder().getElements())
			if (element instanceof ItemDisplayElement display && !display.getItem().isEmpty())
				shown++;
		return shown;
	}

	@GameTest(maxTicks = 80)
	public void modelsShowTheCoreAndAttachments(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(1, 1, 1), waterCauldron());
		helper.setBlock(new BlockPos(2, 1, 1), PipesRegistry.MECHANICAL_PUMP.defaultBlockState().setValue(PumpBlock.FACING, Direction.EAST));
		pipe(helper, new BlockPos(3, 1, 1), Axis.X);
		pipe(helper, new BlockPos(4, 1, 1), Axis.X);
		helper.setBlock(new BlockPos(5, 1, 1), PipesRegistry.ENCASED_FLUID_PIPE.defaultBlockState()
			.setValue(EncasedPipeBlock.FACING_TO_PROPERTY_MAP.get(Direction.EAST), true)
			.setValue(EncasedPipeBlock.FACING_TO_PROPERTY_MAP.get(Direction.WEST), true));
		helper.setBlock(new BlockPos(3, 1, 3), PipesRegistry.GLASS_FLUID_PIPE.defaultBlockState().setValue(GlassFluidPipeBlock.AXIS, Axis.Z));
		helper.setBlock(new BlockPos(3, 1, 5), PipesRegistry.FLUID_VALVE);
		helper.setBlock(new BlockPos(5, 1, 3), PipesRegistry.SMART_FLUID_PIPE.defaultBlockState()
			.setValue(SmartFluidPipeBlock.FACE, AttachFace.FLOOR).setValue(SmartFluidPipeBlock.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(5, 1, 5), PipesRegistry.HOSE_PULLEY);
		helper.runAfterDelay(10, () -> {
			// the core, an arm towards the next pipe and the connector at the pump
			helper.assertTrue(shownItems(helper, new BlockPos(3, 1, 1)) == 3, "pipe next to a pump: " + shownItems(helper, new BlockPos(3, 1, 1)));
			helper.assertTrue(shownItems(helper, new BlockPos(4, 1, 1)) == 3, "middle pipe: " + shownItems(helper, new BlockPos(4, 1, 1)));
			helper.assertTrue(shownItems(helper, new BlockPos(5, 1, 1)) == 6, "encased pipe, six sides: " + shownItems(helper, new BlockPos(5, 1, 1)));
			helper.assertTrue(shownItems(helper, new BlockPos(2, 1, 1)) == 2, "pump body and cog: " + shownItems(helper, new BlockPos(2, 1, 1)));
			helper.assertTrue(shownItems(helper, new BlockPos(3, 1, 3)) >= 1, "glass pipe");
			helper.assertTrue(shownItems(helper, new BlockPos(3, 1, 5)) == 3, "valve body, shaft and pointer: " + shownItems(helper, new BlockPos(3, 1, 5)));
			helper.assertTrue(shownItems(helper, new BlockPos(5, 1, 3)) >= 1, "smart pipe");
			helper.assertTrue(shownItems(helper, new BlockPos(5, 1, 5)) == 4, "hose pulley body, shaft, coil and magnet: " + shownItems(helper, new BlockPos(5, 1, 5)));
			// breaking a pipe removes its attachments elsewhere only after the neighbour looked again
			helper.setBlock(new BlockPos(3, 1, 1), Blocks.AIR);
		});
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(shownItems(helper, new BlockPos(4, 1, 1)) == 3, "middle pipe after: " + shownItems(helper, new BlockPos(4, 1, 1)));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 400)
	public void hosePulleyModelShowsTheHose(GameTestHelper helper) {
		floor(helper);
		for (int x = 2; x <= 4; x++)
			for (int z = 1; z <= 3; z++)
				helper.setBlock(new BlockPos(x, 1, z), Blocks.WATER);
		BlockPos pulley = new BlockPos(3, 5, 2);
		helper.setBlock(pulley, PipesRegistry.HOSE_PULLEY.defaultBlockState().setValue(HosePulleyBlock.HORIZONTAL_FACING, Direction.SOUTH));
		motor(helper, new BlockPos(2, 5, 2), Direction.EAST, 256);
		helper.succeedWhen(() -> {
			// body, shaft, coil, magnet and four pieces of hose
			int shown = shownItems(helper, pulley);
			helper.assertTrue(shown == 8, "hose pulley model shows " + shown + " parts");
		});
	}

	@GameTest(maxTicks = 20)
	public void displayModelsExist(GameTestHelper helper) {
		java.util.List<String> models = new java.util.ArrayList<>(java.util.List.of("encased_fluid_pipe/block_flat", "encased_fluid_pipe/block_open",
			"fluid_pipe/window", "mechanical_pump/cog", "fluid_valve/pointer", "hose_pulley/hose_coil", "hose_pulley/rope",
			"hose_pulley/pulley_magnet", "hose_pulley/rope_half_magnet", "smart_fluid_pipe/block", "shaft"));
		for (int bits = 1; bits < 64; bits++)
			models.add("fluid_pipe/polymer_core/" + String.format("%6s", Integer.toBinaryString(bits)).replace(' ', '0'));
		for (Direction d : Direction.values())
			for (var type : FluidTransportBehaviour.AttachmentTypes.values())
				if (type != FluidTransportBehaviour.AttachmentTypes.NONE)
					models.add(dev.createpolymer.fluids.pipes.PipeAttachments.model(type, d));
		for (String model : models)
			helper.assertTrue(PipesGameTests.class.getResource("/assets/create/models/block/" + model + ".json") != null, "missing model " + model);
		for (String file : java.util.List.of("mechanical_pump", "smart_fluid_pipe", "fluid_valve", "hose_pulley", "glass_fluid_pipe"))
			helper.assertTrue(PipesGameTests.class.getResource("/create_polymer/blockstates/" + file + ".json") != null, "missing blockstate " + file);
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void breakingAPipeCutsTheFlow(GameTestHelper helper) {
		BlockPos end = pumpLine(helper, 4);
		BlockPos last = end.west();
		BlockPos middle = new BlockPos(5, 1, 1);
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(transport(helper, last).hasAnyPressure(), "the pump should pressurise the whole line");
			helper.setBlock(middle, Blocks.AIR);
		});
		helper.runAfterDelay(100, () -> helper.succeedWhen(() -> {
			helper.assertTrue(!transport(helper, last).hasAnyPressure(), "the pipe behind the gap should have lost its pressure");
			helper.assertTrue(transport(helper, new BlockPos(3, 1, 1)).hasAnyPressure(), "the pipe at the pump keeps its pressure");
		}));
	}

	@GameTest(maxTicks = 100)
	public void pipeFlowSurvivesSaveAndLoad(GameTestHelper helper) {
		BlockPos end = pumpLine(helper, 2);
		BlockPos last = end.west();
		helper.succeedWhen(() -> {
			FluidTransportBehaviour pipe = transport(helper, last);
			helper.assertTrue(pipe != null && pipe.getFlow(Direction.EAST) != null && !pipe.getFlow(Direction.EAST).fluid.isEmpty(),
				"water should be flowing towards the open end");
			var be = helper.getLevel().getBlockEntity(helper.absolutePos(last));
			var output = net.minecraft.world.level.storage.TagValueOutput.createWithoutContext(net.minecraft.util.ProblemReporter.DISCARDING);
			be.saveCustomOnly(output);
			pipe.interfaces.clear();
			var input = net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,
				helper.getLevel().registryAccess(), output.buildResult());
			be.loadCustomOnly(input);
			var flow = transport(helper, last).getFlow(Direction.EAST);
			helper.assertTrue(flow != null && flow.fluid.is(Fluids.WATER) && !flow.inbound, "the outward flow is restored: " + flow);
		});
	}

	@GameTest(maxTicks = 40)
	public void wrenchTurnsAPipeIntoGlassAndBack(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		pipe(helper, pos, Axis.X);
		pipe(helper, pos.east(), Axis.X);
		pipe(helper, pos.west(), Axis.X);
		var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(dev.createpolymer.registry.ModItems.WRENCH));
		helper.runAfterDelay(2, () -> {
			wrench(helper, player, pos);
			helper.assertBlockPresent(PipesRegistry.GLASS_FLUID_PIPE, pos);
			helper.assertBlockProperty(pos, GlassFluidPipeBlock.AXIS, Axis.X);
			wrench(helper, player, pos);
			helper.assertBlockPresent(PipesRegistry.FLUID_PIPE, pos);
			helper.assertTrue(FluidPropagator.getStraightPipeAxis(helper.getBlockState(pos)) == Axis.X, "back to a straight pipe");
			helper.succeed();
		});
	}

	private static void wrench(GameTestHelper helper, net.minecraft.world.entity.player.Player player, BlockPos pos) {
		var hit = new net.minecraft.world.phys.BlockHitResult(helper.absoluteVec(net.minecraft.world.phys.Vec3.atCenterOf(pos)).add(0, .5, 0), Direction.UP, helper.absolutePos(pos), false);
		var result = player.getMainHandItem().useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
		helper.assertTrue(result.consumesAction(), "the wrench did nothing: " + result);
	}

	@GameTest(maxTicks = 300)
	public void waterAndLavaMeetingInAPipeMakeCobblestone(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(1, 1, 1), waterCauldron());
		pump(helper, new BlockPos(2, 1, 1), Direction.EAST, 256);
		pipe(helper, new BlockPos(3, 1, 1), Axis.X);
		// the junction connects west, south and east on its own
		BlockPos junction = new BlockPos(4, 1, 1);
		helper.setBlock(junction, PipesRegistry.FLUID_PIPE.defaultBlockState());
		pipe(helper, new BlockPos(5, 1, 1), Axis.X);
		pipe(helper, new BlockPos(4, 1, 2), Axis.Z);
		pump(helper, new BlockPos(4, 1, 3), Direction.NORTH, 256);
		helper.setBlock(new BlockPos(4, 1, 4), Blocks.LAVA_CAULDRON);
		helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.COBBLESTONE, junction));
	}

	@GameTest(maxTicks = 20)
	public void everyBlockStateHasADisplayVariant(GameTestHelper helper) {
		java.util.Map<String, net.minecraft.world.level.block.Block> blocks = java.util.Map.of("mechanical_pump", PipesRegistry.MECHANICAL_PUMP,
			"smart_fluid_pipe", PipesRegistry.SMART_FLUID_PIPE, "fluid_valve", PipesRegistry.FLUID_VALVE,
			"hose_pulley", PipesRegistry.HOSE_PULLEY, "glass_fluid_pipe", PipesRegistry.GLASS_FLUID_PIPE);
		blocks.forEach((file, block) -> {
			for (BlockState state : block.getStateDefinition().getPossibleStates())
				helper.assertTrue(dev.createpolymer.visual.BlockstateModels.get(file, state) != null, "no model in " + file + " for " + state);
		});
		helper.succeed();
	}
}
