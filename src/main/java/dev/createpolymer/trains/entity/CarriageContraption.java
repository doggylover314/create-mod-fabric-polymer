package dev.createpolymer.trains.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import dev.createpolymer.api.behaviour.interaction.ConductorBlockInteractionBehavior;
import dev.createpolymer.api.behaviour.interaction.MovingInteractionBehaviour;
import dev.createpolymer.api.contraption.ContraptionType;
import dev.createpolymer.contraptions.AssemblyException;
import dev.createpolymer.contraptions.Contraption;
import dev.createpolymer.contraptions.MountedStorageManager;
import dev.createpolymer.contraptions.actors.trainControls.ControlsBlock;
import dev.createpolymer.contraptions.minecart.TrainCargoManager;
import dev.createpolymer.foundation.collision.CollisionList;
import dev.createpolymer.registry.feature.TrainsRegistry;
import dev.createpolymer.trains.TrackNbt;
import dev.createpolymer.trains.bogey.AbstractBogeyBlock;
import dev.createpolymer.util.Couple;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

public class CarriageContraption extends Contraption {

	private Direction assemblyDirection;
	private boolean forwardControls;
	private boolean backwardControls;

	public Couple<Boolean> blockConductors;
	public Map<BlockPos, Couple<Boolean>> conductorSeats;

	protected MountedStorageManager storageProxy;

	// during assembly only
	private int bogeys;
	private boolean sidewaysControls;
	private BlockPos secondBogeyPos;
	private List<BlockPos> assembledBlockConductors;

	// the part of the carriage that has entered a portal
	public int portalCutoffMin;
	public int portalCutoffMax;

	static final MountedStorageManager fallbackStorage;

	static {
		fallbackStorage = new MountedStorageManager();
		fallbackStorage.initialize();
	}

	public CarriageContraption() {
		conductorSeats = new HashMap<>();
		assembledBlockConductors = new ArrayList<>();
		blockConductors = Couple.create(false, false);
		portalCutoffMin = Integer.MIN_VALUE;
		portalCutoffMax = Integer.MAX_VALUE;
		storage = new TrainCargoManager();
	}

	public CarriageContraption(Direction assemblyDirection) {
		this();
		this.assemblyDirection = assemblyDirection;
		this.bogeys = 0;
	}

	@Override
	public boolean assemble(Level world, BlockPos pos) throws AssemblyException {
		if (!searchMovedStructure(world, pos, null))
			return false;
		if (blocks.size() <= 1)
			return false;
		if (bogeys == 0)
			return false;
		if (bogeys > 2)
			throw new AssemblyException(TrackNbt.translateDirect("train_assembly.too_many_bogeys", bogeys));
		if (sidewaysControls)
			throw new AssemblyException(TrackNbt.translateDirect("train_assembly.sideways_controls"));

		for (BlockPos blazePos : assembledBlockConductors)
			for (Direction direction : Iterate.directionsInAxis(assemblyDirection.getAxis()))
				if (inControl(blazePos, direction))
					blockConductors.set(direction != assemblyDirection, true);
		for (BlockPos seatPos : getSeats())
			for (Direction direction : Iterate.directionsInAxis(assemblyDirection.getAxis()))
				if (inControl(seatPos, direction))
					conductorSeats.computeIfAbsent(seatPos, p -> Couple.create(false, false))
						.set(direction != assemblyDirection, true);

		return true;
	}

	public boolean inControl(BlockPos pos, Direction direction) {
		BlockPos controlsPos = pos.relative(direction);
		if (!blocks.containsKey(controlsPos))
			return false;
		StructureBlockInfo info = blocks.get(controlsPos);
		if (!info.state()
			.is(TrainsRegistry.CONTROLS))
			return false;
		return info.state()
			.getValue(ControlsBlock.FACING) == direction.getOpposite();
	}

	public void swapStorageAfterAssembly(CarriageContraptionEntity cce) {
		// Ensure that the entity does not hold its inventory data, because the global
		// carriage manages it instead
		Carriage carriage = cce.getCarriage();
		if (carriage.storage == null) {
			carriage.storage = (TrainCargoManager) storage;
			storage = new MountedStorageManager();
		}
		storageProxy = carriage.storage;
	}

	public void returnStorageForDisassembly(MountedStorageManager storage) {
		this.storage = storage;
	}

	@Override
	protected boolean isAnchoringBlockAt(BlockPos pos) {
		return false;
	}

	@Override
	protected Pair<StructureBlockInfo, BlockEntity> capture(Level world, BlockPos pos) {
		BlockState blockState = world.getBlockState(pos);

		if (blockState.getBlock() instanceof AbstractBogeyBlock<?>) {
			bogeys++;
			if (bogeys == 2)
				secondBogeyPos = pos;
		}

		MovingInteractionBehaviour behaviour = MovingInteractionBehaviour.REGISTRY.get(blockState);
		if (behaviour instanceof ConductorBlockInteractionBehavior conductor && conductor.isValidConductor(blockState)) {
			assembledBlockConductors.add(toLocalPos(pos));
		}

		if (blockState.is(TrainsRegistry.CONTROLS)) {
			Direction facing = blockState.getValue(ControlsBlock.FACING);
			if (facing.getAxis() != assemblyDirection.getAxis())
				sidewaysControls = true;
			else {
				boolean forwards = facing == assemblyDirection;
				if (forwards)
					forwardControls = true;
				else
					backwardControls = true;
			}
		}

		return super.capture(world, pos);
	}

	@Override
	public CompoundTag writeNBT(HolderLookup.Provider registries, boolean spawnPacket) {
		CompoundTag tag = super.writeNBT(registries, spawnPacket);
		NBTHelper.writeEnum(tag, "AssemblyDirection", getAssemblyDirection());
		tag.putBoolean("FrontControls", forwardControls);
		tag.putBoolean("BackControls", backwardControls);
		tag.putBoolean("FrontBlazeConductor", blockConductors.getFirst());
		tag.putBoolean("BackBlazeConductor", blockConductors.getSecond());
		ListTag list = NBTHelper.writeCompoundList(conductorSeats.entrySet(), e -> {
			CompoundTag compoundTag = new CompoundTag();
			compoundTag.store("Pos", BlockPos.CODEC, e.getKey());
			compoundTag.putBoolean("Forward", e.getValue()
				.getFirst());
			compoundTag.putBoolean("Backward", e.getValue()
				.getSecond());
			return compoundTag;
		});
		tag.put("ConductorSeats", list);
		return tag;
	}

	@Override
	public void readNBT(Level world, CompoundTag nbt, boolean spawnData) {
		assemblyDirection = NBTHelper.readEnum(nbt, "AssemblyDirection", Direction.class);
		forwardControls = nbt.getBooleanOr("FrontControls", false);
		backwardControls = nbt.getBooleanOr("BackControls", false);
		blockConductors = Couple.create(nbt.getBooleanOr("FrontBlazeConductor", false),
			nbt.getBooleanOr("BackBlazeConductor", false));
		conductorSeats.clear();
		NBTHelper.iterateCompoundList(nbt.getListOrEmpty("ConductorSeats"),
			c -> conductorSeats.put(c.read("Pos", BlockPos.CODEC)
				.orElse(BlockPos.ZERO),
				Couple.create(c.getBooleanOr("Forward", false), c.getBooleanOr("Backward", false))));
		super.readNBT(world, nbt, spawnData);
	}

	@Override
	public boolean canBeStabilized(Direction facing, BlockPos localPos) {
		return false;
	}

	@Override
	public ContraptionType getType() {
		return TrainsRegistry.CARRIAGE_CONTRAPTION_TYPE;
	}

	public Direction getAssemblyDirection() {
		return assemblyDirection;
	}

	public boolean hasForwardControls() {
		return forwardControls;
	}

	public boolean hasBackwardControls() {
		return backwardControls;
	}

	public BlockPos getSecondBogeyPos() {
		return secondBogeyPos;
	}

	@Override
	@Nullable
	public CollisionList getSimplifiedEntityColliders() {
		if (notInPortal())
			return super.getSimplifiedEntityColliders();
		return null;
	}

	@Override
	public boolean isHiddenInPortal(BlockPos localPos) {
		if (notInPortal())
			return super.isHiddenInPortal(localPos);
		Direction facing = assemblyDirection;
		Axis axis = facing.getClockWise()
			.getAxis();
		int coord = axis.choose(localPos.getZ(), localPos.getY(), localPos.getX()) * -facing.getAxisDirection()
			.getStep();
		return !withinVisible(coord) || atSeam(coord);
	}

	public boolean isHiddenInPortal(int posAlongMovementAxis) {
		if (notInPortal())
			return false;
		return !withinVisible(posAlongMovementAxis) || atSeam(posAlongMovementAxis);
	}

	public boolean notInPortal() {
		return portalCutoffMin == Integer.MIN_VALUE && portalCutoffMax == Integer.MAX_VALUE;
	}

	public boolean atSeam(BlockPos localPos) {
		Direction facing = assemblyDirection;
		Axis axis = facing.getClockWise()
			.getAxis();
		int coord = axis.choose(localPos.getZ(), localPos.getY(), localPos.getX()) * -facing.getAxisDirection()
			.getStep();
		return atSeam(coord);
	}

	public boolean withinVisible(BlockPos localPos) {
		Direction facing = assemblyDirection;
		Axis axis = facing.getClockWise()
			.getAxis();
		int coord = axis.choose(localPos.getZ(), localPos.getY(), localPos.getX()) * -facing.getAxisDirection()
			.getStep();
		return withinVisible(coord);
	}

	public boolean atSeam(int posAlongMovementAxis) {
		return posAlongMovementAxis == portalCutoffMin || posAlongMovementAxis == portalCutoffMax;
	}

	public boolean withinVisible(int posAlongMovementAxis) {
		return posAlongMovementAxis > portalCutoffMin && posAlongMovementAxis < portalCutoffMax;
	}

	@Override
	public MountedStorageManager getStorage() {
		return storageProxy == null ? fallbackStorage : storageProxy;
	}

	@Override
	public void writeStorage(CompoundTag nbt, HolderLookup.Provider registries, boolean spawnPacket) {
		if (!spawnPacket)
			return;
		if (storageProxy != null)
			storageProxy.write(nbt, registries, spawnPacket);
	}

}
