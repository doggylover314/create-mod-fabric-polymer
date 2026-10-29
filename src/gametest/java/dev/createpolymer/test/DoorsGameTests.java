package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.assertSpeed;
import static dev.createpolymer.test.TestUtil.floor;

import java.util.List;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.contraptions.AbstractContraptionEntity;
import dev.createpolymer.contraptions.ControlledContraptionEntity;
import dev.createpolymer.contraptions.elevator.ElevatorContactBlock;
import dev.createpolymer.contraptions.elevator.ElevatorContactBlockEntity;
import dev.createpolymer.contraptions.elevator.ElevatorContraption;
import dev.createpolymer.contraptions.elevator.ElevatorPulleyBlockEntity;
import dev.createpolymer.contraptions.behaviour.DoorMovingInteraction;
import dev.createpolymer.contraptions.glue.SuperGlueEntity;
import dev.createpolymer.decoration.TrainTrapdoorBlock;
import dev.createpolymer.decoration.bracket.BracketBlock;
import dev.createpolymer.decoration.bracket.BracketBlock.BracketType;
import dev.createpolymer.decoration.bracket.BracketedBlockEntityBehaviour;
import dev.createpolymer.decoration.girder.GirderBlock;
import dev.createpolymer.decoration.girder.GirderEncasedShaftBlock;
import dev.createpolymer.decoration.girder.GirderShapes;
import dev.createpolymer.decoration.placard.PlacardBlock;
import dev.createpolymer.decoration.placard.PlacardBlockEntity;
import dev.createpolymer.decoration.slidingDoor.DoorControl;
import dev.createpolymer.decoration.slidingDoor.DoorControlBehaviour;
import dev.createpolymer.decoration.slidingDoor.SlidingDoorBlock;
import dev.createpolymer.decoration.slidingDoor.SlidingDoorBlockEntity;
import dev.createpolymer.decoration.slidingDoor.SlidingDoorShapes;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.base.HorizontalKineticBlock;
import dev.createpolymer.kinetics.base.RotatedPillarKineticBlock;
import dev.createpolymer.kinetics.motor.CreativeMotorBlockEntity;
import dev.createpolymer.redstone.contact.RedstoneContactBlock;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.DoorsRegistry;
import dev.createpolymer.registry.feature.PipesRegistry;
import dev.createpolymer.registry.feature.PulleyRegistry;
import dev.createpolymer.registry.feature.RedstoneRegistry;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DoorsGameTests {

	private static final List<SlidingDoorBlock> DOORS = DoorsRegistry.SLIDING_DOORS;

	private static BlockHitResult hit(GameTestHelper helper, BlockPos pos, Direction face) {
		BlockPos abs = helper.absolutePos(pos);
		return new BlockHitResult(Vec3.atCenterOf(abs).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(.5)), face, abs, false);
	}

	private static Player survivalPlayer(GameTestHelper helper, ItemStack held) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		return player;
	}

	private static void door(GameTestHelper helper, BlockPos pos, SlidingDoorBlock block, Direction facing, DoorHingeSide hinge) {
		BlockState lower = block.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HINGE, hinge);
		helper.setBlock(pos, lower);
		helper.setBlock(pos.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
	}

	private static boolean open(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockState(pos).getValue(DoorBlock.OPEN);
	}

	private static VoxelShape collision(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockState(pos).getCollisionShape(helper.getLevel(), helper.absolutePos(pos));
	}

	private static List<ItemEntity> items(GameTestHelper helper, BlockPos pos) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(2));
	}

	private static ItemDisplayElement element(GameTestHelper helper, BlockPos pos, int index) {
		BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(pos));
		helper.assertTrue(attachment != null, "no model at " + pos);
		int i = 0;
		for (VirtualElement element : attachment.holder().getElements())
			if (element instanceof ItemDisplayElement display) {
				if (i++ == index)
					return display;
			}
		helper.fail("no display " + index + " at " + pos);
		return null;
	}

	private static int shownElements(GameTestHelper helper, BlockPos pos) {
		BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(pos));
		if (attachment == null)
			return -1;
		int shown = 0;
		for (VirtualElement element : attachment.holder().getElements())
			if (element instanceof ItemDisplayElement display && !display.getItem().isEmpty())
				shown++;
		return shown;
	}

	private static BlockPos cabinOf(AbstractContraptionEntity entity) {
		return entity.getContraption().getBlocks().values().stream()
			.filter(info -> info.state().getBlock() instanceof SlidingDoorBlock && info.state().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)
			.findFirst().orElseThrow().pos();
	}

	// ---- registration ----

	@GameTest(maxTicks = 20)
	public void everyBlockHasItsItemRecipeAndLootTable(GameTestHelper helper) {
		var server = helper.getLevel().getServer();
		var manager = server.getRecipeManager();
		for (Block block : DoorsRegistry.BLOCKS) {
			Identifier id = BuiltInRegistries.BLOCK.getKey(block);
			boolean hasItem = block != DoorsRegistry.METAL_GIRDER_ENCASED_SHAFT;
			helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id) == hasItem, "item of " + id);
			helper.assertTrue(block.getLootTable().isPresent()
				&& server.reloadableRegistries().getLootTable(block.getLootTable().get()) != net.minecraft.world.level.storage.loot.LootTable.EMPTY, "loot table of " + id);
			if (hasItem) {
				ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, CreatePolymer.id("crafting/kinetics/" + id.getPath()));
				helper.assertTrue(manager.byKey(key).isPresent(), "recipe of " + id);
			}
		}
		helper.assertTrue(DoorsRegistry.BLOCKS.size() == 12, "registered " + DoorsRegistry.BLOCKS.size() + " blocks");
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void foldingDoorsAreTheAndesiteAndCopperOnes(GameTestHelper helper) {
		helper.assertTrue(DoorsRegistry.ANDESITE_DOOR.isFoldingDoor() && DoorsRegistry.COPPER_DOOR.isFoldingDoor(), "andesite and copper fold");
		helper.assertTrue(!DoorsRegistry.BRASS_DOOR.isFoldingDoor() && !DoorsRegistry.TRAIN_DOOR.isFoldingDoor()
			&& !DoorsRegistry.FRAMED_GLASS_DOOR.isFoldingDoor(), "brass, train and glass slide");
		for (Direction d : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST))
			for (boolean hinge : new boolean[] { true, false })
				helper.assertTrue(SlidingDoorShapes.get(d, hinge, true) != SlidingDoorShapes.get(d, hinge, false), "folded shape equals the sliding one");
		helper.succeed();
	}

	// ---- sliding doors ----

	@GameTest(maxTicks = 80)
	public void slidingDoorOpensAndClosesOnUse(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		door(helper, pos, DoorsRegistry.TRAIN_DOOR, Direction.NORTH, DoorHingeSide.LEFT);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		helper.assertTrue(!open(helper, pos) && helper.getBlockState(pos).getValue(SlidingDoorBlock.VISIBLE), "a placed door is not closed and visible");

		helper.runAfterDelay(2, () -> {
			helper.useBlock(pos, player);
			helper.assertTrue(open(helper, pos), "door did not open");
			helper.assertTrue(open(helper, pos.above()), "upper half did not open");
			helper.assertTrue(!helper.getBlockState(pos).getValue(SlidingDoorBlock.VISIBLE), "an opening door is still visible");
		});
		helper.runAfterDelay(14, () -> {
			helper.assertTrue(helper.getBlockEntity(pos, SlidingDoorBlockEntity.class).getAnimationValue() == 1, "animation did not reach open");
			helper.useBlock(pos, player);
			helper.assertTrue(!open(helper, pos) && !open(helper, pos.above()), "door did not close");
			helper.assertTrue(!helper.getBlockState(pos).getValue(SlidingDoorBlock.VISIBLE), "a closing door shows its block before it arrives");
		});
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(helper.getBlockState(pos).getValue(SlidingDoorBlock.VISIBLE), "the closed door did not come back");
			helper.assertTrue(helper.getBlockState(pos.above()).getValue(SlidingDoorBlock.VISIBLE), "the upper half did not come back");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void everySlidingDoorOpensAndHasATexturedModel(GameTestHelper helper) {
		floor(helper);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		for (int i = 0; i < DOORS.size(); i++) {
			BlockPos pos = new BlockPos(1 + i, 1, 2);
			door(helper, pos, DOORS.get(i), Direction.SOUTH, DoorHingeSide.RIGHT);
			helper.useBlock(pos, player);
		}
		helper.runAfterDelay(4, () -> {
			for (int i = 0; i < DOORS.size(); i++) {
				BlockPos pos = new BlockPos(1 + i, 1, 2);
				helper.assertTrue(open(helper, pos), DOORS.get(i) + " did not open");
				// a model of the lower half draws two pieces, the upper half has none of its own
				helper.assertTrue(shownElements(helper, pos) == 2, "pieces of " + DOORS.get(i) + ": " + shownElements(helper, pos));
				helper.assertTrue(BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(pos.above())) == null, "the upper half has a model");
			}
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void slidingDoorCollisionAndHitboxFollowTheState(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		door(helper, pos, DoorsRegistry.BRASS_DOOR, Direction.EAST, DoorHingeSide.LEFT);
		SlidingDoorBlock block = DoorsRegistry.BRASS_DOOR;

		VoxelShape closed = collision(helper, pos);
		// a closed door east facing is a slab at the west side of the block, 16 high and as wide as the block
		helper.assertTrue(closed.bounds().getZsize() == 1 && Math.abs(closed.bounds().getXsize() - .1875) < 1e-6, "closed shape " + closed.bounds());
		// Polymer's door states are picked by the shape they have: the client's slab is the closed door's
		BlockState client = block.getPolymerBlockState(helper.getBlockState(pos), null);
		helper.assertTrue(client.getBlock() instanceof DoorBlock, "players do not see a door slab: " + client);
		helper.assertTrue(client.getCollisionShape(helper.getLevel(), helper.absolutePos(pos)).bounds().equals(closed.bounds()),
			"the slab players bump into is " + client.getCollisionShape(helper.getLevel(), helper.absolutePos(pos)).bounds() + " but the door's is " + closed.bounds());

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		helper.useBlock(pos, player);
		VoxelShape opened = collision(helper, pos);
		helper.assertTrue(opened == SlidingDoorShapes.get(Direction.EAST, false, false), "open shape " + opened.bounds());
		// the doorway is clear: nothing in the middle of the block
		helper.assertTrue(!Shapes.joinIsNotEmpty(opened, Shapes.box(.3, 0, .3, .7, 1, .7), net.minecraft.world.phys.shapes.BooleanOp.AND), "an open door blocks the middle");
		helper.assertTrue(block.getPolymerBlockState(helper.getBlockState(pos), null).isAir(), "players see something where the door slid away");

		helper.runAfterDelay(14, () -> {
			helper.useBlock(pos, player);
			// closing: panels are on their way, the block is not back yet
			helper.assertTrue(collision(helper, pos) == opened, "shape of a closing door is " + collision(helper, pos).bounds());
		});
		helper.runAfterDelay(34, () -> {
			helper.assertTrue(collision(helper, pos).bounds().equals(closed.bounds()), "shape after closing " + collision(helper, pos).bounds());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void slidingDoorOpensWithRedstoneAndClosesWithout(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		door(helper, pos, DoorsRegistry.ANDESITE_DOOR, Direction.NORTH, DoorHingeSide.LEFT);
		helper.runAfterDelay(3, () -> helper.setBlock(new BlockPos(1, 1, 2), Blocks.REDSTONE_BLOCK));
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(open(helper, pos) && open(helper, pos.above()), "the powered door did not open");
			helper.assertTrue(helper.getBlockState(pos).getValue(DoorBlock.POWERED), "the door is not marked powered");
			helper.setBlock(new BlockPos(1, 1, 2), Blocks.AIR);
		});
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(!open(helper, pos) && !open(helper, pos.above()), "the door stayed open without power");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void powerReachingTheUpperHalfOpensTheDoor(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		door(helper, pos, DoorsRegistry.COPPER_DOOR, Direction.NORTH, DoorHingeSide.LEFT);
		helper.runAfterDelay(3, () -> helper.setBlock(new BlockPos(1, 2, 2), Blocks.REDSTONE_BLOCK));
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(open(helper, pos) && open(helper, pos.above()), "the door did not open");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void doubleDoorsOpenTogether(GameTestHelper helper) {
		floor(helper);
		BlockPos left = new BlockPos(2, 1, 2);
		BlockPos right = new BlockPos(3, 1, 2);
		// facing north the two doors sit side by side with their hinges at the outside
		door(helper, left, DoorsRegistry.TRAIN_DOOR, Direction.NORTH, DoorHingeSide.LEFT);
		door(helper, right, DoorsRegistry.TRAIN_DOOR, Direction.NORTH, DoorHingeSide.RIGHT);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		helper.runAfterDelay(2, () -> {
			helper.useBlock(left, player);
			helper.assertTrue(open(helper, left) && open(helper, right), "the partner did not open: " + open(helper, left) + " " + open(helper, right));
			helper.assertTrue(open(helper, right.above()), "the partner's upper half did not open");
		});
		helper.runAfterDelay(14, () -> {
			helper.useBlock(right, player);
			helper.assertTrue(!open(helper, left) && !open(helper, right), "the pair did not close together");
		});
		helper.runAfterDelay(20, () -> helper.setBlock(new BlockPos(4, 1, 2), Blocks.REDSTONE_BLOCK));
		helper.runAfterDelay(26, () -> {
			helper.assertTrue(open(helper, left) && open(helper, right), "power on one door did not open both");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void slidingPanelsMoveInTheModel(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		// facing north the door is a slab at the south side; with the hinge on the left it slides to the west... by
		// Create's rule: clockwise of the facing (east), reversed for a left hinge
		door(helper, pos, DoorsRegistry.TRAIN_DOOR, Direction.NORTH, DoorHingeSide.LEFT);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		helper.runAfterDelay(3, () -> {
			var closedLower = element(helper, pos, 0).getTranslation();
			helper.assertTrue(Math.abs(closedLower.x()) < 1e-4 && Math.abs(closedLower.z()) < 1e-4, "closed lower panel is at " + closedLower);
			helper.assertTrue(Math.abs(element(helper, pos, 1).getTranslation().y() - (1 - 1 / 512f)) < 1e-4, "upper panel height");
			helper.useBlock(pos, player);
		});
		helper.runAfterDelay(16, () -> {
			var lower = element(helper, pos, 0).getTranslation();
			var upper = element(helper, pos, 1).getTranslation();
			// open: moved by 13/16 to the west, and 1/32 out of the frame towards the facing
			helper.assertTrue(Math.abs(lower.x() + 13 / 16f) < 1e-3, "open lower panel x " + lower.x());
			helper.assertTrue(Math.abs(lower.z() + 1 / 32f) < 1e-3, "open lower panel z " + lower.z());
			helper.assertTrue(Math.abs(upper.x() - lower.x()) < 1e-4 && Math.abs(upper.z() - lower.z()) < 1e-4, "upper panel moved differently");
			helper.useBlock(pos, player);
		});
		helper.runAfterDelay(34, () -> {
			var lower = element(helper, pos, 0).getTranslation();
			helper.assertTrue(Math.abs(lower.x()) < 1e-3 && Math.abs(lower.z()) < 1e-3, "the panel did not return: " + lower);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void foldingPanelsSwingOnTheirHingesInTheModel(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		door(helper, pos, DoorsRegistry.ANDESITE_DOOR, Direction.EAST, DoorHingeSide.LEFT);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(element(helper, pos, 1).getLeftRotation().w() > .999f || element(helper, pos, 1).getLeftRotation().w() < -.999f
				|| Math.abs(element(helper, pos, 1).getLeftRotation().y()) > .999f, "closed panels are turned");
			helper.useBlock(pos, player);
		});
		helper.runAfterDelay(16, () -> {
			// both panels folded about the vertical axis: the first by 91 degrees, the second back by 181 more
			var q0 = element(helper, pos, 0).getLeftRotation();
			var q1 = element(helper, pos, 1).getLeftRotation();
			helper.assertTrue(Math.abs(q0.x()) < 1e-4 && Math.abs(q0.z()) < 1e-4, "first panel does not turn about y: " + q0);
			helper.assertTrue(Math.abs(q1.x()) < 1e-4 && Math.abs(q1.z()) < 1e-4, "second panel does not turn about y: " + q1);
			helper.assertTrue(q0.y() * q1.y() < 0 || Math.abs(q0.y() - q1.y()) > .1, "the panels turned the same way");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void breakingSlidingDoorsDropsOneDoor(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		door(helper, pos, DoorsRegistry.FRAMED_GLASS_DOOR, Direction.NORTH, DoorHingeSide.LEFT);
		BlockState lower = helper.getBlockState(pos);
		BlockState upper = helper.getBlockState(pos.above());
		ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
		List<ItemStack> fromLower = Block.getDrops(lower, helper.getLevel(), helper.absolutePos(pos), null, null, pickaxe);
		List<ItemStack> fromUpper = Block.getDrops(upper, helper.getLevel(), helper.absolutePos(pos.above()), null, null, pickaxe);
		helper.assertTrue(fromLower.size() == 1 && fromLower.get(0).is(DoorsRegistry.FRAMED_GLASS_DOOR_ITEM), "lower drops " + fromLower);
		helper.assertTrue(fromUpper.isEmpty(), "upper drops " + fromUpper);
		helper.setBlock(pos, Blocks.AIR);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(helper.getBlockState(pos.above()).isAir(), "the upper half stayed");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void doorsPlaceFromTheirItem(GameTestHelper helper) {
		floor(helper);
		Player player = survivalPlayer(helper, new ItemStack(DoorsRegistry.BRASS_DOOR_ITEM, 2));
		helper.useBlock(new BlockPos(2, 0, 2), player, hit(helper, new BlockPos(2, 0, 2), Direction.UP));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 1, 2)).getBlock() == DoorsRegistry.BRASS_DOOR, "no door was placed");
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 2, 2)).getBlock() == DoorsRegistry.BRASS_DOOR
				&& helper.getBlockState(new BlockPos(2, 2, 2)).getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER, "no upper half");
			helper.assertTrue(helper.getBlockEntity(new BlockPos(2, 1, 2), SlidingDoorBlockEntity.class) != null, "no block entity");
			helper.succeed();
		});
	}

	// ---- trapdoors ----

	@GameTest(maxTicks = 40)
	public void trainTrapdoorsOpenOnUseAndShowTexturedStates(GameTestHelper helper) {
		floor(helper);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		for (TrainTrapdoorBlock trapdoor : List.of(DoorsRegistry.TRAIN_TRAPDOOR, DoorsRegistry.FRAMED_GLASS_TRAPDOOR)) {
			helper.assertTrue(trapdoor.isTextured(), trapdoor + " has no textured client states");
			BlockPos pos = new BlockPos(trapdoor == DoorsRegistry.TRAIN_TRAPDOOR ? 1 : 3, 1, 2);
			helper.setBlock(pos, trapdoor.defaultBlockState().setValue(TrapDoorBlock.FACING, Direction.WEST));
			BlockState closedClient = trapdoor.getPolymerBlockState(helper.getBlockState(pos), null);
			helper.useBlock(pos, player);
			helper.assertTrue(helper.getBlockState(pos).getValue(TrapDoorBlock.OPEN), trapdoor + " did not open");
			BlockState openClient = trapdoor.getPolymerBlockState(helper.getBlockState(pos), null);
			helper.assertTrue(closedClient != openClient, "open and closed look the same");
			helper.assertTrue(!closedClient.is(Blocks.IRON_TRAPDOOR) && !openClient.is(Blocks.IRON_TRAPDOOR), "vanilla fallback states");
			helper.assertTrue(!closedClient.getValue(TrapDoorBlock.OPEN) && openClient.getValue(TrapDoorBlock.OPEN), "client states do not open");
			helper.assertTrue(openClient.getValue(TrapDoorBlock.FACING) == Direction.WEST, "open client state faces " + openClient.getValue(TrapDoorBlock.FACING));
			helper.useBlock(pos, player);
			helper.assertTrue(!helper.getBlockState(pos).getValue(TrapDoorBlock.OPEN), trapdoor + " did not close");
		}
		// the top half and the waterlogged trapdoor have states of their own
		BlockState top = DoorsRegistry.TRAIN_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP);
		BlockState wet = DoorsRegistry.TRAIN_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.WATERLOGGED, true);
		helper.assertTrue(DoorsRegistry.TRAIN_TRAPDOOR.getPolymerBlockState(top, null) != DoorsRegistry.TRAIN_TRAPDOOR.getPolymerBlockState(DoorsRegistry.TRAIN_TRAPDOOR.defaultBlockState(), null), "top and bottom look the same");
		helper.assertTrue(DoorsRegistry.TRAIN_TRAPDOOR.getPolymerBlockState(wet, null).getValue(TrapDoorBlock.WATERLOGGED), "no waterlogged client state");
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void trapdoorsConnectLikeCreatesDo(GameTestHelper helper) {
		BlockState a = DoorsRegistry.TRAIN_TRAPDOOR.defaultBlockState();
		helper.assertTrue(TrainTrapdoorBlock.isConnected(a, a, Direction.EAST), "neighbouring closed trapdoors do not connect");
		helper.assertTrue(!TrainTrapdoorBlock.isConnected(a, a.setValue(TrapDoorBlock.OPEN, true), Direction.EAST), "open meets closed");
		helper.succeed();
	}

	// ---- girders ----

	private static BlockState beam(Axis axis) {
		return DoorsRegistry.METAL_GIRDER.defaultBlockState().setValue(GirderBlock.AXIS, axis)
			.setValue(GirderBlock.X, axis == Axis.X).setValue(GirderBlock.Z, axis == Axis.Z);
	}

	@GameTest(maxTicks = 20)
	public void girderShapesFollowTheirAxes(GameTestHelper helper) {
		floor(helper);
		BlockGetterHelper.check(helper, beam(Axis.X), GirderShapes.GIRDER_BEAM.get(Axis.X));
		BlockGetterHelper.check(helper, beam(Axis.Z), GirderShapes.GIRDER_BEAM.get(Axis.Z));
		BlockGetterHelper.check(helper, DoorsRegistry.METAL_GIRDER.defaultBlockState(), GirderShapes.EIGHT_VOXEL_POLE.get(Axis.Y));
		BlockGetterHelper.check(helper, beam(Axis.X).setValue(GirderBlock.Z, true), GirderShapes.GIRDER_CROSS);
		// the beam is 8 wide and 12 high, running the whole block
		var x = GirderShapes.GIRDER_BEAM.get(Axis.X).bounds();
		helper.assertTrue(x.getXsize() == 1 && Math.abs(x.getZsize() - .5) < 1e-6 && Math.abs(x.getYsize() - .75) < 1e-6, "beam bounds " + x);
		helper.succeed();
	}

	/** Compares the collision shape of a state placed in the world with the expected one. */
	private static final class BlockGetterHelper {
		static void check(GameTestHelper helper, BlockState state, VoxelShape expected) {
			helper.setBlock(new BlockPos(0, 1, 0), state);
			VoxelShape actual = collision(helper, new BlockPos(0, 1, 0));
			helper.assertTrue(actual.bounds().equals(expected.bounds()), "shape of " + state + " is " + actual.bounds() + ", expected " + expected.bounds());
			helper.setBlock(new BlockPos(0, 1, 0), Blocks.AIR);
		}
	}

	@GameTest(maxTicks = 40)
	public void girdersConnectToTheirNeighbours(GameTestHelper helper) {
		floor(helper);
		// a beam along x with a pole standing on it and another beam against its end
		BlockPos center = new BlockPos(3, 1, 3);
		helper.setBlock(center, beam(Axis.X).setValue(GirderBlock.AXIS, Axis.X));
		helper.setBlock(center.above(), DoorsRegistry.METAL_GIRDER.defaultBlockState());
		helper.setBlock(center.east(), beam(Axis.X));
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(helper.getBlockState(center).getValue(GirderBlock.TOP), "the beam is not connected to the pole above");
			helper.assertTrue(!helper.getBlockState(center).getValue(GirderBlock.BOTTOM), "the beam is connected downwards");
			helper.assertTrue(helper.getBlockState(center).getValue(GirderBlock.X) && !helper.getBlockState(center).getValue(GirderBlock.Z), "beam changed direction");
			helper.assertTrue(GirderBlock.isConnected(helper.getLevel(), helper.absolutePos(center.east()), helper.getBlockState(center.east()), Direction.EAST) == false,
				"a beam end with air next to it counts as connected");
			// the pole above stands on the beam: its bottom connects
			helper.assertTrue(helper.getBlockState(center.above()).getValue(GirderBlock.BOTTOM), "the pole is not connected to the beam under it");
			// a solid block next to the beam end gets a bracket
			helper.setBlock(center.east(2), Blocks.STONE);
			helper.assertTrue(GirderBlock.isConnected(helper.getLevel(), helper.absolutePos(center.east()), helper.getBlockState(center.east()), Direction.EAST),
				"a beam against stone has no bracket");
			helper.assertTrue(!GirderBlock.isConnected(helper.getLevel(), helper.absolutePos(center.east()), helper.getBlockState(center.east()), Direction.NORTH),
				"a beam along x connects sideways");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void girdersShowTheirPartsInTheModel(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, beam(Axis.X));
		helper.setBlock(pos.east(), Blocks.STONE);
		helper.runAfterDelay(12, () -> {
			// the beam and the bracket against the stone
			helper.assertTrue(shownElements(helper, pos) == 2, "pieces " + shownElements(helper, pos));
			helper.setBlock(pos.west(), Blocks.STONE);
		});
		helper.runAfterDelay(26, () -> {
			helper.assertTrue(shownElements(helper, pos) == 3, "pieces with two brackets " + shownElements(helper, pos));
			helper.setBlock(pos, beam(Axis.X).setValue(GirderBlock.Z, true));
		});
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(helper.getBlockState(pos).getValue(GirderBlock.X) && helper.getBlockState(pos).getValue(GirderBlock.Z), "not a cross");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void girderPlacementExtendsABeamAlongItself(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, beam(Axis.X));
		Player player = survivalPlayer(helper, new ItemStack(DoorsRegistry.METAL_GIRDER_ITEM, 5));
		helper.useBlock(pos, player, hit(helper, pos, Direction.EAST));
		helper.assertTrue(helper.getBlockState(pos.east()).getBlock() == DoorsRegistry.METAL_GIRDER, "no girder was added east");
		BlockState placed = helper.getBlockState(pos.east());
		helper.assertTrue(placed.getValue(GirderBlock.X) && !placed.getValue(GirderBlock.Z) && placed.getValue(GirderBlock.AXIS) == Axis.X, "new girder runs " + placed);
		helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 4, "girder not used up");
		// a pole is extended up and down only
		BlockPos poleAt = new BlockPos(4, 1, 4);
		helper.setBlock(poleAt, DoorsRegistry.METAL_GIRDER.defaultBlockState());
		helper.useBlock(poleAt, player, hit(helper, poleAt, Direction.UP));
		helper.assertTrue(helper.getBlockState(poleAt.above()).getBlock() == DoorsRegistry.METAL_GIRDER, "no girder was added above the pole");
		helper.assertTrue(!helper.getBlockState(poleAt.above()).getValue(GirderBlock.X) && !helper.getBlockState(poleAt.above()).getValue(GirderBlock.Z), "the extension is no pole");
		helper.useBlock(poleAt, player, hit(helper, poleAt, Direction.EAST));
		helper.assertTrue(helper.getBlockState(poleAt.east()).isAir(), "a pole was extended sideways");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void wrenchTogglesTheCapsOfABeam(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, beam(Axis.X));
		Player player = survivalPlayer(helper, new ItemStack(ModItems.WRENCH));
		helper.useBlock(pos, player, hit(helper, pos, Direction.UP));
		helper.assertTrue(helper.getBlockState(pos).getValue(GirderBlock.TOP), "the top cap did not switch on");
		helper.useBlock(pos, player, hit(helper, pos, Direction.UP));
		helper.assertTrue(!helper.getBlockState(pos).getValue(GirderBlock.TOP), "the top cap did not switch off");
		helper.useBlock(pos, player, hit(helper, pos, Direction.DOWN));
		helper.assertTrue(helper.getBlockState(pos).getValue(GirderBlock.BOTTOM), "the bottom cap did not switch on");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void shaftsAndGirdersMakeAnEncasedGirder(GameTestHelper helper) {
		floor(helper);
		// a girder takes a shaft...
		BlockPos girder = new BlockPos(2, 1, 2);
		helper.setBlock(girder, beam(Axis.X));
		Player player = survivalPlayer(helper, new ItemStack(ModItems.SHAFT, 2));
		helper.useBlock(girder, player, hit(helper, girder, Direction.UP));
		BlockState encased = helper.getBlockState(girder);
		helper.assertTrue(encased.getBlock() == DoorsRegistry.METAL_GIRDER_ENCASED_SHAFT, "girder is " + encased);
		// the girder ran along x, so the shaft runs across it
		helper.assertTrue(encased.getValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS) == Axis.Z, "shaft axis " + encased.getValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS));
		helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1, "shaft not used up");

		// ... and a horizontal shaft takes a girder
		BlockPos shaft = new BlockPos(4, 1, 4);
		helper.setBlock(shaft, ModBlocks.SHAFT.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.X));
		Player builder = survivalPlayer(helper, new ItemStack(DoorsRegistry.METAL_GIRDER_ITEM, 2));
		helper.useBlock(shaft, builder, hit(helper, shaft, Direction.UP));
		helper.assertTrue(helper.getBlockState(shaft).getBlock() == DoorsRegistry.METAL_GIRDER_ENCASED_SHAFT, "shaft is " + helper.getBlockState(shaft));
		helper.assertTrue(helper.getBlockState(shaft).getValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS) == Axis.X, "axis kept");
		helper.assertTrue(builder.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1, "girder not used up");

		// a vertical shaft does not fit
		BlockPos upright = new BlockPos(6, 1, 4);
		helper.setBlock(upright, ModBlocks.SHAFT.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.Y));
		helper.useBlock(upright, builder, hit(helper, upright, Direction.UP));
		helper.assertTrue(helper.getBlockState(upright).getBlock() == ModBlocks.SHAFT, "a vertical shaft was encased");
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void girderEncasedShaftTransmitsRotation(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(1, 1, 1), ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(2, 1, 1), ModBlocks.SHAFT.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.X));
		helper.setBlock(new BlockPos(3, 1, 1), DoorsRegistry.METAL_GIRDER_ENCASED_SHAFT.defaultBlockState().setValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS, Axis.X));
		helper.setBlock(new BlockPos(4, 1, 1), ModBlocks.SHAFT.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.X));
		helper.succeedWhen(() -> {
			assertSpeed(helper, new BlockPos(3, 1, 1), 16);
			assertSpeed(helper, new BlockPos(4, 1, 1), 16);
			// the shaft in the girder is a display of its own that spins
			helper.assertTrue(shownElements(helper, new BlockPos(3, 1, 1)) >= 2, "pieces " + shownElements(helper, new BlockPos(3, 1, 1)));
		});
	}

	@GameTest(maxTicks = 40)
	public void girderEncasedShaftCrossedByNothingDoesNotTurnFromTheSide(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(1, 1, 1), ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
		// the encased shaft runs along z: the motor's output meets its side
		helper.setBlock(new BlockPos(2, 1, 1), DoorsRegistry.METAL_GIRDER_ENCASED_SHAFT.defaultBlockState().setValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS, Axis.Z));
		helper.runAfterDelay(10, () -> {
			assertSpeed(helper, new BlockPos(2, 1, 1), 0);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void wrenchTakesTheShaftOutOfAGirderAndItDropsBoth(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		// a cap only stays where something above holds on to it
		helper.setBlock(pos.above(), Blocks.STONE);
		helper.setBlock(pos, DoorsRegistry.METAL_GIRDER_ENCASED_SHAFT.defaultBlockState().setValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS, Axis.Z)
			.setValue(GirderEncasedShaftBlock.TOP, true));
		helper.assertTrue(helper.getBlockState(pos).getValue(GirderEncasedShaftBlock.TOP), "the cap under the stone was lost");
		List<ItemStack> drops = Block.getDrops(helper.getBlockState(pos), helper.getLevel(), helper.absolutePos(pos), null, null, new ItemStack(Items.IRON_PICKAXE));
		helper.assertTrue(drops.stream().anyMatch(s -> s.is(DoorsRegistry.METAL_GIRDER_ITEM)) && drops.stream().anyMatch(s -> s.is(ModItems.SHAFT)), "drops " + drops);

		Player player = survivalPlayer(helper, new ItemStack(ModItems.WRENCH));
		helper.useBlock(pos, player, hit(helper, pos, Direction.UP));
		BlockState girder = helper.getBlockState(pos);
		helper.assertTrue(girder.getBlock() == DoorsRegistry.METAL_GIRDER, "wrenched into " + girder);
		// the shaft ran along z, so the girder runs along x
		helper.assertTrue(girder.getValue(GirderBlock.X) && !girder.getValue(GirderBlock.Z), "girder " + girder);
		helper.assertTrue(girder.getValue(GirderBlock.TOP), "the cap was lost");
		helper.assertTrue(player.getInventory().contains(s -> s.is(ModItems.SHAFT)), "the shaft was not handed back");
		helper.succeed();
	}

	// ---- brackets ----

	private static BracketedBlockEntityBehaviour bracketsOf(GameTestHelper helper, BlockPos pos) {
		return BlockEntityBehaviour.get(helper.getLevel(), helper.absolutePos(pos), BracketedBlockEntityBehaviour.TYPE);
	}

	@GameTest(maxTicks = 60)
	public void bracketsAttachToShaftsAndDropWhenTheyAreReplaced(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, ModBlocks.SHAFT.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.X));
		helper.assertTrue(bracketsOf(helper, pos) != null && !bracketsOf(helper, pos).isBracketPresent(), "a shaft has no bracket behaviour");

		Player player = survivalPlayer(helper, new ItemStack(DoorsRegistry.METAL_BRACKET_ITEM, 3));
		helper.useBlock(pos, player, hit(helper, pos, Direction.UP));
		BlockState bracket = bracketsOf(helper, pos).getBracket();
		helper.assertTrue(bracket != null && bracket.getBlock() == DoorsRegistry.METAL_BRACKET, "bracket " + bracket);
		helper.assertTrue(bracket.getValue(BracketBlock.TYPE) == BracketType.SHAFT && bracket.getValue(BracketBlock.FACING) == Direction.UP, "bracket state " + bracket);
		helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 2, "bracket not used up");

		// another kind replaces it and the old one comes back to the player
		Player swapper = survivalPlayer(helper, new ItemStack(DoorsRegistry.WOODEN_BRACKET_ITEM, 1));
		helper.useBlock(pos, swapper, hit(helper, pos, Direction.UP));
		helper.assertTrue(bracketsOf(helper, pos).getBracket().getBlock() == DoorsRegistry.WOODEN_BRACKET, "the bracket was not replaced");
		helper.assertTrue(swapper.getInventory().contains(s -> s.is(DoorsRegistry.METAL_BRACKET_ITEM)), "the old bracket was not returned");

		helper.runAfterDelay(4, () -> {
			// the bracket is drawn: the shaft and the bracket
			helper.assertTrue(shownElements(helper, pos) == 2, "pieces " + shownElements(helper, pos));
			// breaking the shaft drops the bracket
			helper.setBlock(pos, Blocks.AIR);
			helper.assertTrue(items(helper, pos).stream().anyMatch(e -> e.getItem().is(DoorsRegistry.WOODEN_BRACKET_ITEM)), "the bracket did not drop");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void wrenchTakesBracketsOffAndTheyKeepTheirTypes(GameTestHelper helper) {
		floor(helper);
		BlockPos cog = new BlockPos(2, 1, 2);
		BlockPos pipe = new BlockPos(4, 1, 2);
		helper.setBlock(cog, ModBlocks.COGWHEEL.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.X));
		helper.setBlock(pipe, PipesRegistry.FLUID_PIPE.getAxisState(Axis.Z));
		Player player = survivalPlayer(helper, new ItemStack(DoorsRegistry.METAL_BRACKET_ITEM, 4));
		helper.useBlock(cog, player, hit(helper, cog, Direction.UP));
		helper.useBlock(pipe, player, hit(helper, pipe, Direction.UP));
		helper.assertTrue(bracketsOf(helper, cog).getBracket().getValue(BracketBlock.TYPE) == BracketType.COG, "cog bracket " + bracketsOf(helper, cog).getBracket());
		helper.assertTrue(bracketsOf(helper, pipe).getBracket().getValue(BracketBlock.TYPE) == BracketType.PIPE, "pipe bracket " + bracketsOf(helper, pipe).getBracket());

		Player wrench = survivalPlayer(helper, new ItemStack(ModItems.WRENCH));
		helper.useBlock(cog, wrench, hit(helper, cog, Direction.UP));
		helper.assertTrue(!bracketsOf(helper, cog).isBracketPresent(), "the wrench left the cog's bracket");
		helper.assertTrue(helper.getBlockState(cog).getBlock() == ModBlocks.COGWHEEL, "the cog was rotated or removed");
		helper.assertTrue(wrench.getInventory().contains(s -> s.is(DoorsRegistry.METAL_BRACKET_ITEM)), "bracket not handed back");
		helper.useBlock(pipe, wrench, hit(helper, pipe, Direction.UP));
		helper.assertTrue(!bracketsOf(helper, pipe).isBracketPresent(), "the wrench left the pipe's bracket");
		helper.assertTrue(helper.getBlockState(pipe).getBlock() == PipesRegistry.FLUID_PIPE, "the pipe changed");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void bracketsOnlyFitWhereBracketsFit(GameTestHelper helper) {
		floor(helper);
		BlockPos stone = new BlockPos(2, 1, 2);
		BlockPos encased = new BlockPos(4, 1, 2);
		helper.setBlock(stone, Blocks.STONE);
		helper.setBlock(encased, ModBlocks.ANDESITE_ENCASED_SHAFT.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Axis.X));
		helper.assertTrue(bracketsOf(helper, stone) == null, "stone has a bracket behaviour");
		helper.assertTrue(bracketsOf(helper, encased) == null, "an encased shaft has a bracket behaviour");
		ItemStack brackets = new ItemStack(DoorsRegistry.WOODEN_BRACKET_ITEM, 2);
		Player player = survivalPlayer(helper, brackets);
		helper.useBlock(stone, player, hit(helper, stone, Direction.UP));
		helper.assertTrue(brackets.getCount() == 2, "a bracket was used on stone");
		// encased pipes take none either
		BlockPos encasedPipe = new BlockPos(6, 1, 2);
		helper.setBlock(encasedPipe, PipesRegistry.ENCASED_FLUID_PIPE.defaultBlockState());
		helper.useBlock(encasedPipe, player, hit(helper, encasedPipe, Direction.UP));
		helper.assertTrue(brackets.getCount() == 2, "a bracket was used on an encased pipe");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void pipesWithBracketsKeepTheirShapeAndBracketsSave(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, PipesRegistry.FLUID_PIPE.getAxisState(Axis.X));
		Player player = survivalPlayer(helper, new ItemStack(DoorsRegistry.WOODEN_BRACKET_ITEM, 4));
		helper.useBlock(pos, player, hit(helper, pos, Direction.UP));
		BlockState before = helper.getBlockState(pos);
		// a pipe next to it that would normally bend this one does not change it
		helper.setBlock(pos.north(), PipesRegistry.FLUID_PIPE.getAxisState(Axis.Z));
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(helper.getBlockState(pos) == before, "the bracketed pipe changed from " + before + " to " + helper.getBlockState(pos));
			// save and load
			BlockEntity be = helper.getBlockEntity(pos, BlockEntity.class);
			TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
			be.saveCustomOnly(out);
			CompoundTag tag = out.buildResult();
			helper.assertTrue(tag.contains("Bracket"), "bracket not saved: " + tag);
			BlockEntity copy = PipesRegistry.FLUID_PIPE_BLOCK_ENTITY.create(helper.absolutePos(pos), before);
			copy.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
			BracketedBlockEntityBehaviour loaded = BlockEntityBehaviour.get(copy, BracketedBlockEntityBehaviour.TYPE);
			helper.assertTrue(loaded != null && loaded.getBracket() == bracketsOf(helper, pos).getBracket(), "bracket not loaded");
			helper.succeed();
		});
	}

	// ---- placard ----

	private static BlockState placardOn(Direction facing) {
		return DoorsRegistry.PLACARD.defaultBlockState().setValue(PlacardBlock.FACE, AttachFace.WALL).setValue(PlacardBlock.FACING, facing);
	}

	@GameTest(maxTicks = 80)
	public void placardHoldsAnItemAndPulsesWhenAMatchingItemIsUsed(GameTestHelper helper) {
		floor(helper);
		BlockPos wall = new BlockPos(2, 1, 3);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(wall, Blocks.STONE);
		helper.setBlock(pos, placardOn(Direction.NORTH));
		helper.assertTrue(helper.getBlockState(pos).getBlock() == DoorsRegistry.PLACARD, "the placard did not stay");

		Player player = survivalPlayer(helper, new ItemStack(Items.APPLE, 3));
		helper.useBlock(pos, player, hit(helper, pos, Direction.NORTH));
		PlacardBlockEntity be = helper.getBlockEntity(pos, PlacardBlockEntity.class);
		helper.assertTrue(be.getHeldItem().is(Items.APPLE) && be.getHeldItem().getCount() == 1, "held " + be.getHeldItem());
		helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 2, "the apple was not taken");
		helper.assertTrue(!helper.getBlockState(pos).getValue(PlacardBlock.POWERED), "powered by inserting");

		// the wrong item does nothing
		Player wrong = survivalPlayer(helper, new ItemStack(Items.CARROT));
		helper.useBlock(pos, wrong, hit(helper, pos, Direction.NORTH));
		helper.assertTrue(!helper.getBlockState(pos).getValue(PlacardBlock.POWERED), "a carrot powered an apple placard");
		helper.assertTrue(wrong.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1, "the carrot was taken");

		// the right one pulses
		helper.useBlock(pos, player, hit(helper, pos, Direction.NORTH));
		helper.assertTrue(helper.getBlockState(pos).getValue(PlacardBlock.POWERED), "no pulse for the matching item");
		helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 2, "the matching item was used up");
		helper.assertTrue(helper.getBlockState(pos).getSignal(helper.getLevel(), helper.absolutePos(pos), Direction.NORTH) == 15, "no signal");
		// strong power goes into the block it is attached to
		helper.assertTrue(helper.getLevel().getDirectSignalTo(helper.absolutePos(wall)) == 15, "the attached block is not powered");

		helper.runAfterDelay(30, () -> {
			helper.assertTrue(!helper.getBlockState(pos).getValue(PlacardBlock.POWERED), "the pulse never ended");
			helper.assertTrue(helper.getLevel().getDirectSignalTo(helper.absolutePos(wall)) == 0, "the block is still powered");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void placardPulseLastsAboutOneSecond(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(new BlockPos(2, 1, 3), Blocks.STONE);
		helper.setBlock(pos, placardOn(Direction.NORTH));
		Player player = survivalPlayer(helper, new ItemStack(Items.STICK, 4));
		helper.useBlock(pos, player, hit(helper, pos, Direction.NORTH));
		helper.useBlock(pos, player, hit(helper, pos, Direction.NORTH));
		helper.assertTrue(helper.getBlockState(pos).getValue(PlacardBlock.POWERED), "no pulse");
		helper.runAfterDelay(15, () -> helper.assertTrue(helper.getBlockState(pos).getValue(PlacardBlock.POWERED), "the pulse ended early"));
		helper.runAfterDelay(25, () -> {
			helper.assertTrue(!helper.getBlockState(pos).getValue(PlacardBlock.POWERED), "the pulse lasted too long");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void placardShowsItsItemDropsItAndLeftClickTakesItBack(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(new BlockPos(2, 1, 3), Blocks.STONE);
		helper.setBlock(pos, placardOn(Direction.NORTH));
		helper.runAfterDelay(2, () -> helper.assertTrue(shownElements(helper, pos) == 1, "an empty placard shows " + shownElements(helper, pos)));
		Player player = survivalPlayer(helper, new ItemStack(Items.DIAMOND, 2));
		helper.runAfterDelay(3, () -> helper.useBlock(pos, player, hit(helper, pos, Direction.NORTH)));
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(shownElements(helper, pos) == 2, "the item is not shown: " + shownElements(helper, pos));
			// left click
			helper.getBlockState(pos).attack(helper.getLevel(), helper.absolutePos(pos), player);
			helper.assertTrue(helper.getBlockEntity(pos, PlacardBlockEntity.class).getHeldItem().isEmpty(), "the item stayed");
			helper.assertTrue(player.getInventory().contains(s -> s.is(Items.DIAMOND)), "not handed back");
			helper.useBlock(pos, player, hit(helper, pos, Direction.NORTH));
		});
		helper.runAfterDelay(14, () -> {
			helper.assertTrue(shownElements(helper, pos) == 2, "the item is not shown again");
			helper.setBlock(pos, Blocks.AIR);
			helper.assertTrue(items(helper, pos).stream().anyMatch(e -> e.getItem().is(Items.DIAMOND)), "the item did not drop with the placard");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 20)
	public void placardOnEverySideShowsAThinPlateToPlayers(GameTestHelper helper) {
		BlockState floorState = DoorsRegistry.PLACARD.defaultBlockState().setValue(PlacardBlock.FACE, AttachFace.FLOOR);
		BlockState ceilingState = DoorsRegistry.PLACARD.defaultBlockState().setValue(PlacardBlock.FACE, AttachFace.CEILING);
		BlockState floorClient = DoorsRegistry.PLACARD.getPolymerBlockState(floorState, null);
		BlockState ceilingClient = DoorsRegistry.PLACARD.getPolymerBlockState(ceilingState, null);
		helper.assertTrue(floorClient.getBlock() instanceof TrapDoorBlock && ceilingClient.getBlock() instanceof TrapDoorBlock, "no trapdoor plates: " + floorClient + " " + ceilingClient);
		helper.assertTrue(floorClient != ceilingClient, "floor and ceiling look the same");
		for (Direction d : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
			BlockState client = DoorsRegistry.PLACARD.getPolymerBlockState(placardOn(d), null);
			helper.assertTrue(client.getBlock() instanceof TrapDoorBlock && client.getValue(TrapDoorBlock.OPEN)
				&& client.getValue(TrapDoorBlock.FACING) == d, "wall placard facing " + d + " shows " + client);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void girdersConnectToPlacards(GameTestHelper helper) {
		floor(helper);
		BlockPos girder = new BlockPos(2, 1, 2);
		helper.setBlock(girder, DoorsRegistry.METAL_GIRDER.defaultBlockState());
		helper.assertTrue(!helper.getBlockState(girder).getValue(GirderBlock.X), "a pole starts as a pole");
		// a placard on the pole's east side, facing east, hangs on the girder itself
		helper.setBlock(girder.east(), DoorsRegistry.PLACARD.defaultBlockState().setValue(PlacardBlock.FACE, AttachFace.WALL).setValue(PlacardBlock.FACING, Direction.EAST));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(helper.getBlockState(girder.east()).getBlock() == DoorsRegistry.PLACARD, "the placard fell");
			helper.assertTrue(helper.getBlockState(girder).getValue(GirderBlock.X), "the girder did not connect to the placard");
			helper.succeed();
		});
	}

	// ---- contraptions, elevators and door control ----

	@GameTest(maxTicks = 20)
	public void contraptionsDrawGirdersAndDoorsFromTheirModelParts(GameTestHelper helper) {
		helper.assertTrue(DoorsRegistry.METAL_GIRDER.getContraptionPieces(DoorsRegistry.METAL_GIRDER.defaultBlockState()).size() == 1, "a pole is one piece");
		helper.assertTrue(DoorsRegistry.METAL_GIRDER.getContraptionPieces(beam(Axis.X).setValue(GirderBlock.TOP, true)).size() == 2, "a beam with a cap is two pieces");
		helper.assertTrue(DoorsRegistry.METAL_GIRDER.getContraptionPieces(beam(Axis.X).setValue(GirderBlock.Z, true).setValue(GirderBlock.TOP, true)).size() == 1, "a cross has no caps");
		BlockState encased = DoorsRegistry.METAL_GIRDER_ENCASED_SHAFT.defaultBlockState().setValue(GirderEncasedShaftBlock.BOTTOM, true);
		helper.assertTrue(DoorsRegistry.METAL_GIRDER_ENCASED_SHAFT.getContraptionPieces(encased).size() == 3, "girder, cap and shaft");
		SlidingDoorBlock door = DoorsRegistry.COPPER_DOOR;
		helper.assertTrue(door.getContraptionPieces(door.defaultBlockState()).size() == 1, "a closed door draws its half");
		helper.assertTrue(door.getContraptionPieces(door.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)).size() == 1, "a closed door draws its upper half");
		helper.assertTrue(door.getContraptionPieces(door.defaultBlockState().setValue(DoorBlock.OPEN, true)).isEmpty(), "an open door draws nothing");
		helper.succeed();
	}

	@GameTest(maxTicks = 300, padding = 24)
	public void doorsOnAContraptionAreHiddenOpenWithAClickAndComeBack(GameTestHelper helper) {
		floor(helper);
		BlockPos bearing = new BlockPos(3, 1, 3);
		helper.setBlock(bearing.below(), ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
		helper.setBlock(bearing, dev.createpolymer.registry.feature.BearingRegistry.MECHANICAL_BEARING.defaultBlockState()
			.setValue(dev.createpolymer.contraptions.bearing.BearingBlock.FACING, Direction.UP));
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.STONE);
		// two doors side by side form a double door
		door(helper, new BlockPos(4, 2, 3), DoorsRegistry.TRAIN_DOOR, Direction.NORTH, DoorHingeSide.LEFT);
		door(helper, new BlockPos(5, 2, 3), DoorsRegistry.TRAIN_DOOR, Direction.NORTH, DoorHingeSide.RIGHT);
		helper.getLevel().addFreshEntity(new SuperGlueEntity(helper.getLevel(),
			SuperGlueEntity.span(helper.absolutePos(new BlockPos(3, 2, 3)), helper.absolutePos(new BlockPos(5, 3, 3)))));
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		boolean[] clicked = { false };
		AABB bounds = helper.getBounds().inflate(1);
		helper.runAfterDelay(2, () -> helper.getBlockEntity(bearing.below(), CreativeMotorBlockEntity.class).setSetting(32));
		helper.succeedWhen(() -> {
			List<ControlledContraptionEntity> entities = helper.getLevel().getEntitiesOfClass(ControlledContraptionEntity.class, bounds.inflate(16), e -> bounds.contains(e.position()));
			if (!clicked[0]) {
				helper.assertTrue(entities.size() == 1, "waiting for the contraption");
				ControlledContraptionEntity entity = entities.get(0);
				// the doors' movement behaviours settle on their first tick; a click before it would be undone
				helper.assertTrue(entity.tickCount > 10, "waiting for the doors to settle");
				var infos = entity.getContraption().getBlocks().values().stream().filter(i -> i.state().getBlock() instanceof SlidingDoorBlock).toList();
				helper.assertTrue(infos.size() == 4, "doors on the contraption: " + infos.size());
				for (var info : infos)
					helper.assertTrue(!info.state().getValue(SlidingDoorBlock.VISIBLE), "a door on a contraption is visible");
				// click the first door: the double door and both halves open
				BlockPos lower = infos.stream().filter(i -> i.state().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER && i.state().getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT)
					.findFirst().orElseThrow().pos();
				helper.assertTrue(new DoorMovingInteraction().handlePlayerInteraction(player, InteractionHand.MAIN_HAND, lower, entity), "no interaction");
				long opened = entity.getContraption().getBlocks().values().stream()
					.filter(i -> i.state().getBlock() instanceof SlidingDoorBlock && i.state().getValue(DoorBlock.OPEN)).count();
				helper.assertTrue(opened == 4, "doors opened by the click: " + opened);
				clicked[0] = true;
				// pieces on the contraption: an open door draws nothing
				helper.fail("clicked, turning");
			}
			// the contraption keeps turning for a while: stop it, the doors come back to the world visible
			if (!entities.isEmpty() && Math.abs(entities.get(0).getAngle(1)) >= 90) {
				helper.getBlockEntity(bearing.below(), CreativeMotorBlockEntity.class).setSetting(0);
				helper.fail("stopping");
			}
			helper.assertTrue(entities.isEmpty(), "contraption still exists");
			int found = 0;
			for (int x = 1; x <= 6; x++)
				for (int z = 1; z <= 6; z++)
					for (int y = 2; y <= 3; y++) {
						BlockState state = helper.getBlockState(new BlockPos(x, y, z));
						if (state.getBlock() instanceof SlidingDoorBlock) {
							found++;
							helper.assertTrue(state.getValue(DoorBlock.OPEN), "the door is closed again at " + x + "," + y + "," + z + ": " + state);
							helper.assertTrue(!state.getValue(SlidingDoorBlock.VISIBLE), "an open door is visible");
						}
					}
			helper.assertTrue(found == 4, "doors back in the world: " + found);
		});
	}

	@GameTest(maxTicks = 500, padding = 8)
	public void anElevatorOpensItsDoorWhenItHasArrived(GameTestHelper helper) {
		floor(helper);
		BlockPos pulley = new BlockPos(2, 7, 3);
		// the cabin: stone, a contact and a door beside them
		helper.setBlock(new BlockPos(2, 3, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(2, 2, 3), RedstoneRegistry.REDSTONE_CONTACT.defaultBlockState().setValue(RedstoneContactBlock.FACING, Direction.SOUTH));
		door(helper, new BlockPos(3, 2, 3), DoorsRegistry.TRAIN_DOOR, Direction.EAST, DoorHingeSide.LEFT);
		helper.getLevel().addFreshEntity(new SuperGlueEntity(helper.getLevel(),
			SuperGlueEntity.span(helper.absolutePos(new BlockPos(2, 2, 3)), helper.absolutePos(new BlockPos(3, 3, 3)))));
		helper.setBlock(new BlockPos(2, 2, 4), RedstoneRegistry.REDSTONE_CONTACT.defaultBlockState().setValue(RedstoneContactBlock.FACING, Direction.NORTH));
		helper.setBlock(new BlockPos(2, 5, 4), RedstoneRegistry.REDSTONE_CONTACT.defaultBlockState().setValue(RedstoneContactBlock.FACING, Direction.NORTH));
		helper.setBlock(pulley, PulleyRegistry.ELEVATOR_PULLEY.defaultBlockState().setValue(HorizontalKineticBlock.HORIZONTAL_FACING, Direction.EAST));
		helper.setBlock(pulley.north(), ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.SOUTH));
		helper.runAfterDelay(2, () -> helper.getBlockEntity(pulley.north(), CreativeMotorBlockEntity.class).setSetting(64));
		helper.runAfterDelay(8, () -> helper.getBlockEntity(pulley, ElevatorPulleyBlockEntity.class).clicked());
		helper.runAfterDelay(30, () -> helper.setBlock(new BlockPos(1, 5, 4), Blocks.REDSTONE_BLOCK));

		long start = helper.getLevel().getGameTime();
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getLevel().getGameTime() - start > 40, "waiting");
			List<ControlledContraptionEntity> list = helper.getLevel().getEntitiesOfClass(ControlledContraptionEntity.class, helper.getBounds().inflate(8));
			helper.assertTrue(list.size() == 1 && list.get(0).getContraption() instanceof ElevatorContraption, "no elevator " + list);
			ControlledContraptionEntity entity = list.get(0);
			BlockPos doorPos = cabinOf(entity);
			double y = entity.getY() - helper.absolutePos(BlockPos.ZERO).getY();
			helper.assertTrue(Math.abs(y - 6) < 0.05, "still travelling at y=" + y);
			helper.assertTrue(entity.getContraption().getBlocks().get(doorPos).state().getValue(DoorBlock.OPEN), "the door stayed closed on arrival");
		});
	}

	@GameTest(maxTicks = 40)
	public void doorControlModesSelectSidesAndSave(GameTestHelper helper) {
		helper.assertTrue(DoorControl.ALL.matches(Direction.WEST) && !DoorControl.NONE.matches(Direction.WEST), "all/none");
		helper.assertTrue(DoorControl.EAST.matches(Direction.EAST) && !DoorControl.EAST.matches(Direction.WEST), "east");
		helper.assertTrue(DoorControl.facing(Direction.UP) == DoorControl.NONE && DoorControl.facing(Direction.SOUTH) == DoorControl.SOUTH, "facing");
		helper.assertTrue(DoorControl.NONE.next() == DoorControl.ALL, "cycle");

		floor(helper);
		BlockPos pos = new BlockPos(2, 2, 4);
		helper.setBlock(pos, PulleyRegistry.ELEVATOR_CONTACT.defaultBlockState().setValue(ElevatorContactBlock.FACING, Direction.NORTH));
		helper.runAfterDelay(4, () -> {
			ElevatorContactBlockEntity be = helper.getBlockEntity(pos, ElevatorContactBlockEntity.class);
			helper.assertTrue(be.doorControls != null && be.doorControls.mode == DoorControl.ALL, "default mode");
			be.doorControls.set(DoorControl.WEST);
			TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
			be.saveCustomOnly(out);
			ElevatorContactBlockEntity copy = new ElevatorContactBlockEntity(PulleyRegistry.ELEVATOR_CONTACT_BLOCK_ENTITY, helper.absolutePos(pos), helper.getBlockState(pos));
			copy.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), out.buildResult()));
			helper.assertTrue(copy.doorControls.mode == DoorControl.WEST, "loaded mode " + copy.doorControls.mode);
			helper.assertTrue(BlockEntityBehaviour.get(be, DoorControlBehaviour.TYPE) == be.doorControls, "behaviour lookup");
			helper.succeed();
		});
	}
}
