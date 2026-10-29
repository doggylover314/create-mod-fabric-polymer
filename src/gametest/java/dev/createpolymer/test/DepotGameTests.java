package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import dev.createpolymer.foundation.item.ItemHelper;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.kinetics.belt.behaviour.BeltProcessingBehaviour;
import dev.createpolymer.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult;
import dev.createpolymer.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import dev.createpolymer.kinetics.belt.transport.TransportedItemStack;
import dev.createpolymer.kinetics.press.MechanicalPressBlockEntity;
import dev.createpolymer.kinetics.press.PressingBehaviour;
import dev.createpolymer.logistics.depot.DepotBlockEntity;
import dev.createpolymer.registry.ModBlocks;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class DepotGameTests {

	private static IItemHandler handlerAt(GameTestHelper helper, BlockPos pos, Direction side) {
		return ItemHandlerLookup.get(helper.getLevel(), helper.absolutePos(pos), side);
	}

	@GameTest(maxTicks = 40)
	public void itemHandlerInsertAndExtract(GameTestHelper helper) {
		floor(helper);
		BlockPos depotPos = new BlockPos(2, 1, 2);
		helper.setBlock(depotPos, ModBlocks.DEPOT);
		helper.runAfterDelay(2, () -> {
			IItemHandler handler = handlerAt(helper, depotPos, Direction.UP);
			helper.assertTrue(handler != null && handler.getSlots() == 9, "no depot item handler on the top face");

			ItemStack remainder = handler.insertItem(0, new ItemStack(Items.DIAMOND, 3), false);
			helper.assertTrue(remainder.isEmpty(), "diamonds were not accepted: " + remainder);
			DepotBlockEntity depot = helper.getBlockEntity(depotPos, DepotBlockEntity.class);
			helper.assertTrue(depot.getHeldItem().is(Items.DIAMOND) && depot.getHeldItem().getCount() == 3,
				"depot holds " + depot.getHeldItem());

			// a depot only holds one stack
			ItemStack second = handler.insertItem(0, new ItemStack(Items.EMERALD), false);
			helper.assertTrue(second.is(Items.EMERALD), "a second stack was accepted");

			ItemStack extracted = handler.extractItem(0, 2, false);
			helper.assertTrue(extracted.is(Items.DIAMOND) && extracted.getCount() == 2, "extracted " + extracted);
			helper.assertTrue(depot.getHeldItem().getCount() == 1, "depot holds " + depot.getHeldItem());
			helper.assertTrue(handler.extractItem(0, 5, false).getCount() == 1, "could not take the last diamond");
			helper.assertTrue(depot.getHeldItem().isEmpty(), "depot is not empty");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void simulatedInsertionChangesNothing(GameTestHelper helper) {
		floor(helper);
		BlockPos depotPos = new BlockPos(2, 1, 2);
		helper.setBlock(depotPos, ModBlocks.DEPOT);
		helper.runAfterDelay(2, () -> {
			IItemHandler handler = handlerAt(helper, depotPos, Direction.UP);
			ItemStack remainder = handler.insertItem(0, new ItemStack(Items.DIAMOND, 3), true);
			helper.assertTrue(remainder.isEmpty(), "simulation refused the diamonds");
			helper.assertTrue(helper.getBlockEntity(depotPos, DepotBlockEntity.class).getHeldItem().isEmpty(), "simulation inserted");
			DirectBeltInputBehaviour input = helper.getBlockEntity(depotPos, DepotBlockEntity.class).getBehaviour(DirectBeltInputBehaviour.TYPE);
			helper.assertTrue(input != null && !input.isOccupied(Direction.UP), "depot looks occupied");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void directBeltInputFillsDepot(GameTestHelper helper) {
		floor(helper);
		BlockPos depotPos = new BlockPos(2, 1, 2);
		helper.setBlock(depotPos, ModBlocks.DEPOT);
		helper.runAfterDelay(2, () -> {
			DepotBlockEntity depot = helper.getBlockEntity(depotPos, DepotBlockEntity.class);
			DirectBeltInputBehaviour input = depot.getBehaviour(DirectBeltInputBehaviour.TYPE);
			ItemStack remainder = input.handleInsertion(new TransportedItemStack(new ItemStack(Items.GOLD_INGOT, 4)), Direction.EAST, false);
			helper.assertTrue(remainder.isEmpty(), "belt input refused the ingots");
			helper.assertTrue(depot.getHeldItem().getCount() == 4, "depot holds " + depot.getHeldItem());
			helper.assertTrue(input.isOccupied(Direction.EAST), "occupied depot accepted more");
			ItemStack again = input.handleInsertion(new ItemStack(Items.GOLD_INGOT), Direction.EAST, false);
			helper.assertTrue(again.getCount() == 1, "an occupied depot accepted another item");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void itemEntityLandsOnDepot(GameTestHelper helper) {
		floor(helper);
		BlockPos depotPos = new BlockPos(2, 1, 2);
		helper.setBlock(depotPos, ModBlocks.DEPOT);
		helper.runAfterDelay(2, () -> helper.spawnItem(Items.IRON_INGOT, 2.5f, 3f, 2.5f));
		helper.succeedWhen(() -> {
			ItemStack held = helper.getBlockEntity(depotPos, DepotBlockEntity.class).getHeldItem();
			helper.assertTrue(held.is(Items.IRON_INGOT), "depot holds " + held);
		});
	}

	@GameTest(maxTicks = 60)
	public void playerPutsDownAndTakesBack(GameTestHelper helper) {
		floor(helper);
		BlockPos depotPos = new BlockPos(2, 1, 2);
		helper.setBlock(depotPos, ModBlocks.DEPOT);
		helper.runAfterDelay(2, () -> {
			var player = helper.makeMockPlayer(GameType.SURVIVAL);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COPPER_INGOT, 5));
			BlockPos abs = helper.absolutePos(depotPos);
			BlockHitResult top = new BlockHitResult(Vec3.atCenterOf(abs).add(0, .4, 0), Direction.UP, abs, false);
			helper.useBlock(depotPos, player, top);

			DepotBlockEntity depot = helper.getBlockEntity(depotPos, DepotBlockEntity.class);
			helper.assertTrue(depot.getHeldItem().is(Items.COPPER_INGOT) && depot.getHeldItem().getCount() == 5,
				"depot holds " + depot.getHeldItem());
			helper.assertTrue(player.getMainHandItem().isEmpty(), "the item stayed in the hand");

			// an empty hand takes it back
			helper.useBlock(depotPos, player, top);
			helper.assertTrue(depot.getHeldItem().isEmpty(), "depot still holds " + depot.getHeldItem());
			helper.assertTrue(player.getInventory().contains(new ItemStack(Items.COPPER_INGOT)), "the player did not get the ingots back");

			// the sides do nothing
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COPPER_INGOT, 5));
			helper.useBlock(depotPos, player, new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false));
			helper.assertTrue(depot.getHeldItem().isEmpty(), "an item was put down from the side");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void hopperBelowPullsFromDepot(GameTestHelper helper) {
		floor(helper);
		BlockPos hopperPos = new BlockPos(2, 1, 2);
		BlockPos depotPos = new BlockPos(2, 2, 2);
		helper.setBlock(hopperPos, Blocks.HOPPER);
		helper.setBlock(depotPos, ModBlocks.DEPOT);
		helper.runAfterDelay(2, () -> helper.getBlockEntity(depotPos, DepotBlockEntity.class)
			.depotBehaviour.itemHandler.insertItem(0, new ItemStack(Items.DIAMOND, 2), false));
		helper.succeedWhen(() -> {
			HopperBlockEntity hopper = helper.getBlockEntity(hopperPos, HopperBlockEntity.class);
			int found = 0;
			for (int i = 0; i < hopper.getContainerSize(); i++)
				if (hopper.getItem(i).is(Items.DIAMOND))
					found += hopper.getItem(i).getCount();
			helper.assertTrue(found > 0, "hopper pulled nothing from the depot");
		});
	}

	@GameTest(maxTicks = 100)
	public void hopperAbovePushesIntoDepot(GameTestHelper helper) {
		floor(helper);
		BlockPos depotPos = new BlockPos(2, 1, 2);
		BlockPos hopperPos = new BlockPos(2, 2, 2);
		helper.setBlock(depotPos, ModBlocks.DEPOT);
		helper.setBlock(hopperPos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
		helper.runAfterDelay(2, () -> helper.getBlockEntity(hopperPos, HopperBlockEntity.class).setItem(0, new ItemStack(Items.EMERALD, 3)));
		helper.succeedWhen(() -> {
			ItemStack held = helper.getBlockEntity(depotPos, DepotBlockEntity.class).getHeldItem();
			helper.assertTrue(held.is(Items.EMERALD), "depot holds " + held);
		});
	}

	@GameTest(maxTicks = 40)
	public void transportedItemStackSerializes(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		TransportedItemStack held = new TransportedItemStack(new ItemStack(Items.GOLD_INGOT, 7));
		held.beltPosition = .25f;
		held.sideOffset = .1f;
		held.angle = 123;
		held.locked = true;
		held.insertedFrom = Direction.EAST;

		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		held.write(out);
		TransportedItemStack read = TransportedItemStack.read(TagValueInput.create(ProblemReporter.DISCARDING, registries, out.buildResult()));
		helper.assertTrue(read.stack.is(Items.GOLD_INGOT) && read.stack.getCount() == 7, "stack was lost: " + read.stack);
		helper.assertTrue(read.beltPosition == .25f && read.sideOffset == .1f && read.angle == 123 && read.locked
			&& !read.lockedExternally && read.insertedFrom == Direction.EAST, "transport data was lost");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void contentsSurviveSaving(GameTestHelper helper) {
		floor(helper);
		BlockPos depotPos = new BlockPos(2, 1, 2);
		helper.setBlock(depotPos, ModBlocks.DEPOT);
		helper.runAfterDelay(2, () -> {
			DepotBlockEntity depot = helper.getBlockEntity(depotPos, DepotBlockEntity.class);
			depot.depotBehaviour.itemHandler.insertItem(0, new ItemStack(Items.GOLD_INGOT, 7), false);

			var registries = helper.getLevel().registryAccess();
			CompoundTag saved = depot.saveWithFullMetadata(registries);
			BlockEntity loaded = BlockEntity.loadStatic(depot.getBlockPos(), depot.getBlockState(), saved, registries);
			helper.assertTrue(loaded instanceof DepotBlockEntity, "block entity did not load");
			ItemStack held = ((DepotBlockEntity) loaded).getHeldItem();
			helper.assertTrue(held.is(Items.GOLD_INGOT) && held.getCount() == 7, "held stack was lost: " + held);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void itemHelperExtractsFromDepot(GameTestHelper helper) {
		floor(helper);
		BlockPos depotPos = new BlockPos(2, 1, 2);
		helper.setBlock(depotPos, ModBlocks.DEPOT);
		helper.runAfterDelay(2, () -> {
			IItemHandler handler = handlerAt(helper, depotPos, Direction.UP);
			handler.insertItem(0, new ItemStack(Items.DIAMOND, 5), false);
			helper.assertTrue(ItemHelper.extract(handler, ItemHelper.sameItemPredicate(new ItemStack(Items.EMERALD)), true).isEmpty(),
				"extracted an item that is not there");
			helper.assertTrue(ItemHelper.extract(handler, s -> s.is(Items.DIAMOND), 6, true).isEmpty(),
				"extracted more than the depot holds");
			ItemStack two = ItemHelper.extract(handler, s -> s.is(Items.DIAMOND), 2, false);
			helper.assertTrue(two.getCount() == 2, "extracted " + two);
			ItemStack rest = ItemHelper.extract(handler, s -> true, false);
			helper.assertTrue(rest.is(Items.DIAMOND) && rest.getCount() == 3, "extracted " + rest);
			helper.assertTrue(helper.getBlockEntity(depotPos, DepotBlockEntity.class).getHeldItem().isEmpty(), "depot is not empty");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void comparatorReadsFill(GameTestHelper helper) {
		floor(helper);
		BlockPos depotPos = new BlockPos(2, 1, 2);
		helper.setBlock(depotPos, ModBlocks.DEPOT);
		helper.runAfterDelay(2, () -> {
			var level = helper.getLevel();
			BlockPos abs = helper.absolutePos(depotPos);
			int empty = level.getBlockState(abs).getAnalogOutputSignal(level, abs, Direction.UP);
			helper.getBlockEntity(depotPos, DepotBlockEntity.class).depotBehaviour.itemHandler.insertItem(0, new ItemStack(Items.IRON_INGOT, 32), false);
			int half = level.getBlockState(abs).getAnalogOutputSignal(level, abs, Direction.UP);
			helper.assertTrue(empty == 0 && half > 5 && half < 10, "comparator signals " + empty + " and " + half);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void beltProcessingBehaviourHoldsAndRemoves(GameTestHelper helper) {
		floor(helper);
		BlockPos depotPos = new BlockPos(2, 1, 2);
		BlockPos processorPos = new BlockPos(2, 3, 2);
		helper.setBlock(depotPos, ModBlocks.DEPOT);
		// the depot asks the BeltProcessingBehaviour two blocks above it; an unpowered press has one, we set its callback
		helper.setBlock(processorPos, ModBlocks.MECHANICAL_PRESS);
		helper.runAfterDelay(2, () -> {
			DepotBlockEntity depot = helper.getBlockEntity(depotPos, DepotBlockEntity.class);
			BeltProcessingBehaviour processing = helper.getBlockEntity(processorPos, MechanicalPressBlockEntity.class)
				.getBehaviour(BeltProcessingBehaviour.TYPE);
			helper.assertTrue(processing instanceof PressingBehaviour, "the press has no belt processing behaviour");
			boolean[] removeAll = { false };
			processing.whenItemEnters((stack, handler) -> removeAll[0] ? ProcessingResult.REMOVE : ProcessingResult.HOLD);
			depot.depotBehaviour.itemHandler.insertItem(0, new ItemStack(Items.IRON_NUGGET, 3), false);
			helper.runAfterDelay(10, () -> {
				helper.assertTrue(depot.getHeldItem().getCount() == 3, "held item was not kept: " + depot.getHeldItem());
				removeAll[0] = true;
			});
			helper.runAfterDelay(30, () -> {
				helper.assertTrue(depot.getHeldItem().isEmpty(), "the processor did not remove the item: " + depot.getHeldItem());
				helper.succeed();
			});
		});
	}

	private static int shownItems(GameTestHelper helper, BlockPos pos) {
		BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(pos));
		helper.assertTrue(attachment != null, "the depot has no model");
		int shown = 0;
		for (VirtualElement element : attachment.holder().getElements())
			if (element instanceof ItemDisplayElement display && !display.getItem().isEmpty())
				shown++;
		return shown;
	}

	@GameTest(maxTicks = 60)
	public void modelShowsHeldAndOutputItems(GameTestHelper helper) {
		floor(helper);
		BlockPos depotPos = new BlockPos(2, 1, 2);
		helper.setBlock(depotPos, ModBlocks.DEPOT);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(shownItems(helper, depotPos) == 1, "an empty depot shows " + shownItems(helper, depotPos) + " items");
			handlerAt(helper, depotPos, Direction.UP).insertItem(0, new ItemStack(Items.IRON_INGOT, 20), false);
			helper.getBlockEntity(depotPos, DepotBlockEntity.class).depotBehaviour.itemHandler.setStackInSlot(3, new ItemStack(Items.GOLD_INGOT));
		});
		helper.runAfterDelay(8, () -> {
			// the model, the held stack (a stack of 20 is drawn in log2(20) / 2 + 1 = 3 layers) and one output item
			helper.assertTrue(shownItems(helper, depotPos) == 5, "the depot shows " + shownItems(helper, depotPos) + " items");
			handlerAt(helper, depotPos, Direction.UP).extractItem(0, 64, false);
		});
		helper.runAfterDelay(14, () -> {
			helper.assertTrue(shownItems(helper, depotPos) == 2, "after taking the stack the depot shows " + shownItems(helper, depotPos) + " items");
			helper.succeed();
		});
	}
}
