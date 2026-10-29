package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import java.util.List;

import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.belt.transport.TransportedItemStack;
import dev.createpolymer.kinetics.fan.AirCurrent;
import dev.createpolymer.kinetics.fan.EncasedFanBlockEntity;
import dev.createpolymer.kinetics.fan.FanConfig;
import dev.createpolymer.kinetics.fan.NozzleBlock;
import dev.createpolymer.kinetics.fan.processing.AllFanProcessingTypes;
import dev.createpolymer.kinetics.fan.processing.FanProcessingType;
import dev.createpolymer.kinetics.fan.processing.FanProcessingTypeRegistry;
import dev.createpolymer.kinetics.motor.CreativeMotorBlockEntity;
import dev.createpolymer.logistics.depot.DepotBlockEntity;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.feature.FanRegistry;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

public class FanGameTests {

	private static final BlockPos MOTOR = new BlockPos(0, 1, 1);
	private static final BlockPos FAN = new BlockPos(1, 1, 1);
	private static final BlockPos CATALYST = new BlockPos(2, 1, 1);
	private static final BlockPos BARS = new BlockPos(3, 1, 1);

	/** A fan blowing east from (1,1,1), driven by a creative motor; optionally inside a corridor ending in a wall at x = 6. */
	private static void fan(GameTestHelper helper, boolean corridor) {
		floor(helper);
		if (corridor) {
			for (int x = 2; x <= 6; x++) {
				helper.setBlock(new BlockPos(x, 1, 0), Blocks.STONE);
				helper.setBlock(new BlockPos(x, 1, 2), Blocks.STONE);
			}
			helper.setBlock(new BlockPos(6, 1, 1), Blocks.STONE);
		}
		helper.setBlock(MOTOR, ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
		helper.setBlock(FAN, FanRegistry.ENCASED_FAN.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
	}

	/**
	 * The catalyst sits right in front of the fan. Iron bars behind it keep fluids from flowing on to the items,
	 * while the air flow passes through them (create:fan_transparent).
	 */
	private static void catalyst(GameTestHelper helper, BlockState state) {
		helper.setBlock(CATALYST, state);
		helper.setBlock(BARS, Blocks.IRON_BARS);
	}

	private static void quickProcessing() {
		FanConfig.fanProcessingTime = 5;
	}

	private static boolean hasItem(GameTestHelper helper, net.minecraft.world.item.Item item) {
		for (ItemEntity entity : helper.getEntities(net.minecraft.world.entity.EntityTypes.ITEM, new BlockPos(4, 1, 1), 4))
			if (entity.getItem().is(item))
				return true;
		return false;
	}

	@GameTest(maxTicks = 80)
	public void fanPushesItemEntity(GameTestHelper helper) {
		fan(helper, false);
		ItemEntity item = helper.spawnItem(Items.DIAMOND, 3.5f, 1.1f, 1.5f);
		double startX = item.getX();
		helper.succeedWhen(() -> helper.assertTrue(item.isAlive() && item.getX() > startX + 1,
			"the item only moved from " + startX + " to " + item.getX()));
	}

	@GameTest(maxTicks = 100)
	public void reversedFanPullsItemEntity(GameTestHelper helper) {
		fan(helper, false);
		helper.runAfterDelay(2, () -> helper.getBlockEntity(MOTOR, CreativeMotorBlockEntity.class).setSetting(-16));
		ItemEntity item = helper.spawnItem(Items.DIAMOND, 4.5f, 1.1f, 1.5f);
		double startX = item.getX();
		helper.succeedWhen(() -> helper.assertTrue(item.isAlive() && item.getX() < startX - 1,
			"the item only moved from " + startX + " to " + item.getX()));
	}

	@GameTest(maxTicks = 80)
	public void fanPushesPlayer(GameTestHelper helper) {
		fan(helper, false);
		@SuppressWarnings("removal")
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.snapTo(helper.absoluteVec(new Vec3(3.5, 1, 1.5)));
		helper.succeedWhen(() -> {
			helper.assertTrue(player.getDeltaMovement().x > .004, "the player was not pushed: " + player.getDeltaMovement());
			player.connection.disconnect(net.minecraft.network.chat.Component.literal("test over"));
			helper.getLevel().getServer().getPlayerList().remove(player);
		});
	}

	@GameTest(maxTicks = 200)
	public void splashingTurnsIceIntoPackedIce(GameTestHelper helper) {
		quickProcessing();
		fan(helper, true);
		catalyst(helper, Blocks.WATER.defaultBlockState());
		helper.spawnItem(Items.ICE, 5.5f, 1.1f, 1.5f);
		helper.succeedWhen(() -> {
			helper.assertTrue(hasItem(helper, Items.PACKED_ICE), "no packed ice yet");
			helper.assertTrue(!hasItem(helper, Items.ICE), "the ice was not used up");
		});
	}

	@GameTest(maxTicks = 200)
	public void blastingSmeltsRawIronOverLava(GameTestHelper helper) {
		quickProcessing();
		fan(helper, true);
		catalyst(helper, Blocks.LAVA.defaultBlockState());
		helper.spawnItem(Items.RAW_IRON, 5.5f, 1.1f, 1.5f);
		helper.succeedWhen(() -> {
			helper.assertTrue(hasItem(helper, Items.IRON_INGOT), "no iron ingot yet");
			helper.assertTrue(!hasItem(helper, Items.RAW_IRON), "the raw iron was not used up");
		});
	}

	@GameTest(maxTicks = 200)
	public void smokingCooksBeefOverFire(GameTestHelper helper) {
		quickProcessing();
		fan(helper, true);
		helper.setBlock(new BlockPos(2, 0, 1), Blocks.NETHERRACK);
		helper.setBlock(CATALYST, Blocks.FIRE);
		helper.spawnItem(Items.BEEF, 5.5f, 1.1f, 1.5f);
		helper.succeedWhen(() -> {
			helper.assertTrue(hasItem(helper, Items.COOKED_BEEF), "no steak yet");
			helper.assertTrue(!hasItem(helper, Items.BEEF), "the raw beef was not used up");
		});
	}

	@GameTest(maxTicks = 200)
	public void hauntingTurnsSandIntoSoulSand(GameTestHelper helper) {
		quickProcessing();
		fan(helper, true);
		helper.setBlock(new BlockPos(2, 0, 1), Blocks.SOUL_SAND);
		helper.setBlock(CATALYST, Blocks.SOUL_FIRE);
		helper.spawnItem(Items.SAND, 5.5f, 1.1f, 1.5f);
		helper.succeedWhen(() -> {
			helper.assertTrue(hasItem(helper, Items.SOUL_SAND), "no soul sand yet");
			helper.assertTrue(!hasItem(helper, Items.SAND), "the sand was not used up");
		});
	}

	@GameTest(maxTicks = 200)
	public void airFlowWithoutCatalystProcessesNothing(GameTestHelper helper) {
		quickProcessing();
		fan(helper, true);
		helper.spawnItem(Items.ICE, 5.5f, 1.1f, 1.5f);
		helper.runAfterDelay(120, () -> {
			helper.assertTrue(hasItem(helper, Items.ICE), "the ice disappeared");
			helper.assertTrue(!hasItem(helper, Items.PACKED_ICE), "the ice was processed without water");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void splashingProcessesItemOnDepot(GameTestHelper helper) {
		quickProcessing();
		fan(helper, true);
		catalyst(helper, Blocks.WATER.defaultBlockState());
		BlockPos depotPos = new BlockPos(4, 0, 1);
		helper.setBlock(depotPos, ModBlocks.DEPOT);
		helper.runAfterDelay(2, () -> ItemHandlerLookup.get(helper.getLevel(), helper.absolutePos(depotPos), Direction.UP)
			.insertItem(0, new ItemStack(Items.ICE, 3), false));
		helper.succeedWhen(() -> {
			ItemStack held = helper.getBlockEntity(depotPos, DepotBlockEntity.class).getHeldItem();
			helper.assertTrue(held.is(Items.PACKED_ICE) && held.getCount() == 3, "the depot holds " + held);
		});
	}

	@GameTest(maxTicks = 100)
	public void airCurrentStopsAtWallAndHasSegments(GameTestHelper helper) {
		fan(helper, true);
		catalyst(helper, Blocks.WATER.defaultBlockState());
		helper.succeedWhen(() -> {
			AirCurrent current = helper.getBlockEntity(FAN, EncasedFanBlockEntity.class).getAirCurrent();
			helper.assertTrue(current.pushing && current.direction == Direction.EAST, "wrong direction " + current.direction);
			helper.assertTrue(current.maxDistance == 4, "the air current is " + current.maxDistance + " long, expected 4");
			helper.assertTrue(current.getTypeAt(2) == AllFanProcessingTypes.SPLASHING, "no splashing after the water");
			helper.assertTrue(current.getTypeAt(0) == AllFanProcessingTypes.SPLASHING, "no splashing in the water");
			helper.assertTrue(current.getTypeAt(5) == null, "air flow continues past its end");
		});
	}

	@GameTest(maxTicks = 100)
	public void nozzleOnlySurvivesOnItsFan(GameTestHelper helper) {
		fan(helper, false);
		BlockPos nozzle = new BlockPos(2, 1, 1);
		BlockPos stray = new BlockPos(4, 1, 4);
		BlockState state = FanRegistry.NOZZLE.defaultBlockState().setValue(NozzleBlock.FACING, Direction.EAST);
		helper.setBlock(nozzle, state);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(state.canSurvive(helper.getLevel(), helper.absolutePos(nozzle)), "the nozzle cannot stay on its fan");
			helper.assertTrue(!state.canSurvive(helper.getLevel(), helper.absolutePos(stray)), "a nozzle can stay without a fan");
			helper.assertTrue(!state.setValue(NozzleBlock.FACING, Direction.UP).canSurvive(helper.getLevel(), helper.absolutePos(nozzle)),
				"a nozzle can stay on the side of a fan");
			// taking the fan away drops the nozzle
			helper.setBlock(FAN, Blocks.AIR);
			helper.runAfterDelay(2, () -> {
				helper.assertBlockNotPresent(FanRegistry.NOZZLE, nozzle);
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = 100)
	public void nozzlePushesItemEntityAway(GameTestHelper helper) {
		fan(helper, false);
		BlockPos nozzle = new BlockPos(2, 1, 1);
		helper.setBlock(nozzle, FanRegistry.NOZZLE.defaultBlockState().setValue(NozzleBlock.FACING, Direction.EAST));
		ItemEntity item = helper.spawnItem(Items.DIAMOND, 3.5f, 1.1f, 1.5f);
		double startX = item.getX();
		helper.succeedWhen(() -> helper.assertTrue(item.isAlive() && item.getX() > startX + 1,
			"the item only moved from " + startX + " to " + item.getX()));
	}

	@GameTest(maxTicks = 20)
	public void processingTypesAndRecipesAreRegistered(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(FanProcessingTypeRegistry.SORTED_TYPES_VIEW.size() == 4, "expected four fan processing types");
		helper.assertTrue(FanProcessingTypeRegistry.SORTED_TYPES_VIEW.getFirst() == AllFanProcessingTypes.SPLASHING, "types are not sorted by priority");
		helper.assertTrue(FanProcessingType.parse("create:haunting") == AllFanProcessingTypes.HAUNTING, "cannot parse create:haunting");
		helper.assertTrue(AllFanProcessingTypes.parseLegacy("BLASTING") == AllFanProcessingTypes.BLASTING, "cannot parse legacy name");
		helper.assertTrue(AllFanProcessingTypes.SPLASHING.canProcess(new ItemStack(Items.ICE), level), "no splashing recipe for ice");
		helper.assertTrue(AllFanProcessingTypes.HAUNTING.canProcess(new ItemStack(Items.SAND), level), "no haunting recipe for sand");
		helper.assertTrue(AllFanProcessingTypes.SMOKING.canProcess(new ItemStack(Items.BEEF), level), "no smoking recipe for beef");
		helper.assertTrue(AllFanProcessingTypes.BLASTING.canProcess(new ItemStack(Items.RAW_IRON), level), "cannot blast raw iron");
		helper.assertTrue(!AllFanProcessingTypes.SPLASHING.canProcess(new ItemStack(Items.DIAMOND), level), "splashing accepts diamonds");
		List<ItemStack> hauntedSand = AllFanProcessingTypes.HAUNTING.process(new ItemStack(Items.SAND, 5), level);
		helper.assertTrue(hauntedSand != null && hauntedSand.size() == 1 && hauntedSand.getFirst().is(Items.SOUL_SAND) && hauntedSand.getFirst().getCount() == 5,
			"haunting sand gave " + hauntedSand);
		helper.assertTrue(level.recipeAccess().getRecipeFor(FanRegistry.SPLASHING, new SingleRecipeInput(new ItemStack(Items.GRAVEL)), level).isPresent(),
			"no splashing recipe for gravel");
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void catalystTagsAndTransparentBlocks(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		BlockPos water = new BlockPos(1, 1, 1);
		BlockPos lava = new BlockPos(5, 1, 1);
		BlockPos litCampfire = new BlockPos(1, 1, 5);
		BlockPos unlitCampfire = new BlockPos(3, 1, 5);
		BlockPos soulCampfire = new BlockPos(5, 1, 5);
		helper.setBlock(water, Blocks.WATER);
		helper.setBlock(lava, Blocks.LAVA);
		helper.setBlock(litCampfire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
		helper.setBlock(unlitCampfire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
		helper.setBlock(soulCampfire, Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
		helper.assertTrue(FanProcessingType.getAt(level, helper.absolutePos(water)) == AllFanProcessingTypes.SPLASHING, "water is not splashing");
		helper.assertTrue(FanProcessingType.getAt(level, helper.absolutePos(lava)) == AllFanProcessingTypes.BLASTING, "lava is not blasting");
		helper.assertTrue(FanProcessingType.getAt(level, helper.absolutePos(litCampfire)) == AllFanProcessingTypes.SMOKING, "a lit campfire is not smoking");
		helper.assertTrue(FanProcessingType.getAt(level, helper.absolutePos(unlitCampfire)) == null, "an unlit campfire counts");
		helper.assertTrue(FanProcessingType.getAt(level, helper.absolutePos(soulCampfire)) == AllFanProcessingTypes.HAUNTING, "a soul campfire is not haunting");
		helper.assertTrue(Blocks.IRON_BARS.defaultBlockState().is(AirCurrent.FAN_TRANSPARENT), "iron bars are not fan transparent");
		helper.assertTrue(Blocks.OAK_LEAVES.defaultBlockState().is(AirCurrent.FAN_TRANSPARENT), "leaves are not fan transparent");
		helper.assertTrue(!Blocks.STONE.defaultBlockState().is(AirCurrent.FAN_TRANSPARENT), "stone is fan transparent");
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void transportedItemStackKeepsFanProcessingData(GameTestHelper helper) {
		TransportedItemStack stack = new TransportedItemStack(new ItemStack(Items.ICE, 2));
		stack.processedBy = AllFanProcessingTypes.SPLASHING;
		stack.processingTime = 17;
		TransportedItemStack copy = stack.copy();
		helper.assertTrue(copy.processedBy == AllFanProcessingTypes.SPLASHING && copy.processingTime == 17, "copy lost the fan data");

		try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(() -> "fan test", dev.createpolymer.CreatePolymer.LOGGER)) {
			TagValueOutput out = TagValueOutput.createWithContext(reporter, helper.getLevel().registryAccess());
			stack.write(out);
			TransportedItemStack read = TransportedItemStack.read(TagValueInput.create(reporter, helper.getLevel().registryAccess(), out.buildResult()));
			helper.assertTrue(read.processedBy == AllFanProcessingTypes.SPLASHING && read.processingTime == 17,
				"saving lost the fan data: " + read.processedBy + " " + read.processingTime);
			read.clearFanProcessingData();
			helper.assertTrue(read.processedBy == null && read.processingTime == 0, "clearFanProcessingData did nothing");
		}
		helper.succeed();
	}
}
