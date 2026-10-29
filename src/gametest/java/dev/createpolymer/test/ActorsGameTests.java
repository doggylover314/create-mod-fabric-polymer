package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import java.util.List;

import org.apache.commons.lang3.tuple.MutablePair;

import dev.createpolymer.api.behaviour.movement.MovementBehaviour;
import dev.createpolymer.api.contraption.storage.fluid.MountedFluidStorage;
import dev.createpolymer.api.contraption.storage.fluid.MountedFluidStorageType;
import dev.createpolymer.api.contraption.storage.item.MountedItemStorage;
import dev.createpolymer.api.contraption.storage.item.MountedItemStorageType;
import dev.createpolymer.contraptions.Contraption;
import dev.createpolymer.contraptions.ControlledContraptionEntity;
import dev.createpolymer.contraptions.actors.contraptionControls.ContraptionControlsBlock;
import dev.createpolymer.contraptions.actors.contraptionControls.ContraptionControlsBlockEntity;
import dev.createpolymer.contraptions.actors.contraptionControls.ContraptionControlsMovingInteraction;
import dev.createpolymer.contraptions.actors.psi.PortableStorageInterfaceBlock;
import dev.createpolymer.contraptions.actors.psi.PortableStorageInterfaceBlockEntity;
import dev.createpolymer.contraptions.actors.roller.RollerBlock;
import dev.createpolymer.contraptions.actors.roller.RollerBlockEntity;
import dev.createpolymer.contraptions.actors.seat.SeatBlock;
import dev.createpolymer.contraptions.actors.seat.SeatEntity;
import dev.createpolymer.contraptions.behaviour.MovementContext;
import dev.createpolymer.contraptions.glue.SuperGlueEntity;
import dev.createpolymer.contraptions.piston.MechanicalPistonBlock;
import dev.createpolymer.fluids.tank.FluidTankBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.IFluidHandler.FluidAction;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.IItemHandlerModifiable;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.kinetics.base.DirectionalAxisKineticBlock;
import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.motor.CreativeMotorBlockEntity;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.feature.ActorsRegistry;
import dev.createpolymer.registry.feature.ContraptionRegistry;
import dev.createpolymer.registry.feature.CrushingRegistry;
import dev.createpolymer.registry.feature.DeployerRegistry;
import dev.createpolymer.registry.feature.EquipmentRegistry;
import dev.createpolymer.registry.feature.MiscRegistry;
import dev.createpolymer.registry.feature.TanksRegistry;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ActorsGameTests {

	private static final BlockPos PISTON = new BlockPos(2, 3, 2);
	private static final BlockPos EAST_PISTON = new BlockPos(2, 1, 2);

	// ---- setups ----

	/** A piston facing up at (2,3,2) with two poles under it, as in ContraptionGameTests: it can extend by two. */
	private static void upPiston(GameTestHelper helper) {
		floor(helper);
		for (int y = 1; y <= 2; y++)
			helper.setBlock(new BlockPos(2, y, 2), ContraptionRegistry.PISTON_EXTENSION_POLE.defaultBlockState()
				.setValue(MechanicalPistonBlock.FACING, Direction.UP));
		helper.setBlock(PISTON, ContraptionRegistry.MECHANICAL_PISTON.defaultBlockState()
			.setValue(MechanicalPistonBlock.FACING, Direction.UP)
			.setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, false));
	}

	/** A piston facing east at (2,1,2) with two poles behind it (x=1, x=0). */
	private static void eastPiston(GameTestHelper helper) {
		floor(helper);
		for (int x = 0; x <= 1; x++)
			helper.setBlock(new BlockPos(x, 1, 2), ContraptionRegistry.PISTON_EXTENSION_POLE.defaultBlockState()
				.setValue(MechanicalPistonBlock.FACING, Direction.EAST));
		helper.setBlock(EAST_PISTON, ContraptionRegistry.MECHANICAL_PISTON.defaultBlockState()
			.setValue(MechanicalPistonBlock.FACING, Direction.EAST)
			.setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, false));
	}

	/** The motor sits north of the piston and turns its Z axis; a negative speed extends the piston. */
	private static void power(GameTestHelper helper, BlockPos piston, int rpm) {
		BlockPos motor = piston.north();
		helper.setBlock(motor, ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.SOUTH));
		helper.runAfterDelay(2, () -> helper.getBlockEntity(motor, CreativeMotorBlockEntity.class).setSetting(rpm));
	}

	private static List<ControlledContraptionEntity> contraptions(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ControlledContraptionEntity.class,
			new AABB(helper.absolutePos(new BlockPos(0, 0, 0))).inflate(16));
	}

	private static String dump(GameTestHelper helper, BlockPos from, BlockPos to) {
		StringBuilder sb = new StringBuilder();
		for (BlockPos p : BlockPos.betweenClosed(from, to))
			if (!helper.getBlockState(p).isAir())
				sb.append(p.toShortString()).append('=').append(helper.getBlockState(p).getBlock().getDescriptionId()
					.replace("block.minecraft.", "").replace("block.create.", "c:")).append(' ');
		return sb + " | " + contraptions(helper).size() + " contraptions";
	}

	private static SeatBlock seat() {
		return ActorsRegistry.SEATS.get(DyeColor.RED);
	}

	private static List<SeatEntity> seatEntities(GameTestHelper helper, BlockPos relative) {
		return helper.getLevel().getEntitiesOfClass(SeatEntity.class, new AABB(helper.absolutePos(relative)));
	}

	private static List<ItemEntity> items(GameTestHelper helper, net.minecraft.world.item.Item item) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16),
			e -> e.getItem().is(item));
	}

	private static StructureBlockInfo blockAt(ControlledContraptionEntity entity, net.minecraft.world.level.block.Block block) {
		for (StructureBlockInfo info : entity.getContraption().getBlocks().values())
			if (info.state().is(block))
				return info;
		return null;
	}

	// ---- seats ----

	@GameTest(maxTicks = 40)
	public void sixteenSeatColoursAreRegisteredAsTheSeatEntityTypeIsCalledCreateSeat(GameTestHelper helper) {
		helper.assertTrue(ActorsRegistry.SEATS.size() == 16, "seats " + ActorsRegistry.SEATS.size());
		helper.assertTrue(ActorsRegistry.SEAT_ITEMS.size() == 16, "seat items");
		for (DyeColor color : DyeColor.values()) {
			SeatBlock block = ActorsRegistry.SEATS.get(color);
			helper.assertTrue(block.getColor() == color, "colour of " + color);
			helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString()
				.equals("create:" + color.getSerializedName() + "_seat"), "block id of " + color);
		}
		helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(ActorsRegistry.SEAT).toString()
			.equals("create:seat"), "seat entity id");
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void aMobSitsOnASeatAndGetsUpWhenTheSeatIsBroken(GameTestHelper helper) {
		floor(helper);
		BlockPos seatPos = new BlockPos(2, 1, 2);
		helper.setBlock(seatPos, seat().defaultBlockState());
		Pig pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, 4, 1, 4);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(!SeatBlock.isSeatOccupied(helper.getLevel(), helper.absolutePos(seatPos)), "occupied before sitting");
			SeatBlock.sitDown(helper.getLevel(), helper.absolutePos(seatPos), pig);
			helper.assertTrue(pig.getVehicle() instanceof SeatEntity, "the pig does not ride a seat: " + pig.getVehicle());
			helper.assertTrue(SeatBlock.isSeatOccupied(helper.getLevel(), helper.absolutePos(seatPos)), "seat is not occupied");
			helper.assertTrue(seatEntities(helper, seatPos).size() == 1, "seat entities " + seatEntities(helper, seatPos).size());
			Vec3 at = pig.position();
			double dx = at.x - helper.absolutePos(seatPos).getX() - .5, dz = at.z - helper.absolutePos(seatPos).getZ() - .5;
			helper.assertTrue(Math.abs(dx) < .01 && Math.abs(dz) < .01, "pig is not over the seat, off by " + dx + ", " + dz);
			double height = at.y - helper.absolutePos(seatPos).getY();
			helper.assertTrue(height > -0.5 && height < 0.5, "pig height over the seat floor " + height);
			// the seat is a ride of its own: nothing else got in
			helper.assertTrue(!(pig.getVehicle().getPassengers().size() > 1), "more than one passenger");
			helper.setBlock(seatPos, Blocks.AIR);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(pig.getVehicle() == null, "the pig still sits");
			helper.assertTrue(seatEntities(helper, seatPos).isEmpty(), "the seat entity is still there");
		});
	}

	@GameTest(maxTicks = 120)
	public void aMobThatFallsOntoASeatSitsDown(GameTestHelper helper) {
		floor(helper);
		BlockPos seatPos = new BlockPos(2, 1, 2);
		helper.setBlock(seatPos, seat().defaultBlockState());
		Pig pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, new Vec3(2.5, 4, 2.5));
		helper.succeedWhen(() -> helper.assertTrue(pig.getVehicle() instanceof SeatEntity, "the pig did not sit down, it is at " + pig.position()));
	}

	@GameTest(maxTicks = 40)
	public void aPlayerSitsOnASeatWithARightClickAndDyeRecolours(GameTestHelper helper) {
		floor(helper);
		BlockPos seatPos = new BlockPos(2, 1, 2);
		helper.setBlock(seatPos, seat().defaultBlockState());
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setPos(helper.absoluteVec(new Vec3(3.5, 1, 3.5)));
		helper.runAfterDelay(1, () -> {
			helper.useBlock(seatPos, player);
			helper.assertTrue(player.getVehicle() instanceof SeatEntity, "the player did not sit down: " + player.getVehicle());
			player.stopRiding();
			// a dye of another colour recolours the seat and keeps the state
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DYE.blue()));
			helper.useBlock(seatPos, player);
			helper.assertTrue(helper.getBlockState(seatPos).getBlock() == ActorsRegistry.SEATS.get(DyeColor.BLUE),
				"the seat is " + helper.getBlockState(seatPos).getBlock());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void seatCollisionIsLowerForPlayersThanForMobs(GameTestHelper helper) {
		BlockState state = seat().defaultBlockState();
		helper.assertTrue(state.getShape(helper.getLevel(), BlockPos.ZERO).max(Direction.Axis.Y) == .5, "selection shape");
		helper.assertTrue(state.getCollisionShape(helper.getLevel(), BlockPos.ZERO,
			net.minecraft.world.phys.shapes.CollisionContext.empty()).max(Direction.Axis.Y) == 6 / 16d, "mob collision");
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		helper.assertTrue(state.getCollisionShape(helper.getLevel(), BlockPos.ZERO,
			net.minecraft.world.phys.shapes.CollisionContext.of(player)).max(Direction.Axis.Y) == 3 / 16d, "player collision");
		helper.succeed();
	}

	@GameTest(maxTicks = 300)
	public void aSeatOnAPistonContraptionCarriesTheMobAndSeatsItDownAgain(GameTestHelper helper) {
		upPiston(helper);
		helper.setBlock(new BlockPos(2, 4, 2), Blocks.STONE);
		BlockPos seatPos = new BlockPos(2, 5, 2);
		helper.setBlock(seatPos, seat().defaultBlockState());
		Pig pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, 2, 5, 2);
		double startY = pig.getY();
		helper.runAfterDelay(1, () -> SeatBlock.sitDown(helper.getLevel(), helper.absolutePos(seatPos), pig));
		power(helper, PISTON, -64);
		helper.runAfterDelay(30, () -> {
			List<ControlledContraptionEntity> list = contraptions(helper);
			helper.assertTrue(list.size() == 1, "contraptions " + list.size());
			ControlledContraptionEntity entity = list.get(0);
			helper.assertTrue(entity.getContraption().getSeats().size() == 1, "seats of the contraption");
			helper.assertTrue(pig.getVehicle() == entity, "the pig does not ride the contraption: " + pig.getVehicle());
			helper.assertTrue(entity.getContraption().getSeatMapping().containsKey(pig.getUUID()), "seat mapping");
			helper.assertTrue(pig.getY() > startY + .3, "the pig has not been lifted: " + (pig.getY() - startY));
			helper.assertTrue(seatEntities(helper, seatPos).isEmpty(), "the old seat entity lives on");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 7, 2)).getBlock() == seat(), "the seat did not come out at y=7");
			helper.assertTrue(contraptions(helper).isEmpty(), "contraption entity still exists");
			helper.assertTrue(pig.getVehicle() instanceof SeatEntity, "the pig does not sit on the placed seat: " + pig.getVehicle());
			helper.assertTrue(seatEntities(helper, new BlockPos(2, 7, 2)).size() == 1, "no seat entity at the new seat");
		});
	}

	@GameTest(maxTicks = 200)
	public void aMobStandingInThePathOfAMovingSeatSitsOnIt(GameTestHelper helper) {
		upPiston(helper);
		helper.setBlock(new BlockPos(2, 4, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(2, 5, 2), seat().defaultBlockState());
		// the pig stands on the seat's cushion and is lifted by it
		Pig pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, new Vec3(2.5, 5.45, 2.5));
		power(helper, PISTON, -64);
		helper.succeedWhen(() -> {
			helper.assertTrue(pig.getVehicle() instanceof ControlledContraptionEntity || pig.getVehicle() instanceof SeatEntity,
				"the pig did not sit down: " + pig.getVehicle() + " at " + pig.position());
		});
	}

	// ---- drill, harvester, plough ----

	@GameTest(maxTicks = 500)
	public void aDrillOnAPistonContraptionBreaksTheStoneInFrontOfItAndDropsIt(GameTestHelper helper) {
		upPiston(helper);
		helper.setBlock(new BlockPos(2, 4, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(2, 5, 2), CrushingRegistry.MECHANICAL_DRILL.defaultBlockState()
			.setValue(DirectionalKineticBlock.FACING, Direction.UP));
		helper.setBlock(new BlockPos(2, 6, 2), Blocks.STONE);
		power(helper, PISTON, -128);
		helper.runAfterDelay(20, () -> helper.assertTrue(!contraptions(helper).isEmpty() || helper.getBlockState(new BlockPos(2, 6, 2)).isAir(),
			"the contraption never started"));
		helper.succeedWhen(() -> {
			helper.assertTrue(contraptions(helper).isEmpty(), "still moving");
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 7, 2)).is(CrushingRegistry.MECHANICAL_DRILL),
				"the drill is not at the end: y=7 is " + helper.getBlockState(new BlockPos(2, 7, 2)).getBlock() + ", y=6 "
					+ helper.getBlockState(new BlockPos(2, 6, 2)).getBlock());
			helper.assertTrue(!items(helper, Items.COBBLESTONE).isEmpty(), "the stone dropped no cobblestone");
		});
	}

	@GameTest(maxTicks = 500)
	public void aHarvesterOnAPistonContraptionHarvestsAGrownWheatCropAndReplantsIt(GameTestHelper helper) {
		eastPiston(helper);
		helper.setBlock(new BlockPos(3, 1, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(4, 1, 2), ActorsRegistry.MECHANICAL_HARVESTER.defaultBlockState()
			.setValue(RollerBlock.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(5, 0, 2), Blocks.FARMLAND);
		helper.setBlock(new BlockPos(5, 1, 2), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
		// a young crop beside it must stay: the harvester only reaches the cell in front of it
		power(helper, EAST_PISTON, -64);
		helper.succeedWhen(() -> {
			BlockState crop = helper.getBlockState(new BlockPos(5, 1, 2));
			helper.assertTrue(crop.is(Blocks.WHEAT) && crop.getValue(CropBlock.AGE) == 0, "the wheat was not harvested and replanted: " + crop);
			helper.assertTrue(!items(helper, Items.WHEAT).isEmpty(), "no wheat dropped");
		});
	}

	@GameTest(maxTicks = 500)
	public void aPloughOnAPistonContraptionTillsTheDirtItPassesOver(GameTestHelper helper) {
		eastPiston(helper);
		helper.setBlock(new BlockPos(3, 1, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(4, 1, 2), ActorsRegistry.MECHANICAL_PLOUGH.defaultBlockState()
			.setValue(RollerBlock.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(5, 0, 2), Blocks.DIRT);
		helper.setBlock(new BlockPos(6, 0, 2), Blocks.GRASS_BLOCK);
		power(helper, EAST_PISTON, -64);
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(new BlockPos(5, 0, 2)).is(Blocks.FARMLAND), "dirt is " + helper.getBlockState(new BlockPos(5, 0, 2)));
			helper.assertTrue(helper.getBlockState(new BlockPos(6, 0, 2)).is(Blocks.FARMLAND), "grass is " + helper.getBlockState(new BlockPos(6, 0, 2)));
		});
	}

	// ---- roller ----

	@GameTest(maxTicks = 40)
	public void aRollerSharesItsMaterialAndModeWithAdjacentRollers(GameTestHelper helper) {
		floor(helper);
		BlockPos a = new BlockPos(2, 1, 2), b = new BlockPos(3, 1, 2);
		BlockState roller = ActorsRegistry.MECHANICAL_ROLLER.defaultBlockState().setValue(RollerBlock.FACING, Direction.NORTH);
		helper.setBlock(a, roller);
		helper.setBlock(b, roller);
		helper.runAfterDelay(2, () -> {
			RollerBlockEntity first = helper.getBlockEntity(a, RollerBlockEntity.class);
			RollerBlockEntity second = helper.getBlockEntity(b, RollerBlockEntity.class);
			first.filtering.setFilter(new ItemStack(Items.STONE_BRICKS));
			first.mode.setValue(RollerBlockEntity.RollingMode.WIDE_FILL.ordinal());
			helper.assertTrue(second.filtering.getFilter().is(Items.STONE_BRICKS), "filter was not shared: " + second.filtering.getFilter());
			helper.assertTrue(second.mode.get() == RollerBlockEntity.RollingMode.WIDE_FILL, "mode was not shared: " + second.mode.get());
			// stairs and block entities cannot pave
			first.filtering.setFilter(new ItemStack(Items.OAK_STAIRS));
			helper.assertTrue(first.filtering.getFilter().is(Items.STONE_BRICKS), "stairs were accepted");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 500)
	public void aRollerOnAPistonContraptionPavesTheGroundBelowIt(GameTestHelper helper) {
		eastPiston(helper);
		// the roller works two blocks below itself: it hangs at y=3, so it paves at y=1. Glue holds the hanging
		// blocks to the stone in front of the piston.
		helper.setBlock(new BlockPos(3, 1, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(3, 2, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(3, 3, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(4, 3, 2), ActorsRegistry.MECHANICAL_ROLLER.defaultBlockState()
			.setValue(RollerBlock.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(3, 4, 2), Blocks.CHEST);
		helper.getBlockEntity(new BlockPos(3, 4, 2), ChestBlockEntity.class).setItem(0, new ItemStack(Items.STONE_BRICKS, 8));
		helper.runAfterDelay(1, () -> {
			BlockPos a = helper.absolutePos(new BlockPos(3, 1, 2)), b = helper.absolutePos(new BlockPos(4, 4, 2));
			helper.getLevel().addFreshEntity(new SuperGlueEntity(helper.getLevel(), SuperGlueEntity.span(a, b)));
			RollerBlockEntity roller = helper.getBlockEntity(new BlockPos(4, 3, 2), RollerBlockEntity.class);
			roller.filtering.setFilter(new ItemStack(Items.STONE_BRICKS));
			roller.mode.setValue(RollerBlockEntity.RollingMode.STRAIGHT_FILL.ordinal());
		});
		power(helper, EAST_PISTON, -64);
		helper.succeedWhen(() -> helper.assertTrue(helper.getBlockState(new BlockPos(6, 1, 2)).is(Blocks.STONE_BRICKS)
			|| helper.getBlockState(new BlockPos(5, 1, 2)).is(Blocks.STONE_BRICKS), "nothing was paved: "
				+ dump(helper, new BlockPos(0, 0, 2), new BlockPos(7, 5, 2))));
	}

	// ---- portable storage interface ----

	@GameTest(maxTicks = 600)
	public void twoStorageInterfacesMoveItemsBetweenAContraptionChestAndAHopperChest(GameTestHelper helper) {
		eastPiston(helper);
		helper.setBlock(new BlockPos(3, 1, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(4, 1, 2), Blocks.CHEST);
		helper.getBlockEntity(new BlockPos(4, 1, 2), ChestBlockEntity.class).setItem(0, new ItemStack(Items.DIAMOND, 7));
		helper.setBlock(new BlockPos(5, 1, 2), ActorsRegistry.PORTABLE_STORAGE_INTERFACE.defaultBlockState()
			.setValue(PortableStorageInterfaceBlock.FACING, Direction.EAST));
		// the stationary interface, a hopper under it and a chest under that one
		helper.setBlock(new BlockPos(8, 1, 2), ActorsRegistry.PORTABLE_STORAGE_INTERFACE.defaultBlockState()
			.setValue(PortableStorageInterfaceBlock.FACING, Direction.WEST));
		helper.setBlock(new BlockPos(8, 0, 2), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
		helper.setBlock(new BlockPos(8, -1, 2), Blocks.CHEST);
		power(helper, EAST_PISTON, -64);
		helper.succeedWhen(() -> {
			ChestBlockEntity sink = helper.getBlockEntity(new BlockPos(8, -1, 2), ChestBlockEntity.class);
			int total = 0;
			for (int i = 0; i < sink.getContainerSize(); i++)
				if (sink.getItem(i).is(Items.DIAMOND))
					total += sink.getItem(i).getCount();
			helper.assertTrue(total > 0, "the hopper chest has received nothing");
		});
	}

	@GameTest(maxTicks = 600)
	public void twoFluidInterfacesMoveFluidBetweenAContraptionTankAndThePipesOutside(GameTestHelper helper) {
		eastPiston(helper);
		helper.setBlock(new BlockPos(3, 1, 2), TanksRegistry.FLUID_TANK);
		helper.setBlock(new BlockPos(4, 1, 2), ActorsRegistry.PORTABLE_FLUID_INTERFACE.defaultBlockState()
			.setValue(PortableStorageInterfaceBlock.FACING, Direction.EAST));
		BlockPos stationary = new BlockPos(8, 1, 2);
		helper.setBlock(stationary, ActorsRegistry.PORTABLE_FLUID_INTERFACE.defaultBlockState()
			.setValue(PortableStorageInterfaceBlock.FACING, Direction.WEST));
		helper.runAfterDelay(1, () -> helper.getBlockEntity(new BlockPos(3, 1, 2), FluidTankBlockEntity.class).getTankInventory()
			.setFluid(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 4000)));
		power(helper, EAST_PISTON, -64);
		int[] drained = {0};
		helper.succeedWhen(() -> {
			var handler = dev.createpolymer.foundation.fluid.FluidHandlerLookup.get(helper.getLevel(), helper.absolutePos(stationary), null);
			helper.assertTrue(handler != null, "the interface has no fluid handler");
			FluidStack out = handler.drain(500, FluidAction.EXECUTE);
			drained[0] += out.getAmount();
			helper.assertTrue(drained[0] >= 500, "drained " + drained[0] + " from the contraption tank");
		});
	}

	// ---- contraption controls ----

	@GameTest(maxTicks = 200)
	public void contraptionControlsSwitchOffActorsOfTheirFilteredType(GameTestHelper helper) {
		upPiston(helper);
		helper.setBlock(new BlockPos(2, 4, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(2, 5, 2), ActorsRegistry.CONTRAPTION_CONTROLS.defaultBlockState()
			.setValue(ContraptionControlsBlock.FACING, Direction.NORTH));
		helper.setBlock(new BlockPos(2, 6, 2), CrushingRegistry.MECHANICAL_DRILL.defaultBlockState()
			.setValue(DirectionalKineticBlock.FACING, Direction.UP));
		helper.runAfterDelay(2, () -> {
			FilteringBehaviour filtering = helper.getBlockEntity(new BlockPos(2, 5, 2), ContraptionControlsBlockEntity.class).filtering;
			filtering.setFilter(new ItemStack(CrushingRegistry.MECHANICAL_DRILL_ITEM));
			helper.assertTrue(filtering.getFilter().is(CrushingRegistry.MECHANICAL_DRILL_ITEM), "the filter does not take a drill");
			filtering.setFilter(new ItemStack(Items.DIRT));
			helper.assertTrue(filtering.getFilter().is(CrushingRegistry.MECHANICAL_DRILL_ITEM), "the filter takes dirt");
		});
		power(helper, PISTON, -16);
		helper.runAfterDelay(20, () -> {
			ControlledContraptionEntity entity = contraptions(helper).get(0);
			Contraption contraption = entity.getContraption();
			StructureBlockInfo controls = blockAt(entity, ActorsRegistry.CONTRAPTION_CONTROLS);
			StructureBlockInfo drill = blockAt(entity, CrushingRegistry.MECHANICAL_DRILL);
			MutablePair<StructureBlockInfo, MovementContext> drillActor = contraption.getActorAt(drill.pos());
			helper.assertTrue(drillActor != null && !drillActor.right.disabled, "the drill is not an active actor");
			ContraptionControlsMovingInteraction interaction = new ContraptionControlsMovingInteraction();
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			helper.assertTrue(interaction.handlePlayerInteraction(player, InteractionHand.MAIN_HAND, controls.pos(), entity), "no interaction");
			helper.assertTrue(drillActor.right.disabled, "the drill was not switched off");
			helper.assertTrue(contraption.isActorTypeDisabled(new ItemStack(CrushingRegistry.MECHANICAL_DRILL_ITEM)), "the type is not disabled");
			helper.assertTrue(!MovementBehaviour.REGISTRY.get(drill.state()).isActive(drillActor.right), "the drill still acts");
			interaction.handlePlayerInteraction(player, InteractionHand.MAIN_HAND, controls.pos(), entity);
			helper.assertTrue(!drillActor.right.disabled, "the drill was not switched on again");
			helper.assertTrue(!contraption.isActorTypeDisabled(new ItemStack(CrushingRegistry.MECHANICAL_DRILL_ITEM)), "the type is still disabled");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void clickingTheControlsToggleTheirSwitchAndRedstoneSwitchesThemOff(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, ActorsRegistry.CONTRAPTION_CONTROLS.defaultBlockState());
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		helper.runAfterDelay(2, () -> {
			ContraptionControlsBlockEntity controls = helper.getBlockEntity(pos, ContraptionControlsBlockEntity.class);
			helper.assertTrue(!controls.disabled, "disabled from the start");
			helper.useBlock(pos, player);
			helper.assertTrue(controls.disabled, "click did not switch the controls off");
			helper.useBlock(pos, player);
			helper.assertTrue(!controls.disabled, "click did not switch the controls on again");
			// power switches them off
			helper.setBlock(new BlockPos(3, 1, 2), Blocks.REDSTONE_BLOCK);
			helper.runAfterDelay(3, () -> {
				helper.assertTrue(controls.disabled && controls.powered, "redstone did not switch the controls off");
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = 500)
	public void aContactOnAContraptionPowersAStationaryContactItPassesAndReleasesIt(GameTestHelper helper) {
		eastPiston(helper);
		helper.setBlock(new BlockPos(3, 1, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(4, 1, 2), dev.createpolymer.registry.feature.RedstoneRegistry.REDSTONE_CONTACT.defaultBlockState()
			.setValue(dev.createpolymer.redstone.contact.RedstoneContactBlock.FACING, Direction.NORTH));
		BlockPos stationary = new BlockPos(5, 1, 1);
		helper.setBlock(stationary, dev.createpolymer.registry.feature.RedstoneRegistry.REDSTONE_CONTACT.defaultBlockState()
			.setValue(dev.createpolymer.redstone.contact.RedstoneContactBlock.FACING, Direction.SOUTH));
		power(helper, EAST_PISTON, -32);
		boolean[] seenPowered = {false};
		helper.succeedWhen(() -> {
			boolean powered = helper.getBlockState(stationary).getValue(dev.createpolymer.redstone.contact.RedstoneContactBlock.POWERED);
			seenPowered[0] |= powered;
			helper.assertTrue(seenPowered[0], "the contact was never powered");
			helper.assertTrue(contraptions(helper).isEmpty() && !powered, "the contact stays powered");
		});
	}

	@GameTest(maxTicks = 500)
	public void aFunnelOnAContraptionPicksUpItemsInFrontOfItIntoTheContraptionChest(GameTestHelper helper) {
		eastPiston(helper);
		helper.setBlock(new BlockPos(3, 1, 2), Blocks.CHEST);
		helper.setBlock(new BlockPos(4, 1, 2), dev.createpolymer.registry.feature.FunnelRegistry.ANDESITE_FUNNEL.defaultBlockState()
			.setValue(dev.createpolymer.logistics.funnel.FunnelBlock.FACING, Direction.EAST));
		power(helper, EAST_PISTON, -32);
		helper.runAfterDelay(1, () -> helper.spawnItem(Items.GOLD_INGOT, 5.5f, 1.2f, 2.5f).setDeltaMovement(Vec3.ZERO));
		helper.succeedWhen(() -> {
			helper.assertTrue(contraptions(helper).isEmpty(), "still moving");
			ChestBlockEntity chest = helper.getBlockEntity(new BlockPos(5, 1, 2), ChestBlockEntity.class);
			boolean found = false;
			for (int i = 0; i < chest.getContainerSize(); i++)
				found |= chest.getItem(i).is(Items.GOLD_INGOT);
			helper.assertTrue(found, "the chest did not receive the gold ingot");
		});
	}

	@GameTest(maxTicks = 40)
	public void theContraptionControlledTagListsTheActors(GameTestHelper helper) {
		for (net.minecraft.world.item.Item item : List.of(CrushingRegistry.MECHANICAL_DRILL_ITEM, ActorsRegistry.MECHANICAL_HARVESTER_ITEM,
			ActorsRegistry.MECHANICAL_PLOUGH_ITEM, ActorsRegistry.MECHANICAL_ROLLER_ITEM, ActorsRegistry.PORTABLE_STORAGE_INTERFACE_ITEM,
			DeployerRegistry.DEPLOYER_ITEM, Items.BELL))
			helper.assertTrue(new ItemStack(item).is(ActorsRegistry.CONTRAPTION_CONTROLLED), item + " is not contraption-controlled");
		helper.assertTrue(!new ItemStack(Items.DIRT).is(ActorsRegistry.CONTRAPTION_CONTROLLED), "dirt is");
		helper.succeed();
	}

	// ---- movement behaviours and mounted storage ----

	@GameTest(maxTicks = 40)
	public void actorsAreRegisteredForTheirBlocks(GameTestHelper helper) {
		for (Entry e : List.of(new Entry(CrushingRegistry.MECHANICAL_DRILL, "DrillMovementBehaviour"),
			new Entry(dev.createpolymer.registry.feature.SawRegistry.MECHANICAL_SAW, "SawMovementBehaviour"),
			new Entry(DeployerRegistry.DEPLOYER, "DeployerMovementBehaviour"),
			new Entry(dev.createpolymer.registry.feature.FunnelRegistry.BRASS_FUNNEL, "FunnelMovementBehaviour"),
			new Entry(dev.createpolymer.registry.feature.BurnerRegistry.BLAZE_BURNER, "BlazeBurnerMovementBehaviour"),
			new Entry(ModBlocks.BASIN, "BasinMovementBehaviour"),
			new Entry(dev.createpolymer.registry.feature.RedstoneRegistry.REDSTONE_CONTACT, "ContactMovementBehaviour"),
			new Entry(ActorsRegistry.MECHANICAL_HARVESTER, "HarvesterMovementBehaviour"),
			new Entry(ActorsRegistry.PORTABLE_FLUID_INTERFACE, "PortableStorageInterfaceMovement"),
			new Entry(seat(), "SeatMovementBehaviour"))) {
			MovementBehaviour behaviour = MovementBehaviour.REGISTRY.get(e.block);
			helper.assertTrue(behaviour != null && behaviour.getClass().getSimpleName().equals(e.name),
				e.block + " has " + (behaviour == null ? null : behaviour.getClass().getSimpleName()) + ", expected " + e.name);
		}
		helper.assertTrue(dev.createpolymer.api.behaviour.interaction.MovingInteractionBehaviour.REGISTRY.get(seat()) != null, "seat interaction");
		helper.assertTrue(dev.createpolymer.api.behaviour.interaction.MovingInteractionBehaviour.REGISTRY.get(DeployerRegistry.DEPLOYER) != null, "deployer interaction");
		helper.succeed();
	}

	private record Entry(net.minecraft.world.level.block.Block block, String name) {}

	@GameTest(maxTicks = 40)
	public void creativeCrateToolboxAndTankStoragesMountAndSurviveSaving(GameTestHelper helper) {
		floor(helper);
		var level = helper.getLevel();
		var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);

		BlockPos crate = new BlockPos(1, 1, 1);
		helper.setBlock(crate, MiscRegistry.CREATIVE_CRATE);
		helper.runAfterDelay(2, () -> {
			helper.getBlockEntity(crate, dev.createpolymer.logistics.crate.CreativeCrateBlockEntity.class)
				.getBehaviour(FilteringBehaviour.TYPE).setFilter(new ItemStack(Items.EMERALD));
			MountedItemStorageType<?> type = MountedItemStorageType.REGISTRY.get(MiscRegistry.CREATIVE_CRATE);
			helper.assertTrue(type == ActorsRegistry.CREATIVE_CRATE_MOUNTED_STORAGE, "creative crate type " + type);
			MountedItemStorage storage = ActorsRegistry.CREATIVE_CRATE_MOUNTED_STORAGE.mount(level, MiscRegistry.CREATIVE_CRATE.defaultBlockState(),
				helper.absolutePos(crate), level.getBlockEntity(helper.absolutePos(crate)));
			helper.assertTrue(storage != null && storage.extractItem(0, 5, false).is(Items.EMERALD), "the crate supplies no emeralds");
			helper.assertTrue(storage.extractItem(0, 5, false).getCount() == 5, "the crate does not supply endlessly");
			Tag encoded = MountedItemStorage.CODEC.encodeStart(ops, storage).getOrThrow();
			MountedItemStorage decoded = MountedItemStorage.CODEC.parse(ops, encoded).getOrThrow();
			helper.assertTrue(decoded.type == ActorsRegistry.CREATIVE_CRATE_MOUNTED_STORAGE && decoded.extractItem(0, 1, true).is(Items.EMERALD), "crate storage did not survive saving");

			BlockPos toolbox = new BlockPos(2, 1, 1);
			helper.setBlock(toolbox, EquipmentRegistry.TOOLBOXES.get(DyeColor.BLUE));
			helper.runAfterDelay(2, () -> {
				var be = helper.getBlockEntity(toolbox, dev.createpolymer.equipment.toolbox.ToolboxBlockEntity.class);
				IItemHandlerModifiable inventory = (IItemHandlerModifiable) ItemHandlerLookup.get(level, helper.absolutePos(toolbox), null);
				inventory.setStackInSlot(0, new ItemStack(Items.IRON_PICKAXE));
				MountedItemStorageType<?> toolboxType = MountedItemStorageType.REGISTRY.get(EquipmentRegistry.TOOLBOXES.get(DyeColor.BLUE));
				helper.assertTrue(toolboxType == ActorsRegistry.TOOLBOX_MOUNTED_STORAGE, "toolbox type " + toolboxType);
				MountedItemStorage mounted = toolboxType.mount(level, be.getBlockState(), helper.absolutePos(toolbox), be);
				helper.assertTrue(mounted != null && mounted.getStackInSlot(0).is(Items.IRON_PICKAXE), "toolbox contents");
				mounted.setStackInSlot(1, new ItemStack(Items.IRON_PICKAXE));
				Tag toolboxEncoded = MountedItemStorage.CODEC.encodeStart(ops, mounted).getOrThrow();
				MountedItemStorage toolboxDecoded = MountedItemStorage.CODEC.parse(ops, toolboxEncoded).getOrThrow();
				helper.assertTrue(toolboxDecoded.getStackInSlot(1).is(Items.IRON_PICKAXE), "toolbox did not survive saving");
				mounted.unmount(level, be.getBlockState(), helper.absolutePos(toolbox), be);
				helper.assertTrue(inventory.getStackInSlot(1).is(Items.IRON_PICKAXE), "unmounting did not copy the contents back");

				BlockPos tank = new BlockPos(4, 1, 1);
				helper.setBlock(tank, TanksRegistry.FLUID_TANK);
				helper.runAfterDelay(2, () -> {
					FluidTankBlockEntity tankBe = helper.getBlockEntity(tank, FluidTankBlockEntity.class);
					tankBe.getTankInventory().setFluid(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 3000));
					MountedFluidStorageType<?> fluidType = MountedFluidStorageType.REGISTRY.get(TanksRegistry.FLUID_TANK);
					helper.assertTrue(fluidType == ActorsRegistry.FLUID_TANK_MOUNTED_STORAGE, "tank type " + fluidType);
					MountedFluidStorage fluid = fluidType.mount(level, tankBe.getBlockState(), helper.absolutePos(tank), tankBe);
					helper.assertTrue(fluid != null && fluid.getFluidInTank(0).getAmount() == 3000, "tank contents");
					helper.assertTrue(fluid.drain(1000, FluidAction.EXECUTE).getAmount() == 1000, "drain");
					Tag fluidEncoded = MountedFluidStorage.CODEC.encodeStart(ops, fluid).getOrThrow();
					MountedFluidStorage fluidDecoded = MountedFluidStorage.CODEC.parse(ops, fluidEncoded).getOrThrow();
					helper.assertTrue(fluidDecoded.getFluidInTank(0).getAmount() == 2000, "tank did not survive saving");
					fluid.unmount(level, tankBe.getBlockState(), helper.absolutePos(tank), tankBe);
					helper.assertTrue(tankBe.getTankInventory().getFluidAmount() == 2000, "unmounting did not write the fluid back");
					helper.assertTrue(MountedFluidStorageType.REGISTRY.get(TanksRegistry.CREATIVE_FLUID_TANK) == ActorsRegistry.CREATIVE_FLUID_TANK_MOUNTED_STORAGE, "creative tank type");
					helper.assertTrue(MountedItemStorageType.REGISTRY.get(Blocks.DROPPER) == ActorsRegistry.DISPENSER_MOUNTED_STORAGE && ActorsRegistry.DISPENSER_MOUNTED_STORAGE.isInternal(), "dispenser type");
					helper.succeed();
				});
			});
		});
	}

	// ---- deployer ----

	@GameTest(maxTicks = 400)
	public void aDeployerOnAPistonContraptionPlacesABlockFromTheContraptionChest(GameTestHelper helper) {
		eastPiston(helper);
		helper.setBlock(new BlockPos(3, 1, 2), Blocks.CHEST);
		helper.getBlockEntity(new BlockPos(3, 1, 2), ChestBlockEntity.class).setItem(0, new ItemStack(Items.COBBLESTONE, 16));
		helper.setBlock(new BlockPos(4, 1, 2), DeployerRegistry.DEPLOYER.defaultBlockState()
			.setValue(DirectionalKineticBlock.FACING, Direction.EAST)
			.setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, false));
		power(helper, EAST_PISTON, -32);
		helper.succeedWhen(() -> {
			// the deployer reaches two blocks in front of itself
			boolean placed = false;
			for (int x = 5; x <= 7; x++)
				placed |= helper.getBlockState(new BlockPos(x, 1, 2)).is(Blocks.COBBLESTONE);
			helper.assertTrue(placed, "the deployer placed nothing: " + dump(helper, new BlockPos(0, 1, 2), new BlockPos(7, 3, 2)));
		});
	}

	@GameTest(maxTicks = 300)
	public void aBurnerAndABasinOnAContraptionDoNotBreakTheContraption(GameTestHelper helper) {
		upPiston(helper);
		helper.setBlock(new BlockPos(2, 4, 2), dev.createpolymer.registry.feature.BurnerRegistry.BLAZE_BURNER.defaultBlockState());
		helper.setBlock(new BlockPos(2, 5, 2), ModBlocks.BASIN);
		helper.runAfterDelay(2, () -> helper.getBlockEntity(new BlockPos(2, 5, 2), dev.createpolymer.processing.basin.BasinBlockEntity.class)
			.inputInventory.set(0, new ItemStack(Items.DIAMOND, 3)));
		power(helper, PISTON, -128);
		helper.succeedWhen(() -> {
			helper.assertTrue(contraptions(helper).isEmpty(), "still moving");
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 7, 2)).is(ModBlocks.BASIN), "basin is at " + dump(helper, new BlockPos(2, 1, 2), new BlockPos(2, 8, 2)));
			helper.assertTrue(helper.getBlockEntity(new BlockPos(2, 7, 2), dev.createpolymer.processing.basin.BasinBlockEntity.class)
				.inputInventory.get(0).is(Items.DIAMOND), "the basin lost its items");
		});
	}
}
