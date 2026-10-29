package dev.createpolymer.trains.schedule;

import java.util.ArrayList;
import java.util.List;

import dev.createpolymer.trains.schedule.condition.ScheduleWaitCondition;
import dev.createpolymer.trains.schedule.condition.ScheduledDelay;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;

/**
 * Chest GUI for what a train waits for at a stop. Every row is a group of conditions that are met one after the other;
 * the train leaves as soon as any one group is complete (Create shows the groups as columns). Click a condition to
 * edit it, right-click to remove it; the green button adds a condition to the group and the button below adds a group.
 */
public class ScheduleConditionsGui extends SimpleGui {

	private static final int GROUPS = 5, PER_GROUP = 6;
	private static final int ADD_CONDITION = 7, REMOVE_GROUP = 8;
	private static final int BACK = 45, ADD_GROUP = 49, CLOSE = 53;

	private final ScheduleEditor editor;
	private final ScheduleEntry entry;

	public ScheduleConditionsGui(ScheduleEditor editor, ScheduleEntry entry) {
		super(MenuType.GENERIC_9x6, editor.player, false);
		this.editor = editor;
		this.entry = entry;
		setLockPlayerInventory(true);
		setTitle(ScheduleLang.translate("schedule.condition.editor"));
		refresh();
	}

	@Override
	public boolean stillValid() {
		return editor.stillValid();
	}

	private void changed() {
		editor.save();
		refresh();
	}

	private void refresh() {
		for (int i = 0; i < 54; i++)
			clearSlot(i);

		List<List<ScheduleWaitCondition>> groups = entry.conditions;
		for (int row = 0; row < GROUPS && row < groups.size(); row++) {
			List<ScheduleWaitCondition> group = groups.get(row);
			int base = row * 9;

			setSlot(base, new GuiElementBuilder(row == 0 ? Items.CLOCK : Items.HOPPER)
				.setName((row == 0 ? ScheduleLang.translate("schedule.condition_type")
					: ScheduleLang.translate("gui.schedule.alternative_condition")).withStyle(ChatFormatting.GOLD))
				.hideDefaultTooltip());

			for (int i = 0; i < group.size() && i < PER_GROUP; i++) {
				ScheduleWaitCondition condition = group.get(i);
				boolean canRemove = group.size() > 1 || groups.size() > 1;
				GuiElementBuilder button = ScheduleEditor.describe(condition, "condition", ScheduleEditor.iconOf(condition));
				button.addLoreLine(Component.empty());
				button.addLoreLine(ScheduleLang.translate("gui.schedule.lmb_edit")
					.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
				if (canRemove)
					button.addLoreLine(ScheduleLang.translate("gui.schedule.rmb_remove")
						.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
				button.setCallback(click -> {
					if (click.isRight && canRemove) {
						group.remove(condition);
						if (group.isEmpty())
							groups.remove(group);
						changed();
					} else if (click.isLeft)
						ScheduleInputGui.forCondition(editor, entry, group, condition)
							.open();
				});
				setSlot(base + 1 + i, button);
			}

			if (group.size() < PER_GROUP)
				setSlot(base + ADD_CONDITION, new GuiElementBuilder(Items.DYE.pick(DyeColor.LIME))
					.setName(ScheduleLang.translate("gui.schedule.add_condition")
						.withStyle(ChatFormatting.GREEN))
					.hideDefaultTooltip()
					.setCallback(() -> {
						ScheduleWaitCondition added = new ScheduledDelay();
						group.add(added);
						editor.save();
						ScheduleInputGui.forCondition(editor, entry, group, added)
							.open();
					}));
			if (groups.size() > 1)
				setSlot(base + REMOVE_GROUP, new GuiElementBuilder(Items.BARRIER)
					.setName(ScheduleLang.text("gui.schedule.remove_group", "Remove this group")
						.withStyle(ChatFormatting.RED))
					.hideDefaultTooltip()
					.setCallback(() -> {
						groups.remove(group);
						changed();
					}));
		}

		setSlot(BACK, new GuiElementBuilder(Items.ARROW)
			.setName(Component.translatable("create.gui.value_settings.confirm"))
			.hideDefaultTooltip()
			.setCallback(editor::openList));
		if (groups.size() < GROUPS)
			setSlot(ADD_GROUP, new GuiElementBuilder(Items.EMERALD)
				.setName(ScheduleLang.translate("gui.schedule.alternative_condition")
					.withStyle(ChatFormatting.GREEN))
				.hideDefaultTooltip()
				.setCallback(() -> {
					List<ScheduleWaitCondition> group = new ArrayList<>();
					ScheduleWaitCondition added = new ScheduledDelay();
					group.add(added);
					groups.add(group);
					editor.save();
					ScheduleInputGui.forCondition(editor, entry, group, added)
						.open();
				}));
		setSlot(CLOSE, new GuiElementBuilder(Items.EMERALD_BLOCK)
			.setName(Component.translatable("create.gui.value_settings.confirm"))
			.hideDefaultTooltip()
			.setCallback(() -> close()));
	}
}
