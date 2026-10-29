package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import java.util.List;
import java.util.UUID;

import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import dev.createpolymer.logistics.BigItemStack;
import dev.createpolymer.logistics.box.Glob;
import dev.createpolymer.logistics.box.PackageEntity;
import dev.createpolymer.logistics.box.PackageItem;
import dev.createpolymer.logistics.packager.InventorySummary;
import dev.createpolymer.logistics.packager.PackagerBlock;
import dev.createpolymer.logistics.packager.PackagerBlockEntity;
import dev.createpolymer.logistics.packagePort.PackagePortGui;
import dev.createpolymer.logistics.packagePort.PackagePortTarget.ChainConveyorFrogportTarget;
import dev.createpolymer.logistics.packagePort.frogport.FrogportBlockEntity;
import dev.createpolymer.logistics.packagePort.postbox.PostboxBlock;
import dev.createpolymer.logistics.packagePort.postbox.PostboxBlockEntity;
import dev.createpolymer.logistics.packagerLink.LogisticallyLinkedBlockItem;
import dev.createpolymer.logistics.packagerLink.LogisticallyLinkedBehaviour;
import dev.createpolymer.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import dev.createpolymer.logistics.packagerLink.LogisticsManager;
import dev.createpolymer.logistics.packagerLink.PackagerLinkBlock;
import dev.createpolymer.logistics.packagerLink.PackagerLinkBlockEntity;
import dev.createpolymer.logistics.stockTicker.PackageOrderWithCrafts;
import dev.createpolymer.registry.feature.PackagesRegistry;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class PackagesGameTests {

	private static int countIn(IItemHandler handler, Item item) {
		int count = 0;
		for (int i = 0; i < handler.getSlots(); i++)
			if (handler.getStackInSlot(i).is(item))
				count += handler.getStackInSlot(i).getCount();
		return count;
	}

	private static ChestBlockEntity chest(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockEntity(pos, ChestBlockEntity.class);
	}

	private static int countIn(ChestBlockEntity chest, Item item) {
		return countIn(ItemHandlerLookup.get(chest.getLevel(), chest.getBlockPos(), null), item);
	}

	private static PackagerBlockEntity packager(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockEntity(pos, PackagerBlockEntity.class);
	}

	private static void placePackager(GameTestHelper helper, BlockPos pos, Direction facing) {
		helper.setBlock(pos, PackagesRegistry.PACKAGER.defaultBlockState().setValue(PackagerBlock.FACING, facing));
	}

	/** A player with a connection (a plain mock player has none, and anything that messages the player needs one). */
	@SuppressWarnings("removal")
	private static ServerPlayer connectedPlayer(GameTestHelper helper) {
		return helper.makeMockServerPlayerInLevel();
	}

	private static void leave(GameTestHelper helper, ServerPlayer player) {
		player.connection.disconnect(Component.literal("test over"));
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	private static ItemStack packageOf(ItemStack... stacks) {
		return PackageItem.containing(List.of(stacks));
	}

	@GameTest
	public void addressesMatchWithWildcards(GameTestHelper helper) {
		helper.assertTrue(PackageItem.matchAddress("Warehouse", "Warehouse"), "equal addresses match");
		helper.assertTrue(PackageItem.matchAddress("Warehouse 1", "Warehouse *"), "a wildcard in the filter matches");
		helper.assertTrue(PackageItem.matchAddress("Ware*", "Warehouse 1"), "a wildcard in the address matches");
		helper.assertTrue(PackageItem.matchAddress("anything", "*"), "* matches everything");
		helper.assertTrue(PackageItem.matchAddress("", ""), "no address matches an empty filter");
		helper.assertTrue(!PackageItem.matchAddress("Warehouse", ""), "an empty filter matches only unaddressed packages");
		helper.assertTrue(!PackageItem.matchAddress("Factory", "Warehouse"), "different addresses do not match");
		helper.assertTrue(!PackageItem.matchAddress("Factory", "Ware*"), "a wildcard does not match another name");
		helper.assertTrue("a.b".matches(Glob.toRegexPattern("a.b")) && !"axb".matches(Glob.toRegexPattern("a.b")),
			"dots in a pattern are literal");
		helper.assertTrue("cat".matches(Glob.toRegexPattern("{cat,dog}")) && "c?t".length() == 3,
			"alternatives work");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void packagerPacksChestItemsIntoAnAddressedPackage(GameTestHelper helper) {
		floor(helper);
		BlockPos chestPos = new BlockPos(2, 1, 2);
		BlockPos packagerPos = new BlockPos(3, 1, 2);
		helper.setBlock(chestPos, Blocks.CHEST);
		placePackager(helper, packagerPos, Direction.EAST);
		helper.setBlock(new BlockPos(3, 2, 2), Blocks.OAK_SIGN);
		helper.runAfterDelay(1, () -> {
			ChestBlockEntity chest = chest(helper, chestPos);
			chest.setItem(0, new ItemStack(Items.IRON_INGOT, 40));
			chest.setItem(5, new ItemStack(Items.GOLD_INGOT, 10));
			SignBlockEntity sign = helper.getBlockEntity(new BlockPos(3, 2, 2), SignBlockEntity.class);
			sign.setText(sign.getText(SignTextSlot.FRONT).asMutable().setLine(0, Component.literal("Warehouse")).asImmutable(),
				SignTextSlot.FRONT);
		});
		// the packager is powered by redstone, as in Create
		helper.runAfterDelay(6, () -> helper.setBlock(new BlockPos(3, 1, 1), Blocks.REDSTONE_BLOCK));
		helper.succeedWhen(() -> {
			PackagerBlockEntity packager = packager(helper, packagerPos);
			ItemStack box = packager.heldBox;
			helper.assertTrue(PackageItem.isPackage(box), "the packager holds no package: " + box);
			helper.assertTrue(PackageItem.getAddress(box).equals("Warehouse"), "the package address is '" + PackageItem.getAddress(box) + "'");
			ItemStackHandler contents = PackageItem.getContents(box);
			helper.assertTrue(countIn(contents, Items.IRON_INGOT) == 40, "the package has " + countIn(contents, Items.IRON_INGOT) + " iron");
			helper.assertTrue(countIn(contents, Items.GOLD_INGOT) == 10, "the package has " + countIn(contents, Items.GOLD_INGOT) + " gold");
			helper.assertTrue(countIn(chest(helper, chestPos), Items.IRON_INGOT) == 0, "the chest still holds iron");
			helper.assertTrue(countIn(chest(helper, chestPos), Items.GOLD_INGOT) == 0, "the chest still holds gold");
		});
	}

	@GameTest(maxTicks = 100)
	public void packagerUnpacksAPackageIntoAChest(GameTestHelper helper) {
		floor(helper);
		BlockPos chestPos = new BlockPos(2, 1, 2);
		BlockPos packagerPos = new BlockPos(3, 1, 2);
		helper.setBlock(chestPos, Blocks.CHEST);
		placePackager(helper, packagerPos, Direction.EAST);
		helper.runAfterDelay(5, () -> {
			PackagerBlockEntity packager = packager(helper, packagerPos);
			ItemStack box = packageOf(new ItemStack(Items.IRON_INGOT, 20), new ItemStack(Items.GOLD_INGOT, 5));
			ItemStack remainder = packager.inventory.insertItem(0, box, false);
			helper.assertTrue(remainder.isEmpty(), "the packager did not take the package: " + remainder);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(countIn(chest(helper, chestPos), Items.IRON_INGOT) == 20, "the chest has not got the iron");
			helper.assertTrue(countIn(chest(helper, chestPos), Items.GOLD_INGOT) == 5, "the chest has not got the gold");
			helper.assertTrue(packager(helper, packagerPos).heldBox.isEmpty(), "the packager holds a package");
		});
	}

	@GameTest(maxTicks = 100)
	public void packagerRefusesAPackageThatDoesNotFit(GameTestHelper helper) {
		floor(helper);
		BlockPos chestPos = new BlockPos(2, 1, 2);
		BlockPos packagerPos = new BlockPos(3, 1, 2);
		helper.setBlock(chestPos, Blocks.CHEST);
		placePackager(helper, packagerPos, Direction.EAST);
		helper.runAfterDelay(5, () -> {
			ChestBlockEntity chest = chest(helper, chestPos);
			for (int i = 0; i < chest.getContainerSize(); i++)
				chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
			ItemStack box = packageOf(new ItemStack(Items.IRON_INGOT, 20));
			ItemStack remainder = packager(helper, packagerPos).inventory.insertItem(0, box, false);
			helper.assertTrue(!remainder.isEmpty(), "the packager took a package it cannot unpack");
			helper.succeed();
		});
	}

	@GameTest
	public void openingAPackageGivesItsContents(GameTestHelper helper) {
		ServerPlayer player = connectedPlayer(helper);
		ItemStack box = packageOf(new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.EMERALD, 7));
		player.setItemInHand(InteractionHand.MAIN_HAND, box);
		player.setShiftKeyDown(true);

		InteractionResult result = ((PackageItem) box.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(result.consumesAction(), "opening the package did nothing");
		int diamonds = 0, emeralds = 0;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			helper.assertTrue(!PackageItem.isPackage(stack), "the package is still in the inventory");
			if (stack.is(Items.DIAMOND))
				diamonds += stack.getCount();
			if (stack.is(Items.EMERALD))
				emeralds += stack.getCount();
		}
		leave(helper, player);
		helper.assertTrue(diamonds == 3 && emeralds == 7, "the player got " + diamonds + " diamonds and " + emeralds + " emeralds");
		helper.succeed();
	}

	@GameTest
	public void packagesKeepAddressAndContentsWhenSaved(GameTestHelper helper) {
		ItemStack box = packageOf(new ItemStack(Items.COAL, 12));
		PackageItem.addAddress(box, "Peter");
		PackageItem.setOrder(box, 5, 1, true, 2, false, PackageOrderWithCrafts.simple(List.of(new BigItemStack(new ItemStack(Items.COAL), 12))));
		var ops = helper.getLevel().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
		ItemStack copy = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, box).getOrThrow()).getOrThrow();
		helper.assertTrue(PackageItem.getAddress(copy).equals("Peter"), "the address was lost");
		helper.assertTrue(countIn(PackageItem.getContents(copy), Items.COAL) == 12, "the contents were lost");
		helper.assertTrue(PackageItem.getOrderId(copy) == 5 && PackageItem.getIndex(copy) == 2 && PackageItem.isFinalLink(copy)
			&& !PackageItem.isFinal(copy) && PackageItem.getOrderContext(copy).stacks().size() == 1, "the order data was lost");
		helper.succeed();
	}

	private static BlockPos linkedPackager(GameTestHelper helper, int z, UUID frequency) {
		BlockPos chestPos = new BlockPos(1, 1, z);
		BlockPos packagerPos = new BlockPos(2, 1, z);
		helper.setBlock(chestPos, Blocks.CHEST);
		placePackager(helper, packagerPos, Direction.EAST);
		return packagerPos;
	}

	@GameTest(maxTicks = 100)
	public void twoLinksOnTheSameFrequencySeeTheCombinedSummary(GameTestHelper helper) {
		floor(helper);
		BlockPos packagerA = linkedPackager(helper, 2, null);
		BlockPos packagerB = linkedPackager(helper, 5, null);
		BlockPos linkA = packagerA.above();
		BlockPos linkB = packagerB.above();
		helper.setBlock(linkA, PackagesRegistry.STOCK_LINK.defaultBlockState().setValue(PackagerLinkBlock.FACE, AttachFace.FLOOR));
		UUID[] freq = new UUID[1];
		helper.runAfterDelay(1, () -> {
			chest(helper, new BlockPos(1, 1, 2)).setItem(0, new ItemStack(Items.IRON_INGOT, 10));
			chest(helper, new BlockPos(1, 1, 5)).setItem(0, new ItemStack(Items.IRON_INGOT, 5));
			chest(helper, new BlockPos(1, 1, 5)).setItem(1, new ItemStack(Items.GOLD_INGOT, 3));

			// tune a second stock link to the first one's network and place it on the other packager
			freq[0] = helper.getBlockEntity(linkA, PackagerLinkBlockEntity.class).behaviour.freqId;
			ServerPlayer player = connectedPlayer(helper);
			player.setXRot(90);
			ItemStack stack = new ItemStack(PackagesRegistry.STOCK_LINK_ITEM);
			helper.assertTrue(LogisticallyLinkedBlockItem.networkFromStack(stack) == null, "a fresh link is tuned");
			LogisticallyLinkedBlockItem.assignFrequency(stack, player, freq[0]);
			helper.assertTrue(freq[0].equals(LogisticallyLinkedBlockItem.networkFromStack(stack)), "the frequency was not stored in the item");
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			BlockHitResult hit = new BlockHitResult(helper.absoluteVec(new Vec3(2.5, 2, 5.5)), Direction.UP, helper.absolutePos(packagerB), false);
			InteractionResult placed = stack.getItem().useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, stack, hit));
			leave(helper, player);
			helper.assertTrue(placed.consumesAction(), "the tuned link was not placed: " + placed);
		});
		helper.runAfterDelay(12, () -> {
			PackagerLinkBlockEntity b = helper.getBlockEntity(linkB, PackagerLinkBlockEntity.class);
			helper.assertTrue(freq[0].equals(b.behaviour.freqId), "the placed link got frequency " + b.behaviour.freqId);

			InventorySummary summary = LogisticsManager.getSummaryOfNetwork(freq[0], true);
			helper.assertTrue(summary.getCountOf(new ItemStack(Items.IRON_INGOT)) == 15,
				"the network has " + summary.getCountOf(new ItemStack(Items.IRON_INGOT)) + " iron instead of 15");
			helper.assertTrue(summary.getCountOf(new ItemStack(Items.GOLD_INGOT)) == 3, "the network has not got the gold");
			helper.assertTrue(summary.contributingLinks == 2, "two links contribute, not " + summary.contributingLinks);
			helper.succeed();
		});
	}

	@GameTest
	public void logisticsNetworksAreSavedWithTheWorld(GameTestHelper helper) {
		var server = helper.getLevel().getServer();
		var data = dev.createpolymer.logistics.packagerLink.LogisticsNetworkSavedData.load(server);
		helper.assertTrue(PackagesRegistry.LOGISTICS.logisticsNetworks == data.getLogisticsNetworks(),
			"the manager does not use the saved data of the world");
		UUID id = UUID.randomUUID(), owner = UUID.randomUUID();
		net.minecraft.core.GlobalPos pos = net.minecraft.core.GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(1, 1, 1)));
		PackagesRegistry.LOGISTICS.linkAdded(id, pos, owner);
		PackagesRegistry.LOGISTICS.getQueuedPromises(id).add(new dev.createpolymer.logistics.packagerLink.RequestPromise(
			new BigItemStack(new ItemStack(Items.IRON_INGOT), 5)));
		PackagesRegistry.LOGISTICS.logisticsNetworks.get(id).locked = true;
		helper.assertTrue(data.isDirty(), "adding a link did not mark the data as changed");

		var ops = helper.getLevel().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
		var codec = dev.createpolymer.logistics.packagerLink.LogisticsNetworkSavedData.CODEC;
		var copy = codec.parse(ops, codec.encodeStart(ops, data).getOrThrow()).getOrThrow();
		var network = copy.getLogisticsNetworks().get(id);
		helper.assertTrue(network != null, "the network was not saved");
		helper.assertTrue(owner.equals(network.owner) && network.locked, "owner or lock were lost");
		helper.assertTrue(network.totalLinks.contains(pos) && network.loadedLinks.isEmpty(), "the links were lost");
		helper.assertTrue(network.panelPromises.flatten(false).size() == 1
			&& network.panelPromises.getTotalPromisedAndRemoveExpired(new ItemStack(Items.IRON_INGOT), -1) == 5, "the promises were lost");
		PackagesRegistry.LOGISTICS.linkRemoved(id, pos);
		helper.assertTrue(!data.getLogisticsNetworks().containsKey(id), "the empty network was not dropped");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aRequestOnTheNetworkMakesAddressedPackages(GameTestHelper helper) {
		floor(helper);
		BlockPos packagerA = linkedPackager(helper, 2, null);
		helper.setBlock(packagerA.above(), PackagesRegistry.STOCK_LINK.defaultBlockState().setValue(PackagerLinkBlock.FACE, AttachFace.FLOOR));
		UUID[] freq = new UUID[1];
		helper.runAfterDelay(1, () -> {
			chest(helper, new BlockPos(1, 1, 2)).setItem(0, new ItemStack(Items.IRON_INGOT, 10));
			freq[0] = helper.getBlockEntity(packagerA.above(), PackagerLinkBlockEntity.class).behaviour.freqId;
		});
		helper.runAfterDelay(10, () -> {
			PackageOrderWithCrafts order = PackageOrderWithCrafts.simple(List.of(new BigItemStack(new ItemStack(Items.IRON_INGOT), 4)));
			boolean sent = LogisticsManager.broadcastPackageRequest(freq[0], RequestType.PLAYER, order, null, "Shop");
			helper.assertTrue(sent, "the request was not accepted");
			PackagerBlockEntity packager = packager(helper, packagerA);
			ItemStack box = packager.heldBox;
			helper.assertTrue(PackageItem.isPackage(box), "the packager holds no package");
			helper.assertTrue(PackageItem.getAddress(box).equals("Shop"), "address " + PackageItem.getAddress(box));
			helper.assertTrue(countIn(PackageItem.getContents(box), Items.IRON_INGOT) == 4, "the package does not hold 4 iron");
			helper.assertTrue(PackageItem.hasOrderData(box) && PackageItem.isFinal(box), "the package carries no order data");
			helper.assertTrue(countIn(chest(helper, new BlockPos(1, 1, 2)), Items.IRON_INGOT) == 6, "the chest was not drained by 4");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void packageEntitySavesAndLoads(GameTestHelper helper) {
		floor(helper);
		var level = helper.getLevel();
		PackageEntity entity = new PackageEntity(level, helper.absoluteVec(new Vec3(3.5, 1, 3.5)).x, helper.absoluteVec(new Vec3(3.5, 1, 3.5)).y,
			helper.absoluteVec(new Vec3(3.5, 1, 3.5)).z);
		ItemStack box = packageOf(new ItemStack(Items.COPPER_INGOT, 9));
		PackageItem.addAddress(box, "Saved");
		entity.setBox(box);
		entity.setYRot(135);

		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		entity.saveWithoutId(out);
		CompoundTag tag = out.buildResult();

		PackageEntity loaded = PackagesRegistry.PACKAGE.create(level, EntitySpawnReason.LOAD);
		loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
		helper.assertTrue(PackageItem.isPackage(loaded.getBox()), "the loaded entity holds " + loaded.getBox());
		helper.assertTrue(loaded.getBox().getItem() == box.getItem(), "the style changed");
		helper.assertTrue(PackageItem.getAddress(loaded.getBox()).equals("Saved"), "the address was lost");
		helper.assertTrue(countIn(PackageItem.getContents(loaded.getBox()), Items.COPPER_INGOT) == 9, "the contents were lost");
		helper.assertTrue(loaded.getYRot() == 135, "the rotation was lost");
		helper.assertTrue(Math.abs(loaded.getBbWidth() - PackageItem.getWidth(box)) < 1e-4
			&& Math.abs(loaded.getBbHeight() - PackageItem.getHeight(box)) < 1e-4, "the hitbox does not fit the package");

		level.addFreshEntity(loaded);
		helper.runAfterDelay(3, () -> {
			PackageEntity.Model model = loaded.getModel();
			helper.assertTrue(model != null, "no display model was attached");
			helper.assertTrue(model.getElements().size() == 2, "the model has " + model.getElements().size() + " elements");
			boolean display = false, hitbox = false;
			for (VirtualElement element : model.getElements()) {
				display |= element instanceof ItemDisplayElement item && item.getItem().getItem() == box.getItem();
				hitbox |= element instanceof eu.pb4.polymer.virtualentity.api.elements.InteractionElement;
			}
			helper.assertTrue(display, "the package model is not shown");
			helper.assertTrue(hitbox, "there is no hitbox");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void packageEntityFallsAndCanBePickedUp(GameTestHelper helper) {
		floor(helper);
		var level = helper.getLevel();
		Vec3 start = helper.absoluteVec(new Vec3(3.5, 4, 3.5));
		PackageEntity entity = new PackageEntity(level, start.x, start.y, start.z);
		entity.setBox(packageOf(new ItemStack(Items.IRON_NUGGET, 3)));
		level.addFreshEntity(entity);
		helper.succeedWhen(() -> {
			helper.assertTrue(entity.onGround(), "the package has not landed, y=" + entity.getY());
			helper.assertTrue(entity.getY() - helper.absoluteVec(new Vec3(0, 1, 0)).y < 0.05, "the package floats at " + entity.getY());
			helper.assertTrue(entity.getDeltaMovement().horizontalDistance() < 0.01, "the package is still sliding");
		});
		helper.runAfterDelay(70, () -> {
			ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
			helper.assertTrue(entity.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO) == InteractionResult.SUCCESS, "interaction failed");
			helper.assertTrue(PackageItem.isPackage(player.getMainHandItem()), "the player did not pick the package up");
			helper.assertTrue(entity.isRemoved(), "the package entity is still there");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void hittingAPackageBreaksItOpen(GameTestHelper helper) {
		floor(helper);
		var level = helper.getLevel();
		Vec3 pos = helper.absoluteVec(new Vec3(3.5, 1, 3.5));
		PackageEntity entity = new PackageEntity(level, pos.x, pos.y, pos.z);
		entity.setBox(packageOf(new ItemStack(Items.LAPIS_LAZULI, 6)));
		level.addFreshEntity(entity);
		helper.runAfterDelay(30, () -> {
			DamageSource source = level.damageSources().generic();
			entity.hurtServer(level, source, 4);
			helper.assertTrue(entity.isRemoved(), "the package survived");
			int dropped = 0;
			for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, entity.getBoundingBox().inflate(3)))
				if (item.getItem().is(Items.LAPIS_LAZULI))
					dropped += item.getItem().getCount();
			helper.assertTrue(dropped == 6, "the contents dropped: " + dropped);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void droppedPackageItemsBecomePackageEntities(GameTestHelper helper) {
		floor(helper);
		var level = helper.getLevel();
		Vec3 pos = helper.absoluteVec(new Vec3(3.5, 2, 3.5));
		ItemStack box = packageOf(new ItemStack(Items.STICK, 2));
		ItemEntity item = new ItemEntity(level, pos.x, pos.y, pos.z, box);
		level.addFreshEntity(item);
		helper.succeedWhen(() -> {
			List<PackageEntity> entities = level.getEntitiesOfClass(PackageEntity.class, new net.minecraft.world.phys.AABB(pos, pos).inflate(2, 3, 2));
			helper.assertTrue(entities.size() == 1, "there are " + entities.size() + " package entities");
			helper.assertTrue(countIn(PackageItem.getContents(entities.get(0).getBox()), Items.STICK) == 2, "the contents are gone");
			helper.assertTrue(item.isRemoved(), "the dropped item is still there");
		});
	}

	private static BlockPos frogport(GameTestHelper helper) {
		BlockPos pos = new BlockPos(3, 1, 3);
		helper.setBlock(pos, PackagesRegistry.PACKAGE_FROGPORT);
		return pos;
	}

	@GameTest(maxTicks = 80)
	public void frogportCatchesPackagesIntoItsInventory(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = frogport(helper);
		helper.runAfterDelay(3, () -> {
			FrogportBlockEntity port = helper.getBlockEntity(pos, FrogportBlockEntity.class);
			port.startAnimation(packageOf(new ItemStack(Items.BREAD, 2)), false);
			helper.assertTrue(port.isAnimationInProgress(), "the frog does not move");
		});
		helper.succeedWhen(() -> {
			FrogportBlockEntity port = helper.getBlockEntity(pos, FrogportBlockEntity.class);
			helper.assertTrue(!port.isAnimationInProgress(), "the frog is still catching");
			int packages = 0;
			for (int i = 0; i < port.inventory.getSlots(); i++)
				if (PackageItem.isPackage(port.inventory.getStackInSlot(i)))
					packages++;
			helper.assertTrue(packages == 1, "the frogport holds " + packages + " packages");
		});
	}

	@GameTest
	public void frogportRemembersItsTargetAndAddress(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = frogport(helper);
		FrogportBlockEntity port = helper.getBlockEntity(pos, FrogportBlockEntity.class);
		port.addressFilter = "Peter";
		port.acceptsPackages = false;
		port.goggles = true;
		port.passiveYaw = 33.75f;
		port.target = new ChainConveyorFrogportTarget(new BlockPos(0, 2, 3), 2.5f, new BlockPos(0, 0, 4), true);
		port.inventory.setStackInSlot(4, packageOf(new ItemStack(Items.CLAY_BALL, 4)));

		var registries = helper.getLevel().registryAccess();
		CompoundTag saved = port.saveCustomOnly(registries);
		FrogportBlockEntity copy = new FrogportBlockEntity(PackagesRegistry.PACKAGE_FROGPORT_BLOCK_ENTITY, port.getBlockPos(), port.getBlockState());
		copy.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved));
		helper.assertTrue(copy.addressFilter.equals("Peter") && !copy.acceptsPackages && copy.goggles && copy.passiveYaw == 33.75f, "the settings were lost");
		helper.assertTrue(copy.target instanceof ChainConveyorFrogportTarget t && t.chainPos == 2.5f && t.flipped
			&& t.relativePos.equals(new BlockPos(0, 2, 3)) && new BlockPos(0, 0, 4).equals(t.connection), "the target was lost: " + copy.target);
		helper.assertTrue(countIn(PackageItem.getContents(copy.inventory.getStackInSlot(4)), Items.CLAY_BALL) == 4, "the stored package was lost");
		helper.succeed();
	}

	@GameTest
	public void frogportOnlyHandsOutPackagesThatAreNotItsOwn(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = frogport(helper);
		FrogportBlockEntity port = helper.getBlockEntity(pos, FrogportBlockEntity.class);
		port.addressFilter = "Peter";
		IItemHandler outside = ItemHandlerLookup.get(helper.getLevel(), helper.absolutePos(pos), Direction.NORTH);
		ItemStack forPeter = packageOf(new ItemStack(Items.APPLE));
		PackageItem.addAddress(forPeter, "Peter");
		ItemStack forPaul = packageOf(new ItemStack(Items.APPLE));
		PackageItem.addAddress(forPaul, "Paul");
		helper.assertTrue(outside.insertItem(0, forPeter, false).getCount() == 1, "a package for the port was taken from outside");
		helper.assertTrue(outside.insertItem(0, forPaul, false).isEmpty(), "a package for somebody else was refused");
		port.inventory.setStackInSlot(1, forPeter);
		helper.assertTrue(!outside.extractItem(1, 1, true).isEmpty(), "the package for the port cannot be taken out");
		port.inventory.setStackInSlot(1, forPaul);
		helper.assertTrue(outside.extractItem(1, 1, true).isEmpty(), "a package for somebody else can be taken out");
		helper.assertTrue(outside.insertItem(2, new ItemStack(Items.STICK), false).getCount() == 1, "non-packages are accepted");
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void postboxRaisesItsFlagForPackagesAndOpensWithTheGui(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		helper.setBlock(pos, PackagesRegistry.PACKAGE_POSTBOXES.get(DyeColor.RED).defaultBlockState().setValue(PostboxBlock.FACING, Direction.NORTH));
		helper.runAfterDelay(3, () -> {
			PostboxBlockEntity postbox = helper.getBlockEntity(pos, PostboxBlockEntity.class);
			helper.assertTrue(postbox.flag.getChaseTarget() == 0, "the flag is up on an empty postbox");
			postbox.inventory.insertItem(0, packageOf(new ItemStack(Items.PAPER)), false);

			ServerPlayer player = connectedPlayer(helper);
			PackagePortGui gui = new PackagePortGui(player, postbox);
			gui.open();
			helper.assertTrue(postbox.getOpenTracker().openCount == 1, "the postbox does not know it is open");
			helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(pos)).getValue(PostboxBlock.OPEN), "the postbox lid is closed");
			helper.assertTrue(PackageItem.isPackage(gui.getCustomSlot(0).getItem()), "the first GUI slot does not show the package");
			gui.close();
			leave(helper, player);
		});
		helper.succeedWhen(() -> {
			PostboxBlockEntity postbox = helper.getBlockEntity(pos, PostboxBlockEntity.class);
			helper.assertTrue(postbox.flag.getChaseTarget() == 1 && postbox.flag.getValue() > .5f, "the flag is not up: " + postbox.flag.getValue());
		});
	}
}
