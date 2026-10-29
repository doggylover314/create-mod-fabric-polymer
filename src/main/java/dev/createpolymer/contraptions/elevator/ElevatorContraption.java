package dev.createpolymer.contraptions.elevator;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;

import dev.createpolymer.api.contraption.ContraptionType;
import dev.createpolymer.contraptions.AbstractContraptionEntity;
import dev.createpolymer.contraptions.AssemblyException;
import dev.createpolymer.contraptions.elevator.ElevatorColumn.ColumnCoords;
import dev.createpolymer.contraptions.pulley.PulleyContraption;
import dev.createpolymer.redstone.contact.RedstoneContactBlock;
import dev.createpolymer.registry.feature.PulleyRegistry;
import dev.createpolymer.registry.feature.RedstoneRegistry;
import dev.createpolymer.util.Couple;
import dev.createpolymer.util.IntAttached;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

public class ElevatorContraption extends PulleyContraption {

	protected ColumnCoords column;
	protected int contactYOffset;
	public boolean arrived;

	private int namesListVersion = -1;
	public List<IntAttached<Couple<String>>> namesList = ImmutableList.of();

	public int maxContactY;
	public int minContactY;

	// during assembly only
	private int contacts;

	public ElevatorContraption() {
		super();
	}

	public ElevatorContraption(int initialOffset) {
		super(initialOffset);
	}

	/**
	 * Create sends the compiled floor list to the clients that track the entity; here it is kept for the floor
	 * selection of {@link ElevatorContactGui} and contraption controls.
	 */
	@Override
	public void tickStorage(AbstractContraptionEntity entity) {
		super.tickStorage(entity);

		if (entity.tickCount % 10 != 0)
			return;

		ColumnCoords coords = getGlobalColumn();
		ElevatorColumn column = ElevatorColumn.get(entity.level(), coords);

		if (column == null)
			return;
		if (column.namesListVersion == namesListVersion)
			return;

		namesList = column.compileNamesList();
		namesListVersion = column.namesListVersion;
	}

	public ColumnCoords getGlobalColumn() {
		return column.relative(anchor);
	}

	public Integer getCurrentTargetY(Level level) {
		ColumnCoords coords = getGlobalColumn();
		ElevatorColumn column = ElevatorColumn.get(level, coords);
		if (column == null)
			return null;
		if (!column.isTargetAvailable())
			return null;
		int targetedYLevel = column.getTargetedYLevel();
		if (isTargetUnreachable(targetedYLevel))
			return null;
		return targetedYLevel;
	}

	public boolean isTargetUnreachable(int contactY) {
		return contactY < minContactY || contactY > maxContactY;
	}

	@Override
	public boolean assemble(Level world, BlockPos pos) throws AssemblyException {
		if (!searchMovedStructure(world, pos, null))
			return false;
		if (blocks.size() <= 0)
			return false;
		if (contacts == 0)
			throw new AssemblyException("no_contacts");
		if (contacts > 1)
			throw new AssemblyException("too_many_contacts");
		ElevatorColumn column = ElevatorColumn.get(world, getGlobalColumn());
		if (column != null && column.isActive())
			throw new AssemblyException("column_conflict");
		startMoving(world);
		return true;
	}

	@Override
	protected Pair<StructureBlockInfo, BlockEntity> capture(Level world, BlockPos pos) {
		BlockState blockState = world.getBlockState(pos);

		if (!blockState.is(RedstoneRegistry.REDSTONE_CONTACT))
			return super.capture(world, pos);

		Direction facing = blockState.getValue(RedstoneContactBlock.FACING);
		if (facing.getAxis() == Axis.Y)
			return super.capture(world, pos);

		contacts++;
		BlockPos local = toLocalPos(pos.relative(facing));
		column = new ColumnCoords(local.getX(), local.getZ(), facing.getOpposite());
		contactYOffset = local.getY();

		return super.capture(world, pos);
	}

	public int getContactYOffset() {
		return contactYOffset;
	}

	public void broadcastFloorData(Level level, BlockPos contactPos) {
		ElevatorColumn column = ElevatorColumn.get(level, getGlobalColumn());
		if (!(level.getBlockEntity(contactPos) instanceof ElevatorContactBlockEntity ecbe))
			return;
		if (column != null)
			column.floorReached(level, ecbe.shortName);
	}

	@Override
	public CompoundTag writeNBT(HolderLookup.Provider registries, boolean spawnPacket) {
		CompoundTag tag = super.writeNBT(registries, spawnPacket);
		tag.putBoolean("Arrived", arrived);
		tag.put("Column", column.write());
		tag.putInt("ContactY", contactYOffset);
		tag.putInt("MaxContactY", maxContactY);
		tag.putInt("MinContactY", minContactY);
		return tag;
	}

	@Override
	public void readNBT(Level world, CompoundTag nbt, boolean spawnData) {
		arrived = nbt.getBooleanOr("Arrived", false);
		column = ColumnCoords.read(nbt.getCompoundOrEmpty("Column"));
		contactYOffset = nbt.getIntOr("ContactY", 0);
		maxContactY = nbt.getIntOr("MaxContactY", 0);
		minContactY = nbt.getIntOr("MinContactY", 0);
		super.readNBT(world, nbt, spawnData);
	}

	@Override
	public ContraptionType getType() {
		return PulleyRegistry.ELEVATOR;
	}

}
