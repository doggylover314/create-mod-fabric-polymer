package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.base.RotatedPillarKineticBlock;
import dev.createpolymer.kinetics.motor.CreativeMotorBlockEntity;
import dev.createpolymer.processing.basin.BasinBlockEntity;
import dev.createpolymer.processing.burner.BlazeBurnerBlock;
import dev.createpolymer.processing.burner.BlazeBurnerBlock.HeatLevel;
import dev.createpolymer.processing.burner.BlazeBurnerBlockEntity;
import dev.createpolymer.processing.burner.LitBlazeBurnerBlock;
import dev.createpolymer.processing.burner.LitBlazeBurnerBlock.FlameType;
import dev.createpolymer.processing.recipe.HeatCondition;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.BurnerRegistry;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class BurnerGameTests {

	private static final BlockPos BURNER = new BlockPos(2, 1, 2);
	private static final BlockPos BASIN = new BlockPos(2, 2, 2);

	private static void burner(GameTestHelper helper, BlockPos pos, HeatLevel heat) {
		helper.setBlock(pos, BurnerRegistry.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, heat));
	}

	private static HeatLevel heat(GameTestHelper helper, BlockPos pos) {
		return BlazeBurnerBlock.getHeatLevelOf(helper.getBlockState(pos));
	}

	private static void assertHeat(GameTestHelper helper, BlockPos pos, HeatLevel expected) {
		HeatLevel actual = heat(helper, pos);
		helper.assertTrue(actual == expected, "Heat at " + pos + " was " + actual + ", expected " + expected);
	}

	/** Throws the stack into the burner like a player would: right-click on the burner with the item. */
	private static void feed(GameTestHelper helper, BlockPos pos, Item item) {
		BlockState state = helper.getBlockState(pos);
		BlazeBurnerBlock.TryInsertResult result =
			BlazeBurnerBlock.tryInsert(state, helper.getLevel(), helper.absolutePos(pos), new ItemStack(item), false, true, false);
		helper.assertTrue(result.result() == InteractionResult.SUCCESS, item + " was not accepted as fuel");
	}

	private static boolean contains(BasinBlockEntity basin, Item item) {
		for (ItemStack s : basin.outputInventory.stacks())
			if (s.is(item))
				return true;
		return false;
	}

	/** Burner (given heat) - basin - gap - mixer, driven by a motor and a cogwheel next to the mixer. */
	private static BasinBlockEntity mixerOnBurner(GameTestHelper helper, HeatLevel burnerHeat) {
		floor(helper);
		burner(helper, BURNER, burnerHeat);
		helper.setBlock(BASIN, ModBlocks.BASIN);
		helper.setBlock(new BlockPos(2, 4, 2), ModBlocks.MECHANICAL_MIXER);
		helper.setBlock(new BlockPos(3, 3, 2), ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
		helper.setBlock(new BlockPos(3, 4, 2), ModBlocks.COGWHEEL.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.Y));
		helper.runAfterDelay(2, () -> helper.getBlockEntity(new BlockPos(3, 3, 2), CreativeMotorBlockEntity.class).setSetting(128));
		return helper.getBlockEntity(BASIN, BasinBlockEntity.class);
	}

	@GameTest(maxTicks = 500)
	public void regularFuelKindlesThenFades(GameTestHelper helper) {
		floor(helper);
		burner(helper, BURNER, HeatLevel.SMOULDERING);
		assertHeat(helper, BURNER, HeatLevel.SMOULDERING);
		feed(helper, BURNER, Items.OAK_PLANKS); // 300 ticks
		assertHeat(helper, BURNER, HeatLevel.KINDLED);
		helper.runAfterDelay(50, () -> assertHeat(helper, BURNER, HeatLevel.KINDLED));
		// under 1.25% of the heat capacity is left
		helper.runAfterDelay(200, () -> assertHeat(helper, BURNER, HeatLevel.FADING));
		helper.succeedWhen(() -> assertHeat(helper, BURNER, HeatLevel.SMOULDERING));
	}

	@GameTest(maxTicks = 20)
	public void blazeCakeSuperheatsAndCreativeCakeNeverEnds(GameTestHelper helper) {
		floor(helper);
		BlockPos cake = new BlockPos(1, 1, 1);
		BlockPos creative = new BlockPos(4, 1, 1);
		burner(helper, cake, HeatLevel.SMOULDERING);
		burner(helper, creative, HeatLevel.SMOULDERING);
		feed(helper, cake, BurnerRegistry.BLAZE_CAKE);
		feed(helper, creative, BurnerRegistry.CREATIVE_BLAZE_CAKE);
		helper.runAfterDelay(5, () -> {
			assertHeat(helper, cake, HeatLevel.SEETHING);
			assertHeat(helper, creative, HeatLevel.KINDLED);
			helper.assertTrue(helper.getBlockEntity(creative, BlazeBurnerBlockEntity.class).isCreative(), "creative burner");
			// a regular fuel must not downgrade the superheated burner or touch the creative one
			helper.assertTrue(BlazeBurnerBlock.tryInsert(helper.getBlockState(cake), helper.getLevel(), helper.absolutePos(cake),
				new ItemStack(Items.COAL), false, true, false).result() != InteractionResult.SUCCESS, "coal accepted by a seething burner");
			helper.assertTrue(BlazeBurnerBlock.tryInsert(helper.getBlockState(creative), helper.getLevel(), helper.absolutePos(creative),
				new ItemStack(Items.COAL), false, true, false).result() != InteractionResult.SUCCESS, "coal accepted by a creative burner");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 20)
	public void lightLevels(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(1, 1, 1), BurnerRegistry.BLAZE_BURNER);
		burner(helper, new BlockPos(2, 1, 1), HeatLevel.SMOULDERING);
		burner(helper, new BlockPos(3, 1, 1), HeatLevel.KINDLED);
		helper.setBlock(new BlockPos(4, 1, 1), BurnerRegistry.LIT_BLAZE_BURNER);
		helper.setBlock(new BlockPos(5, 1, 1), BurnerRegistry.LIT_BLAZE_BURNER.defaultBlockState().setValue(LitBlazeBurnerBlock.FLAME_TYPE, FlameType.SOUL));
		int[] expected = { 0, 8, 15, 12, 9 };
		for (int i = 0; i < expected.length; i++) {
			int light = helper.getBlockState(new BlockPos(1 + i, 1, 1)).getLightEmission();
			helper.assertTrue(light == expected[i], "light of burner " + i + " was " + light + ", expected " + expected[i]);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 600)
	public void heatedRecipeWaitsForHeat(GameTestHelper helper) {
		BasinBlockEntity basin = mixerOnBurner(helper, HeatLevel.SMOULDERING);
		helper.runAfterDelay(2, () -> {
			basin.insertInput(new ItemStack(Items.GOLD_NUGGET), false);
			basin.insertInput(new ItemStack(Items.COPPER_INGOT), false);
		});
		helper.runAfterDelay(150, () -> {
			helper.assertTrue(!contains(basin, Items.AMETHYST_SHARD), "heated recipe ran on a smouldering burner");
			feed(helper, BURNER, BurnerRegistry.CREATIVE_BLAZE_CAKE);
			assertHeat(helper, BURNER, HeatLevel.KINDLED);
			helper.succeedWhen(() -> helper.assertTrue(contains(basin, Items.AMETHYST_SHARD), "no amethyst shard yet"));
		});
	}

	@GameTest(maxTicks = 600)
	public void superheatedRecipeNeedsSeething(GameTestHelper helper) {
		BasinBlockEntity basin = mixerOnBurner(helper, HeatLevel.SMOULDERING);
		feed(helper, BURNER, Items.OAK_PLANKS);
		assertHeat(helper, BURNER, HeatLevel.KINDLED);
		helper.runAfterDelay(2, () -> {
			basin.insertInput(new ItemStack(Items.SUGAR), false);
			basin.insertInput(new ItemStack(Items.COCOA_BEANS), false);
		});
		helper.runAfterDelay(150, () -> {
			helper.assertTrue(!contains(basin, Items.COOKIE), "superheated recipe ran on a kindled burner");
			feed(helper, BURNER, BurnerRegistry.BLAZE_CAKE);
			assertHeat(helper, BURNER, HeatLevel.SEETHING);
			helper.succeedWhen(() -> helper.assertTrue(contains(basin, Items.COOKIE), "no cookie yet"));
		});
	}

	@GameTest(maxTicks = 20)
	public void unheatedRecipesStillRunWithoutBurner(GameTestHelper helper) {
		// the heat conditions themselves, as Create defines them
		helper.assertTrue(HeatCondition.NONE.testBlazeBurner(HeatLevel.NONE), "none/none");
		helper.assertTrue(!HeatCondition.HEATED.testBlazeBurner(HeatLevel.NONE), "heated/none");
		helper.assertTrue(!HeatCondition.HEATED.testBlazeBurner(HeatLevel.SMOULDERING), "heated/smouldering");
		helper.assertTrue(HeatCondition.HEATED.testBlazeBurner(HeatLevel.FADING), "heated/fading");
		helper.assertTrue(HeatCondition.HEATED.testBlazeBurner(HeatLevel.SEETHING), "heated/seething");
		helper.assertTrue(!HeatCondition.SUPERHEATED.testBlazeBurner(HeatLevel.KINDLED), "superheated/kindled");
		helper.assertTrue(HeatCondition.SUPERHEATED.testBlazeBurner(HeatLevel.SEETHING), "superheated/seething");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void thrownEggIsEaten(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(4, 1, 2);
		burner(helper, pos, HeatLevel.SMOULDERING);
		BlockPos abs = helper.absolutePos(pos);
		ThrownEgg egg = new ThrownEgg(helper.getLevel(), abs.getX() - .5, abs.getY() + .6, abs.getZ() + .5, new ItemStack(Items.EGG));
		egg.setDeltaMovement(.5, 0, 0);
		helper.getLevel().addFreshEntity(egg);
		helper.succeedWhen(() -> {
			assertHeat(helper, pos, HeatLevel.FADING);
			helper.assertTrue(helper.getBlockEntity(pos, BlazeBurnerBlockEntity.class).getRemainingBurnTime() > 0, "egg gave no burn time");
		});
	}

	@GameTest(maxTicks = 100)
	public void waterSplashExtinguishesLitBurner(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(4, 1, 2);
		helper.setBlock(pos, BurnerRegistry.LIT_BLAZE_BURNER);
		BlockPos abs = helper.absolutePos(pos);
		ThrownSplashPotion potion = new ThrownSplashPotion(helper.getLevel(), abs.getX() - .5, abs.getY() + .6, abs.getZ() + .5,
			PotionContents.createItemStack(Items.SPLASH_POTION, Potions.WATER));
		potion.setDeltaMovement(.5, 0, 0);
		helper.getLevel().addFreshEntity(potion);
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(pos).is(BurnerRegistry.BLAZE_BURNER), "lit burner is still lit");
			assertHeat(helper, pos, HeatLevel.NONE);
		});
	}

	@GameTest(maxTicks = 20)
	public void lightingAndExtinguishing(GameTestHelper helper) {
		floor(helper);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		helper.setBlock(BURNER, BurnerRegistry.BLAZE_BURNER);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(BURNER)), Direction.UP, helper.absolutePos(BURNER), false);

		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
		helper.useBlock(BURNER, player, hit);
		helper.assertTrue(helper.getBlockState(BURNER).is(BurnerRegistry.LIT_BLAZE_BURNER), "flint and steel did not light the burner");

		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SOUL_SAND));
		helper.useBlock(BURNER, player, hit);
		helper.assertTrue(helper.getBlockState(BURNER).getValue(LitBlazeBurnerBlock.FLAME_TYPE) == FlameType.SOUL, "soul sand did not make a soul flame");

		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SHOVEL));
		helper.useBlock(BURNER, player, hit);
		helper.assertTrue(helper.getBlockState(BURNER).is(BurnerRegistry.BLAZE_BURNER), "shovel did not put the flame out");
		assertHeat(helper, BURNER, HeatLevel.NONE);
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void gogglesToggle(GameTestHelper helper) {
		floor(helper);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		burner(helper, BURNER, HeatLevel.SMOULDERING);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(BURNER)), Direction.UP, helper.absolutePos(BURNER), false);
		BlazeBurnerBlockEntity be = helper.getBlockEntity(BURNER, BlazeBurnerBlockEntity.class);

		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.GOGGLES));
		helper.useBlock(BURNER, player, hit);
		helper.assertTrue(be.goggles, "goggles were not put on");
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.useBlock(BURNER, player, hit);
		helper.assertTrue(!be.goggles, "goggles were not taken off");
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void emptyBurnerCapturesBlaze(GameTestHelper helper) {
		floor(helper);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Blaze blaze = helper.spawn(EntityTypes.BLAZE, new BlockPos(3, 1, 3));
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BurnerRegistry.EMPTY_BLAZE_BURNER));
		player.getItemInHand(InteractionHand.MAIN_HAND).interactLivingEntity(player, blaze, InteractionHand.MAIN_HAND);
		helper.assertTrue(!blaze.isAlive(), "the blaze was not captured");
		helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).is(BurnerRegistry.BLAZE_BURNER_ITEM), "no blaze burner in hand");

		// and from a spawner
		BlockPos spawnerPos = new BlockPos(5, 1, 5);
		helper.setBlock(spawnerPos, Blocks.SPAWNER);
		helper.getBlockEntity(spawnerPos, SpawnerBlockEntity.class).setEntityId(EntityTypes.BLAZE, helper.getLevel().getRandom());
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BurnerRegistry.EMPTY_BLAZE_BURNER));
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(spawnerPos)), Direction.UP, helper.absolutePos(spawnerPos), false);
		InteractionResult result = BurnerRegistry.EMPTY_BLAZE_BURNER.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
		helper.assertTrue(result.consumesAction(), "spawner click was not used");
		helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).is(BurnerRegistry.BLAZE_BURNER_ITEM), "no blaze burner from the spawner");
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void placedBurnerStartsAccordingToItem(GameTestHelper helper) {
		floor(helper);
		Player player = helper.makeMockPlayer(GameType.CREATIVE);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BurnerRegistry.EMPTY_BLAZE_BURNER));
		helper.placeAt(player, player.getItemInHand(InteractionHand.MAIN_HAND), new BlockPos(1, 0, 1), Direction.UP);
		helper.assertTrue(helper.getBlockState(new BlockPos(1, 1, 1)).is(BurnerRegistry.BLAZE_BURNER), "empty burner was not placed");
		assertHeat(helper, new BlockPos(1, 1, 1), HeatLevel.NONE);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BurnerRegistry.BLAZE_BURNER_ITEM));
		helper.placeAt(player, player.getItemInHand(InteractionHand.MAIN_HAND), new BlockPos(3, 0, 1), Direction.UP);
		assertHeat(helper, new BlockPos(3, 1, 1), HeatLevel.SMOULDERING);
		helper.assertTrue(helper.getBlockEntity(new BlockPos(3, 1, 1), BlazeBurnerBlockEntity.class) != null, "no block entity");
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void burnerSurvivesSaveAndLoad(GameTestHelper helper) {
		floor(helper);
		burner(helper, BURNER, HeatLevel.SMOULDERING);
		feed(helper, BURNER, Items.OAK_PLANKS);
		BlazeBurnerBlockEntity be = helper.getBlockEntity(BURNER, BlazeBurnerBlockEntity.class);
		be.goggles = true;
		net.minecraft.nbt.CompoundTag tag = be.saveWithFullMetadata(helper.getLevel().registryAccess());
		net.minecraft.world.level.block.entity.BlockEntity loaded = net.minecraft.world.level.block.entity.BlockEntity
			.loadStatic(helper.absolutePos(BURNER), helper.getBlockState(BURNER), tag, helper.getLevel().registryAccess());
		helper.assertTrue(loaded instanceof BlazeBurnerBlockEntity, "block entity did not load");
		BlazeBurnerBlockEntity copy = (BlazeBurnerBlockEntity) loaded;
		helper.assertTrue(copy.getActiveFuel() == be.getActiveFuel() && copy.getActiveFuel() == BlazeBurnerBlockEntity.FuelType.NORMAL, "fuel type");
		helper.assertTrue(copy.getRemainingBurnTime() == be.getRemainingBurnTime() && copy.getRemainingBurnTime() > 0, "burn time");
		helper.assertTrue(copy.goggles && !copy.isCreative(), "goggles");
		helper.succeed();
	}

	private static int shownParts(GameTestHelper helper, BlockPos pos) {
		var attachment = eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(pos));
		helper.assertTrue(attachment != null, "no display model at " + pos);
		int shown = 0;
		for (var element : attachment.holder().getElements())
			if (element instanceof eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement item && !item.getItem().isEmpty())
				shown++;
		return shown;
	}

	@GameTest(maxTicks = 100)
	public void modelShowsPartsForHeat(GameTestHelper helper) {
		floor(helper);
		BlockPos empty = new BlockPos(1, 1, 1);
		BlockPos smouldering = new BlockPos(3, 1, 1);
		BlockPos heated = new BlockPos(5, 1, 1);
		helper.setBlock(empty, BurnerRegistry.BLAZE_BURNER);
		burner(helper, smouldering, HeatLevel.SMOULDERING);
		burner(helper, heated, HeatLevel.SMOULDERING);
		feed(helper, heated, BurnerRegistry.CREATIVE_BLAZE_CAKE);
		helper.setBlock(heated.above(), ModBlocks.BASIN);
		helper.succeedWhen(() -> {
			// the cage alone, cage and head, cage + head + two rods + flame once the head has lowered into the basin
			helper.assertTrue(shownParts(helper, empty) == 1, "empty burner shows " + shownParts(helper, empty));
			helper.assertTrue(shownParts(helper, smouldering) == 2, "smouldering burner shows " + shownParts(helper, smouldering));
			helper.assertTrue(shownParts(helper, heated) == 5, "heated burner under a basin shows " + shownParts(helper, heated));
		});
	}
}
