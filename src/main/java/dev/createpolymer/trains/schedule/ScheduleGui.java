package dev.createpolymer.trains.schedule;

import java.util.List;

import dev.createpolymer.trains.schedule.condition.ScheduleWaitCondition;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Chest GUI that replaces Create's ScheduleScreen: the list of the stops of the schedule on an item, five to a page.
 * Every stop is a row: the instruction on the left (click to edit), what the train waits for there in the middle (click
 * to edit the conditions), and the buttons to copy, move and remove it on the right. Below are the page buttons, the
 * buttons to add a stop, to loop the schedule and to change the stop the train starts with.
 */
public class ScheduleGui extends SimpleGui {

	private static final int ROWS = 5;
	private static final int CONDITION_COLUMNS = 4;
	private static final int DUPLICATE = 5, UP = 6, DOWN = 7, REMOVE = 8;
	private static final int PREVIOUS = 45, PAGE = 46, NEXT = 47, ADD = 49, LOOP = 50, RESET = 51, SKIP = 52, CLOSE = 53;

	private final ScheduleEditor editor;
	private final Schedule schedule;

	public static void open(ServerPlayer player, ItemStack stack) {
		new ScheduleEditor(player, stack).openList();
	}

	public ScheduleGui(ScheduleEditor editor) {
		super(MenuType.GENERIC_9x6, editor.player, false);
		this.editor = editor;
		this.schedule = editor.schedule;
		setLockPlayerInventory(true);
		setTitle(editor.stack.getItemName());
		refresh();
	}

	@Override
	public boolean stillValid() {
		return editor.stillValid();
	}

	private int pageCount() {
		return Math.max(1, (schedule.entries.size() + ROWS - 1) / ROWS);
	}

	private void changed() {
		editor.save();
		refresh();
	}

	private void refresh() {
		for (int i = 0; i < 54; i++)
			clearSlot(i);

		editor.page = Math.clamp(editor.page, 0, pageCount() - 1);
		for (int row = 0; row < ROWS; row++) {
			int index = editor.page * ROWS + row;
			if (index < schedule.entries.size())
				showEntry(row, index);
		}

		boolean progress = !schedule.entries.isEmpty();
		setSlot(PREVIOUS, new GuiElementBuilder(Items.ARROW)
			.setName(Component.translatableWithFallback("spectatorMenu.previous_page", "Previous Page"))
			.hideDefaultTooltip()
			.setCallback(() -> {
				editor.page--;
				refresh();
			}));
		setSlot(PAGE, new GuiElementBuilder(Items.PAPER)
			.setName(ScheduleLang.text("schedule.page", "Page %1$s of %2$s", editor.page + 1, pageCount())
				.withStyle(ChatFormatting.GRAY))
			.hideDefaultTooltip());
		setSlot(NEXT, new GuiElementBuilder(Items.ARROW)
			.setName(Component.translatableWithFallback("spectatorMenu.next_page", "Next Page"))
			.hideDefaultTooltip()
			.setCallback(() -> {
				editor.page++;
				refresh();
			}));
		if (editor.page == 0)
			clearSlot(PREVIOUS);
		if (editor.page >= pageCount() - 1)
			clearSlot(NEXT);

		setSlot(ADD, new GuiElementBuilder(Items.EMERALD)
			.setName(ScheduleLang.translate("gui.schedule.add_entry")
				.withStyle(ChatFormatting.GREEN))
			.hideDefaultTooltip()
			.setCallback(this::addEntry));

		GuiElementBuilder loop = new GuiElementBuilder(Items.REPEATER)
			.setName(ScheduleLang.translate("schedule.loop")
				.withStyle(schedule.cyclic ? ChatFormatting.GREEN : ChatFormatting.GRAY))
			.addLoreLine(ScheduleLang.translate("schedule.loop1")
				.withStyle(ChatFormatting.GRAY))
			.addLoreLine(ScheduleLang.translate("schedule.loop2")
				.withStyle(ChatFormatting.GRAY))
			.glow(schedule.cyclic)
			.hideDefaultTooltip()
			.setCallback(() -> {
				schedule.cyclic = !schedule.cyclic;
				changed();
			});
		setSlot(LOOP, loop);

		if (progress && schedule.savedProgress > 0)
			setSlot(RESET, new GuiElementBuilder(Items.DYE.pick(DyeColor.RED))
				.setName(ScheduleLang.translate("schedule.reset")
					.withStyle(ChatFormatting.RED))
				.hideDefaultTooltip()
				.setCallback(() -> {
					schedule.savedProgress = 0;
					changed();
				}));
		if (schedule.entries.size() > 1)
			setSlot(SKIP, new GuiElementBuilder(Items.DYE.pick(DyeColor.ORANGE))
				.setName(ScheduleLang.translate("schedule.skip")
					.withStyle(ChatFormatting.GOLD))
				.hideDefaultTooltip()
				.setCallback(() -> {
					schedule.savedProgress = (schedule.savedProgress + 1) % schedule.entries.size();
					editor.page = schedule.savedProgress / ROWS;
					changed();
				}));

		setSlot(CLOSE, new GuiElementBuilder(Items.EMERALD_BLOCK)
			.setName(Component.translatable("create.gui.value_settings.confirm"))
			.hideDefaultTooltip()
			.setCallback(() -> close()));
	}

	private void showEntry(int row, int index) {
		ScheduleEntry entry = schedule.entries.get(index);
		int base = row * 9;
		boolean current = index == schedule.savedProgress && schedule.entries.size() > 1;

		GuiElementBuilder instruction = ScheduleEditor.describe(entry.instruction, "instruction",
			ScheduleEditor.iconOf(entry.instruction));
		if (current) {
			instruction.addLoreLine(ScheduleLang.text("schedule.progress", "The train starts with this stop")
				.withStyle(ChatFormatting.YELLOW));
			instruction.glow();
		}
		instruction.addLoreLine(Component.empty());
		instruction.addLoreLine(ScheduleLang.translate("gui.schedule.lmb_edit")
			.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		instruction.setCallback(() -> ScheduleInputGui.forInstruction(editor, entry).open());
		setSlot(base, instruction);

		if (entry.instruction.supportsConditions()) {
			List<List<ScheduleWaitCondition>> columns = entry.conditions;
			for (int c = 0; c < CONDITION_COLUMNS && c < columns.size(); c++) {
				List<ScheduleWaitCondition> column = columns.get(c);
				if (column.isEmpty())
					continue;
				ScheduleWaitCondition first = column.get(0);
				GuiElementBuilder condition = ScheduleEditor.describe(first, "condition", ScheduleEditor.iconOf(first));
				for (int i = 1; i < column.size(); i++)
					condition.addLoreLine(Component.literal("+ ")
						.withStyle(ChatFormatting.GRAY)
						.append(column.get(i)
							.getTitleAs("condition")
							.get(0)));
				boolean last = c == CONDITION_COLUMNS - 1 && columns.size() > CONDITION_COLUMNS;
				if (last)
					condition.addLoreLine(ScheduleLang.text("schedule.more_conditions", "+%1$s more", columns.size() - c - 1)
						.withStyle(ChatFormatting.GRAY));
				condition.addLoreLine(Component.empty());
				condition.addLoreLine(ScheduleLang.translate("gui.schedule.lmb_edit")
					.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
				condition.setCallback(() -> editor.openConditions(entry));
				setSlot(base + 1 + c, condition);
			}
		}

		setSlot(base + DUPLICATE, new GuiElementBuilder(Items.PAPER)
			.setName(ScheduleLang.translate("gui.schedule.duplicate")
				.withStyle(ChatFormatting.AQUA))
			.hideDefaultTooltip()
			.setCallback(() -> {
				schedule.entries.add(index, entry.clone(editor.registries));
				changed();
			}));
		if (index > 0)
			setSlot(base + UP, new GuiElementBuilder(Items.DYE.pick(DyeColor.LIME))
				.setName(ScheduleLang.translate("gui.schedule.move_up")
					.withStyle(ChatFormatting.GREEN))
				.hideDefaultTooltip()
				.setCallback(() -> {
					schedule.entries.remove(index);
					schedule.entries.add(index - 1, entry);
					changed();
				}));
		if (index < schedule.entries.size() - 1)
			setSlot(base + DOWN, new GuiElementBuilder(Items.DYE.pick(DyeColor.ORANGE))
				.setName(ScheduleLang.translate("gui.schedule.move_down")
					.withStyle(ChatFormatting.GOLD))
				.hideDefaultTooltip()
				.setCallback(() -> {
					schedule.entries.remove(index);
					schedule.entries.add(index + 1, entry);
					changed();
				}));
		setSlot(base + REMOVE, new GuiElementBuilder(Items.BARRIER)
			.setName(ScheduleLang.translate("gui.schedule.remove_entry")
				.withStyle(ChatFormatting.RED))
			.hideDefaultTooltip()
			.setCallback(() -> {
				schedule.entries.remove(index);
				changed();
			}));
	}

	/** Create asks for the instruction of a new stop before it adds it; here it is added and then edited. */
	private void addEntry() {
		ScheduleEntry entry = editor.newEntry();
		schedule.entries.add(entry);
		editor.page = (schedule.entries.size() - 1) / ROWS;
		editor.save();
		ScheduleInputGui.forInstruction(editor, entry).open();
	}
}
