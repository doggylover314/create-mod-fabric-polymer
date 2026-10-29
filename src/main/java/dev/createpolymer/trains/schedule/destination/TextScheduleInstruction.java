package dev.createpolymer.trains.schedule.destination;

import java.util.List;

import com.google.common.collect.ImmutableList;

import dev.createpolymer.trains.schedule.ScheduleConfigBuilder;
import dev.createpolymer.trains.schedule.ScheduleLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public abstract class TextScheduleInstruction extends ScheduleInstruction {

	protected String getLabelText() {
		return textData("Text");
	}

	@Override
	public List<Component> getTitleAs(String type) {
		return ImmutableList.of(ScheduleLang.translate("schedule." + type + "." + getId().getPath() + ".summary")
			.withStyle(ChatFormatting.GOLD), ScheduleLang.translate("generic.in_quotes", Component.literal(getLabelText())));
	}

	/** The wildcards the text may contain, 3 for the filters of Create; negative for no limit. */
	protected int maxWildcards() {
		return -1;
	}

	/** What the text is called in the editor, and the lines that explain it. */
	protected Component textTitle() {
		return ScheduleLang.translate("schedule.instruction.name_edit_box");
	}

	protected List<Component> textHint() {
		return List.of();
	}

	@Override
	public void initConfigurationWidgets(ScheduleConfigBuilder builder) {
		builder.addTextInput("Text", textTitle(), textHint(), maxWildcards());
	}

}
