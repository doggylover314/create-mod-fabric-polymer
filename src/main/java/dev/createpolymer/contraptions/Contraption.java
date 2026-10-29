package dev.createpolymer.contraptions;

import static dev.createpolymer.contraptions.piston.MechanicalPistonBlock.isExtensionPole;
import static dev.createpolymer.contraptions.piston.MechanicalPistonBlock.isPistonHead;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import dev.createpolymer.api.behaviour.interaction.MovingInteractionBehaviour;
import dev.createpolymer.api.behaviour.movement.MovementBehaviour;
import dev.createpolymer.api.contraption.BlockMovementChecks;
import dev.createpolymer.api.contraption.ContraptionType;
import dev.createpolymer.contraptions.actors.contraptionControls.ContraptionControlsMovement;
import dev.createpolymer.contraptions.actors.seat.SeatBlock;
import dev.createpolymer.contraptions.actors.seat.SeatEntity;
import dev.createpolymer.contraptions.behaviour.MovementContext;
import dev.createpolymer.contraptions.glue.SuperGlueEntity;
import dev.createpolymer.contraptions.piston.MechanicalPistonBlock;
import dev.createpolymer.contraptions.piston.MechanicalPistonBlock.PistonState;
import dev.createpolymer.contraptions.piston.MechanicalPistonHeadBlock;
import dev.createpolymer.contraptions.piston.PistonExtensionPoleBlock;
import dev.createpolymer.foundation.blockEntity.IMultiBlockEntityContainer;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.logistics.crate.CreativeCrateBlockEntity;
import dev.createpolymer.redstone.contact.RedstoneContactBlock;
import dev.createpolymer.registry.feature.RedstoneRegistry;
import dev.createpolymer.kinetics.base.BlockBreakingMovementBehaviour;
import dev.createpolymer.contraptions.actors.harvester.HarvesterMovementBehaviour;
import dev.createpolymer.foundation.collision.CollisionList;
import dev.createpolymer.foundation.collision.CollisionList.Populate;
import dev.createpolymer.impl.contraption.ContraptionBlockTags;
import dev.createpolymer.registry.feature.ContraptionRegistry;
import dev.createpolymer.registry.feature.MinecartRegistry;
import dev.createpolymer.util.BlockFace;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.NBTHelper;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ported from Create's Contraption (MIT, The Create Team). Branches for blocks that are not ported yet (chassis,
 * belts, bearings, gantries, pulleys, stickers, bogeys, cart assemblers, sliding doors, ...) are marked in
 * {@link #moveBlock}, {@link #capture}, {@link #addBlock} and {@link #addBlocksToWorld}; whoever ports the block adds
 * the branch back next to the marker.
 * <p>
 * There is no client side: the client-only ClientContraption is replaced by a version counter that the display
 * entities of {@code dev.createpolymer.contraptions.render} watch.
 */
public abstract class Contraption {

	public final CollisionList simplifiedEntityColliders = new CollisionList();
	public AbstractContraptionEntity entity;

	public AABB bounds;
	public BlockPos anchor;
	public boolean stalled;
	public boolean hasUniversalCreativeCrate;
	public boolean disassembled;

	protected Map<BlockPos, StructureBlockInfo> blocks;
	protected List<MutablePair<StructureBlockInfo, MovementContext>> actors;
	protected Map<BlockPos, MovingInteractionBehaviour> interactors;
	protected List<ItemStack> disabledActors;

	protected List<AABB> superglue;
	protected List<BlockPos> seats;
	protected Map<UUID, Integer> seatMapping;
	protected Map<UUID, BlockFace> stabilizedSubContraptions;
	protected MountedStorageManager storage;
	protected Multimap<BlockPos, StructureBlockInfo> capturedMultiblocks;

	private Set<SuperGlueEntity> glueToRemove;
	private Map<BlockPos, Entity> initialPassengers;
	protected List<BlockFace> pendingSubContraptions;

	/** Raised whenever the shown blocks change; the display entities rebuild when it moves. */
	private int structureVersion;

	// Thin level used for generating optimized collision shapes.
	protected ContraptionWorld collisionLevel;

	public Contraption() {
		blocks = new HashMap<>();
		seats = new ArrayList<>();
		actors = new ArrayList<>();
		disabledActors = new ArrayList<>();
		interactors = new HashMap<>();
		superglue = new ArrayList<>();
		seatMapping = new HashMap<>();
		glueToRemove = new HashSet<>();
		initialPassengers = new HashMap<>();
		pendingSubContraptions = new ArrayList<>();
		stabilizedSubContraptions = new HashMap<>();
		storage = new MountedStorageManager();
		capturedMultiblocks = ArrayListMultimap.create();
	}

	public ContraptionWorld getContraptionWorld() {
		if (collisionLevel == null)
			collisionLevel = new ContraptionWorld(entity.level(), this);
		return collisionLevel;
	}

	public abstract boolean assemble(Level world, BlockPos pos) throws AssemblyException;

	public abstract boolean canBeStabilized(Direction facing, BlockPos localPos);

	public abstract ContraptionType getType();

	protected boolean customBlockPlacement(LevelAccessor world, BlockPos pos, BlockState state) {
		return false;
	}

	protected boolean customBlockRemoval(LevelAccessor world, BlockPos pos, BlockState state) {
		return false;
	}

	protected boolean addToInitialFrontier(Level world, BlockPos pos, Direction forcedDirection,
										   Queue<BlockPos> frontier) throws AssemblyException {
		return true;
	}

	public static Contraption fromNBT(Level world, CompoundTag nbt, boolean spawnData) {
		String type = nbt.getStringOr("Type", "");
		Contraption contraption = ContraptionType.fromType(type);
		if (contraption == null)
			throw new IllegalStateException("Unknown contraption type " + type);
		contraption.readNBT(world, nbt, spawnData);
		contraption.collisionLevel = new ContraptionWorld(world, contraption);
		contraption.invalidateColliders();
		return contraption;
	}

	public boolean searchMovedStructure(Level world, BlockPos pos, @Nullable Direction forcedDirection)
		throws AssemblyException {
		initialPassengers.clear();
		Queue<BlockPos> frontier = new UniqueLinkedList<>();
		Set<BlockPos> visited = new HashSet<>();
		anchor = pos;

		if (bounds == null)
			bounds = new AABB(BlockPos.ZERO);

		if (!BlockMovementChecks.isBrittle(world.getBlockState(pos)))
			frontier.add(pos);
		if (!addToInitialFrontier(world, pos, forcedDirection, frontier))
			return false;
		for (int limit = 100000; limit > 0; limit--) {
			if (frontier.isEmpty())
				return true;
			if (!moveBlock(world, forcedDirection, frontier, visited))
				return false;
		}
		throw AssemblyException.structureTooLarge();
	}

	public void onEntityCreated(AbstractContraptionEntity entity) {
		this.entity = entity;

		// Create subcontraptions (stabilized bearings)
		createSubContraptions(entity);

		storage.initialize();
		invalidateColliders();
	}

	/**
	 * Spawns one StabilizedContraption per entry of {@link #pendingSubContraptions} (the stabilized bearings found while
	 * collecting blocks), removes its blocks from the world, adds an OrientedContraptionEntity and records it in
	 * {@link #stabilizedSubContraptions}.
	 */
	protected void createSubContraptions(AbstractContraptionEntity entity) {
		for (BlockFace blockFace : pendingSubContraptions) {
			Direction face = blockFace.getFace();
			dev.createpolymer.contraptions.bearing.StabilizedContraption subContraption =
				new dev.createpolymer.contraptions.bearing.StabilizedContraption(face);
			Level world = entity.level();
			BlockPos pos = blockFace.getPos();
			try {
				if (!subContraption.assemble(world, pos))
					continue;
			} catch (AssemblyException e) {
				continue;
			}
			subContraption.removeBlocksFromWorld(world, BlockPos.ZERO);
			OrientedContraptionEntity movedContraption = OrientedContraptionEntity.create(world, subContraption, face);
			BlockPos anchor = blockFace.getConnectedPos();
			movedContraption.setPos(anchor.getX() + .5f, anchor.getY(), anchor.getZ() + .5f);
			world.addFreshEntity(movedContraption);
			stabilizedSubContraptions.put(movedContraption.getUUID(), new BlockFace(toLocalPos(pos), face));
		}
	}

	private void moveWindmillBearing(BlockPos pos, Queue<BlockPos> frontier, Set<BlockPos> visited, BlockState state) {
		Direction facing = state.getValue(dev.createpolymer.contraptions.bearing.WindmillBearingBlock.FACING);
		BlockPos offset = pos.relative(facing);
		if (!visited.contains(offset))
			frontier.add(offset);
	}

	private void moveBearing(BlockPos pos, Queue<BlockPos> frontier, Set<BlockPos> visited, BlockState state) {
		Direction facing = state.getValue(dev.createpolymer.contraptions.bearing.MechanicalBearingBlock.FACING);
		if (!canBeStabilized(facing, pos.subtract(anchor))) {
			BlockPos offset = pos.relative(facing);
			if (!visited.contains(offset))
				frontier.add(offset);
			return;
		}
		pendingSubContraptions.add(new BlockFace(pos, facing));
	}

	private boolean moveChassis(Level world, BlockPos pos, Direction movementDirection, Queue<BlockPos> frontier,
		Set<BlockPos> visited) {
		BlockEntity be = world.getBlockEntity(pos);
		if (!(be instanceof dev.createpolymer.contraptions.chassis.ChassisBlockEntity chassis))
			return false;
		chassis.addAttachedChasses(frontier, visited);
		List<BlockPos> includedBlockPositions = chassis.getIncludedBlockPositions(movementDirection, false);
		if (includedBlockPositions == null)
			return false;
		for (BlockPos blockPos : includedBlockPositions)
			if (!visited.contains(blockPos))
				frontier.add(blockPos);
		return true;
	}

	public void onEntityInitialize(Level world, AbstractContraptionEntity contraptionEntity) {
		if (world.isClientSide())
			return;

		for (OrientedContraptionEntity orientedCE : world.getEntitiesOfClass(OrientedContraptionEntity.class,
			contraptionEntity.getBoundingBox()
				.inflate(1)))
			if (stabilizedSubContraptions.containsKey(orientedCE.getUUID()))
				orientedCE.startRiding(contraptionEntity);

		for (BlockPos seatPos : getSeats()) {
			Entity passenger = initialPassengers.get(seatPos);
			if (passenger == null)
				continue;
			int seatIndex = getSeats().indexOf(seatPos);
			if (seatIndex == -1)
				continue;
			contraptionEntity.addSittingPassenger(passenger, seatIndex);
		}
	}

	/**
	 * move the first block in frontier queue
	 */
	protected boolean moveBlock(Level world, @Nullable Direction forcedDirection, Queue<BlockPos> frontier,
								Set<BlockPos> visited) throws AssemblyException {
		BlockPos pos = frontier.poll();
		if (pos == null)
			return false;
		visited.add(pos);

		if (world.isOutsideBuildHeight(pos))
			return true;
		if (!world.isLoaded(pos))
			throw AssemblyException.unloadedChunk(pos);
		if (isAnchoringBlockAt(pos))
			return true;
		BlockState state = world.getBlockState(pos);
		if (!BlockMovementChecks.isMovementNecessary(state, world, pos))
			return true;
		if (!movementAllowed(state, world, pos))
			throw AssemblyException.unmovableBlock(pos, state);

		if (state.getBlock() instanceof dev.createpolymer.contraptions.chassis.AbstractChassisBlock
			&& !moveChassis(world, pos, forcedDirection, frontier, visited))
			return false;

		// belts: moveBelt(pos, frontier, visited, state);

		if (state.getBlock() instanceof dev.createpolymer.contraptions.bearing.WindmillBearingBlock
			&& world.getBlockEntity(pos) instanceof dev.createpolymer.contraptions.bearing.WindmillBearingBlockEntity wbbe)
			wbbe.disassembleForMovement();

		// gantry carriage / shaft: moveGantryPinion, moveGantryShaft
		if (state.getBlock() instanceof dev.createpolymer.contraptions.gantry.GantryCarriageBlock)
			moveGantryPinion(world, pos, frontier, visited, state);

		if (state.getBlock() instanceof dev.createpolymer.kinetics.gantry.GantryShaftBlock)
			moveGantryShaft(world, pos, frontier, visited, state);

		if (state.getBlock() instanceof dev.createpolymer.contraptions.chassis.StickerBlock
			&& state.getValue(dev.createpolymer.contraptions.chassis.StickerBlock.EXTENDED)) {
			Direction offset = state.getValue(dev.createpolymer.contraptions.chassis.StickerBlock.FACING);
			BlockPos attached = pos.relative(offset);
			if (!visited.contains(attached)
				&& !BlockMovementChecks.isNotSupportive(world.getBlockState(attached), offset.getOpposite()))
				frontier.add(attached);
		}

		// chain conveyors: ccbe.notifyConnectedToValidate();

		// Double Chest halves stick together
		if (state.hasProperty(ChestBlock.TYPE) && state.hasProperty(ChestBlock.FACING)
			&& state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			Direction offset = ChestBlock.getConnectedDirection(state);
			BlockPos attached = pos.relative(offset);
			if (!visited.contains(attached))
				frontier.add(attached);
		}

		// Bogeys tend to have sticky sides
		if (state.getBlock() instanceof dev.createpolymer.trains.bogey.AbstractBogeyBlock<?> bogey)
			for (Direction d : bogey.getStickySurfaces(world, pos, state))
				if (!visited.contains(pos.relative(d)))
					frontier.add(pos.relative(d));

		// Bearings potentially create stabilized sub-contraptions
		if (state.getBlock() instanceof dev.createpolymer.contraptions.bearing.MechanicalBearingBlock)
			moveBearing(pos, frontier, visited, state);

		// WM Bearings attach their structure when moved
		if (state.getBlock() instanceof dev.createpolymer.contraptions.bearing.WindmillBearingBlock)
			moveWindmillBearing(pos, frontier, visited, state);

		// Seats transfer their passenger to the contraption
		if (ContraptionBlockTags.SEATS.matches(state))
			moveSeat(world, pos);

		// Pulleys drag their rope and their attached structure: movePulley
		if (state.getBlock() instanceof dev.createpolymer.contraptions.pulley.PulleyBlock)
			movePulley(world, pos, frontier, visited);

		// Pistons drag their attaches poles and extension
		if (state.getBlock() instanceof MechanicalPistonBlock)
			if (!moveMechanicalPiston(world, pos, frontier, visited, state))
				return false;
		if (isExtensionPole(state))
			movePistonPole(world, pos, frontier, visited, state);
		if (isPistonHead(state))
			movePistonHead(world, pos, frontier, visited, state);

		// Cart assemblers attach themselves
		BlockPos posDown = pos.below();
		BlockState stateBelow = world.getBlockState(posDown);
		if (!visited.contains(posDown) && stateBelow.is(MinecartRegistry.CART_ASSEMBLER))
			frontier.add(posDown);

		// Slime blocks and super glue drag adjacent blocks if possible
		for (Direction offset : Iterate.directions) {
			BlockPos offsetPos = pos.relative(offset);
			BlockState blockState = world.getBlockState(offsetPos);
			if (isAnchoringBlockAt(offsetPos))
				continue;
			if (!movementAllowed(blockState, world, offsetPos)) {
				if (offset == forcedDirection)
					throw AssemblyException.unmovableBlock(pos, state);
				continue;
			}

			boolean wasVisited = visited.contains(offsetPos);
			boolean faceHasGlue = SuperGlueEntity.isGlued(world, pos, offset, glueToRemove);
			boolean blockAttachedTowardsFace =
				BlockMovementChecks.isBlockAttachedTowards(blockState, world, offsetPos, offset.getOpposite());
			boolean brittle = BlockMovementChecks.isBrittle(blockState);
			boolean canStick = !brittle && canStickToEachOther(state, blockState);
			if (canStick) {
				if (state.getPistonPushReaction() == PushReaction.PUSH
					|| blockState.getPistonPushReaction() == PushReaction.PUSH) {
					canStick = false;
				}
				if (BlockMovementChecks.isNotSupportive(state, offset)) {
					canStick = false;
				}
				if (BlockMovementChecks.isNotSupportive(blockState, offset.getOpposite())) {
					canStick = false;
				}
			}

			if (!wasVisited && (canStick || blockAttachedTowardsFace || faceHasGlue
				|| (offset == forcedDirection && !BlockMovementChecks.isNotSupportive(state, forcedDirection))))
				frontier.add(offsetPos);
		}

		addBlock(world, pos, capture(world, pos));
		if (blocks.size() <= ContraptionRegistry.maxBlocksMoved)
			return true;
		else
			throw AssemblyException.structureTooLarge();
	}

	/** NeoForge's default BlockState.canStickTo: slime and honey do not mix, and one side must be sticky. */
	private static boolean canStickToEachOther(BlockState a, BlockState b) {
		if (a.is(Blocks.HONEY_BLOCK) && b.is(Blocks.SLIME_BLOCK))
			return false;
		if (a.is(Blocks.SLIME_BLOCK) && b.is(Blocks.HONEY_BLOCK))
			return false;
		return a.is(Blocks.SLIME_BLOCK) || a.is(Blocks.HONEY_BLOCK) || b.is(Blocks.SLIME_BLOCK) || b.is(Blocks.HONEY_BLOCK);
	}

	protected void movePistonHead(Level world, BlockPos pos, Queue<BlockPos> frontier, Set<BlockPos> visited,
								  BlockState state) {
		Direction direction = state.getValue(MechanicalPistonHeadBlock.FACING);
		BlockPos offset = pos.relative(direction.getOpposite());
		if (!visited.contains(offset)) {
			BlockState blockState = world.getBlockState(offset);
			if (isExtensionPole(blockState) && blockState.getValue(PistonExtensionPoleBlock.FACING)
				.getAxis() == direction.getAxis())
				frontier.add(offset);
			if (blockState.getBlock() instanceof MechanicalPistonBlock) {
				Direction pistonFacing = blockState.getValue(MechanicalPistonBlock.FACING);
				if (pistonFacing == direction
					&& blockState.getValue(MechanicalPistonBlock.STATE) == PistonState.EXTENDED)
					frontier.add(offset);
			}
		}
		if (state.getValue(MechanicalPistonHeadBlock.TYPE) == PistonType.STICKY) {
			BlockPos attached = pos.relative(direction);
			if (!visited.contains(attached))
				frontier.add(attached);
		}
	}

	protected void movePistonPole(Level world, BlockPos pos, Queue<BlockPos> frontier, Set<BlockPos> visited,
								  BlockState state) {
		for (Direction d : Iterate.directionsInAxis(state.getValue(PistonExtensionPoleBlock.FACING)
			.getAxis())) {
			BlockPos offset = pos.relative(d);
			if (!visited.contains(offset)) {
				BlockState blockState = world.getBlockState(offset);
				if (isExtensionPole(blockState) && blockState.getValue(PistonExtensionPoleBlock.FACING)
					.getAxis() == d.getAxis())
					frontier.add(offset);
				if (isPistonHead(blockState) && blockState.getValue(MechanicalPistonHeadBlock.FACING)
					.getAxis() == d.getAxis())
					frontier.add(offset);
				if (blockState.getBlock() instanceof MechanicalPistonBlock) {
					Direction pistonFacing = blockState.getValue(MechanicalPistonBlock.FACING);
					if (pistonFacing == d || pistonFacing == d.getOpposite()
						&& blockState.getValue(MechanicalPistonBlock.STATE) == PistonState.EXTENDED)
						frontier.add(offset);
				}
			}
		}
	}

	private void moveSeat(Level world, BlockPos pos) {
		BlockPos local = toLocalPos(pos);
		getSeats().add(local);
		List<SeatEntity> seatsEntities = world.getEntitiesOfClass(SeatEntity.class, new AABB(pos));
		if (!seatsEntities.isEmpty()) {
			SeatEntity seat = seatsEntities.get(0);
			List<Entity> passengers = seat.getPassengers();
			if (!passengers.isEmpty())
				initialPassengers.put(local, passengers.get(0));
		}
	}

	protected void moveGantryPinion(Level world, BlockPos pos, Queue<BlockPos> frontier, Set<BlockPos> visited,
									BlockState state) {
		BlockPos offset = pos.relative(state.getValue(dev.createpolymer.contraptions.gantry.GantryCarriageBlock.FACING));
		if (!visited.contains(offset))
			frontier.add(offset);
		Axis rotationAxis = ((dev.createpolymer.kinetics.base.IRotate) state.getBlock()).getRotationAxis(state);
		for (Direction d : Iterate.directionsInAxis(rotationAxis)) {
			offset = pos.relative(d);
			BlockState offsetState = world.getBlockState(offset);
			if (offsetState.getBlock() instanceof dev.createpolymer.kinetics.gantry.GantryShaftBlock
				&& offsetState.getValue(dev.createpolymer.kinetics.gantry.GantryShaftBlock.FACING)
					.getAxis() == d.getAxis())
				if (!visited.contains(offset))
					frontier.add(offset);
		}
	}

	protected void moveGantryShaft(Level world, BlockPos pos, Queue<BlockPos> frontier, Set<BlockPos> visited,
								   BlockState state) {
		for (Direction d : Iterate.directions) {
			BlockPos offset = pos.relative(d);
			if (!visited.contains(offset)) {
				BlockState offsetState = world.getBlockState(offset);
				Direction facing = state.getValue(dev.createpolymer.kinetics.gantry.GantryShaftBlock.FACING);
				if (d.getAxis() == facing.getAxis()
					&& offsetState.getBlock() instanceof dev.createpolymer.kinetics.gantry.GantryShaftBlock
					&& offsetState.getValue(dev.createpolymer.kinetics.gantry.GantryShaftBlock.FACING) == facing)
					frontier.add(offset);
				else if (offsetState.getBlock() instanceof dev.createpolymer.contraptions.gantry.GantryCarriageBlock
					&& offsetState.getValue(dev.createpolymer.contraptions.gantry.GantryCarriageBlock.FACING) == d)
					frontier.add(offset);
			}
		}
	}

	private void movePulley(Level world, BlockPos pos, Queue<BlockPos> frontier, Set<BlockPos> visited) {
		int limit = ContraptionRegistry.maxRopeLength;
		BlockPos ropePos = pos;
		while (limit-- >= 0) {
			ropePos = ropePos.below();
			if (!world.isLoaded(ropePos))
				break;
			BlockState ropeState = world.getBlockState(ropePos);
			Block block = ropeState.getBlock();
			if (!(block instanceof dev.createpolymer.contraptions.pulley.PulleyBlock.RopeBlock)
				&& !(block instanceof dev.createpolymer.contraptions.pulley.PulleyBlock.MagnetBlock)) {
				if (!visited.contains(ropePos))
					frontier.add(ropePos);
				break;
			}
			addBlock(world, ropePos, capture(world, ropePos));
		}
	}

	private boolean moveMechanicalPiston(Level world, BlockPos pos, Queue<BlockPos> frontier, Set<BlockPos> visited,
										 BlockState state) throws AssemblyException {
		Direction direction = state.getValue(MechanicalPistonBlock.FACING);
		PistonState pistonState = state.getValue(MechanicalPistonBlock.STATE);
		if (pistonState == PistonState.MOVING)
			return false;

		BlockPos offset = pos.relative(direction.getOpposite());
		if (!visited.contains(offset)) {
			BlockState poleState = world.getBlockState(offset);
			if (isExtensionPole(poleState) && poleState.getValue(PistonExtensionPoleBlock.FACING)
				.getAxis() == direction.getAxis())
				frontier.add(offset);
		}

		if (pistonState == PistonState.EXTENDED || MechanicalPistonBlock.isStickyPiston(state)) {
			offset = pos.relative(direction);
			if (!visited.contains(offset))
				frontier.add(offset);
		}

		return true;
	}

	protected Pair<StructureBlockInfo, BlockEntity> capture(Level world, BlockPos pos) {
		BlockState blockstate = world.getBlockState(pos);
		if (blockstate.is(RedstoneRegistry.REDSTONE_CONTACT))
			blockstate = blockstate.setValue(RedstoneContactBlock.POWERED, true);
		// sliding doors rest closed or open on contraptions: their panels are not drawn by the block
		if (blockstate.hasProperty(dev.createpolymer.decoration.slidingDoor.SlidingDoorBlock.VISIBLE))
			blockstate = blockstate.setValue(dev.createpolymer.decoration.slidingDoor.SlidingDoorBlock.VISIBLE, false);
		if (blockstate.getBlock() instanceof dev.createpolymer.contraptions.actors.trainControls.ControlsBlock
			&& this instanceof dev.createpolymer.trains.entity.CarriageContraption)
			blockstate = blockstate.setValue(dev.createpolymer.contraptions.actors.trainControls.ControlsBlock.OPEN, true);
		if (blockstate.getBlock() instanceof ButtonBlock) {
			blockstate = blockstate.setValue(ButtonBlock.POWERED, false);
			world.scheduleTick(pos, blockstate.getBlock(), -1);
		}
		if (blockstate.getBlock() instanceof PressurePlateBlock) {
			blockstate = blockstate.setValue(PressurePlateBlock.POWERED, false);
			world.scheduleTick(pos, blockstate.getBlock(), -1);
		}
		CompoundTag compoundnbt = getBlockEntityNBT(world, pos);
		BlockEntity blockEntity = world.getBlockEntity(pos);
		// powered shafts become plain bracketed shafts, factory panels write safe data

		return Pair.of(new StructureBlockInfo(pos, blockstate, compoundnbt), blockEntity);
	}

	protected void addBlock(Level level, BlockPos pos, Pair<StructureBlockInfo, BlockEntity> pair) {
		StructureBlockInfo captured = pair.getKey();
		BlockPos localPos = pos.subtract(anchor);
		BlockState state = captured.state();
		StructureBlockInfo structureBlockInfo = new StructureBlockInfo(localPos, state, captured.nbt());

		if (blocks.put(localPos, structureBlockInfo) != null)
			return;
		bounds = bounds.minmax(new AABB(localPos));

		BlockEntity be = pair.getValue();

		storage.addBlock(level, state, pos, localPos, be);

		captureMultiblock(localPos, structureBlockInfo, be);

		if (MovementBehaviour.REGISTRY.get(state) != null)
			actors.add(MutablePair.of(structureBlockInfo, null));

		MovingInteractionBehaviour interactionBehaviour = MovingInteractionBehaviour.REGISTRY.get(state);
		if (interactionBehaviour != null)
			interactors.put(localPos, interactionBehaviour);

		if (be instanceof CreativeCrateBlockEntity crate && crate.getBehaviour(FilteringBehaviour.TYPE)
			.getFilter()
			.isEmpty())
			hasUniversalCreativeCrate = true;
	}

	protected void captureMultiblock(BlockPos localPos, StructureBlockInfo structureBlockInfo, BlockEntity be) {
		if (!(be instanceof IMultiBlockEntityContainer multiBlockBE))
			return;

		CompoundTag nbt = structureBlockInfo.nbt();
		BlockPos controllerPos = localPos;
		if (nbt.contains("Controller"))
			controllerPos = toLocalPos(nbt.read("Controller", BlockPos.CODEC).orElse(localPos));
		nbt.store("Controller", BlockPos.CODEC, controllerPos);

		if (multiBlockBE.isController() && multiBlockBE.getHeight() <= 1 && multiBlockBE.getWidth() <= 1) {
			nbt.store("LastKnownPos", BlockPos.CODEC, BlockPos.ZERO.below(Integer.MAX_VALUE - 1));
			return;
		}

		nbt.remove("LastKnownPos");
		capturedMultiblocks.put(controllerPos, structureBlockInfo);
	}

	@Nullable
	protected CompoundTag getBlockEntityNBT(Level world, BlockPos pos) {
		BlockEntity blockEntity = world.getBlockEntity(pos);
		if (blockEntity == null)
			return null;
		CompoundTag nbt = blockEntity.saveWithFullMetadata(world.registryAccess());
		nbt.remove("x");
		nbt.remove("y");
		nbt.remove("z");

		return nbt;
	}

	protected BlockPos toLocalPos(BlockPos globalPos) {
		return globalPos.subtract(anchor);
	}

	protected boolean movementAllowed(BlockState state, Level world, BlockPos pos) {
		return BlockMovementChecks.isMovementAllowed(state, world, pos);
	}

	protected boolean isAnchoringBlockAt(BlockPos pos) {
		return pos.equals(anchor);
	}

	public void readNBT(Level world, CompoundTag nbt, boolean spawnData) {
		readBlocksCompound(nbt.getCompoundOrEmpty("Blocks"), world);

		capturedMultiblocks.clear();
		nbt.getListOrEmpty("CapturedMultiblocks").forEach(c -> {
			if (!(c instanceof CompoundTag tag))
				return;
			if (!tag.contains("Controller") && !tag.contains("Parts"))
				return;

			BlockPos controllerPos = tag.read("Controller", BlockPos.CODEC).orElse(BlockPos.ZERO);
			tag.getListOrEmpty("Parts")
				.forEach(part -> {
					if (!(part instanceof CompoundTag cPart))
						return;
					BlockPos partPos = cPart.read("Pos", BlockPos.CODEC).orElse(BlockPos.ZERO);
					StructureBlockInfo partInfo = this.blocks.get(partPos);
					capturedMultiblocks.put(controllerPos, partInfo);
				});
		});

		storage.read(nbt, world.registryAccess(), spawnData, this);

		actors.clear();
		nbt.getListOrEmpty("Actors")
			.forEach(c -> {
				if (!(c instanceof CompoundTag comp))
					return;
				StructureBlockInfo info = this.blocks.get(comp.read("Pos", BlockPos.CODEC).orElse(BlockPos.ZERO));
				if (info == null)
					return;
				MovementContext context = MovementContext.readNBT(world, info, comp, this);
				getActors().add(MutablePair.of(info, context));
			});

		disabledActors = new ArrayList<>(NBTHelper.readItemList(
			TagValueInput.create(ProblemReporter.DISCARDING, world.registryAccess(), nbt), "DisabledActors"));
		for (ItemStack stack : disabledActors)
			setActorsActive(stack, false);

		superglue.clear();
		NBTHelper.iterateCompoundList(nbt.getListOrEmpty("Superglue"),
			c -> superglue.add(SuperGlueEntity.readBoundingBox(c)));

		seats.clear();
		NBTHelper.iterateCompoundList(nbt.getListOrEmpty("Seats"),
			c -> seats.add(c.read("Pos", BlockPos.CODEC).orElse(BlockPos.ZERO)));

		seatMapping.clear();
		NBTHelper.iterateCompoundList(nbt.getListOrEmpty("Passengers"),
			c -> c.read("Id", net.minecraft.core.UUIDUtil.CODEC)
				.ifPresent(id -> seatMapping.put(id, c.getIntOr("Seat", 0))));

		stabilizedSubContraptions.clear();
		NBTHelper.iterateCompoundList(nbt.getListOrEmpty("SubContraptions"),
			c -> c.read("Id", net.minecraft.core.UUIDUtil.CODEC).ifPresent(id -> stabilizedSubContraptions.put(id,
				BlockFace.fromNBT(c.getCompoundOrEmpty("Location")))));

		interactors.clear();
		NBTHelper.iterateCompoundList(nbt.getListOrEmpty("Interactors"), c -> {
			BlockPos pos = c.read("Pos", BlockPos.CODEC).orElse(BlockPos.ZERO);
			StructureBlockInfo structureBlockInfo = getBlocks().get(pos);
			if (structureBlockInfo == null)
				return;
			MovingInteractionBehaviour behaviour = MovingInteractionBehaviour.REGISTRY.get(structureBlockInfo.state());
			if (behaviour != null)
				interactors.put(pos, behaviour);
		});

		if (nbt.contains("BoundsFront"))
			bounds = NBTHelper.readAABB(nbt.getListOrEmpty("BoundsFront"));

		stalled = nbt.getBooleanOr("Stalled", false);
		hasUniversalCreativeCrate = nbt.getBooleanOr("BottomlessSupply", false);
		anchor = nbt.read("Anchor", BlockPos.CODEC).orElse(BlockPos.ZERO);
	}

	public CompoundTag writeNBT(HolderLookup.Provider registries, boolean spawnPacket) {
		CompoundTag nbt = new CompoundTag();
		nbt.putString("Type", this.getType().id.toString());

		CompoundTag blocksNBT = writeBlocksCompound(spawnPacket);

		ListTag multiblocksNBT = new ListTag();
		capturedMultiblocks.keySet().forEach(controllerPos -> {
			CompoundTag tag = new CompoundTag();
			tag.store("Controller", BlockPos.CODEC, controllerPos);

			Collection<StructureBlockInfo> multiblockParts = capturedMultiblocks.get(controllerPos);
			ListTag partsNBT = new ListTag();
			multiblockParts.forEach(info -> {
				CompoundTag c = new CompoundTag();
				c.store("Pos", BlockPos.CODEC, info.pos());
				partsNBT.add(c);
			});
			tag.put("Parts", partsNBT);

			multiblocksNBT.add(tag);
		});

		ListTag actorsNBT = new ListTag();
		for (MutablePair<StructureBlockInfo, MovementContext> actor : getActors()) {
			MovementBehaviour behaviour = MovementBehaviour.REGISTRY.get(actor.left.state());
			if (behaviour == null)
				continue;
			CompoundTag compound = new CompoundTag();
			compound.store("Pos", BlockPos.CODEC, actor.left.pos());
			behaviour.writeExtraData(actor.right);
			actor.right.writeToNBT(compound);
			actorsNBT.add(compound);
		}

		ListTag superglueNBT = new ListTag();
		if (!spawnPacket) {
			for (AABB glueEntry : superglue) {
				CompoundTag c = new CompoundTag();
				SuperGlueEntity.writeBoundingBox(c, glueEntry);
				superglueNBT.add(c);
			}
		}

		writeStorage(nbt, registries, spawnPacket);

		ListTag interactorNBT = new ListTag();
		for (BlockPos pos : interactors.keySet()) {
			CompoundTag c = new CompoundTag();
			c.store("Pos", BlockPos.CODEC, pos);
			interactorNBT.add(c);
		}

		ListTag seatsNBT = new ListTag();
		for (BlockPos pos : getSeats()) {
			CompoundTag c = new CompoundTag();
			c.store("Pos", BlockPos.CODEC, pos);
			seatsNBT.add(c);
		}
		nbt.put("Seats", seatsNBT);

		ListTag passengersNBT = new ListTag();
		for (Entry<UUID, Integer> e : getSeatMapping().entrySet()) {
			CompoundTag tag = new CompoundTag();
			tag.store("Id", net.minecraft.core.UUIDUtil.CODEC, e.getKey());
			tag.putInt("Seat", e.getValue());
			passengersNBT.add(tag);
		}
		nbt.put("Passengers", passengersNBT);

		ListTag subContraptionsNBT = new ListTag();
		for (Entry<UUID, BlockFace> e : stabilizedSubContraptions.entrySet()) {
			CompoundTag tag = new CompoundTag();
			tag.store("Id", net.minecraft.core.UUIDUtil.CODEC, e.getKey());
			tag.put("Location", e.getValue()
				.serializeNBT());
			subContraptionsNBT.add(tag);
		}
		nbt.put("SubContraptions", subContraptionsNBT);

		nbt.put("Blocks", blocksNBT);
		nbt.put("Actors", actorsNBT);
		nbt.put("CapturedMultiblocks", multiblocksNBT);
		nbt.put("Interactors", interactorNBT);
		nbt.put("Superglue", superglueNBT);
		nbt.store("Anchor", BlockPos.CODEC, anchor);
		nbt.putBoolean("Stalled", stalled);
		nbt.putBoolean("BottomlessSupply", hasUniversalCreativeCrate);

		net.minecraft.world.level.storage.TagValueOutput disabled =
			net.minecraft.world.level.storage.TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		NBTHelper.writeItemList(disabled, "DisabledActors", disabledActors);
		nbt.merge(disabled.buildResult());

		if (bounds != null) {
			ListTag bb = NBTHelper.writeAABB(bounds);
			nbt.put("BoundsFront", bb);
		}

		return nbt;
	}

	public void writeStorage(CompoundTag nbt, HolderLookup.Provider registries, boolean spawnPacket) {
		storage.write(nbt, registries, spawnPacket);
	}

	private CompoundTag writeBlocksCompound(boolean spawnPacket) {
		CompoundTag compound = new CompoundTag();
		Object2IntLinkedOpenHashMap<BlockState> palette = new Object2IntLinkedOpenHashMap<>();
		palette.defaultReturnValue(-1);
		ListTag blockList = new ListTag();

		for (StructureBlockInfo block : this.blocks.values()) {
			int id = palette.getInt(block.state());
			if (id == -1) {
				id = palette.size();
				palette.put(block.state(), id);
			}
			BlockPos pos = block.pos();
			CompoundTag c = new CompoundTag();
			c.putLong("Pos", pos.asLong());
			c.putInt("State", id);
			if (block.nbt() != null)
				c.put("Data", block.nbt());
			blockList.add(c);
		}

		ListTag paletteNBT = new ListTag();
		for (BlockState state : palette.keySet())
			paletteNBT.add(NbtUtils.writeBlockState(state));

		compound.put("Palette", paletteNBT);
		compound.put("BlockList", blockList);

		return compound;
	}

	private void readBlocksCompound(CompoundTag compound, Level world) {
		blocks.clear();

		HolderGetter<Block> holderGetter = world.holderLookup(Registries.BLOCK);
		List<BlockState> palette = new ArrayList<>();
		for (Tag tag : compound.getListOrEmpty("Palette"))
			if (tag instanceof CompoundTag stateTag)
				palette.add(NbtUtils.readBlockState(holderGetter, stateTag));

		for (Tag tag : compound.getListOrEmpty("BlockList")) {
			if (!(tag instanceof CompoundTag c))
				continue;
			int stateId = c.getIntOr("State", 0);
			if (stateId < 0 || stateId >= palette.size())
				continue;
			StructureBlockInfo info = new StructureBlockInfo(BlockPos.of(c.getLongOr("Pos", 0)), palette.get(stateId),
				c.contains("Data") ? c.getCompoundOrEmpty("Data") : null);
			this.blocks.put(info.pos(), info);
		}

		resetClientContraption();
	}

	public void removeBlocksFromWorld(Level world, BlockPos offset) {
		glueToRemove.forEach(glue -> {
			superglue.add(glue.getBoundingBox()
				.move(Vec3.atLowerCornerOf(offset.offset(anchor))
					.scale(-1)));
			glue.discard();
		});

		List<BoundingBox> minimisedGlue = new ArrayList<>();
		for (int i = 0; i < superglue.size(); i++)
			minimisedGlue.add(null);

		for (boolean brittles : Iterate.trueAndFalse) {
			for (Iterator<StructureBlockInfo> iterator = blocks.values()
				.iterator(); iterator.hasNext(); ) {
				StructureBlockInfo block = iterator.next();
				if (brittles != BlockMovementChecks.isBrittle(block.state()))
					continue;

				for (int i = 0; i < superglue.size(); i++) {
					AABB aabb = superglue.get(i);
					if (aabb == null
						|| !aabb.contains(block.pos().getX() + .5, block.pos().getY() + .5, block.pos().getZ() + .5))
						continue;
					if (minimisedGlue.get(i) == null)
						minimisedGlue.set(i, new BoundingBox(block.pos()));
					else
						minimisedGlue.set(i, minimisedGlue.get(i).encapsulate(block.pos()));
				}

				BlockPos add = block.pos().offset(anchor)
					.offset(offset);
				if (customBlockRemoval(world, add, block.state()))
					continue;
				BlockState oldState = world.getBlockState(add);
				Block blockIn = oldState.getBlock();
				boolean blockMismatch = block.state().getBlock() != blockIn;
				if (blockMismatch)
					iterator.remove();
				world.removeBlockEntity(add);
				int flags = Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_KNOWN_SHAPE
					| Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE;
				if (blockIn instanceof SimpleWaterloggedBlock && oldState.hasProperty(BlockStateProperties.WATERLOGGED)
					&& oldState.getValue(BlockStateProperties.WATERLOGGED)) {
					world.setBlock(add, Blocks.WATER.defaultBlockState(), flags);
					continue;
				}
				world.setBlock(add, Blocks.AIR.defaultBlockState(), flags);
			}
		}

		superglue.clear();
		for (BoundingBox box : minimisedGlue) {
			if (box == null)
				continue;
			AABB bb = new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
			if (bb.getSize() > 1.01)
				superglue.add(bb);
		}

		for (StructureBlockInfo block : blocks.values()) {
			BlockPos add = block.pos().offset(anchor)
				.offset(offset);

			int flags = Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_ALL;
			world.sendBlockUpdated(add, block.state(), Blocks.AIR.defaultBlockState(), flags);

			// when the blockstate is set to air, the block's POI data is removed, but
			// the neighbour update tries to remove it again, so to prevent an error from being logged by
			// double-removal we add the POI data back now
			if (world instanceof ServerLevel serverWorld)
				PoiTypes.forState(block.state())
					.ifPresent(poiType -> {
						serverWorld.getServer()
							.execute(() -> serverWorld.getPoiManager()
								.add(add, poiType));
					});

			markAndNotifyBlock(world, add, block.state(), Blocks.AIR.defaultBlockState(), flags, 512);
			block.state().updateIndirectNeighbourShapes(world, add, flags & -2);
		}
	}

	/**
	 * Level.markAndNotifyBlock of earlier versions: notify the neighbours of a block that changed with
	 * {@link Block#UPDATE_NEIGHBORS} suppressed, and update neighbour shapes.
	 */
	public static void markAndNotifyBlock(Level world, BlockPos pos, BlockState oldState, BlockState newState,
		int flags, int recursionLeft) {
		BlockState current = world.getBlockState(pos);
		if (current != newState)
			return;
		if (oldState != current)
			world.setBlocksDirty(pos, oldState, current);
		if ((flags & Block.UPDATE_CLIENTS) != 0)
			world.sendBlockUpdated(pos, oldState, newState, flags);
		if ((flags & Block.UPDATE_NEIGHBORS) != 0) {
			world.updateNeighborsAt(pos, oldState.getBlock());
			if (newState.hasAnalogOutputSignal())
				world.updateNeighbourForOutputSignal(pos, newState.getBlock());
		}
		if ((flags & Block.UPDATE_KNOWN_SHAPE) == 0 && recursionLeft > 0) {
			int i = flags & -34;
			oldState.updateIndirectNeighbourShapes(world, pos, i, recursionLeft - 1);
			newState.updateNeighbourShapes(world, pos, i, recursionLeft - 1);
			newState.updateIndirectNeighbourShapes(world, pos, i, recursionLeft - 1);
		}
		world.updatePOIOnBlockStateChange(pos, oldState, current);
	}

	public void addBlocksToWorld(Level world, StructureTransform transform) {
		if (disassembled)
			return;
		disassembled = true;

		boolean shouldDropBlocks = !ContraptionRegistry.noDropWhenContraptionReplaceBlocks;

		translateMultiblockControllers(transform);

		for (boolean nonBrittles : Iterate.trueAndFalse) {
			for (StructureBlockInfo block : blocks.values()) {
				if (nonBrittles == BlockMovementChecks.isBrittle(block.state()))
					continue;

				BlockPos targetPos = transform.apply(block.pos());
				BlockState state = transform.apply(block.state());

				if (customBlockPlacement(world, targetPos, state))
					continue;

				if (nonBrittles && world instanceof ServerLevel serverLevel)
					for (Direction face : Iterate.directions)
						state = state.updateShape(world, serverLevel, targetPos, face, targetPos.relative(face),
							world.getBlockState(targetPos.relative(face)), world.getRandom());

				BlockState blockState = world.getBlockState(targetPos);
				if (blockState.getDestroySpeed(world, targetPos) == -1 || (state.getCollisionShape(world, targetPos)
					.isEmpty()
					&& !blockState.getCollisionShape(world, targetPos)
					.isEmpty())) {
					if (targetPos.getY() == world.getMinY())
						targetPos = targetPos.above();
					world.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, targetPos, Block.getId(state));
					if (shouldDropBlocks) {
						Block.dropResources(state, world, targetPos, null);
					}
					continue;
				}
				if (state.getBlock() instanceof SimpleWaterloggedBlock
					&& state.hasProperty(BlockStateProperties.WATERLOGGED)) {
					FluidState fluidState = world.getFluidState(targetPos);
					state = state.setValue(BlockStateProperties.WATERLOGGED, fluidState.getType() == Fluids.WATER);
				}

				world.destroyBlock(targetPos, shouldDropBlocks);

				// shafts re-pick their shaft type, sliding doors become visible again
				if (state.hasProperty(dev.createpolymer.decoration.slidingDoor.SlidingDoorBlock.VISIBLE))
					state = state.setValue(dev.createpolymer.decoration.slidingDoor.SlidingDoorBlock.VISIBLE,
						!state.getValue(dev.createpolymer.decoration.slidingDoor.SlidingDoorBlock.OPEN))
						.setValue(dev.createpolymer.decoration.slidingDoor.SlidingDoorBlock.POWERED, false);
				// Stop Sculk shriekers from getting "stuck" if moved mid-shriek.
				if (state.is(Blocks.SCULK_SHRIEKER)) {
					state = Blocks.SCULK_SHRIEKER.defaultBlockState();
				}

				world.setBlock(targetPos, state, Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_ALL);

				boolean verticalRotation = transform.rotationAxis == null || transform.rotationAxis.isHorizontal();
				verticalRotation = verticalRotation && transform.rotation != Rotation.NONE;
				if (verticalRotation) {
					// ropes and magnets break when turned sideways
					if (state.getBlock() instanceof dev.createpolymer.contraptions.pulley.PulleyBlock.RopeBlock
						|| state.getBlock() instanceof dev.createpolymer.contraptions.pulley.PulleyBlock.MagnetBlock
						|| state.getBlock() instanceof DoorBlock)
						world.destroyBlock(targetPos, shouldDropBlocks);
				}

				BlockEntity blockEntity = world.getBlockEntity(targetPos);

				CompoundTag tag = block.nbt();

				// Temporary fix: Calling load on a Sculk sensor causes it to not react to vibrations.
				if (state.is(Blocks.SCULK_SENSOR) || state.is(Blocks.SCULK_SHRIEKER))
					tag = null;

				if (blockEntity != null && tag != null) {
					tag.putInt("x", targetPos.getX());
					tag.putInt("y", targetPos.getY());
					tag.putInt("z", targetPos.getZ());

					// pulleys forget their offsets when turned sideways
					if (verticalRotation && blockEntity instanceof dev.createpolymer.contraptions.pulley.PulleyBlockEntity) {
						tag.remove("Offset");
						tag.remove("InitialOffset");
					}

					if (blockEntity instanceof IMultiBlockEntityContainer) {
						if (tag.contains("LastKnownPos") || capturedMultiblocks.isEmpty()) {
							tag.store("LastKnownPos", BlockPos.CODEC, BlockPos.ZERO.below(Integer.MAX_VALUE - 1));
							tag.remove("Controller");
						}
					}

					blockEntity.loadWithComponents(
						TagValueInput.create(ProblemReporter.DISCARDING, world.registryAccess(), tag));
				}

				storage.unmount(world, block, targetPos, blockEntity);

				if (blockEntity != null) {
					transform.apply(blockEntity);
				}
			}
		}

		for (StructureBlockInfo block : blocks.values()) {
			if (!shouldUpdateAfterMovement(block))
				continue;
			BlockPos targetPos = transform.apply(block.pos());
			markAndNotifyBlock(world, targetPos, block.state(), block.state(),
				Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_ALL, 512);
		}

		for (AABB box : superglue) {
			box = new AABB(transform.apply(new Vec3(box.minX, box.minY, box.minZ)),
				transform.apply(new Vec3(box.maxX, box.maxY, box.maxZ)));
			if (!world.isClientSide())
				world.addFreshEntity(new SuperGlueEntity(world, box));
		}
	}

	protected void translateMultiblockControllers(StructureTransform transform) {
		if (transform.rotationAxis != null && transform.rotationAxis != Axis.Y && transform.rotation != Rotation.NONE) {
			capturedMultiblocks.values().forEach(info -> {
				info.nbt().store("LastKnownPos", BlockPos.CODEC, BlockPos.ZERO.below(Integer.MAX_VALUE - 1));
			});
			return;
		}

		capturedMultiblocks.keySet().forEach(controllerPos -> {
			Collection<StructureBlockInfo> multiblockParts = capturedMultiblocks.get(controllerPos);
			Optional<BoundingBox> optionalBoundingBox = BoundingBox.encapsulatingPositions(multiblockParts.stream().map(info -> transform.apply(info.pos())).toList());
			if (optionalBoundingBox.isEmpty())
				return;

			BoundingBox boundingBox = optionalBoundingBox.get();
			BlockPos newControllerPos = new BlockPos(boundingBox.minX(), boundingBox.minY(), boundingBox.minZ());
			BlockPos otherPos = transform.unapply(newControllerPos);

			multiblockParts.forEach(info -> info.nbt().store("Controller", BlockPos.CODEC, newControllerPos));

			if (controllerPos.equals(otherPos))
				return;

			// swap nbt data to the new controller position
			StructureBlockInfo prevControllerInfo = blocks.get(controllerPos);
			StructureBlockInfo newControllerInfo = blocks.get(otherPos);
			if (prevControllerInfo == null || newControllerInfo == null)
				return;

			blocks.put(otherPos, new StructureBlockInfo(newControllerInfo.pos(), newControllerInfo.state(), prevControllerInfo.nbt()));
			blocks.put(controllerPos, new StructureBlockInfo(prevControllerInfo.pos(), prevControllerInfo.state(), newControllerInfo.nbt()));
		});
	}

	public void addPassengersToWorld(Level world, StructureTransform transform, List<Entity> seatedEntities) {
		for (Entity seatedEntity : seatedEntities) {
			if (getSeatMapping().isEmpty())
				continue;
			Integer seatIndex = getSeatMapping().get(seatedEntity.getUUID());
			if (seatIndex == null)
				continue;
			BlockPos seatPos = getSeats().get(seatIndex);
			seatPos = transform.apply(seatPos);
			if (!(world.getBlockState(seatPos)
				.getBlock() instanceof SeatBlock))
				continue;
			if (SeatBlock.isSeatOccupied(world, seatPos))
				continue;
			SeatBlock.sitDown(world, seatPos, seatedEntity);
		}
	}

	public void startMoving(Level world) {
		disabledActors.clear();

		for (MutablePair<StructureBlockInfo, MovementContext> pair : actors) {
			MovementContext context = new MovementContext(world, pair.left, this);
			MovementBehaviour behaviour = MovementBehaviour.REGISTRY.get(pair.left.state());
			if (behaviour != null)
				behaviour.startMoving(context);
			pair.setRight(context);
			if (behaviour instanceof ContraptionControlsMovement)
				disableActorOnStart(context);
		}

		for (ItemStack stack : disabledActors)
			setActorsActive(stack, false);
	}

	protected void disableActorOnStart(MovementContext context) {
		if (!ContraptionControlsMovement.isDisabledInitially(context))
			return;
		ItemStack filter = ContraptionControlsMovement.getFilter(context);
		if (filter == null)
			return;
		if (isActorTypeDisabled(filter))
			return;
		disabledActors.add(filter);
	}

	/** Whether the filter stack of contraption controls matches. */
	protected boolean isSameFilter(ItemStack a, ItemStack b) {
		return ContraptionControlsMovement.isSameFilter(a, b);
	}

	public boolean isActorTypeDisabled(ItemStack filter) {
		return disabledActors.stream()
			.anyMatch(i -> isSameFilter(i, filter));
	}

	public void setActorsActive(ItemStack referenceStack, boolean enable) {
		for (MutablePair<StructureBlockInfo, MovementContext> pair : actors) {
			MovementBehaviour behaviour = MovementBehaviour.REGISTRY.get(pair.left.state());
			if (behaviour == null)
				continue;
			ItemStack behaviourStack = behaviour.canBeDisabledVia(pair.right);
			if (behaviourStack == null)
				continue;
			if (!referenceStack.isEmpty() && !isSameFilter(referenceStack, behaviourStack))
				continue;
			pair.right.disabled = !enable;
			if (!enable)
				behaviour.onDisabledByControls(pair.right);
		}
	}

	public List<ItemStack> getDisabledActors() {
		return disabledActors;
	}

	public void stop(Level world) {
		forEachActor(world, (behaviour, ctx) -> {
			behaviour.stopMoving(ctx);
			ctx.position = null;
			ctx.motion = Vec3.ZERO;
			ctx.relativeMotion = Vec3.ZERO;
			ctx.rotation = v -> v;
		});
	}

	public void forEachActor(Level world, BiConsumer<MovementBehaviour, MovementContext> callBack) {
		for (MutablePair<StructureBlockInfo, MovementContext> pair : actors) {
			MovementBehaviour behaviour = MovementBehaviour.REGISTRY.get(pair.getLeft().state());
			if (behaviour == null)
				continue;
			callBack.accept(behaviour, pair.getRight());
		}
	}

	protected boolean shouldUpdateAfterMovement(StructureBlockInfo info) {
		if (PoiTypes.forState(info.state())
			.isPresent())
			return false;
		if (info.state().getBlock() instanceof dev.createpolymer.decoration.slidingDoor.SlidingDoorBlock)
			return false;
		return true;
	}

	public void expandBoundsAroundAxis(Axis axis) {
		Set<BlockPos> blocks = getBlocks().keySet();

		int radius = (int) (Math.ceil(getRadius(blocks, axis)));

		int maxX = radius + 2;
		int maxY = radius + 2;
		int maxZ = radius + 2;
		int minX = -radius - 1;
		int minY = -radius - 1;
		int minZ = -radius - 1;

		if (axis == Axis.X) {
			maxX = (int) bounds.maxX;
			minX = (int) bounds.minX;
		} else if (axis == Axis.Y) {
			maxY = (int) bounds.maxY;
			minY = (int) bounds.minY;
		} else if (axis == Axis.Z) {
			maxZ = (int) bounds.maxZ;
			minZ = (int) bounds.minZ;
		}

		bounds = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
	}

	public Map<UUID, Integer> getSeatMapping() {
		return seatMapping;
	}

	public BlockPos getSeatOf(UUID entityId) {
		if (!getSeatMapping().containsKey(entityId))
			return null;
		int seatIndex = getSeatMapping().get(entityId);
		if (seatIndex >= getSeats().size())
			return null;
		return getSeats().get(seatIndex);
	}

	public BlockPos getBearingPosOf(UUID subContraptionEntityId) {
		if (stabilizedSubContraptions.containsKey(subContraptionEntityId))
			return stabilizedSubContraptions.get(subContraptionEntityId)
				.getConnectedPos();
		return null;
	}

	public void setSeatMapping(Map<UUID, Integer> seatMapping) {
		this.seatMapping = seatMapping;
	}

	public List<BlockPos> getSeats() {
		return seats;
	}

	public Map<BlockPos, StructureBlockInfo> getBlocks() {
		return blocks;
	}

	public List<MutablePair<StructureBlockInfo, MovementContext>> getActors() {
		return actors;
	}

	@Nullable
	public MutablePair<StructureBlockInfo, MovementContext> getActorAt(BlockPos localPos) {
		for (MutablePair<StructureBlockInfo, MovementContext> pair : actors)
			if (localPos.equals(pair.left.pos()))
				return pair;
		return null;
	}

	public Map<BlockPos, MovingInteractionBehaviour> getInteractors() {
		return interactors;
	}

	public Map<UUID, BlockFace> getStabilizedSubContraptions() {
		return stabilizedSubContraptions;
	}

	public void invalidateColliders() {
		if (bounds == null)
			return;
		getContraptionWorld();
		simplifiedEntityColliders.size = 0;

		var populate = new Populate(simplifiedEntityColliders);

		for (Entry<BlockPos, StructureBlockInfo> entry : blocks.entrySet()) {
			StructureBlockInfo info = entry.getValue();
			BlockPos localPos = entry.getKey();
			VoxelShape collisionShape = info.state().getCollisionShape(collisionLevel, localPos, CollisionContext.empty());
			if (collisionShape.isEmpty())
				continue;

			populate.offsetX = localPos.getX();
			populate.offsetY = localPos.getY();
			populate.offsetZ = localPos.getZ();
			collisionShape.forAllBoxes(populate);
		}
	}

	public static double getRadius(Iterable<? extends Vec3i> blocks, Axis axis) {
		Axis axisA;
		Axis axisB;

		switch (axis) {
			case X -> {
				axisA = Axis.Y;
				axisB = Axis.Z;
			}
			case Y -> {
				axisA = Axis.X;
				axisB = Axis.Z;
			}
			case Z -> {
				axisA = Axis.X;
				axisB = Axis.Y;
			}
			default -> throw new IllegalStateException("Unexpected value: " + axis);
		}

		int maxDistSq = 0;
		for (Vec3i vec : blocks) {
			int a = vec.get(axisA);
			int b = vec.get(axisB);

			int distSq = a * a + b * b;

			if (distSq > maxDistSq)
				maxDistSq = distSq;
		}

		return Math.sqrt(maxDistSq);
	}

	public MountedStorageManager getStorage() {
		return this.storage;
	}

	public boolean isHiddenInPortal(BlockPos localPos) {
		return false;
	}

	@Nullable
	public CollisionList getSimplifiedEntityColliders() {
		return simplifiedEntityColliders;
	}

	public void tickStorage(AbstractContraptionEntity entity) {
		getStorage().tick(entity);
	}

	public boolean containsBlockBreakers() {
		for (MutablePair<StructureBlockInfo, MovementContext> pair : actors) {
			MovementBehaviour behaviour = MovementBehaviour.REGISTRY.get(pair.left.state());
			if (behaviour instanceof BlockBreakingMovementBehaviour || behaviour instanceof HarvesterMovementBehaviour)
				return true;
		}
		return false;
	}

	// Hooks for contraption kinds that are not ported yet; Oriented/Abstract entities consult them.

	/** Facing of a stabilized (bearing) sub-contraption, null for every other kind. */
	@Nullable
	public Direction getStabilizedFacing() {
		return null;
	}

	/**
	 * Facing of a bearing contraption (Create: {@code ((BearingContraption) contraption).getFacing()}), used by
	 * ControlledContraptionEntity for actors on the bearing axis. Null for every other kind.
	 */
	@Nullable
	public Direction getBearingFacing() {
		return null;
	}

	/** Minecart contraptions in {@code ROTATION_LOCKED} mode keep their yaw. */
	public boolean isRotationLocked() {
		return false;
	}

	/** Minecart contraptions in {@code ROTATE_PAUSED} mode do not tick actors while turning. */
	public boolean pausesActorsWhileRotating() {
		return false;
	}

	/** True for minecart contraptions (Create: {@code instanceof MountedContraption}). */
	public boolean isMountedOnCart() {
		return false;
	}

	/** Minecart contraptions pick up the inventories of the cart they ride on. */
	public void addExtraInventories(Entity cart) {
	}

	/** Whether carts may not pick this contraption up again (blocks marked NO_PICKUP). */
	public boolean forbidsPickup() {
		return false;
	}

	// Visual change tracking (replaces Create's ClientContraption invalidation)

	public int getStructureVersion() {
		return structureVersion;
	}

	/**
	 * Entirely reset the shown contraption, rebuilding the display entities.
	 */
	public void resetClientContraption() {
		structureVersion++;
	}

	/**
	 * Invalidate the structure of the shown contraption, triggering a rebuild of the displayed blocks.
	 */
	public void invalidateClientContraptionStructure() {
		structureVersion++;
	}

	/**
	 * Invalidate the children of the shown contraption, triggering a rebuild of all child displays.
	 */
	public void invalidateClientContraptionChildren() {
		structureVersion++;
	}
}
