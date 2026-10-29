package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.JsonOps;

import dev.createpolymer.fluids.drain.ItemDrainBlockEntity;
import dev.createpolymer.fluids.potion.PotionFluid;
import dev.createpolymer.fluids.potion.PotionFluid.BottleType;
import dev.createpolymer.fluids.potion.PotionFluidHandler;
import dev.createpolymer.fluids.spout.SpoutBlockEntity;
import dev.createpolymer.fluids.tank.BoilerData;
import dev.createpolymer.fluids.tank.FluidTankBlock;
import dev.createpolymer.fluids.tank.FluidTankBlock.Shape;
import dev.createpolymer.fluids.tank.FluidTankBlockEntity;
import dev.createpolymer.fluids.transfer.EmptyingRecipe;
import dev.createpolymer.fluids.transfer.FillingRecipe;
import dev.createpolymer.fluids.transfer.GenericItemEmptying;
import dev.createpolymer.fluids.transfer.GenericItemFilling;
import dev.createpolymer.foundation.fluid.FluidHandlerLookup;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.IFluidHandler;
import dev.createpolymer.foundation.fluid.IFluidHandler.FluidAction;
import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import dev.createpolymer.kinetics.motor.CreativeMotorBlockEntity;
import dev.createpolymer.kinetics.press.MechanicalPressBlock;
import dev.createpolymer.logistics.depot.DepotBlockEntity;
import dev.createpolymer.processing.basin.BasinBlockEntity;
import dev.createpolymer.processing.basin.BasinRecipeLogic;
import dev.createpolymer.processing.burner.BlazeBurnerBlock;
import dev.createpolymer.processing.burner.BlazeBurnerBlock.HeatLevel;
import dev.createpolymer.processing.burner.BlazeBurnerBlockEntity;
import dev.createpolymer.processing.recipe.BasinRecipe;
import dev.createpolymer.processing.recipe.FluidIngredient;
import dev.createpolymer.processing.recipe.HeatCondition;
import dev.createpolymer.processing.recipe.ProcessingOutput;
import dev.createpolymer.processing.recipe.ProcessingRecipe.Params;
import dev.createpolymer.processing.recipe.SizedFluidIngredient;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.feature.BurnerRegistry;
import dev.createpolymer.registry.feature.FluidsRegistry;
import dev.createpolymer.registry.feature.TanksRegistry;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class TanksGameTests {

	private static final int CAPACITY = FluidTankBlockEntity.getCapacityMultiplier();

	private static BlockState tank() {
		return TanksRegistry.FLUID_TANK.defaultBlockState();
	}

	/** A width x height x width block of tanks with its lowest corner at (x0, y0, z0). */
	private static List<BlockPos> placeTanks(GameTestHelper helper, int x0, int y0, int z0, int width, int height, Block block) {
		List<BlockPos> placed = new ArrayList<>();
		for (int y = 0; y < height; y++)
			for (int x = 0; x < width; x++)
				for (int z = 0; z < width; z++) {
					BlockPos pos = new BlockPos(x0 + x, y0 + y, z0 + z);
					helper.setBlock(pos, block);
					placed.add(pos);
				}
		return placed;
	}

	private static IFluidHandler handlerAt(GameTestHelper helper, BlockPos pos) {
		return FluidHandlerLookup.get(helper.getLevel(), helper.absolutePos(pos), null);
	}

	private static FluidTankBlockEntity tankAt(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockEntity(pos, FluidTankBlockEntity.class);
	}

	private static BlockHitResult hit(GameTestHelper helper, BlockPos pos, Direction face) {
		BlockPos abs = helper.absolutePos(pos);
		return new BlockHitResult(Vec3.atCenterOf(abs), face, abs, false);
	}

	private static FluidStack water(int amount) {
		return new FluidStack(Fluids.WATER, amount);
	}

	private static ItemStack waterBottle() {
		return PotionContents.createItemStack(Items.POTION, Potions.WATER);
	}

	// ---- fluid tank ----

	@GameTest
	public void cubeOfEightFormsOneTankAndSharesFluid(GameTestHelper helper) {
		floor(helper);
		List<BlockPos> blocks = placeTanks(helper, 2, 1, 2, 2, 2, TanksRegistry.FLUID_TANK);
		helper.runAfterDelay(2, () -> {
			int controllers = 0;
			for (BlockPos pos : blocks)
				if (tankAt(helper, pos).isController())
					controllers++;
			helper.assertTrue(controllers == 1, "expected one controller, found " + controllers);
			FluidTankBlockEntity controller = tankAt(helper, new BlockPos(2, 1, 2));
			helper.assertTrue(controller.isController(), "the lowest corner is not the controller");
			helper.assertTrue(controller.getWidth() == 2 && controller.getHeight() == 2,
				"tank is " + controller.getWidth() + " wide and " + controller.getHeight() + " high");
			helper.assertTrue(controller.getTankInventory().getCapacity() == 8 * CAPACITY,
				"capacity is " + controller.getTankInventory().getCapacity());
			for (BlockPos pos : blocks)
				helper.assertTrue(tankAt(helper, pos).getControllerBE() == controller, "block " + pos + " has another controller");

			// the top blocks are marked as such
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 2, 2)).getValue(FluidTankBlock.TOP), "upper layer is not the top");
			helper.assertTrue(!helper.getBlockState(new BlockPos(2, 2, 2)).getValue(FluidTankBlock.BOTTOM), "upper layer is the bottom");
			helper.assertTrue(helper.getBlockState(new BlockPos(3, 1, 3)).getValue(FluidTankBlock.BOTTOM), "lower layer is not the bottom");

			// a fluid put into any block ends up in the whole tank
			IFluidHandler far = handlerAt(helper, new BlockPos(3, 2, 3));
			helper.assertTrue(far.fill(water(5000), FluidAction.EXECUTE) == 5000, "water was not accepted");
			for (BlockPos pos : blocks) {
				FluidStack inside = handlerAt(helper, pos).getFluidInTank(0);
				helper.assertTrue(inside.is(Fluids.WATER) && inside.getAmount() == 5000, "block " + pos + " sees " + inside);
			}
			helper.assertTrue(Math.abs(controller.getFillState() - 5000f / (8 * CAPACITY)) < 1e-4, "fill state is " + controller.getFillState());
			helper.succeed();
		});
	}

	@GameTest
	public void tanksStackToTheMaximumHeight(GameTestHelper helper) {
		floor(helper);
		placeTanks(helper, 2, 1, 2, 1, 4, TanksRegistry.FLUID_TANK);
		helper.runAfterDelay(2, () -> {
			FluidTankBlockEntity controller = tankAt(helper, new BlockPos(2, 1, 2));
			helper.assertTrue(controller.isController() && controller.getHeight() == 4 && controller.getWidth() == 1,
				"tank is " + controller.getWidth() + " x " + controller.getHeight());
			helper.assertTrue(controller.getTotalTankSize() == 4, "size " + controller.getTotalTankSize());
			helper.assertTrue(controller.getTankInventory().getCapacity() == 4 * CAPACITY, "capacity");
			helper.succeed();
		});
	}

	@GameTest
	public void differentFluidsDoNotMerge(GameTestHelper helper) {
		floor(helper);
		BlockPos lower = new BlockPos(2, 1, 2);
		BlockPos middle = new BlockPos(2, 2, 2);
		BlockPos upper = new BlockPos(2, 3, 2);
		helper.setBlock(lower, TanksRegistry.FLUID_TANK);
		helper.setBlock(upper, TanksRegistry.FLUID_TANK);
		helper.runAfterDelay(2, () -> {
			handlerAt(helper, lower).fill(water(1000), FluidAction.EXECUTE);
			handlerAt(helper, upper).fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE);
			// an empty tank between them joins one of them, never both
			helper.setBlock(middle, TanksRegistry.FLUID_TANK);
			helper.runAfterDelay(3, () -> {
				int tallest = Math.max(tankAt(helper, lower).getControllerBE().getHeight(), tankAt(helper, upper).getControllerBE().getHeight());
				helper.assertTrue(tallest == 2, "water and lava tanks formed a tank of height " + tallest);
				helper.assertTrue(tankAt(helper, lower).getControllerBE() != tankAt(helper, upper).getControllerBE(), "water and lava share a controller");
				helper.assertTrue(handlerAt(helper, lower).getFluidInTank(0).is(Fluids.WATER)
					&& handlerAt(helper, upper).getFluidInTank(0).is(Fluids.LAVA), "fluids were mixed up");
				helper.assertTrue(handlerAt(helper, lower).getFluidInTank(0).getAmount() == 1000
					&& handlerAt(helper, upper).getFluidInTank(0).getAmount() == 1000, "fluid was lost");
				helper.succeed();
			});
		});
	}

	@GameTest
	public void breakingATankSplitsItAndKeepsTheFluid(GameTestHelper helper) {
		floor(helper);
		BlockPos lower = new BlockPos(2, 1, 2);
		BlockPos upper = new BlockPos(2, 2, 2);
		placeTanks(helper, 2, 1, 2, 1, 2, TanksRegistry.FLUID_TANK);
		helper.runAfterDelay(2, () -> {
			handlerAt(helper, lower).fill(water(12000), FluidAction.EXECUTE);
			helper.assertTrue(tankAt(helper, lower).getHeight() == 2, "no tank of height 2");
			helper.destroyBlock(lower);
			helper.runAfterDelay(2, () -> {
				FluidTankBlockEntity remaining = tankAt(helper, upper);
				helper.assertTrue(remaining.isController() && remaining.getHeight() == 1, "remaining block is not a tank of its own");
				FluidStack left = handlerAt(helper, upper).getFluidInTank(0);
				helper.assertTrue(left.is(Fluids.WATER) && left.getAmount() == CAPACITY, "remaining block holds " + left);
				helper.assertTrue(helper.getBlockState(upper).getValue(FluidTankBlock.TOP) && helper.getBlockState(upper).getValue(FluidTankBlock.BOTTOM),
					"state of the remaining block was not reset");
				helper.succeed();
			});
		});
	}

	@GameTest
	public void creativePlayerFillsAndEmptiesATankWithBuckets(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, TanksRegistry.FLUID_TANK);
		helper.runAfterDelay(2, () -> {
			Player player = helper.makeMockPlayer(GameType.CREATIVE);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
			helper.useBlock(pos, player, hit(helper, pos, Direction.UP));
			FluidStack inside = handlerAt(helper, pos).getFluidInTank(0);
			helper.assertTrue(inside.is(Fluids.WATER) && inside.getAmount() == 1000, "tank holds " + inside);
			helper.assertTrue(player.getMainHandItem().is(Items.WATER_BUCKET), "creative players keep their bucket");

			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
			helper.useBlock(pos, player, hit(helper, pos, Direction.UP));
			helper.assertTrue(handlerAt(helper, pos).getFluidInTank(0).isEmpty(), "bucket did not take the water");
			helper.succeed();
		});
	}

	@GameTest
	public void survivalPlayerCannotUseARegularTankButCanUseACreativeOne(GameTestHelper helper) {
		floor(helper);
		BlockPos regular = new BlockPos(1, 1, 1);
		BlockPos creative = new BlockPos(4, 1, 4);
		helper.setBlock(regular, TanksRegistry.FLUID_TANK);
		helper.setBlock(creative, TanksRegistry.CREATIVE_FLUID_TANK);
		helper.runAfterDelay(2, () -> {
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
			helper.useBlock(regular, player, hit(helper, regular, Direction.UP));
			helper.assertTrue(handlerAt(helper, regular).getFluidInTank(0).isEmpty(), "a survival player filled a regular tank");
			helper.assertTrue(player.getMainHandItem().is(Items.WATER_BUCKET), "the bucket was used");

			// a creative tank takes the fluid of the bucket (which stays full) and then never runs dry
			helper.useBlock(creative, player, hit(helper, creative, Direction.UP));
			helper.assertTrue(player.getMainHandItem().is(Items.WATER_BUCKET), "the bucket was used up: " + player.getMainHandItem());
			FluidStack inside = handlerAt(helper, creative).getFluidInTank(0);
			helper.assertTrue(inside.is(Fluids.WATER) && inside.getAmount() == CAPACITY, "creative tank holds " + inside);
			helper.assertTrue(handlerAt(helper, creative).fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE) == 1000
				&& handlerAt(helper, creative).getFluidInTank(0).is(Fluids.WATER), "the creative tank changed its fluid by itself");
			helper.assertTrue(handlerAt(helper, creative).drain(500, FluidAction.EXECUTE).getAmount() == 500
				&& handlerAt(helper, creative).getFluidInTank(0).getAmount() == CAPACITY, "draining emptied the creative tank");

			// an empty bucket takes a bucket of it and clears the tank
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
			helper.useBlock(creative, player, hit(helper, creative, Direction.UP));
			helper.assertTrue(player.getInventory().contains(new ItemStack(Items.WATER_BUCKET)), "no water bucket came out of the creative tank");
			helper.assertTrue(handlerAt(helper, creative).getFluidInTank(0).isEmpty(), "the creative tank was not cleared");
			helper.succeed();
		});
	}

	@GameTest
	public void wrenchTogglesTheWindows(GameTestHelper helper) {
		floor(helper);
		placeTanks(helper, 2, 1, 2, 2, 1, TanksRegistry.FLUID_TANK);
		BlockPos corner = new BlockPos(2, 1, 2);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(helper.getBlockState(corner).getValue(FluidTankBlock.SHAPE) == Shape.WINDOW_NW,
				"corner shape is " + helper.getBlockState(corner).getValue(FluidTankBlock.SHAPE));
			helper.assertTrue(helper.getBlockState(new BlockPos(3, 1, 3)).getValue(FluidTankBlock.SHAPE) == Shape.WINDOW_SE, "opposite corner shape");

			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			var context = new net.minecraft.world.item.context.UseOnContext(player, InteractionHand.MAIN_HAND, hit(helper, corner, Direction.NORTH));
			((FluidTankBlock) helper.getBlockState(corner).getBlock()).onWrenched(helper.getBlockState(corner), context);
			for (int x = 2; x <= 3; x++)
				for (int z = 2; z <= 3; z++)
					helper.assertTrue(helper.getBlockState(new BlockPos(x, 1, z)).getValue(FluidTankBlock.SHAPE) == Shape.PLAIN, "windows are still on");
			((FluidTankBlock) helper.getBlockState(corner).getBlock()).onWrenched(helper.getBlockState(corner), context);
			helper.assertTrue(helper.getBlockState(corner).getValue(FluidTankBlock.SHAPE) == Shape.WINDOW_NW, "windows did not come back");
			helper.succeed();
		});
	}

	@GameTest
	public void comparatorReadsTheFillLevel(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, TanksRegistry.FLUID_TANK);
		helper.runAfterDelay(2, () -> {
			BlockState state = helper.getBlockState(pos);
			helper.assertTrue(state.getAnalogOutputSignal(helper.getLevel(), helper.absolutePos(pos), Direction.NORTH) == 0, "empty tank outputs a signal");
			handlerAt(helper, pos).fill(water(CAPACITY / 2), FluidAction.EXECUTE);
			int half = state.getAnalogOutputSignal(helper.getLevel(), helper.absolutePos(pos), Direction.NORTH);
			helper.assertTrue(half == 8, "half full tank outputs " + half);
			handlerAt(helper, pos).fill(water(CAPACITY), FluidAction.EXECUTE);
			helper.assertTrue(state.getAnalogOutputSignal(helper.getLevel(), helper.absolutePos(pos), Direction.NORTH) == 15, "full tank does not output 15");
			helper.succeed();
		});
	}

	@GameTest
	public void lavaMakesTheTankShine(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, TanksRegistry.FLUID_TANK);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(helper.getBlockState(pos).getLightEmission() == 0, "an empty tank shines");
			handlerAt(helper, pos).fill(new FluidStack(Fluids.LAVA, CAPACITY), FluidAction.EXECUTE);
			int light = helper.getBlockState(pos).getLightEmission();
			helper.assertTrue(light == (int) (15 / 1.2f), "tank with lava emits " + light);
			handlerAt(helper, pos).drain(CAPACITY, FluidAction.EXECUTE);
			helper.assertTrue(helper.getBlockState(pos).getLightEmission() == 0, "an emptied tank still shines");
			helper.succeed();
		});
	}

	@GameTest
	public void tankKeepsItsFluidWhenSavedAndLoaded(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		placeTanks(helper, 2, 1, 2, 1, 2, TanksRegistry.FLUID_TANK);
		helper.runAfterDelay(2, () -> {
			FluidTankBlockEntity be = tankAt(helper, pos);
			PotionContents harming = new PotionContents(Potions.HARMING);
			FluidStack potion = PotionFluid.of(3000, harming, BottleType.SPLASH);
			handlerAt(helper, pos).fill(potion, FluidAction.EXECUTE);
			CompoundTag tag = be.saveWithFullMetadata(helper.getLevel().registryAccess());
			BlockEntity loaded = BlockEntity.loadStatic(helper.absolutePos(pos), helper.getBlockState(pos), tag, helper.getLevel().registryAccess());
			helper.assertTrue(loaded instanceof FluidTankBlockEntity, "block entity was not loaded");
			FluidTankBlockEntity copy = (FluidTankBlockEntity) loaded;
			FluidStack inside = copy.getTankInventory().getFluid();
			helper.assertTrue(copy.getHeight() == 2 && copy.getTankInventory().getCapacity() == 2 * CAPACITY, "size was lost");
			helper.assertTrue(inside.is(FluidsRegistry.POTION) && inside.getAmount() == 3000, "fluid was lost: " + inside);
			helper.assertTrue(inside.get(DataComponents.POTION_CONTENTS).is(Potions.HARMING)
				&& inside.get(FluidsRegistry.POTION_FLUID_BOTTLE_TYPE) == BottleType.SPLASH, "fluid components were lost");
			helper.succeed();
		});
	}

	@GameTest
	public void everyTankModelTheCodeAsksForExists(GameTestHelper helper) {
		int checked = 0;
		for (boolean creative : new boolean[] { false, true })
			for (int width = 1; width <= 3; width++)
				for (int height = 1; height <= 5; height++)
					for (int dx = 0; dx < width; dx++)
						for (int dy = 0; dy < height; dy++)
							for (int dz = 0; dz < width; dz++)
								for (boolean window : new boolean[] { true, false }) {
									String name = FluidTankBlock.Model.modelName(creative, width, height, dx, dy, dz, window);
									helper.assertTrue(TanksGameTests.class.getResource("/assets/create/models/block/" + name + ".json") != null,
										"missing model " + name);
									checked++;
								}
		helper.assertTrue(checked > 100, "checked " + checked);
		helper.succeed();
	}

	@GameTest
	public void boilerCountsEnginesAndTakesTheirHeat(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 2, 2);
		// a gold block next to a tank stands in for a steam engine until those are ported
		var engine = (java.util.function.BiPredicate<BlockState, Direction>) (state, d) -> state.is(Blocks.GOLD_BLOCK) && d == Direction.EAST;
		BoilerData.ENGINE_ATTACHMENTS.add(engine);
		try {
			BlockPos burner = new BlockPos(2, 1, 2);
			helper.setBlock(burner, BurnerRegistry.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, HeatLevel.SMOULDERING));
			BlazeBurnerBlock.tryInsert(helper.getBlockState(burner), helper.getLevel(), helper.absolutePos(burner), new ItemStack(Items.OAK_PLANKS),
				false, true, false);
			helper.setBlock(pos, TanksRegistry.FLUID_TANK);
			helper.setBlock(new BlockPos(3, 2, 2), Blocks.GOLD_BLOCK);
			helper.runAfterDelay(3, () -> {
				FluidTankBlockEntity be = tankAt(helper, pos);
				be.updateBoilerState();
				helper.assertTrue(be.boiler.attachedEngines == 1 && be.boiler.isActive(), "engines: " + be.boiler.attachedEngines);
				helper.assertTrue(helper.getBlockState(pos).getValue(FluidTankBlock.SHAPE) == Shape.PLAIN, "boilers close their windows");
				be.boiler.updateTemperature(be);
				helper.assertTrue(be.boiler.activeHeat == 1 && !be.boiler.passiveHeat, "heat: " + be.boiler.activeHeat);
				// a boiler is supplied with water through its handler, which keeps nothing
				IFluidHandler handler = handlerAt(helper, pos);
				helper.assertTrue(handler.fill(water(100), FluidAction.EXECUTE) == 100 && handler.getFluidInTank(0).isEmpty(), "water was not taken");
				helper.assertTrue(handler.fill(new FluidStack(Fluids.LAVA, 100), FluidAction.EXECUTE) == 0, "lava was taken by a boiler");
				helper.succeed();
			});
		} finally {
			helper.runAfterDelay(10, () -> BoilerData.ENGINE_ATTACHMENTS.remove(engine));
		}
	}

	@GameTest
	public void blazeBurnerHeatsATankAbove(GameTestHelper helper) {
		floor(helper);
		BlockPos burner = new BlockPos(2, 1, 2);
		helper.setBlock(burner, BurnerRegistry.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, HeatLevel.SMOULDERING));
		helper.setBlock(new BlockPos(2, 2, 2), TanksRegistry.FLUID_TANK);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(helper.getBlockEntity(burner, BlazeBurnerBlockEntity.class).isValidBlockAbove(), "a tank above is not a valid block");
			helper.succeed();
		});
	}

	@GameTest
	public void tankItemExtendsAWideTankByAWholeLayer(GameTestHelper helper) {
		floor(helper);
		placeTanks(helper, 2, 1, 2, 2, 1, TanksRegistry.FLUID_TANK);
		helper.runAfterDelay(1, () -> {
			// one 2x2 layer is a tank of width 2; right-clicking its top with 4 tanks in hand places the next layer
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			ItemStack stack = new ItemStack(TanksRegistry.FLUID_TANK_ITEM, 4);
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			BlockPos clicked = helper.absolutePos(new BlockPos(2, 1, 2));
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(clicked).add(0, .5, 0), Direction.UP, clicked, false);
			stack.useOn(new net.minecraft.world.item.context.UseOnContext(player, InteractionHand.MAIN_HAND, hit));
			helper.assertTrue(stack.isEmpty(), "tanks left in hand: " + stack.getCount());
		});
		helper.runAfterDelay(4, () -> {
			List<BlockPos> all = new ArrayList<>();
			for (int y = 1; y <= 2; y++)
				for (int x = 2; x <= 3; x++)
					for (int z = 2; z <= 3; z++)
						all.add(new BlockPos(x, y, z));
			int controllers = 0;
			for (BlockPos pos : all) {
				helper.assertBlockPresent(TanksRegistry.FLUID_TANK, pos);
				if (tankAt(helper, pos).isController())
					controllers++;
			}
			helper.assertTrue(controllers == 1, "expected one 2x2x2 tank, found " + controllers + " controllers");
			helper.assertTrue(tankAt(helper, new BlockPos(2, 1, 2)).getHeight() == 2, "wrong height");
			helper.succeed();
		});
	}

	@GameTest
	public void tankItemWithStoredFluidKeepsOnlyOneBlocksWorth(GameTestHelper helper) {
		floor(helper);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack stack = new ItemStack(TanksRegistry.FLUID_TANK_ITEM);
		CompoundTag fluid = new CompoundTag();
		fluid.putString("id", "minecraft:water");
		fluid.putInt("amount", 100000);
		CompoundTag content = new CompoundTag();
		content.put("Fluid", fluid);
		CompoundTag data = new CompoundTag();
		data.put("TankContent", content);
		data.putInt("Size", 3);
		data.putInt("Height", 5);
		stack.set(DataComponents.BLOCK_ENTITY_DATA, net.minecraft.world.item.component.TypedEntityData.of(TanksRegistry.FLUID_TANK_BLOCK_ENTITY, data));
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.placeAt(player, stack, pos.below(), Direction.UP);
		helper.runAfterDelay(2, () -> {
			FluidTankBlockEntity tank = tankAt(helper, pos);
			helper.assertTrue(tank.getWidth() == 1 && tank.getHeight() == 1, "the structure of the old tank came along: " + tank.getWidth() + " x " + tank.getHeight());
			FluidStack inside = tank.getTankInventory().getFluid();
			helper.assertTrue(inside.is(Fluids.WATER) && inside.getAmount() == CAPACITY, "tank holds " + inside);
			helper.succeed();
		});
	}

	@GameTest
	public void largeTanksOnlyHaveWindowsAtTheEdgeCenters(GameTestHelper helper) {
		floor(helper);
		placeTanks(helper, 1, 1, 1, 3, 1, TanksRegistry.FLUID_TANK);
		helper.runAfterDelay(2, () -> {
			for (int x = 0; x < 3; x++)
				for (int z = 0; z < 3; z++) {
					Shape shape = helper.getBlockState(new BlockPos(1 + x, 1, 1 + z)).getValue(FluidTankBlock.SHAPE);
					Shape expected = Math.abs(x - z) == 1 ? Shape.WINDOW : Shape.PLAIN;
					helper.assertTrue(shape == expected, "block " + x + "," + z + " is " + shape + ", expected " + expected);
				}
			helper.assertTrue(tankAt(helper, new BlockPos(1, 1, 1)).getWidth() == 3, "no 3x3 tank");
			helper.succeed();
		});
	}

	@GameTest
	public void basinKeepsItsFluidsWhenSavedAndLoaded(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, ModBlocks.BASIN);
		helper.runAfterDelay(2, () -> {
			BasinBlockEntity basin = helper.getBlockEntity(pos, BasinBlockEntity.class);
			basin.getFluidCapability().fill(water(300), FluidAction.EXECUTE);
			basin.getFluidCapability().fill(new FluidStack(Fluids.LAVA, 700), FluidAction.EXECUTE);
			CompoundTag tag = basin.saveWithFullMetadata(helper.getLevel().registryAccess());
			BlockEntity loaded = BlockEntity.loadStatic(helper.absolutePos(pos), helper.getBlockState(pos), tag, helper.getLevel().registryAccess());
			helper.assertTrue(loaded instanceof BasinBlockEntity, "block entity was not loaded");
			IFluidHandler handler = ((BasinBlockEntity) loaded).getFluidCapability();
			int water = 0, lava = 0;
			for (int tank = 0; tank < handler.getTanks(); tank++) {
				FluidStack inside = handler.getFluidInTank(tank);
				if (inside.is(Fluids.WATER))
					water += inside.getAmount();
				if (inside.is(Fluids.LAVA))
					lava += inside.getAmount();
			}
			helper.assertTrue(water == 300 && lava == 700, "basin holds " + water + " water and " + lava + " lava");
			helper.succeed();
		});
	}

	// ---- potions and recipes ----

	@GameTest
	public void potionFluidsAndBottles(GameTestHelper helper) {
		Level level = helper.getLevel();
		// water bottle -> 250 mB of water and an empty bottle
		var emptied = GenericItemEmptying.emptyItem(level, waterBottle(), true);
		helper.assertTrue(emptied.getFirst().is(Fluids.WATER) && emptied.getFirst().getAmount() == 250, "water bottle gave " + emptied.getFirst());
		helper.assertTrue(emptied.getSecond().is(Items.GLASS_BOTTLE), "bottle is " + emptied.getSecond());

		// other potions keep their contents and bottle type
		ItemStack splash = PotionContents.createItemStack(Items.SPLASH_POTION, Potions.STRONG_HEALING);
		FluidStack fluid = GenericItemEmptying.emptyItem(level, splash, true).getFirst();
		helper.assertTrue(fluid.is(FluidsRegistry.POTION) && fluid.getAmount() == 250, "splash potion gave " + fluid);
		helper.assertTrue(fluid.get(FluidsRegistry.POTION_FLUID_BOTTLE_TYPE) == BottleType.SPLASH, "bottle type " + fluid.get(FluidsRegistry.POTION_FLUID_BOTTLE_TYPE));
		ItemStack refilled = PotionFluidHandler.fillBottle(new ItemStack(Items.GLASS_BOTTLE), fluid);
		helper.assertTrue(refilled.is(Items.SPLASH_POTION) && refilled.get(DataComponents.POTION_CONTENTS).is(Potions.STRONG_HEALING),
			"bottle fills with " + refilled);

		// fluids with components survive their codec
		var json = FluidStack.CODEC.encodeStart(JsonOps.INSTANCE, fluid).getOrThrow();
		FluidStack decoded = FluidStack.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		helper.assertTrue(FluidStack.isSameFluidSameComponents(fluid, decoded) && decoded.getAmount() == 250, "codec lost the components: " + json);

		// the Fabric variant of a flowing fluid is the still one; the registry ids are Create's
		helper.assertTrue(FluidVariant.of(FluidsRegistry.POTION).getFluid() == FluidsRegistry.POTION, "variant");
		helper.assertTrue(BuiltInRegistries.FLUID.getKey(FluidsRegistry.HONEY).toString().equals("create:honey")
			&& BuiltInRegistries.FLUID.getKey(FluidsRegistry.HONEY.getFlowing()).toString().equals("create:flowing_honey"), "ids");
		helper.assertTrue(FluidsRegistry.HONEY.isSame(FluidsRegistry.HONEY.getFlowing()), "flowing honey is not honey");
		helper.succeed();
	}

	@GameTest
	public void genericEmptyingAndFillingOfBuckets(GameTestHelper helper) {
		Level level = helper.getLevel();
		helper.assertTrue(GenericItemEmptying.canItemBeEmptied(level, new ItemStack(Items.LAVA_BUCKET)), "lava bucket cannot be emptied");
		helper.assertTrue(!GenericItemEmptying.canItemBeEmptied(level, new ItemStack(Items.BUCKET)), "empty bucket can be emptied");
		ItemStack lavaBucket = new ItemStack(Items.LAVA_BUCKET, 2);
		var emptied = GenericItemEmptying.emptyItem(level, lavaBucket, false);
		helper.assertTrue(emptied.getFirst().is(Fluids.LAVA) && emptied.getFirst().getAmount() == 1000 && emptied.getSecond().is(Items.BUCKET)
			&& lavaBucket.getCount() == 1, "lava bucket gave " + emptied + ", left " + lavaBucket);

		helper.assertTrue(GenericItemFilling.canItemBeFilled(level, new ItemStack(Items.BUCKET)), "bucket cannot be filled");
		helper.assertTrue(!GenericItemFilling.canItemBeFilled(level, new ItemStack(Items.MILK_BUCKET)), "milk bucket can be filled");
		FluidStack lava = new FluidStack(Fluids.LAVA, 1500);
		int required = GenericItemFilling.getRequiredAmountForItem(level, new ItemStack(Items.BUCKET), lava);
		helper.assertTrue(required == 1000, "a bucket needs " + required);
		ItemStack bucket = new ItemStack(Items.BUCKET, 2);
		ItemStack filled = GenericItemFilling.fillItem(level, required, bucket, lava);
		helper.assertTrue(filled.is(Items.LAVA_BUCKET) && bucket.getCount() == 1 && lava.getAmount() == 500, "filled " + filled + ", " + lava);
		helper.assertTrue(GenericItemFilling.getRequiredAmountForItem(level, new ItemStack(Items.BUCKET), new FluidStack(FluidsRegistry.POTION, 1000)) == -1,
			"a bucket takes potions");

		// glass bottles take water in portions of 250 mB
		FluidStack tank = water(600);
		helper.assertTrue(GenericItemFilling.getRequiredAmountForItem(level, new ItemStack(Items.GLASS_BOTTLE), tank) == 250, "bottle amount");
		ItemStack bottle = GenericItemFilling.fillItem(level, 250, new ItemStack(Items.GLASS_BOTTLE), tank);
		helper.assertTrue(bottle.is(Items.POTION) && bottle.get(DataComponents.POTION_CONTENTS).is(Potions.WATER) && tank.getAmount() == 350, "bottle " + bottle);
		helper.succeed();
	}

	@GameTest
	public void convertedRecipesWithFluidsAreLoaded(GameTestHelper helper) {
		var recipes = helper.getLevel().recipeAccess().getRecipes();
		boolean lava = false, filling = false, emptying = false, honey = false;
		for (RecipeHolder<?> holder : recipes) {
			String id = holder.id().identifier().toString();
			if (id.equals("create:mixing/lava_from_cobble") && holder.value() instanceof BasinRecipe recipe) {
				lava = recipe.getFluidResults().size() == 1 && recipe.getFluidResults().get(0).is(Fluids.LAVA)
					&& recipe.getFluidResults().get(0).getAmount() == 50 && recipe.getRequiredHeat() == HeatCondition.SUPERHEATED
					&& recipe.getIngredients().size() == 1 && recipe.getFluidIngredients().isEmpty();
			}
			if (id.equals("create:filling/blaze_cake") && holder.value() instanceof FillingRecipe recipe) {
				filling = recipe.getRequiredFluid().amount() == 250 && recipe.getRequiredFluid().test(new FluidStack(Fluids.LAVA, 250))
					&& !recipe.getRequiredFluid().test(new FluidStack(Fluids.LAVA, 200)) && !recipe.getRequiredFluid().test(water(250))
					&& recipe.rollResults(helper.getLevel().getRandom()).get(0).is(BurnerRegistry.BLAZE_CAKE);
			}
			if (id.equals("create:emptying/honey_bottle") && holder.value() instanceof EmptyingRecipe recipe) {
				emptying = recipe.getResultingFluid().is(FluidsRegistry.HONEY) && recipe.getResultingFluid().getAmount() == 250
					&& recipe.rollResults(helper.getLevel().getRandom()).get(0).is(Items.GLASS_BOTTLE);
			}
			if (id.equals("create:compacting/honey") && holder.value() instanceof BasinRecipe recipe) {
				// the honey tag has the honey fluid in it
				honey = recipe.getFluidIngredients().size() == 1 && recipe.getFluidIngredients().get(0).test(new FluidStack(FluidsRegistry.HONEY, 1000))
					&& !recipe.getFluidIngredients().get(0).test(new FluidStack(FluidsRegistry.HONEY, 999));
			}
		}
		helper.assertTrue(lava, "mixing/lava_from_cobble is missing or wrong");
		helper.assertTrue(filling, "filling/blaze_cake is missing or wrong");
		helper.assertTrue(emptying, "emptying/honey_bottle is missing or wrong");
		helper.assertTrue(honey, "compacting/honey is missing or wrong");
		helper.succeed();
	}

	@GameTest
	public void honeyAndChocolateBucketsWorkWithTheFluidApis(GameTestHelper helper) {
		Level level = helper.getLevel();
		for (var pair : List.of(
			dev.createpolymer.util.Pair.of(FluidsRegistry.HONEY, FluidsRegistry.HONEY_BUCKET),
			dev.createpolymer.util.Pair.of(FluidsRegistry.CHOCOLATE, FluidsRegistry.CHOCOLATE_BUCKET))) {
			var fluid = pair.getFirst();
			var bucket = pair.getSecond();
			helper.assertTrue(fluid.getBucket() == bucket, "the fluid does not know its bucket");
			var emptied = GenericItemEmptying.emptyItem(level, new ItemStack(bucket), false);
			helper.assertTrue(emptied.getFirst().is(fluid) && emptied.getFirst().getAmount() == 1000 && emptied.getSecond().is(Items.BUCKET),
				"bucket gave " + emptied);
			FluidStack available = new FluidStack(fluid, 1500);
			helper.assertTrue(GenericItemFilling.getRequiredAmountForItem(level, new ItemStack(Items.BUCKET), available) == 1000, "bucket amount");
			ItemStack filled = GenericItemFilling.fillItem(level, 1000, new ItemStack(Items.BUCKET), available);
			helper.assertTrue(filled.is(bucket) && available.getAmount() == 500, "filled " + filled);
		}
		// they cannot be poured out into the world
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		helper.assertTrue(!FluidsRegistry.HONEY_BUCKET.use(level, player, InteractionHand.MAIN_HAND).consumesAction(), "the bucket was used");
		helper.succeed();
	}

	@GameTest
	public void everyFluidDisplayHasAnItemDefinition(GameTestHelper helper) {
		List<FluidStack> fluids = List.of(water(1), new FluidStack(Fluids.LAVA, 1), new FluidStack(FluidsRegistry.HONEY, 1),
			new FluidStack(FluidsRegistry.CHOCOLATE, 1), new FluidStack(FluidsRegistry.TEA, 1),
			PotionFluid.of(1, new PotionContents(Potions.HARMING), BottleType.REGULAR), new FluidStack(Fluids.FLOWING_WATER, 1),
			new FluidStack(FluidsRegistry.HONEY.getFlowing(), 1));
		for (FluidStack fluid : fluids) {
			ItemStack stack = dev.createpolymer.fluids.FluidDisplay.stack(fluid);
			var model = stack.get(DataComponents.ITEM_MODEL);
			helper.assertTrue(model != null && model.getNamespace().equals("create") && TanksGameTests.class.getResource("/assets/create/items/" + model.getPath() + ".json") != null,
				"no item definition for " + fluid + ": " + model);
		}
		var generic = dev.createpolymer.fluids.FluidDisplay.stack(new FluidStack(Fluids.EMPTY, 1));
		helper.assertTrue(generic.isEmpty() || generic.get(DataComponents.ITEM_MODEL) != null, "empty fluid");
		helper.succeed();
	}

	// ---- basin ----

	private static BasinRecipe recipe(List<Ingredient> items, List<SizedFluidIngredient> fluids, List<ItemStack> results,
		List<FluidStack> fluidResults) {
		List<ProcessingOutput> outputs = new ArrayList<>();
		for (ItemStack result : results)
			outputs.add(new ProcessingOutput(result.getItem(), result.getCount(), DataComponentPatch.EMPTY, 1));
		return BasinRecipe.mixing(new Params(items, outputs, 0, HeatCondition.NONE, fluids, fluidResults));
	}

	@GameTest
	public void basinRecipeUsesAndMakesFluids(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, ModBlocks.BASIN);
		helper.runAfterDelay(2, () -> {
			BasinBlockEntity basin = helper.getBlockEntity(pos, BasinBlockEntity.class);
			BasinRecipe recipe = recipe(List.of(Ingredient.of(Items.SAND)),
				List.of(new SizedFluidIngredient(FluidIngredient.of(Fluids.WATER), 250)), List.of(new ItemStack(Items.CLAY_BALL)),
				List.of(new FluidStack(Fluids.LAVA, 100)));

			basin.insertInput(new ItemStack(Items.SAND, 2), false);
			helper.assertTrue(!BasinRecipeLogic.match(basin, recipe), "the recipe matches without water");
			helper.assertTrue(basin.getFluidCapability().fill(water(200), FluidAction.EXECUTE) == 200, "basin refused water");
			helper.assertTrue(!BasinRecipeLogic.match(basin, recipe), "the recipe matches with too little water");
			helper.assertTrue(basin.getFluidCapability().fill(water(300), FluidAction.EXECUTE) == 300, "basin refused water");

			helper.assertTrue(BasinRecipeLogic.match(basin, recipe), "the recipe does not match");
			helper.assertTrue(basin.outputInventory.isEmpty() && basin.getFluidCapability().getFluidInTank(0).isEmpty(), "matching changed the basin");
			helper.assertTrue(BasinRecipeLogic.apply(basin, recipe), "the recipe was not applied");

			boolean clay = false;
			for (ItemStack stack : basin.outputInventory.stacks())
				clay |= stack.is(Items.CLAY_BALL);
			helper.assertTrue(clay, "no clay");
			helper.assertTrue(basin.inputInventory.get(0).getCount() == 1, "one sand should be left, found " + basin.inputInventory.get(0));
			FluidStack output = basin.getFluidCapability().getFluidInTank(0);
			helper.assertTrue(output.is(Fluids.LAVA) && output.getAmount() == 100, "output tank holds " + output);
			int water = 0;
			for (int tank = 0; tank < basin.getFluidCapability().getTanks(); tank++)
				if (basin.getFluidCapability().getFluidInTank(tank).is(Fluids.WATER))
					water += basin.getFluidCapability().getFluidInTank(tank).getAmount();
			helper.assertTrue(water == 250, "250 mB of water should be left, found " + water);
			helper.succeed();
		});
	}

	@GameTest
	public void basinFluidsAreOnlyInsertedThroughTheInputTanks(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, ModBlocks.BASIN);
		helper.runAfterDelay(2, () -> {
			BasinBlockEntity basin = helper.getBlockEntity(pos, BasinBlockEntity.class);
			IFluidHandler handler = handlerAt(helper, pos);
			helper.assertTrue(handler != null && handler.getTanks() == 4, "basin has " + (handler == null ? 0 : handler.getTanks()) + " tanks");
			helper.assertTrue(handler.fill(water(1000), FluidAction.EXECUTE) == 1000, "an input tank of 1000 should take 1000 of water");
			// different fluids go to different tanks, so lava finds the second input tank
			helper.assertTrue(handler.fill(new FluidStack(Fluids.LAVA, 1500), FluidAction.EXECUTE) == 1000, "lava should fill the other input tank");
			helper.assertTrue(handler.fill(new FluidStack(FluidsRegistry.HONEY, 100), FluidAction.EXECUTE) == 0, "a third fluid found room");
			helper.assertTrue(basin.getTanks().getSecond().isEmpty(), "fluid went into the output tanks");
			helper.assertTrue(handler.drain(new FluidStack(Fluids.LAVA, 100), FluidAction.EXECUTE).getAmount() == 100, "lava was not drained");
			helper.succeed();
		});
	}

	@GameTest
	public void playerFillsAndEmptiesABasinWithBucketsAndBottles(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, ModBlocks.BASIN);
		helper.runAfterDelay(2, () -> {
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			BasinBlockEntity basin = helper.getBlockEntity(pos, BasinBlockEntity.class);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
			helper.useBlock(pos, player, hit(helper, pos, Direction.UP));
			helper.assertTrue(player.getMainHandItem().is(Items.BUCKET), "player holds " + player.getMainHandItem());
			helper.assertTrue(basin.inputTank.getPrimaryHandler().getFluid().is(Fluids.WATER) && basin.inputTank.getPrimaryHandler().getFluidAmount() == 1000,
				"the basin holds " + basin.inputTank.getPrimaryHandler().getFluid());

			// a glass bottle takes 250 mB of it
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE, 2));
			helper.useBlock(pos, player, hit(helper, pos, Direction.UP));
			helper.assertTrue(player.getMainHandItem().getCount() == 1, "player holds " + player.getMainHandItem());
			helper.assertTrue(player.getInventory().contains(waterBottle()), "no water bottle");
			helper.assertTrue(basin.inputTank.getPrimaryHandler().getFluidAmount() == 750, "the basin holds " + basin.inputTank.getPrimaryHandler().getFluid());

			// a sponge soaks up the rest
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SPONGE));
			helper.useBlock(pos, player, hit(helper, pos, Direction.UP));
			helper.assertTrue(basin.inputTank.isEmpty(), "the sponge left water behind");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 600)
	public void mixerMeltsAHoneyBlockOnABlazeBurner(GameTestHelper helper) {
		floor(helper);
		BlockPos burner = new BlockPos(2, 1, 2);
		BlockPos basinPos = new BlockPos(2, 2, 2);
		helper.setBlock(burner, BurnerRegistry.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, HeatLevel.SMOULDERING));
		helper.setBlock(basinPos, ModBlocks.BASIN);
		helper.setBlock(new BlockPos(2, 4, 2), ModBlocks.MECHANICAL_MIXER);
		helper.setBlock(new BlockPos(3, 3, 2), ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
		helper.setBlock(new BlockPos(3, 4, 2), ModBlocks.COGWHEEL.defaultBlockState().setValue(dev.createpolymer.kinetics.base.RotatedPillarKineticBlock.AXIS, Direction.Axis.Y));
		helper.runAfterDelay(2, () -> {
			helper.getBlockEntity(new BlockPos(3, 3, 2), CreativeMotorBlockEntity.class).setSetting(128);
			BlazeBurnerBlock.tryInsert(helper.getBlockState(burner), helper.getLevel(), helper.absolutePos(burner), new ItemStack(Items.OAK_PLANKS),
				false, true, false);
			helper.getBlockEntity(basinPos, BasinBlockEntity.class).insertInput(new ItemStack(Items.HONEY_BLOCK), false);
		});
		helper.succeedWhen(() -> {
			BasinBlockEntity basin = helper.getBlockEntity(basinPos, BasinBlockEntity.class);
			int honey = 0;
			for (int tank = 0; tank < basin.getFluidCapability().getTanks(); tank++)
				if (basin.getFluidCapability().getFluidInTank(tank).is(FluidsRegistry.HONEY))
					honey += basin.getFluidCapability().getFluidInTank(tank).getAmount();
			helper.assertTrue(honey == 1000, "basin holds " + honey + " mB of honey");
			helper.assertTrue(basin.inputInventory.isEmpty(), "the honey block was not used up");
		});
	}

	@GameTest(maxTicks = 500)
	public void pressCompactsHoneyIntoABlock(GameTestHelper helper) {
		floor(helper);
		BlockPos basinPos = new BlockPos(2, 1, 2);
		helper.setBlock(basinPos, ModBlocks.BASIN);
		helper.setBlock(new BlockPos(1, 3, 2), ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(2, 3, 2), ModBlocks.MECHANICAL_PRESS.defaultBlockState().setValue(MechanicalPressBlock.HORIZONTAL_FACING, Direction.EAST));
		helper.runAfterDelay(2, () -> {
			helper.getBlockEntity(new BlockPos(1, 3, 2), CreativeMotorBlockEntity.class).setSetting(256);
			helper.getBlockEntity(basinPos, BasinBlockEntity.class).getFluidCapability().fill(new FluidStack(FluidsRegistry.HONEY, 1000), FluidAction.EXECUTE);
		});
		helper.succeedWhen(() -> {
			BasinBlockEntity basin = helper.getBlockEntity(basinPos, BasinBlockEntity.class);
			boolean block = false;
			for (ItemStack stack : basin.outputInventory.stacks())
				block |= stack.is(Items.HONEY_BLOCK);
			helper.assertTrue(block, "no honey block yet");
			helper.assertTrue(basin.getFluidCapability().getFluidInTank(2).isEmpty() && basin.getFluidCapability().getFluidInTank(3).isEmpty(),
				"the honey was not used up");
		});
	}

	// ---- spout ----

	@GameTest(maxTicks = 200)
	public void spoutFillsABottleWithWaterOnADepot(GameTestHelper helper) {
		floor(helper);
		BlockPos depotPos = new BlockPos(2, 1, 2);
		BlockPos spoutPos = new BlockPos(2, 3, 2);
		helper.setBlock(depotPos, ModBlocks.DEPOT);
		helper.setBlock(spoutPos, TanksRegistry.SPOUT);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(handlerAt(helper, spoutPos).fill(water(1000), FluidAction.EXECUTE) == 1000, "spout refused water");
			helper.assertTrue(FluidHandlerLookup.get(helper.getLevel(), helper.absolutePos(spoutPos), Direction.DOWN) == null,
				"the underside of a spout should not be a fluid port");
			helper.getBlockEntity(depotPos, DepotBlockEntity.class).setHeldItem(new ItemStack(Items.GLASS_BOTTLE, 2));
		});
		helper.succeedWhen(() -> {
			DepotBlockEntity depot = helper.getBlockEntity(depotPos, DepotBlockEntity.class);
			ItemStack held = depot.getHeldItem();
			int potions = 0;
			for (ItemStack stack : allStacks(depot))
				if (stack.is(Items.POTION) && stack.get(DataComponents.POTION_CONTENTS).is(Potions.WATER))
					potions += stack.getCount();
			helper.assertTrue(potions == 2, "depot holds " + held + " and " + potions + " water bottles");
			FluidStack left = helper.getBlockEntity(spoutPos, SpoutBlockEntity.class).getTank().getPrimaryHandler().getFluid();
			helper.assertTrue(left.getAmount() == 500, "spout has " + left + " left");
		});
	}

	private static List<ItemStack> allStacks(DepotBlockEntity depot) {
		List<ItemStack> stacks = new ArrayList<>();
		for (int slot = 0; slot < depot.depotBehaviour.itemHandler.getSlots(); slot++)
			stacks.add(depot.depotBehaviour.itemHandler.getStackInSlot(slot));
		return stacks;
	}

	@GameTest(maxTicks = 200)
	public void spoutUsesFillingRecipes(GameTestHelper helper) {
		floor(helper);
		BlockPos depotPos = new BlockPos(2, 1, 2);
		BlockPos spoutPos = new BlockPos(2, 3, 2);
		helper.setBlock(depotPos, ModBlocks.DEPOT);
		helper.setBlock(spoutPos, TanksRegistry.SPOUT);
		helper.runAfterDelay(2, () -> {
			handlerAt(helper, spoutPos).fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE);
			helper.getBlockEntity(depotPos, DepotBlockEntity.class).setHeldItem(new ItemStack(BurnerRegistry.BLAZE_CAKE_BASE));
		});
		helper.succeedWhen(() -> {
			DepotBlockEntity depot = helper.getBlockEntity(depotPos, DepotBlockEntity.class);
			boolean cake = false;
			for (ItemStack stack : allStacks(depot))
				cake |= stack.is(BurnerRegistry.BLAZE_CAKE);
			helper.assertTrue(cake, "depot holds " + depot.getHeldItem());
			FluidStack left = helper.getBlockEntity(spoutPos, SpoutBlockEntity.class).getTank().getPrimaryHandler().getFluid();
			helper.assertTrue(left.getAmount() == 750, "spout has " + left + " left");
		});
	}

	@GameTest(maxTicks = 100)
	public void spoutWaitsForFluidAndPassesWrongItems(GameTestHelper helper) {
		floor(helper);
		BlockPos depotPos = new BlockPos(2, 1, 2);
		BlockPos spoutPos = new BlockPos(2, 3, 2);
		helper.setBlock(depotPos, ModBlocks.DEPOT);
		helper.setBlock(spoutPos, TanksRegistry.SPOUT);
		helper.runAfterDelay(2, () -> {
			handlerAt(helper, spoutPos).fill(water(1000), FluidAction.EXECUTE);
			// iron cannot be filled: it stays as it is
			helper.getBlockEntity(depotPos, DepotBlockEntity.class).setHeldItem(new ItemStack(Items.IRON_INGOT, 3));
		});
		helper.runAfterDelay(60, () -> {
			DepotBlockEntity depot = helper.getBlockEntity(depotPos, DepotBlockEntity.class);
			helper.assertTrue(depot.getHeldItem().is(Items.IRON_INGOT) && depot.getHeldItem().getCount() == 3, "depot holds " + depot.getHeldItem());
			helper.assertTrue(helper.getBlockEntity(spoutPos, SpoutBlockEntity.class).getTank().getPrimaryHandler().getFluidAmount() == 1000, "water was used");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void spoutTurnsDirtIntoMud(GameTestHelper helper) {
		floor(helper);
		BlockPos spoutPos = new BlockPos(2, 3, 2);
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.DIRT);
		helper.setBlock(spoutPos, TanksRegistry.SPOUT);
		helper.runAfterDelay(2, () -> handlerAt(helper, spoutPos).fill(water(1000), FluidAction.EXECUTE));
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 1, 2)).is(Blocks.MUD), "dirt was not turned into mud");
			helper.assertTrue(helper.getBlockEntity(spoutPos, SpoutBlockEntity.class).getTank().getPrimaryHandler().getFluidAmount() == 750, "water left");
		});
	}

	// ---- item drain ----

	@GameTest(maxTicks = 200)
	public void itemDrainEmptiesABucketIntoItsTank(GameTestHelper helper) {
		floor(helper);
		BlockPos drainPos = new BlockPos(2, 1, 2);
		helper.setBlock(drainPos, TanksRegistry.ITEM_DRAIN);
		helper.runAfterDelay(2, () -> {
			ItemDrainBlockEntity drain = helper.getBlockEntity(drainPos, ItemDrainBlockEntity.class);
			DirectBeltInputBehaviour input = drain.getBehaviour(DirectBeltInputBehaviour.TYPE);
			ItemStack remainder = input.handleInsertion(new ItemStack(Items.WATER_BUCKET, 2), Direction.NORTH, false);
			helper.assertTrue(remainder.getCount() == 1 && remainder.is(Items.WATER_BUCKET), "the drain should take one bucket, left " + remainder);
		});
		helper.succeedWhen(() -> {
			FluidStack inside = handlerAt(helper, drainPos).getFluidInTank(0);
			helper.assertTrue(inside.is(Fluids.WATER) && inside.getAmount() == 1000, "drain holds " + inside);
		});
	}

	@GameTest(maxTicks = 200)
	public void itemDrainUsesEmptyingRecipesAndLetsPlayersTakeTheFluid(GameTestHelper helper) {
		floor(helper);
		BlockPos drainPos = new BlockPos(2, 1, 2);
		helper.setBlock(drainPos, TanksRegistry.ITEM_DRAIN);
		helper.runAfterDelay(2, () -> {
			ItemDrainBlockEntity drain = helper.getBlockEntity(drainPos, ItemDrainBlockEntity.class);
			drain.getBehaviour(DirectBeltInputBehaviour.TYPE).handleInsertion(new ItemStack(Items.HONEY_BOTTLE), Direction.NORTH, false);
		});
		helper.runAfterDelay(40, () -> {
			FluidStack inside = handlerAt(helper, drainPos).getFluidInTank(0);
			helper.assertTrue(inside.is(FluidsRegistry.HONEY) && inside.getAmount() == 250, "drain holds " + inside);
			// pipes and players cannot put fluid into the drain, only take it out
			helper.assertTrue(handlerAt(helper, drainPos).fill(water(100), FluidAction.EXECUTE) == 0, "fluid was inserted from outside");
			helper.assertTrue(FluidHandlerLookup.get(helper.getLevel(), helper.absolutePos(drainPos), Direction.UP) == null, "the top is a fluid port");
			FluidStack drained = handlerAt(helper, drainPos).drain(100, FluidAction.EXECUTE);
			helper.assertTrue(drained.getAmount() == 100, "drained " + drained);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void playerEmptiesABucketIntoTheDrain(GameTestHelper helper) {
		floor(helper);
		BlockPos drainPos = new BlockPos(2, 1, 2);
		helper.setBlock(drainPos, TanksRegistry.ITEM_DRAIN);
		helper.runAfterDelay(2, () -> {
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LAVA_BUCKET));
			helper.useBlock(drainPos, player, hit(helper, drainPos, Direction.UP));
			FluidStack inside = handlerAt(helper, drainPos).getFluidInTank(0);
			helper.assertTrue(inside.is(Fluids.LAVA) && inside.getAmount() == 1000, "drain holds " + inside);
			helper.assertTrue(player.getMainHandItem().is(Items.BUCKET), "player holds " + player.getMainHandItem());
			helper.succeed();
		});
	}
}
