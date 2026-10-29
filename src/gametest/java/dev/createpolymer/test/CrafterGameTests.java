package dev.createpolymer.test;

import static dev.createpolymer.kinetics.base.HorizontalKineticBlock.HORIZONTAL_FACING;
import static dev.createpolymer.test.TestUtil.floor;

import dev.createpolymer.foundation.blockEntity.behaviour.edgeInteraction.EdgeInteractionHandler;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.ItemHandlerHelper;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.base.RotatedPillarKineticBlock;
import dev.createpolymer.kinetics.crafter.ConnectedInputHandler;
import dev.createpolymer.kinetics.crafter.MechanicalCrafterBlock;
import dev.createpolymer.kinetics.crafter.MechanicalCrafterBlockEntity;
import dev.createpolymer.kinetics.crafter.MechanicalCrafterBlockEntity.Phase;
import dev.createpolymer.kinetics.motor.CreativeMotorBlockEntity;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.CrafterRegistry;
import dev.createpolymer.util.Pointing;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class CrafterGameTests {
	/** Crafters face north, so GameTestHelper.useBlock (which hits the north face) hits their front. */
	private static final Direction FACING = Direction.NORTH;
	private static final int Z = 3;

	private static BlockState crafter(Direction target) {
		for (Pointing pointing : Pointing.values()) {
			BlockState state = CrafterRegistry.MECHANICAL_CRAFTER.defaultBlockState()
				.setValue(HORIZONTAL_FACING, FACING)
				.setValue(MechanicalCrafterBlock.POINTING, pointing);
			if (MechanicalCrafterBlock.getTargetDirection(state) == target)
				return state;
		}
		throw new IllegalStateException("no pointing towards " + target);
	}

	/**
	 * A width x height grid of crafters with its lower left corner at (originX, originY, Z), all gathering into
	 * the crafter of the bottom row in column rootCol, which delivers downwards.
	 */
	private static void grid(GameTestHelper helper, int originX, int originY, int width, int height, int rootCol) {
		for (int col = 0; col < width; col++) {
			for (int row = 0; row < height; row++) {
				Direction target = Direction.DOWN;
				if (row == 0 && col < rootCol)
					target = Direction.EAST;
				if (row == 0 && col > rootCol)
					target = Direction.WEST;
				helper.setBlock(new BlockPos(originX + col, originY + row, Z), crafter(target));
			}
		}
	}

	/** A motor behind a cogwheel next to the grid; the cogwheel meshes with the crafter at (originX, originY). */
	private static void power(GameTestHelper helper, int originX, int originY, int rpm) {
		helper.setBlock(new BlockPos(originX - 1, originY, Z + 1), ModBlocks.CREATIVE_MOTOR.defaultBlockState()
			.setValue(DirectionalKineticBlock.FACING, Direction.NORTH));
		helper.setBlock(new BlockPos(originX - 1, originY, Z), ModBlocks.COGWHEEL.defaultBlockState()
			.setValue(RotatedPillarKineticBlock.AXIS, Axis.Z));
		helper.runAfterDelay(2, () -> helper.getBlockEntity(new BlockPos(originX - 1, originY, Z + 1), CreativeMotorBlockEntity.class)
			.setSetting(rpm));
	}

	private static MechanicalCrafterBlockEntity be(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockEntity(pos, MechanicalCrafterBlockEntity.class);
	}

	private static void insert(GameTestHelper helper, BlockPos pos, ItemStack stack) {
		IItemHandler handler = ItemHandlerLookup.get(helper.getLevel(), helper.absolutePos(pos), null);
		helper.assertTrue(handler != null, "no item handler at " + pos);
		ItemStack remainder = ItemHandlerHelper.insertItem(handler, stack, false);
		helper.assertTrue(remainder.isEmpty(), "crafter at " + pos + " did not take " + stack + ", left " + remainder);
	}

	private static int countIn(ChestBlockEntity chest, Item item) {
		int count = 0;
		for (int i = 0; i < chest.getContainerSize(); i++)
			if (chest.getItem(i).is(item))
				count += chest.getItem(i).getCount();
		return count;
	}

	private static int shownItems(GameTestHelper helper, BlockPos pos, Item item) {
		BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(pos));
		helper.assertTrue(attachment != null, "the crafter has no model");
		int shown = 0;
		for (VirtualElement element : attachment.holder().getElements())
			if (element instanceof ItemDisplayElement display && display.getItem().is(item))
				shown++;
		return shown;
	}

	/** Model pieces: casing, cog, arrow or belt, lid; anything else is a held item. */
	private static int shownPieces(GameTestHelper helper, BlockPos pos) {
		BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(pos));
		helper.assertTrue(attachment != null, "the crafter has no model");
		int shown = 0;
		for (VirtualElement element : attachment.holder().getElements())
			if (element instanceof ItemDisplayElement display && !display.getItem().isEmpty())
				shown++;
		return shown;
	}

	private static void assertAllIdle(GameTestHelper helper, int originX, int originY, int width, int height) {
		for (int col = 0; col < width; col++) {
			for (int row = 0; row < height; row++) {
				MechanicalCrafterBlockEntity crafter = be(helper, new BlockPos(originX + col, originY + row, Z));
				helper.assertTrue(crafter.getPhase() == Phase.IDLE, "crafter " + col + "," + row + " is " + crafter.getPhase());
				helper.assertTrue(crafter.getInventory().getItem(0).isEmpty(), "crafter " + col + "," + row + " still holds an item");
			}
		}
	}

	@GameTest(maxTicks = 400)
	public void gridCraftsChestFromPlanks(GameTestHelper helper) {
		floor(helper);
		// a ring of eight planks: the middle crafter is covered so the chain does not wait for it
		grid(helper, 1, 2, 3, 3, 1);
		BlockPos chest = new BlockPos(2, 1, Z);
		helper.setBlock(chest, Blocks.CHEST);
		power(helper, 1, 2, 128);
		BlockPos middle = new BlockPos(2, 3, Z);
		helper.runAfterDelay(10, () -> {
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CrafterRegistry.CRAFTER_SLOT_COVER));
			helper.useBlock(middle, player);
			helper.assertTrue(be(helper, middle).isCovered(), "the slot cover was not put on");

			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_PLANKS, 8));
			for (int col = 0; col < 3; col++) {
				for (int row = 0; row < 3; row++) {
					BlockPos pos = new BlockPos(1 + col, 2 + row, Z);
					if (!pos.equals(middle))
						helper.useBlock(pos, player);
				}
			}
			helper.assertTrue(player.getMainHandItem().isEmpty(), "the planks were not used up: " + player.getMainHandItem());
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(countIn(helper.getBlockEntity(chest, ChestBlockEntity.class), Items.CHEST) == 1,
				"no chest in the output yet");
			assertAllIdle(helper, 1, 2, 3, 3);
		});
	}

	@GameTest(maxTicks = 400)
	public void craftingTableRecipeNeedsOnlyTwoByTwo(GameTestHelper helper) {
		floor(helper);
		// 2x2 of planks makes a crafting table; the crafters stay a 2x2 block of the 3x3 above
		grid(helper, 1, 2, 2, 2, 0);
		BlockPos chest = new BlockPos(1, 1, Z);
		helper.setBlock(chest, Blocks.CHEST);
		power(helper, 1, 2, 256);
		helper.runAfterDelay(10, () -> {
			for (int col = 0; col < 2; col++)
				for (int row = 0; row < 2; row++)
					insert(helper, new BlockPos(1 + col, 2 + row, Z), new ItemStack(Items.OAK_PLANKS));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(countIn(helper.getBlockEntity(chest, ChestBlockEntity.class), Items.CRAFTING_TABLE) == 1,
				"no crafting table in the output yet");
			assertAllIdle(helper, 1, 2, 2, 2);
		});
	}

	@GameTest(maxTicks = 400)
	public void mechanicalCraftingRecipeBiggerThanThreeByThree(GameTestHelper helper) {
		floor(helper);
		// create_polymer_test:four_wide_emeralds is 4 wide: iron ingots over sticks
		grid(helper, 1, 2, 4, 2, 1);
		BlockPos chest = new BlockPos(2, 1, Z);
		helper.setBlock(chest, Blocks.CHEST);
		power(helper, 1, 2, 256);
		helper.runAfterDelay(10, () -> {
			for (int col = 0; col < 4; col++) {
				insert(helper, new BlockPos(1 + col, 3, Z), new ItemStack(Items.IRON_INGOT));
				insert(helper, new BlockPos(1 + col, 2, Z), new ItemStack(Items.STICK));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(countIn(helper.getBlockEntity(chest, ChestBlockEntity.class), Items.EMERALD) == 3,
				"no emeralds in the output yet");
			assertAllIdle(helper, 1, 2, 4, 2);
		});
	}

	@GameTest(maxTicks = 300)
	public void wrongPatternEjectsItems(GameTestHelper helper) {
		floor(helper);
		// swapped rows: sticks over iron matches nothing, so everything is spat out in front of the crafters
		grid(helper, 1, 2, 4, 2, 1);
		helper.setBlock(new BlockPos(2, 1, Z), Blocks.CHEST);
		power(helper, 1, 2, 256);
		helper.runAfterDelay(10, () -> {
			for (int col = 0; col < 4; col++) {
				insert(helper, new BlockPos(1 + col, 3, Z), new ItemStack(Items.STICK));
				insert(helper, new BlockPos(1 + col, 2, Z), new ItemStack(Items.IRON_INGOT));
			}
		});
		helper.succeedWhen(() -> {
			assertAllIdle(helper, 1, 2, 4, 2);
			helper.assertItemEntityPresent(Items.STICK, new BlockPos(2, 3, Z - 1), 3);
			helper.assertItemEntityPresent(Items.IRON_INGOT, new BlockPos(2, 2, Z - 1), 3);
			helper.assertTrue(countIn(helper.getBlockEntity(new BlockPos(2, 1, Z), ChestBlockEntity.class), Items.EMERALD) == 0,
				"emeralds from a wrong pattern");
		});
	}

	@GameTest(maxTicks = 100)
	public void incompleteGridWaits(GameTestHelper helper) {
		floor(helper);
		grid(helper, 1, 2, 2, 2, 0);
		helper.setBlock(new BlockPos(1, 1, Z), Blocks.CHEST);
		power(helper, 1, 2, 256);
		helper.runAfterDelay(10, () -> {
			for (int i = 0; i < 3; i++)
				insert(helper, new BlockPos(1 + (i % 2), 2 + (i / 2), Z), new ItemStack(Items.OAK_PLANKS));
		});
		helper.runAfterDelay(60, () -> {
			for (int i = 0; i < 3; i++) {
				MechanicalCrafterBlockEntity crafter = be(helper, new BlockPos(1 + (i % 2), 2 + (i / 2), Z));
				helper.assertTrue(crafter.getPhase() == Phase.IDLE, "crafter " + i + " started without its neighbour");
				helper.assertTrue(!crafter.getInventory().getItem(0).isEmpty(), "crafter " + i + " lost its item");
			}
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 120)
	public void slotCoverBlocksInsertion(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, Z);
		helper.setBlock(pos, crafter(Direction.UP));
		helper.runAfterDelay(2, () -> {
			MechanicalCrafterBlockEntity crafter = be(helper, pos);
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			int before = shownPieces(helper, pos);

			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CrafterRegistry.CRAFTER_SLOT_COVER, 2));
			helper.useBlock(pos, player);
			helper.assertTrue(crafter.isCovered(), "the cover was not put on");
			helper.assertTrue(player.getMainHandItem().getCount() == 1, "the cover was not used up");

			// a second cover does not fit, and the slot takes nothing
			helper.useBlock(pos, player);
			helper.assertTrue(player.getMainHandItem().getCount() == 1, "a second cover was used");
			IItemHandler handler = ItemHandlerLookup.get(helper.getLevel(), helper.absolutePos(pos), null);
			ItemStack remainder = handler.insertItem(0, new ItemStack(Items.OAK_PLANKS), false);
			helper.assertTrue(remainder.getCount() == 1, "a covered slot took an item");
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_PLANKS, 4));
			helper.useBlock(pos, player);
			helper.assertTrue(player.getMainHandItem().getCount() == 4, "a covered slot took an item from the hand");
			helper.assertTrue(crafter.getInventory().getItem(0).isEmpty(), "the covered slot holds an item");
			helper.assertTrue(shownPieces(helper, pos) == before, "the lid is not shown yet");

			helper.runAfterDelay(2, () -> {
				helper.assertTrue(shownPieces(helper, pos) == before + 1, "the lid is not shown: " + shownPieces(helper, pos) + " pieces, " + before + " before");

				// an empty hand takes the cover off again
				player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				helper.useBlock(pos, player);
				helper.assertTrue(!crafter.isCovered(), "the cover is still on");
				helper.assertTrue(player.getInventory().contains(new ItemStack(CrafterRegistry.CRAFTER_SLOT_COVER)), "the cover did not come back");

				// and the slot works again
				ItemStack taken = handler.insertItem(0, new ItemStack(Items.OAK_PLANKS), false);
				helper.assertTrue(taken.isEmpty(), "the open slot refused an item");
				helper.runAfterDelay(2, () -> {
					helper.assertTrue(shownItems(helper, pos, Items.OAK_PLANKS) == 1, "the held item is not shown");
					helper.succeed();
				});
			});
		});
	}

	@GameTest(maxTicks = 100)
	public void playerPutsItemInFrontAndTakesItBack(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, Z);
		helper.setBlock(pos, crafter(Direction.UP));
		helper.runAfterDelay(2, () -> {
			MechanicalCrafterBlockEntity crafter = be(helper, pos);
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_INGOT, 3));
			helper.useBlock(pos, player);
			helper.assertTrue(crafter.getInventory().getItem(0).is(Items.IRON_INGOT), "the crafter did not take the ingot");
			helper.assertTrue(crafter.getInventory().getItem(0).getCount() == 1, "the slot holds more than one item");
			helper.assertTrue(player.getMainHandItem().getCount() == 2, "the hand holds " + player.getMainHandItem());

			// the slot is full: the rest stays in the hand
			helper.useBlock(pos, player);
			helper.assertTrue(player.getMainHandItem().getCount() == 2, "the full slot took another ingot");

			// extraction through the handler is not possible, only the empty hand can take the item
			IItemHandler handler = ItemHandlerLookup.get(helper.getLevel(), helper.absolutePos(pos), null);
			helper.assertTrue(handler.extractItem(0, 1, false).isEmpty(), "a hopper could take the item");

			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			helper.useBlock(pos, player);
			helper.assertTrue(crafter.getInventory().getItem(0).isEmpty(), "the slot still holds an item");
			helper.assertTrue(player.getInventory().contains(new ItemStack(Items.IRON_INGOT)), "the ingot did not come back");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void wrenchOnEdgeConnectsAndSplitsInventories(GameTestHelper helper) {
		floor(helper);
		BlockPos a = new BlockPos(2, 1, Z);
		BlockPos b = new BlockPos(3, 1, Z);
		helper.setBlock(a, crafter(Direction.UP));
		helper.setBlock(b, crafter(Direction.UP));
		helper.runAfterDelay(2, () -> {
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WRENCH));
			BlockPos abs = helper.absolutePos(a);
			// the top face, close to the edge shared with b
			BlockHitResult hit = new BlockHitResult(new Vec3(abs.getX() + .9, abs.getY() + 1, abs.getZ() + .5), Direction.UP, abs, false);

			helper.assertTrue(handlerSlots(helper, b) == 1, "a lone crafter has several slots");
			InteractionResult result = EdgeInteractionHandler.onBlockActivated(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit);
			helper.assertTrue(result == InteractionResult.SUCCESS, "the wrench did nothing: " + result);
			helper.assertTrue(be(helper, a).getInput().isController(), "a is not the controller");
			helper.assertTrue(!be(helper, b).getInput().isController(), "b is still a controller");
			helper.assertTrue(handlerSlots(helper, a) == 2 && handlerSlots(helper, b) == 2, "the inventories were not joined");

			// filling the joined inventory goes into both crafters, in order
			IItemHandler joined = ItemHandlerLookup.get(helper.getLevel(), helper.absolutePos(b), null);
			helper.assertTrue(ItemHandlerHelper.insertItem(joined, new ItemStack(Items.IRON_INGOT, 2), false).isEmpty(),
				"the joined inventory is too small");
			helper.assertTrue(be(helper, a).getInventory().getItem(0).is(Items.IRON_INGOT)
				&& be(helper, b).getInventory().getItem(0).is(Items.IRON_INGOT), "the items did not spread");

			// the connection survives saving
			BlockEntity loaded = BlockEntity.loadStatic(helper.absolutePos(b), helper.getBlockState(b),
				be(helper, b).saveWithFullMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
			helper.assertTrue(loaded instanceof MechanicalCrafterBlockEntity c && !c.getInput().isController(), "the connection was not saved");

			// a second click on the same edge splits them again
			result = EdgeInteractionHandler.onBlockActivated(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit);
			helper.assertTrue(result == InteractionResult.SUCCESS, "the second click did nothing");
			helper.assertTrue(be(helper, a).getInput().isController() && be(helper, b).getInput().isController(), "they are still joined");
			helper.assertTrue(handlerSlots(helper, a) == 1 && handlerSlots(helper, b) == 1, "the inventories are still joined");

			// without the wrench nothing happens
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
			result = EdgeInteractionHandler.onBlockActivated(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit);
			helper.assertTrue(result == InteractionResult.PASS, "a stick connected crafters");
			helper.succeed();
		});
	}

	private static int handlerSlots(GameTestHelper helper, BlockPos pos) {
		return ItemHandlerLookup.get(helper.getLevel(), helper.absolutePos(pos), null).getSlots();
	}

	@GameTest(maxTicks = 60)
	public void breakingTheMiddleCrafterSplitsTheGroup(GameTestHelper helper) {
		floor(helper);
		BlockPos a = new BlockPos(2, 1, Z);
		BlockPos b = new BlockPos(3, 1, Z);
		BlockPos c = new BlockPos(4, 1, Z);
		helper.setBlock(a, crafter(Direction.UP));
		helper.setBlock(b, crafter(Direction.UP));
		helper.setBlock(c, crafter(Direction.UP));
		helper.runAfterDelay(2, () -> {
			ConnectedInputHandler.toggleConnection(helper.getLevel(), helper.absolutePos(a), helper.absolutePos(b));
			ConnectedInputHandler.toggleConnection(helper.getLevel(), helper.absolutePos(b), helper.absolutePos(c));
			helper.assertTrue(handlerSlots(helper, a) == 3 && handlerSlots(helper, c) == 3, "the three crafters did not join: "
				+ handlerSlots(helper, a) + ", " + handlerSlots(helper, c));
			helper.setBlock(b, Blocks.AIR);
			helper.assertTrue(be(helper, a).getInput().isController() && be(helper, c).getInput().isController(),
				"the neighbours did not get their own controller");
			helper.assertTrue(handlerSlots(helper, a) == 1 && handlerSlots(helper, c) == 1,
				"the neighbours still share inventories: " + handlerSlots(helper, a) + ", " + handlerSlots(helper, c));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void wrenchTurnsTheArrowOnTheFront(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, Z);
		helper.setBlock(pos, crafter(Direction.UP));
		helper.runAfterDelay(2, () -> {
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WRENCH));
			BlockPos abs = helper.absolutePos(pos);
			// the centre of the front face, so it is not an edge click
			BlockHitResult front = new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0, -.5), Direction.NORTH, abs, false);
			Pointing before = helper.getBlockState(pos).getValue(MechanicalCrafterBlock.POINTING);
			helper.useBlock(pos, player, front);
			Pointing after = helper.getBlockState(pos).getValue(MechanicalCrafterBlock.POINTING);
			helper.assertTrue(after != before, "the arrow did not turn");
			helper.assertTrue(be(helper, pos).getTargetDirection() == MechanicalCrafterBlock.getTargetDirection(helper.getBlockState(pos)),
				"the block entity points elsewhere");

			// any other face leaves it alone
			BlockHitResult side = new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0, .5), Direction.SOUTH, abs, false);
			helper.useBlock(pos, player, side);
			helper.assertTrue(helper.getBlockState(pos).getValue(MechanicalCrafterBlock.POINTING) == after, "the back turned the arrow");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 400)
	public void redstoneStartsTheGridWithEmptySlots(GameTestHelper helper) {
		floor(helper);
		// eight planks and no cover on the middle crafter: only a redstone signal starts this one
		grid(helper, 1, 2, 3, 3, 1);
		BlockPos chest = new BlockPos(2, 1, Z);
		helper.setBlock(chest, Blocks.CHEST);
		power(helper, 1, 2, 256);
		helper.runAfterDelay(10, () -> {
			for (int col = 0; col < 3; col++)
				for (int row = 0; row < 3; row++)
					if (col != 1 || row != 1)
						insert(helper, new BlockPos(1 + col, 2 + row, Z), new ItemStack(Items.OAK_PLANKS));
		});
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(be(helper, new BlockPos(1, 2, Z)).getPhase() == Phase.IDLE, "the grid started without a signal");
			helper.setBlock(new BlockPos(1, 4, Z + 1), Blocks.REDSTONE_BLOCK);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(countIn(helper.getBlockEntity(chest, ChestBlockEntity.class), Items.CHEST) == 1,
				"no chest in the output yet");
			assertAllIdle(helper, 1, 2, 3, 3);
		});
	}

	@GameTest(maxTicks = 60)
	public void breakingDropsHeldItemAndCover(GameTestHelper helper) {
		floor(helper);
		BlockPos holding = new BlockPos(2, 1, Z);
		BlockPos covered = new BlockPos(5, 1, Z);
		helper.setBlock(holding, crafter(Direction.UP));
		helper.setBlock(covered, crafter(Direction.UP));
		helper.runAfterDelay(2, () -> {
			insert(helper, holding, new ItemStack(Items.GOLD_INGOT));
			be(helper, covered).setCovered(true);
			helper.setBlock(holding, Blocks.AIR);
			helper.setBlock(covered, Blocks.AIR);
		});
		helper.runAfterDelay(6, () -> {
			helper.assertItemEntityPresent(Items.GOLD_INGOT, holding, 2);
			helper.assertItemEntityPresent(CrafterRegistry.CRAFTER_SLOT_COVER, covered, 2);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void stateSurvivesSaving(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, Z);
		helper.setBlock(pos, crafter(Direction.UP));
		helper.runAfterDelay(2, () -> {
			MechanicalCrafterBlockEntity crafter = be(helper, pos);
			crafter.getInventory().setStackInSlot(0, new ItemStack(Items.DIAMOND));
			crafter.setCovered(true);
			CompoundTag tag = crafter.saveWithFullMetadata(helper.getLevel().registryAccess());
			BlockEntity loaded = BlockEntity.loadStatic(helper.absolutePos(pos), helper.getBlockState(pos), tag, helper.getLevel().registryAccess());
			helper.assertTrue(loaded instanceof MechanicalCrafterBlockEntity, "the block entity did not load");
			MechanicalCrafterBlockEntity copy = (MechanicalCrafterBlockEntity) loaded;
			helper.assertTrue(copy.getInventory().getItem(0).is(Items.DIAMOND), "the item was lost");
			helper.assertTrue(copy.isCovered(), "the cover was lost");
			helper.assertTrue(copy.getPhase() == Phase.IDLE, "the phase changed");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void itemsSlideTowardsTheTargetWhileExporting(GameTestHelper helper) {
		floor(helper);
		// two crafters, the left one gathers into the right one; slow, so the movement can be sampled
		grid(helper, 1, 2, 2, 1, 1);
		helper.setBlock(new BlockPos(2, 1, Z), Blocks.CHEST);
		BlockPos source = new BlockPos(1, 2, Z);
		power(helper, 1, 2, 16);
		helper.runAfterDelay(10, () -> {
			insert(helper, source, new ItemStack(Items.IRON_INGOT));
			insert(helper, new BlockPos(2, 2, Z), new ItemStack(Items.IRON_INGOT));
		});
		Vec3[] first = new Vec3[1];
		helper.succeedWhen(() -> {
			helper.assertTrue(be(helper, source).getPhase() == Phase.EXPORTING, "not exporting yet");
			BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(source));
			Vec3 at = null;
			for (VirtualElement element : attachment.holder().getElements())
				if (element instanceof ItemDisplayElement display && display.getItem().is(Items.IRON_INGOT))
					at = new Vec3(display.getTranslation().x(), display.getTranslation().y(), display.getTranslation().z());
			helper.assertTrue(at != null, "the held ingot is not shown");
			if (first[0] == null)
				first[0] = at;
			// it moves from the front of the block towards the target crafter, one block to the east
			helper.assertTrue(at.distanceTo(first[0]) > .1, "the ingot has not moved yet: " + at);
			helper.assertTrue(at.x > first[0].x, "the ingot does not move east: " + first[0] + " -> " + at);
		});
	}
}
