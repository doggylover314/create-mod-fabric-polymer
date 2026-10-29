package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dev.createpolymer.fluids.drain.ItemDrainBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.foundation.fluid.FluidHandlerLookup;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.IFluidHandler;
import dev.createpolymer.foundation.fluid.IFluidHandler.FluidAction;
import dev.createpolymer.item.GogglesInfo;
import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.base.RotatedPillarKineticBlock;
import dev.createpolymer.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import dev.createpolymer.kinetics.deployer.AssemblyOperatorBlockItem;
import dev.createpolymer.kinetics.mechanicalArm.AllArmInteractionPointTypes;
import dev.createpolymer.kinetics.mechanicalArm.ArmBlockEntity;
import dev.createpolymer.kinetics.mechanicalArm.ArmInteractionPoint;
import dev.createpolymer.kinetics.motor.CreativeMotorBlockEntity;
import dev.createpolymer.logistics.depot.DepotBlockEntity;
import dev.createpolymer.logistics.depot.EjectorBlock;
import dev.createpolymer.logistics.depot.EjectorBlockEntity;
import dev.createpolymer.logistics.depot.EjectorBlockEntity.State;
import dev.createpolymer.logistics.funnel.BeltFunnelBlock;
import dev.createpolymer.logistics.funnel.BeltFunnelBlock.Shape;
import dev.createpolymer.logistics.funnel.FunnelBlock;
import dev.createpolymer.processing.basin.BasinBlock;
import dev.createpolymer.processing.basin.BasinBlockEntity;
import dev.createpolymer.processing.recipe.ProcessingOutput;
import dev.createpolymer.processing.recipe.ProcessingRecipe;
import dev.createpolymer.redstone.smartObserver.SmartObserverBlock;
import dev.createpolymer.redstone.smartObserver.SmartObserverBlockEntity;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.ArmRegistry;
import dev.createpolymer.registry.feature.EjectorRegistry;
import dev.createpolymer.registry.feature.FunnelRegistry;
import dev.createpolymer.registry.feature.RedstoneRegistry;
import dev.createpolymer.registry.feature.TanksRegistry;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Cross-feature behaviour: funnels with arms, ejectors, drains and observers, basin output, goggles, recipes. */
public class SeamsGameTests {

	private static final BlockPos ARM = new BlockPos(3, 1, 3);

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

	private static IFluidHandler handlerAt(GameTestHelper helper, BlockPos pos) {
		return FluidHandlerLookup.get(helper.getLevel(), helper.absolutePos(pos), null);
	}

	private static void power(GameTestHelper helper, int rpm) {
		BlockPos cog = ARM.east();
		helper.setBlock(cog.below(), ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
		helper.setBlock(cog, ModBlocks.COGWHEEL.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.Y));
		helper.runAfterDelay(2, () -> helper.getBlockEntity(cog.below(), CreativeMotorBlockEntity.class).setSetting(rpm));
	}

	private static ArmInteractionPoint point(GameTestHelper helper, BlockPos pos, boolean input) {
		BlockPos abs = helper.absolutePos(pos);
		ArmInteractionPoint point = ArmInteractionPoint.create(helper.getLevel(), abs, helper.getLevel().getBlockState(abs));
		helper.assertTrue(point != null, "no interaction point for the block at " + pos);
		if (input)
			point.cycleMode();
		return point;
	}

	// ---- 1: arm funnel point ----

	@GameTest(maxTicks = 40)
	public void armFunnelPointTypeFollowsCreatesRules(GameTestHelper helper) {
		floor(helper);
		BlockPos funnel = new BlockPos(2, 2, 2);
		BlockPos belt = new BlockPos(5, 2, 2);
		chest(helper, new BlockPos(2, 1, 2));
		helper.setBlock(funnel, floorFunnel(false, false));
		helper.runAfterDelay(2, () -> {
			var level = helper.getLevel();
			BlockPos abs = helper.absolutePos(funnel);
			helper.assertTrue(AllArmInteractionPointTypes.FUNNEL.canCreatePoint(level, abs, level.getBlockState(abs)),
				"a collecting funnel is not a point");
			helper.assertTrue(point(helper, funnel, false).getType() == AllArmInteractionPointTypes.FUNNEL,
				"the point of a funnel has type " + point(helper, funnel, false).getType());

			BlockState extracting = floorFunnel(false, true);
			helper.assertTrue(!AllArmInteractionPointTypes.FUNNEL.canCreatePoint(level, abs, extracting),
				"an extracting funnel is a point");
			BlockState pushing = beltFunnel(false, Direction.NORTH, Shape.PUSHING);
			helper.assertTrue(!AllArmInteractionPointTypes.FUNNEL.canCreatePoint(level, abs, pushing),
				"a pushing belt funnel is a point");
			BlockState pulling = beltFunnel(true, Direction.NORTH, Shape.PULLING);
			helper.assertTrue(AllArmInteractionPointTypes.FUNNEL.canCreatePoint(level, abs, pulling),
				"a pulling belt funnel is not a point");
			helper.assertTrue(!AllArmInteractionPointTypes.FUNNEL.canCreatePoint(level, abs, Blocks.CHEST.defaultBlockState()),
				"a chest is a funnel point");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void armFunnelPointInsertsIntoTheInventoryBehindTheFunnel(GameTestHelper helper) {
		floor(helper);
		BlockPos funnel = new BlockPos(2, 2, 2);
		ChestBlockEntity chest = chest(helper, new BlockPos(2, 1, 2));
		helper.setBlock(funnel, floorFunnel(false, false));
		helper.runAfterDelay(2, () -> {
			ArmInteractionPoint point = point(helper, funnel, false);
			ItemStack remainder = point.insert(null, new ItemStack(Items.IRON_INGOT, 3), false);
			helper.assertTrue(remainder.isEmpty(), "the funnel kept " + remainder);
			helper.assertTrue(countIn(chest, Items.IRON_INGOT) == 3, "the chest holds " + countIn(chest, Items.IRON_INGOT));
			helper.assertTrue(point.extract(null, 0, 1, false).isEmpty(), "a funnel point gave items out");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void poweredFunnelRefusesArmItems(GameTestHelper helper) {
		floor(helper);
		BlockPos funnel = new BlockPos(2, 2, 2);
		ChestBlockEntity chest = chest(helper, new BlockPos(2, 1, 2));
		helper.setBlock(funnel, floorFunnel(false, false).setValue(FunnelBlock.POWERED, true));
		helper.runAfterDelay(2, () -> {
			ItemStack remainder = point(helper, funnel, false).insert(null, new ItemStack(Items.IRON_INGOT, 3), false);
			helper.assertTrue(remainder.getCount() == 3, "a powered funnel took items: " + remainder);
			helper.assertTrue(countIn(chest, Items.IRON_INGOT) == 0, "the chest got items");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 400)
	public void armMovesItemsFromAChestIntoAFunnel(GameTestHelper helper) {
		floor(helper);
		BlockPos in = new BlockPos(1, 1, 3);
		BlockPos funnel = new BlockPos(3, 2, 5);
		helper.setBlock(ARM, ArmRegistry.MECHANICAL_ARM);
		chest(helper, in);
		ChestBlockEntity out = chest(helper, new BlockPos(3, 1, 5));
		helper.setBlock(funnel, floorFunnel(false, false));
		power(helper, 128);
		helper.runAfterDelay(3, () -> {
			helper.getBlockEntity(in, ChestBlockEntity.class).setItem(0, new ItemStack(Items.DIAMOND, 3));
			BlockPos anchor = helper.absolutePos(ARM);
			helper.getBlockEntity(ARM, ArmBlockEntity.class).setInteractionPoints(Arrays.stream(
				new ArmInteractionPoint[] { point(helper, in, true), point(helper, funnel, false) })
				.map(p -> p.serialize(anchor)).toList());
		});
		helper.succeedWhen(() -> helper.assertTrue(countIn(out, Items.DIAMOND) == 3, "the chest behind the funnel holds " + countIn(out, Items.DIAMOND)));
	}

	// ---- 2: weighted ejector with a belt funnel on top ----

	private static final BlockPos EJECTOR = new BlockPos(1, 1, 2);
	private static final BlockPos MOTOR = new BlockPos(1, 1, 3);
	private static final BlockPos TARGET = new BlockPos(4, 1, 2);

	/** The ejector faces east, so its belt funnel faces west and exports into the chest on its east side. */
	private static void buildEjector(GameTestHelper helper, boolean withFunnel, ItemStack... chestContent) {
		floor(helper);
		helper.setBlock(EJECTOR, EjectorRegistry.WEIGHTED_EJECTOR.defaultBlockState()
			.setValue(EjectorBlock.HORIZONTAL_FACING, Direction.EAST));
		helper.setBlock(MOTOR, ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.NORTH));
		helper.setBlock(TARGET, ModBlocks.DEPOT);
		if (withFunnel) {
			chest(helper, new BlockPos(2, 2, 2), chestContent);
			helper.setBlock(new BlockPos(1, 2, 2), beltFunnel(false, Direction.WEST, Shape.PULLING));
		}
		helper.runAfterDelay(1, () -> {
			helper.getBlockEntity(EJECTOR, EjectorBlockEntity.class).setTarget(3, 0);
			helper.getBlockEntity(MOTOR, CreativeMotorBlockEntity.class).setSetting(256);
		});
	}

	private static void putOnEjector(GameTestHelper helper, ItemStack stack) {
		EjectorBlockEntity ejector = helper.getBlockEntity(EJECTOR, EjectorBlockEntity.class);
		helper.assertTrue(ejector.getState() == State.CHARGED, "the ejector is not charged: " + ejector.getState());
		ItemStack remainder = ejector.getDepotBehaviour().itemHandler.insertItem(0, stack, false);
		helper.assertTrue(remainder.isEmpty(), "the ejector did not take the items: " + remainder);
	}

	@GameTest(maxTicks = 160)
	public void ejectorHandsItemsToABeltFunnelInsteadOfLaunching(GameTestHelper helper) {
		buildEjector(helper, true);
		ChestBlockEntity chest = helper.getBlockEntity(new BlockPos(2, 2, 2), ChestBlockEntity.class);
		helper.runAfterDelay(25, () -> putOnEjector(helper, new ItemStack(Items.GOLD_INGOT, 5)));
		helper.succeedWhen(() -> {
			helper.assertTrue(countIn(chest, Items.GOLD_INGOT) == 5, "the chest behind the funnel holds " + countIn(chest, Items.GOLD_INGOT));
			helper.assertTrue(helper.getBlockEntity(EJECTOR, EjectorBlockEntity.class).getDepotBehaviour().getHeldItemStack().isEmpty(),
				"the ejector still holds its items");
			helper.assertTrue(helper.getBlockEntity(TARGET, DepotBlockEntity.class).getHeldItem().isEmpty(),
				"the items were launched as well");
		});
	}

	@GameTest(maxTicks = 160)
	public void ejectorKeepsItemsWhileItsBeltFunnelCannotTakeThem(GameTestHelper helper) {
		ItemStack[] full = new ItemStack[27];
		Arrays.fill(full, new ItemStack(Items.COBBLESTONE, 64));
		buildEjector(helper, true, full);
		helper.runAfterDelay(25, () -> putOnEjector(helper, new ItemStack(Items.GOLD_INGOT, 5)));
		helper.runAfterDelay(100, () -> {
			EjectorBlockEntity ejector = helper.getBlockEntity(EJECTOR, EjectorBlockEntity.class);
			helper.assertTrue(ejector.getDepotBehaviour().getHeldItemStack().is(Items.GOLD_INGOT), "the ejector lost its items");
			helper.assertTrue(helper.getBlockEntity(TARGET, DepotBlockEntity.class).getHeldItem().isEmpty(), "the items were launched");
			helper.assertTrue(ejector.getLaunchedItemCount() == 0, "an item is flying");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 160)
	public void ejectorWithoutAFunnelStillLaunches(GameTestHelper helper) {
		buildEjector(helper, false);
		helper.runAfterDelay(25, () -> putOnEjector(helper, new ItemStack(Items.GOLD_INGOT, 5)));
		helper.succeedWhen(() -> {
			ItemStack held = helper.getBlockEntity(TARGET, DepotBlockEntity.class).getHeldItem();
			helper.assertTrue(held.is(Items.GOLD_INGOT) && held.getCount() == 5, "the target depot holds " + held);
		});
	}

	// ---- 3: item drain with a belt funnel ----

	@GameTest(maxTicks = 120)
	public void itemDrainExportsIntoABeltFunnel(GameTestHelper helper) {
		floor(helper);
		BlockPos drainPos = new BlockPos(2, 1, 2);
		helper.setBlock(drainPos, TanksRegistry.ITEM_DRAIN);
		// items inserted from the north leave through the north side: the funnel faces south and its back is north
		ChestBlockEntity chest = chest(helper, new BlockPos(2, 2, 1));
		helper.setBlock(new BlockPos(2, 2, 2), beltFunnel(false, Direction.SOUTH, Shape.PULLING));
		helper.runAfterDelay(2, () -> {
			ItemDrainBlockEntity drain = helper.getBlockEntity(drainPos, ItemDrainBlockEntity.class);
			ItemStack remainder = drain.getBehaviour(DirectBeltInputBehaviour.TYPE)
				.handleInsertion(new ItemStack(Items.IRON_INGOT, 2), Direction.NORTH, false);
			helper.assertTrue(remainder.isEmpty(), "the drain kept " + remainder);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(countIn(chest, Items.IRON_INGOT) == 2, "the chest holds " + countIn(chest, Items.IRON_INGOT));
			helper.assertTrue(helper.getBlockEntity(drainPos, ItemDrainBlockEntity.class).getHeldItemStack().isEmpty(),
				"the drain still holds its item");
		});
	}

	// ---- 4: funnel -> content observer ----

	private static BlockPos observerNextToFunnel(GameTestHelper helper, ItemStack filter) {
		floor(helper);
		chest(helper, new BlockPos(2, 1, 2), new ItemStack(Items.IRON_INGOT, 4));
		BlockPos observer = new BlockPos(3, 2, 2);
		// the observer looks west, at the funnel
		helper.setBlock(observer, RedstoneRegistry.SMART_OBSERVER.defaultBlockState()
			.setValue(SmartObserverBlock.TARGET, AttachFace.WALL)
			.setValue(SmartObserverBlock.FACING, Direction.WEST));
		helper.runAfterDelay(1, () -> {
			if (!filter.isEmpty())
				helper.getBlockEntity(observer, SmartObserverBlockEntity.class).getBehaviour(FilteringBehaviour.TYPE).setFilter(filter);
			helper.setBlock(new BlockPos(2, 2, 2), floorFunnel(false, true));
		});
		return observer;
	}

	@GameTest(maxTicks = 120)
	public void funnelTransferPowersAContentObserverNextToIt(GameTestHelper helper) {
		BlockPos observer = observerNextToFunnel(helper, ItemStack.EMPTY);
		helper.succeedWhen(() -> helper.assertTrue(helper.getBlockState(observer).getValue(SmartObserverBlock.POWERED),
			"the observer was not powered by the funnel"));
	}

	@GameTest(maxTicks = 100)
	public void contentObserverFilterAppliesToFunnelTransfers(GameTestHelper helper) {
		BlockPos observer = observerNextToFunnel(helper, new ItemStack(Items.DIAMOND));
		helper.runAfterDelay(80, () -> {
			// the funnel has been pushing iron out all this time: nothing matched the diamond filter
			helper.assertTrue(countIn(helper.getBlockEntity(new BlockPos(2, 1, 2), ChestBlockEntity.class), Items.IRON_INGOT) < 4,
				"the funnel did not move anything");
			helper.assertTrue(!helper.getBlockState(observer).getValue(SmartObserverBlock.POWERED), "the observer reacted to iron");
			helper.succeed();
		});
	}

	// ---- 5: basin side output ----

	@GameTest(maxTicks = 80)
	public void wrenchingABasinPicksTheSideItOutputsTo(GameTestHelper helper) {
		floor(helper);
		BlockPos basin = new BlockPos(3, 2, 3);
		BlockPos depot = new BlockPos(4, 1, 3);
		helper.setBlock(basin, ModBlocks.BASIN);
		helper.setBlock(depot, ModBlocks.DEPOT);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(helper.getBlockState(basin).getValue(BasinBlock.FACING) == Direction.DOWN, "a new basin outputs sideways");
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WRENCH));
			BlockPos abs = helper.absolutePos(basin);
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.EAST, abs, false);
			InteractionResult result = ModItems.WRENCH.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
			helper.assertTrue(result == InteractionResult.SUCCESS, "wrench result " + result);
			helper.assertTrue(helper.getBlockState(basin).getValue(BasinBlock.FACING) == Direction.EAST,
				"the basin faces " + helper.getBlockState(basin).getValue(BasinBlock.FACING));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void basinCannotOutputToASideWithoutAnInput(GameTestHelper helper) {
		floor(helper);
		BlockPos basin = new BlockPos(3, 2, 3);
		helper.setBlock(basin, ModBlocks.BASIN);
		helper.setBlock(new BlockPos(4, 1, 3), Blocks.STONE);
		helper.runAfterDelay(2, () -> {
			helper.getBlockEntity(basin, BasinBlockEntity.class).onWrenched(Direction.EAST);
			helper.assertTrue(helper.getBlockState(basin).getValue(BasinBlock.FACING) == Direction.DOWN,
				"the basin faces a stone block");
			helper.assertTrue(!BasinBlock.canOutputTo(helper.getLevel(), helper.absolutePos(basin), Direction.EAST), "canOutputTo");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 120)
	public void basinOutputGoesToTheDepotOnItsSide(GameTestHelper helper) {
		floor(helper);
		BlockPos basin = new BlockPos(3, 2, 3);
		BlockPos depot = new BlockPos(4, 1, 3);
		helper.setBlock(basin, ModBlocks.BASIN);
		helper.setBlock(depot, ModBlocks.DEPOT);
		helper.runAfterDelay(2, () -> {
			BasinBlockEntity be = helper.getBlockEntity(basin, BasinBlockEntity.class);
			be.onWrenched(Direction.EAST);
			helper.assertTrue(helper.getBlockState(basin).getValue(BasinBlock.FACING) == Direction.EAST, "the basin does not face the depot");
			helper.assertTrue(be.acceptOutputs(List.of(new ItemStack(Items.IRON_INGOT, 3)), true), "the output is refused");
			helper.assertTrue(be.outputInventory.isEmpty(), "a simulation changed the output slots");
			helper.assertTrue(be.acceptOutputs(List.of(new ItemStack(Items.IRON_INGOT, 3)), false), "the output is refused");
			helper.assertTrue(be.outputInventory.isEmpty(), "the item went into the basin's own slots");
			helper.assertTrue(!be.canContinueProcessing(), "the basin does not wait for its output to leave");
		});
		helper.succeedWhen(() -> {
			ItemStack held = helper.getBlockEntity(depot, DepotBlockEntity.class).getHeldItem();
			helper.assertTrue(held.is(Items.IRON_INGOT) && held.getCount() == 3, "the depot holds " + held);
			helper.assertTrue(helper.getBlockEntity(basin, BasinBlockEntity.class).canContinueProcessing(), "the buffer was not emptied");
		});
	}

	@GameTest(maxTicks = 80)
	public void rewrenchingTheOutputSideDisablesItAndMovesTheContentsBack(GameTestHelper helper) {
		floor(helper);
		BlockPos basin = new BlockPos(3, 2, 3);
		helper.setBlock(basin, ModBlocks.BASIN);
		helper.setBlock(new BlockPos(4, 1, 3), ModBlocks.DEPOT);
		helper.runAfterDelay(2, () -> {
			BasinBlockEntity be = helper.getBlockEntity(basin, BasinBlockEntity.class);
			be.outputInventory.set(0, new ItemStack(Items.GOLD_INGOT, 2));
			be.onWrenched(Direction.EAST);
			helper.assertTrue(helper.getBlockState(basin).getValue(BasinBlock.FACING) == Direction.EAST, "not facing east");
			helper.assertTrue(be.outputInventory.isEmpty(), "the output slots were not emptied into the new output");
			// wrenching the side it already outputs to turns that output off again
			be.onWrenched(Direction.EAST);
			helper.assertTrue(helper.getBlockState(basin).getValue(BasinBlock.FACING) == Direction.DOWN, "the side was not disabled");
			helper.succeed();
		});
	}

	// ---- 6: assembly operator items ----

	@GameTest(maxTicks = 40)
	public void assemblyOperatorItemsAcceptBeltsAndEjectors(GameTestHelper helper) {
		for (Item item : new Item[] { ModItems.MECHANICAL_PRESS, ModItems.MECHANICAL_MIXER, TanksRegistry.SPOUT_ITEM,
			dev.createpolymer.registry.feature.DeployerRegistry.DEPLOYER_ITEM })
			helper.assertTrue(item instanceof AssemblyOperatorBlockItem, item + " is not an AssemblyOperatorBlockItem");
		helper.assertTrue(!(dev.createpolymer.registry.feature.SawRegistry.MECHANICAL_SAW_ITEM instanceof AssemblyOperatorBlockItem),
			"the saw item is one");
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void pressPlacedOnAnEjectorGoesTwoBlocksUp(GameTestHelper helper) {
		floor(helper);
		BlockPos ejector = new BlockPos(2, 1, 2);
		helper.setBlock(ejector, EjectorRegistry.WEIGHTED_EJECTOR.defaultBlockState().setValue(EjectorBlock.HORIZONTAL_FACING, Direction.EAST));
		helper.runAfterDelay(2, () -> {
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			ItemStack press = new ItemStack(ModItems.MECHANICAL_PRESS);
			player.setItemInHand(InteractionHand.MAIN_HAND, press);
			BlockPos abs = helper.absolutePos(ejector);
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(0, .5, 0), Direction.UP, abs, false);
			InteractionResult result = ((BlockItem) press.getItem()).useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
			helper.assertTrue(result.consumesAction(), "the press was not placed: " + result);
			helper.assertBlockPresent(ModBlocks.MECHANICAL_PRESS, ejector.above(2));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void pressPlacedOnAHorizontalBeltGoesTwoBlocksUp(GameTestHelper helper) {
		floor(helper);
		BlockPos start = new BlockPos(1, 1, 1);
		BlockPos end = new BlockPos(3, 1, 1);
		helper.setBlock(start, ModBlocks.SHAFT.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.Z));
		helper.setBlock(end, ModBlocks.SHAFT.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.Z));
		dev.createpolymer.kinetics.belt.item.BeltConnectorItem.createBelts(helper.getLevel(), helper.absolutePos(start), helper.absolutePos(end));
		BlockPos middle = new BlockPos(2, 1, 1);
		helper.runAfterDelay(2, () -> {
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			ItemStack press = new ItemStack(ModItems.MECHANICAL_PRESS);
			player.setItemInHand(InteractionHand.MAIN_HAND, press);
			BlockPos abs = helper.absolutePos(middle);
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(0, .5, 0), Direction.UP, abs, false);
			InteractionResult result = ((BlockItem) press.getItem()).useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
			helper.assertTrue(result.consumesAction(), "the press was not placed: " + result);
			helper.assertBlockPresent(ModBlocks.MECHANICAL_PRESS, middle.above(2));
			helper.succeed();
		});
	}

	// ---- 7: recipe conversion ----

	@GameTest(maxTicks = 40)
	public void recipesUseVanillaCopperNuggets(GameTestHelper helper) {
		int found = 0;
		for (RecipeHolder<?> holder : helper.getLevel().recipeAccess().getRecipes()) {
			String id = holder.id().identifier().toString();
			if (!id.equals("create:splashing/crushed_raw_copper") && !id.equals("create:crushing/tuff")
				&& !id.equals("create:crushing/veridium"))
				continue;
			helper.assertTrue(holder.value() instanceof ProcessingRecipe<?>, id + " is not a processing recipe");
			boolean nugget = false;
			for (ProcessingOutput output : ((ProcessingRecipe<?>) holder.value()).getRollableResults())
				nugget |= output.getStack().is(Items.COPPER_NUGGET);
			helper.assertTrue(nugget, id + " does not give vanilla copper nuggets");
			found++;
		}
		helper.assertTrue(found == 3, "found " + found + " of the 3 copper nugget recipes");
		helper.succeed();
	}

	// ---- 8: one ProperWaterloggedBlock ----

	@GameTest(maxTicks = 20)
	public void pipesUseTheFoundationWaterloggedInterface(GameTestHelper helper) {
		helper.assertTrue(dev.createpolymer.foundation.block.ProperWaterloggedBlock.class
			.isAssignableFrom(dev.createpolymer.fluids.pipes.SmartFluidPipeBlock.class), "smart pipe");
		helper.assertTrue(dev.createpolymer.foundation.block.ProperWaterloggedBlock.class
			.isAssignableFrom(dev.createpolymer.fluids.pipes.valve.FluidValveBlock.class), "valve");
		helper.succeed();
	}

	// ---- 9: goggle information ----

	private static String plain(List<Component> lines) {
		StringBuilder builder = new StringBuilder();
		for (Component line : lines)
			builder.append(line.getString()).append('\n');
		return builder.toString();
	}

	@GameTest(maxTicks = 40)
	public void tankGoggleTooltipListsTheFluid(GameTestHelper helper) {
		floor(helper);
		BlockPos tank = new BlockPos(2, 1, 2);
		helper.setBlock(tank, TanksRegistry.FLUID_TANK);
		helper.runAfterDelay(2, () -> {
			var be = helper.getBlockEntity(tank, dev.createpolymer.fluids.tank.FluidTankBlockEntity.class);
			List<Component> tooltip = new ArrayList<>();
			helper.assertTrue(be.addToGoggleTooltip(tooltip, false), "no tooltip for an empty tank");
			helper.assertTrue(plain(tooltip).contains("Capacity: 8,000"), "no capacity line: " + plain(tooltip));

			handlerAt(helper, tank).fill(new FluidStack(Fluids.WATER, 500), FluidAction.EXECUTE);
			tooltip.clear();
			helper.assertTrue(be.addToGoggleTooltip(tooltip, false), "no tooltip for a tank with water");
			String text = plain(tooltip);
			helper.assertTrue(text.contains("Fluid Container") && text.contains("Water") && text.contains("500mB"), "tooltip: " + text);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void basinGoggleTooltipListsItemsAndFluids(GameTestHelper helper) {
		floor(helper);
		BlockPos basin = new BlockPos(2, 1, 2);
		helper.setBlock(basin, ModBlocks.BASIN);
		helper.runAfterDelay(2, () -> {
			BasinBlockEntity be = helper.getBlockEntity(basin, BasinBlockEntity.class);
			List<Component> tooltip = new ArrayList<>();
			helper.assertTrue(be.addToGoggleTooltip(tooltip, false), "no tooltip");
			helper.assertTrue(tooltip.isEmpty(), "an empty basin shows " + plain(tooltip));

			be.inputInventory.set(0, new ItemStack(Items.IRON_INGOT, 7));
			handlerAt(helper, basin).fill(new FluidStack(Fluids.WATER, 250), FluidAction.EXECUTE);
			be.addToGoggleTooltip(tooltip, false);
			String text = plain(tooltip);
			helper.assertTrue(text.contains("Basin Contents") && text.contains("Iron Ingot x7") && text.contains("Water 250mB"), "tooltip: " + text);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void gogglePagesPackLinesAndDropIndents(GameTestHelper helper) {
		List<Component> tooltip = new ArrayList<>();
		dev.createpolymer.util.Lang.builder("create").text("Header").forGoggles(tooltip);
		tooltip.add(Component.literal(""));
		dev.createpolymer.util.Lang.builder("create").text("Second line").forGoggles(tooltip, 1);
		List<Component> pages = GogglesInfo.paginate(tooltip);
		helper.assertTrue(pages.size() == 1, "pages: " + pages.size());
		helper.assertTrue(pages.get(0).getString().equals("Header  |  Second line"), "page: '" + pages.get(0).getString() + "'");

		List<Component> long_ = new ArrayList<>();
		for (int i = 0; i < 6; i++)
			dev.createpolymer.util.Lang.builder("create").text("A line that takes up some room " + i).forGoggles(long_);
		helper.assertTrue(GogglesInfo.paginate(long_).size() > 1, "long tooltips are not split");
		helper.succeed();
	}
}
