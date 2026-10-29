package dev.createpolymer.test;

import java.util.List;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.equipment.armor.CardboardArmorItem;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.feature.ExtrasRegistry;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Create's simple blocks and items that are registered by {@link ExtrasRegistry}. */
public class ExtrasGameTests {

	private static final List<String> ITEMS = List.of("bar_of_chocolate", "sweet_roll", "chocolate_glazed_berries", "honeyed_apple",
		"cardboard_sword", "cardboard_helmet", "cardboard_chestplate", "cardboard_leggings", "cardboard_boots",
		"chromatic_compound", "shadow_steel", "refined_radiance", "vertical_gearbox", "shadow_steel_casing",
		"refined_radiance_casing", "peculiar_bell", "haunted_bell", "experience_block", "rose_quartz_block",
		"rose_quartz_tiles", "small_rose_quartz_tiles");

	private static final List<String> BLOCKS = List.of("shadow_steel_casing", "refined_radiance_casing", "peculiar_bell",
		"haunted_bell", "experience_block", "rose_quartz_block", "rose_quartz_tiles", "small_rose_quartz_tiles");

	private static void check(GameTestHelper helper, boolean condition, String message) {
		helper.assertTrue(condition, message);
	}

	private static Identifier id(String path) {
		return CreatePolymer.id(path);
	}

	@GameTest
	public void everyNewIdIsRegistered(GameTestHelper helper) {
		for (String item : ITEMS)
			check(helper, BuiltInRegistries.ITEM.containsKey(id(item)), "item create:" + item + " is not registered");
		for (String block : BLOCKS)
			check(helper, BuiltInRegistries.BLOCK.containsKey(id(block)), "block create:" + block + " is not registered");
		check(helper, BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id("peculiar_bell")), "peculiar bell has no block entity type");
		check(helper, BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id("cursed_bell")), "haunted bell has no block entity type");
		helper.succeed();
	}

	@GameTest
	public void foodHasCreatesNutrition(GameTestHelper helper) {
		checkNutrition(helper, ExtrasRegistry.BAR_OF_CHOCOLATE, 6);
		checkNutrition(helper, ExtrasRegistry.SWEET_ROLL, 6);
		checkNutrition(helper, ExtrasRegistry.CHOCOLATE_GLAZED_BERRIES, 7);
		checkNutrition(helper, ExtrasRegistry.HONEYED_APPLE, 8);
		helper.succeed();
	}

	private static void checkNutrition(GameTestHelper helper, Item food, int nutrition) {
		var properties = new ItemStack(food).get(DataComponents.FOOD);
		check(helper, properties != null && properties.nutrition() == nutrition,
			food + " has nutrition " + (properties == null ? "none" : properties.nutrition()) + ", expected " + nutrition);
	}

	@GameTest
	public void verticalGearboxItemPlacesAVerticalGearbox(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		BlockPos absolute = helper.absolutePos(pos);
		Player player = helper.makeMockPlayer(GameType.CREATIVE);
		ItemStack stack = new ItemStack(ExtrasRegistry.VERTICAL_GEARBOX);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);

		InteractionResult result = ExtrasRegistry.VERTICAL_GEARBOX.place(
			new BlockPlaceContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, stack, hit));
		check(helper, result == InteractionResult.SUCCESS, "placing the vertical gearbox gave " + result);

		BlockState state = helper.getBlockState(pos);
		check(helper, state.is(ModBlocks.GEARBOX), "the placed block is " + state.getBlock());
		check(helper, state.getValue(BlockStateProperties.AXIS) != Axis.Y,
			"the vertical gearbox has the vertical axis " + state.getValue(BlockStateProperties.AXIS));
		helper.succeed();
	}

	@GameTest
	public void experienceBlockDropsItself(GameTestHelper helper) {
		List<ItemStack> drops = Block.getDrops(ExtrasRegistry.EXPERIENCE_BLOCK.defaultBlockState(), helper.getLevel(),
			BlockPos.ZERO, null, null, new ItemStack(Items.DIAMOND_PICKAXE));
		check(helper, drops.stream().anyMatch(s -> s.is(ExtrasRegistry.EXPERIENCE_BLOCK_ITEM)),
			"the block of experience dropped " + drops);
		helper.succeed();
	}

	@GameTest
	public void casingsAndRoseQuartzBlocksPlaceAndDropThemselves(GameTestHelper helper) {
		List<Block> blocks = List.of(ExtrasRegistry.SHADOW_STEEL_CASING, ExtrasRegistry.REFINED_RADIANCE_CASING,
			ExtrasRegistry.ROSE_QUARTZ_BLOCK, ExtrasRegistry.ROSE_QUARTZ_TILES, ExtrasRegistry.SMALL_ROSE_QUARTZ_TILES);
		BlockPos pos = new BlockPos(1, 2, 1);
		for (Block block : blocks) {
			helper.setBlock(pos, block);
			check(helper, helper.getBlockState(pos).is(block), block + " did not place");

			Item item = block.asItem();
			List<ItemStack> drops = Block.getDrops(block.defaultBlockState(), helper.getLevel(), BlockPos.ZERO, null, null,
				new ItemStack(Items.DIAMOND_PICKAXE));
			check(helper, drops.stream().anyMatch(s -> s.is(item)), block + " dropped " + drops + " instead of itself");
			helper.setBlock(pos, Blocks.AIR);
		}
		helper.succeed();
	}

	@GameTest
	public void peculiarBellOverSoulFireBecomesHauntedBell(GameTestHelper helper) {
		// a ceiling bell: the stone above it is the support, the soul fire below is what converts it
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos.above(), Blocks.STONE);
		helper.setBlock(pos.below().below(), Blocks.SOUL_SAND);
		helper.setBlock(pos.below(), Blocks.SOUL_FIRE);
		BlockPos support = helper.absolutePos(pos.above());
		Player player = helper.makeMockPlayer(GameType.CREATIVE);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(support), Direction.DOWN, support, false);
		BlockPlaceContext context = new BlockPlaceContext(helper.getLevel(), player, InteractionHand.MAIN_HAND,
			new ItemStack(ExtrasRegistry.PECULIAR_BELL_ITEM), hit);

		BlockState placed = ExtrasRegistry.PECULIAR_BELL.getStateForPlacement(context);
		check(helper, placed != null && placed.is(ExtrasRegistry.HAUNTED_BELL),
			"a peculiar bell over soul fire is " + (placed == null ? "nothing" : placed.getBlock()));
		helper.succeed();
	}

	@GameTest
	public void cardboardArmourEquips(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		List<Item> pieces = List.of(ExtrasRegistry.CARDBOARD_HELMET, ExtrasRegistry.CARDBOARD_CHESTPLATE,
			ExtrasRegistry.CARDBOARD_LEGGINGS, ExtrasRegistry.CARDBOARD_BOOTS);
		List<EquipmentSlot> slots = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
		for (int i = 0; i < pieces.size(); i++) {
			Item piece = pieces.get(i);
			EquipmentSlot slot = slots.get(i);
			check(helper, piece instanceof CardboardArmorItem, piece + " is not a cardboard armour item");
			ItemStack stack = new ItemStack(piece);
			var equippable = stack.get(DataComponents.EQUIPPABLE);
			check(helper, equippable != null && equippable.slot() == slot, piece + " is not equippable in " + slot);
			player.setItemSlot(slot, stack);
			check(helper, player.getItemBySlot(slot).is(piece), piece + " did not go on in " + slot);
		}
		helper.succeed();
	}
}
