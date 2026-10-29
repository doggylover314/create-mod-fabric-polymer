package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.base.RotatedPillarKineticBlock;
import dev.createpolymer.kinetics.crusher.CrushingWheelControllerBlock;
import dev.createpolymer.kinetics.crusher.CrushingWheelControllerBlockEntity;
import dev.createpolymer.kinetics.motor.CreativeMotorBlockEntity;
import dev.createpolymer.processing.recipe.CrushingRecipe;
import dev.createpolymer.processing.recipe.ProcessingOutput;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.CrushingRegistry;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CrushingGameTests {

	private static void motor(GameTestHelper helper, BlockPos pos, Direction facing, int speed) {
		helper.setBlock(pos, ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, facing));
		helper.runAfterDelay(2, () -> helper.getBlockEntity(pos, CreativeMotorBlockEntity.class).setSetting(speed));
	}

	private static BlockState drill(Direction facing) {
		return CrushingRegistry.MECHANICAL_DRILL.defaultBlockState().setValue(DirectionalKineticBlock.FACING, facing);
	}

	private static BlockState wheel(Axis axis) {
		return CrushingRegistry.CRUSHING_WHEEL.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, axis);
	}

	/**
	 * Two wheels around the X axis with a one block gap at (2, 3, 3), driven in opposite directions
	 * (or the same direction when sameDirection is set).
	 */
	private static void crusher(GameTestHelper helper, boolean sameDirection) {
		floor(helper);
		helper.setBlock(new BlockPos(2, 3, 2), wheel(Axis.X));
		helper.setBlock(new BlockPos(2, 3, 4), wheel(Axis.X));
		motor(helper, new BlockPos(1, 3, 2), Direction.EAST, 64);
		if (sameDirection)
			motor(helper, new BlockPos(1, 3, 4), Direction.EAST, 64);
		else
			motor(helper, new BlockPos(3, 3, 4), Direction.WEST, 64);
	}

	@GameTest(maxTicks = 200)
	public void drillBreaksStoneAndDropsCobblestone(GameTestHelper helper) {
		floor(helper);
		motor(helper, new BlockPos(1, 1, 2), Direction.EAST, 128);
		helper.setBlock(new BlockPos(2, 1, 2), drill(Direction.EAST));
		BlockPos target = new BlockPos(3, 1, 2);
		helper.setBlock(target, Blocks.STONE);
		helper.succeedWhen(() -> {
			helper.assertBlockNotPresent(Blocks.STONE, target);
			helper.assertItemEntityPresent(Items.COBBLESTONE, target, 2);
		});
	}

	@GameTest(maxTicks = 120)
	public void drillLeavesUnbreakableBlocksAlone(GameTestHelper helper) {
		floor(helper);
		motor(helper, new BlockPos(1, 1, 2), Direction.EAST, 128);
		helper.setBlock(new BlockPos(2, 1, 2), drill(Direction.EAST));
		BlockPos target = new BlockPos(3, 1, 2);
		helper.setBlock(target, Blocks.BEDROCK);
		helper.runAfterDelay(100, () -> {
			helper.assertBlockPresent(Blocks.BEDROCK, target);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void drillHurtsEntitiesInFront(GameTestHelper helper) {
		floor(helper);
		motor(helper, new BlockPos(2, 1, 2), Direction.UP, 128);
		helper.setBlock(new BlockPos(2, 2, 2), drill(Direction.UP));
		// the pig stands on the casing, with its feet in the space of the drill head
		Pig pig = helper.spawn(EntityTypes.PIG, 2.5f, 2.76f, 2.5f);
		pig.setNoAi(true);
		helper.succeedWhen(() -> helper.assertTrue(!pig.isAlive() || pig.getHealth() < pig.getMaxHealth(), "pig was not hurt"));
	}

	@GameTest(maxTicks = 100)
	public void crushingRecipeLoadsWithChanceOutputs(GameTestHelper helper) {
		var recipe = helper.getLevel().recipeAccess()
			.getRecipeFor(CrushingRegistry.CRUSHING, new SingleRecipeInput(new ItemStack(Items.GRAVEL)), helper.getLevel());
		helper.assertTrue(recipe.isPresent(), "no crushing recipe for gravel");
		CrushingRecipe value = recipe.get().value();
		helper.assertTrue(value.getRollableResults().size() == 3, "gravel should have 3 outputs");
		boolean chance = false;
		for (ProcessingOutput output : value.getRollableResults())
			chance |= output.chance() < 1f;
		helper.assertTrue(chance, "chance outputs were lost");
		helper.assertTrue(value.getProcessingDuration() == 250, "processing time was not read");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void oppositeWheelsMakeAValidController(GameTestHelper helper) {
		crusher(helper, false);
		BlockPos gap = new BlockPos(2, 3, 3);
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(CrushingRegistry.CRUSHING_WHEEL_CONTROLLER, gap);
			BlockState state = helper.getBlockState(gap);
			helper.assertTrue(state.getValue(CrushingWheelControllerBlock.VALID), "controller is not valid");
			helper.assertTrue(state.getValue(CrushingWheelControllerBlock.FACING) == Direction.DOWN,
				"controller faces " + state.getValue(CrushingWheelControllerBlock.FACING));
			helper.assertTrue(helper.getBlockEntity(gap, CrushingWheelControllerBlockEntity.class).crushingspeed > 0, "no crushing speed");
		});
	}

	@GameTest(maxTicks = 100)
	public void wheelsTurningTheSameWayAreNotValid(GameTestHelper helper) {
		crusher(helper, true);
		BlockPos gap = new BlockPos(2, 3, 3);
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(CrushingRegistry.CRUSHING_WHEEL_CONTROLLER, gap);
			helper.assertFalse(helper.getBlockState(gap).getValue(CrushingWheelControllerBlock.VALID), "controller should not be valid");
		});
	}

	@GameTest(maxTicks = 100)
	public void removingAWheelRemovesTheController(GameTestHelper helper) {
		crusher(helper, false);
		BlockPos gap = new BlockPos(2, 3, 3);
		helper.runAfterDelay(20, () -> {
			helper.assertBlockPresent(CrushingRegistry.CRUSHING_WHEEL_CONTROLLER, gap);
			helper.setBlock(new BlockPos(2, 3, 2), Blocks.AIR);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 3, 2)).isAir(), "wheel is still there");
			helper.assertBlockNotPresent(CrushingRegistry.CRUSHING_WHEEL_CONTROLLER, gap);
		});
	}

	@GameTest(maxTicks = 400)
	public void crushingWheelsCrushGravel(GameTestHelper helper) {
		crusher(helper, false);
		helper.runAfterDelay(20, () -> helper.spawnItem(Items.GRAVEL, 2.5f, 4.5f, 3.5f));
		helper.succeedWhen(() -> helper.assertItemEntityPresent(Items.SAND, new BlockPos(2, 2, 3), 3));
	}

	@GameTest(maxTicks = 400)
	public void crushingWheelsFallBackToMilling(GameTestHelper helper) {
		crusher(helper, false);
		helper.runAfterDelay(20, () -> helper.spawnItem(Items.WHEAT, 2.5f, 4.5f, 3.5f));
		helper.succeedWhen(() -> helper.assertItemEntityPresent(ModItems.WHEAT_FLOUR, new BlockPos(2, 2, 3), 3));
	}

	@GameTest(maxTicks = 400)
	public void crushingWheelsKillMobs(GameTestHelper helper) {
		crusher(helper, false);
		helper.runAfterDelay(20, () -> {
			Pig pig = helper.spawn(EntityTypes.PIG, 2.5f, 4.5f, 3.5f);
			helper.succeedWhen(() -> helper.assertTrue(!pig.isAlive(), "pig is still alive"));
		});
	}

	@GameTest(maxTicks = 400)
	public void hopperFeedsTheController(GameTestHelper helper) {
		crusher(helper, false);
		BlockPos hopper = new BlockPos(2, 4, 3);
		helper.setBlock(hopper, Blocks.HOPPER);
		helper.runAfterDelay(20, () -> helper.getBlockEntity(hopper, HopperBlockEntity.class).setItem(0, new ItemStack(Items.GRAVEL)));
		helper.succeedWhen(() -> helper.assertItemEntityPresent(Items.SAND, new BlockPos(2, 2, 3), 3));
	}

	@GameTest(maxTicks = 100)
	public void controllerCollisionIsAPlatformForMobsOnly(GameTestHelper helper) {
		crusher(helper, false);
		BlockPos gap = new BlockPos(2, 3, 3);
		Pig pig = helper.spawn(EntityTypes.PIG, 2.5f, 6f, 6.5f);
		pig.setNoAi(true);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(CrushingRegistry.CRUSHING_WHEEL_CONTROLLER, gap);
			BlockState state = helper.getBlockState(gap);
			BlockPos abs = helper.absolutePos(gap);
			VoxelShape forPig = state.getCollisionShape(helper.getLevel(), abs, CollisionContext.of(pig));
			helper.assertFalse(forPig.isEmpty(), "mobs should find a platform");
			helper.assertTrue(Math.abs(forPig.max(Axis.Y) - 13 / 16.0) < 1e-6, "platform should be 13px, was " + forPig.max(Axis.Y));
			helper.assertTrue(state.getCollisionShape(helper.getLevel(), abs, CollisionContext.of(player)).isEmpty(),
				"players should fall through");
		});
	}
}
