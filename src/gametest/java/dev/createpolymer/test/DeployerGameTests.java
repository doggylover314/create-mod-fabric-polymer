package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.deployer.DeployerBlock;
import dev.createpolymer.kinetics.deployer.DeployerBlockEntity;
import dev.createpolymer.kinetics.deployer.DeployerBlockEntity.Mode;
import dev.createpolymer.kinetics.deployer.DeployerFakePlayer;
import dev.createpolymer.kinetics.motor.CreativeMotorBlockEntity;
import dev.createpolymer.kinetics.press.MechanicalPressBlock;
import dev.createpolymer.logistics.depot.DepotBlockEntity;
import dev.createpolymer.processing.sequenced.SequencedAssemblyRecipe;
import dev.createpolymer.processing.sequenced.SequencedAssemblyRecipe.SequencedAssembly;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.DeployerRegistry;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class DeployerGameTests {

	private static BlockState deployer(Direction facing) {
		return DeployerRegistry.DEPLOYER.defaultBlockState()
			.setValue(DeployerBlock.FACING, facing)
			.setValue(DeployerBlock.AXIS_ALONG_FIRST_COORDINATE, false);
	}

	/** A deployer facing {@code facing} (horizontal or down) turns around the Z axis here; the motor sits north of it. */
	private static void powered(GameTestHelper helper, BlockPos deployerPos, Direction facing) {
		helper.setBlock(deployerPos, deployer(facing));
		helper.setBlock(deployerPos.north(), ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.SOUTH));
		helper.runAfterDelay(2, () -> helper.getBlockEntity(deployerPos.north(), CreativeMotorBlockEntity.class).setSetting(256));
	}

	private static IItemHandler handlerAt(GameTestHelper helper, BlockPos pos) {
		return ItemHandlerLookup.get(helper.getLevel(), helper.absolutePos(pos), null);
	}

	private static void hold(GameTestHelper helper, BlockPos deployerPos, ItemStack stack) {
		ItemStack remainder = handlerAt(helper, deployerPos).insertItem(0, stack, false);
		helper.assertTrue(remainder.isEmpty(), "the deployer did not take " + stack + ", left " + remainder);
	}

	private static boolean holds(IItemHandler handler, Item item) {
		for (int i = 0; i < handler.getSlots(); i++)
			if (handler.getStackInSlot(i).is(item))
				return true;
		return false;
	}

	@GameTest(maxTicks = 400)
	public void deployerUsesBoneMealOnCrop(GameTestHelper helper) {
		floor(helper);
		BlockPos deployerPos = new BlockPos(3, 2, 3);
		BlockPos cropPos = new BlockPos(5, 2, 3);
		helper.setBlock(cropPos.below(), Blocks.FARMLAND);
		helper.setBlock(cropPos, Blocks.WHEAT);
		powered(helper, deployerPos, Direction.EAST);
		helper.runAfterDelay(4, () -> hold(helper, deployerPos, new ItemStack(Items.BONE_MEAL, 16)));
		helper.succeedWhen(() -> {
			BlockState crop = helper.getBlockState(cropPos);
			helper.assertTrue(crop.is(Blocks.WHEAT) && crop.getValue(CropBlock.AGE) > 0, "the crop did not grow: " + crop);
		});
	}

	@GameTest(maxTicks = 500)
	public void deployerInPunchModeBreaksBlockAndKeepsDrops(GameTestHelper helper) {
		floor(helper);
		BlockPos deployerPos = new BlockPos(3, 2, 3);
		BlockPos target = new BlockPos(5, 2, 3);
		helper.setBlock(target, Blocks.DIRT);
		powered(helper, deployerPos, Direction.EAST);
		helper.runAfterDelay(4, () -> {
			helper.getBlockEntity(deployerPos, DeployerBlockEntity.class).changeMode();
			helper.assertTrue(helper.getBlockEntity(deployerPos, DeployerBlockEntity.class).getMode() == Mode.PUNCH, "mode did not change");
			hold(helper, deployerPos, new ItemStack(Items.IRON_SHOVEL));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(target).isAir(), "the block is still there: " + helper.getBlockState(target));
			helper.assertTrue(holds(handlerAt(helper, deployerPos), Items.DIRT), "the dirt did not end up in the deployer");
			helper.assertItemEntityNotPresent(Items.DIRT, target, 3);
		});
	}

	@GameTest(maxTicks = 500)
	public void deployerInPunchModeAttacksEntitiesAndCollectsLoot(GameTestHelper helper) {
		floor(helper);
		BlockPos deployerPos = new BlockPos(3, 2, 3);
		BlockPos target = new BlockPos(5, 2, 3);
		// a single hit kills it: it would be knocked out of the deployer's reach otherwise
		Chicken chicken = helper.spawnWithNoFreeWill(EntityTypes.CHICKEN, target);
		powered(helper, deployerPos, Direction.EAST);
		helper.runAfterDelay(4, () -> {
			helper.getBlockEntity(deployerPos, DeployerBlockEntity.class).changeMode();
			hold(helper, deployerPos, new ItemStack(Items.DIAMOND_SWORD));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(!chicken.isAlive(), "the chicken is still alive, health " + chicken.getHealth());
			IItemHandler inventory = handlerAt(helper, deployerPos);
			helper.assertTrue(holds(inventory, Items.CHICKEN), "the loot did not end up in the deployer");
		});
	}

	@GameTest(maxTicks = 300)
	public void deployerAppliesDeployingRecipeOnDepot(GameTestHelper helper) {
		floor(helper);
		BlockPos depotPos = new BlockPos(3, 1, 3);
		BlockPos deployerPos = new BlockPos(3, 3, 3);
		helper.setBlock(depotPos, ModBlocks.DEPOT);
		powered(helper, deployerPos, Direction.DOWN);
		helper.runAfterDelay(4, () -> {
			// create:deploying/cogwheel: a shaft and planks make a cogwheel
			hold(helper, deployerPos, new ItemStack(Items.OAK_PLANKS, 4));
			handlerAt(helper, depotPos).insertItem(0, new ItemStack(ModItems.SHAFT), false);
		});
		helper.succeedWhen(() -> {
			DepotBlockEntity depot = helper.getBlockEntity(depotPos, DepotBlockEntity.class);
			helper.assertTrue(holds(depot.depotBehaviour.itemHandler, ModItems.COGWHEEL), "no cogwheel on the depot: " + depot.getHeldItem());
			helper.assertTrue(handlerAt(helper, deployerPos).getStackInSlot(0).getCount() == 3,
				"the planks were not used up: " + handlerAt(helper, deployerPos).getStackInSlot(0));
		});
	}

	@GameTest(maxTicks = 300)
	public void deployerAppliesItemApplicationRecipeOnDepot(GameTestHelper helper) {
		floor(helper);
		BlockPos depotPos = new BlockPos(3, 1, 3);
		BlockPos deployerPos = new BlockPos(3, 3, 3);
		helper.setBlock(depotPos, ModBlocks.DEPOT);
		powered(helper, deployerPos, Direction.DOWN);
		helper.runAfterDelay(4, () -> {
			// create:item_application/andesite_casing_from_log
			hold(helper, deployerPos, new ItemStack(ModItems.ANDESITE_ALLOY));
			handlerAt(helper, depotPos).insertItem(0, new ItemStack(Items.STRIPPED_OAK_LOG), false);
		});
		helper.succeedWhen(() -> {
			DepotBlockEntity depot = helper.getBlockEntity(depotPos, DepotBlockEntity.class);
			helper.assertTrue(holds(depot.depotBehaviour.itemHandler, ModItems.ANDESITE_CASING), "no casing on the depot: " + depot.getHeldItem());
		});
	}

	@GameTest(maxTicks = 300)
	public void deployerKeepsHeldItemWhenRecipeSaysSo(GameTestHelper helper) {
		floor(helper);
		BlockPos depotPos = new BlockPos(3, 1, 3);
		BlockPos deployerPos = new BlockPos(3, 3, 3);
		helper.setBlock(depotPos, ModBlocks.DEPOT);
		powered(helper, deployerPos, Direction.DOWN);
		helper.runAfterDelay(4, () -> {
			// create:deploying/waxed_copper_block_from_adding_wax keeps the honeycomb
			hold(helper, deployerPos, new ItemStack(Items.HONEYCOMB_BLOCK));
			handlerAt(helper, depotPos).insertItem(0, new ItemStack(Items.COPPER_BLOCK.weathering().unaffected()), false);
		});
		helper.succeedWhen(() -> {
			DepotBlockEntity depot = helper.getBlockEntity(depotPos, DepotBlockEntity.class);
			helper.assertTrue(holds(depot.depotBehaviour.itemHandler, Items.COPPER_BLOCK.waxed().unaffected()), "not waxed: " + depot.getHeldItem());
			helper.assertTrue(handlerAt(helper, deployerPos).getStackInSlot(0).is(Items.HONEYCOMB_BLOCK), "the honeycomb block was used up");
		});
	}

	@GameTest(maxTicks = 40)
	public void fakePlayerUsesTheOwner(GameTestHelper helper) {
		floor(helper);
		BlockPos deployerPos = new BlockPos(3, 2, 3);
		helper.setBlock(deployerPos, deployer(Direction.EAST));
		helper.runAfterDelay(2, () -> {
			DeployerFakePlayer player = helper.getBlockEntity(deployerPos, DeployerBlockEntity.class).getPlayer();
			helper.assertTrue(player != null, "no fake player");
			helper.assertTrue(player.getEyeHeight() == 0, "eyes at " + player.getEyeHeight());
			helper.assertTrue(player.getGameProfile().name().equals("Deployer"), "named " + player.getGameProfile().name());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void playerSwapsHeldItemWithDeployer(GameTestHelper helper) {
		floor(helper);
		BlockPos deployerPos = new BlockPos(3, 2, 3);
		helper.setBlock(deployerPos, deployer(Direction.EAST));
		helper.runAfterDelay(2, () -> {
			var player = helper.makeMockPlayer(GameType.SURVIVAL);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COPPER_INGOT, 5));
			BlockPos abs = helper.absolutePos(deployerPos);
			BlockHitResult front = new BlockHitResult(Vec3.atCenterOf(abs).add(.5, 0, 0), Direction.EAST, abs, false);
			helper.useBlock(deployerPos, player, front);

			DeployerFakePlayer fake = helper.getBlockEntity(deployerPos, DeployerBlockEntity.class).getPlayer();
			helper.assertTrue(fake.getMainHandItem().is(Items.COPPER_INGOT) && fake.getMainHandItem().getCount() == 5,
				"the deployer holds " + fake.getMainHandItem());
			helper.assertTrue(player.getMainHandItem().isEmpty(), "the item stayed in the hand");

			// the back of the deployer is no place to swap
			helper.useBlock(deployerPos, player, new BlockHitResult(Vec3.atCenterOf(abs).add(-.5, 0, 0), Direction.WEST, abs, false));
			helper.assertTrue(fake.getMainHandItem().getCount() == 5, "an item was swapped at the back");

			helper.useBlock(deployerPos, player, front);
			helper.assertTrue(fake.getMainHandItem().isEmpty(), "the deployer still holds " + fake.getMainHandItem());
			helper.assertTrue(player.getMainHandItem().is(Items.COPPER_INGOT) && player.getMainHandItem().getCount() == 5,
				"the player holds " + player.getMainHandItem());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void contentsSurviveSaving(GameTestHelper helper) {
		floor(helper);
		BlockPos deployerPos = new BlockPos(3, 2, 3);
		helper.setBlock(deployerPos, deployer(Direction.EAST));
		helper.runAfterDelay(2, () -> {
			DeployerBlockEntity deployer = helper.getBlockEntity(deployerPos, DeployerBlockEntity.class);
			deployer.changeMode();
			hold(helper, deployerPos, new ItemStack(Items.BONE_MEAL, 5));

			var registries = helper.getLevel().registryAccess();
			CompoundTag saved = deployer.saveWithFullMetadata(registries);
			BlockEntity loaded = BlockEntity.loadStatic(deployer.getBlockPos(), deployer.getBlockState(), saved, registries);
			helper.assertTrue(loaded instanceof DeployerBlockEntity, "block entity did not load");
			DeployerBlockEntity copy = (DeployerBlockEntity) loaded;
			helper.assertTrue(copy.getMode() == Mode.PUNCH, "mode was lost");
			copy.setLevel(helper.getLevel());
			copy.initialize();
			ItemStack held = copy.getPlayer().getMainHandItem();
			helper.assertTrue(held.is(Items.BONE_MEAL) && held.getCount() == 5, "held stack was lost: " + held);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void assemblyProgressSurvivesSaving(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		ItemStack stack = new ItemStack(DeployerRegistry.INCOMPLETE_TRACK_ITEM);
		stack.set(DeployerRegistry.SEQUENCED_ASSEMBLY, new SequencedAssembly(Identifier.parse("create:sequenced_assembly/test_two_steps"), 3, .75f));

		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		out.store("Item", ItemStack.CODEC, stack);
		ItemStack read = TagValueInput.create(ProblemReporter.DISCARDING, registries, out.buildResult())
			.read("Item", ItemStack.CODEC)
			.orElseThrow();
		SequencedAssembly assembly = read.get(DeployerRegistry.SEQUENCED_ASSEMBLY);
		helper.assertTrue(assembly != null && assembly.step() == 3 && assembly.progress() == .75f
			&& assembly.id().getPath().equals("sequenced_assembly/test_two_steps"), "progress was lost: " + assembly);
		helper.assertTrue(DeployerRegistry.INCOMPLETE_TRACK_ITEM.getProgress(read) == .75f, "progress is " + DeployerRegistry.INCOMPLETE_TRACK_ITEM.getProgress(read));
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void modelShowsHandAndHeldItem(GameTestHelper helper) {
		floor(helper);
		BlockPos deployerPos = new BlockPos(3, 2, 3);
		powered(helper, deployerPos, Direction.EAST);
		helper.runAfterDelay(4, () -> hold(helper, deployerPos, new ItemStack(Items.BONE_MEAL, 16)));
		helper.succeedWhen(() -> {
			BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(deployerPos));
			helper.assertTrue(attachment != null, "the deployer has no model");
			int shown = 0;
			boolean boneMeal = false;
			for (VirtualElement element : attachment.holder().getElements())
				if (element instanceof ItemDisplayElement display && !display.getItem().isEmpty()) {
					shown++;
					boneMeal |= display.getItem().is(Items.BONE_MEAL);
				}
			// casing, shaft, pole, hand and the held item
			helper.assertTrue(shown == 5 && boneMeal, "the model shows " + shown + " items, bone meal: " + boneMeal);
		});
	}

	@GameTest(maxTicks = 40)
	public void sequencedAssemblyRecipeLoads(GameTestHelper helper) {
		helper.runAfterDelay(2, () -> {
			boolean found = false;
			for (var holder : helper.getLevel().recipeAccess().getRecipes())
				if (holder.value() instanceof SequencedAssemblyRecipe recipe && holder.id().identifier().getPath().equals("sequenced_assembly/test_two_steps")) {
					found = true;
					helper.assertTrue(recipe.getSequence().size() == 2, "sequence has " + recipe.getSequence().size() + " steps");
					helper.assertTrue(recipe.getTransitionalItem().is(DeployerRegistry.INCOMPLETE_TRACK_ITEM), "wrong transitional item");
				}
			helper.assertTrue(found, "test recipe was not loaded");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 600)
	public void sequencedAssemblyPressesAndDeploys(GameTestHelper helper) {
		floor(helper);
		BlockPos pressDepot = new BlockPos(2, 1, 2);
		BlockPos pressPos = new BlockPos(2, 3, 2);
		BlockPos deployerDepot = new BlockPos(5, 1, 3);
		BlockPos deployerPos = new BlockPos(5, 3, 3);

		helper.setBlock(pressDepot, ModBlocks.DEPOT);
		helper.setBlock(new BlockPos(1, 3, 2), ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
		helper.setBlock(pressPos, ModBlocks.MECHANICAL_PRESS.defaultBlockState().setValue(MechanicalPressBlock.HORIZONTAL_FACING, Direction.EAST));
		helper.setBlock(deployerDepot, ModBlocks.DEPOT);
		powered(helper, deployerPos, Direction.DOWN);
		helper.runAfterDelay(2, () -> helper.getBlockEntity(new BlockPos(1, 3, 2), CreativeMotorBlockEntity.class).setSetting(256));
		helper.runAfterDelay(4, () -> {
			hold(helper, deployerPos, new ItemStack(Items.STICK, 2));
			handlerAt(helper, pressDepot).insertItem(0, new ItemStack(Items.DIAMOND), false);
		});

		boolean[] moved = { false };
		helper.succeedWhen(() -> {
			DepotBlockEntity first = helper.getBlockEntity(pressDepot, DepotBlockEntity.class);
			DepotBlockEntity second = helper.getBlockEntity(deployerDepot, DepotBlockEntity.class);
			if (!moved[0]) {
				ItemStack pressed = first.getHeldItem();
				helper.assertTrue(pressed.is(DeployerRegistry.INCOMPLETE_TRACK_ITEM), "the press has not made the incomplete item yet: " + pressed);
				SequencedAssembly assembly = pressed.get(DeployerRegistry.SEQUENCED_ASSEMBLY);
				helper.assertTrue(assembly != null && assembly.step() == 1 && Math.abs(assembly.progress() - .5f) < 1e-4,
					"progress after pressing: " + assembly);
				helper.assertTrue(assembly.id().getPath().equals("sequenced_assembly/test_two_steps"), "recipe id " + assembly.id());
				// Create moves the item on a belt to the next machine; do that here
				ItemStack taken = handlerAt(helper, pressDepot).extractItem(0, 1, false);
				helper.assertTrue(second.depotBehaviour.itemHandler.insertItem(0, taken, false).isEmpty(), "the second depot refused the item");
				moved[0] = true;
			}
			helper.assertTrue(holds(second.depotBehaviour.itemHandler, Items.EMERALD), "the deployer has not finished the assembly: " + second.getHeldItem());
			helper.assertTrue(handlerAt(helper, deployerPos).getStackInSlot(0).getCount() == 1, "the sticks were not used up");
		});
	}
}
