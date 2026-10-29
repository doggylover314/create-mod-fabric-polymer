package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import java.util.ArrayList;
import java.util.List;

import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.base.RotatedPillarKineticBlock;
import dev.createpolymer.kinetics.belt.BeltBlockEntity;
import dev.createpolymer.kinetics.belt.item.BeltConnectorItem;
import dev.createpolymer.kinetics.belt.transport.TransportedItemStack;
import dev.createpolymer.kinetics.crusher.CrushingWheelControllerBlock;
import dev.createpolymer.kinetics.crusher.CrushingWheelControllerBlockEntity;
import dev.createpolymer.kinetics.fan.processing.AllFanProcessingTypes;
import dev.createpolymer.kinetics.fan.FanConfig;
import dev.createpolymer.kinetics.motor.CreativeMotorBlockEntity;
import dev.createpolymer.logistics.chute.ChuteBlockEntity;
import dev.createpolymer.logistics.funnel.BeltFunnelBlock;
import dev.createpolymer.logistics.funnel.BeltFunnelBlock.Shape;
import dev.createpolymer.logistics.funnel.FunnelBlock;
import dev.createpolymer.logistics.funnel.FunnelBlockEntity;
import dev.createpolymer.logistics.funnel.FunnelItem;
import dev.createpolymer.processing.burner.BlazeBurnerBlock;
import dev.createpolymer.processing.burner.BlazeBurnerBlockEntity;
import dev.createpolymer.processing.burner.LitBlazeBurnerBlock;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.feature.BurnerRegistry;
import dev.createpolymer.registry.feature.ChuteRegistry;
import dev.createpolymer.registry.feature.CrushingRegistry;
import dev.createpolymer.registry.feature.FanRegistry;
import dev.createpolymer.registry.feature.FunnelRegistry;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class FunnelGameTests {

	// The belt runs along +X at z = 1 and y = 1 from the shaft at x = 1 to the one at x = 6, like in BeltGameTests.
	private static final BlockPos MOTOR = new BlockPos(1, 1, 0);
	private static final BlockPos START = new BlockPos(1, 1, 1);
	private static final BlockPos END = new BlockPos(6, 1, 1);

	private static void buildBelt(GameTestHelper helper, int speed) {
		floor(helper);
		helper.setBlock(START, ModBlocks.SHAFT.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.Z));
		helper.setBlock(END, ModBlocks.SHAFT.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.Z));
		helper.setBlock(MOTOR, ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.SOUTH));
		helper.runAfterDelay(1, () -> helper.getBlockEntity(MOTOR, CreativeMotorBlockEntity.class).setSetting(-speed));
		BeltConnectorItem.createBelts(helper.getLevel(), helper.absolutePos(START), helper.absolutePos(END));
	}

	private static int itemsOnBelt(GameTestHelper helper, Item item) {
		int found = 0;
		var inventory = helper.getBlockEntity(START, BeltBlockEntity.class).getInventory();
		if (inventory == null)
			return 0;
		for (TransportedItemStack t : inventory.getTransportedItems())
			if (t.stack.is(item))
				found += t.stack.getCount();
		return found;
	}

	private static IItemHandler beltHandler(GameTestHelper helper, BlockPos pos) {
		return ItemHandlerLookup.get(helper.getLevel(), helper.absolutePos(pos), Direction.UP);
	}

	private static ChestBlockEntity chest(GameTestHelper helper, BlockPos pos, ItemStack... stacks) {
		helper.setBlock(pos, Blocks.CHEST);
		ChestBlockEntity chest = helper.getBlockEntity(pos, ChestBlockEntity.class);
		for (int i = 0; i < stacks.length; i++)
			chest.setItem(i, stacks[i]);
		return chest;
	}

	private static int countIn(ChestBlockEntity chest, Item item) {
		int count = 0;
		for (int i = 0; i < chest.getContainerSize(); i++)
			if (chest.getItem(i).is(item))
				count += chest.getItem(i).getCount();
		return count;
	}

	private static int entities(GameTestHelper helper, BlockPos around, double radius, Item item) {
		int count = 0;
		for (ItemEntity entity : helper.getEntities(EntityTypes.ITEM, around, radius))
			if (entity.getItem().is(item))
				count += entity.getItem().getCount();
		return count;
	}

	private static BlockState floorFunnel(boolean brass, boolean extracting) {
		return (brass ? FunnelRegistry.BRASS_FUNNEL : FunnelRegistry.ANDESITE_FUNNEL).defaultBlockState()
			.setValue(FunnelBlock.FACING, Direction.UP)
			.setValue(FunnelBlock.EXTRACTING, extracting);
	}

	private static BlockState beltFunnel(boolean brass, Direction facing, Shape shape) {
		return (brass ? FunnelRegistry.BRASS_BELT_FUNNEL : FunnelRegistry.ANDESITE_BELT_FUNNEL).defaultBlockState()
			.setValue(BeltFunnelBlock.HORIZONTAL_FACING, facing)
			.setValue(BeltFunnelBlock.SHAPE, shape);
	}

	// ---- in the world ----

	@GameTest(maxTicks = 100)
	public void andesiteFunnelExtractsFromChestIntoTheWorld(GameTestHelper helper) {
		floor(helper);
		chest(helper, new BlockPos(2, 1, 2), new ItemStack(Items.IRON_INGOT, 4));
		helper.setBlock(new BlockPos(2, 2, 2), floorFunnel(false, true));
		helper.succeedWhen(() -> {
			helper.assertTrue(entities(helper, new BlockPos(2, 2, 2), 1.5, Items.IRON_INGOT) > 0, "nothing came out of the funnel");
		});
	}

	@GameTest(maxTicks = 100)
	public void funnelCollectsItemEntityIntoChest(GameTestHelper helper) {
		floor(helper);
		ChestBlockEntity chest = chest(helper, new BlockPos(2, 1, 2));
		helper.setBlock(new BlockPos(2, 2, 2), floorFunnel(false, false));
		helper.runAfterDelay(2, () -> helper.spawnItem(Items.GOLD_INGOT, 2.5f, 2.7f, 2.5f));
		helper.succeedWhen(() -> helper.assertTrue(countIn(chest, Items.GOLD_INGOT) == 1, "the chest holds no gold"));
	}

	@GameTest(maxTicks = 100)
	public void poweredFunnelDoesNothing(GameTestHelper helper) {
		floor(helper);
		ChestBlockEntity chest = chest(helper, new BlockPos(2, 1, 2), new ItemStack(Items.IRON_INGOT, 4));
		helper.setBlock(new BlockPos(2, 2, 2), floorFunnel(false, true).setValue(FunnelBlock.POWERED, true));
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(countIn(chest, Items.IRON_INGOT) == 4, "a powered funnel extracted");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void brassFunnelFilterOnlyMovesMatchingItems(GameTestHelper helper) {
		floor(helper);
		ChestBlockEntity chest = chest(helper, new BlockPos(2, 1, 2), new ItemStack(Items.IRON_INGOT, 5),
			new ItemStack(Items.GOLD_INGOT, 5));
		BlockPos funnel = new BlockPos(2, 2, 2);
		helper.setBlock(funnel, floorFunnel(true, true));
		helper.getBlockEntity(funnel, FunnelBlockEntity.class).getBehaviour(FilteringBehaviour.TYPE)
			.setFilter(new ItemStack(Items.GOLD_INGOT));
		helper.succeedWhen(() -> {
			helper.assertTrue(entities(helper, funnel, 1.5, Items.GOLD_INGOT) > 0, "no gold came out");
			helper.assertTrue(entities(helper, funnel, 3, Items.IRON_INGOT) == 0, "iron came out");
			helper.assertTrue(countIn(chest, Items.IRON_INGOT) == 5, "iron left the chest");
		});
	}

	@GameTest(maxTicks = 200)
	public void brassFunnelOnBeltRespectsTheExactAmount(GameTestHelper helper) {
		buildBelt(helper, 32);
		ChestBlockEntity chest = chest(helper, new BlockPos(3, 2, 2), new ItemStack(Items.IRON_INGOT, 5),
			new ItemStack(Items.GOLD_INGOT, 5));
		BlockPos funnel = new BlockPos(3, 2, 1);
		helper.runAfterDelay(4, () -> {
			helper.setBlock(funnel, beltFunnel(true, Direction.NORTH, Shape.PUSHING));
			FilteringBehaviour filtering = helper.getBlockEntity(funnel, FunnelBlockEntity.class).getBehaviour(FilteringBehaviour.TYPE);
			filtering.setFilter(new ItemStack(Items.GOLD_INGOT));
			filtering.count = 2;
			filtering.upTo = false;
		});
		helper.runAfterDelay(150, () -> {
			// 5 gold = two batches of exactly 2; the last one stays
			helper.assertTrue(countIn(chest, Items.GOLD_INGOT) == 1, "the chest holds " + countIn(chest, Items.GOLD_INGOT) + " gold");
			helper.assertTrue(countIn(chest, Items.IRON_INGOT) == 5, "iron left the chest");
			helper.succeed();
		});
	}

	// ---- belts ----

	@GameTest(maxTicks = 200)
	public void extractingBeltFunnelPutsChestItemsOnTheBelt(GameTestHelper helper) {
		buildBelt(helper, 32);
		chest(helper, new BlockPos(3, 2, 2), new ItemStack(Items.COPPER_INGOT, 3));
		helper.runAfterDelay(4, () -> helper.setBlock(new BlockPos(3, 2, 1), beltFunnel(false, Direction.NORTH, Shape.PUSHING)));
		helper.succeedWhen(() -> helper.assertTrue(
			itemsOnBelt(helper, Items.COPPER_INGOT) > 0 || entities(helper, new BlockPos(7, 1, 1), 3, Items.COPPER_INGOT) > 0,
			"nothing was put on the belt"));
	}

	@GameTest(maxTicks = 300)
	public void pullingBeltFunnelMovesBeltItemsIntoChest(GameTestHelper helper) {
		buildBelt(helper, 32);
		ChestBlockEntity chest = chest(helper, new BlockPos(3, 2, 2));
		helper.runAfterDelay(4, () -> helper.setBlock(new BlockPos(3, 2, 1), beltFunnel(false, Direction.NORTH, Shape.PULLING)));
		helper.runAfterDelay(8, () -> beltHandler(helper, new BlockPos(2, 1, 1)).insertItem(0, new ItemStack(Items.EMERALD, 3), false));
		helper.succeedWhen(() -> {
			helper.assertTrue(countIn(chest, Items.EMERALD) == 3, "the chest holds " + countIn(chest, Items.EMERALD) + " emeralds");
			helper.assertTrue(itemsOnBelt(helper, Items.EMERALD) == 0, "an item stayed on the belt");
		});
	}

	@GameTest(maxTicks = 300)
	public void retractedBeltFunnelBlocksTheBeltAndTakesItems(GameTestHelper helper) {
		buildBelt(helper, 32);
		ChestBlockEntity chest = chest(helper, new BlockPos(4, 2, 1));
		helper.runAfterDelay(4, () -> {
			helper.setBlock(new BlockPos(3, 2, 1), beltFunnel(false, Direction.WEST, Shape.RETRACTED));
			helper.assertTrue(helper.getBlockState(new BlockPos(3, 2, 1)).getValue(BeltFunnelBlock.SHAPE) == Shape.RETRACTED,
				"the funnel did not stay retracted");
		});
		helper.runAfterDelay(8, () -> beltHandler(helper, new BlockPos(2, 1, 1)).insertItem(0, new ItemStack(Items.DIAMOND, 2), false));
		helper.succeedWhen(() -> {
			helper.assertTrue(countIn(chest, Items.DIAMOND) == 2, "the chest holds " + countIn(chest, Items.DIAMOND) + " diamonds");
			helper.assertTrue(entities(helper, new BlockPos(7, 1, 1), 3, Items.DIAMOND) == 0, "diamonds passed the funnel");
		});
	}

	@GameTest(maxTicks = 100)
	public void funnelItemPlacedOnABeltMakesABeltFunnel(GameTestHelper helper) {
		buildBelt(helper, 32);
		// a chest next to the belt: a funnel placed against its side lands above the belt, and the item has to place
		// instead of opening the chest
		BlockPos chest = new BlockPos(3, 2, 2);
		chest(helper, chest);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FunnelRegistry.ANDESITE_FUNNEL_ITEM, 2));
		helper.runAfterDelay(8, () -> {
			BlockPos abs = helper.absolutePos(chest);
			// GameTestHelper.useBlock skips Fabric's use-block callbacks, so the funnel item's one is called directly
			var result = FunnelItem.funnelItemAlwaysPlacesWhenUsed(player, helper.getLevel(), InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0, -.5), Direction.NORTH, abs, false));
			helper.assertTrue(result.consumesAction(), "the callback did not place: " + result);
			BlockState state = helper.getBlockState(new BlockPos(3, 2, 1));
			helper.assertTrue(state.is(FunnelRegistry.ANDESITE_BELT_FUNNEL), "placed " + state);
			helper.assertTrue(state.getValue(BeltFunnelBlock.HORIZONTAL_FACING) == Direction.NORTH, "facing " + state);
			helper.assertTrue(player.getMainHandItem().getCount() == 1, "the funnel item was not used: " + player.getMainHandItem());
			helper.succeed();
		});
	}

	// ---- chutes and fans ----

	@GameTest(maxTicks = 200)
	public void funnelBelowAChutePullsItsItemOut(GameTestHelper helper) {
		floor(helper);
		BlockPos chute = new BlockPos(2, 3, 2);
		helper.setBlock(chute, ChuteRegistry.CHUTE);
		BlockPos funnel = new BlockPos(2, 2, 2);
		helper.setBlock(funnel, FunnelRegistry.ANDESITE_FUNNEL.defaultBlockState()
			.setValue(FunnelBlock.FACING, Direction.DOWN)
			.setValue(FunnelBlock.EXTRACTING, true));
		helper.runAfterDelay(4, () -> helper.getBlockEntity(chute, ChuteBlockEntity.class).setItem(new ItemStack(Items.QUARTZ, 2)));
		helper.succeedWhen(() -> {
			// an andesite funnel takes one item at a time, and waits while that one lies below it
			helper.assertTrue(entities(helper, new BlockPos(2, 1, 2), 2, Items.QUARTZ) == 1, "the funnel did not pull an item out");
			helper.assertTrue(helper.getBlockEntity(chute, ChuteBlockEntity.class).getItem().getCount() == 1,
				"the chute holds " + helper.getBlockEntity(chute, ChuteBlockEntity.class).getItem());
		});
	}

	@GameTest(maxTicks = 300)
	public void fanBelowAChutePushesItsItemUp(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(2, 1, 2), ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
		helper.runAfterDelay(2, () -> helper.getBlockEntity(new BlockPos(2, 1, 2), CreativeMotorBlockEntity.class).setSetting(128));
		helper.setBlock(new BlockPos(2, 2, 2), FanRegistry.ENCASED_FAN.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
		helper.setBlock(new BlockPos(2, 3, 2), ChuteRegistry.CHUTE);
		helper.setBlock(new BlockPos(2, 4, 2), ChuteRegistry.CHUTE);
		helper.runAfterDelay(10, () -> helper.getBlockEntity(new BlockPos(2, 3, 2), ChuteBlockEntity.class).setItem(new ItemStack(Items.AMETHYST_SHARD)));
		helper.succeedWhen(() -> {
			helper.assertTrue(entities(helper, new BlockPos(2, 5, 2), 2.5, Items.AMETHYST_SHARD) == 1, "the item was not pushed out of the top");
			ItemEntity found = null;
			for (ItemEntity entity : helper.getEntities(EntityTypes.ITEM, new BlockPos(2, 5, 2), 3))
				if (entity.getItem().is(Items.AMETHYST_SHARD))
					found = entity;
			helper.assertTrue(found != null && found.getY() > helper.absolutePos(new BlockPos(2, 5, 2)).getY() - .1, "the item is still low");
		});
	}

	// ---- crushing wheels ----

	@GameTest(maxTicks = 300)
	public void beltFeedsItemsIntoCrushingWheels(GameTestHelper helper) {
		buildBelt(helper, 32);
		BlockPos controller = new BlockPos(3, 2, 1);
		helper.runAfterDelay(4, () -> {
			helper.setBlock(controller, CrushingRegistry.CRUSHING_WHEEL_CONTROLLER.defaultBlockState()
				.setValue(CrushingWheelControllerBlock.FACING, Direction.EAST)
				.setValue(CrushingWheelControllerBlock.VALID, true));
			helper.getBlockEntity(controller, CrushingWheelControllerBlockEntity.class).crushingspeed = 16;
		});
		helper.runAfterDelay(8, () -> beltHandler(helper, new BlockPos(2, 1, 1)).insertItem(0, new ItemStack(Items.RAW_IRON), false));
		helper.succeedWhen(() -> {
			boolean crushed = false;
			var inventory = helper.getBlockEntity(START, BeltBlockEntity.class).getInventory();
			if (inventory != null)
				for (TransportedItemStack t : inventory.getTransportedItems())
					crushed |= !t.stack.is(Items.RAW_IRON);
			for (ItemEntity entity : helper.getEntities(EntityTypes.ITEM, new BlockPos(5, 1, 1), 6))
				crushed |= !entity.getItem().is(Items.RAW_IRON);
			helper.assertTrue(crushed, "nothing was crushed");
			helper.assertTrue(itemsOnBelt(helper, Items.RAW_IRON) == 0, "the raw iron stayed on the belt");
		});
	}

	// ---- blaze burners under fans ----

	@GameTest(maxTicks = 100)
	public void fanProcessingTypesCheckTheBurnersHeat(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		for (BlazeBurnerBlock.HeatLevel heat : BlazeBurnerBlock.HeatLevel.values()) {
			helper.setBlock(pos, BurnerRegistry.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, heat));
			var level = helper.getLevel();
			BlockPos abs = helper.absolutePos(pos);
			helper.assertTrue(AllFanProcessingTypes.BLASTING.isValidAt(level, abs) == heat.isAtLeast(BlazeBurnerBlock.HeatLevel.FADING),
				"blasting over heat " + heat);
			helper.assertTrue(AllFanProcessingTypes.SMOKING.isValidAt(level, abs) == (heat == BlazeBurnerBlock.HeatLevel.SMOULDERING),
				"smoking over heat " + heat);
		}
		helper.setBlock(pos, BurnerRegistry.LIT_BLAZE_BURNER.defaultBlockState().setValue(LitBlazeBurnerBlock.FLAME_TYPE, LitBlazeBurnerBlock.FlameType.SOUL));
		BlockPos abs = helper.absolutePos(pos);
		helper.assertTrue(AllFanProcessingTypes.HAUNTING.isValidAt(helper.getLevel(), abs), "no haunting over a soul flame");
		helper.assertTrue(!AllFanProcessingTypes.SMOKING.isValidAt(helper.getLevel(), abs), "smoking over a soul flame");
		helper.setBlock(pos, BurnerRegistry.LIT_BLAZE_BURNER.defaultBlockState().setValue(LitBlazeBurnerBlock.FLAME_TYPE, LitBlazeBurnerBlock.FlameType.REGULAR));
		helper.assertTrue(!AllFanProcessingTypes.HAUNTING.isValidAt(helper.getLevel(), abs), "haunting over a regular flame");
		helper.assertTrue(AllFanProcessingTypes.SMOKING.isValidAt(helper.getLevel(), abs), "no smoking over a regular flame");
		helper.succeed();
	}

	@GameTest(maxTicks = 300)
	public void blastingFanWorksOverAKindledBlazeBurner(GameTestHelper helper) {
		FanConfig.fanProcessingTime = 5;
		floor(helper);
		for (int x = 2; x <= 6; x++) {
			helper.setBlock(new BlockPos(x, 1, 0), Blocks.STONE);
			helper.setBlock(new BlockPos(x, 1, 2), Blocks.STONE);
		}
		helper.setBlock(new BlockPos(6, 1, 1), Blocks.STONE);
		helper.setBlock(new BlockPos(0, 1, 1), ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(1, 1, 1), FanRegistry.ENCASED_FAN.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
		BlockPos burner = new BlockPos(2, 1, 1);
		helper.setBlock(burner, BurnerRegistry.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.KINDLED));
		helper.getBlockEntity(burner, BlazeBurnerBlockEntity.class).isCreative = true;
		helper.setBlock(new BlockPos(3, 1, 1), Blocks.IRON_BARS);
		helper.spawnItem(Items.RAW_IRON, 5.5f, 1.1f, 1.5f);
		helper.succeedWhen(() -> {
			helper.assertTrue(entities(helper, new BlockPos(4, 1, 1), 4, Items.IRON_INGOT) > 0, "no iron ingot yet");
			helper.assertTrue(entities(helper, new BlockPos(4, 1, 1), 4, Items.RAW_IRON) == 0, "the raw iron was not used up");
		});
	}

	// ---- looks ----

	@GameTest(maxTicks = 100)
	public void funnelModelShowsBodyFlapsAndFilter(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos, FunnelRegistry.BRASS_FUNNEL.defaultBlockState().setValue(FunnelBlock.FACING, Direction.NORTH));
		helper.getBlockEntity(pos, FunnelBlockEntity.class).getBehaviour(FilteringBehaviour.TYPE).setFilter(new ItemStack(Items.IRON_INGOT));
		helper.runAfterDelay(6, () -> {
			BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(pos));
			helper.assertTrue(attachment != null, "no model");
			List<ItemDisplayElement> displays = new ArrayList<>();
			for (VirtualElement element : attachment.holder().getElements())
				if (element instanceof ItemDisplayElement display && !display.getItem().isEmpty())
					displays.add(display);
			int flaps = 0;
			boolean body = false;
			boolean filter = false;
			for (ItemDisplayElement display : displays) {
				var model = display.getItem().get(DataComponents.ITEM_MODEL);
				String name = model == null ? "" : model.toString();
				flaps += name.contains("funnel/flap") ? 1 : 0;
				body |= name.contains("brass_funnel_horizontal");
				filter |= display.getItem().is(Items.IRON_INGOT);
			}
			helper.assertTrue(flaps == 4, "flap parts: " + flaps);
			helper.assertTrue(body, "no body model among " + displays.size() + " displays");
			helper.assertTrue(filter, "no filter display");
			helper.succeed();
		});
	}
}
