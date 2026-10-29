package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.ItemHandlerHelper;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.logistics.vault.ItemVaultBlock;
import dev.createpolymer.logistics.vault.ItemVaultBlockEntity;
import dev.createpolymer.registry.feature.VaultRegistry;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class VaultGameTests {

	private static BlockState vault(Axis axis) {
		return VaultRegistry.ITEM_VAULT.defaultBlockState().setValue(ItemVaultBlock.HORIZONTAL_AXIS, axis);
	}

	/** A width x width x length block of vaults along Z with its lowest corner at (x0, y0, z0). */
	private static List<BlockPos> placeVaults(GameTestHelper helper, int x0, int y0, int z0, int width, int length) {
		List<BlockPos> placed = new ArrayList<>();
		for (int z = 0; z < length; z++)
			for (int y = 0; y < width; y++)
				for (int x = 0; x < width; x++) {
					BlockPos pos = new BlockPos(x0 + x, y0 + y, z0 + z);
					helper.setBlock(pos, vault(Axis.Z));
					placed.add(pos);
				}
		return placed;
	}

	private static ItemVaultBlockEntity vaultAt(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockEntity(pos, ItemVaultBlockEntity.class);
	}

	private static List<ItemVaultBlockEntity> controllers(GameTestHelper helper, List<BlockPos> positions) {
		List<ItemVaultBlockEntity> controllers = new ArrayList<>();
		for (BlockPos pos : positions)
			if (helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof ItemVaultBlockEntity be && be.isController())
				controllers.add(be);
		return controllers;
	}

	private static IItemHandler handlerAt(GameTestHelper helper, BlockPos pos) {
		return ItemHandlerLookup.get(helper.getLevel(), helper.absolutePos(pos), null);
	}

	private static int countOf(IItemHandler handler, net.minecraft.world.item.Item item) {
		int count = 0;
		for (int i = 0; i < handler.getSlots(); i++)
			if (handler.getStackInSlot(i).is(item))
				count += handler.getStackInSlot(i).getCount();
		return count;
	}

	/** Items in all distinct handlers reachable from the given vault blocks (each multiblock counted once). */
	private static int totalOf(GameTestHelper helper, List<BlockPos> positions, net.minecraft.world.item.Item item) {
		Set<IItemHandler> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		int total = 0;
		for (BlockPos pos : positions) {
			if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof ItemVaultBlockEntity))
				continue;
			IItemHandler handler = handlerAt(helper, pos);
			if (handler != null && seen.add(handler))
				total += countOf(handler, item);
		}
		return total;
	}

	@GameTest
	public void cubeOfEightFormsOneController(GameTestHelper helper) {
		floor(helper);
		List<BlockPos> blocks = placeVaults(helper, 2, 1, 2, 2, 2);
		helper.runAfterDelay(2, () -> {
			List<ItemVaultBlockEntity> controllers = controllers(helper, blocks);
			helper.assertTrue(controllers.size() == 1, "expected one controller, found " + controllers.size());
			ItemVaultBlockEntity controller = controllers.get(0);
			helper.assertTrue(controller.getWidth() == 2 && controller.getHeight() == 2,
				"controller is " + controller.getWidth() + " wide and " + controller.getHeight() + " long");
			helper.assertTrue(controller.getBlockPos().equals(helper.absolutePos(new BlockPos(2, 1, 2))), "controller is at " + controller.getBlockPos() + ", not the lowest corner");
			IItemHandler shared = controller.getCapability();
			helper.assertTrue(shared.getSlots() == 8 * ItemVaultBlockEntity.VAULT_CAPACITY, "slots: " + shared.getSlots());
			for (BlockPos pos : blocks) {
				helper.assertTrue(handlerAt(helper, pos) == shared, "block " + pos + " does not return the controller's handler");
				helper.assertTrue(vaultAt(helper, pos).getControllerBE() == controller, "block " + pos + " has another controller");
				helper.assertTrue(!ItemVaultBlock.isLarge(helper.getBlockState(pos)), "2x2 vaults are not large");
			}
			helper.succeed();
		});
	}

	@GameTest
	public void threeByThreeIsLarge(GameTestHelper helper) {
		floor(helper);
		List<BlockPos> blocks = placeVaults(helper, 1, 1, 1, 3, 3);
		helper.runAfterDelay(2, () -> {
			List<ItemVaultBlockEntity> controllers = controllers(helper, blocks);
			helper.assertTrue(controllers.size() == 1, "expected one controller, found " + controllers.size());
			helper.assertTrue(controllers.get(0).getWidth() == 3 && controllers.get(0).getHeight() == 3, "wrong size");
			helper.assertTrue(controllers.get(0).getCapability().getSlots() == 27 * ItemVaultBlockEntity.VAULT_CAPACITY, "wrong slot count");
			for (BlockPos pos : blocks)
				helper.assertTrue(ItemVaultBlock.isLarge(helper.getBlockState(pos)), "block " + pos + " is not large");
			helper.succeed();
		});
	}

	@GameTest
	public void lengthIsLimitedToThreeTimesTheWidth(GameTestHelper helper) {
		floor(helper);
		// a 1x1 column of 4 vaults cannot be one vault: the longest 1-wide vault is 3
		List<BlockPos> blocks = placeVaults(helper, 3, 1, 1, 1, 4);
		helper.runAfterDelay(2, () -> {
			int blocksInMultis = 0;
			for (ItemVaultBlockEntity c : controllers(helper, blocks)) {
				helper.assertTrue(c.getHeight() <= 3, "a controller is " + c.getHeight() + " long");
				blocksInMultis += c.getWidth() * c.getWidth() * c.getHeight();
			}
			helper.assertTrue(blocksInMultis == 4, "blocks in multis: " + blocksInMultis);
			helper.succeed();
		});
	}

	@GameTest
	public void insertingIntoAnyBlockLandsInTheSharedInventory(GameTestHelper helper) {
		floor(helper);
		List<BlockPos> blocks = placeVaults(helper, 2, 1, 2, 2, 2);
		BlockPos far = new BlockPos(3, 2, 3);
		helper.runAfterDelay(2, () -> {
			IItemHandler viaFar = handlerAt(helper, far);
			ItemStack left = ItemHandlerHelper.insertItemStacked(viaFar, new ItemStack(Items.COBBLESTONE, 64 * 21), false);
			helper.assertTrue(left.isEmpty(), "remainder " + left);
			ItemVaultBlockEntity controller = controllers(helper, blocks).get(0);
			IItemHandler viaController = controller.getCapability();
			helper.assertTrue(countOf(viaController, Items.COBBLESTONE) == 64 * 21, "controller view sees " + countOf(viaController, Items.COBBLESTONE));
			// 21 stacks do not fit in one vault block, so they spread over several parts
			int partsWithItems = 0;
			for (BlockPos pos : blocks) {
				IItemHandler own = vaultAt(helper, pos).getInventoryOfBlock();
				if (countOf(own, Items.COBBLESTONE) > 0)
					partsWithItems++;
			}
			helper.assertTrue(partsWithItems == 2, "items are in " + partsWithItems + " parts");
			helper.assertTrue(countOf(vaultAt(helper, new BlockPos(2, 1, 2)).getInventoryOfBlock(), Items.COBBLESTONE) == 64 * 20,
				"the controller's own inventory is not full");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void hopperAboveFillsTheVault(GameTestHelper helper) {
		floor(helper);
		List<BlockPos> blocks = placeVaults(helper, 2, 1, 2, 2, 2);
		BlockPos hopperPos = new BlockPos(3, 3, 3);
		helper.setBlock(hopperPos, Blocks.HOPPER);
		helper.runAfterDelay(2, () -> helper.getBlockEntity(hopperPos, HopperBlockEntity.class).setItem(0, new ItemStack(Items.DIAMOND, 5)));
		helper.succeedWhen(() -> {
			helper.assertTrue(totalOf(helper, blocks, Items.DIAMOND) == 5, "vault holds " + totalOf(helper, blocks, Items.DIAMOND) + " diamonds");
			helper.assertTrue(helper.getBlockEntity(hopperPos, HopperBlockEntity.class).isEmpty(), "hopper is not empty");
		});
	}

	@GameTest(maxTicks = 100)
	public void hopperBelowDrainsTheVault(GameTestHelper helper) {
		floor(helper);
		placeVaults(helper, 2, 2, 2, 2, 2);
		BlockPos hopperPos = new BlockPos(2, 1, 2);
		helper.setBlock(hopperPos, Blocks.HOPPER);
		helper.runAfterDelay(2, () -> ItemHandlerHelper.insertItemStacked(handlerAt(helper, new BlockPos(3, 3, 3)), new ItemStack(Items.EMERALD, 3), false));
		helper.succeedWhen(() -> helper.assertTrue(helper.getBlockEntity(hopperPos, HopperBlockEntity.class).getItem(0).is(Items.EMERALD),
			"hopper did not take anything out of the vault"));
	}

	@GameTest
	public void breakingABlockSplitsAndKeepsTheRest(GameTestHelper helper) {
		floor(helper);
		List<BlockPos> blocks = placeVaults(helper, 2, 1, 2, 2, 2);
		BlockPos broken = new BlockPos(3, 2, 3);
		helper.runAfterDelay(2, () -> {
			// one distinct stack in the first slot of every block's own inventory
			for (BlockPos pos : blocks)
				vaultAt(helper, pos).getInventoryOfBlock().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
			helper.assertTrue(totalOf(helper, blocks, Items.IRON_INGOT) == 8, "setup failed");
			helper.setBlock(broken, Blocks.AIR);
		});
		helper.runAfterDelay(6, () -> {
			List<BlockPos> rest = new ArrayList<>(blocks);
			rest.remove(broken);
			helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(broken)) == null, "block entity of the broken block is still there");
			// every remaining block belongs to a multiblock whose controller exists, and every block is in exactly one
			int covered = 0;
			Set<BlockPos> seen = new HashSet<>();
			for (ItemVaultBlockEntity c : controllers(helper, rest)) {
				covered += c.getWidth() * c.getWidth() * c.getHeight();
				seen.add(c.getBlockPos());
			}
			helper.assertTrue(covered == rest.size(), "multiblocks cover " + covered + " of " + rest.size() + " blocks");
			for (BlockPos pos : rest) {
				ItemVaultBlockEntity part = vaultAt(helper, pos);
				ItemVaultBlockEntity controller = part.getControllerBE();
				helper.assertTrue(controller != null && controller.isController() && seen.contains(controller.getBlockPos()),
					"block " + pos + " has no valid controller");
				helper.assertTrue(handlerAt(helper, pos) == controller.getCapability(), "block " + pos + " does not share its controller's handler");
			}
			// nothing was lost but the stack of the removed block, which was dropped
			helper.assertTrue(totalOf(helper, rest, Items.IRON_INGOT) == 7, "remaining ingots: " + totalOf(helper, rest, Items.IRON_INGOT));
			List<ItemEntity> drops = helper.getEntities(net.minecraft.world.entity.EntityTypes.ITEM, new BlockPos(3, 2, 3), 3);
			int dropped = 0;
			for (ItemEntity drop : drops)
				dropped += drop.getItem().getCount();
			helper.assertTrue(dropped == 1, "dropped " + dropped + " items, expected exactly the removed block's stack");
			helper.succeed();
		});
	}

	@GameTest
	public void breakingTheControllerReformsTheRest(GameTestHelper helper) {
		floor(helper);
		List<BlockPos> blocks = placeVaults(helper, 2, 1, 2, 2, 2);
		BlockPos controllerPos = new BlockPos(2, 1, 2);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(vaultAt(helper, controllerPos).isController(), "expected the lowest corner to be the controller");
			helper.setBlock(controllerPos, Blocks.AIR);
		});
		helper.runAfterDelay(6, () -> {
			List<BlockPos> rest = new ArrayList<>(blocks);
			rest.remove(controllerPos);
			int covered = 0;
			for (ItemVaultBlockEntity c : controllers(helper, rest))
				covered += c.getWidth() * c.getWidth() * c.getHeight();
			helper.assertTrue(covered == 7, "multiblocks cover " + covered + " of 7 blocks");
			for (BlockPos pos : rest)
				helper.assertTrue(vaultAt(helper, pos).getControllerBE() != null, "block " + pos + " lost its controller");
			helper.succeed();
		});
	}

	@GameTest
	public void placingBlockNextToAVaultJoinsIt(GameTestHelper helper) {
		floor(helper);
		List<BlockPos> blocks = placeVaults(helper, 2, 1, 2, 2, 1);
		BlockPos extra = new BlockPos(2, 1, 4);
		helper.setBlock(extra, vault(Axis.Z));
		helper.setBlock(new BlockPos(3, 1, 4), vault(Axis.Z));
		helper.setBlock(new BlockPos(2, 2, 4), vault(Axis.Z));
		helper.setBlock(new BlockPos(3, 2, 4), vault(Axis.Z));
		helper.runAfterDelay(2, () -> {
			List<BlockPos> all = new ArrayList<>(blocks);
			all.add(extra);
			all.add(new BlockPos(3, 1, 4));
			all.add(new BlockPos(2, 2, 4));
			all.add(new BlockPos(3, 2, 4));
			List<ItemVaultBlockEntity> controllers = controllers(helper, all);
			helper.assertTrue(controllers.size() == 2 || controllers.size() == 1, "controllers: " + controllers.size());
			int covered = 0;
			for (ItemVaultBlockEntity c : controllers)
				covered += c.getWidth() * c.getWidth() * c.getHeight();
			// a gap of one block at z=3 keeps the two layers apart
			helper.assertTrue(covered == 8, "covered " + covered);
			helper.assertTrue(controllers.size() == 2, "two layers with a gap must stay two vaults");
			helper.setBlock(new BlockPos(2, 1, 3), vault(Axis.Z));
			helper.setBlock(new BlockPos(3, 1, 3), vault(Axis.Z));
			helper.setBlock(new BlockPos(2, 2, 3), vault(Axis.Z));
			helper.setBlock(new BlockPos(3, 2, 3), vault(Axis.Z));
		});
		helper.runAfterDelay(5, () -> {
			List<BlockPos> all = placeVaultPositions(2, 1, 2, 2, 3);
			List<ItemVaultBlockEntity> controllers = controllers(helper, all);
			helper.assertTrue(controllers.size() == 1, "expected one 2x2x3 vault, found " + controllers.size() + " controllers");
			helper.assertTrue(controllers.get(0).getWidth() == 2 && controllers.get(0).getHeight() == 3, "wrong size");
			helper.succeed();
		});
	}

	private static List<BlockPos> placeVaultPositions(int x0, int y0, int z0, int width, int length) {
		List<BlockPos> positions = new ArrayList<>();
		for (int z = 0; z < length; z++)
			for (int y = 0; y < width; y++)
				for (int x = 0; x < width; x++)
					positions.add(new BlockPos(x0 + x, y0 + y, z0 + z));
		return positions;
	}

	@GameTest
	public void vaultItemExtendsAnExistingVault(GameTestHelper helper) {
		floor(helper);
		List<BlockPos> layer = placeVaults(helper, 2, 1, 2, 2, 1);
		helper.runAfterDelay(1, () -> {
			// one 2x2 layer is a vault of width 2; right-clicking its south face with 4 vaults in hand places the next layer
			var player = helper.makeMockPlayer(GameType.SURVIVAL);
			ItemStack stack = new ItemStack(VaultRegistry.ITEM_VAULT_ITEM, 4);
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			BlockPos clicked = helper.absolutePos(new BlockPos(3, 1, 2));
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(clicked).add(0, 0, .5), Direction.SOUTH, clicked, false);
			stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
			helper.assertTrue(stack.isEmpty(), "vaults left in hand: " + stack.getCount());
		});
		helper.runAfterDelay(4, () -> {
			List<BlockPos> all = placeVaultPositions(2, 1, 2, 2, 2);
			for (BlockPos pos : all)
				helper.assertBlockPresent(VaultRegistry.ITEM_VAULT, pos);
			List<ItemVaultBlockEntity> controllers = controllers(helper, all);
			helper.assertTrue(controllers.size() == 1, "expected one 2x2x2 vault, found " + controllers.size() + " controllers");
			helper.assertTrue(controllers.get(0).getWidth() == 2 && controllers.get(0).getHeight() == 2, "wrong size");
			helper.assertTrue(layer.size() == 4, "setup");
			helper.succeed();
		});
	}

	@GameTest
	public void comparatorReadsTheWholeVault(GameTestHelper helper) {
		floor(helper);
		List<BlockPos> blocks = placeVaults(helper, 2, 1, 2, 2, 1);
		// a comparator east of the vault reads it from the west (comparators read from the side they face)
		BlockPos comparator = new BlockPos(4, 1, 2);
		helper.setBlock(comparator, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.WEST));
		helper.runAfterDelay(2, () -> {
			// fill half of the 80 slots with full stacks through a block that is not the controller
			IItemHandler viaOther = handlerAt(helper, new BlockPos(3, 2, 2));
			for (int i = 0; i < 40; i++)
				viaOther.insertItem(i, new ItemStack(Items.COBBLESTONE, 64), false);
			BlockState state = helper.getBlockState(blocks.get(0));
			int signal = state.getAnalogOutputSignal(helper.getLevel(), helper.absolutePos(blocks.get(0)), Direction.EAST);
			helper.assertTrue(signal == 8, "analog output was " + signal + ", expected 8");
			// every block of the vault reports the same value
			for (BlockPos pos : blocks)
				helper.assertTrue(helper.getBlockState(pos).getAnalogOutputSignal(helper.getLevel(), helper.absolutePos(pos), Direction.EAST) == 8,
					"block " + pos + " reports another value");
		});
		helper.succeedWhen(() -> {
			BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(comparator));
			helper.assertTrue(be instanceof ComparatorBlockEntity c && c.getOutputSignal() == 8, "comparator was not updated");
		});
	}

	@GameTest
	public void saveAndLoadKeepsStructureAndContents(GameTestHelper helper) {
		floor(helper);
		placeVaults(helper, 2, 1, 2, 2, 2);
		helper.runAfterDelay(2, () -> {
			BlockPos controllerPos = new BlockPos(2, 1, 2);
			BlockPos partPos = new BlockPos(3, 2, 3);
			ItemVaultBlockEntity controller = vaultAt(helper, controllerPos);
			ItemVaultBlockEntity part = vaultAt(helper, partPos);
			part.getInventoryOfBlock().setStackInSlot(4, new ItemStack(Items.GOLD_INGOT, 7));
			var registries = helper.getLevel().registryAccess();

			CompoundTag controllerTag = controller.saveWithFullMetadata(registries);
			ItemVaultBlockEntity loadedController = (ItemVaultBlockEntity) BlockEntity.loadStatic(controller.getBlockPos(),
				controller.getBlockState(), controllerTag, registries);
			helper.assertTrue(loadedController != null && loadedController.isController(), "controller did not load as controller");
			helper.assertTrue(loadedController.getWidth() == 2 && loadedController.getHeight() == 2, "loaded size is wrong");

			CompoundTag partTag = part.saveWithFullMetadata(registries);
			ItemVaultBlockEntity loadedPart = (ItemVaultBlockEntity) BlockEntity.loadStatic(part.getBlockPos(), part.getBlockState(), partTag, registries);
			helper.assertTrue(loadedPart != null && !loadedPart.isController(), "part loaded as a controller");
			helper.assertTrue(loadedPart.getController().equals(controller.getBlockPos()), "part lost its controller position");
			helper.assertTrue(loadedPart.getInventoryOfBlock().getStackInSlot(4).is(Items.GOLD_INGOT)
				&& loadedPart.getInventoryOfBlock().getStackInSlot(4).getCount() == 7, "inventory was not saved");
			helper.assertTrue(loadedPart.getInventoryOfBlock().getSlots() == ItemVaultBlockEntity.VAULT_CAPACITY, "inventory size changed");
			helper.succeed();
		});
	}

	@GameTest
	public void everyVaultBlockHasAModel(GameTestHelper helper) {
		// all positions of all possible vaults, along both axes, map to a model file that exists
		int checked = 0;
		for (Axis axis : new Axis[] { Axis.X, Axis.Z }) {
			for (int width = 1; width <= 3; width++)
				for (int length = 1; length <= 3 * width; length++)
					for (int across = 0; across < width; across++)
						for (int y = 0; y < width; y++)
							for (int along = 0; along < length; along++) {
								BlockPos pos = axis == Axis.X ? new BlockPos(along, y, across) : new BlockPos(across, y, along);
								String name = ItemVaultBlock.Model.modelName(vault(axis), pos, BlockPos.ZERO, width, length);
								String path = "/assets/create/models/block/" + name + ".json";
								helper.assertTrue(ItemVaultBlock.class.getResource(path) != null, "missing model " + path);
								checked++;
							}
		}
		helper.assertTrue(checked > 0, "nothing checked");
		// the isolated block uses the plain tiles; ends of a vault along X are mirrored compared to Z
		String single = ItemVaultBlock.Model.modelName(vault(Axis.Z), BlockPos.ZERO, BlockPos.ZERO, 1, 1);
		helper.assertTrue(single.endsWith("_000000"), "single vault is " + single);
		String zStart = ItemVaultBlock.Model.modelName(vault(Axis.Z), BlockPos.ZERO, BlockPos.ZERO, 1, 3);
		String xStart = ItemVaultBlock.Model.modelName(vault(Axis.X), BlockPos.ZERO, BlockPos.ZERO, 1, 3);
		helper.assertTrue(zStart.endsWith("_000001"), "start of a Z vault is " + zStart);
		helper.assertTrue(xStart.endsWith("_000010"), "start of an X vault is " + xStart);
		helper.succeed();
	}

	@GameTest
	public void wrenchOnTopSplitsTheVault(GameTestHelper helper) {
		floor(helper);
		List<BlockPos> blocks = placeVaults(helper, 2, 1, 2, 2, 2);
		helper.runAfterDelay(2, () -> {
			var player = helper.makeMockPlayer(GameType.SURVIVAL);
			BlockPos clicked = helper.absolutePos(new BlockPos(2, 2, 2));
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(clicked).add(0, .5, 0), Direction.UP, clicked, false);
			UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
			BlockState state = helper.getBlockState(new BlockPos(2, 2, 2));
			((ItemVaultBlock) state.getBlock()).onWrenched(state, context);
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 2, 2)).getValue(ItemVaultBlock.HORIZONTAL_AXIS) == Axis.X, "axis was not turned");
			// the clicked block left the multiblock; the others re-form on the next tick
			helper.assertTrue(vaultAt(helper, new BlockPos(2, 2, 2)).getWidth() == 1, "the clicked block is still part of a vault");
		});
		helper.runAfterDelay(6, () -> {
			// the seven others re-formed into valid vaults
			List<BlockPos> rest = new ArrayList<>(blocks);
			rest.remove(new BlockPos(2, 2, 2));
			int covered = 0;
			for (ItemVaultBlockEntity c : controllers(helper, rest))
				covered += c.getWidth() * c.getWidth() * c.getHeight();
			helper.assertTrue(covered == 7, "multiblocks cover " + covered + " of 7 blocks");
			helper.succeed();
		});
	}
}
