package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.motor.CreativeMotorBlockEntity;
import dev.createpolymer.kinetics.speedController.SpeedControllerBlock;
import dev.createpolymer.kinetics.speedController.SpeedControllerBlockEntity;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.feature.MiscRegistry;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class MiscGameTests {

	// ---- rotation speed controller ----

	/** Creative motor facing east into a speed controller (axis X), with a large cogwheel on top of the controller. */
	private static final BlockPos MOTOR = new BlockPos(1, 1, 2);
	private static final BlockPos CONTROLLER = new BlockPos(2, 1, 2);
	private static final BlockPos COG = new BlockPos(2, 2, 2);

	@GameTest(maxTicks = 160)
	public void speedControllerDrivesLargeCogAtTargetSpeed(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(MOTOR, ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
		helper.setBlock(CONTROLLER, MiscRegistry.ROTATION_SPEED_CONTROLLER.defaultBlockState().setValue(SpeedControllerBlock.HORIZONTAL_AXIS, Axis.X));
		helper.setBlock(COG, ModBlocks.LARGE_COGWHEEL.defaultBlockState().setValue(BlockStateProperties.AXIS, Axis.Z));
		helper.runAfterDelay(2, () -> {
			helper.getBlockEntity(MOTOR, CreativeMotorBlockEntity.class).setSetting(32);
			helper.getBlockEntity(CONTROLLER, SpeedControllerBlockEntity.class).targetSpeed.setValue(64);
		});
		helper.succeedWhen(() -> {
			float speed = helper.getBlockEntity(COG, KineticBlockEntity.class).getSpeed();
			helper.assertTrue(Math.abs(speed - 64) < 1e-3, "the large cogwheel turns at " + speed + ", expected 64");
		});
	}

	// ---- creative crate ----

	private static final BlockPos CRATE = new BlockPos(1, 1, 1);

	@GameTest(maxTicks = 20)
	public void creativeCrateGivesEndlessFilterItem(GameTestHelper helper) {
		helper.setBlock(CRATE, MiscRegistry.CREATIVE_CRATE.defaultBlockState());
		BlockPos abs = helper.absolutePos(CRATE);
		BlockEntityBehaviour.get(helper.getLevel(), abs, FilteringBehaviour.TYPE).setFilter(new ItemStack(Items.STONE));

		IItemHandler handler = ItemHandlerLookup.get(helper.getLevel(), abs, null);
		helper.assertTrue(handler != null, "the creative crate has no item handler");
		for (int i = 0; i < 3; i++) {
			ItemStack taken = handler.extractItem(0, 64, false);
			helper.assertTrue(taken.is(Items.STONE) && taken.getCount() == 64, "extraction " + i + " gave " + taken);
		}
		ItemStack refused = handler.insertItem(0, new ItemStack(Items.DIRT, 5), false);
		helper.assertTrue(refused.isEmpty(), "the crate did not void the inserted items: " + refused);
		helper.succeed();
	}

	// ---- item hatch ----

	/** Item hatch facing east, a chest on its east side. */
	private static final BlockPos HATCH = new BlockPos(2, 1, 2);
	private static final BlockPos CHEST = new BlockPos(3, 1, 2);

	private static void hatchAndChest(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(HATCH, MiscRegistry.ITEM_HATCH.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
		helper.setBlock(CHEST, Blocks.CHEST);
	}

	private static int countInChest(GameTestHelper helper, net.minecraft.world.item.Item item) {
		IItemHandler chest = ItemHandlerLookup.get(helper.getLevel(), helper.absolutePos(CHEST), null);
		int total = 0;
		for (int slot = 0; slot < chest.getSlots(); slot++) {
			ItemStack stack = chest.getStackInSlot(slot);
			if (stack.is(item))
				total += stack.getCount();
		}
		return total;
	}

	@GameTest(maxTicks = 20)
	public void itemHatchDepositsMatchingItemsIntoChest(GameTestHelper helper) {
		hatchAndChest(helper);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.getInventory().setItem(player.getInventory().getSelectedSlot(), new ItemStack(Items.STONE, 10));

		helper.useBlock(HATCH, player);

		helper.assertTrue(countInChest(helper, Items.STONE) == 10, "the chest holds " + countInChest(helper, Items.STONE) + " stone");
		helper.assertTrue(player.getMainHandItem().isEmpty(), "the player still holds " + player.getMainHandItem());
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void itemHatchFilterKeepsOtherItems(GameTestHelper helper) {
		hatchAndChest(helper);
		BlockEntityBehaviour.get(helper.getLevel(), helper.absolutePos(HATCH), FilteringBehaviour.TYPE).setFilter(new ItemStack(Items.DIRT));
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.getInventory().setItem(player.getInventory().getSelectedSlot(), new ItemStack(Items.STONE, 10));

		helper.useBlock(HATCH, player);

		helper.assertTrue(countInChest(helper, Items.STONE) == 0, "filtered-out stone reached the chest");
		helper.assertTrue(player.getMainHandItem().is(Items.STONE) && player.getMainHandItem().getCount() == 10,
			"the player lost the stone: " + player.getMainHandItem());
		helper.succeed();
	}
}
