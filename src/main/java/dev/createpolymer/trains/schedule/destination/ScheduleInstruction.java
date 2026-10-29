package dev.createpolymer.trains.schedule.destination;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.trains.graph.DiscoveredPath;
import dev.createpolymer.trains.schedule.Schedule;
import dev.createpolymer.trains.schedule.ScheduleDataEntry;
import dev.createpolymer.trains.schedule.ScheduleRuntime;
import dev.createpolymer.util.Pair;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

public abstract class ScheduleInstruction extends ScheduleDataEntry {

	public abstract boolean supportsConditions();

	@Nullable
	public abstract DiscoveredPath start(ScheduleRuntime runtime, Level level);

	public final CompoundTag write(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		CompoundTag dataCopy = data.copy();
		writeAdditional(registries, dataCopy);
		tag.putString("Id", getId().toString());
		tag.put("Data", dataCopy);
		return tag;
	}

	public static ScheduleInstruction fromTag(HolderLookup.Provider registries, CompoundTag tag) {
		Identifier location = Identifier.tryParse(tag.getStringOr("Id", ""));
		Supplier<? extends ScheduleInstruction> supplier = null;
		for (Pair<Identifier, Supplier<? extends ScheduleInstruction>> pair : Schedule.INSTRUCTION_TYPES)
			if (pair.getFirst()
				.equals(location))
				supplier = pair.getSecond();

		if (supplier == null) {
			CreatePolymer.LOGGER.warn("Could not parse schedule instruction type: " + location);
			return new DestinationInstruction();
		}

		ScheduleInstruction scheduleDestination = supplier.get();
		// Left around for migration purposes. Data added in writeAdditional has moved into the "Data" tag
		scheduleDestination.readAdditional(registries, tag);
		// the tag may be the component of an item, which must not change
		CompoundTag data = tag.getCompoundOrEmpty("Data")
			.copy();
		scheduleDestination.readAdditional(registries, data);
		scheduleDestination.data = data;
		return scheduleDestination;
	}

}
