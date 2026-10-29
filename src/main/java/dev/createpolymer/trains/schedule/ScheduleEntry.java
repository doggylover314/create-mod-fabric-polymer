package dev.createpolymer.trains.schedule;

import java.util.ArrayList;
import java.util.List;

import dev.createpolymer.trains.schedule.condition.ScheduleWaitCondition;
import dev.createpolymer.trains.schedule.destination.ScheduleInstruction;
import dev.createpolymer.util.NBTHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public class ScheduleEntry {

	public ScheduleInstruction instruction;
	public List<List<ScheduleWaitCondition>> conditions;

	public ScheduleEntry() {
		conditions = new ArrayList<>();
	}

	public ScheduleEntry(ScheduleInstruction instruction, List<List<ScheduleWaitCondition>> conditions) {
		this.instruction = instruction;
		this.conditions = conditions;
	}

	public ScheduleEntry clone(HolderLookup.Provider registries) {
		return fromTag(registries, write(registries));
	}

	public CompoundTag write(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		ListTag outer = new ListTag();
		tag.put("Instruction", instruction.write(registries));
		if (!instruction.supportsConditions())
			return tag;
		for (List<ScheduleWaitCondition> column : conditions)
			outer.add(NBTHelper.writeCompoundList(column, t -> t.write(registries)));
		tag.put("Conditions", outer);
		return tag;
	}

	public static ScheduleEntry fromTag(HolderLookup.Provider registries, CompoundTag tag) {
		ScheduleEntry entry = new ScheduleEntry();
		entry.instruction = ScheduleInstruction.fromTag(registries, tag.getCompoundOrEmpty("Instruction"));
		entry.conditions = new ArrayList<>();
		if (entry.instruction.supportsConditions())
			for (Tag t : tag.getListOrEmpty("Conditions"))
				if (t instanceof ListTag list) {
					// conditions of a type that does not exist (any more) are dropped
					List<ScheduleWaitCondition> column = new ArrayList<>(NBTHelper.readCompoundList(list,
						conditionTag -> ScheduleWaitCondition.fromTag(registries, conditionTag)));
					column.removeIf(java.util.Objects::isNull);
					if (!column.isEmpty())
						entry.conditions.add(column);
				}
		return entry;
	}

}
