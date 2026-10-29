package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.assertSpeed;
import static dev.createpolymer.test.TestUtil.floor;

import dev.createpolymer.decoration.steamWhistle.WhistleBlock;
import dev.createpolymer.decoration.steamWhistle.WhistleBlock.WhistleSize;
import dev.createpolymer.decoration.steamWhistle.WhistleBlockEntity;
import dev.createpolymer.decoration.steamWhistle.WhistleExtenderBlock;
import dev.createpolymer.decoration.steamWhistle.WhistleExtenderBlock.WhistleExtenderShape;
import dev.createpolymer.fluids.tank.FluidTankBlockEntity;
import dev.createpolymer.foundation.fluid.FluidHandlerLookup;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.IFluidHandler.FluidAction;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.base.RotatedPillarKineticBlock;
import dev.createpolymer.kinetics.steamEngine.PoweredShaftBlockEntity;
import dev.createpolymer.kinetics.steamEngine.SteamEngineBlock;
import dev.createpolymer.kinetics.steamEngine.SteamEngineBlockEntity;
import dev.createpolymer.processing.burner.BlazeBurnerBlock;
import dev.createpolymer.processing.burner.BlazeBurnerBlock.HeatLevel;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.feature.BurnerRegistry;
import dev.createpolymer.registry.feature.SteamRegistry;
import dev.createpolymer.registry.feature.TanksRegistry;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.material.Fluids;

public class SteamGameTests {

	private static final int WATER_TICKS = 150;

	/** A lit blaze burner (heat level KINDLED, fed with planks as Create does). */
	private static void burner(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, BurnerRegistry.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, HeatLevel.SMOULDERING));
		BlazeBurnerBlock.tryInsert(helper.getBlockState(pos), helper.getLevel(), helper.absolutePos(pos), new ItemStack(Items.OAK_PLANKS),
			false, true, false);
	}

	/** Pours water into the boiler at the tank every tick, 20 mB a tick is enough for two heat levels. */
	private static void feedWater(GameTestHelper helper, BlockPos tank, int amount) {
		for (int t = 1; t < WATER_TICKS; t++)
			helper.runAtTickTime(t, () -> {
				var handler = FluidHandlerLookup.get(helper.getLevel(), helper.absolutePos(tank), null);
				if (handler != null)
					handler.fill(new FluidStack(Fluids.WATER, amount), FluidAction.EXECUTE);
			});
	}

	private static void tank(GameTestHelper helper, BlockPos from, int width, int height) {
		for (int y = 0; y < height; y++)
			for (int x = 0; x < width; x++)
				for (int z = 0; z < width; z++)
					helper.setBlock(from.offset(x, y, z), TanksRegistry.FLUID_TANK);
	}

	/** An engine on the east side of the tank block at {@code attachedTo}, with a vertical shaft two blocks further. */
	private static void engineEast(GameTestHelper helper, BlockPos engine) {
		helper.setBlock(SteamEngineBlock.getShaftPos(engineState(Direction.EAST), engine), ModBlocks.SHAFT.defaultBlockState()
			.setValue(RotatedPillarKineticBlock.AXIS, Axis.Y));
		helper.setBlock(engine, engineState(Direction.EAST));
	}

	private static BlockState engineState(Direction facing) {
		return SteamRegistry.STEAM_ENGINE.defaultBlockState()
			.setValue(SteamEngineBlock.FACE, AttachFace.WALL)
			.setValue(SteamEngineBlock.FACING, facing);
	}

	private static PoweredShaftBlockEntity powered(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockEntity(pos, PoweredShaftBlockEntity.class);
	}

	// ---- steam engine ----

	@GameTest(maxTicks = 200)
	public void engineOnBoilerTurnsItsShaft(GameTestHelper helper) {
		floor(helper);
		BlockPos tank = new BlockPos(2, 2, 2);
		BlockPos engine = new BlockPos(4, 2, 2);
		BlockPos shaft = new BlockPos(6, 2, 2);
		BlockPos above = new BlockPos(6, 3, 2);
		burner(helper, new BlockPos(2, 1, 2));
		tank(helper, tank, 2, 1);
		engineEast(helper, engine);
		helper.setBlock(above, ModBlocks.SHAFT.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.Y));
		feedWater(helper, tank, 20);
		helper.runAfterDelay(1, () -> helper.assertBlockPresent(SteamRegistry.POWERED_SHAFT, shaft));
		helper.succeedWhen(() -> {
			assertSpeed(helper, shaft, 64);
			assertSpeed(helper, above, 64);
			helper.assertTrue(powered(helper, shaft).engineEfficiency == 1, "efficiency " + powered(helper, shaft).engineEfficiency);
			// a powered shaft is a source of stress capacity: 1024 SU per RPM of the engine, spread over 4 speed levels
			float capacity = helper.getBlockEntity(above, KineticBlockEntity.class).getNetworkCapacity();
			helper.assertTrue(Math.abs(capacity - 256 * 64) < 1, "network capacity " + capacity);
		});
	}

	@GameTest(maxTicks = 200)
	public void engineWithoutWaterDoesNotTurn(GameTestHelper helper) {
		floor(helper);
		burner(helper, new BlockPos(2, 1, 2));
		tank(helper, new BlockPos(2, 2, 2), 2, 1);
		BlockPos engine = new BlockPos(4, 2, 2);
		BlockPos shaft = new BlockPos(6, 2, 2);
		engineEast(helper, engine);
		helper.runAfterDelay(120, () -> {
			assertSpeed(helper, shaft, 0);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void moreHeatGivesMoreCapacity(GameTestHelper helper) {
		floor(helper);
		// a 2x2x2 boiler (heat level 2 by size) on two burners, with two engines
		burner(helper, new BlockPos(2, 1, 2));
		burner(helper, new BlockPos(3, 1, 3));
		tank(helper, new BlockPos(2, 2, 2), 2, 2);
		BlockPos engineA = new BlockPos(4, 2, 2);
		BlockPos engineB = new BlockPos(4, 3, 3);
		engineEast(helper, engineA);
		engineEast(helper, engineB);
		feedWater(helper, new BlockPos(2, 2, 2), 30);
		helper.succeedWhen(() -> {
			BlockPos a = new BlockPos(6, 2, 2);
			BlockPos b = new BlockPos(6, 3, 3);
			// two heat levels for two engines: both at full power
			assertSpeed(helper, a, 64);
			assertSpeed(helper, b, 64);
			helper.assertTrue(helper.getBlockEntity(new BlockPos(2, 2, 2), FluidTankBlockEntity.class).boiler.attachedEngines == 2,
				"engines " + helper.getBlockEntity(new BlockPos(2, 2, 2), FluidTankBlockEntity.class).boiler.attachedEngines);
		});
	}

	@GameTest(maxTicks = 200)
	public void lessHeatThanEnginesSharesThePower(GameTestHelper helper) {
		floor(helper);
		// the same boiler on one burner: one heat level for two engines, each gets half the efficiency
		burner(helper, new BlockPos(2, 1, 2));
		tank(helper, new BlockPos(2, 2, 2), 2, 2);
		engineEast(helper, new BlockPos(4, 2, 2));
		engineEast(helper, new BlockPos(4, 3, 3));
		feedWater(helper, new BlockPos(2, 2, 2), 30);
		helper.succeedWhen(() -> {
			PoweredShaftBlockEntity a = powered(helper, new BlockPos(6, 2, 2));
			helper.assertTrue(Math.abs(a.engineEfficiency - .5f) < 1e-3, "efficiency " + a.engineEfficiency);
			// efficiency .5: speed modifier 1 + floor(.5 * 4) = 3 times 16 RPM, capacity 512 / 3 per RPM
			assertSpeed(helper, new BlockPos(6, 2, 2), 48);
			assertSpeed(helper, new BlockPos(6, 3, 3), 48);
			float capacity = a.calculateAddedStressCapacity();
			helper.assertTrue(Math.abs(capacity - 512f / 3) < .01, "capacity per RPM " + capacity);
		});
	}

	@GameTest(maxTicks = 200)
	public void engineStopsWhenTheBurnerGoesOut(GameTestHelper helper) {
		floor(helper);
		BlockPos burner = new BlockPos(2, 1, 2);
		burner(helper, burner);
		tank(helper, new BlockPos(2, 2, 2), 2, 1);
		engineEast(helper, new BlockPos(4, 2, 2));
		feedWater(helper, new BlockPos(2, 2, 2), 20);
		helper.runAtTickTime(80, () -> {
			assertSpeed(helper, new BlockPos(6, 2, 2), 64);
			helper.setBlock(burner, Blocks.AIR);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 85, "waiting");
			assertSpeed(helper, new BlockPos(6, 2, 2), 0);
		});
	}

	@GameTest(maxTicks = 100)
	public void poweredShaftReturnsToAShaftWhenTheEngineIsRemoved(GameTestHelper helper) {
		floor(helper);
		burner(helper, new BlockPos(2, 1, 2));
		tank(helper, new BlockPos(2, 2, 2), 2, 1);
		BlockPos engine = new BlockPos(4, 2, 2);
		BlockPos shaft = new BlockPos(6, 2, 2);
		engineEast(helper, engine);
		helper.runAfterDelay(2, () -> {
			helper.assertBlockPresent(SteamRegistry.POWERED_SHAFT, shaft);
			helper.setBlock(engine, Blocks.AIR);
		});
		helper.succeedWhenBlockPresent(ModBlocks.SHAFT, shaft);
	}

	@GameTest(maxTicks = 100)
	public void engineModelAnimatesWithTheShaft(GameTestHelper helper) {
		floor(helper);
		burner(helper, new BlockPos(2, 1, 2));
		tank(helper, new BlockPos(2, 2, 2), 2, 1);
		BlockPos engine = new BlockPos(4, 2, 2);
		engineEast(helper, engine);
		feedWater(helper, new BlockPos(2, 2, 2), 20);
		helper.succeedWhen(() -> {
			assertSpeed(helper, new BlockPos(6, 2, 2), 64);
			BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(engine));
			helper.assertTrue(attachment != null, "the engine has no model");
			// block, piston, linkage and connector; the moving parts are shown once the shaft turns
			int shown = 0;
			for (VirtualElement element : attachment.holder().getElements())
				if (element instanceof ItemDisplayElement display && display.getScale().x() > .5f)
					shown++;
			helper.assertTrue(shown == 4, "shown parts: " + shown);
			SteamEngineBlockEntity be = helper.getBlockEntity(engine, SteamEngineBlockEntity.class);
			helper.assertTrue(be.getTargetAngle() != null, "no target angle");
		});
	}

	// ---- steam whistle ----

	private static BlockState whistle(boolean powered, WhistleSize size) {
		return SteamRegistry.STEAM_WHISTLE.defaultBlockState()
			.setValue(WhistleBlock.POWERED, powered)
			.setValue(WhistleBlock.SIZE, size);
	}

	@GameTest(maxTicks = 200)
	public void whistleBlowsOnAHeatedBoilerWhenPowered(GameTestHelper helper) {
		floor(helper);
		burner(helper, new BlockPos(2, 1, 2));
		tank(helper, new BlockPos(2, 2, 2), 2, 1);
		BlockPos whistle = new BlockPos(2, 3, 2);
		helper.setBlock(whistle, whistle(false, WhistleSize.SMALL));
		helper.runAfterDelay(5, () -> {
			WhistleBlockEntity be = helper.getBlockEntity(whistle, WhistleBlockEntity.class);
			helper.assertTrue(be.getTank() != null && be.getTank().boiler.attachedWhistles == 1, "the boiler does not count the whistle");
			helper.assertTrue(!be.isPlayingSound() && be.animation.getChaseTarget() == 0, "an unpowered whistle blows");
			// a redstone block next to it powers the whistle
			helper.setBlock(new BlockPos(3, 3, 2), Blocks.REDSTONE_BLOCK);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(whistle).getValue(WhistleBlock.POWERED), "the whistle is not powered");
			WhistleBlockEntity be = helper.getBlockEntity(whistle, WhistleBlockEntity.class);
			helper.assertTrue(be.animation.getChaseTarget() == 1 && be.isPlayingSound(), "the whistle does not blow");
		});
	}

	@GameTest(maxTicks = 100)
	public void whistleStaysSilentOnAColdBoiler(GameTestHelper helper) {
		floor(helper);
		tank(helper, new BlockPos(2, 2, 2), 2, 1);
		BlockPos whistle = new BlockPos(2, 3, 2);
		helper.setBlock(whistle, whistle(true, WhistleSize.LARGE));
		helper.runAfterDelay(40, () -> {
			WhistleBlockEntity be = helper.getBlockEntity(whistle, WhistleBlockEntity.class);
			helper.assertTrue(be.getTank().boiler.isActive(), "the whistle does not make the tank a boiler");
			helper.assertTrue(!be.isPlayingSound() && be.animation.getChaseTarget() == 0, "a whistle on a cold boiler blows");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void whistleStopsWhenUnpowered(GameTestHelper helper) {
		floor(helper);
		burner(helper, new BlockPos(2, 1, 2));
		tank(helper, new BlockPos(2, 2, 2), 2, 1);
		BlockPos whistle = new BlockPos(2, 3, 2);
		helper.setBlock(whistle, whistle(true, WhistleSize.MEDIUM));
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(helper.getBlockEntity(whistle, WhistleBlockEntity.class).isPlayingSound(), "the whistle does not blow");
			helper.setBlock(whistle, whistle(false, WhistleSize.MEDIUM));
		});
		helper.succeedWhen(() -> {
			WhistleBlockEntity be = helper.getBlockEntity(whistle, WhistleBlockEntity.class);
			helper.assertTrue(helper.getTick() > 25 && !be.isPlayingSound() && be.animation.getChaseTarget() == 0, "the whistle still blows");
		});
	}

	@GameTest(maxTicks = 100)
	public void extendersLowerThePitchAndStack(GameTestHelper helper) {
		floor(helper);
		burner(helper, new BlockPos(2, 1, 2));
		tank(helper, new BlockPos(2, 2, 2), 2, 1);
		BlockPos whistle = new BlockPos(2, 3, 2);
		helper.setBlock(whistle, whistle(false, WhistleSize.LARGE));
		helper.runAfterDelay(2, () -> {
			WhistleBlock.incrementSize(helper.getLevel(), helper.absolutePos(whistle));
		});
		helper.runAfterDelay(6, () -> {
			BlockState ext = helper.getBlockState(whistle.above());
			helper.assertTrue(ext.is(SteamRegistry.STEAM_WHISTLE_EXTENSION) && ext.getValue(WhistleExtenderBlock.SHAPE) == WhistleExtenderShape.SINGLE
				&& ext.getValue(WhistleExtenderBlock.SIZE) == WhistleSize.LARGE, "extension: " + ext);
			helper.assertTrue(helper.getBlockEntity(whistle, WhistleBlockEntity.class).getPitch() == 1, "pitch " + helper.getBlockEntity(whistle, WhistleBlockEntity.class).getPitch());
			WhistleBlock.incrementSize(helper.getLevel(), helper.absolutePos(whistle));
		});
		helper.runAfterDelay(10, () -> {
			BlockState ext = helper.getBlockState(whistle.above());
			helper.assertTrue(ext.getValue(WhistleExtenderBlock.SHAPE) == WhistleExtenderShape.DOUBLE, "extension: " + ext);
			helper.assertTrue(helper.getBlockEntity(whistle, WhistleBlockEntity.class).getPitch() == 2, "pitch " + helper.getBlockEntity(whistle, WhistleBlockEntity.class).getPitch());
			WhistleBlock.incrementSize(helper.getLevel(), helper.absolutePos(whistle));
		});
		helper.runAfterDelay(14, () -> {
			helper.assertBlockPresent(SteamRegistry.STEAM_WHISTLE_EXTENSION, whistle.above(2));
			helper.assertTrue(helper.getBlockState(whistle.above()).getValue(WhistleExtenderBlock.SHAPE) == WhistleExtenderShape.DOUBLE_CONNECTED,
				"extension: " + helper.getBlockState(whistle.above()));
			helper.assertTrue(helper.getBlockEntity(whistle, WhistleBlockEntity.class).getPitch() == 3, "pitch " + helper.getBlockEntity(whistle, WhistleBlockEntity.class).getPitch());
			// removing the whistle's base takes the extension with it
			helper.setBlock(whistle, Blocks.AIR);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 18, "waiting");
			helper.assertBlockNotPresent(SteamRegistry.STEAM_WHISTLE_EXTENSION, whistle.above());
		});
	}

	@GameTest(maxTicks = 60)
	public void whistleOnlyStaysOnATank(GameTestHelper helper) {
		floor(helper);
		BlockPos whistle = new BlockPos(2, 1, 2);
		helper.setBlock(whistle, whistle(false, WhistleSize.MEDIUM));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(!whistle(false, WhistleSize.MEDIUM).canSurvive(helper.getLevel(), helper.absolutePos(whistle)), "a whistle survives on stone");
			helper.setBlock(new BlockPos(2, 1, 2), TanksRegistry.FLUID_TANK);
			helper.setBlock(new BlockPos(2, 2, 2), whistle(false, WhistleSize.MEDIUM));
		});
		helper.runAfterDelay(6, () -> {
			helper.assertBlockPresent(SteamRegistry.STEAM_WHISTLE, new BlockPos(2, 2, 2));
			BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)));
			helper.assertTrue(attachment != null, "the whistle has no model");
			helper.succeed();
		});
	}
}
