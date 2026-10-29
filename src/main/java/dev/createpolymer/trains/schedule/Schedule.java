package dev.createpolymer.trains.schedule;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.trains.schedule.condition.FluidThresholdCondition;
import dev.createpolymer.trains.schedule.condition.IdleCargoCondition;
import dev.createpolymer.trains.schedule.condition.ItemThresholdCondition;
import dev.createpolymer.trains.schedule.condition.PlayerPassengerCondition;
import dev.createpolymer.trains.schedule.condition.RedstoneLinkCondition;
import dev.createpolymer.trains.schedule.condition.ScheduleWaitCondition;
import dev.createpolymer.trains.schedule.condition.ScheduledDelay;
import dev.createpolymer.trains.schedule.condition.StationPoweredCondition;
import dev.createpolymer.trains.schedule.condition.StationUnloadedCondition;
import dev.createpolymer.trains.schedule.condition.TimeOfDayCondition;
import dev.createpolymer.trains.schedule.destination.ChangeThrottleInstruction;
import dev.createpolymer.trains.schedule.destination.ChangeTitleInstruction;
import dev.createpolymer.trains.schedule.destination.DeliverPackagesInstruction;
import dev.createpolymer.trains.schedule.destination.DestinationInstruction;
import dev.createpolymer.trains.schedule.destination.FetchPackagesInstruction;
import dev.createpolymer.trains.schedule.destination.ScheduleInstruction;
import dev.createpolymer.util.NBTHelper;
import dev.createpolymer.util.Pair;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class Schedule {

	public static List<Pair<Identifier, Supplier<? extends ScheduleInstruction>>> INSTRUCTION_TYPES =
		new ArrayList<>();
	public static List<Pair<Identifier, Supplier<? extends ScheduleWaitCondition>>> CONDITION_TYPES =
		new ArrayList<>();

	static {
		registerInstruction("destination", DestinationInstruction::new);
		registerInstruction("package_delivery", DeliverPackagesInstruction::new);
		registerInstruction("package_retrieval", FetchPackagesInstruction::new);
		registerInstruction("rename", ChangeTitleInstruction::new);
		registerInstruction("throttle", ChangeThrottleInstruction::new);
		registerCondition("delay", ScheduledDelay::new);
		registerCondition("time_of_day", TimeOfDayCondition::new);
		registerCondition("fluid_threshold", FluidThresholdCondition::new);
		registerCondition("item_threshold", ItemThresholdCondition::new);
		registerCondition("redstone_link", RedstoneLinkCondition::new);
		registerCondition("player_count", PlayerPassengerCondition::new);
		registerCondition("idle", IdleCargoCondition::new);
		registerCondition("unloaded", StationUnloadedCondition::new);
		registerCondition("powered", StationPoweredCondition::new);
	}

	private static void registerInstruction(String name, Supplier<? extends ScheduleInstruction> factory) {
		INSTRUCTION_TYPES.add(Pair.of(CreatePolymer.id(name), factory));
	}

	private static void registerCondition(String name, Supplier<? extends ScheduleWaitCondition> factory) {
		CONDITION_TYPES.add(Pair.of(CreatePolymer.id(name), factory));
	}

	public static <T> List<? extends Component> getTypeOptions(List<Pair<Identifier, T>> list) {
		String langSection = list.equals(INSTRUCTION_TYPES) ? "instruction." : "condition.";
		return list.stream()
			.map(Pair::getFirst)
			.map(rl -> rl.getNamespace() + ".schedule." + langSection + rl.getPath())
			.map(key -> Component.translatable(key))
			.toList();
	}

	public List<ScheduleEntry> entries;
	public boolean cyclic;
	public int savedProgress;

	public Schedule() {
		this(new ArrayList<>(), true, 0);
	}

	public Schedule(List<ScheduleEntry> entries, boolean cyclic, int savedProgress) {
		this.entries = entries;
		this.cyclic = cyclic;
		this.savedProgress = savedProgress;
	}

	public CompoundTag write(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		ListTag list = NBTHelper.writeCompoundList(entries, t -> t.write(registries));
		tag.put("Entries", list);
		tag.putBoolean("Cyclic", cyclic);
		if (savedProgress > 0)
			tag.putInt("Progress", savedProgress);
		return tag;
	}

	public static Schedule fromTag(HolderLookup.Provider registries, CompoundTag tag) {
		Schedule schedule = new Schedule();
		schedule.entries = NBTHelper.readCompoundList(tag.getListOrEmpty("Entries"), t -> ScheduleEntry.fromTag(registries, t));
		schedule.cyclic = tag.getBooleanOr("Cyclic", false);
		if (tag.contains("Progress"))
			schedule.savedProgress = tag.getIntOr("Progress", 0);
		return schedule;
	}

}
