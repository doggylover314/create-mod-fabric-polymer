package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.kinetics.base.DirectionalAxisKineticBlock;
import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import dev.createpolymer.kinetics.motor.CreativeMotorBlockEntity;
import dev.createpolymer.kinetics.saw.SawBlock;
import dev.createpolymer.kinetics.saw.SawBlockEntity;
import dev.createpolymer.logistics.depot.DepotBlockEntity;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.SawRegistry;
import dev.createpolymer.visual.BlockstateModels;
import dev.createpolymer.visual.ModelItems;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SawGameTests {

	/** An upward saw whose rotation axis is Z, so items travel along +X (or -X when it turns backwards). */
	private static BlockState upwardSaw() {
		return SawRegistry.MECHANICAL_SAW.defaultBlockState()
			.setValue(SawBlock.FACING, Direction.UP)
			.setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, false);
	}

	private static void powerFromNorth(GameTestHelper helper, BlockPos sawPos, int speed) {
		BlockPos motor = sawPos.north();
		helper.setBlock(motor, ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.SOUTH));
		helper.runAfterDelay(2, () -> helper.getBlockEntity(motor, CreativeMotorBlockEntity.class).setSetting(speed));
	}

	@GameTest(maxTicks = 400)
	public void sawCutsItemEntityWithCuttingRecipe(GameTestHelper helper) {
		floor(helper);
		BlockPos saw = new BlockPos(2, 1, 2);
		helper.setBlock(saw, upwardSaw());
		powerFromNorth(helper, saw, 128);
		// andesite alloy also has stonecutting recipes (ladders, bars, scaffolding); pick the cutting one like a player would
		helper.runAfterDelay(3, () -> helper.getBlockEntity(saw, SawBlockEntity.class).getBehaviour(FilteringBehaviour.TYPE)
			.setFilter(new ItemStack(ModItems.SHAFT)));
		helper.runAfterDelay(4, () -> helper.spawnItem(ModItems.ANDESITE_ALLOY, 2.5f, 3f, 2.5f));
		// Create's cutting recipe: one andesite alloy makes six shafts, which the saw throws out in front of it
		helper.succeedWhen(() -> helper.assertItemEntityCountIs(ModItems.SHAFT, new BlockPos(3, 1, 2), 2, 6));
	}

	@GameTest(maxTicks = 400)
	public void sawThrowsItemsAgainstRotationWhenReversed(GameTestHelper helper) {
		floor(helper);
		BlockPos saw = new BlockPos(4, 1, 2);
		helper.setBlock(saw, upwardSaw());
		powerFromNorth(helper, saw, -128);
		// andesite alloy also has stonecutting recipes (ladders, bars, scaffolding); pick the cutting one like a player would
		helper.runAfterDelay(3, () -> helper.getBlockEntity(saw, SawBlockEntity.class).getBehaviour(FilteringBehaviour.TYPE)
			.setFilter(new ItemStack(ModItems.SHAFT)));
		helper.runAfterDelay(4, () -> helper.spawnItem(ModItems.ANDESITE_ALLOY, 4.5f, 3f, 2.5f));
		helper.succeedWhen(() -> helper.assertItemEntityCountIs(ModItems.SHAFT, new BlockPos(3, 1, 2), .6, 6));
	}

	@GameTest(maxTicks = 400)
	public void sawPassesResultsOnToADepot(GameTestHelper helper) {
		floor(helper);
		BlockPos saw = new BlockPos(2, 1, 2);
		BlockPos depot = new BlockPos(3, 1, 2);
		helper.setBlock(saw, upwardSaw());
		helper.setBlock(depot, ModBlocks.DEPOT);
		powerFromNorth(helper, saw, 128);
		// andesite alloy also has stonecutting recipes (ladders, bars, scaffolding); pick the cutting one like a player would
		helper.runAfterDelay(3, () -> helper.getBlockEntity(saw, SawBlockEntity.class).getBehaviour(FilteringBehaviour.TYPE)
			.setFilter(new ItemStack(ModItems.SHAFT)));
		// what a belt does: hand the item to the saw's direct belt input
		helper.runAfterDelay(4, () -> {
			DirectBeltInputBehaviour input = helper.getBlockEntity(saw, SawBlockEntity.class).getBehaviour(DirectBeltInputBehaviour.TYPE);
			helper.assertTrue(input != null && input.canInsertFromSide(Direction.WEST), "the saw has no belt input");
			ItemStack remainder = input.handleInsertion(new ItemStack(ModItems.ANDESITE_ALLOY), Direction.WEST, false);
			helper.assertTrue(remainder.isEmpty(), "the saw did not take the item: " + remainder);
		});
		helper.succeedWhen(() -> {
			ItemStack held = helper.getBlockEntity(depot, DepotBlockEntity.class).getHeldItem();
			helper.assertTrue(held.is(ModItems.SHAFT) && held.getCount() == 6, "depot holds " + held);
		});
	}

	@GameTest(maxTicks = 300)
	public void sawPicksStonecutterRecipeByFilter(GameTestHelper helper) {
		floor(helper);
		BlockPos saw = new BlockPos(2, 1, 2);
		helper.setBlock(saw, upwardSaw());
		powerFromNorth(helper, saw, 128);
		helper.runAfterDelay(2, () -> {
			SawBlockEntity be = helper.getBlockEntity(saw, SawBlockEntity.class);
			helper.assertTrue(be.getBehaviour(FilteringBehaviour.TYPE).setFilter(new ItemStack(Items.COBBLESTONE_STAIRS)),
				"the filter was refused");
			be.getBehaviour(DirectBeltInputBehaviour.TYPE).handleInsertion(new ItemStack(Items.COBBLESTONE), Direction.WEST, false);
		});
		helper.succeedWhen(() -> {
			helper.assertItemEntityCountIs(Items.COBBLESTONE_STAIRS, new BlockPos(3, 1, 2), 2, 1);
			helper.assertItemEntityNotPresent(Items.COBBLESTONE_SLAB, new BlockPos(3, 1, 2), 3);
			helper.assertItemEntityNotPresent(Items.COBBLESTONE_WALL, new BlockPos(3, 1, 2), 3);
		});
	}

	@GameTest(maxTicks = 400)
	public void sawLeavesUnknownItemsAlone(GameTestHelper helper) {
		floor(helper);
		BlockPos saw = new BlockPos(2, 1, 2);
		helper.setBlock(saw, upwardSaw());
		powerFromNorth(helper, saw, 128);
		helper.runAfterDelay(4, () -> helper.getBlockEntity(saw, SawBlockEntity.class).getBehaviour(DirectBeltInputBehaviour.TYPE)
			.handleInsertion(new ItemStack(Items.DIAMOND), Direction.WEST, false));
		// an item without a recipe is simply passed through
		helper.succeedWhen(() -> helper.assertItemEntityCountIs(Items.DIAMOND, new BlockPos(3, 1, 2), 2, 1));
	}

	@GameTest(maxTicks = 100)
	public void horizontalSawDoesNotTakeItemEntities(GameTestHelper helper) {
		floor(helper);
		BlockPos saw = new BlockPos(2, 1, 2);
		helper.setBlock(saw, SawRegistry.MECHANICAL_SAW.defaultBlockState().setValue(SawBlock.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(1, 1, 2), ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
		helper.runAfterDelay(2, () -> helper.getBlockEntity(new BlockPos(1, 1, 2), CreativeMotorBlockEntity.class).setSetting(128));
		helper.runAfterDelay(4, () -> helper.spawnItem(ModItems.ANDESITE_ALLOY, 2.5f, 3f, 2.5f));
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(helper.getBlockEntity(saw, SawBlockEntity.class).inventory.isEmpty(), "a horizontal saw took an item");
			helper.assertItemEntityPresent(ModItems.ANDESITE_ALLOY);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 300)
	public void horizontalSawFellsTree(GameTestHelper helper) {
		floor(helper);
		BlockPos saw = new BlockPos(2, 1, 2);
		helper.setBlock(saw, SawRegistry.MECHANICAL_SAW.defaultBlockState().setValue(SawBlock.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(1, 1, 2), ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
		helper.runAfterDelay(2, () -> helper.getBlockEntity(new BlockPos(1, 1, 2), CreativeMotorBlockEntity.class).setSetting(256));

		// a four block trunk right in front of the saw, with a crown that would not decay
		for (int y = 1; y <= 4; y++)
			helper.setBlock(new BlockPos(3, y, 2), Blocks.OAK_LOG);
		BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState()
			.setValue(BlockStateProperties.PERSISTENT, true)
			.setValue(BlockStateProperties.DISTANCE, 1);
		helper.setBlock(new BlockPos(3, 5, 2), leaves);
		helper.setBlock(new BlockPos(4, 4, 2), leaves);
		helper.setBlock(new BlockPos(2, 4, 3), leaves);

		helper.succeedWhen(() -> {
			for (int y = 1; y <= 4; y++) {
				int logY = y;
				helper.assertBlock(new BlockPos(3, y, 2), b -> b == Blocks.AIR, b -> Component.literal("log at y=" + logY + " is still there"));
			}
			helper.assertBlock(new BlockPos(3, 5, 2), b -> b == Blocks.AIR, b -> Component.literal("leaves are still there"));
			helper.assertItemEntityCountIs(Items.OAK_LOG, new BlockPos(3, 3, 2), 6, 4);
		});
	}

	@GameTest(maxTicks = 100)
	public void horizontalSawLeavesStoneAlone(GameTestHelper helper) {
		floor(helper);
		BlockPos saw = new BlockPos(2, 1, 2);
		helper.setBlock(saw, SawRegistry.MECHANICAL_SAW.defaultBlockState().setValue(SawBlock.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(1, 1, 2), ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(3, 1, 2), Blocks.STONE);
		helper.runAfterDelay(2, () -> helper.getBlockEntity(new BlockPos(1, 1, 2), CreativeMotorBlockEntity.class).setSetting(256));
		helper.runAfterDelay(60, () -> {
			helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 1, 2));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void sawContentsSurviveSaving(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, upwardSaw());
		helper.runAfterDelay(2, () -> {
			SawBlockEntity saw = helper.getBlockEntity(pos, SawBlockEntity.class);
			saw.inventory.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 1));
			saw.inventory.setStackInSlot(3, new ItemStack(Items.DIAMOND, 5));
			var registries = helper.getLevel().registryAccess();
			CompoundTag saved = saw.saveWithFullMetadata(registries);
			BlockEntity loaded = BlockEntity.loadStatic(saw.getBlockPos(), saw.getBlockState(), saved, registries);
			helper.assertTrue(loaded instanceof SawBlockEntity, "block entity did not load");
			SawBlockEntity copy = (SawBlockEntity) loaded;
			helper.assertTrue(copy.inventory.getStackInSlot(0).is(Items.COBBLESTONE), "input was lost");
			helper.assertTrue(copy.inventory.getStackInSlot(3).is(Items.DIAMOND) && copy.inventory.getStackInSlot(3).getCount() == 5,
				"output was lost");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void sawHasDamageSourceAndStress(GameTestHelper helper) {
		helper.assertTrue(SawRegistry.sawDamage(helper.getLevel()).type().msgId().equals("create.mechanical_saw"), "damage type missing");
		helper.assertTrue(dev.createpolymer.kinetics.base.BlockStressValues.getImpact(SawRegistry.MECHANICAL_SAW) == 4,
			"stress impact is not 4");
		helper.assertTrue(SawBlock.getDamage(256) == 10, "damage is not capped at 10");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void everySawStateHasAModel(GameTestHelper helper) {
		for (BlockState state : SawRegistry.MECHANICAL_SAW.getStateDefinition().getPossibleStates()) {
			var variant = BlockstateModels.get("mechanical_saw", state);
			helper.assertTrue(variant != null, "no model for " + state);
			boolean vertical = state.getValue(SawBlock.FACING).getAxis().isVertical();
			helper.assertTrue(variant.model().getPath().endsWith(vertical ? "/vertical" : "/horizontal"), "wrong model for " + state);
			// the display model must build for every state
			helper.assertTrue(new SawBlock.Model(state) != null, "no display model for " + state);
		}
		helper.succeed();
	}

	private static boolean displays(GameTestHelper helper, BlockPos pos, ItemStack wanted) {
		BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(pos));
		helper.assertTrue(attachment != null, "the saw has no model");
		for (VirtualElement element : attachment.holder().getElements())
			if (element instanceof ItemDisplayElement display && ItemStack.isSameItemSameComponents(display.getItem(), wanted))
				return true;
		return false;
	}

	@GameTest(maxTicks = 80)
	public void modelShowsBladeForTheRotationDirectionAndTheItem(GameTestHelper helper) {
		floor(helper);
		BlockPos saw = new BlockPos(2, 1, 2);
		helper.setBlock(saw, upwardSaw());
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(displays(helper, saw, ModelItems.block("mechanical_saw/vertical")), "no casing");
			helper.assertTrue(displays(helper, saw, ModelItems.block("shaft")), "no shaft");
			helper.assertTrue(displays(helper, saw, ModelItems.block("mechanical_saw/blade_vertical_inactive")), "no still blade");
		});
		helper.runAfterDelay(4, () -> powerFromNorth(helper, saw, 128));
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(displays(helper, saw, ModelItems.block("mechanical_saw/blade_vertical_active")), "no spinning blade");
			helper.getBlockEntity(saw, SawBlockEntity.class).inventory.insertItem(0, new ItemStack(Items.COBBLESTONE), false);
		});
		helper.runAfterDelay(16, () -> {
			helper.assertTrue(displays(helper, saw, new ItemStack(Items.COBBLESTONE)), "the cut item is not shown");
			helper.getBlockEntity(saw, SawBlockEntity.class).getBehaviour(FilteringBehaviour.TYPE).setFilter(new ItemStack(Items.COBBLESTONE_STAIRS));
		});
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(displays(helper, saw, new ItemStack(Items.COBBLESTONE_STAIRS)), "the filter is not shown");
			helper.succeed();
		});
	}
}
