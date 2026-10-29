package dev.createpolymer.trains.schedule.condition;

import java.util.List;

import com.google.common.collect.ImmutableList;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.registry.feature.ActorsRegistry;
import dev.createpolymer.trains.entity.Train;
import dev.createpolymer.trains.schedule.ScheduleConfigBuilder;
import dev.createpolymer.trains.schedule.ScheduleLang;
import dev.createpolymer.util.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class PlayerPassengerCondition extends ScheduleWaitCondition {

	@Override
	public Pair<ItemStack, Component> getSummary() {
		int target = getTarget();
		return Pair.of(new ItemStack(ActorsRegistry.SEAT_ITEMS.get(DyeColor.YELLOW)),
			ScheduleLang.translate("schedule.condition.player_count." + (target == 1 ? "summary" : "summary_plural"), target));
	}

	@Override
	public Identifier getId() {
		return CreatePolymer.id("player_count");
	}

	public int getTarget() {
		return intData("Count");
	}

	public boolean canOvershoot() {
		return intData("Exact") != 0;
	}

	@Override
	public List<Component> getTitleAs(String type) {
		int target = getTarget();
		return ImmutableList.of(ScheduleLang.translate("schedule.condition.player_count.seated",
			ScheduleLang.translate("schedule.condition.player_count." + (target == 1 ? "summary" : "summary_plural"),
				Component.literal("" + target).withStyle(ChatFormatting.DARK_AQUA))));
	}

	@Override
	public void initConfigurationWidgets(ScheduleConfigBuilder builder) {
		builder.addScrollInput("Count", ScheduleLang.translate("schedule.condition.player_count.players"), 0, 21, 1, 5,
			"");
		builder.addSelectionScrollInput("Exact", ScheduleLang.translate("schedule.condition.player_count.condition"),
			ScheduleLang.translatedOptions("schedule.condition.player_count", "exactly", "or_above"));
	}

	@Override
	public boolean tickCompletion(Level level, Train train, CompoundTag context) {
		int prev = context.getIntOr("PrevPlayerCount", 0);
		int present = train.countPlayerPassengers();
		int target = getTarget();
		context.putInt("PrevPlayerCount", present);
		if (prev != present)
			requestStatusToUpdate(context);
		return canOvershoot() ? present >= target : present == target;
	}

	@Override
	public MutableComponent getWaitingStatus(Level level, Train train, CompoundTag tag) {
		return ScheduleLang.translate("schedule.condition.player_count.status", train.countPlayerPassengers(), getTarget());
	}

}
