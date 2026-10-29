package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.base.RotatedPillarKineticBlock;
import dev.createpolymer.kinetics.belt.BeltBlockEntity;
import dev.createpolymer.kinetics.belt.item.BeltConnectorItem;
import dev.createpolymer.kinetics.belt.transport.TransportedItemStack;
import dev.createpolymer.kinetics.chainConveyor.ChainConveyorBlockEntity;
import dev.createpolymer.kinetics.chainConveyor.ChainConveyorConnectionHandler;
import dev.createpolymer.kinetics.chainConveyor.ChainConveyorInteractionHandler;
import dev.createpolymer.kinetics.chainConveyor.ChainConveyorInteractionHandler.Selection;
import dev.createpolymer.kinetics.chainConveyor.ChainConveyorPackage;
import dev.createpolymer.kinetics.chainConveyor.ChainConveyorRidingHandler;
import dev.createpolymer.kinetics.chainConveyor.ChainPackageInteractionHandler;
import dev.createpolymer.kinetics.chainConveyor.ServerChainConveyorHandler;
import dev.createpolymer.kinetics.motor.CreativeMotorBlockEntity;
import dev.createpolymer.logistics.box.PackageEntity;
import dev.createpolymer.logistics.box.PackageItem;
import dev.createpolymer.logistics.depot.DepotBlockEntity;
import dev.createpolymer.logistics.packagePort.PackagePortTarget.ChainConveyorFrogportTarget;
import dev.createpolymer.logistics.packagePort.PackagePortTargetSelectionHandler;
import dev.createpolymer.logistics.packagePort.frogport.FrogportBlockEntity;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.BeltRegistry;
import dev.createpolymer.registry.feature.ChainConveyorRegistry;
import dev.createpolymer.registry.feature.ChuteRegistry;
import dev.createpolymer.registry.feature.FunnelRegistry;
import dev.createpolymer.logistics.funnel.FunnelBlock;
import dev.createpolymer.registry.feature.PackagesRegistry;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class ChainConveyorGameTests {

	// Two chain conveyors, five blocks apart, and a motor below the first one. A frogport next to the second one.
	private static final BlockPos A = new BlockPos(1, 2, 2);
	private static final BlockPos B = new BlockPos(6, 2, 2);
	private static final BlockPos MOTOR = new BlockPos(1, 1, 2);
	private static final BlockPos FROGPORT = new BlockPos(6, 2, 1);

	private static ChainConveyorBlockEntity conveyor(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockEntity(pos, ChainConveyorBlockEntity.class);
	}

	private static BlockPos connection(GameTestHelper helper, BlockPos from, BlockPos to) {
		return helper.absolutePos(to).subtract(helper.absolutePos(from));
	}

	/** The conveyors, connected with a chain, with a creative motor at the given speed under the first. */
	private static void buildPair(GameTestHelper helper, int speed) {
		floor(helper);
		helper.setBlock(MOTOR, ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
		helper.setBlock(A, ChainConveyorRegistry.CHAIN_CONVEYOR);
		helper.setBlock(B, ChainConveyorRegistry.CHAIN_CONVEYOR);
		helper.getBlockEntity(MOTOR, CreativeMotorBlockEntity.class).setSetting(speed);
		conveyor(helper, A).addConnectionTo(helper.absolutePos(B));
		conveyor(helper, B).addConnectionTo(helper.absolutePos(A));
	}

	private static ItemStack addressed(String address, ItemStack... contents) {
		ItemStack box = PackageItem.containing(List.of(contents));
		PackageItem.addAddress(box, address);
		return box;
	}

	private static int packagesIn(FrogportBlockEntity port, String address) {
		int found = 0;
		for (int i = 0; i < port.inventory.getSlots(); i++) {
			ItemStack stack = port.inventory.getStackInSlot(i);
			if (PackageItem.isPackage(stack) && PackageItem.getAddress(stack).equals(address))
				found++;
		}
		return found;
	}

	/** Like GameTestHelper.makeMockServerPlayerInLevel, which only makes creative players. */
	private static ServerPlayer survivalPlayer(GameTestHelper helper) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public GameType gameMode() {
				return GameType.SURVIVAL;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		return player;
	}

	@SuppressWarnings("removal")
	private static void leave(GameTestHelper helper, ServerPlayer player) {
		player.connection.disconnect(net.minecraft.network.chat.Component.literal("test over"));
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	private static BlockHitResult hit(GameTestHelper helper, BlockPos pos) {
		BlockPos abs = helper.absolutePos(pos);
		return new BlockHitResult(Vec3.atCenterOf(abs).add(0, .3, 0), Direction.UP, abs, false);
	}

	// ---- registration ----

	@GameTest
	public void chainConveyorIsRegisteredLikeCreates(GameTestHelper helper) {
		helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(ChainConveyorRegistry.CHAIN_CONVEYOR)
			.toString().equals("create:chain_conveyor"), "block id");
		helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE
			.getKey(ChainConveyorRegistry.CHAIN_CONVEYOR_BLOCK_ENTITY).toString().equals("create:chain_conveyor"), "block entity id");
		helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ChainConveyorRegistry.CHAIN_CONVEYOR_ITEM)
			.toString().equals("create:chain_conveyor"), "item id");
		helper.assertTrue(ChainConveyorBlockEntity.getChainCost(new BlockPos(5, 0, 0)) == 2, "5 blocks cost 2 chains");
		helper.assertTrue(ChainConveyorBlockEntity.getChainCost(new BlockPos(1, 0, 0)) == 1, "a chain costs at least 1");
		helper.succeed();
	}

	// ---- kinetics ----

	@GameTest(maxTicks = 60)
	public void rotationTravelsAlongTheChain(GameTestHelper helper) {
		buildPair(helper, 64);
		helper.succeedWhen(() -> {
			TestUtil.assertSpeed(helper, A, 64);
			TestUtil.assertSpeed(helper, B, 64);
		});
	}

	@GameTest(maxTicks = 40)
	public void conveyorsOnlyKeepConnectionsBothSidesKnow(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(A, ChainConveyorRegistry.CHAIN_CONVEYOR);
		helper.setBlock(B, ChainConveyorRegistry.CHAIN_CONVEYOR);
		// only one side knows about the chain: it is dropped by the first tick
		conveyor(helper, A).addConnectionTo(helper.absolutePos(B));
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(conveyor(helper, A).connections.isEmpty(), "a one-sided connection was kept");
			helper.succeed();
		});
	}

	// ---- connecting with chains ----

	@GameTest(maxTicks = 40)
	public void clickingTwoConveyorsWithChainsConnectsThemAndTakesChains(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(A, ChainConveyorRegistry.CHAIN_CONVEYOR);
		helper.setBlock(B, ChainConveyorRegistry.CHAIN_CONVEYOR);
		ServerPlayer player = survivalPlayer(helper);
		ItemStack chains = new ItemStack(Items.IRON_CHAIN, 5);
		player.setItemInHand(InteractionHand.MAIN_HAND, chains);

		InteractionResult first = ChainConveyorConnectionHandler.onItemUsedOnBlock(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit(helper, A));
		helper.assertTrue(first.consumesAction(), "the first click did nothing");
		helper.assertTrue(helper.absolutePos(A).equals(ChainConveyorConnectionHandler.getSelection(player)), "the first conveyor was not selected");
		helper.assertTrue(conveyor(helper, A).connections.isEmpty(), "connected after one click");

		InteractionResult second = ChainConveyorConnectionHandler.onItemUsedOnBlock(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit(helper, B));
		helper.assertTrue(second.consumesAction(), "the second click did nothing");
		helper.assertTrue(ChainConveyorConnectionHandler.getSelection(player) == null, "the selection was kept");
		helper.assertTrue(conveyor(helper, A).connections.contains(connection(helper, A, B)), "A is not connected to B");
		helper.assertTrue(conveyor(helper, B).connections.contains(connection(helper, B, A)), "B is not connected to A");
		// 5 blocks of chain cost 2 chains
		helper.assertTrue(player.getMainHandItem().getCount() == 3, "the player has " + player.getMainHandItem().getCount() + " chains left instead of 3");
		leave(helper, player);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void notEnoughChainsMeansNoConnection(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(A, ChainConveyorRegistry.CHAIN_CONVEYOR);
		helper.setBlock(B, ChainConveyorRegistry.CHAIN_CONVEYOR);
		ServerPlayer player = survivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_CHAIN, 1));
		ChainConveyorConnectionHandler.onItemUsedOnBlock(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit(helper, A));
		ChainConveyorConnectionHandler.onItemUsedOnBlock(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit(helper, B));
		helper.assertTrue(conveyor(helper, A).connections.isEmpty(), "connected without the chains");
		helper.assertTrue(player.getMainHandItem().getCount() == 1, "chains were taken");
		leave(helper, player);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void conveyorsTooCloseOrTooFarCannotBeConnected(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(A, ChainConveyorRegistry.CHAIN_CONVEYOR);
		helper.setBlock(new BlockPos(3, 2, 2), ChainConveyorRegistry.CHAIN_CONVEYOR);
		ServerPlayer player = survivalPlayer(helper);
		ItemStack chain = new ItemStack(Items.IRON_CHAIN, 64);
		String tooClose = ChainConveyorConnectionHandler.validate(helper.getLevel(), helper.absolutePos(A), helper.absolutePos(new BlockPos(3, 2, 2)), player, chain);
		helper.assertTrue("chain_conveyor.too_close".equals(tooClose), "two blocks apart: " + tooClose);
		String far = ChainConveyorConnectionHandler.validate(helper.getLevel(), helper.absolutePos(A), helper.absolutePos(A).offset(40, 0, 0), player, chain);
		helper.assertTrue("chain_conveyor.too_far".equals(far), "40 blocks apart: " + far);
		String vertical = ChainConveyorConnectionHandler.validate(helper.getLevel(), helper.absolutePos(A), helper.absolutePos(A).offset(0, 6, 0), player, chain);
		helper.assertTrue("chain_conveyor.cannot_connect_vertically".equals(vertical), "above: " + vertical);
		String steep = ChainConveyorConnectionHandler.validate(helper.getLevel(), helper.absolutePos(A), helper.absolutePos(A).offset(3, 6, 0), player, chain);
		helper.assertTrue("chain_conveyor.too_steep".equals(steep), "steep: " + steep);
		leave(helper, player);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void disconnectingGivesTheChainsBack(GameTestHelper helper) {
		buildPair(helper, 16);
		ServerPlayer player = survivalPlayer(helper);
		ChainConveyorConnectionHandler.connect(player, helper.absolutePos(A), helper.absolutePos(B), new ItemStack(Items.IRON_CHAIN), false);
		helper.assertTrue(conveyor(helper, A).connections.isEmpty() && conveyor(helper, B).connections.isEmpty(), "still connected");
		int chains = 0;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems())
			if (stack.is(Items.IRON_CHAIN))
				chains += stack.getCount();
		helper.assertTrue(chains == 2, "the player got " + chains + " chains back instead of 2");
		leave(helper, player);
		helper.succeed();
	}

	// ---- packages on the chains ----

	@GameTest(maxTicks = 400)
	public void packageTravelsToTheFrogportOfTheOtherConveyor(GameTestHelper helper) {
		sendPackageAcrossTheChain(helper, 128);
	}

	@GameTest(maxTicks = 400)
	public void packageTravelsWhenTheConveyorsTurnTheOtherWay(GameTestHelper helper) {
		sendPackageAcrossTheChain(helper, -128);
	}

	private static void sendPackageAcrossTheChain(GameTestHelper helper, int speed) {
		buildPair(helper, speed);
		helper.setBlock(FROGPORT, PackagesRegistry.PACKAGE_FROGPORT);
		FrogportBlockEntity port = helper.getBlockEntity(FROGPORT, FrogportBlockEntity.class);
		port.addressFilter = "X";
		port.target = new ChainConveyorFrogportTarget(helper.absolutePos(B).subtract(helper.absolutePos(FROGPORT)), 180f, (BlockPos) null, false);
		port.filterChanged();

		ChainConveyorBlockEntity a = conveyor(helper, A);
		a.addLoopingPackage(new ChainConveyorPackage(90, addressed("X", new ItemStack(Items.IRON_INGOT, 3))));
		// a package for nobody on the chains keeps going round
		a.addLoopingPackage(new ChainConveyorPackage(270, addressed("Nobody", new ItemStack(Items.COAL))));

		helper.succeedWhen(() -> {
			helper.assertTrue(packagesIn(port, "X") == 1, "the frogport has " + packagesIn(port, "X") + " packages for X");
			helper.assertTrue(packagesIn(port, "Nobody") == 0, "the frogport caught a package for somebody else");
			helper.assertTrue(a.getLoopingPackages().stream().noneMatch(p -> PackageItem.getAddress(p.item).equals("X")),
				"the package is still on the first conveyor");
			int onChains = a.getLoopingPackages().size() + conveyor(helper, B).getLoopingPackages().size()
				+ a.getTravellingPackages().values().stream().mapToInt(List::size).sum()
				+ conveyor(helper, B).getTravellingPackages().values().stream().mapToInt(List::size).sum();
			helper.assertTrue(onChains == 1, onChains + " packages are left on the chains instead of 1");
		});
	}

	@GameTest(maxTicks = 300)
	public void packageGoesTheOtherWayWhenTheFrogportIsOnTheFirstConveyor(GameTestHelper helper) {
		buildPair(helper, 128);
		BlockPos portPos = new BlockPos(1, 2, 3);
		helper.setBlock(portPos, PackagesRegistry.PACKAGE_FROGPORT);
		FrogportBlockEntity port = helper.getBlockEntity(portPos, FrogportBlockEntity.class);
		port.addressFilter = "Home";
		port.target = new ChainConveyorFrogportTarget(helper.absolutePos(A).subtract(helper.absolutePos(portPos)), 0f, (BlockPos) null, false);
		port.filterChanged();
		// the package starts on the second conveyor and has to cross the chain backwards
		conveyor(helper, B).addLoopingPackage(new ChainConveyorPackage(10, addressed("Home", new ItemStack(Items.GOLD_INGOT))));
		helper.succeedWhen(() -> helper.assertTrue(packagesIn(port, "Home") == 1, "the frogport has not caught the package"));
	}

	@GameTest(maxTicks = 120)
	public void packagesWithoutADestinationLoop(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(MOTOR, ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
		helper.setBlock(A, ChainConveyorRegistry.CHAIN_CONVEYOR);
		helper.getBlockEntity(MOTOR, CreativeMotorBlockEntity.class).setSetting(64);
		ChainConveyorBlockEntity a = conveyor(helper, A);
		ChainConveyorPackage box = new ChainConveyorPackage(0, addressed("Z", new ItemStack(Items.STICK)));
		a.addLoopingPackage(box);
		float[] last = {0};
		float[] travelled = {0};
		helper.succeedWhen(() -> {
			float position = box.chainPosition;
			helper.assertTrue(position >= 0 && position < 360, "the position left the circle: " + position);
			helper.assertTrue(a.getLoopingPackages().contains(box) && a.getLoopingPackages().size() == 1, "the package left the conveyor");
			float step = position - last[0];
			if (step < 0)
				step += 360;
			travelled[0] += step;
			last[0] = position;
			helper.assertTrue(travelled[0] >= 720, "the package travelled only " + travelled[0] + " degrees");
			// the package hangs on the wheel: 0.875 blocks from the middle of the conveyor
			Vec3 centre = Vec3.atBottomCenterOf(helper.absolutePos(A));
			double radius = box.worldPosition.subtract(centre).multiply(1, 0, 1).length();
			helper.assertTrue(Math.abs(radius - 0.875) < 1e-3, "the package is " + radius + " blocks from the middle");
		});
	}

	@GameTest(maxTicks = 40)
	public void stoppedConveyorsKeepTheirPackagesStill(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(A, ChainConveyorRegistry.CHAIN_CONVEYOR);
		ChainConveyorBlockEntity a = conveyor(helper, A);
		ChainConveyorPackage box = new ChainConveyorPackage(45, addressed("Z", new ItemStack(Items.STICK)));
		a.addLoopingPackage(box);
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(box.chainPosition == 45, "the package moved to " + box.chainPosition);
			helper.assertTrue(box.worldPosition != null, "the package has no position");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void conveyorSavesConnectionsAndPackages(GameTestHelper helper) {
		buildPair(helper, 16);
		ChainConveyorBlockEntity a = conveyor(helper, A);
		BlockPos toB = connection(helper, A, B);
		a.addLoopingPackage(new ChainConveyorPackage(123, addressed("Loop", new ItemStack(Items.APPLE, 2))));
		a.addTravellingPackage(new ChainConveyorPackage(2.5f, addressed("Travel", new ItemStack(Items.BREAD))), toB);

		var registries = helper.getLevel().registryAccess();
		CompoundTag saved = a.saveCustomOnly(registries);
		ChainConveyorBlockEntity copy = new ChainConveyorBlockEntity(ChainConveyorRegistry.CHAIN_CONVEYOR_BLOCK_ENTITY, a.getBlockPos(), a.getBlockState());
		copy.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved));
		helper.assertTrue(copy.connections.equals(java.util.Set.of(toB)), "connections: " + copy.connections);
		helper.assertTrue(copy.getLoopingPackages().size() == 1 && copy.getLoopingPackages().get(0).chainPosition == 123
			&& PackageItem.getAddress(copy.getLoopingPackages().get(0).item).equals("Loop"), "the looping package was lost");
		List<ChainConveyorPackage> travelling = copy.getTravellingPackages().get(toB);
		helper.assertTrue(travelling != null && travelling.size() == 1 && travelling.get(0).chainPosition == 2.5f
			&& PackageItem.getAddress(travelling.get(0).item).equals("Travel"), "the travelling package was lost");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void breakingAConveyorDropsItsPackagesAndChains(GameTestHelper helper) {
		buildPair(helper, 16);
		ChainConveyorBlockEntity a = conveyor(helper, A);
		a.addLoopingPackage(new ChainConveyorPackage(0, addressed("Lost", new ItemStack(Items.CLAY_BALL))));
		helper.runAfterDelay(3, () -> {
			helper.getLevel().destroyBlock(helper.absolutePos(A), false);
			helper.assertTrue(conveyor(helper, B).connections.isEmpty(), "B still has the chain");
			helper.runAfterDelay(3, () -> {
				boolean dropped = false;
				int chains = 0;
				for (PackageEntity entity : helper.getEntities(PackagesRegistry.PACKAGE, A, 4))
					dropped |= PackageItem.getAddress(entity.getBox()).equals("Lost");
				for (var item : helper.getEntities(net.minecraft.world.entity.EntityTypes.ITEM, B, 8))
					if (item.getItem().is(Items.IRON_CHAIN))
						chains += item.getItem().getCount();
				helper.assertTrue(dropped, "the package was not dropped");
				helper.assertTrue(chains == 2, chains + " chains were dropped instead of 2");
				helper.succeed();
			});
		});
	}

	// ---- pointing at chains ----

	private static Selection pointAt(GameTestHelper helper, ServerPlayer player, Vec3 point, boolean dismantling) {
		player.setPos(point.add(0, 1.5, 1.8));
		player.lookAt(EntityAnchorArgument.Anchor.EYES, point);
		return ChainConveyorInteractionHandler.select(player, dismantling);
	}

	@GameTest(maxTicks = 40)
	public void playersCanPointAtAChain(GameTestHelper helper) {
		buildPair(helper, 16);
		helper.runAfterDelay(3, () -> {
			ChainConveyorBlockEntity a = conveyor(helper, A);
			BlockPos toB = connection(helper, A, B);
			Vec3 point = a.getPackagePosition(2f, toB);
			ServerPlayer player = survivalPlayer(helper);
			Selection selection = pointAt(helper, player, point, false);
			helper.assertTrue(selection != null, "nothing selected");
			helper.assertTrue(selection.lift().equals(helper.absolutePos(A)) && toB.equals(selection.connection()),
				"selected " + selection.lift() + " " + selection.connection());
			helper.assertTrue(Math.abs(selection.chainPosition() - 2) <= 1, "chain position " + selection.chainPosition());
			helper.assertTrue(selection.bakedPosition().distanceTo(point) < 0.5, "baked position " + selection.bakedPosition());

			// the wheel of the conveyor can be pointed at as well: that is a position on the loop
			Vec3 onWheel = a.getPackagePosition(90, null);
			Selection loop = pointAt(helper, player, onWheel.add(0, -.2, 0), false);
			helper.assertTrue(loop != null && loop.connection() == null, "the wheel was not selected: " + loop);
			leave(helper, player);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void frogportInHandPicksTheChainAsTarget(GameTestHelper helper) {
		buildPair(helper, 16);
		helper.runAfterDelay(3, () -> {
			BlockPos toB = connection(helper, A, B);
			Vec3 point = conveyor(helper, A).getPackagePosition(2f, toB);
			ServerPlayer player = survivalPlayer(helper);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PackagesRegistry.PACKAGE_FROGPORT_ITEM));
			player.setPos(point.add(0, 1.5, 1.8));
			player.lookAt(EntityAnchorArgument.Anchor.EYES, point);
			InteractionResult result = ChainConveyorInteractionHandler.onUse(player, InteractionHand.MAIN_HAND);
			helper.assertTrue(result.consumesAction(), "the click did nothing");
			var target = PackagePortTargetSelectionHandler.getSelection(player.getMainHandItem());
			helper.assertTrue(target instanceof ChainConveyorFrogportTarget, "target " + target);
			ChainConveyorFrogportTarget chainTarget = (ChainConveyorFrogportTarget) target;
			helper.assertTrue(chainTarget.relativePos.equals(helper.absolutePos(A)) && toB.equals(chainTarget.connection),
				"target at " + chainTarget.relativePos + " " + chainTarget.connection);
			leave(helper, player);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void packagesCanBePutOnAChainAndTakenOffAgain(GameTestHelper helper) {
		buildPair(helper, 0);
		helper.runAfterDelay(3, () -> {
			ChainConveyorBlockEntity a = conveyor(helper, A);
			BlockPos toB = connection(helper, A, B);
			Vec3 point = a.getPackagePosition(2f, toB);
			ServerPlayer player = survivalPlayer(helper);
			ItemStack box = addressed("Mail", new ItemStack(Items.EMERALD));
			player.setItemInHand(InteractionHand.MAIN_HAND, box);
			player.setPos(point.add(0, 1.5, 1.8));
			player.lookAt(EntityAnchorArgument.Anchor.EYES, point);

			InteractionResult put = ChainConveyorInteractionHandler.onUse(player, InteractionHand.MAIN_HAND);
			helper.assertTrue(put.consumesAction(), "putting the package down did nothing");
			List<ChainConveyorPackage> onChain = a.getTravellingPackages().get(toB);
			helper.assertTrue(onChain != null && onChain.size() == 1, "the package is not on the chain");
			helper.assertTrue(player.getMainHandItem().isEmpty(), "the player still holds the package");
			helper.assertTrue(PackageItem.getAddress(onChain.get(0).item).equals("Mail"), "address lost");

			// let the conveyor place the package, then look at it
			helper.runAfterDelay(3, () -> {
				ChainConveyorPackage pckg = a.getTravellingPackages().get(toB).get(0);
				helper.assertTrue(pckg.worldPosition != null, "the package has no position");
				Vec3 boxAt = pckg.worldPosition.subtract(0, 9 / 16f, 0);
				player.setPos(boxAt.add(0, 1.2, 1.8));
				player.lookAt(EntityAnchorArgument.Anchor.EYES, boxAt);
				InteractionResult take = ChainPackageInteractionHandler.onUse(player, InteractionHand.MAIN_HAND);
				helper.assertTrue(take.consumesAction(), "taking the package did nothing");
				helper.assertTrue(PackageItem.isPackage(player.getMainHandItem()) && PackageItem.getAddress(player.getMainHandItem()).equals("Mail"),
					"the player holds " + player.getMainHandItem());
				helper.assertTrue(a.getTravellingPackages().get(toB).isEmpty(), "the package is still on the chain");
				leave(helper, player);
				helper.succeed();
			});
		});
	}

	// ---- riding ----

	@GameTest(maxTicks = 60)
	public void aWrenchLetsPlayersRideTheChain(GameTestHelper helper) {
		buildPair(helper, 64);
		helper.runAfterDelay(5, () -> {
			ChainConveyorBlockEntity a = conveyor(helper, A);
			BlockPos toB = connection(helper, A, B);
			Vec3 point = a.getPackagePosition(2f, toB);
			ServerPlayer player = survivalPlayer(helper);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WRENCH));
			player.setPos(point.add(0, 1.5, 1.8));
			player.lookAt(EntityAnchorArgument.Anchor.EYES, point);
			InteractionResult result = ChainConveyorInteractionHandler.onUse(player, InteractionHand.MAIN_HAND);
			helper.assertTrue(result.consumesAction(), "the wrench did nothing");
			helper.assertTrue(ChainConveyorRidingHandler.isRiding(player), "the player does not ride");
			helper.assertTrue(player.getVehicle() != null, "the player has no seat");
			helper.assertTrue(ServerChainConveyorHandler.isHanging(player), "the player is not hanging");
			Vec3 start = player.getVehicle().position();

			helper.runAfterDelay(10, () -> {
				helper.assertTrue(ChainConveyorRidingHandler.isRiding(player), "the ride ended");
				helper.assertTrue(player.getVehicle().position().distanceTo(start) > 0.5, "the seat did not move along the chain");
				// the player hangs under the chain: their head is half a block below it
				helper.assertTrue(player.getEyeY() < conveyor(helper, A).getPackagePosition(0, null).y + 1, "the player is not under the chain");
				ChainConveyorRidingHandler.stopRiding(player);
				helper.assertTrue(player.getVehicle() == null && !ServerChainConveyorHandler.isHanging(player), "the player is still on the chain");
				leave(helper, player);
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = 40)
	public void sneakingWithAWrenchTakesTheChainDown(GameTestHelper helper) {
		buildPair(helper, 16);
		helper.runAfterDelay(3, () -> {
			ChainConveyorBlockEntity a = conveyor(helper, A);
			BlockPos toB = connection(helper, A, B);
			Vec3 point = a.getPackagePosition(2f, toB);
			ServerPlayer player = survivalPlayer(helper);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WRENCH));
			player.setShiftKeyDown(true);
			player.setPos(point.add(0, 1.5, 1.8));
			player.lookAt(EntityAnchorArgument.Anchor.EYES, point);
			InteractionResult result = ChainConveyorInteractionHandler.onUse(player, InteractionHand.MAIN_HAND);
			helper.assertTrue(result.consumesAction(), "the wrench did nothing");
			helper.assertTrue(a.connections.isEmpty() && conveyor(helper, B).connections.isEmpty(), "the chain is still there");
			leave(helper, player);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void wrenchingAConveyorAwayGivesTheChainsToThePlayer(GameTestHelper helper) {
		buildPair(helper, 16);
		helper.runAfterDelay(3, () -> {
			ServerPlayer player = survivalPlayer(helper);
			ItemStack wrench = new ItemStack(ModItems.WRENCH);
			player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
			player.setShiftKeyDown(true);
			InteractionResult result = wrench.getItem().useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, wrench,
				hit(helper, A)));
			helper.assertTrue(result.consumesAction(), "the wrench did nothing");
			helper.assertTrue(helper.getBlockState(A).isAir(), "the conveyor is still there");
			helper.assertTrue(conveyor(helper, B).connections.isEmpty(), "B still has the chain");
			int chains = 0;
			boolean conveyor = false;
			for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
				if (stack.is(Items.IRON_CHAIN))
					chains += stack.getCount();
				conveyor |= stack.is(ChainConveyorRegistry.CHAIN_CONVEYOR_ITEM);
			}
			helper.assertTrue(chains == 2, "the player got " + chains + " chains instead of 2");
			helper.assertTrue(conveyor, "the player did not get the conveyor");
			helper.assertTrue(helper.getEntities(net.minecraft.world.entity.EntityTypes.ITEM, A, 8).stream()
				.noneMatch(item -> item.getItem().is(Items.IRON_CHAIN)), "chains were dropped as well");
			leave(helper, player);
			helper.succeed();
		});
	}

	// ---- model ----

	@GameTest(maxTicks = 60)
	public void modelShowsChainsGuardsAndHangingPackages(GameTestHelper helper) {
		buildPair(helper, 16);
		ChainConveyorBlockEntity a = conveyor(helper, A);
		a.addLoopingPackage(new ChainConveyorPackage(0, addressed("Shown", new ItemStack(Items.IRON_NUGGET))));
		helper.succeedWhen(() -> {
			BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(A));
			helper.assertTrue(attachment != null, "the conveyor has no model");
			int chainLinks = 0, packages = 0, rigging = 0, parts = 0;
			for (VirtualElement element : attachment.holder().getElements()) {
				if (element instanceof BlockDisplayElement display && display.getBlockState().is(Blocks.IRON_CHAIN))
					chainLinks++;
				else if (element instanceof ItemDisplayElement display && display.getItem().getItem() instanceof PackageItem)
					packages++;
				else if (element instanceof ItemDisplayElement display && !display.getItem().isEmpty()) {
					parts++;
					if (display.getItem().getItem() == net.minecraft.world.item.Items.PAPER)
						rigging++;
				}
			}
			int expectedLinks = Math.max(1, Math.round(a.connectionStats.get(connection(helper, A, B)).chainLength()));
			helper.assertTrue(chainLinks == expectedLinks, chainLinks + " chain links instead of " + expectedLinks);
			helper.assertTrue(packages == 1, packages + " packages are shown");
			// casing, shaft, wheel, a guard and the rigging of the package
			helper.assertTrue(parts >= 5 && rigging >= 5, "only " + parts + " model parts");
		});
	}

	// ---- package entities landing on item transport ----

	private static PackageEntity dropPackage(GameTestHelper helper, double x, double y, double z) {
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		PackageEntity entity = new PackageEntity(helper.getLevel(), at.x, at.y, at.z);
		entity.setBox(addressed("Falling", new ItemStack(Items.CARROT, 2)));
		helper.getLevel().addFreshEntity(entity);
		return entity;
	}

	@GameTest(maxTicks = 100)
	public void packageEntityLandsOnDepot(GameTestHelper helper) {
		floor(helper);
		BlockPos depotPos = new BlockPos(2, 1, 2);
		helper.setBlock(depotPos, ModBlocks.DEPOT);
		helper.runAfterDelay(2, () -> dropPackage(helper, 2.5, 4, 2.5));
		helper.succeedWhen(() -> {
			ItemStack held = helper.getBlockEntity(depotPos, DepotBlockEntity.class).getHeldItem();
			helper.assertTrue(PackageItem.isPackage(held), "depot holds " + held);
			helper.assertTrue(PackageItem.getAddress(held).equals("Falling"), "the address was lost");
		});
	}

	@GameTest(maxTicks = 100)
	public void packageEntityRestingOnDepotIsTakenIn(GameTestHelper helper) {
		floor(helper);
		BlockPos depotPos = new BlockPos(2, 1, 2);
		helper.setBlock(depotPos, ModBlocks.DEPOT);
		// a package placed on the depot does not fall onto it
		helper.runAfterDelay(2, () -> dropPackage(helper, 2.5, 1 + 13 / 16f, 2.5));
		helper.succeedWhen(() -> {
			ItemStack held = helper.getBlockEntity(depotPos, DepotBlockEntity.class).getHeldItem();
			helper.assertTrue(PackageItem.isPackage(held), "depot holds " + held);
		});
	}

	@GameTest(maxTicks = 220)
	public void packageEntityLandsOnBelt(GameTestHelper helper) {
		floor(helper);
		BlockPos start = new BlockPos(1, 1, 1);
		BlockPos end = new BlockPos(6, 1, 1);
		helper.setBlock(start, ModBlocks.SHAFT.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.Z));
		helper.setBlock(end, ModBlocks.SHAFT.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.Z));
		helper.setBlock(new BlockPos(1, 1, 0), ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.SOUTH));
		helper.runAfterDelay(1, () -> helper.getBlockEntity(new BlockPos(1, 1, 0), CreativeMotorBlockEntity.class).setSetting(-16));
		BeltConnectorItem.createBelts(helper.getLevel(), helper.absolutePos(start), helper.absolutePos(end));
		helper.runAfterDelay(2, () -> dropPackage(helper, 2.5, 4, 1.5));
		helper.succeedWhen(() -> {
			var inventory = helper.getBlockEntity(start, BeltBlockEntity.class).getInventory();
			helper.assertTrue(inventory != null, "no belt inventory");
			boolean found = false;
			for (TransportedItemStack transported : inventory.getTransportedItems())
				found |= PackageItem.isPackage(transported.stack) && PackageItem.getAddress(transported.stack).equals("Falling");
			helper.assertTrue(found, "the package is not on the belt");
			helper.assertTrue(helper.getEntities(PackagesRegistry.PACKAGE, new BlockPos(3, 2, 1), 4).isEmpty(), "the package entity is still there");
		});
	}

	@GameTest(maxTicks = 100)
	public void packageEntityDropsIntoAChute(GameTestHelper helper) {
		floor(helper);
		BlockPos chestPos = new BlockPos(3, 1, 3);
		helper.setBlock(chestPos, Blocks.CHEST);
		helper.setBlock(new BlockPos(3, 2, 3), ChuteRegistry.CHUTE.defaultBlockState());
		helper.runAfterDelay(2, () -> dropPackage(helper, 3.5, 5, 3.5));
		helper.succeedWhen(() -> {
			ChestBlockEntity chest = helper.getBlockEntity(chestPos, ChestBlockEntity.class);
			boolean found = false;
			for (int i = 0; i < chest.getContainerSize(); i++)
				found |= PackageItem.isPackage(chest.getItem(i));
			helper.assertTrue(found, "the chest has not got the package");
			helper.assertTrue(helper.getEntities(PackagesRegistry.PACKAGE, new BlockPos(3, 3, 3), 4).isEmpty(), "the package entity is still there");
		});
	}

	@GameTest(maxTicks = 120)
	public void packageEntityFallsIntoAFunnel(GameTestHelper helper) {
		floor(helper);
		BlockPos chestPos = new BlockPos(2, 1, 2);
		helper.setBlock(chestPos, Blocks.CHEST);
		helper.setBlock(new BlockPos(2, 2, 2), FunnelRegistry.ANDESITE_FUNNEL.defaultBlockState()
			.setValue(FunnelBlock.FACING, Direction.UP)
			.setValue(FunnelBlock.EXTRACTING, false));
		helper.runAfterDelay(2, () -> dropPackage(helper, 2.5, 4.5, 2.5));
		helper.succeedWhen(() -> {
			ChestBlockEntity chest = helper.getBlockEntity(chestPos, ChestBlockEntity.class);
			boolean found = false;
			for (int i = 0; i < chest.getContainerSize(); i++)
				found |= PackageItem.isPackage(chest.getItem(i));
			helper.assertTrue(found, "the chest has not got the package");
			helper.assertTrue(helper.getEntities(PackagesRegistry.PACKAGE, new BlockPos(2, 3, 2), 4).isEmpty(), "the package entity is still there");
		});
	}

	@GameTest
	public void itemHelperSeesThePackageInAPackageEntity(GameTestHelper helper) {
		floor(helper);
		PackageEntity entity = dropPackage(helper, 3.5, 3, 3.5);
		ItemStack box = dev.createpolymer.foundation.item.ItemHelper.fromItemEntity(entity);
		helper.assertTrue(PackageItem.isPackage(box) && PackageItem.getAddress(box).equals("Falling"), "ItemHelper found " + box);
		helper.succeed();
	}
}
