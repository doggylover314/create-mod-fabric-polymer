package dev.createpolymer.trains.schedule.condition;

import java.util.List;

import com.google.common.collect.ImmutableList;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.trains.entity.Train;
import dev.createpolymer.trains.schedule.ScheduleConfigBuilder;
import dev.createpolymer.trains.schedule.ScheduleLang;
import dev.createpolymer.util.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class TimeOfDayCondition extends ScheduleWaitCondition {

	private static final String[] ROTATIONS = { "every_24", "every_12", "every_6", "every_4", "every_3", "every_2",
		"every_1", "every_0_45", "every_0_30", "every_0_15" };

	public TimeOfDayCondition() {
		data.putInt("Hour", 8);
		data.putInt("Rotation", 5);
	}

	@Override
	public boolean tickCompletion(Level level, Train train, CompoundTag context) {
		int maxTickDiff = 40;
		int targetHour = intData("Hour");
		int targetMinute = intData("Minute");
		int dayTime = (int) (level.getDefaultClockTime() % getRotation());
		int targetTicks =
			(int) ((((targetHour + 18) % 24) * 1000 + Math.ceil(targetMinute / 60f * 1000)) % getRotation());
		int diff = dayTime - targetTicks;
		return diff >= 0 && maxTickDiff >= diff;
	}

	public int getRotation() {
		int index = intData("Rotation");
		return switch (index) {
		case 9 -> 250;
		case 8 -> 500;
		case 7 -> 750;
		case 6 -> 1000;
		case 5 -> 2000;
		case 4 -> 3000;
		case 3 -> 4000;
		case 2 -> 6000;
		case 1 -> 12000;
		default -> 24000;
		};
	}

	@Override
	public Pair<ItemStack, Component> getSummary() {
		// Create draws a clock showing the time on top of a structure void
		return Pair.of(new ItemStack(Items.CLOCK), getDigitalDisplay(intData("Hour"), intData("Minute"), false));
	}

	public MutableComponent getDigitalDisplay(int hour, int minute, boolean doubleDigitHrs) {
		int hour12raw = hour % 12 == 0 ? 12 : hour % 12;
		String hr12 = doubleDigitHrs ? twoDigits(hour12raw) : ("" + hour12raw);
		String hr24 = doubleDigitHrs ? twoDigits(hour) : ("" + hour);
		return ScheduleLang.translate("schedule.condition.time_of_day.digital_format", hr12, hr24, twoDigits(minute),
			hour > 11 ? ScheduleLang.translate("generic.daytime.pm") : ScheduleLang.translate("generic.daytime.am"));
	}

	private static List<Component> rotationOptions() {
		return ScheduleLang.translatedOptions("schedule.condition.time_of_day.rotation", ROTATIONS);
	}

	@Override
	public List<Component> getTitleAs(String type) {
		return ImmutableList.of(ScheduleLang.translate("schedule.condition.time_of_day.scheduled"),
			getDigitalDisplay(intData("Hour"), intData("Minute"), false).withStyle(ChatFormatting.DARK_AQUA)
				.append(Component.literal(" -> ").withStyle(ChatFormatting.DARK_GRAY))
				.append(rotationOptions()
					.get(Math.floorMod(intData("Rotation"), ROTATIONS.length))
					.copy()
					.withStyle(ChatFormatting.GRAY)));
	}

	public String twoDigits(int t) {
		return t < 10 ? "0" + t : "" + t;
	}

	@Override
	public Identifier getId() {
		return CreatePolymer.id("time_of_day");
	}

	@Override
	public void initConfigurationWidgets(ScheduleConfigBuilder builder) {
		builder.addScrollInput("Hour", ScheduleLang.translate("generic.daytime.hour"), 0, 24, 1, 6, "");
		builder.addScrollInput("Minute", ScheduleLang.translate("generic.daytime.minute"), 0, 60, 1, 15, "");
		builder.addSelectionScrollInput("Rotation", ScheduleLang.translate("schedule.condition.time_of_day.rotation"),
			rotationOptions());
	}

	@Override
	public MutableComponent getWaitingStatus(Level level, Train train, CompoundTag tag) {
		int targetHour = intData("Hour");
		int targetMinute = intData("Minute");
		int dayTime = (int) (level.getDefaultClockTime() % getRotation());
		int targetTicks =
			(int) ((((targetHour + 18) % 24) * 1000 + Math.ceil(targetMinute / 60f * 1000)) % getRotation());
		int diff = targetTicks - dayTime;

		if (diff < 0)
			diff += getRotation();

		int departureTime = (int) (level.getDefaultClockTime() + diff) % 24000;
		int departingHour = (departureTime / 1000 + 6) % 24;
		int departingMinute = (departureTime % 1000) * 60 / 1000;

		return ScheduleLang.translate("schedule.condition.time_of_day.status")
			.append(getDigitalDisplay(departingHour, departingMinute, false));
	}

}
