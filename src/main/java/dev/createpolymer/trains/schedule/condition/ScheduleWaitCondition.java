package dev.createpolymer.trains.schedule.condition;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.trains.entity.Train;
import dev.createpolymer.trains.schedule.Schedule;
import dev.createpolymer.trains.schedule.ScheduleDataEntry;
import dev.createpolymer.util.Pair;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

public abstract class ScheduleWaitCondition extends ScheduleDataEntry {

	public abstract boolean tickCompletion(Level level, Train train, CompoundTag context);

	protected void requestStatusToUpdate(CompoundTag context) {
		context.putInt("StatusVersion", context.getIntOr("StatusVersion", 0) + 1);
	}

	public final CompoundTag write(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		CompoundTag dataCopy = data.copy();
		writeAdditional(registries, dataCopy);
		tag.putString("Id", getId().toString());
		tag.put("Data", dataCopy);
		return tag;
	}

	@Nullable
	public static ScheduleWaitCondition fromTag(HolderLookup.Provider registries, CompoundTag tag) {
		Identifier location = Identifier.tryParse(tag.getStringOr("Id", ""));
		Supplier<? extends ScheduleWaitCondition> supplier = null;
		for (Pair<Identifier, Supplier<? extends ScheduleWaitCondition>> pair : Schedule.CONDITION_TYPES)
			if (pair.getFirst()
				.equals(location))
				supplier = pair.getSecond();

		if (supplier == null) {
			CreatePolymer.LOGGER.warn("Could not parse waiting condition type: " + location);
			return null;
		}

		ScheduleWaitCondition condition = supplier.get();
		// Left around for migration purposes. Data added in writeAdditional has moved into the "Data" tag
		condition.readAdditional(registries, tag);
		// the tag may be the component of an item, which must not change
		CompoundTag data = tag.getCompoundOrEmpty("Data")
			.copy();
		condition.readAdditional(registries, data);
		condition.data = data;
		return condition;
	}

	public abstract MutableComponent getWaitingStatus(Level level, Train train, CompoundTag tag);

}
