package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.logistics.box.PackageItem;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueSettingsInputHandler;
import dev.createpolymer.logistics.depot.DepotBlockEntity;
import dev.createpolymer.logistics.factoryBoard.FactoryPanelBehaviour;
import dev.createpolymer.logistics.factoryBoard.FactoryPanelBlock;
import dev.createpolymer.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import dev.createpolymer.logistics.factoryBoard.FactoryPanelBlockEntity;
import dev.createpolymer.logistics.factoryBoard.FactoryPanelConfigurationPacket;
import dev.createpolymer.logistics.factoryBoard.FactoryPanelConnection;
import dev.createpolymer.logistics.factoryBoard.FactoryPanelConnectionHandler;
import dev.createpolymer.logistics.factoryBoard.FactoryPanelConnectionPacket;
import dev.createpolymer.logistics.factoryBoard.FactoryPanelGui;
import dev.createpolymer.logistics.factoryBoard.FactoryPanelSetItemGui;
import dev.createpolymer.logistics.factoryBoard.FactoryPanelPosition;
import dev.createpolymer.logistics.factoryBoard.FactoryPanelSupportBehaviour;
import dev.createpolymer.logistics.packager.PackagerBlock;
import dev.createpolymer.logistics.packager.PackagerBlockEntity;
import dev.createpolymer.logistics.packagerLink.LogisticallyLinkedBlockItem;
import dev.createpolymer.logistics.packagerLink.LogisticsManager;
import dev.createpolymer.logistics.packagerLink.PackagerLinkBlock;
import dev.createpolymer.logistics.packagerLink.PackagerLinkBlockEntity;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.FactoryGaugeRegistry;
import dev.createpolymer.registry.feature.PackagesRegistry;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.SguiUtils;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class FactoryGaugeGameTests {

	private static final BlockPos CHEST_B = new BlockPos(1, 1, 2);
	private static final BlockPos PACKAGER_B = new BlockPos(2, 1, 2);
	private static final BlockPos CHEST_A = new BlockPos(1, 1, 5);
	private static final BlockPos PACKAGER_A = new BlockPos(2, 1, 5);
	private static final BlockPos RESTOCKER = new BlockPos(2, 1, 4);

	@SuppressWarnings("removal")
	private static ServerPlayer connectedPlayer(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		// the mock player starts at 0, 0, 0 and the connection handler wants it near the gauges (but not in the way:
		// players collide with the barrier a gauge looks like to them)
		player.setPos(helper.absoluteVec(new Vec3(6.5, 1, 6.5)));
		return player;
	}

	private static void leave(GameTestHelper helper, ServerPlayer player) {
		player.connection.disconnect(net.minecraft.network.chat.Component.literal("test over"));
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	private static BlockState gauge(AttachFace face, Direction facing) {
		return FactoryGaugeRegistry.FACTORY_GAUGE.defaultBlockState()
			.setValue(FactoryPanelBlock.FACE, face)
			.setValue(FactoryPanelBlock.FACING, facing);
	}

	private static FactoryPanelBlockEntity gaugeAt(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockEntity(pos, FactoryPanelBlockEntity.class);
	}

	/** Sets up a block with a gauge on the floor and activates (and configures) one of its panels right away. */
	private static FactoryPanelBehaviour panel(GameTestHelper helper, BlockPos pos, PanelSlot slot, UUID network, Item item,
		int count) {
		if (!helper.getBlockState(pos).is(FactoryGaugeRegistry.FACTORY_GAUGE))
			helper.setBlock(pos, gauge(AttachFace.FLOOR, Direction.NORTH));
		FactoryPanelBlockEntity be = gaugeAt(helper, pos);
		helper.assertTrue(be.addPanel(slot, network), "the panel could not be added");
		FactoryPanelBehaviour behaviour = be.panels.get(slot);
		if (item != null)
			behaviour.setFilter(new ItemStack(item));
		behaviour.count = count;
		behaviour.upTo = true;
		return behaviour;
	}

	private static FactoryPanelPosition at(GameTestHelper helper, BlockPos pos, PanelSlot slot) {
		return new FactoryPanelPosition(helper.absolutePos(pos), slot);
	}

	private static ChestBlockEntity chest(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockEntity(pos, ChestBlockEntity.class);
	}

	private static int countIn(IItemHandler handler, Item item) {
		int count = 0;
		for (int i = 0; i < handler.getSlots(); i++)
			if (handler.getStackInSlot(i).is(item))
				count += handler.getStackInSlot(i).getCount();
		return count;
	}

	private static int countIn(GameTestHelper helper, BlockPos chest, Item item) {
		return countIn(ItemHandlerLookup.get(helper.getLevel(), helper.absolutePos(chest), null), item);
	}

	/** A chest, a packager on it and a stock link on the packager. */
	private static void stockNetwork(GameTestHelper helper, BlockPos chest, BlockPos packager) {
		helper.setBlock(chest, Blocks.CHEST);
		helper.setBlock(packager, PackagesRegistry.PACKAGER.defaultBlockState().setValue(PackagerBlock.FACING, Direction.EAST));
		helper.setBlock(packager.above(), PackagesRegistry.STOCK_LINK.defaultBlockState().setValue(PackagerLinkBlock.FACE, AttachFace.FLOOR));
	}

	private static UUID networkOf(GameTestHelper helper, BlockPos packager) {
		return helper.getBlockEntity(packager.above(), PackagerLinkBlockEntity.class).behaviour.freqId;
	}

	/** What the redstone link has for gauges, on any smart block entity. */
	private static FactoryPanelSupportBehaviour attachSupport(GameTestHelper helper, BlockPos pos,
		Supplier<Boolean> isOutput, Supplier<Boolean> power, Runnable onNotify) {
		DepotBlockEntity be = helper.getBlockEntity(pos, DepotBlockEntity.class);
		FactoryPanelSupportBehaviour support = new FactoryPanelSupportBehaviour(be, isOutput, power, onNotify);
		be.attachBehaviourLate(support);
		return support;
	}

	/** The middle of a panel's slot in the world, and a player looking straight at it. */
	private static ServerPlayer lookingAt(GameTestHelper helper, BlockPos gaugePos, PanelSlot slot) {
		BlockState state = helper.getBlockState(gaugePos);
		Vec3 center = helper.absoluteVec(Vec3.atLowerCornerOf(gaugePos).add(slotCenter(state, slot)));
		ServerPlayer player = connectedPlayer(helper);
		// 2 blocks above a gauge on the floor, looking straight down
		player.setPos(center.x, center.y + 2 - player.getEyeHeight(), center.z);
		player.setXRot(90);
		player.setYRot(0);
		return player;
	}

	private static Vec3 slotCenter(BlockState state, PanelSlot slot) {
		// FactoryPanelSlotPositioning is package private: the same maths through the public pieces
		double x = .25 + slot.xOffset * .5, z = .25 + slot.yOffset * .5;
		Vec3 vec = new Vec3(x, 1.5 / 16f, z);
		vec = dev.createpolymer.util.VecHelper.rotateCentered(vec, 180, Direction.Axis.Y);
		vec = dev.createpolymer.util.VecHelper.rotateCentered(vec, Math.toDegrees(FactoryPanelBlock.getXRot(state)) + 90, Direction.Axis.X);
		vec = dev.createpolymer.util.VecHelper.rotateCentered(vec, Math.toDegrees(FactoryPanelBlock.getYRot(state)), Direction.Axis.Y);
		return vec;
	}

	// ---------------------------------------------------------------------------------------------------------

	@GameTest(maxTicks = 40)
	public void panelsKeepTheirSettingsWhenSaved(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		UUID network = UUID.randomUUID();
		FactoryPanelBehaviour output = panel(helper, pos, PanelSlot.BOTTOM_LEFT, network, Items.IRON_INGOT, 16);
		FactoryPanelBehaviour input = panel(helper, pos, PanelSlot.TOP_RIGHT, network, Items.COPPER_INGOT, 0);
		output.addConnection(input.getPanelPosition());
		output.recipeAddress = "Workshop";
		output.recipeOutput = 4;
		output.promiseClearingInterval = 3;
		output.targetedBy.get(input.getPanelPosition()).amount = 5;
		output.targetedBy.get(input.getPanelPosition()).arrowBendMode = 2;
		output.upTo = false;

		helper.runAfterDelay(2, () -> {
			var registries = helper.getLevel().registryAccess();
			FactoryPanelBlockEntity be = gaugeAt(helper, pos);
			BlockEntity loaded = BlockEntity.loadStatic(be.getBlockPos(), be.getBlockState(), be.saveWithFullMetadata(registries), registries);
			helper.assertTrue(loaded instanceof FactoryPanelBlockEntity, "the block entity did not load");
			FactoryPanelBlockEntity copy = (FactoryPanelBlockEntity) loaded;

			helper.assertTrue(copy.activePanels() == 2, "the copy has " + copy.activePanels() + " panels");
			FactoryPanelBehaviour out = copy.panels.get(PanelSlot.BOTTOM_LEFT);
			FactoryPanelBehaviour in = copy.panels.get(PanelSlot.TOP_RIGHT);
			helper.assertTrue(!copy.panels.get(PanelSlot.TOP_LEFT).isActive(), "an unused panel came back");
			helper.assertTrue(out.getFilter().is(Items.IRON_INGOT) && in.getFilter().is(Items.COPPER_INGOT), "the items were lost");
			helper.assertTrue(out.count == 16 && !out.upTo && in.count == 0, "the amounts were lost: " + out.count + " " + out.upTo);
			helper.assertTrue(network.equals(out.network) && network.equals(in.network), "the network was lost");
			helper.assertTrue("Workshop".equals(out.recipeAddress) && out.recipeOutput == 4 && out.promiseClearingInterval == 3,
				"the recipe settings were lost");
			FactoryPanelConnection connection = out.targetedBy.get(in.getPanelPosition());
			helper.assertTrue(connection != null && connection.amount == 5 && connection.arrowBendMode == 2, "the connection was lost");
			helper.assertTrue(in.targeting.contains(out.getPanelPosition()), "the source does not know it is used");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void connectingAndDisconnectingGauges(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		UUID network = UUID.randomUUID();
		FactoryPanelBehaviour output = panel(helper, pos, PanelSlot.BOTTOM_LEFT, network, Items.IRON_INGOT, 16);
		FactoryPanelBehaviour ingredient = panel(helper, pos, PanelSlot.TOP_LEFT, network, Items.RAW_IRON, 0);
		FactoryPanelBehaviour other = panel(helper, pos, PanelSlot.TOP_RIGHT, network, Items.COPPER_INGOT, 0);
		ServerPlayer player = connectedPlayer(helper);

		new FactoryPanelConnectionPacket(ingredient.getPanelPosition(), output.getPanelPosition(), false).applySettings(player);
		new FactoryPanelConnectionPacket(other.getPanelPosition(), output.getPanelPosition(), false).applySettings(player);
		helper.assertTrue(output.targetedBy.size() == 2, "the output has " + output.targetedBy.size() + " inputs");
		helper.assertTrue(ingredient.targeting.contains(output.getPanelPosition()) && other.targeting.contains(output.getPanelPosition()),
			"the inputs do not know their target");

		// the amounts of the inputs and the recipe come with the configuration the GUI builds
		new FactoryPanelConfigurationPacket(output.getPanelPosition(), "Smithy",
			java.util.Map.of(ingredient.getPanelPosition(), 3), List.of(), 2, 5, other.getPanelPosition(), false, false, false)
				.applySettings(player, gaugeAt(helper, pos));
		helper.assertTrue(output.targetedBy.get(ingredient.getPanelPosition()).amount == 3, "the amount did not change");
		helper.assertTrue(!output.targetedBy.containsKey(other.getPanelPosition()) && other.targeting.isEmpty(), "the input was not removed");
		helper.assertTrue("Smithy".equals(output.recipeAddress) && output.recipeOutput == 2 && output.promiseClearingInterval == 5,
			"the settings were not applied");

		// removing a gauge disconnects it
		helper.assertTrue(gaugeAt(helper, pos).removePanel(PanelSlot.TOP_LEFT), "the panel was not removed");
		helper.assertTrue(output.targetedBy.isEmpty(), "the output still has an input");

		// a reset takes the gauge back to a blank panel
		new FactoryPanelConfigurationPacket(output.getPanelPosition(), "", java.util.Map.of(), List.of(), 1, -1, null, false, true, false)
			.applySettings(player, gaugeAt(helper, pos));
		helper.assertTrue(output.getFilter().isEmpty() && output.count == 0 && output.recipeAddress.isEmpty(), "the reset left settings");
		leave(helper, player);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void connectionsNeedMatchingGauges(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		BlockPos wall = new BlockPos(5, 2, 5);
		helper.setBlock(wall.south(), Blocks.STONE);
		UUID network = UUID.randomUUID();
		FactoryPanelBehaviour output = panel(helper, pos, PanelSlot.BOTTOM_LEFT, network, Items.IRON_INGOT, 16);
		FactoryPanelBehaviour ingredient = panel(helper, pos, PanelSlot.TOP_LEFT, network, Items.RAW_IRON, 0);
		helper.setBlock(wall, gauge(AttachFace.WALL, Direction.NORTH));
		helper.assertTrue(gaugeAt(helper, wall).addPanel(PanelSlot.BOTTOM_LEFT, network), "no panel on the wall");
		gaugeAt(helper, wall).panels.get(PanelSlot.BOTTOM_LEFT).setFilter(new ItemStack(Items.GOLD_INGOT));

		// a click on the gauge on the wall: the player looks up, so the ray cannot pick anything and the click location counts
		BlockState wallState = helper.getBlockState(wall);
		Vec3 onThePanel = helper.absoluteVec(Vec3.atLowerCornerOf(wall).add(slotCenter(wallState, PanelSlot.BOTTOM_LEFT)));
		ServerPlayer player = connectedPlayer(helper);
		player.setPos(helper.absoluteVec(new Vec3(5.5, 1, 1.5)));
		player.setXRot(-90);
		FactoryPanelConnectionHandler.startConnection(player, output);
		helper.assertTrue(FactoryPanelConnectionHandler.isConnecting(player), "the selection was not remembered");
		BlockHitResult elsewhere = new BlockHitResult(onThePanel, Direction.NORTH, helper.absolutePos(wall), false);
		InteractionResult result = FactoryPanelConnectionHandler.onRightClick(player, helper.getLevel(), InteractionHand.MAIN_HAND, elsewhere);
		helper.assertTrue(result == InteractionResult.SUCCESS, "the click was not taken: " + result);
		helper.assertTrue(output.targetedBy.isEmpty(), "gauges on different surfaces were connected");
		helper.assertTrue(!FactoryPanelConnectionHandler.isConnecting(player), "the failed connection is still pending");

		// an ingredient on the same surface works, clicked by looking at it
		ServerPlayer looker = lookingAt(helper, pos, PanelSlot.TOP_LEFT);
		FactoryPanelConnectionHandler.startConnection(looker, output);
		BlockHitResult cube = new BlockHitResult(helper.absoluteVec(new Vec3(3.5, 2, 3.5)), Direction.UP, helper.absolutePos(pos), false);
		result = FactoryPanelConnectionHandler.onRightClick(looker, helper.getLevel(), InteractionHand.MAIN_HAND, cube);
		helper.assertTrue(result == InteractionResult.SUCCESS, "the second click was not taken: " + result);
		helper.assertTrue(output.targetedBy.containsKey(ingredient.getPanelPosition()), "the gauge that was looked at was not connected");
		helper.assertTrue(!FactoryPanelConnectionHandler.isConnecting(looker), "the finished connection is still pending");

		// sneaking drops a pending connection
		FactoryPanelConnectionHandler.startConnection(looker, ingredient);
		looker.setShiftKeyDown(true);
		FactoryPanelConnectionHandler.onRightClick(looker, helper.getLevel(), InteractionHand.MAIN_HAND, cube);
		helper.assertTrue(!FactoryPanelConnectionHandler.isConnecting(looker), "sneaking did not drop the connection");
		leave(helper, looker);
		leave(helper, player);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void theRayPicksThePanelTheClientCannotSee(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		UUID network = UUID.randomUUID();
		panel(helper, pos, PanelSlot.BOTTOM_LEFT, network, Items.IRON_INGOT, 16);
		panel(helper, pos, PanelSlot.TOP_RIGHT, network, Items.COPPER_INGOT, 16);
		for (PanelSlot slot : PanelSlot.values()) {
			ServerPlayer player = lookingAt(helper, pos, slot);
			BlockState state = helper.getBlockState(pos);
			BlockPos abs = helper.absolutePos(pos);
			// the client's own hit is on the top of the whole block, which is no use for choosing a slot
			Vec3 clientHit = Vec3.atCenterOf(abs).add(0, .5, 0);
			Vec3 picked = FactoryPanelBlock.pickLocation(player, helper.getLevel(), abs, state, clientHit);
			PanelSlot targeted = FactoryPanelBlock.getTargetedSlot(abs, state, picked);
			helper.assertTrue(targeted == slot, "looking at " + slot + " picked " + targeted);
			leave(helper, player);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void aPlacedGaugeNeedsATunedItem(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		UUID network = UUID.randomUUID();
		ServerPlayer player = connectedPlayer(helper);
		player.setXRot(90);

		ItemStack untuned = new ItemStack(FactoryGaugeRegistry.FACTORY_GAUGE_ITEM);
		player.setItemInHand(InteractionHand.MAIN_HAND, untuned);
		BlockHitResult hit = new BlockHitResult(helper.absoluteVec(new Vec3(3.5, 1, 3.5)), Direction.UP, helper.absolutePos(pos.below()), false);
		InteractionResult denied = untuned.getItem().useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, untuned, hit));
		helper.assertTrue(!denied.consumesAction(), "an untuned gauge was placed: " + denied);
		helper.assertTrue(helper.getBlockState(pos).isAir(), "an untuned gauge left a block");

		ItemStack tuned = new ItemStack(FactoryGaugeRegistry.FACTORY_GAUGE_ITEM);
		LogisticallyLinkedBlockItem.assignFrequency(tuned, player, network);
		player.setItemInHand(InteractionHand.MAIN_HAND, tuned);
		InteractionResult placed = tuned.getItem().useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, tuned, hit));
		helper.assertTrue(placed.consumesAction(), "the tuned gauge was not placed: " + placed);
		leave(helper, player);

		helper.runAfterDelay(3, () -> {
			helper.assertTrue(helper.getBlockState(pos).is(FactoryGaugeRegistry.FACTORY_GAUGE), "there is no gauge");
			FactoryPanelBlockEntity be = gaugeAt(helper, pos);
			helper.assertTrue(be.activePanels() == 1, "the gauge has " + be.activePanels() + " panels");
			for (FactoryPanelBehaviour behaviour : be.panels.values())
				if (behaviour.isActive())
					helper.assertTrue(network.equals(behaviour.network), "the panel is on network " + behaviour.network);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void breakingAGaugeDropsEveryPanel(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		UUID network = UUID.randomUUID();
		panel(helper, pos, PanelSlot.BOTTOM_LEFT, network, Items.IRON_INGOT, 16);
		panel(helper, pos, PanelSlot.TOP_RIGHT, network, null, 0);
		panel(helper, pos, PanelSlot.TOP_LEFT, network, null, 0);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(gaugeAt(helper, pos).activePanels() == 3, "not all panels survived the first ticks");
			helper.setBlock(pos, Blocks.AIR);
		});
		helper.runAfterDelay(8, () -> {
			int found = 0;
			for (ItemEntity entity : helper.getEntities(net.minecraft.world.entity.EntityTypes.ITEM, pos, 3))
				if (entity.getItem().is(FactoryGaugeRegistry.FACTORY_GAUGE_ITEM))
					found += entity.getItem().getCount();
			// the block's own loot is one more; the others come from the block entity
			helper.assertTrue(found == 2, "dropped " + found + " gauges");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void aGaugeWithoutPanelsDisappears(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		helper.setBlock(pos, gauge(AttachFace.FLOOR, Direction.SOUTH));
		helper.succeedWhen(() -> helper.assertTrue(helper.getBlockState(pos).isAir(), "the empty gauge is still there"));
	}

	@GameTest(maxTicks = 40)
	public void wrenchBendsTheArrows(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		UUID network = UUID.randomUUID();
		FactoryPanelBehaviour output = panel(helper, pos, PanelSlot.BOTTOM_LEFT, network, Items.IRON_INGOT, 16);
		FactoryPanelBehaviour ingredient = panel(helper, pos, PanelSlot.TOP_RIGHT, network, Items.RAW_IRON, 0);
		output.addConnection(ingredient.getPanelPosition());
		ServerPlayer player = connectedPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WRENCH));
		helper.assertTrue(ingredient.bypassesInput(player.getMainHandItem()), "the wrench does not skip the gauge's own GUI");
		FactoryPanelBehaviour lonely = panel(helper, pos, PanelSlot.TOP_LEFT, network, null, 0);
		lonely.onShortInteract(player, InteractionHand.MAIN_HAND, Direction.UP,
			new BlockHitResult(helper.absoluteVec(new Vec3(3.5, 2, 3.5)), Direction.UP, helper.absolutePos(pos), false));
		helper.assertTrue(lonely.getFilter().isEmpty(), "the wrench was set as the item of a gauge");
		BlockHitResult hit = new BlockHitResult(helper.absoluteVec(new Vec3(3.5, 2, 3.5)), Direction.UP, helper.absolutePos(pos), false);
		FactoryPanelConnection connection = output.targetedBy.get(ingredient.getPanelPosition());
		helper.assertTrue(connection.arrowBendMode == -1, "the arrows start bent in mode " + connection.arrowBendMode);
		ingredient.onShortInteract(player, InteractionHand.MAIN_HAND, Direction.UP, hit);
		helper.assertTrue(connection.arrowBendMode == 0, "the wrench set mode " + connection.arrowBendMode);
		ingredient.onShortInteract(player, InteractionHand.MAIN_HAND, Direction.UP, hit);
		helper.assertTrue(connection.arrowBendMode == 1, "the wrench set mode " + connection.arrowBendMode);
		helper.assertTrue(!connection.getPath(helper.getLevel(), helper.getBlockState(pos), output.getPanelPosition()).isEmpty(),
			"the bent arrow has no path");
		leave(helper, player);
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void gaugeShowsItsPanelsItemAndStockLevel(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		UUID network = UUID.randomUUID();
		FactoryPanelBehaviour output = panel(helper, pos, PanelSlot.BOTTOM_LEFT, network, Items.IRON_INGOT, 16);
		FactoryPanelBehaviour ingredient = panel(helper, pos, PanelSlot.TOP_RIGHT, network, Items.RAW_IRON, 0);
		output.addConnection(ingredient.getPanelPosition());
		helper.runAfterDelay(8, () -> {
			BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(pos));
			helper.assertTrue(attachment != null, "the gauge has no model");
			int iron = 0, rawIron = 0, models = 0, others = 0;
			boolean stockLevel = false;
			for (VirtualElement element : attachment.holder().getElements()) {
				if (element instanceof ItemDisplayElement display) {
					if (display.getItem().is(Items.IRON_INGOT))
						iron++;
					else if (display.getItem().is(Items.RAW_IRON))
						rawIron++;
					else if (display.getItem().is(Items.PAPER))
						models++;
					else if (!display.getItem().isEmpty())
						others++;
				} else if (element instanceof TextDisplayElement text && text.getText().getString().contains("16")) {
					stockLevel = true;
				}
			}
			helper.assertTrue(iron == 1 && rawIron == 1, "the items are shown " + iron + " and " + rawIron + " times");
			// two panels, the bulb of the one with a target amount and at least one arrow piece
			helper.assertTrue(models >= 4, "only " + models + " model parts are shown");
			helper.assertTrue(others == 0, "unexpected items are shown");
			helper.assertTrue(stockLevel, "the stock level is not written on the gauge");

			// taking the panel away takes its display away
			gaugeAt(helper, pos).removePanel(PanelSlot.BOTTOM_LEFT);
			helper.runAfterDelay(4, () -> {
				int ironLeft = 0;
				for (VirtualElement element : attachment.holder().getElements())
					if (element instanceof ItemDisplayElement display && display.getItem().is(Items.IRON_INGOT))
						ironLeft++;
				helper.assertTrue(ironLeft == 0, "the removed panel still shows its item");
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = 300)
	public void gaugeSatisfiedByTheNetworkPowersItsLinks(GameTestHelper helper) {
		floor(helper);
		stockNetwork(helper, CHEST_B, PACKAGER_B);
		BlockPos gaugePos = new BlockPos(5, 1, 5);
		BlockPos depotOut = new BlockPos(6, 1, 5);
		BlockPos depotIn = new BlockPos(6, 1, 3);
		helper.setBlock(depotOut, ModBlocks.DEPOT);
		helper.setBlock(depotIn, ModBlocks.DEPOT);
		boolean[] power = new boolean[1];
		int[] notified = new int[1];
		FactoryPanelSupportBehaviour[] outLink = new FactoryPanelSupportBehaviour[1];
		FactoryPanelSupportBehaviour[] inLink = new FactoryPanelSupportBehaviour[1];
		FactoryPanelBehaviour[] gauge = new FactoryPanelBehaviour[1];
		int[] stage = new int[1];

		helper.runAfterDelay(1, () -> {
			chest(helper, CHEST_B).setItem(0, new ItemStack(Items.IRON_INGOT, 20));
			UUID network = networkOf(helper, PACKAGER_B);
			gauge[0] = panel(helper, gaugePos, PanelSlot.BOTTOM_LEFT, network, Items.IRON_INGOT, 16);

			// stand-ins for the redstone link: one that wants to know about the gauge, one that powers it
			outLink[0] = attachSupport(helper, depotOut, () -> false, () -> false, () -> notified[0]++);
			inLink[0] = attachSupport(helper, depotIn, () -> true, () -> power[0], () -> {
			});
			gauge[0].addConnection(at(helper, depotOut, PanelSlot.BOTTOM_LEFT));
			helper.assertTrue(gauge[0].targetedByLinks.size() == 1, "the link was not connected");
			helper.assertTrue(outLink[0].getLinkedPanels().contains(gauge[0].getPanelPosition()), "the link does not know the gauge");
		});

		helper.succeedWhen(() -> {
			if (gauge[0] == null)
				helper.assertTrue(false, "not set up yet");
			if (stage[0] == 0) {
				helper.assertTrue(gauge[0].satisfied, "the gauge is not satisfied by 20 iron, it sees " + gauge[0].getLevelInStorage());
				helper.assertTrue(Boolean.TRUE.equals(outLink[0].shouldBePoweredTristate()), "the link is not told to power");
				helper.assertTrue(notified[0] > 0, "the link was not notified");
				helper.assertTrue(!gauge[0].redstonePowered, "the gauge is powered");
				// the stock runs out: the output stops, and a powered input pauses the gauge
				chest(helper, CHEST_B).setItem(0, ItemStack.EMPTY);
				LogisticsManager.SUMMARIES.invalidate(gauge[0].network);
				gauge[0].addConnection(at(helper, depotIn, PanelSlot.BOTTOM_LEFT));
				helper.assertTrue(gauge[0].targetedByLinks.size() == 2, "the second link was not connected");
				power[0] = true;
				inLink[0].notifyPanels();
				stage[0] = 1;
			}
			helper.assertTrue(!gauge[0].satisfied, "the gauge is still satisfied without iron");
			helper.assertTrue(Boolean.FALSE.equals(outLink[0].shouldBePoweredTristate()), "the link is still told to power");
			helper.assertTrue(gauge[0].redstonePowered, "the redstone input did not power the gauge");
			power[0] = false;
			inLink[0].notifyPanels();
			helper.assertTrue(!gauge[0].redstonePowered, "the gauge stays powered");
		});
	}

	@GameTest(maxTicks = 400)
	public void restockerGaugeOrdersWhatIsMissingAndWaitsForIt(GameTestHelper helper) {
		floor(helper);
		stockNetwork(helper, CHEST_B, PACKAGER_B);
		helper.setBlock(CHEST_A, Blocks.CHEST);
		helper.setBlock(PACKAGER_A, PackagesRegistry.PACKAGER.defaultBlockState().setValue(PackagerBlock.FACING, Direction.EAST));
		helper.setBlock(RESTOCKER, gauge(AttachFace.WALL, Direction.NORTH));
		FactoryPanelBehaviour[] gauge = new FactoryPanelBehaviour[1];
		ItemStack[] box = new ItemStack[1];
		int[] promisedWhenSent = new int[1];

		// a gauge block without panels removes itself, so the panel comes right away and the network once the link has one
		helper.assertTrue(gaugeAt(helper, RESTOCKER).addPanel(PanelSlot.BOTTOM_LEFT, null), "the panel was not added");
		helper.runAfterDelay(1, () -> {
			chest(helper, CHEST_B).setItem(0, new ItemStack(Items.IRON_INGOT, 100));
			chest(helper, CHEST_A).setItem(0, new ItemStack(Items.IRON_INGOT, 4));
			gauge[0] = gaugeAt(helper, RESTOCKER).panels.get(PanelSlot.BOTTOM_LEFT);
			gauge[0].setNetwork(networkOf(helper, PACKAGER_B));
			gauge[0].setFilter(new ItemStack(Items.IRON_INGOT));
			gauge[0].count = 16;
			gauge[0].upTo = true;
			gauge[0].recipeAddress = "Shop";
		});

		helper.succeedWhen(() -> {
			if (gauge[0] == null)
				helper.assertTrue(false, "not set up yet");
			helper.assertTrue(gaugeAt(helper, RESTOCKER).restocker, "the gauge on the packager is no restocker");
			PackagerBlockEntity supplier = helper.getBlockEntity(PACKAGER_B, PackagerBlockEntity.class);
			if (box[0] == null) {
				helper.assertTrue(PackageItem.isPackage(supplier.heldBox), "the supplying packager holds no package");
				// the package leaves the packager soon, so take it when it shows and judge it afterwards
				box[0] = supplier.heldBox.copy();
				supplier.heldBox = ItemStack.EMPTY;
				promisedWhenSent[0] = gauge[0].getPromised();
				// the package arrives at the restocked packager, which unpacks it into its chest
				PackagerBlockEntity restocked = helper.getBlockEntity(PACKAGER_A, PackagerBlockEntity.class);
				helper.assertTrue(restocked.inventory.insertItem(0, box[0], false).isEmpty(), "the packager did not take the package");
			}
			helper.assertTrue(PackageItem.getAddress(box[0]).equals("Shop"), "the package is addressed to '" + PackageItem.getAddress(box[0]) + "'");
			int packed = countIn(PackageItem.getContents(box[0]), Items.IRON_INGOT);
			helper.assertTrue(packed == 12, "the package holds " + packed + " iron, 12 are missing");
			helper.assertTrue(promisedWhenSent[0] == 12, "the gauge promised " + promisedWhenSent[0] + " when it sent the order");
			helper.assertTrue(countIn(helper, CHEST_A, Items.IRON_INGOT) == 16, "the chest has " + countIn(helper, CHEST_A, Items.IRON_INGOT) + " iron");
			helper.assertTrue(gauge[0].satisfied, "the gauge is not satisfied after the delivery");
			helper.assertTrue(gauge[0].getPromised() == 0, "the promise stays after the delivery: " + gauge[0].getPromised());
		});
	}

	@GameTest(maxTicks = 60)
	public void gaugeOnlyBecomesRestockerOnPackagers(GameTestHelper helper) {
		floor(helper);
		BlockPos onStone = new BlockPos(5, 1, 1);
		helper.setBlock(PACKAGER_A, PackagesRegistry.PACKAGER.defaultBlockState().setValue(PackagerBlock.FACING, Direction.EAST));
		helper.setBlock(RESTOCKER, gauge(AttachFace.WALL, Direction.NORTH));
		helper.assertTrue(gaugeAt(helper, RESTOCKER).addPanel(PanelSlot.TOP_LEFT, UUID.randomUUID()), "no panel on the packager");
		panel(helper, onStone, PanelSlot.TOP_LEFT, UUID.randomUUID(), Items.IRON_INGOT, 4);
		helper.runAfterDelay(4, () -> {
			helper.assertTrue(gaugeAt(helper, RESTOCKER).restocker, "the gauge on the packager is no restocker");
			helper.assertTrue(gaugeAt(helper, RESTOCKER).getRestockedPackager() != null, "the restocked packager is not found");
			helper.assertTrue(!gaugeAt(helper, onStone).restocker, "the gauge on stone is a restocker");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void clickingAGaugeSetsItsItemOrOpensItsScreens(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		UUID network = UUID.randomUUID();
		FactoryPanelBehaviour panel = panel(helper, pos, PanelSlot.BOTTOM_LEFT, network, null, 0);
		ServerPlayer player = lookingAt(helper, pos, PanelSlot.BOTTOM_LEFT);
		BlockHitResult cube = new BlockHitResult(helper.absoluteVec(new Vec3(3.5, 2, 3.5)), Direction.UP, helper.absolutePos(pos), false);
		var level = helper.getLevel();

		// no item yet: an empty hand asks for one
		helper.assertTrue(ValueSettingsInputHandler.onBlockActivated(player, level, InteractionHand.MAIN_HAND, cube) == InteractionResult.SUCCESS,
			"the click was not handled");
		helper.assertTrue(SguiUtils.getCurrentGui(player) instanceof FactoryPanelSetItemGui, "the item screen did not open: " + SguiUtils.getCurrentGui(player));
		FactoryPanelSetItemGui ghost = (FactoryPanelSetItemGui) SguiUtils.getCurrentGui(player);
		// the ghost slot takes what is on the cursor, and the gauge has it at once; an empty cursor clears it
		player.containerMenu.setCarried(new ItemStack(Items.GOLD_INGOT, 5));
		ghost.onClick(0, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, ghost.getGuiElement(0));
		helper.assertTrue(panel.getFilter().is(Items.GOLD_INGOT), "the ghost item was not set: " + panel.getFilter());
		player.containerMenu.setCarried(ItemStack.EMPTY);
		ghost.onClick(0, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, ghost.getGuiElement(0));
		helper.assertTrue(panel.getFilter().isEmpty(), "the ghost item was not cleared: " + panel.getFilter());
		ghost.close();

		// holding an item sets it
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_INGOT, 3));
		ValueSettingsInputHandler.onBlockActivated(player, level, InteractionHand.MAIN_HAND, cube);
		helper.assertTrue(panel.getFilter().is(Items.IRON_INGOT), "the held item was not set: " + panel.getFilter());
		helper.assertTrue(player.getMainHandItem().getCount() == 3, "setting the item used up the held item");

		// with an item set an empty hand opens the configuration
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		ValueSettingsInputHandler.onBlockActivated(player, level, InteractionHand.MAIN_HAND, cube);
		helper.assertTrue(SguiUtils.getCurrentGui(player) instanceof FactoryPanelGui, "the configuration did not open: " + SguiUtils.getCurrentGui(player));
		SguiUtils.getCurrentGui(player).close();

		// a tuned gauge item in hand adopts the network of the panel
		ItemStack item = new ItemStack(FactoryGaugeRegistry.FACTORY_GAUGE_ITEM);
		player.setItemInHand(InteractionHand.MAIN_HAND, item);
		ValueSettingsInputHandler.onBlockActivated(player, level, InteractionHand.MAIN_HAND, cube);
		helper.assertTrue(network.equals(LogisticallyLinkedBlockItem.networkFromStack(item)), "the item was not tuned to the panel's network");
		leave(helper, player);
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void gaugeScreenAdjustsAmountsAndFindsCraftingRecipes(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		UUID network = UUID.randomUUID();
		FactoryPanelBehaviour output = panel(helper, pos, PanelSlot.BOTTOM_LEFT, network, Items.IRON_BLOCK, 0);
		FactoryPanelBehaviour ingredient = panel(helper, pos, PanelSlot.TOP_RIGHT, network, Items.IRON_INGOT, 0);
		output.addConnection(ingredient.getPanelPosition());
		ServerPlayer player = connectedPlayer(helper);

		FactoryPanelGui gui = new FactoryPanelGui(player, output);
		gui.open();
		// slot 9 offers the crafting table because iron ingots make iron blocks
		var craft = gui.getGuiElement(9);
		helper.assertTrue(craft != null && craft.getItemStack().is(Items.CRAFTING_TABLE), "no crafting button: " + (craft == null ? null : craft.getItemStack()));
		craft.getGuiCallback().click(9, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, gui);
		helper.assertTrue(output.activeCraftingArrangement.size() == 9, "the arrangement has " + output.activeCraftingArrangement.size() + " slots");
		long ingots = output.activeCraftingArrangement.stream().filter(i -> i.is(Items.IRON_INGOT)).count();
		helper.assertTrue(ingots == 9, "the arrangement has " + ingots + " iron ingots");
		helper.assertTrue(output.targetedBy.get(ingredient.getPanelPosition()).amount == 9, "the recipe needs 9 ingots, the connection has "
			+ output.targetedBy.get(ingredient.getPanelPosition()).amount);

		// the target amount: +10 on the '+10' button, in stacks after the unit button
		var plus10 = gui.getGuiElement(41);
		plus10.getGuiCallback().click(41, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, gui);
		helper.assertTrue(output.count == 10 && output.upTo, "the amount is " + output.count);
		gui.getGuiElement(36).getGuiCallback().click(36, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, gui);
		helper.assertTrue(output.count == 10 && !output.upTo, "the unit did not change");

		gui.close();

		// an address typed in the anvil goes straight onto the panel; a screen opened afterwards keeps it
		output.recipeAddress = "Smithy";
		FactoryPanelGui again = new FactoryPanelGui(player, output);
		again.open();
		again.close();
		helper.assertTrue("Smithy".equals(output.recipeAddress), "closing the screen lost the address");
		helper.assertTrue(output.count == 10 && !output.upTo && output.activeCraftingArrangement.size() == 9,
			"closing the screen changed the settings");

		// the stock keeper's crafting patterns use the same layout
		var recipe = helper.getLevel().recipeAccess().getRecipes().stream()
			.filter(r -> r.value() instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped
				&& shaped.assemble(net.minecraft.world.item.crafting.CraftingInput.EMPTY).is(Items.IRON_BLOCK))
			.findFirst().orElseThrow();
		var layout = FactoryPanelGui.convertRecipeToPackageOrderContext(
			(net.minecraft.world.item.crafting.CraftingRecipe) recipe.value(),
			List.of(new dev.createpolymer.logistics.BigItemStack(new ItemStack(Items.IRON_INGOT), 9)), true);
		helper.assertTrue(layout.size() == 9 && layout.stream().allMatch(b -> b.stack.is(Items.IRON_INGOT)), "the layout of iron blocks is wrong");
		leave(helper, player);
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void aSecondPanelGoesWhereThePlayerLooks(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		UUID network = UUID.randomUUID();
		FactoryPanelBehaviour first = panel(helper, pos, PanelSlot.BOTTOM_LEFT, network, Items.IRON_INGOT, 1);
		ServerPlayer player = lookingAt(helper, pos, PanelSlot.TOP_RIGHT);
		BlockHitResult cube = new BlockHitResult(helper.absoluteVec(new Vec3(3.5, 2, 3.5)), Direction.UP, helper.absolutePos(pos), false);

		ItemStack item = new ItemStack(FactoryGaugeRegistry.FACTORY_GAUGE_ITEM, 2);
		LogisticallyLinkedBlockItem.assignFrequency(item, player, network);
		player.setItemInHand(InteractionHand.MAIN_HAND, item);
		// the item is used on the free slot, the gauge's own handling takes it
		InteractionResult result = helper.getBlockState(pos).useItemOn(item, helper.getLevel(), player, InteractionHand.MAIN_HAND, cube);
		helper.assertTrue(result == InteractionResult.SUCCESS, "the gauge was not added: " + result);
		FactoryPanelBlockEntity be = gaugeAt(helper, pos);
		helper.assertTrue(be.panels.get(PanelSlot.TOP_RIGHT).isActive() && !be.panels.get(PanelSlot.TOP_LEFT).isActive()
			&& !be.panels.get(PanelSlot.BOTTOM_RIGHT).isActive(), "the panel went to the wrong slot");
		helper.assertTrue(network.equals(be.panels.get(PanelSlot.TOP_RIGHT).network), "the new panel is on another network");
		helper.assertTrue(first.isActive() && be.activePanels() == 2, "the first panel was disturbed");
		helper.assertTrue(item.getCount() == 1 || player.isCreative(), "the item was not used up: " + item.getCount());

		// the same slot is taken now: the block cannot be replaced there
		helper.assertTrue(!helper.getBlockState(pos).canBeReplaced(new net.minecraft.world.item.context.BlockPlaceContext(
			helper.getLevel(), player, InteractionHand.MAIN_HAND, item, cube)), "an occupied slot can be replaced");

		// breaking the block takes away the panel that is looked at, until one is left
		ServerPlayer looker = lookingAt(helper, pos, PanelSlot.TOP_RIGHT);
		FactoryPanelBlock block = (FactoryPanelBlock) helper.getBlockState(pos).getBlock();
		helper.assertTrue(block.tryDestroySubPanelFirst(helper.getBlockState(pos), helper.getLevel(), helper.absolutePos(pos), looker),
			"the looked-at panel was not broken first");
		helper.assertTrue(!be.panels.get(PanelSlot.TOP_RIGHT).isActive() && be.panels.get(PanelSlot.BOTTOM_LEFT).isActive(),
			"the wrong panel was broken");
		helper.assertTrue(!block.tryDestroySubPanelFirst(helper.getBlockState(pos), helper.getLevel(), helper.absolutePos(pos), looker),
			"the last panel was broken on its own");
		leave(helper, looker);
		leave(helper, player);
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void sneakingWithAWrenchPicksUpOnePanel(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		UUID network = UUID.randomUUID();
		panel(helper, pos, PanelSlot.BOTTOM_LEFT, network, Items.IRON_INGOT, 1);
		panel(helper, pos, PanelSlot.TOP_RIGHT, network, Items.COPPER_INGOT, 1);
		ServerPlayer player = lookingAt(helper, pos, PanelSlot.TOP_RIGHT);
		player.setShiftKeyDown(true);
		ItemStack wrench = new ItemStack(ModItems.WRENCH);
		player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
		BlockHitResult cube = new BlockHitResult(helper.absoluteVec(new Vec3(3.5, 2, 3.5)), Direction.UP, helper.absolutePos(pos), false);
		InteractionResult result = wrench.getItem().useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, wrench, cube));
		helper.assertTrue(result.consumesAction(), "the wrench did nothing: " + result);
		FactoryPanelBlockEntity be = gaugeAt(helper, pos);
		helper.assertTrue(!be.panels.get(PanelSlot.TOP_RIGHT).isActive() && be.panels.get(PanelSlot.BOTTOM_LEFT).isActive(),
			"the wrench took the wrong panel");
		leave(helper, player);
		helper.succeed();
	}

	@GameTest
	public void modelRotationAgreesWithCreatesVertexMaths(GameTestHelper helper) {
		Vec3[] points = {new Vec3(.1, .2, .3), new Vec3(.5, 0, .5), new Vec3(.9, .05, .05), new Vec3(0, 0, 1)};
		for (AttachFace face : AttachFace.values())
			for (Direction facing : Direction.Plane.HORIZONTAL) {
				BlockState state = gauge(face, facing);
				var rotation = dev.createpolymer.logistics.factoryBoard.FactoryPanelModel.rotationOf(state);
				float xRot = (float) Math.toDegrees(FactoryPanelBlock.getXRot(state));
				float yRot = (float) Math.toDegrees(FactoryPanelBlock.getYRot(state));
				for (PanelSlot slot : PanelSlot.values())
					for (Vec3 p : points) {
						// Create: FactoryPanelModel.addPanel
						Vec3 expected = p.add(slot.xOffset * .5, 0, slot.yOffset * .5);
						expected = dev.createpolymer.util.VecHelper.rotateCentered(expected, 180, Direction.Axis.Y);
						expected = dev.createpolymer.util.VecHelper.rotateCentered(expected, xRot + 90, Direction.Axis.X);
						expected = dev.createpolymer.util.VecHelper.rotateCentered(expected, yRot, Direction.Axis.Y);
						// the display model: centred on the block
						var v = rotation.transformPosition(new org.joml.Vector3f((float) p.x - .5f + slot.xOffset * .5f, (float) p.y - .5f,
							(float) p.z - .5f + slot.yOffset * .5f));
						double error = expected.distanceTo(new Vec3(v.x + .5, v.y + .5, v.z + .5));
						helper.assertTrue(error < 1e-3, "the display of " + face + " " + facing + " " + slot + " is " + error + " off");
					}
			}
		helper.succeed();
	}
}
