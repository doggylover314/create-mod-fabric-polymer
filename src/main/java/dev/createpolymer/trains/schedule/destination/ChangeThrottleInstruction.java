package dev.createpolymer.trains.schedule.destination;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.ImmutableList;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.registry.feature.TrainsRegistry;
import dev.createpolymer.trains.graph.DiscoveredPath;
import dev.createpolymer.trains.schedule.ScheduleConfigBuilder;
import dev.createpolymer.trains.schedule.ScheduleLang;
import dev.createpolymer.trains.schedule.ScheduleRuntime;
import dev.createpolymer.trains.schedule.ScheduleRuntime.State;
import dev.createpolymer.util.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ChangeThrottleInstruction extends ScheduleInstruction {

	public ChangeThrottleInstruction() {
		super();
		data.putInt("Value", 100);
	}

	@Override
	public Pair<ItemStack, Component> getSummary() {
		return Pair.of(icon(), formatted());
	}

	private MutableComponent formatted() {
		return Component.literal(intData("Value") + "%");
	}

	@Override
	public Identifier getId() {
		return CreatePolymer.id("throttle");
	}

	@Override
	public ItemStack getSecondLineIcon() {
		return icon();
	}

	@Override
	public boolean supportsConditions() {
		return false;
	}

	@Override
	public List<Component> getTitleAs(String type) {
		return ImmutableList.of(ScheduleLang
			.translate("schedule." + type + "." + getId().getPath() + ".summary",
				formatted().withStyle(ChatFormatting.WHITE))
			.withStyle(ChatFormatting.GOLD));
	}

	@Override
	public void initConfigurationWidgets(ScheduleConfigBuilder builder) {
		builder.addScrollInput("Value", ScheduleLang.translate("schedule.instruction.throttle_edit_box"), 5, 101, 5, 25,
			"%");
	}

	public float getThrottle() {
		return intData("Value") / 100f;
	}

	private ItemStack icon() {
		return new ItemStack(TrainsRegistry.CONTROLS_ITEM);
	}

	@Override
	public List<Component> getSecondLineTooltip(int slot) {
		return ImmutableList.of(ScheduleLang.translate("schedule.instruction.throttle_edit_box"),
			ScheduleLang.translate("schedule.instruction.throttle_edit_box_1")
				.withStyle(ChatFormatting.GRAY));
	}

	@Override
	@Nullable
	public DiscoveredPath start(ScheduleRuntime runtime, Level level) {
		runtime.train.throttle = getThrottle();
		runtime.state = State.PRE_TRANSIT;
		runtime.currentEntry++;
		return null;
	}

}
