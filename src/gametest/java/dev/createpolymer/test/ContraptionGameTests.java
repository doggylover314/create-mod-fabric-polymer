package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import java.util.List;

import dev.createpolymer.api.contraption.BlockMovementChecks;
import dev.createpolymer.contraptions.AssemblyException;
import dev.createpolymer.contraptions.Contraption;
import dev.createpolymer.contraptions.ControlledContraptionEntity;
import dev.createpolymer.contraptions.StructureTransform;
import dev.createpolymer.contraptions.glue.SuperGlueEntity;
import dev.createpolymer.contraptions.glue.SuperGlueSelectionHelper;
import dev.createpolymer.contraptions.piston.MechanicalPistonBlock;
import dev.createpolymer.contraptions.piston.MechanicalPistonBlock.PistonState;
import dev.createpolymer.contraptions.piston.MechanicalPistonBlockEntity;
import dev.createpolymer.contraptions.piston.PistonContraption;
import dev.createpolymer.kinetics.base.DirectionalAxisKineticBlock;
import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.motor.CreativeMotorBlockEntity;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.feature.ContraptionRegistry;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;

public class ContraptionGameTests {

	private static final BlockPos PISTON = new BlockPos(2, 3, 2);

	/**
	 * A sticky piston facing up at y=3 with two poles under it (y=1, y=2) and a stone block with a chest on top
	 * (y=4, y=5). Poles behind the piston are what lets it extend, by two blocks.
	 */
	private static void pistonSetup(GameTestHelper helper, boolean sticky) {
		floor(helper);
		helper.setBlock(new BlockPos(2, 1, 2), ContraptionRegistry.PISTON_EXTENSION_POLE.defaultBlockState()
			.setValue(MechanicalPistonBlock.FACING, Direction.UP));
		helper.setBlock(new BlockPos(2, 2, 2), ContraptionRegistry.PISTON_EXTENSION_POLE.defaultBlockState()
			.setValue(MechanicalPistonBlock.FACING, Direction.UP));
		helper.setBlock(PISTON, (sticky ? ContraptionRegistry.STICKY_MECHANICAL_PISTON : ContraptionRegistry.MECHANICAL_PISTON)
			.defaultBlockState()
			.setValue(MechanicalPistonBlock.FACING, Direction.UP)
			.setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, false));
		helper.setBlock(new BlockPos(2, 4, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(2, 5, 2), Blocks.CHEST);
		helper.getBlockEntity(new BlockPos(2, 5, 2), ChestBlockEntity.class).setItem(0, new ItemStack(Items.DIAMOND, 5));
		helper.getBlockEntity(new BlockPos(2, 5, 2), ChestBlockEntity.class).setItem(26, new ItemStack(Items.EMERALD, 2));
	}

	/** Super glue around the stone and the chest, so a sticky piston brings both back. */
	private static void glueStructure(GameTestHelper helper) {
		BlockPos a = helper.absolutePos(new BlockPos(2, 4, 2));
		BlockPos b = helper.absolutePos(new BlockPos(2, 5, 2));
		helper.getLevel().addFreshEntity(new SuperGlueEntity(helper.getLevel(), SuperGlueEntity.span(a, b)));
	}

	/** The piston's rotation axis is Z; extending needs a negative speed with the motor north of the piston. */
	private static void power(GameTestHelper helper, int rpm) {
		BlockPos motor = PISTON.north();
		helper.setBlock(motor, ModBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.SOUTH));
		helper.runAfterDelay(2, () -> helper.getBlockEntity(motor, CreativeMotorBlockEntity.class).setSetting(rpm));
	}

	private static void setSpeed(GameTestHelper helper, int rpm) {
		helper.getBlockEntity(PISTON.north(), CreativeMotorBlockEntity.class).setSetting(rpm);
	}

	private static String column(GameTestHelper helper) {
		StringBuilder sb = new StringBuilder();
		for (int y = 1; y <= 7; y++)
			sb.append(y).append('=').append(helper.getBlockState(new BlockPos(2, y, 2)).getBlock().getDescriptionId()).append(' ');
		return sb.append(contraptions(helper).size()).append(" contraptions").toString();
	}

	private static List<ControlledContraptionEntity> contraptions(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ControlledContraptionEntity.class,
			new AABB(helper.absolutePos(new BlockPos(0, 0, 0))).inflate(16));
	}

	@GameTest(maxTicks = 300)
	public void pistonPushesBlocksTwoAndBackWithBlockEntityData(GameTestHelper helper) {
		pistonSetup(helper, true);
		glueStructure(helper);
		power(helper, -128);
		// extended: the structure sits two blocks higher, the chest kept its items
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 6, 2)).getBlock() == Blocks.STONE, "stone is not at y=6");
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 7, 2)).getBlock() == Blocks.CHEST, "chest is not at y=7");
			ChestBlockEntity chest = helper.getBlockEntity(new BlockPos(2, 7, 2), ChestBlockEntity.class);
			helper.assertTrue(chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount() == 5, "chest lost its diamonds: " + chest.getItem(0));
			helper.assertTrue(chest.getItem(26).is(Items.EMERALD) && chest.getItem(26).getCount() == 2, "chest lost its emeralds");
			helper.assertTrue(helper.getBlockState(PISTON).getValue(MechanicalPistonBlock.STATE) == PistonState.EXTENDED, "piston is not extended");
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 5, 2)).getBlock() == ContraptionRegistry.MECHANICAL_PISTON_HEAD, "no piston head at y=5");
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 4, 2)).getBlock() == ContraptionRegistry.PISTON_EXTENSION_POLE, "no pole at y=4");
			helper.assertTrue(contraptions(helper).isEmpty(), "contraption entity still exists");
		});
	}

	@GameTest(maxTicks = 500)
	public void pistonReturnsTheStructureWhenReversed(GameTestHelper helper) {
		pistonSetup(helper, true);
		glueStructure(helper);
		power(helper, -128);
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 6, 2)).getBlock() == Blocks.STONE, "stone did not extend to y=6");
			setSpeed(helper, 128);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 4, 2)).getBlock() == Blocks.STONE, "stone is not back at y=4");
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 5, 2)).getBlock() == Blocks.CHEST, "chest is not back at y=5, column: " + column(helper));
			ChestBlockEntity chest = helper.getBlockEntity(new BlockPos(2, 5, 2), ChestBlockEntity.class);
			helper.assertTrue(chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount() == 5, "chest lost its diamonds: " + chest.getItem(0));
			helper.assertTrue(helper.getBlockState(PISTON).getValue(MechanicalPistonBlock.STATE) == PistonState.RETRACTED, "piston did not retract");
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 6, 2)).isAir(), "left a block behind at y=6");
			helper.assertTrue(contraptions(helper).isEmpty(), "contraption entity still exists");
		});
	}

	@GameTest(maxTicks = 100)
	public void movingContraptionHasEntityVisualAndSaves(GameTestHelper helper) {
		pistonSetup(helper, true);
		power(helper, -32);
		helper.runAfterDelay(20, () -> {
			List<ControlledContraptionEntity> list = contraptions(helper);
			helper.assertTrue(list.size() == 1, "expected one contraption entity, found " + list.size());
			ControlledContraptionEntity entity = list.get(0);
			Contraption contraption = entity.getContraption();
			// head + 2 poles + stone + chest
			helper.assertTrue(contraption.getBlocks().size() == 5, "contraption has " + contraption.getBlocks().size() + " blocks");
			helper.assertTrue(helper.getBlockState(PISTON).getValue(MechanicalPistonBlock.STATE) == PistonState.MOVING, "piston is not in its moving state");

			// display entities: one per block, and hitboxes for the stone
			helper.assertTrue(entity.getVisual() != null, "no visual");
			helper.assertTrue(entity.getVisual().getPieceCount() >= 5, "pieces " + entity.getVisual().getPieceCount());
			helper.assertTrue(entity.getVisual().getProxyCount() >= 1, "no collision proxy");

			// save and load
			TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
			entity.saveWithoutId(out);
			CompoundTag tag = out.buildResult();
			ControlledContraptionEntity copy = new ControlledContraptionEntity(ContraptionRegistry.CONTROLLED_CONTRAPTION, helper.getLevel());
			copy.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
			Contraption loaded = copy.getContraption();
			helper.assertTrue(loaded != null && loaded.getBlocks().size() == 5, "loaded contraption: " + (loaded == null ? null : loaded.getBlocks().size()));
			helper.assertTrue(loaded.getBlocks().get(new BlockPos(0, 0, 0)).state().is(Blocks.STONE), "stone is not at the anchor after loading");
			helper.assertTrue(loaded.getBlocks().get(new BlockPos(0, 1, 0)).state().is(Blocks.CHEST), "chest is not above the anchor after loading");
			CompoundTag chestData = loaded.getBlocks().get(new BlockPos(0, 1, 0)).nbt();
			helper.assertTrue(chestData != null && chestData.toString().contains("diamond"), "chest data lost: " + chestData);
			helper.assertTrue(loaded.getStorage().getAllItemStorages().size() == 1, "chest storage was not mounted");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void itemOnTopIsCarriedUp(GameTestHelper helper) {
		pistonSetup(helper, false);
		helper.setBlock(new BlockPos(2, 5, 2), Blocks.STONE);
		power(helper, -64);
		// the stone top is at y=5; an item resting there rides with the contraption
		helper.runAfterDelay(1, () -> helper.spawnItem(Items.ECHO_SHARD, 2.5f, 6.1f, 2.5f).setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO));
		helper.succeedWhen(() -> {
			List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(new BlockPos(2, 0, 2))).inflate(2, 10, 2),
				e -> e.getItem().is(Items.ECHO_SHARD));
			helper.assertTrue(items.size() == 1, "item entities: " + items.size());
			double y = items.get(0).getY() - helper.absolutePos(BlockPos.ZERO).getY();
			helper.assertTrue(y > 6.9, "item is at y=" + y + ", it was not carried up two blocks");
		});
	}

	@GameTest(maxTicks = 40)
	public void bedrockAndObsidianCannotBeMoved(GameTestHelper helper) {
		pistonSetup(helper, false);
		helper.setBlock(new BlockPos(2, 5, 2), Blocks.BEDROCK);
		helper.runAfterDelay(2, () -> {
			try {
				new PistonContraption(Direction.UP, false).assemble(helper.getLevel(), helper.absolutePos(PISTON));
				helper.fail("a piston assembled around bedrock");
			} catch (AssemblyException e) {
				helper.assertTrue(e.hasPosition() && e.getPosition().equals(helper.absolutePos(new BlockPos(2, 5, 2))),
					"exception points at " + e.getPosition());
			}
			helper.setBlock(new BlockPos(2, 5, 2), Blocks.OBSIDIAN);
			helper.assertTrue(!BlockMovementChecks.isMovementAllowed(Blocks.OBSIDIAN.defaultBlockState(), helper.getLevel(), helper.absolutePos(new BlockPos(2, 5, 2))),
				"obsidian is movable");
			try {
				new PistonContraption(Direction.UP, false).assemble(helper.getLevel(), helper.absolutePos(PISTON));
				helper.fail("a piston assembled around obsidian");
			} catch (AssemblyException e) {
				// expected
			}
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void pistonShowsAssemblyExceptionAndKeepsStillWhenBlocked(GameTestHelper helper) {
		pistonSetup(helper, false);
		helper.setBlock(new BlockPos(2, 5, 2), Blocks.BEDROCK);
		power(helper, -128);
		helper.succeedWhen(() -> {
			MechanicalPistonBlockEntity piston = helper.getBlockEntity(PISTON, MechanicalPistonBlockEntity.class);
			helper.assertTrue(piston.getLastAssemblyException() != null, "no assembly exception");
			helper.assertTrue(!piston.running, "piston ran");
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 4, 2)).is(Blocks.STONE), "stone moved");
		});
	}

	@GameTest(maxTicks = 40)
	public void pistonWithoutPolesReportsMissingPoles(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(PISTON, ContraptionRegistry.MECHANICAL_PISTON.defaultBlockState().setValue(MechanicalPistonBlock.FACING, Direction.UP));
		helper.runAfterDelay(2, () -> {
			try {
				new PistonContraption(Direction.UP, false).assemble(helper.getLevel(), helper.absolutePos(PISTON));
				helper.fail("assembled without poles");
			} catch (AssemblyException e) {
				helper.succeed();
			}
		});
	}

	@GameTest(maxTicks = 40)
	public void glueMovesBlocksThatDoNotStick(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.SAND);
		helper.setBlock(new BlockPos(2, 1, 3), Blocks.DIRT);
		helper.runAfterDelay(1, () -> {
			var level = helper.getLevel();
			BlockPos a = helper.absolutePos(new BlockPos(2, 1, 2));
			BlockPos b = helper.absolutePos(new BlockPos(2, 1, 3));
			var group = SuperGlueSelectionHelper.searchGlueGroup(level, a, b, false);
			helper.assertTrue(group != null && group.contains(b), "no glue group between two adjacent blocks");
			SuperGlueEntity glue = new SuperGlueEntity(level, SuperGlueEntity.span(a, b));
			level.addFreshEntity(glue);
			helper.assertTrue(SuperGlueEntity.isGlued(level, a, Direction.SOUTH, null), "blocks are not glued");
			helper.assertTrue(!SuperGlueEntity.isGlued(level, a, Direction.NORTH, null), "glued towards outside the box");
			// the entity saves its box
			TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
			glue.saveWithoutId(out);
			SuperGlueEntity copy = new SuperGlueEntity(ContraptionRegistry.SUPER_GLUE, level);
			copy.setPos(glue.position());
			copy.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), out.buildResult()));
			helper.assertTrue(copy.getBoundingBox().equals(glue.getBoundingBox()), "box " + copy.getBoundingBox() + " vs " + glue.getBoundingBox());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void structureTransformRotatesStatesAndPositions(GameTestHelper helper) {
		StructureTransform transform = new StructureTransform(BlockPos.ZERO, 0, 90, 0);
		helper.assertTrue(transform.rotation == Rotation.COUNTERCLOCKWISE_90, "rotation " + transform.rotation);
		BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
		BlockState rotated = transform.apply(stairs);
		helper.assertTrue(rotated.getValue(StairBlock.FACING) == Direction.WEST, "stairs face " + rotated.getValue(StairBlock.FACING));
		BlockPos moved = transform.apply(new BlockPos(1, 0, 0));
		helper.assertTrue(moved.equals(new BlockPos(0, 0, -1)), "position " + moved);
		// around a horizontal axis a facing block turns over
		StructureTransform flip = new StructureTransform(BlockPos.ZERO, 0, 0, 180);
		BlockState up = Blocks.PISTON.defaultBlockState().setValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING, Direction.UP);
		helper.assertTrue(flip.apply(up).getValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING) == Direction.DOWN, "flipped piston");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void storageIsMountedForChestsBarrelsAndVaults(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.BARREL);
		helper.setBlock(new BlockPos(2, 1, 1), Blocks.CHEST);
		helper.setBlock(new BlockPos(3, 1, 1), Blocks.FURNACE);
		helper.runAfterDelay(2, () -> {
			var type = dev.createpolymer.api.contraption.storage.item.MountedItemStorageType.REGISTRY;
			helper.assertTrue(type.get(Blocks.BARREL) == ContraptionRegistry.SIMPLE_MOUNTED_STORAGE, "barrel type");
			helper.assertTrue(type.get(Blocks.CHEST) == ContraptionRegistry.CHEST_MOUNTED_STORAGE, "chest type");
			helper.assertTrue(type.get(ModBlocks.DEPOT) == ContraptionRegistry.DEPOT_MOUNTED_STORAGE, "depot type");
			var barrel = ContraptionRegistry.SIMPLE_MOUNTED_STORAGE.mount(helper.getLevel(), Blocks.BARREL.defaultBlockState(),
				helper.absolutePos(new BlockPos(1, 1, 1)), helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(1, 1, 1))));
			helper.assertTrue(barrel != null && barrel.getSlots() == 27, "barrel storage " + barrel);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void playersStandingOnContraptionsAreCarriedNotKicked(GameTestHelper helper) {
		// the support code needs a connected player; this checks the pure part: a contraption's contact motion
		pistonSetup(helper, false);
		power(helper, -64);
		helper.runAfterDelay(10, () -> {
			ControlledContraptionEntity entity = contraptions(helper).get(0);
			var motion = entity.getContactPointMotion(entity.position().add(0, 2, 0));
			helper.assertTrue(motion.y > 0.05 && motion.y < 0.5, "contact point motion " + motion);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 20)
	public void rotationStateMatchesTheEntitiesRotation(GameTestHelper helper) {
		var level = helper.getLevel();
		net.minecraft.world.phys.Vec3[] probes = {new net.minecraft.world.phys.Vec3(1, 0, 0), new net.minecraft.world.phys.Vec3(0, 1, 0),
			new net.minecraft.world.phys.Vec3(0, 0, 1), new net.minecraft.world.phys.Vec3(1, 2, 3)};
		for (net.minecraft.core.Direction.Axis axis : net.minecraft.core.Direction.Axis.values()) {
			ControlledContraptionEntity controlled = new ControlledContraptionEntity(ContraptionRegistry.CONTROLLED_CONTRAPTION, level);
			controlled.setRotationAxis(axis);
			controlled.setAngle(90);
			for (var v : probes)
				helper.assertTrue(controlled.applyRotation(v, 1).distanceTo(controlled.getRotationState().applyTo(v)) < 1e-4,
					"controlled " + axis + " rotates " + v + " to " + controlled.applyRotation(v, 1) + " but the state says " + controlled.getRotationState().applyTo(v));
		}
		dev.createpolymer.contraptions.OrientedContraptionEntity oriented =
			new dev.createpolymer.contraptions.OrientedContraptionEntity(ContraptionRegistry.ORIENTED_CONTRAPTION, level);
		oriented.setInitialOrientation(Direction.EAST);
		oriented.yaw = 30;
		oriented.pitch = 20;
		for (var v : probes)
			helper.assertTrue(oriented.applyRotation(v, 1).distanceTo(oriented.getRotationState().applyTo(v)) < 1e-3,
				"oriented rotates " + v + " to " + oriented.applyRotation(v, 1) + " but the state says " + oriented.getRotationState().applyTo(v));
		helper.succeed();
	}
}
