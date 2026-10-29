package dev.createpolymer.trains.schedule;

import java.util.ArrayList;
import java.util.List;

/**
 * Stand-in for Create's Schedule: trains read the entries of the schedule they run. The schedule item, its
 * instructions and conditions come with the schedules.
 */
public class Schedule {

	public List<Object> entries = new ArrayList<>();
	public boolean cyclic = true;
	public int savedProgress;

}
