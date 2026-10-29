package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import java.util.List;
import java.util.Optional;

import dev.createpolymer.equipment.armor.BacktankBlockEntity;
import dev.createpolymer.equipment.armor.BacktankUtil;
import dev.createpolymer.equipment.armor.DivingBootsItem;
import dev.createpolymer.equipment.armor.DivingHelmetItem;
import dev.createpolymer.equipment.armor.NetheriteDivingHandler;
import dev.createpolymer.equipment.extendoGrip.ExtendoGripItem;
import dev.createpolymer.equipment.potatoCannon.AllPotatoProjectileTypes;
import dev.createpolymer.equipment.potatoCannon.PotatoCannonItem;
import dev.createpolymer.equipment.potatoCannon.PotatoCannonProjectileType;
import dev.createpolymer.equipment.potatoCannon.PotatoProjectileEntity;
import dev.createpolymer.equipment.sandPaper.SandPaperItem;
import dev.createpolymer.equipment.sandPaper.SandPaperPolishingRecipe;
import dev.createpolymer.equipment.toolbox.ToolboxBlock;
import dev.createpolymer.equipment.zapper.ShootableGadgetItemMethods;
import dev.createpolymer.equipment.toolbox.ToolboxBlockEntity;
import dev.createpolymer.equipment.toolbox.ToolboxDyeingRecipe;
import dev.createpolymer.equipment.toolbox.ToolboxGui;
import dev.createpolymer.equipment.toolbox.ToolboxInventory;
import dev.createpolymer.equipment.toolbox.ToolboxSlot;
import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.feature.EquipmentRegistry;
import dev.createpolymer.registry.feature.MaterialsRegistry;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class EquipmentGameTests {

	private static void check(GameTestHelper helper, boolean condition, String message) {
		helper.assertTrue(condition, Component.literal(message));
	}

	private static ServerPlayer player(GameTestHelper helper) {
		return (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
	}

	@GameTest
	public void sandPaperRecipePolishesRoseQuartz(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ItemStack quartz = new ItemStack(MaterialsRegistry.ROSE_QUARTZ);
		check(helper, SandPaperPolishingRecipe.canPolish(level, quartz), "rose quartz can be polished");
		check(helper, !SandPaperPolishingRecipe.canPolish(level, new ItemStack(Items.STONE)), "stone cannot");
		ItemStack polished = SandPaperPolishingRecipe.applyPolish(level, Vec3.ZERO, quartz, new ItemStack(EquipmentRegistry.SAND_PAPER));
		check(helper, polished.is(MaterialsRegistry.POLISHED_ROSE_QUARTZ), "recipe result is polished rose quartz, was " + polished);
		helper.succeed();
	}

	@GameTest
	public void sandPaperPolishesTheItemInTheOtherHand(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack paper = new ItemStack(EquipmentRegistry.SAND_PAPER);
		player.setItemInHand(InteractionHand.MAIN_HAND, paper);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(MaterialsRegistry.ROSE_QUARTZ, 2));

		InteractionResult result = paper.getItem()
			.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		check(helper, result.consumesAction(), "using sand paper with quartz in the other hand starts polishing");
		check(helper, paper.has(EquipmentRegistry.SAND_PAPER_POLISHING), "the quartz is held by the sand paper");
		check(helper, player.getOffhandItem().getCount() == 1, "one quartz was taken");

		((SandPaperItem) paper.getItem()).finishUsingItem(paper, helper.getLevel(), player);
		check(helper, !paper.has(EquipmentRegistry.SAND_PAPER_POLISHING), "polishing finished");
		check(helper, paper.getDamageValue() == 1, "the sand paper took damage");
		int polished = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++)
			if (player.getInventory().getItem(i).is(MaterialsRegistry.POLISHED_ROSE_QUARTZ))
				polished += player.getInventory().getItem(i).getCount();
		check(helper, polished == 1, "the player received the polished quartz, got " + polished);
		helper.succeed();
	}

	@GameTest
	public void sandPaperScrapesCopper(GameTestHelper helper) {
		floor(helper);
		BlockPos rel = new BlockPos(2, 1, 2);
		helper.setBlock(rel, Blocks.COPPER_BLOCK.weathering().exposed());
		ServerPlayer player = player(helper);
		ItemStack paper = new ItemStack(EquipmentRegistry.SAND_PAPER);
		player.setItemInHand(InteractionHand.MAIN_HAND, paper);
		BlockPos pos = helper.absolutePos(rel);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
		InteractionResult result = paper.getItem()
			.useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, paper, hit));
		check(helper, result.consumesAction(), "scraping succeeded");
		helper.assertBlockPresent(Blocks.COPPER_BLOCK.weathering().unaffected(), rel);
		helper.succeed();
	}

	@GameTest
	public void potatoTypesAreLoadedFromData(GameTestHelper helper) {
		var access = helper.getLevel().registryAccess();
		check(helper, PotatoCannonProjectileType.getTypeForItem(access, Items.POTATO).isPresent(), "potato is ammo");
		check(helper, PotatoCannonProjectileType.getTypeForItem(access, Items.COD).isPresent(), "fish is ammo");
		check(helper, PotatoCannonProjectileType.getTypeForItem(access, Items.STONE).isEmpty(), "stone is not");
		var potato = PotatoCannonProjectileType.getTypeForItem(access, Items.POTATO).get().value();
		check(helper, potato.damage() == 5 && potato.reloadTicks() == 15 && potato.onBlockHit().isPresent(), "potato values are Create's");
		check(helper, access.lookupOrThrow(EquipmentRegistry.POTATO_PROJECTILE_TYPE).get(AllPotatoProjectileTypes.FALLBACK).isPresent(), "fallback exists");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void potatoCannonShootsAProjectileThatHurtsATarget(GameTestHelper helper) {
		floor(helper);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Vec3 feet = helper.absoluteVec(new Vec3(4.5, 1, 1.5));
		player.snapTo(feet.x, feet.y, feet.z, 0, 0);
		// the barrel is held to the side of the player, so the target stands in front of the barrel
		Vec3 barrel = ShootableGadgetItemMethods.getGunBarrelVec(player, true, new Vec3(.75f, -0.15f, 1.5f));
		Cow target = helper.spawnWithNoFreeWill(EntityTypes.COW, BlockPos.containing(barrel.add(0, -.7, 3)));
		target.snapTo(barrel.x, barrel.y - .7, barrel.z + 3);
		ItemStack cannon = new ItemStack(EquipmentRegistry.POTATO_CANNON);
		player.setItemInHand(InteractionHand.MAIN_HAND, cannon);
		player.getInventory().add(new ItemStack(Items.POTATO, 3));
		float health = target.getHealth();

		InteractionResult result = cannon.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		check(helper, result.consumesAction(), "the cannon fired");
		check(helper, !helper.getLevel().getEntitiesOfClass(PotatoProjectileEntity.class, target.getBoundingBox().inflate(10)).isEmpty(),
			"a projectile entity exists");
		int potatoes = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++)
			if (player.getInventory().getItem(i).is(Items.POTATO))
				potatoes += player.getInventory().getItem(i).getCount();
		check(helper, potatoes == 2, "one potato was used, " + potatoes + " left");
		check(helper, cannon.getDamageValue() == 1, "the cannon took damage without a backtank");

		helper.succeedWhen(() -> check(helper, target.getHealth() < health, "the cow should be hurt, health " + target.getHealth()));
	}

	@GameTest
	public void backtankItemKeepsAirWhenPlacedAndBroken(GameTestHelper helper) {
		floor(helper);
		BlockPos rel = new BlockPos(2, 1, 2);
		ItemStack stack = new ItemStack(EquipmentRegistry.COPPER_BACKTANK_ITEM);
		stack.set(EquipmentRegistry.BACKTANK_AIR, 321);
		helper.setBlock(rel, EquipmentRegistry.COPPER_BACKTANK.defaultBlockState());
		BlockPos pos = helper.absolutePos(rel);
		BlockState state = helper.getBlockState(rel);
		EquipmentRegistry.COPPER_BACKTANK.setPlacedBy(helper.getLevel(), pos, state, null, stack);

		BacktankBlockEntity be = helper.getBlockEntity(rel, BacktankBlockEntity.class);
		check(helper, be.getAirLevel() == 321, "the block took the air of the item, was " + be.getAirLevel());

		ItemStack clone = state.getCloneItemStack(helper.getLevel(), pos, true);
		check(helper, clone.is(EquipmentRegistry.COPPER_BACKTANK_ITEM), "pick block gives the backtank item, was " + clone);
		check(helper, clone.getOrDefault(EquipmentRegistry.BACKTANK_AIR, 0) == 321, "pick block keeps the air");

		List<ItemStack> drops = Block.getDrops(state, helper.getLevel(), pos, be);
		check(helper, drops.size() == 1 && drops.get(0).is(EquipmentRegistry.COPPER_BACKTANK_ITEM), "loot table drops the backtank item, was " + drops);
		check(helper, drops.get(0).getOrDefault(EquipmentRegistry.BACKTANK_AIR, 0) == 321, "the dropped item keeps the air");
		helper.succeed();
	}

	@GameTest(maxTicks = 150)
	public void rotatingBacktankBlockCollectsAir(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(2, 3, 2), ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.DOWN));
		BlockPos tank = new BlockPos(2, 2, 2);
		helper.setBlock(tank, EquipmentRegistry.COPPER_BACKTANK.defaultBlockState());
		helper.succeedWhen(() -> {
			BacktankBlockEntity be = helper.getBlockEntity(tank, BacktankBlockEntity.class);
			check(helper, Math.abs(be.getSpeed()) > 0, "backtank rotates");
			check(helper, be.getAirLevel() >= 3, "air level should rise, is " + be.getAirLevel());
			ItemStack clone = helper.getBlockState(tank).getCloneItemStack(helper.getLevel(), helper.absolutePos(tank), true);
			check(helper, clone.getOrDefault(EquipmentRegistry.BACKTANK_AIR, 0) == be.getAirLevel(), "the picked up tank has the same air");
		});
	}

	@GameTest(maxTicks = 100)
	public void divingHelmetDrainsBacktankAirUnderwater(GameTestHelper helper) {
		floor(helper);
		for (int x = 1; x <= 3; x++)
			for (int y = 1; y <= 4; y++)
				for (int z = 1; z <= 3; z++)
					helper.setBlock(new BlockPos(x, y, z), Blocks.WATER);
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(EquipmentRegistry.COPPER_DIVING_HELMET));
		ItemStack tank = new ItemStack(EquipmentRegistry.COPPER_BACKTANK_ITEM);
		tank.set(EquipmentRegistry.BACKTANK_AIR, 100);
		zombie.setItemSlot(EquipmentSlot.CHEST, tank);
		zombie.setAirSupply(5);

		helper.succeedWhen(() -> {
			DivingHelmetItem.breatheUnderwater(zombie);
			check(helper, zombie.isEyeInFluid(net.minecraft.tags.FluidTags.WATER), "the zombie's head is underwater");
			check(helper, zombie.getAirSupply() == zombie.getMaxAirSupply(), "air supply refilled, is " + zombie.getAirSupply());
			check(helper, BacktankUtil.getAir(zombie.getItemBySlot(EquipmentSlot.CHEST)) < 100, "backtank air was used");
		});
	}

	@GameTest
	public void backtankAirIsUsedUpAndWarns(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack tank = new ItemStack(EquipmentRegistry.COPPER_BACKTANK_ITEM);
		tank.set(EquipmentRegistry.BACKTANK_AIR, 5);
		player.setItemSlot(EquipmentSlot.CHEST, tank);
		check(helper, BacktankUtil.getAllWithAir(player).size() == 1, "tank with air is found");
		check(helper, BacktankUtil.canAbsorbDamage(player, 200), "a tank absorbs damage");
		check(helper, BacktankUtil.getAir(tank) == 5 - Math.max(900 / 200, 1), "cost is a fraction of the air, left " + BacktankUtil.getAir(tank));
		BacktankUtil.consumeAir(player, tank, 100);
		check(helper, BacktankUtil.getAir(tank) == 0 && BacktankUtil.getAllWithAir(player).isEmpty(), "empty tanks are ignored");
		helper.succeed();
	}

	@GameTest
	public void netheriteSetGrantsFireResistance(GameTestHelper helper) {
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
		ItemStack tank = new ItemStack(EquipmentRegistry.NETHERITE_BACKTANK_ITEM);
		tank.set(EquipmentRegistry.BACKTANK_AIR, 50);
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(EquipmentRegistry.NETHERITE_DIVING_HELMET));
		zombie.setItemSlot(EquipmentSlot.CHEST, tank);
		zombie.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
		NetheriteDivingHandler.tick(zombie);
		check(helper, !zombie.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE), "boots are missing");
		zombie.setItemSlot(EquipmentSlot.FEET, new ItemStack(EquipmentRegistry.NETHERITE_DIVING_BOOTS));
		NetheriteDivingHandler.tick(zombie);
		check(helper, zombie.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE), "full set is fire resistant");
		zombie.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		NetheriteDivingHandler.tick(zombie);
		check(helper, !zombie.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE), "taking off the backtank ends it");
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void heavyBootsSinkInWater(GameTestHelper helper) {
		floor(helper);
		for (int x = 1; x <= 3; x++)
			for (int y = 1; y <= 4; y++)
				for (int z = 1; z <= 3; z++)
					helper.setBlock(new BlockPos(x, y, z), Blocks.WATER);
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
		zombie.setItemSlot(EquipmentSlot.FEET, new ItemStack(EquipmentRegistry.COPPER_DIVING_BOOTS));
		helper.succeedWhen(() -> {
			DivingBootsItem.accelerateDescentUnderwater(zombie);
			check(helper, zombie.isInWater(), "the zombie is in water");
			check(helper, zombie.getAttributeValue(Attributes.GRAVITY) > 0.5, "gravity is raised in water");
			check(helper, zombie.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY) == 1, "walking in water is easy");
		});
	}

	@GameTest
	public void extendoGripExtendsReach(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		double block = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
		double entity = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EquipmentRegistry.EXTENDO_GRIP));
		ExtendoGripItem.tick(player);
		check(helper, player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) == block + 3, "one grip adds 3 blocks");
		check(helper, player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) == entity + 3, "also for entities");
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(EquipmentRegistry.EXTENDO_GRIP));
		ExtendoGripItem.tick(player);
		check(helper, player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) == block + 5, "two grips add 5");
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		ExtendoGripItem.tick(player);
		check(helper, player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) == block, "reach is back to normal");
		helper.succeed();
	}

	@GameTest
	public void toolboxStoresAndReturnsItems(GameTestHelper helper) {
		floor(helper);
		BlockPos rel = new BlockPos(2, 1, 2);
		helper.setBlock(rel, EquipmentRegistry.TOOLBOXES.get(DyeColor.BROWN).defaultBlockState());
		ToolboxBlockEntity be = helper.getBlockEntity(rel, ToolboxBlockEntity.class);
		ToolboxInventory inventory = be.getInventory();

		check(helper, inventory.insertItem(0, new ItemStack(Items.COBBLESTONE, 64), false).isEmpty(), "first stack fits");
		check(helper, inventory.insertItem(1, new ItemStack(Items.COBBLESTONE, 30), false).isEmpty(), "second stack fits");
		check(helper, !inventory.insertItem(2, new ItemStack(Items.DIRT, 5), false).isEmpty(), "other items do not share a compartment");
		check(helper, inventory.insertItem(4, new ItemStack(Items.DIRT, 5), false).isEmpty(), "they go into the next one");
		check(helper, inventory.getFilter(0).is(Items.COBBLESTONE) && inventory.getFilter(1).is(Items.DIRT), "compartments remember their item");

		ItemStack taken = inventory.takeFromCompartment(70, 0, false);
		check(helper, taken.is(Items.COBBLESTONE) && taken.getCount() == 70, "70 cobblestone come back, got " + taken);
		check(helper, inventory.takeFromCompartment(100, 0, true).getCount() == 24, "24 are left");

		// moving the toolbox keeps its contents
		BlockPos pos = helper.absolutePos(rel);
		BlockState state = helper.getBlockState(rel);
		ItemStack item = ((ToolboxBlock) state.getBlock()).asItem().getDefaultInstance();
		ItemStack clone = state.getCloneItemStack(helper.getLevel(), pos, true);
		check(helper, clone.has(EquipmentRegistry.TOOLBOX_INVENTORY), "the item stores the contents");
		List<ItemStack> drops = Block.getDrops(state, helper.getLevel(), pos, be);
		check(helper, drops.size() == 1 && drops.get(0).has(EquipmentRegistry.TOOLBOX_INVENTORY), "the loot table keeps the contents, drops " + drops);
		check(helper, drops.get(0).get(EquipmentRegistry.TOOLBOX_INVENTORY).getStackInSlot(4).getCount() == 5, "dirt is in the dropped item");

		BlockPos other = new BlockPos(5, 1, 2);
		helper.setBlock(other, EquipmentRegistry.TOOLBOXES.get(DyeColor.RED).defaultBlockState());
		((ToolboxBlock) helper.getBlockState(other).getBlock()).setPlacedBy(helper.getLevel(), helper.absolutePos(other),
			helper.getBlockState(other), null, drops.get(0));
		ToolboxInventory placed = helper.getBlockEntity(other, ToolboxBlockEntity.class).getInventory();
		check(helper, placed.getStackInSlot(4).getCount() == 5 && placed.getFilter(1).is(Items.DIRT), "a placed toolbox gets the contents");
		check(helper, item.is(EquipmentRegistry.TOOLBOX_ITEMS.get(DyeColor.BROWN)), "the block item is registered");
		helper.succeed();
	}

	@GameTest
	public void dyeingAToolboxKeepsItsContents(GameTestHelper helper) {
		floor(helper);
		BlockPos rel = new BlockPos(2, 1, 2);
		helper.setBlock(rel, EquipmentRegistry.TOOLBOXES.get(DyeColor.BROWN).defaultBlockState());
		helper.getBlockEntity(rel, ToolboxBlockEntity.class).getInventory().insertItem(0, new ItemStack(Items.COAL, 12), false);

		ServerPlayer player = player(helper);
		ItemStack dye = new ItemStack(Items.DYE.red());
		player.setItemInHand(InteractionHand.MAIN_HAND, dye);
		BlockPos pos = helper.absolutePos(rel);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
		InteractionResult result = helper.getBlockState(rel).useItemOn(dye, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
		check(helper, result.consumesAction(), "the toolbox was dyed");
		helper.assertBlockPresent(EquipmentRegistry.TOOLBOXES.get(DyeColor.RED), rel);
		ToolboxInventory inventory = helper.getBlockEntity(rel, ToolboxBlockEntity.class).getInventory();
		check(helper, inventory.getStackInSlot(0).getCount() == 12, "the coal is still inside");
		helper.succeed();
	}

	@GameTest
	public void toolboxDyeingRecipe(GameTestHelper helper) {
		ItemStack toolbox = new ItemStack(EquipmentRegistry.TOOLBOXES.get(DyeColor.BROWN));
		ToolboxInventory contents = new ToolboxInventory(null);
		contents.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
		toolbox.set(EquipmentRegistry.TOOLBOX_INVENTORY, contents);
		CraftingInput input = CraftingInput.of(2, 1, List.of(toolbox, new ItemStack(Items.DYE.blue())));
		ToolboxDyeingRecipe recipe = ToolboxDyeingRecipe.INSTANCE;
		check(helper, recipe.matches(input, helper.getLevel()), "toolbox and dye match");
		ItemStack result = recipe.assemble(input);
		check(helper, result.is(EquipmentRegistry.TOOLBOX_ITEMS.get(DyeColor.BLUE)), "result is blue, was " + result);
		check(helper, result.get(EquipmentRegistry.TOOLBOX_INVENTORY).getStackInSlot(0).getCount() == 3, "contents are kept");
		check(helper, !recipe.matches(CraftingInput.of(2, 1, List.of(toolbox, new ItemStack(Items.STONE))), helper.getLevel()), "stone is no dye");
		helper.succeed();
	}

	@GameTest
	public void toolboxGuiSlotsAreTheCompartments(GameTestHelper helper) {
		floor(helper);
		BlockPos rel = new BlockPos(2, 1, 2);
		helper.setBlock(rel, EquipmentRegistry.TOOLBOXES.get(DyeColor.BROWN).defaultBlockState());
		ToolboxBlockEntity be = helper.getBlockEntity(rel, ToolboxBlockEntity.class);
		ServerPlayer player = player(helper);
		ToolboxGui gui = new ToolboxGui(player, be);
		check(helper, be.getOpenCount() == 1, "the box counts as open");

		// compartment 1 is the first of the right half of the first row
		ToolboxSlot slot = (ToolboxSlot) gui.getSlotRedirectOrPlayer(5);
		check(helper, slot.mayPlace(new ItemStack(Items.STICK)), "empty compartments take anything");
		slot.set(new ItemStack(Items.STICK, 10));
		check(helper, be.getInventory().getFilter(1).is(Items.STICK), "compartment 1 now holds sticks");
		ToolboxSlot sibling = (ToolboxSlot) gui.getSlotRedirectOrPlayer(6);
		check(helper, sibling.mayPlace(new ItemStack(Items.STICK)) && !sibling.mayPlace(new ItemStack(Items.STONE)), "the compartment takes only sticks");
		check(helper, slot.getMaxStackSize(new ItemStack(Items.STICK)) == 64, "a full stack can be put into the first slot");
		check(helper, slot.remove(4).getCount() == 4 && slot.getItem().getCount() == 6, "taking from the slot works");

		gui.onRemoved();
		check(helper, be.getOpenCount() == 0, "closing the gui closes the box");
		helper.succeed();
	}

	@GameTest
	public void treeFertilizerGrowsASapling(GameTestHelper helper) {
		for (int x = 0; x < 8; x++)
			for (int z = 0; z < 8; z++)
				helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
		BlockPos rel = new BlockPos(3, 1, 3);
		BlockPos pos = helper.absolutePos(rel);
		ServerPlayer player = player(helper);
		ItemStack fertilizer = new ItemStack(EquipmentRegistry.TREE_FERTILIZER, 64);
		player.setItemInHand(InteractionHand.MAIN_HAND, fertilizer);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);

		// a tree feature can fail on its own, so the sapling is put back and the fertilizer kept: try again
		boolean grown = false;
		for (int attempt = 0; attempt < 20 && !grown; attempt++) {
			helper.setBlock(rel, Blocks.OAK_SAPLING);
			int before = fertilizer.getCount();
			InteractionResult result = fertilizer.getItem()
				.useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, fertilizer, hit));
			if (result.consumesAction()) {
				grown = true;
				check(helper, helper.getLevel().getBlockState(pos).is(net.minecraft.tags.BlockTags.LOGS), "a trunk stands where the sapling was");
				check(helper, fertilizer.getCount() == before - 1, "one fertilizer was used");
			} else {
				check(helper, fertilizer.getCount() == before, "failed growth keeps the fertilizer");
				check(helper, helper.getLevel().getBlockState(pos).is(Blocks.OAK_SAPLING), "failed growth keeps the sapling");
			}
		}
		check(helper, grown, "the sapling grew within 20 attempts");
		helper.succeed();
	}

	@GameTest
	public void buildersTeaIsADrink(GameTestHelper helper) {
		ItemStack tea = new ItemStack(EquipmentRegistry.BUILDERS_TEA);
		var consumable = tea.get(DataComponents.CONSUMABLE);
		check(helper, consumable != null && consumable.consumeTicks() == 42, "tea takes 42 ticks to drink");
		check(helper, consumable.animation() == net.minecraft.world.item.ItemUseAnimation.DRINK, "tea is drunk");
		check(helper, tea.has(DataComponents.FOOD) && tea.getMaxStackSize() == 16, "tea is food that stacks to 16");
		check(helper, tea.has(DataComponents.USE_REMAINDER), "a glass bottle is left");
		helper.succeed();
	}
}
