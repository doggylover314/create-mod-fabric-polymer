package dev.createpolymer.test;

import java.util.List;

import com.mojang.serialization.DataResult;

import dev.createpolymer.contraptions.Contraption;
import dev.createpolymer.contraptions.OrientedContraptionEntity;
import dev.createpolymer.contraptions.glue.SuperGlueEntity;
import dev.createpolymer.contraptions.minecart.CouplingHandler;
import dev.createpolymer.contraptions.minecart.MinecartCouplingItem;
import dev.createpolymer.contraptions.minecart.FurnaceCartFuel;
import dev.createpolymer.contraptions.minecart.capability.CapabilityMinecartController;
import dev.createpolymer.contraptions.minecart.capability.MinecartController;
import dev.createpolymer.contraptions.mounted.CartAssembleRailType;
import dev.createpolymer.contraptions.mounted.CartAssemblerBlock;
import dev.createpolymer.contraptions.mounted.CartAssemblerBlockEntity;
import dev.createpolymer.contraptions.mounted.CartAssemblerBlockEntity.CartMovementMode;
import dev.createpolymer.contraptions.mounted.MinecartContraptionItem;
import dev.createpolymer.contraptions.mounted.MountedContraption;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.visual.BlockstateModels;
import dev.createpolymer.registry.feature.MinecartRegistry;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class MinecartGameTests {

	/**
	 * The track runs along +X at y = 1 and z = 2, from x = 0 to x = 6, and ends at a wall at x = 7: the test area
	 * is 8 blocks wide, and the tests around it share the world.
	 */
	private static final int Z = 2;
	private static final int LAST_RAIL = 6;

	private static void track(GameTestHelper helper) {
		for (int x = 0; x <= LAST_RAIL; x++) {
			helper.setBlock(new BlockPos(x, 0, Z), Blocks.STONE);
			helper.setBlock(new BlockPos(x, 1, Z),
				Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.EAST_WEST));
		}
		for (int y = 0; y <= 3; y++)
			helper.setBlock(new BlockPos(LAST_RAIL + 1, y, Z), Blocks.STONE);
	}

	private static BlockPos railPos(int x) {
		return new BlockPos(x, 1, Z);
	}

	/** Replaces the rail at x with an assembler; a redstone block under it powers it. */
	private static void assembler(GameTestHelper helper, int x, CartAssembleRailType type, boolean powered) {
		if (powered)
			helper.setBlock(new BlockPos(x, 0, Z), Blocks.REDSTONE_BLOCK);
		helper.setBlock(railPos(x), MinecartRegistry.CART_ASSEMBLER.defaultBlockState()
			.setValue(CartAssemblerBlock.RAIL_SHAPE, RailShape.EAST_WEST)
			.setValue(CartAssemblerBlock.RAIL_TYPE, type));
	}

	private static <T extends AbstractMinecart> T cart(GameTestHelper helper, net.minecraft.world.entity.EntityType<T> type,
		double x) {
		return helper.spawn(type, new Vec3(x, 1.0625, Z + 0.5));
	}

	private static ItemStack find(Player player, net.minecraft.world.item.Item item) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++)
			if (player.getInventory().getItem(i).is(item))
				return player.getInventory().getItem(i);
		return ItemStack.EMPTY;
	}

	private static List<OrientedContraptionEntity> contraptions(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(OrientedContraptionEntity.class, helper.getBounds().inflate(1),
			Entity::isAlive);
	}

	private static double relativeX(GameTestHelper helper, net.minecraft.world.entity.Entity entity) {
		return entity.getX() - helper.absolutePos(BlockPos.ZERO).getX();
	}

	@GameTest(maxTicks = 200)
	public void poweredAssemblerPicksUpTheBlocksAboveAndCarriesThem(GameTestHelper helper) {
		track(helper);
		assembler(helper, 2, CartAssembleRailType.REGULAR, true);
		helper.setBlock(new BlockPos(2, 2, Z), Blocks.STONE);
		Minecart cart = cart(helper, EntityTypes.MINECART, 0.5);
		cart.setDeltaMovement(0.3, 0, 0);
		helper.runAfterDelay(2, () -> helper.assertTrue(helper.getBlockState(railPos(2)).getValue(CartAssemblerBlock.POWERED),
			"the assembler is not powered by the redstone block below"));
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 2, Z)).isAir(), "the stone was not picked up");
			List<OrientedContraptionEntity> list = contraptions(helper);
			helper.assertTrue(list.size() == 1, "contraption entities: " + list.size());
			OrientedContraptionEntity entity = list.get(0);
			helper.assertTrue(entity.getVehicle() == cart, "the contraption does not ride the cart: " + entity.getVehicle());
			helper.assertTrue(entity.getContraption() instanceof MountedContraption, "not a mounted contraption");
			// the stone and the anchor that stands in for the assembler
			helper.assertTrue(entity.getContraption().getBlocks().size() == 2,
				"contraption blocks: " + entity.getContraption().getBlocks().size());
			helper.assertTrue(entity.getContraption().getBlocks().get(new BlockPos(0, 1, 0)).state().is(Blocks.STONE),
				"stone is not above the anchor");
			helper.assertTrue(helper.getBlockState(railPos(2)).is(MinecartRegistry.CART_ASSEMBLER),
				"the assembler is gone");
			// it goes along with the cart
			helper.assertTrue(relativeX(helper, cart) > 4, "the cart is at x=" + relativeX(helper, cart));
			helper.assertTrue(Math.abs(entity.getX() - cart.getX()) < 1, "contraption at " + entity.getX() + ", cart at " + cart.getX());
			helper.assertTrue(entity.getVisual() != null && entity.getVisual().getPieceCount() >= 2,
				"the contraption has no display");
		});
	}

	@GameTest(maxTicks = 300)
	public void unpoweredAssemblerPutsTheBlocksBack(GameTestHelper helper) {
		track(helper);
		assembler(helper, 1, CartAssembleRailType.REGULAR, true);
		assembler(helper, 5, CartAssembleRailType.REGULAR, false);
		helper.setBlock(new BlockPos(1, 2, Z), Blocks.DIAMOND_BLOCK);
		Minecart cart = cart(helper, EntityTypes.MINECART, 0.5);
		cart.setDeltaMovement(0.3, 0, 0);
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(new BlockPos(1, 2, Z)).isAir(), "the block was not picked up");
			int placedAt = -1;
			for (int x = 2; x <= LAST_RAIL; x++)
				if (helper.getBlockState(new BlockPos(x, 2, Z)).is(Blocks.DIAMOND_BLOCK))
					placedAt = x;
			helper.assertTrue(placedAt >= 5, "the block was not put back at the second assembler, found at " + placedAt);
			helper.assertTrue(contraptions(helper).isEmpty(), "the contraption still exists");
			helper.assertTrue(cart.getPassengers().isEmpty(), "the cart still carries something");
			helper.assertTrue(helper.getBlockState(railPos(5)).is(MinecartRegistry.CART_ASSEMBLER), "the second assembler is gone");
		});
	}

	@GameTest(maxTicks = 300)
	public void blocksKeepTheirBlockEntityDataThroughTheCart(GameTestHelper helper) {
		track(helper);
		assembler(helper, 1, CartAssembleRailType.REGULAR, true);
		assembler(helper, 5, CartAssembleRailType.REGULAR, false);
		helper.setBlock(new BlockPos(1, 2, Z), Blocks.CHEST);
		helper.getBlockEntity(new BlockPos(1, 2, Z), ChestBlockEntity.class).setItem(5, new ItemStack(Items.EMERALD, 7));
		Minecart cart = cart(helper, EntityTypes.MINECART, 0.5);
		cart.setDeltaMovement(0.3, 0, 0);
		helper.succeedWhen(() -> {
			BlockPos chest = null;
			for (int x = 2; x <= LAST_RAIL; x++)
				if (helper.getBlockState(new BlockPos(x, 2, Z)).is(Blocks.CHEST))
					chest = new BlockPos(x, 2, Z);
			helper.assertTrue(chest != null, "no chest after the second assembler; cart at x=" + relativeX(helper, cart));
			ItemStack stack = helper.getBlockEntity(chest, ChestBlockEntity.class).getItem(5);
			helper.assertTrue(stack.is(Items.EMERALD) && stack.getCount() == 7, "chest content: " + stack);
		});
	}

	@GameTest(maxTicks = 200)
	public void assemblerSettingSelectsTheRotationMode(GameTestHelper helper) {
		track(helper);
		assembler(helper, 2, CartAssembleRailType.REGULAR, true);
		helper.setBlock(new BlockPos(2, 2, Z), Blocks.STONE);
		CartAssemblerBlockEntity be = helper.getBlockEntity(railPos(2), CartAssemblerBlockEntity.class);
		var behaviour = be.getBehaviour(dev.createpolymer.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE);
		helper.assertTrue(behaviour != null, "no scroll option behaviour");
		behaviour.value = CartMovementMode.ROTATION_LOCKED.ordinal();
		Minecart cart = cart(helper, EntityTypes.MINECART, 0.5);
		cart.setDeltaMovement(0.3, 0, 0);
		helper.succeedWhen(() -> {
			List<OrientedContraptionEntity> list = contraptions(helper);
			helper.assertTrue(list.size() == 1, "contraption entities: " + list.size());
			Contraption contraption = list.get(0).getContraption();
			helper.assertTrue(contraption.isRotationLocked() && !contraption.pausesActorsWhileRotating(), "mode not applied");
			// the mode is saved with the contraption
			CompoundTag tag = contraption.writeNBT(helper.getLevel().registryAccess(), false);
			Contraption copy = Contraption.fromNBT(helper.getLevel(), tag, false);
			helper.assertTrue(copy instanceof MountedContraption mc && mc.rotationMode == CartMovementMode.ROTATION_LOCKED,
				"mode after loading: " + copy);
		});
	}

	@GameTest(maxTicks = 200)
	public void assemblerWithoutAnythingAboveDoesNotAssemble(GameTestHelper helper) {
		track(helper);
		assembler(helper, 2, CartAssembleRailType.REGULAR, true);
		Minecart cart = cart(helper, EntityTypes.MINECART, 0.5);
		cart.setDeltaMovement(0.3, 0, 0);
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(contraptions(helper).isEmpty(), "assembled an empty contraption");
			helper.assertTrue(cart.getPassengers().isEmpty(), "the cart carries something");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 300)
	public void poweredRailAssemblerSpeedsTheCartUp(GameTestHelper helper) {
		track(helper);
		assembler(helper, 2, CartAssembleRailType.POWERED_RAIL, true);
		helper.setBlock(new BlockPos(2, 2, Z), Blocks.STONE);
		Minecart cart = cart(helper, EntityTypes.MINECART, 0.5);
		cart.setDeltaMovement(0.1, 0, 0);
		// an assembler on a powered rail speeds the cart up in its direction of travel
		helper.succeedWhen(() -> {
			helper.assertTrue(!contraptions(helper).isEmpty(), "no contraption");
			helper.assertTrue(relativeX(helper, cart) > 5.5, "the cart is at x=" + relativeX(helper, cart));
		});
	}

	// ---- couplings ----

	@GameTest(maxTicks = 200)
	public void coupledCartsKeepTheirDistance(GameTestHelper helper) {
		track(helper);
		Minecart front = cart(helper, EntityTypes.MINECART, 4.5);
		Minecart back = cart(helper, EntityTypes.MINECART, 1.5);
		Player player = helper.makeMockPlayer(GameType.CREATIVE);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(CouplingHandler.tryToCoupleCarts(player, helper.getLevel(), front.getId(), back.getId()),
				"could not couple the carts");
			MinecartController controller = MinecartController.get(front);
			helper.assertTrue(controller.isLeadingCoupling() && !controller.isConnectedToCoupling(), "coupling data of the front cart");
			helper.assertTrue(MinecartController.get(back).isConnectedToCoupling(), "coupling data of the back cart");
			helper.assertTrue(controller.getCouplingLength(true) == 3, "coupling length " + controller.getCouplingLength(true));
			front.setDeltaMovement(0.2, 0, 0);
		});
		helper.runAfterDelay(25, () -> {
			double distance = front.position().distanceTo(back.position());
			helper.assertTrue(Math.abs(distance - 3) < 0.6, "distance between the carts: " + distance + ", front at x="
				+ relativeX(helper, front) + ", back at x=" + relativeX(helper, back));
			// the pull splits the push between both carts
			helper.assertTrue(relativeX(helper, back) > 2.5, "the back cart was not pulled along, it is at x=" + relativeX(helper, back));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void cartsWithoutACouplingDoNotFollow(GameTestHelper helper) {
		track(helper);
		Minecart front = cart(helper, EntityTypes.MINECART, 4.5);
		Minecart back = cart(helper, EntityTypes.MINECART, 1.5);
		helper.runAfterDelay(3, () -> front.setDeltaMovement(0.2, 0, 0));
		helper.runAfterDelay(25, () -> {
			helper.assertTrue(relativeX(helper, back) < 2, "the back cart moved to x=" + relativeX(helper, back));
			helper.assertTrue(front.position().distanceTo(back.position()) > 3.5, "the carts are close");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void couplingRulesAreEnforced(GameTestHelper helper) {
		track(helper);
		Minecart a = cart(helper, EntityTypes.MINECART, 0.5);
		Minecart b = cart(helper, EntityTypes.MINECART, 2.5);
		Minecart c = cart(helper, EntityTypes.MINECART, 4.5);
		Minecart far = cart(helper, EntityTypes.MINECART, 6.5);
		Player player = helper.makeMockPlayer(GameType.CREATIVE);
		helper.runAfterDelay(3, () -> {
			var level = helper.getLevel();
			helper.assertTrue(CouplingHandler.tryToCoupleCarts(player, level, a.getId(), b.getId()), "a-b");
			helper.assertTrue(CouplingHandler.tryToCoupleCarts(player, level, b.getId(), c.getId()), "b-c");
			// a middle cart has two couplings already
			helper.assertTrue(MinecartController.get(b).isFullyCoupled(), "b is not fully coupled");
			helper.assertTrue(!CouplingHandler.tryToCoupleCarts(player, level, b.getId(), far.getId()), "a third coupling was made");
			// no loops
			helper.assertTrue(!CouplingHandler.tryToCoupleCarts(player, level, a.getId(), c.getId()), "a loop was made");
			// the chain can be followed from either end
			MinecartController next = CouplingHandler.getNextInCouplingChain(level, MinecartController.get(a), true);
			helper.assertTrue(next == MinecartController.get(b), "next cart after a");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void aCartThatDiesFreesItsNeighboursAndDropsTheCoupling(GameTestHelper helper) {
		track(helper);
		Minecart a = cart(helper, EntityTypes.MINECART, 1.5);
		Minecart b = cart(helper, EntityTypes.MINECART, 4.5);
		Player player = helper.makeMockPlayer(GameType.CREATIVE);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(CouplingHandler.tryToCoupleCarts(player, helper.getLevel(), a.getId(), b.getId()), "coupling");
			a.discard();
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(!MinecartController.get(b).isConnectedToCoupling() && !MinecartController.get(b).isLeadingCoupling(),
				"the other cart is still coupled");
			List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
				new AABB(helper.absolutePos(new BlockPos(3, 1, Z))).inflate(2.5), e -> e.getItem().is(MinecartRegistry.MINECART_COUPLING));
			helper.assertTrue(items.size() == 1, "dropped couplings: " + items.size());
		});
	}

	@GameTest(maxTicks = 100)
	public void aWrenchRemovesCouplingsAndGivesThemBack(GameTestHelper helper) {
		track(helper);
		Minecart a = cart(helper, EntityTypes.MINECART, 1.5);
		Minecart b = cart(helper, EntityTypes.MINECART, 4.5);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(CouplingHandler.tryToCoupleCarts(player, helper.getLevel(), a.getId(), b.getId()), "coupling");
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WRENCH));
			var result = net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker()
				.interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, a, null);
			helper.assertTrue(result == InteractionResult.SUCCESS, "wrench result " + result);
			helper.assertTrue(!MinecartController.get(a).isLeadingCoupling() && !MinecartController.get(b).isConnectedToCoupling(),
				"still coupled");
			helper.assertTrue(player.getInventory().countItem(MinecartRegistry.MINECART_COUPLING) == 1, "coupling was not given back");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void couplingsSurviveSavingTheCart(GameTestHelper helper) {
		track(helper);
		Minecart a = cart(helper, EntityTypes.MINECART, 1.5);
		Minecart b = cart(helper, EntityTypes.MINECART, 4.5);
		Player player = helper.makeMockPlayer(GameType.CREATIVE);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(CouplingHandler.tryToCoupleCarts(player, helper.getLevel(), a.getId(), b.getId()), "coupling");
			MinecartController controller = MinecartController.get(a);
			DataResult<net.minecraft.nbt.Tag> encoded = MinecartController.CODEC.encodeStart(NbtOps.INSTANCE, controller);
			MinecartController loaded = MinecartController.CODEC.parse(NbtOps.INSTANCE, encoded.getOrThrow()).getOrThrow();
			helper.assertTrue(loaded.isLeadingCoupling() && !loaded.isConnectedToCoupling(), "coupling after loading");
			helper.assertTrue(b.getUUID().equals(loaded.getCoupledCart(true)), "coupled cart after loading");
			helper.assertTrue(loaded.getCouplingLength(true) == controller.getCouplingLength(true), "length after loading");
			helper.assertTrue(!loaded.isPresent(), "a controller read from a save has a cart before it joins a level");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void clickingTwoCartsWithACouplingCouplesThem(GameTestHelper helper) {
		track(helper);
		Minecart a = cart(helper, EntityTypes.MINECART, 1.5);
		Minecart b = cart(helper, EntityTypes.MINECART, 4.5);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MinecartRegistry.MINECART_COUPLING, 2));
		helper.runAfterDelay(3, () -> {
			var level = helper.getLevel();
			var first = MinecartCouplingItem.handleInteractionWithMinecart(player, level, InteractionHand.MAIN_HAND, a, null);
			helper.assertTrue(first == InteractionResult.SUCCESS, "first click " + first);
			helper.assertTrue(a.getUUID().equals(MinecartCouplingItem.getSelectedCart(player)), "the first cart was not selected");
			helper.assertTrue(!MinecartController.get(a).isLeadingCoupling(), "coupled after one click");
			var second = MinecartCouplingItem.handleInteractionWithMinecart(player, level, InteractionHand.MAIN_HAND, b, null);
			helper.assertTrue(second == InteractionResult.SUCCESS, "second click " + second);
			helper.assertTrue(MinecartController.get(a).isLeadingCoupling() && MinecartController.get(b).isConnectedToCoupling(),
				"the carts are not coupled");
			helper.assertTrue(MinecartCouplingItem.getSelectedCart(player) == null, "the selection stayed");
			helper.assertTrue(player.getMainHandItem().getCount() == 1, "the coupling was not used up: " + player.getMainHandItem());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void aSavedAndLoadedCartKeepsItsCouplingOnceItJoinsALevel(GameTestHelper helper) {
		track(helper);
		Minecart a = cart(helper, EntityTypes.MINECART, 1.5);
		Minecart b = cart(helper, EntityTypes.MINECART, 4.5);
		Player player = helper.makeMockPlayer(GameType.CREATIVE);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(CouplingHandler.tryToCoupleCarts(player, helper.getLevel(), a.getId(), b.getId()), "coupling");
			TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
			a.saveWithoutId(out);
			CompoundTag tag = out.buildResult();
			Minecart copy = EntityTypes.MINECART.create(helper.getLevel(), EntitySpawnReason.LOAD);
			copy.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
			// what the load event of the level does
			CapabilityMinecartController.attach(copy, helper.getLevel());
			MinecartController loaded = MinecartController.get(copy);
			helper.assertTrue(loaded != MinecartController.EMPTY, "the controller was not saved: " + tag);
			helper.assertTrue(loaded != MinecartController.get(a), "the controller was shared");
			helper.assertTrue(loaded.isLeadingCoupling(), "the coupling is gone");
			helper.assertTrue(b.getUUID().equals(loaded.getCoupledCart(true)), "coupled to " + loaded.getCoupledCart(true));
			helper.assertTrue(loaded.cart() == copy && loaded.isPresent(), "the controller is not bound to its cart");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 300)
	public void twoAssemblersOnTheSameStructureMakeACarriageOfCoupledCarts(GameTestHelper helper) {
		track(helper);
		assembler(helper, 1, CartAssembleRailType.REGULAR, true);
		assembler(helper, 4, CartAssembleRailType.REGULAR, false);
		for (int x = 1; x <= 4; x++)
			helper.setBlock(new BlockPos(x, 2, Z), Blocks.OAK_PLANKS);
		helper.getLevel().addFreshEntity(new SuperGlueEntity(helper.getLevel(),
			SuperGlueEntity.span(helper.absolutePos(new BlockPos(1, 2, Z)), helper.absolutePos(new BlockPos(4, 2, Z)))));
		Minecart rear = cart(helper, EntityTypes.MINECART, 0.5);
		Minecart front = cart(helper, EntityTypes.MINECART, 4.5);
		rear.setDeltaMovement(0.2, 0, 0);
		boolean[] pushed = { false };
		helper.succeedWhen(() -> {
			List<OrientedContraptionEntity> list = contraptions(helper);
			helper.assertTrue(list.size() == 1, "contraption entities: " + list.size());
			OrientedContraptionEntity carriage = list.get(0);
			helper.assertTrue(rear.getUUID().equals(carriage.getCouplingId()), "coupling id " + carriage.getCouplingId());
			helper.assertTrue(MinecartController.get(rear).isCoupledThroughContraption() && MinecartController.get(front).isCoupledThroughContraption(),
				"the carts are not coupled through the contraption");
			// two anchors and the four planks between them
			helper.assertTrue(carriage.getContraption().getBlocks().size() == 6, "blocks: " + carriage.getContraption().getBlocks().size());
			helper.assertTrue(carriage.getCoupledCartsIfPresent() != null, "no coupled carts for the contraption");
			if (!pushed[0]) {
				pushed[0] = true;
				front.setDeltaMovement(0.15, 0, 0);
			}
			// the carriage is held between the carts and turned along them
			double distance = rear.position().distanceTo(front.position());
			helper.assertTrue(Math.abs(distance - 3) < 0.6, "distance " + distance + ", rear at " + relativeX(helper, rear) + ", front at "
				+ relativeX(helper, front) + ", length " + MinecartController.get(rear).getCouplingLength(true));
			helper.assertTrue(relativeX(helper, front) > 5, "the carriage did not move, front at " + relativeX(helper, front));
			helper.assertTrue(Math.abs(AngleHelper.wrapAngle180(carriage.yaw - carriage.getInitialYaw())) < 200, "yaw " + carriage.yaw);
		});
	}

	@GameTest(maxTicks = 100)
	public void aCartCoupledThroughAContraptionCannotBeBoarded(GameTestHelper helper) {
		track(helper);
		assembler(helper, 1, CartAssembleRailType.REGULAR, true);
		assembler(helper, 4, CartAssembleRailType.REGULAR, false);
		for (int x = 1; x <= 4; x++)
			helper.setBlock(new BlockPos(x, 2, Z), Blocks.OAK_PLANKS);
		helper.getLevel().addFreshEntity(new SuperGlueEntity(helper.getLevel(),
			SuperGlueEntity.span(helper.absolutePos(new BlockPos(1, 2, Z)), helper.absolutePos(new BlockPos(4, 2, Z)))));
		Minecart rear = cart(helper, EntityTypes.MINECART, 0.5);
		Minecart front = cart(helper, EntityTypes.MINECART, 4.5);
		rear.setDeltaMovement(0.2, 0, 0);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		helper.succeedWhen(() -> {
			helper.assertTrue(MinecartController.get(front).isCoupledThroughContraption(), "not coupled yet");
			var result = net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker()
				.interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, front, null);
			helper.assertTrue(result == InteractionResult.FAIL, "interaction with the carriage's cart: " + result);
			var pig = helper.spawn(EntityTypes.PIG, new Vec3(4.5, 1.5, Z + 0.5));
			pig.startRiding(front, true, false);
			helper.runAfterDelay(2, () -> helper.assertTrue(!front.getPassengers().contains(pig), "the pig stayed on the cart"));
		});
	}

	@GameTest(maxTicks = 40)
	public void assemblerModelsExistForEveryState(GameTestHelper helper) {
		int states = 0;
		for (CartAssembleRailType type : CartAssembleRailType.values())
			for (RailShape shape : new RailShape[] { RailShape.EAST_WEST, RailShape.NORTH_SOUTH })
				for (boolean powered : new boolean[] { true, false })
					for (boolean backwards : new boolean[] { true, false })
						for (boolean water : new boolean[] { true, false }) {
							BlockState state = MinecartRegistry.CART_ASSEMBLER.defaultBlockState()
								.setValue(CartAssemblerBlock.RAIL_TYPE, type)
								.setValue(CartAssemblerBlock.RAIL_SHAPE, shape)
								.setValue(CartAssemblerBlock.POWERED, powered)
								.setValue(CartAssemblerBlock.BACKWARDS, backwards)
								.setValue(net.minecraft.world.level.block.BaseRailBlock.WATERLOGGED, water);
							helper.assertTrue(BlockstateModels.get("cart_assembler", state) != null, "no model for " + state);
							new CartAssemblerBlock.Model(state);
							states++;
						}
		helper.assertTrue(states == 80, "states " + states);
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void anAssemblerDropsItselfAndItsRail(GameTestHelper helper) {
		track(helper);
		assembler(helper, 3, CartAssembleRailType.POWERED_RAIL, false);
		BlockPos pos = helper.absolutePos(railPos(3));
		List<ItemStack> drops = Block.getDrops(helper.getBlockState(railPos(3)), helper.getLevel(), pos, helper.getLevel().getBlockEntity(pos));
		helper.assertTrue(drops.stream().anyMatch(d -> d.is(MinecartRegistry.CART_ASSEMBLER_ITEM)), "no assembler in " + drops);
		helper.assertTrue(drops.stream().anyMatch(d -> d.is(Items.POWERED_RAIL)), "no powered rail in " + drops);
		helper.succeed();
	}

	// ---- minecart contraption items ----

	@GameTest(maxTicks = 300)
	public void aWrenchPicksUpAMinecartContraptionAndTheItemPlacesItAgain(GameTestHelper helper) {
		track(helper);
		assembler(helper, 1, CartAssembleRailType.REGULAR, true);
		helper.setBlock(new BlockPos(1, 2, Z), Blocks.EMERALD_BLOCK);
		Minecart cart = cart(helper, EntityTypes.MINECART, 0.5);
		cart.setDeltaMovement(0.3, 0, 0);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(contraptions(helper).size() == 1, "contraption entities: " + contraptions(helper).size());
			cart.setDeltaMovement(Vec3.ZERO);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WRENCH));
			var result = MinecartContraptionItem.wrenchCanBeUsedToPickUpMinecartContraptions(player, helper.getLevel(),
				InteractionHand.MAIN_HAND, cart, null);
			helper.assertTrue(result == InteractionResult.SUCCESS, "pickup result " + result);
			helper.assertTrue(cart.isRemoved(), "the cart is still there");
			helper.assertTrue(contraptions(helper).stream().noneMatch(OrientedContraptionEntity::isAlive), "the contraption is still there");
			ItemStack item = find(player, MinecartRegistry.MINECART_CONTRAPTION);
			helper.assertTrue(!item.isEmpty(), "no picked up item in the inventory");
			CompoundTag data = item.get(MinecartRegistry.MINECART_CONTRAPTION_DATA);
			helper.assertTrue(data != null && data.contains("InitialOrientation") && data.contains("Blocks"), "contraption data: " + data);

			// place it on the rail at x = 5
			player.setItemInHand(InteractionHand.MAIN_HAND, item);
			BlockPos target = railPos(5);
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(target)), Direction.UP, helper.absolutePos(target), false);
			InteractionResult placed = item.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
			helper.assertTrue(placed == InteractionResult.SUCCESS, "placing: " + placed);
			helper.assertTrue(item.isEmpty(), "the item was not used up");
			List<OrientedContraptionEntity> list = contraptions(helper);
			helper.assertTrue(list.size() == 1, "contraptions after placing: " + list.size());
			OrientedContraptionEntity restored = list.get(0);
			helper.assertTrue(restored.getVehicle() instanceof Minecart, "the restored contraption has no cart: " + restored.getVehicle());
			helper.assertTrue(restored.getContraption().getBlocks().size() == 2, "restored blocks: " + restored.getContraption().getBlocks().size());
			helper.assertTrue(restored.getContraption().getBlocks().get(new BlockPos(0, 1, 0)).state().is(Blocks.EMERALD_BLOCK),
				"the emerald block is not above the anchor");
			helper.assertTrue(relativeX(helper, restored.getVehicle()) > 5 && relativeX(helper, restored.getVehicle()) < 6, "cart at "
				+ relativeX(helper, restored.getVehicle()));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void aWrenchInCreativeAlsoPicksUpFurnaceAndChestCartContraptions(GameTestHelper helper) {
		track(helper);
		for (var type : new net.minecraft.world.entity.EntityType[] { EntityTypes.FURNACE_MINECART, EntityTypes.CHEST_MINECART }) {
			@SuppressWarnings("unchecked")
			AbstractMinecart cart = cart(helper, (net.minecraft.world.entity.EntityType<AbstractMinecart>) type, 4.5);
			MountedContraption contraption = new MountedContraption();
			helper.setBlock(new BlockPos(4, 2, Z), Blocks.STONE);
			helper.setBlock(railPos(4), MinecartRegistry.CART_ASSEMBLER.defaultBlockState()
				.setValue(CartAssemblerBlock.RAIL_SHAPE, RailShape.EAST_WEST)
				.setValue(CartAssemblerBlock.RAIL_TYPE, CartAssembleRailType.REGULAR));
			try {
				helper.assertTrue(contraption.assemble(helper.getLevel(), helper.absolutePos(railPos(4))), "assembling");
			} catch (dev.createpolymer.contraptions.AssemblyException e) {
				helper.fail(e.getMessage());
			}
			contraption.removeBlocksFromWorld(helper.getLevel(), BlockPos.ZERO);
			contraption.startMoving(helper.getLevel());
			OrientedContraptionEntity entity = OrientedContraptionEntity.create(helper.getLevel(), contraption, Direction.EAST);
			entity.setPos(cart.getX(), cart.getY(), cart.getZ());
			helper.getLevel().addFreshEntity(entity);
			entity.startRiding(cart);
			Player player = helper.makeMockPlayer(GameType.CREATIVE);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WRENCH));
			var result = MinecartContraptionItem.wrenchCanBeUsedToPickUpMinecartContraptions(player, helper.getLevel(),
				InteractionHand.MAIN_HAND, cart, null);
			helper.assertTrue(result == InteractionResult.SUCCESS, "pickup result " + result);
			var expected = type == EntityTypes.FURNACE_MINECART ? MinecartRegistry.FURNACE_MINECART_CONTRAPTION
				: MinecartRegistry.CHEST_MINECART_CONTRAPTION;
			helper.assertTrue(!find(player, expected).isEmpty(), "no " + expected + " for " + type);
			helper.setBlock(railPos(4), Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.EAST_WEST));
		}
		helper.succeed();
	}

	// ---- furnace carts ----

	@GameTest(maxTicks = 200)
	public void aFurnaceCartBurnsFuelFromTheStorageOfItsContraption(GameTestHelper helper) {
		track(helper);
		assembler(helper, 2, CartAssembleRailType.REGULAR, true);
		helper.setBlock(new BlockPos(2, 2, Z), Blocks.CHEST);
		ChestBlockEntity chest = helper.getBlockEntity(new BlockPos(2, 2, Z), ChestBlockEntity.class);
		chest.setItem(0, new ItemStack(Items.COAL, 4));
		MinecartFurnace cart = cart(helper, EntityTypes.FURNACE_MINECART, 0.5);
		cart.setDeltaMovement(0.15, 0, 0);
		helper.assertTrue(FurnaceCartFuel.getFuel(cart) == 0, "the cart starts with fuel");
		helper.succeedWhen(() -> {
			List<OrientedContraptionEntity> list = contraptions(helper);
			helper.assertTrue(list.size() == 1, "contraption entities: " + list.size());
			helper.assertTrue(FurnaceCartFuel.getFuel(cart) > 3000, "fuel " + FurnaceCartFuel.getFuel(cart));
			var fuelItems = list.get(0).getContraption().getStorage().getFuelItems();
			helper.assertTrue(fuelItems != null, "no fuel storage");
			int coal = 0;
			for (int i = 0; i < fuelItems.getSlots(); i++)
				if (fuelItems.getStackInSlot(i).is(Items.COAL))
					coal += fuelItems.getStackInSlot(i).getCount();
			helper.assertTrue(coal == 3, "coal left in the contraption: " + coal);
		});
	}

	@GameTest(maxTicks = 200)
	public void aChestCartHandsItsInventoryToTheContraption(GameTestHelper helper) {
		track(helper);
		assembler(helper, 2, CartAssembleRailType.REGULAR, true);
		helper.setBlock(new BlockPos(2, 2, Z), Blocks.STONE);
		MinecartChest cart = cart(helper, EntityTypes.CHEST_MINECART, 0.5);
		cart.setItem(4, new ItemStack(Items.DIAMOND, 3));
		cart.setDeltaMovement(0.3, 0, 0);
		helper.succeedWhen(() -> {
			List<OrientedContraptionEntity> list = contraptions(helper);
			helper.assertTrue(list.size() == 1, "contraption entities: " + list.size());
			var items = list.get(0).getContraption().getStorage().getAllItems();
			int diamonds = 0;
			for (int i = 0; i < items.getSlots(); i++)
				if (items.getStackInSlot(i).is(Items.DIAMOND))
					diamonds += items.getStackInSlot(i).getCount();
			helper.assertTrue(diamonds == 3, "diamonds reachable from the contraption: " + diamonds);
		});
	}

	// ---- blocks ----

	@GameTest(maxTicks = 40)
	public void railsHaveTheRailTag(GameTestHelper helper) {
		helper.assertTrue(MinecartRegistry.CART_ASSEMBLER.defaultBlockState().is(BlockTags.RAILS), "cart assembler is not a rail");
		helper.assertTrue(MinecartRegistry.CONTROLLER_RAIL.defaultBlockState().is(BlockTags.RAILS), "controller rail is not a rail");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void clientsSeeVanillaRailsInsteadOfTheAssembler(GameTestHelper helper) {
		for (CartAssembleRailType type : CartAssembleRailType.values())
			for (RailShape shape : new RailShape[] { RailShape.EAST_WEST, RailShape.NORTH_SOUTH })
				for (boolean powered : new boolean[] { true, false }) {
					BlockState state = MinecartRegistry.CART_ASSEMBLER.defaultBlockState()
						.setValue(CartAssemblerBlock.RAIL_TYPE, type)
						.setValue(CartAssemblerBlock.RAIL_SHAPE, shape)
						.setValue(CartAssemblerBlock.POWERED, powered);
					BlockState client = MinecartRegistry.CART_ASSEMBLER.getPolymerBlockState(state, null);
					helper.assertTrue(client.is(BlockTags.RAILS), type + " shows " + client);
					var shapeProperty = ((net.minecraft.world.level.block.BaseRailBlock) client.getBlock()).getShapeProperty();
					helper.assertTrue(client.getValue(shapeProperty) == shape, type + " shows the shape " + client.getValue(shapeProperty));
					if (type == CartAssembleRailType.POWERED_RAIL)
						helper.assertTrue(client.is(Blocks.POWERED_RAIL), "powered rail type shows " + client);
					if (type == CartAssembleRailType.REGULAR)
						helper.assertTrue(client.is(Blocks.RAIL), "regular type shows " + client);
				}
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void aWrenchTurnsAnAssemblerAndSneakingTakesItOffTheRail(GameTestHelper helper) {
		track(helper);
		assembler(helper, 3, CartAssembleRailType.POWERED_RAIL, false);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WRENCH));
		BlockPos abs = helper.absolutePos(railPos(3));
		UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
		BlockState state = helper.getBlockState(railPos(3));
		helper.assertTrue(((CartAssemblerBlock) state.getBlock()).onWrenched(state, context) == InteractionResult.SUCCESS, "wrench");
		helper.assertTrue(helper.getBlockState(railPos(3)).getValue(CartAssemblerBlock.RAIL_SHAPE) == RailShape.NORTH_SOUTH,
			"the assembler did not turn: " + helper.getBlockState(railPos(3)));
		state = helper.getBlockState(railPos(3));
		helper.assertTrue(((CartAssemblerBlock) state.getBlock()).onSneakWrenched(state, context) == InteractionResult.SUCCESS, "sneak wrench");
		BlockState after = helper.getBlockState(railPos(3));
		helper.assertTrue(after.is(Blocks.POWERED_RAIL), "the block under the assembler is " + after);
		helper.assertTrue(player.getInventory().countItem(MinecartRegistry.CART_ASSEMBLER_ITEM) == 1, "the assembler was not given back");
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void anAssemblerReplacesARailWhenUsedOnIt(GameTestHelper helper) {
		track(helper);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack stack = new ItemStack(MinecartRegistry.CART_ASSEMBLER_ITEM, 2);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockPos abs = helper.absolutePos(railPos(4));
		InteractionResult result = stack.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));
		helper.assertTrue(result == InteractionResult.SUCCESS, "result " + result);
		BlockState state = helper.getBlockState(railPos(4));
		helper.assertTrue(state.is(MinecartRegistry.CART_ASSEMBLER), "state " + state);
		helper.assertTrue(state.getValue(CartAssemblerBlock.RAIL_TYPE) == CartAssembleRailType.REGULAR, "type " + state.getValue(CartAssemblerBlock.RAIL_TYPE));
		helper.assertTrue(state.getValue(CartAssemblerBlock.RAIL_SHAPE) == RailShape.EAST_WEST, "shape");
		helper.assertTrue(stack.getCount() == 1, "stack " + stack.getCount());
		// a powered rail held in the hand swaps the rail type
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.POWERED_RAIL));
		InteractionResult swapped = state.useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
		helper.assertTrue(swapped == InteractionResult.SUCCESS, "swap " + swapped);
		helper.assertTrue(helper.getBlockState(railPos(4)).getValue(CartAssemblerBlock.RAIL_TYPE) == CartAssembleRailType.POWERED_RAIL,
			"type after swapping: " + helper.getBlockState(railPos(4)).getValue(CartAssemblerBlock.RAIL_TYPE));
		helper.assertTrue(player.getInventory().countItem(Items.RAIL) == 1, "the old rail was not given back");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void controllerRailSetsTheCartSpeedFromTheSignal(GameTestHelper helper) {
		track(helper);
		helper.setBlock(new BlockPos(3, 0, Z), Blocks.REDSTONE_BLOCK);
		helper.setBlock(railPos(3), MinecartRegistry.CONTROLLER_RAIL.defaultBlockState()
			.setValue(dev.createpolymer.redstone.rail.ControllerRailBlock.SHAPE, RailShape.EAST_WEST)
			.setValue(dev.createpolymer.redstone.rail.ControllerRailBlock.BACKWARDS, true)
			.setValue(dev.createpolymer.redstone.rail.ControllerRailBlock.POWER, 15));
		// pointing east: backwards = true on an east-west rail
		Minecart cart = cart(helper, EntityTypes.MINECART, 2.5);
		cart.setDeltaMovement(0.05, 0, 0);
		helper.succeedWhen(() -> helper.assertTrue(relativeX(helper, cart) > 6, "the cart is at x=" + relativeX(helper, cart)));
	}
}
