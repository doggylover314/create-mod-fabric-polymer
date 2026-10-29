package dev.createpolymer.trains.schedule.destination;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.ImmutableList;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.trains.graph.DiscoveredPath;
import dev.createpolymer.trains.schedule.ScheduleLang;
import dev.createpolymer.trains.schedule.ScheduleRuntime;
import dev.createpolymer.trains.schedule.ScheduleRuntime.State;
import dev.createpolymer.util.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class ChangeTitleInstruction extends TextScheduleInstruction {

	@Override
	public Pair<ItemStack, Component> getSummary() {
		return Pair.of(icon(), Component.literal(getLabelText()));
	}

	@Override
	public Identifier getId() {
		return CreatePolymer.id("rename");
	}

	@Override
	public ItemStack getSecondLineIcon() {
		return icon();
	}

	@Override
	public boolean supportsConditions() {
		return false;
	}

	public String getScheduleTitle() {
		return getLabelText();
	}

	private ItemStack icon() {
		return new ItemStack(Items.NAME_TAG);
	}

	@Override
	public List<Component> getSecondLineTooltip(int slot) {
		return ImmutableList.of(ScheduleLang.translate("schedule.instruction.name_edit_box"),
			ScheduleLang.translate("schedule.instruction.name_edit_box_1")
				.withStyle(ChatFormatting.GRAY),
			ScheduleLang.translate("schedule.instruction.name_edit_box_2")
				.withStyle(ChatFormatting.DARK_GRAY));
	}

	@Override
	protected List<Component> textHint() {
		return getSecondLineTooltip(0).subList(1, 3);
	}

	@Override
	@Nullable
	public DiscoveredPath start(ScheduleRuntime runtime, Level level) {
		runtime.currentTitle = getScheduleTitle();
		runtime.state = State.PRE_TRANSIT;
		runtime.currentEntry++;
		return null;
	}

}
