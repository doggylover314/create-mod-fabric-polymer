package dev.createpolymer.contraptions.elevator;

import java.util.List;

import dev.createpolymer.contraptions.elevator.ElevatorColumn.ColumnCoords;
import dev.createpolymer.decoration.slidingDoor.DoorControlBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.util.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class ElevatorContactBlockEntity extends SmartBlockEntity {

	public ColumnCoords columnCoords;
	public boolean activateBlock;

	public String shortName;
	public String longName;

	public String lastReportedCurrentFloor = "";

	private int yTargetFromNBT = Integer.MIN_VALUE;
	private boolean deferNameGenerator;

	public DoorControlBehaviour doorControls;

	public ElevatorContactBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		shortName = "";
		longName = "";
		deferNameGenerator = false;
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		behaviours.add(doorControls = new DoorControlBehaviour(this));
	}

	@Override
	protected void write(ValueOutput tag) {
		super.write(tag);

		tag.putString("ShortName", shortName);
		tag.putString("LongName", longName);

		if (lastReportedCurrentFloor != null)
			tag.putString("LastReportedCurrentFloor", lastReportedCurrentFloor);

		tag.putBoolean("Activate", activateBlock);
		if (columnCoords == null)
			return;

		ElevatorColumn column = ElevatorColumn.get(level, columnCoords);
		if (column == null)
			return;
		tag.putInt("ColumnTarget", column.getTargetedYLevel());
		if (column.isActive())
			tag.putBoolean("ColumnActive", true);
	}

	@Override
	protected void read(ValueInput tag) {
		super.read(tag);

		shortName = tag.getStringOr("ShortName", "");
		longName = tag.getStringOr("LongName", "");

		tag.getString("LastReportedCurrentFloor")
			.ifPresent(floor -> lastReportedCurrentFloor = floor);

		activateBlock = tag.getBooleanOr("Activate", false);
		if (tag.getInt("ColumnTarget")
			.isEmpty())
			return;

		int target = tag.getIntOr("ColumnTarget", 0);
		boolean active = tag.getBooleanOr("ColumnActive", false);

		if (columnCoords == null) {
			yTargetFromNBT = target;
			return;
		}

		ElevatorColumn column = ElevatorColumn.getOrCreate(level, columnCoords);
		column.target(target);
		column.setActive(active);
	}

	public void updateDisplayedFloor(String floor) {
		if (floor.equals(lastReportedCurrentFloor))
			return;
		lastReportedCurrentFloor = floor;
		// Create notifies the display links gathering from this contact here
	}

	@Override
	public void initialize() {
		super.initialize();
		columnCoords = ElevatorContactBlock.getColumnCoords(level, worldPosition);
		if (columnCoords == null)
			return;
		ElevatorColumn column = ElevatorColumn.getOrCreate(level, columnCoords);
		column.add(worldPosition);
		if (shortName.isBlank())
			deferNameGenerator = true;
		if (yTargetFromNBT == Integer.MIN_VALUE)
			return;
		column.target(yTargetFromNBT);
		yTargetFromNBT = Integer.MIN_VALUE;
	}

	@Override
	public void tick() {
		super.tick();
		if (!deferNameGenerator)
			return;
		if (columnCoords != null)
			ElevatorColumn.getOrCreate(level, columnCoords)
				.initNames(level);
		deferNameGenerator = false;
	}

	@Override
	public void invalidate() {
		if (columnCoords != null) {
			ElevatorColumn column = ElevatorColumn.get(level, columnCoords);
			if (column != null)
				column.remove(worldPosition);
		}
		super.invalidate();
	}

	public void updateName(String shortName, String longName) {
		this.shortName = shortName;
		this.longName = longName;
		this.deferNameGenerator = false;
		notifyUpdate();

		ElevatorColumn column = ElevatorColumn.get(level, columnCoords);
		if (column != null)
			column.namesChanged();
	}

	public Couple<String> getNames() {
		return Couple.create(shortName, longName);
	}

}
