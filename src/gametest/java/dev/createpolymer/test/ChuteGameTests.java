package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.logistics.chute.ChuteBlock;
import dev.createpolymer.logistics.chute.ChuteBlock.Shape;
import dev.createpolymer.logistics.chute.ChuteBlockEntity;
import dev.createpolymer.logistics.chute.ChuteShapes;
import dev.createpolymer.logistics.chute.SmartChuteBlock;
import dev.createpolymer.logistics.chute.SmartChuteBlockEntity;
import dev.createpolymer.registry.feature.ChuteRegistry;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

public class ChuteGameTests {

	private static BlockState chute() {
		return ChuteRegistry.CHUTE.defaultBlockState();
	}

	private static BlockState smartChute(boolean powered) {
		return ChuteRegistry.SMART_CHUTE.defaultBlockState().setValue(SmartChuteBlock.POWERED, powered);
	}

	private static IItemHandler handlerAt(GameTestHelper helper, BlockPos pos) {
		return ItemHandlerLookup.get(helper.getLevel(), helper.absolutePos(pos), Direction.UP);
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
		helper.assertTrue(attachment != null, "the chute has no model");
		int shown = 0;
		for (VirtualElement element : attachment.holder().getElements())
			if (element instanceof ItemDisplayElement display && display.getItem().is(item))
				shown++;
		return shown;
	}

	@GameTest(maxTicks = 120)
	public void itemFallsThroughChuteLineIntoChest(GameTestHelper helper) {
		floor(helper);
		BlockPos chestPos = new BlockPos(3, 1, 3);
		helper.setBlock(chestPos, Blocks.CHEST);
		BlockPos top = new BlockPos(3, 4, 3);
		for (int y = 2; y <= 4; y++)
			helper.setBlock(new BlockPos(3, y, 3), chute());
		helper.runAfterDelay(2, () -> {
			ItemStack remainder = handlerAt(helper, top).insertItem(0, new ItemStack(Items.DIAMOND, 5), false);
			helper.assertTrue(remainder.isEmpty(), "the top chute did not accept the diamonds: " + remainder);
			helper.assertTrue(helper.getBlockEntity(top, ChuteBlockEntity.class).getItem().is(Items.DIAMOND), "the chute is empty");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(countIn(helper.getBlockEntity(chestPos, ChestBlockEntity.class), Items.DIAMOND) == 5, "the chest has not got the diamonds");
			for (int y = 2; y <= 4; y++)
				helper.assertTrue(helper.getBlockEntity(new BlockPos(3, y, 3), ChuteBlockEntity.class).getItem().isEmpty(), "chute " + y + " still holds an item");
		});
	}

	@GameTest(maxTicks = 100)
	public void chuteAcceptsOnlyOneStackAtATime(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 2, 3);
		helper.setBlock(new BlockPos(3, 1, 3), Blocks.STONE);
		helper.setBlock(pos, chute());
		helper.runAfterDelay(2, () -> {
			IItemHandler handler = handlerAt(helper, pos);
			helper.assertTrue(handler.insertItem(0, new ItemStack(Items.IRON_INGOT, 3), true).isEmpty(), "simulation refused");
			helper.assertTrue(helper.getBlockEntity(pos, ChuteBlockEntity.class).getItem().isEmpty(), "simulation inserted");
			helper.assertTrue(handler.insertItem(0, new ItemStack(Items.IRON_INGOT, 3), false).isEmpty(), "the chute refused the ingots");
			ItemStack second = handler.insertItem(0, new ItemStack(Items.GOLD_INGOT), false);
			helper.assertTrue(second.is(Items.GOLD_INGOT), "a second stack was accepted");
			ItemStack taken = handler.extractItem(0, 2, false);
			helper.assertTrue(taken.getCount() == 2 && helper.getBlockEntity(pos, ChuteBlockEntity.class).getItem().getCount() == 1,
				"extracted " + taken);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void itemEntityOnTopIsTakenIn(GameTestHelper helper) {
		floor(helper);
		BlockPos chestPos = new BlockPos(3, 1, 3);
		helper.setBlock(chestPos, Blocks.CHEST);
		helper.setBlock(new BlockPos(3, 2, 3), chute());
		helper.runAfterDelay(2, () -> helper.spawnItem(Items.GOLD_INGOT, 3.5f, 5f, 3.5f));
		helper.succeedWhen(() -> {
			helper.assertTrue(countIn(helper.getBlockEntity(chestPos, ChestBlockEntity.class), Items.GOLD_INGOT) == 1, "the chest has not got the ingot");
			helper.assertTrue(helper.getEntities(net.minecraft.world.entity.EntityTypes.ITEM, new BlockPos(3, 3, 3), 3).isEmpty(), "the item entity is still there");
		});
	}

	@GameTest(maxTicks = 100)
	public void hopperAboveFeedsAChute(GameTestHelper helper) {
		floor(helper);
		BlockPos chestPos = new BlockPos(3, 1, 3);
		helper.setBlock(chestPos, Blocks.CHEST);
		helper.setBlock(new BlockPos(3, 2, 3), chute());
		BlockPos hopperPos = new BlockPos(3, 3, 3);
		helper.setBlock(hopperPos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
		helper.runAfterDelay(2, () -> helper.getBlockEntity(hopperPos, HopperBlockEntity.class).setItem(0, new ItemStack(Items.EMERALD, 3)));
		helper.succeedWhen(() -> helper.assertTrue(countIn(helper.getBlockEntity(chestPos, ChestBlockEntity.class), Items.EMERALD) > 0,
			"nothing arrived in the chest"));
	}

	@GameTest(maxTicks = 120)
	public void chuteTakesItemsFromAChestAbove(GameTestHelper helper) {
		floor(helper);
		BlockPos upper = new BlockPos(3, 4, 3);
		BlockPos lower = new BlockPos(3, 1, 3);
		helper.setBlock(upper, Blocks.CHEST);
		helper.setBlock(new BlockPos(3, 3, 3), chute());
		helper.setBlock(new BlockPos(3, 2, 3), chute());
		helper.setBlock(lower, Blocks.CHEST);
		helper.runAfterDelay(2, () -> {
			ChestBlockEntity chest = helper.getBlockEntity(upper, ChestBlockEntity.class);
			chest.setItem(0, new ItemStack(Items.IRON_INGOT, 20));
			chest.setItem(1, new ItemStack(Items.COAL, 5));
		});
		helper.succeedWhen(() -> {
			ChestBlockEntity above = helper.getBlockEntity(upper, ChestBlockEntity.class);
			ChestBlockEntity below = helper.getBlockEntity(lower, ChestBlockEntity.class);
			helper.assertTrue(countIn(below, Items.IRON_INGOT) == 20 && countIn(below, Items.COAL) == 5, "the lower chest holds "
				+ countIn(below, Items.IRON_INGOT) + " iron and " + countIn(below, Items.COAL) + " coal");
			helper.assertTrue(above.isEmpty(), "the upper chest is not empty");
		});
	}

	@GameTest(maxTicks = 160)
	public void smartChuteOnlyLetsTheFilteredItemPass(GameTestHelper helper) {
		floor(helper);
		BlockPos upper = new BlockPos(3, 3, 3);
		BlockPos smart = new BlockPos(3, 2, 3);
		BlockPos lower = new BlockPos(3, 1, 3);
		helper.setBlock(upper, Blocks.CHEST);
		helper.setBlock(smart, smartChute(false));
		helper.setBlock(lower, Blocks.CHEST);
		helper.runAfterDelay(2, () -> {
			helper.getBlockEntity(smart, SmartChuteBlockEntity.class).getBehaviour(FilteringBehaviour.TYPE).setFilter(new ItemStack(Items.DIAMOND));
			ChestBlockEntity chest = helper.getBlockEntity(upper, ChestBlockEntity.class);
			chest.setItem(0, new ItemStack(Items.DIRT, 10));
			chest.setItem(1, new ItemStack(Items.DIAMOND, 4));
			chest.setItem(2, new ItemStack(Items.COBBLESTONE, 3));
		});
		helper.runAfterDelay(100, () -> {
			ChestBlockEntity above = helper.getBlockEntity(upper, ChestBlockEntity.class);
			ChestBlockEntity below = helper.getBlockEntity(lower, ChestBlockEntity.class);
			helper.assertTrue(countIn(below, Items.DIAMOND) == 4, "the lower chest holds " + countIn(below, Items.DIAMOND) + " diamonds");
			helper.assertTrue(countIn(below, Items.DIRT) == 0 && countIn(below, Items.COBBLESTONE) == 0, "an unfiltered item passed");
			helper.assertTrue(countIn(above, Items.DIRT) == 10 && countIn(above, Items.COBBLESTONE) == 3, "unfiltered items left the upper chest");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void smartChuteHandlerChecksTheFilter(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 2, 3);
		helper.setBlock(new BlockPos(3, 1, 3), Blocks.STONE);
		helper.setBlock(pos, smartChute(false));
		helper.runAfterDelay(2, () -> {
			SmartChuteBlockEntity be = helper.getBlockEntity(pos, SmartChuteBlockEntity.class);
			be.getBehaviour(FilteringBehaviour.TYPE).setFilter(new ItemStack(Items.DIAMOND));
			IItemHandler handler = handlerAt(helper, pos);
			ItemStack dirt = handler.insertItem(0, new ItemStack(Items.DIRT, 4), false);
			helper.assertTrue(dirt.is(Items.DIRT) && dirt.getCount() == 4 && be.getItem().isEmpty(), "dirt passed the diamond filter");
			ItemStack diamond = handler.insertItem(0, new ItemStack(Items.DIAMOND, 2), false);
			helper.assertTrue(diamond.isEmpty() && be.getItem().is(Items.DIAMOND), "the filtered item was refused: " + diamond);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void poweredSmartChuteDoesNothing(GameTestHelper helper) {
		floor(helper);
		BlockPos upper = new BlockPos(3, 3, 3);
		BlockPos smart = new BlockPos(3, 2, 3);
		BlockPos lower = new BlockPos(3, 1, 3);
		helper.setBlock(upper, Blocks.CHEST);
		helper.setBlock(smart, smartChute(true));
		helper.setBlock(new BlockPos(4, 2, 3), Blocks.REDSTONE_BLOCK);
		helper.setBlock(lower, Blocks.CHEST);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(helper.getBlockState(smart).getValue(SmartChuteBlock.POWERED), "the smart chute lost its power");
			helper.getBlockEntity(upper, ChestBlockEntity.class).setItem(0, new ItemStack(Items.DIAMOND, 4));
			helper.assertTrue(handlerAt(helper, smart).insertItem(0, new ItemStack(Items.DIAMOND), false).is(Items.DIAMOND),
				"a powered smart chute accepted an item");
		});
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(countIn(helper.getBlockEntity(upper, ChestBlockEntity.class), Items.DIAMOND) == 4, "the chute pulled from the chest");
			helper.assertTrue(helper.getBlockEntity(lower, ChestBlockEntity.class).isEmpty(), "something reached the lower chest");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void smartChuteFollowsRedstone(GameTestHelper helper) {
		floor(helper);
		BlockPos smart = new BlockPos(3, 2, 3);
		helper.setBlock(smart, smartChute(false));
		helper.runAfterDelay(2, () -> helper.setBlock(new BlockPos(4, 2, 3), Blocks.REDSTONE_BLOCK));
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(helper.getBlockState(smart).getValue(SmartChuteBlock.POWERED), "the smart chute is not powered");
			helper.setBlock(new BlockPos(4, 2, 3), Blocks.AIR);
		});
		helper.runAfterDelay(16, () -> {
			helper.assertTrue(!helper.getBlockState(smart).getValue(SmartChuteBlock.POWERED), "the smart chute is still powered");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void chuteItemPlacesChutesOnTheSideOfAChute(GameTestHelper helper) {
		floor(helper);
		BlockPos vertical = new BlockPos(3, 1, 4);
		helper.setBlock(vertical, chute());
		helper.runAfterDelay(2, () -> {
			var player = helper.makeMockPlayer(GameType.SURVIVAL);
			ItemStack stack = new ItemStack(ChuteRegistry.CHUTE_ITEM, 3);
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			BlockPos clicked = helper.absolutePos(vertical);
			// the north face of the chute: Create puts the diagonal chute one block up, leading into it
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(clicked).add(0, -.3, -.5), Direction.NORTH, clicked, false);
			stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
			helper.assertTrue(stack.getCount() == 2, "chutes left in hand: " + stack.getCount());
			BlockState placed = helper.getBlockState(new BlockPos(3, 2, 3));
			helper.assertTrue(placed.is(ChuteRegistry.CHUTE), "no chute was placed one block up: " + placed);
			helper.assertTrue(placed.getValue(ChuteBlock.FACING) == Direction.NORTH, "the new chute faces " + placed.getValue(ChuteBlock.FACING));
		});
		helper.runAfterDelay(8, () -> {
			// the chute it leads into has become an intersection
			helper.assertTrue(helper.getBlockState(vertical).getValue(ChuteBlock.SHAPE) == Shape.INTERSECTION,
				"the vertical chute is " + helper.getBlockState(vertical).getValue(ChuteBlock.SHAPE));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void chuteItemPlacesAVerticalChuteOnTheGround(GameTestHelper helper) {
		floor(helper);
		helper.runAfterDelay(1, () -> {
			var player = helper.makeMockPlayer(GameType.SURVIVAL);
			ItemStack stack = new ItemStack(ChuteRegistry.CHUTE_ITEM, 2);
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			BlockPos clicked = helper.absolutePos(new BlockPos(3, 0, 3));
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(clicked).add(0, .5, 0), Direction.UP, clicked, false);
			stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
			BlockState placed = helper.getBlockState(new BlockPos(3, 1, 3));
			helper.assertTrue(placed.is(ChuteRegistry.CHUTE) && placed.getValue(ChuteBlock.FACING) == Direction.DOWN, "placed " + placed);
			helper.assertTrue(stack.getCount() == 1, "chutes left in hand: " + stack.getCount());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void shapesDependOnTheState(GameTestHelper helper) {
		BlockState normal = chute();
		helper.assertTrue(ChuteShapes.getShape(normal) == ChuteShapes.CHUTE, "a vertical chute has the chute shape");
		helper.assertTrue(ChuteShapes.getShape(normal.setValue(ChuteBlock.SHAPE, Shape.INTERSECTION)) == Shapes.block(), "an intersection is a block");
		helper.assertTrue(ChuteShapes.getShape(normal.setValue(ChuteBlock.SHAPE, Shape.ENCASED)) == Shapes.block(), "an encased chute is a block");
		helper.assertTrue(ChuteShapes.getShape(smartChute(false)) == Shapes.block(), "a smart chute is a block");
		BlockState diagonal = normal.setValue(ChuteBlock.FACING, Direction.NORTH);
		var slope = ChuteShapes.getCollisionShape(diagonal);
		helper.assertTrue(!slope.isEmpty() && slope.max(Direction.Axis.Y) > 1, "the slope reaches above the block: " + slope.max(Direction.Axis.Y));
		var south = ChuteShapes.getCollisionShape(diagonal.setValue(ChuteBlock.FACING, Direction.SOUTH));
		helper.assertTrue(south.max(Direction.Axis.Z) == 1 && slope.min(Direction.Axis.Z) == 0, "the slopes face opposite ways");
		helper.assertTrue(ChuteShapes.getCollisionShape(normal).max(Direction.Axis.Y) == 1, "a vertical chute is no higher than a block");
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void wrenchCyclesTheShape(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		helper.setBlock(pos, chute());
		helper.runAfterDelay(2, () -> {
			var player = helper.makeMockPlayer(GameType.SURVIVAL);
			BlockPos abs = helper.absolutePos(pos);
			UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false));
			ChuteBlock block = ChuteRegistry.CHUTE;
			block.onWrenched(helper.getBlockState(pos), context);
			helper.assertTrue(helper.getBlockState(pos).getValue(ChuteBlock.SHAPE) == Shape.WINDOW, "first wrench gave " + helper.getBlockState(pos));
			block.onWrenched(helper.getBlockState(pos), context);
			helper.assertTrue(helper.getBlockState(pos).getValue(ChuteBlock.SHAPE) == Shape.NORMAL, "second wrench gave " + helper.getBlockState(pos));
			helper.setBlock(pos, chute().setValue(ChuteBlock.SHAPE, Shape.ENCASED));
			block.onWrenched(helper.getBlockState(pos), context);
			helper.assertTrue(helper.getBlockState(pos).getValue(ChuteBlock.SHAPE) == Shape.NORMAL, "wrenching an encased chute gave " + helper.getBlockState(pos));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void windowChuteShowsItsItem(GameTestHelper helper) {
		floor(helper);
		BlockPos windowPos = new BlockPos(2, 1, 2);
		BlockPos normalPos = new BlockPos(5, 1, 5);
		helper.setBlock(windowPos, chute().setValue(ChuteBlock.SHAPE, Shape.WINDOW));
		helper.setBlock(normalPos, chute());
		helper.runAfterDelay(2, () -> {
			handlerAt(helper, windowPos).insertItem(0, new ItemStack(Items.DIAMOND), false);
			handlerAt(helper, normalPos).insertItem(0, new ItemStack(Items.DIAMOND), false);
			helper.assertTrue(shownItems(helper, windowPos, Items.DIAMOND) == 0, "an empty window chute shows an item");
		});
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(shownItems(helper, windowPos, Items.DIAMOND) == 1, "the window chute shows " + shownItems(helper, windowPos, Items.DIAMOND) + " diamonds");
			helper.assertTrue(shownItems(helper, normalPos, Items.DIAMOND) == 0, "a closed chute shows its item");
			handlerAt(helper, windowPos).extractItem(0, 64, false);
		});
		helper.runAfterDelay(14, () -> {
			helper.assertTrue(shownItems(helper, windowPos, Items.DIAMOND) == 0, "the item is still shown after taking it out");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void chuteWithNothingBelowDropsTheItem(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 4, 3);
		helper.setBlock(pos, chute());
		helper.runAfterDelay(2, () -> handlerAt(helper, pos).insertItem(0, new ItemStack(Items.REDSTONE, 2), false));
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockEntity(pos, ChuteBlockEntity.class).getItem().isEmpty(), "the chute still holds the item");
			int found = 0;
			for (ItemEntity entity : helper.getEntities(net.minecraft.world.entity.EntityTypes.ITEM, new BlockPos(3, 3, 3), 4))
				found += entity.getItem().getCount();
			helper.assertTrue(found == 2, "dropped " + found + " items");
		});
	}

	@GameTest(maxTicks = 60)
	public void breakingAChuteDropsItsItem(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		helper.setBlock(pos, chute());
		helper.runAfterDelay(2, () -> {
			handlerAt(helper, pos).insertItem(0, new ItemStack(Items.QUARTZ, 3), false);
			helper.setBlock(pos, Blocks.AIR);
		});
		helper.runAfterDelay(6, () -> {
			int found = 0;
			for (ItemEntity entity : helper.getEntities(net.minecraft.world.entity.EntityTypes.ITEM, pos, 3))
				found += entity.getItem().getCount();
			helper.assertTrue(found == 3, "dropped " + found + " items");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void itemSurvivesSaving(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		helper.setBlock(pos, smartChute(false));
		helper.runAfterDelay(2, () -> {
			SmartChuteBlockEntity be = helper.getBlockEntity(pos, SmartChuteBlockEntity.class);
			be.getBehaviour(FilteringBehaviour.TYPE).setFilter(new ItemStack(Items.GOLD_INGOT));
			handlerAt(helper, pos).insertItem(0, new ItemStack(Items.GOLD_INGOT, 7), false);
			var registries = helper.getLevel().registryAccess();
			CompoundTag saved = be.saveWithFullMetadata(registries);
			BlockEntity loaded = BlockEntity.loadStatic(be.getBlockPos(), be.getBlockState(), saved, registries);
			helper.assertTrue(loaded instanceof SmartChuteBlockEntity, "block entity did not load");
			SmartChuteBlockEntity chute = (SmartChuteBlockEntity) loaded;
			helper.assertTrue(chute.getItem().is(Items.GOLD_INGOT) && chute.getItem().getCount() == 7, "item was lost: " + chute.getItem());
			helper.assertTrue(chute.getBehaviour(FilteringBehaviour.TYPE).getFilter().is(Items.GOLD_INGOT), "filter was lost");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void smartChuteShowsTheFilter(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		helper.setBlock(pos, smartChute(false));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(shownItems(helper, pos, Items.AMETHYST_SHARD) == 0, "an item is shown before there is a filter");
			helper.getBlockEntity(pos, SmartChuteBlockEntity.class).getBehaviour(FilteringBehaviour.TYPE).setFilter(new ItemStack(Items.AMETHYST_SHARD));
		});
		helper.runAfterDelay(8, () -> {
			// one display entity for every side of the chute
			int shown = shownItems(helper, pos, Items.AMETHYST_SHARD);
			helper.assertTrue(shown == 4, "the filter is shown " + shown + " times");
			helper.succeed();
		});
	}
}
