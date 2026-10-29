package dev.createpolymer.trains.schedule;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.trains.entity.Train;
import dev.createpolymer.trains.graph.DiscoveredPath;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Stand-in for Create's ScheduleRuntime: a train asks it for the next destination and tells it when it arrives. A
 * train without a schedule (the only kind until the schedules come) is idle, so everything here is a no-op.
 */
public class ScheduleRuntime {

	public enum State {
		PRE_TRANSIT, IN_TRANSIT, POST_TRANSIT
	}

	Train train;
	private Schedule schedule;

	public boolean isAutoSchedule;
	public boolean paused;
	public boolean completed;
	public State state;
	public int currentEntry;
	public boolean displayLinkUpdateRequested;

	public ScheduleRuntime(Train train) {
		this.train = train;
		reset();
	}

	public void reset() {
		paused = false;
		completed = false;
		isAutoSchedule = false;
		currentEntry = 0;
		state = State.PRE_TRANSIT;
	}

	public void tick(Level level) {}

	public void transitInterrupted() {}

	public void destinationReached() {}

	/** The path to the destination of the current schedule instruction, null if there is none. */
	@Nullable
	public DiscoveredPath startCurrentInstruction(Level level) {
		return null;
	}

	@Nullable
	public Schedule getSchedule() {
		return schedule;
	}

	public void setSchedule(Schedule schedule, boolean auto) {
		reset();
		this.schedule = schedule;
		this.isAutoSchedule = auto;
		train.status.newSchedule();
	}

	/** Takes the schedule off the train and returns it as an item (empty if the train runs none). */
	public ItemStack returnSchedule(HolderLookup.Provider registries) {
		return ItemStack.EMPTY;
	}

	public CompoundTag write(HolderLookup.Provider registries) {
		return new CompoundTag();
	}

	public void read(HolderLookup.Provider registries, CompoundTag tag) {}

}
