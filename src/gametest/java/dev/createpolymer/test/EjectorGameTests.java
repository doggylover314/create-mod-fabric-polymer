package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import dev.createpolymer.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.motor.CreativeMotorBlockEntity;
import dev.createpolymer.logistics.depot.DepotBlockEntity;
import dev.createpolymer.logistics.depot.EjectorBlock;
import dev.createpolymer.logistics.depot.EjectorBlockEntity;
import dev.createpolymer.logistics.depot.EjectorBlockEntity.State;
import dev.createpolymer.logistics.depot.EntityLauncher;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.feature.EjectorRegistry;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class EjectorGameTests {
	/** Ejector facing east, a motor driving it from the south and a depot three blocks east of it. */
	private static final BlockPos EJECTOR = new BlockPos(1, 1, 2);
	private static final BlockPos MOTOR = new BlockPos(1, 1, 3);
	private static final BlockPos TARGET = new BlockPos(4, 1, 2);

	private static void build(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(EJECTOR, EjectorRegistry.WEIGHTED_EJECTOR.defaultBlockState()
			.setValue(EjectorBlock.HORIZONTAL_FACING, Direction.EAST));
		helper.setBlock(MOTOR, ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.NORTH));
		helper.setBlock(TARGET, ModBlocks.DEPOT);
		helper.runAfterDelay(1, () -> {
			helper.getBlockEntity(EJECTOR, EjectorBlockEntity.class).setTarget(3, 0);
			// full speed: the plate is back up within a few ticks
			helper.getBlockEntity(MOTOR, CreativeMotorBlockEntity.class).setSetting(256);
		});
	}

	private static EjectorBlockEntity ejector(GameTestHelper helper) {
		return helper.getBlockEntity(EJECTOR, EjectorBlockEntity.class);
	}

	private static ItemStack targetItem(GameTestHelper helper) {
		return helper.getBlockEntity(TARGET, DepotBlockEntity.class).getHeldItem();
	}

	private static IItemHandler handler(GameTestHelper helper) {
		return ejector(helper).getDepotBehaviour().itemHandler;
	}

	private static void putOn(GameTestHelper helper, ItemStack stack) {
		helper.assertTrue(ejector(helper).getState() == State.CHARGED, "the ejector is not charged: " + ejector(helper).getState());
		ItemStack remainder = handler(helper).insertItem(0, stack, false);
		helper.assertTrue(remainder.isEmpty(), "the ejector did not take the items: " + remainder);
	}

	@GameTest(maxTicks = 120)
	public void launchesHeldItemOntoTargetDepot(GameTestHelper helper) {
		build(helper);
		helper.runAfterDelay(25, () -> putOn(helper, new ItemStack(Items.GOLD_INGOT, 5)));
		helper.succeedWhen(() -> {
			ItemStack held = targetItem(helper);
			helper.assertTrue(held.is(Items.GOLD_INGOT) && held.getCount() == 5, "the target depot holds " + held);
			helper.assertTrue(ejector(helper).getDepotBehaviour().getHeldItemStack().isEmpty(), "the ejector still holds its item");
			helper.assertTrue(ejector(helper).getLaunchedItemCount() == 0, "an item is still flying");
		});
	}

	@GameTest(maxTicks = 120)
	public void launcherArcReachesTheTarget(GameTestHelper helper) {
		// Create's trajectory: the arc ends at the target, one block above the ejector's centre at the start
		EntityLauncher launcher = new EntityLauncher(3, 0);
		Vec3 start = Vec3.atCenterOf(BlockPos.ZERO);
		Vec3 end = launcher.getGlobalPos(launcher.getTotalFlyingTicks(), Direction.WEST, BlockPos.ZERO);
		helper.assertTrue(Math.abs(end.x - start.x - 3) < 1e-6 && Math.abs(end.z - start.z) < 1e-6, "the arc ends at " + end.subtract(start));
		helper.assertTrue(Math.abs(end.y - start.y) < 1, "the arc ends at height " + (end.y - start.y));
		helper.assertTrue(launcher.getTotalFlyingTicks() > 5 && launcher.getTotalFlyingTicks() < 40, "flight time " + launcher.getTotalFlyingTicks());
		EntityLauncher up = new EntityLauncher(2, 4);
		Vec3 upEnd = up.getGlobalPos(up.getTotalFlyingTicks(), Direction.WEST, BlockPos.ZERO);
		helper.assertTrue(upEnd.y - start.y > 2 && upEnd.y - start.y < 4, "the arc ends at height " + (upEnd.y - start.y));
		helper.succeed();
	}

	@GameTest(maxTicks = 120)
	public void launchesEntityStandingOnIt(GameTestHelper helper) {
		build(helper);
		double startX = helper.absoluteVec(new Vec3(1.5, 1, 2.5)).x;
		ArmorStand[] stand = new ArmorStand[1];
		helper.runAfterDelay(25, () -> {
			helper.assertTrue(ejector(helper).getState() == State.CHARGED, "the ejector is not charged");
			stand[0] = helper.spawn(EntityTypes.ARMOR_STAND, new Vec3(1.5, 2.3, 2.5));
		});
		boolean[] triggered = { false };
		helper.onEachTick(() -> {
			if (stand[0] != null && ejector(helper).getState() != State.CHARGED)
				triggered[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(stand[0] != null, "nothing was spawned yet");
			helper.assertTrue(triggered[0], "the ejector did not trigger");
			helper.assertTrue(stand[0].getX() > startX + 2.5, "the armor stand is at x " + (stand[0].getX() - startX));
		});
	}

	@GameTest(maxTicks = 120)
	public void launchesPlayerAndOpensTheElytra(GameTestHelper helper) {
		build(helper);
		ServerPlayer[] player = new ServerPlayer[1];
		helper.runAfterDelay(25, () -> {
			helper.assertTrue(ejector(helper).getState() == State.CHARGED, "the ejector is not charged");
			@SuppressWarnings("removal")
			ServerPlayer mock = helper.makeMockServerPlayerInLevel();
			mock.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
			mock.snapTo(helper.absoluteVec(new Vec3(1.5, 1 + 13 / 16d, 2.5)));
			mock.setOnGround(true);
			player[0] = mock;
		});
		Vec3[] launched = new Vec3[1];
		boolean[] gliding = { false };
		helper.onEachTick(() -> {
			if (player[0] == null || launched[0] != null || ejector(helper).getState() == State.CHARGED)
				return;
			launched[0] = player[0].getDeltaMovement();
			gliding[0] = player[0].isFallFlying();
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(launched[0] != null, "the player did not trigger the ejector");
			helper.assertTrue(launched[0].x > .1, "the player was launched with " + launched[0]);
			helper.assertTrue(gliding[0], "the elytra was not opened");
			player[0].connection.disconnect(net.minecraft.network.chat.Component.literal("test over"));
			helper.getLevel().getServer().getPlayerList().remove(player[0]);
		});
	}

	@GameTest(maxTicks = 120)
	public void entityAboveTheEjectorDoesNotTriggerIt(GameTestHelper helper) {
		build(helper);
		helper.runAfterDelay(25, () -> {
			ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, new Vec3(1.5, 4, 2.5));
			stand.setNoGravity(true);
		});
		helper.runAfterDelay(50, () -> {
			helper.assertTrue(ejector(helper).getState() == State.CHARGED, "an entity in the air triggered the ejector");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 160)
	public void poweredEjectorWaitsForTheRedstoneToGo(GameTestHelper helper) {
		build(helper);
		helper.setBlock(new BlockPos(0, 1, 2), Blocks.REDSTONE_BLOCK);
		helper.runAfterDelay(25, () -> {
			helper.assertTrue(ejector(helper).isPowered(), "the ejector does not know it is powered");
			putOn(helper, new ItemStack(Items.IRON_INGOT, 2));
		});
		helper.runAfterDelay(55, () -> {
			helper.assertTrue(ejector(helper).getDepotBehaviour().getHeldItemStack().getCount() == 2, "a powered ejector let go of its item");
			helper.assertTrue(targetItem(helper).isEmpty(), "a powered ejector launched");
			helper.setBlock(new BlockPos(0, 1, 2), Blocks.AIR);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(!ejector(helper).isPowered(), "still powered");
			helper.assertTrue(targetItem(helper).is(Items.IRON_INGOT) && targetItem(helper).getCount() == 2,
				"the target depot holds " + targetItem(helper));
		});
	}

	@GameTest(maxTicks = 160)
	public void waitsForTheConfiguredStackSize(GameTestHelper helper) {
		build(helper);
		helper.runAfterDelay(25, () -> {
			ejector(helper).getBehaviour(ScrollValueBehaviour.TYPE).setValue(3);
			putOn(helper, new ItemStack(Items.COPPER_INGOT, 2));
		});
		helper.runAfterDelay(50, () -> {
			helper.assertTrue(targetItem(helper).isEmpty() && ejector(helper).getState() == State.CHARGED, "launched with too few items");
			handler(helper).insertItem(0, new ItemStack(Items.COPPER_INGOT, 1), false);
		});
		helper.succeedWhen(() -> {
			ItemStack held = targetItem(helper);
			helper.assertTrue(held.is(Items.COPPER_INGOT) && held.getCount() == 3, "the target depot holds " + held);
		});
	}

	@GameTest(maxTicks = 120)
	public void itemFliesAsDisplayWhileInTheAir(GameTestHelper helper) {
		build(helper);
		helper.runAfterDelay(25, () -> putOn(helper, new ItemStack(Items.DIAMOND)));
		boolean[] seenFlying = { false };
		helper.onEachTick(() -> {
			if (ejector(helper).getLaunchedItemCount() == 0)
				return;
			BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(EJECTOR));
			helper.assertTrue(attachment != null, "the ejector has no model");
			int diamonds = 0;
			for (VirtualElement element : attachment.holder().getElements())
				if (element instanceof ItemDisplayElement display && display.getItem().is(Items.DIAMOND))
					diamonds++;
			if (diamonds == 1)
				seenFlying[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(targetItem(helper).is(Items.DIAMOND), "the item has not landed");
			helper.assertTrue(seenFlying[0], "no display followed the flying item");
		});
	}

	@GameTest(maxTicks = 120)
	public void heldItemIsShownOnThePlate(GameTestHelper helper) {
		build(helper);
		helper.runAfterDelay(25, () -> {
			ejector(helper).setTarget(3, 0);
			ejector(helper).getBehaviour(ScrollValueBehaviour.TYPE).setValue(64);
			putOn(helper, new ItemStack(Items.EMERALD));
		});
		helper.runAfterDelay(35, () -> {
			BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(EJECTOR));
			helper.assertTrue(attachment != null, "the ejector has no model");
			int emeralds = 0;
			for (VirtualElement element : attachment.holder().getElements())
				if (element instanceof ItemDisplayElement display && display.getItem().is(Items.EMERALD))
					emeralds++;
			helper.assertTrue(emeralds == 1, "the ejector shows " + emeralds + " emeralds");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 160)
	public void aimedWithTheItemBeforePlacing(GameTestHelper helper) {
		floor(helper);
		BlockPos target = new BlockPos(5, 3, 2);
		helper.setBlock(target, Blocks.STONE);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack ejectorItem = new ItemStack(EjectorRegistry.WEIGHTED_EJECTOR_ITEM, 4);
		player.setItemInHand(InteractionHand.MAIN_HAND, ejectorItem);

		// sneak and use on the target block
		player.setShiftKeyDown(true);
		BlockPos absTarget = helper.absolutePos(target);
		helper.useBlock(target, player, new BlockHitResult(Vec3.atCenterOf(absTarget), Direction.UP, absTarget, false));
		helper.assertTrue(player.getMainHandItem().get(EjectorRegistry.EJECTOR_TARGET) != null
			&& player.getMainHandItem().get(EjectorRegistry.EJECTOR_TARGET).pos().equals(absTarget), "the target was not stored");
		helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(1, 1, 2))) == null, "sneaking placed the ejector");

		// place it: 4 blocks west of the target and 2 below
		player.setShiftKeyDown(false);
		BlockPos floorPos = new BlockPos(1, 0, 2);
		BlockPos abs = helper.absolutePos(floorPos);
		helper.useBlock(floorPos, player, new BlockHitResult(Vec3.atCenterOf(abs).add(0, .5, 0), Direction.UP, abs, false));

		EjectorBlockEntity placed = helper.getBlockEntity(new BlockPos(1, 1, 2), EjectorBlockEntity.class);
		helper.assertTrue(placed.getLauncher().getHorizontalDistance() == 4 && placed.getLauncher().getVerticalDistance() == 2,
			"the ejector aims " + placed.getLauncher().getHorizontalDistance() + "/" + placed.getLauncher().getVerticalDistance());
		helper.assertBlockProperty(new BlockPos(1, 1, 2), EjectorBlock.HORIZONTAL_FACING, Direction.EAST);
		helper.assertTrue(player.getMainHandItem().get(EjectorRegistry.EJECTOR_TARGET) == null, "the target was kept in the item");
		helper.assertTrue(placed.getTargetPosition().equals(absTarget), "the target is " + placed.getTargetPosition());

		// a target that is off to the side is refused and the ejector keeps its default aim
		BlockPos side = new BlockPos(4, 1, 5);
		helper.setBlock(side, Blocks.STONE);
		BlockPos absSide = helper.absolutePos(side);
		player.setShiftKeyDown(true);
		helper.useBlock(side, player, new BlockHitResult(Vec3.atCenterOf(absSide), Direction.UP, absSide, false));
		player.setShiftKeyDown(false);
		BlockPos secondFloor = new BlockPos(1, 0, 6);
		BlockPos absSecond = helper.absolutePos(secondFloor);
		helper.setBlock(secondFloor, Blocks.STONE);
		helper.useBlock(secondFloor, player, new BlockHitResult(Vec3.atCenterOf(absSecond).add(0, .5, 0), Direction.UP, absSecond, false));
		EjectorBlockEntity second = helper.getBlockEntity(new BlockPos(1, 1, 6), EjectorBlockEntity.class);
		helper.assertTrue(second.getLauncher().getHorizontalDistance() == 1 && second.getLauncher().getVerticalDistance() == 0,
			"an invalid target changed the aim to " + second.getLauncher().getHorizontalDistance());
		helper.succeed();
	}

	@GameTest(maxTicks = 120)
	public void observerNextToTheEjectorSeesTheLaunch(GameTestHelper helper) {
		build(helper);
		BlockPos observer = new BlockPos(1, 1, 1);
		helper.setBlock(observer, Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, Direction.SOUTH));
		helper.runAfterDelay(25, () -> putOn(helper, new ItemStack(Items.GOLD_NUGGET)));
		boolean[] seen = { false };
		helper.onEachTick(() -> {
			if (helper.getLevel().getBlockState(helper.absolutePos(observer)).getValue(ObserverBlock.POWERED))
				seen[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(targetItem(helper).is(Items.GOLD_NUGGET), "the item has not landed");
			helper.assertTrue(seen[0], "the observer did not notice the ejector");
		});
	}

	@GameTest(maxTicks = 40)
	public void leftClickingTheTargetDeselectsIt(GameTestHelper helper) {
		floor(helper);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EjectorRegistry.WEIGHTED_EJECTOR_ITEM));
		BlockPos target = new BlockPos(3, 0, 3);
		BlockPos abs = helper.absolutePos(target);
		player.setShiftKeyDown(true);
		helper.useBlock(target, player, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
		helper.assertTrue(player.getMainHandItem().get(EjectorRegistry.EJECTOR_TARGET) != null, "the target was not stored");

		BlockPos other = abs.east();
		AttackBlockCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, other, Direction.UP);
		helper.assertTrue(player.getMainHandItem().get(EjectorRegistry.EJECTOR_TARGET) != null, "another block deselected the target");
		AttackBlockCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, abs, Direction.UP);
		helper.assertTrue(player.getMainHandItem().get(EjectorRegistry.EJECTOR_TARGET) == null, "the target is still selected");
		helper.succeed();
	}

	@GameTest(maxTicks = 120)
	public void stateSurvivesSaving(GameTestHelper helper) {
		build(helper);
		helper.runAfterDelay(25, () -> putOn(helper, new ItemStack(Items.GOLD_INGOT, 3)));
		helper.succeedWhen(() -> {
			EjectorBlockEntity ejector = ejector(helper);
			helper.assertTrue(ejector.getLaunchedItemCount() == 1, "no item in the air yet");
			var registries = helper.getLevel().registryAccess();
			CompoundTag saved = ejector.saveWithFullMetadata(registries);
			BlockEntity loaded = BlockEntity.loadStatic(ejector.getBlockPos(), ejector.getBlockState(), saved, registries);
			helper.assertTrue(loaded instanceof EjectorBlockEntity, "block entity did not load");
			EjectorBlockEntity copy = (EjectorBlockEntity) loaded;
			helper.assertTrue(copy.getLauncher().getHorizontalDistance() == 3 && copy.getLauncher().getVerticalDistance() == 0,
				"the aim was lost");
			helper.assertTrue(copy.getLaunchedItemCount() == 1, "the flying item was lost");
			helper.assertTrue(copy.getState() == ejector.getState(), "the state was lost: " + copy.getState());
		});
	}
}
