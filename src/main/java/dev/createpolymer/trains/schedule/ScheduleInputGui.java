package dev.createpolymer.trains.schedule;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.trains.schedule.ScheduleConfigBuilder.IntegerText;
import dev.createpolymer.trains.schedule.ScheduleConfigBuilder.Scroll;
import dev.createpolymer.trains.schedule.ScheduleConfigBuilder.Selection;
import dev.createpolymer.trains.schedule.ScheduleConfigBuilder.Text;
import dev.createpolymer.trains.schedule.ScheduleConfigBuilder.Widget;
import dev.createpolymer.trains.schedule.condition.ScheduleWaitCondition;
import dev.createpolymer.trains.schedule.destination.ScheduleInstruction;
import dev.createpolymer.util.Pair;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Chest GUI for the editor of Create's ScheduleScreen that opens when an instruction or a condition is clicked: the
 * type of the instruction or condition on the left (click for the next, right-click for the previous), then a button
 * for each of its options as the instruction or condition describes them with {@link ScheduleConfigBuilder}, and a
 * button for each item it asks for. Numbers and choices change by clicking, texts are typed in an anvil.
 */
public class ScheduleInputGui extends SimpleGui {

	private static final int SUMMARY = 4;
	private static final int TYPE = 10;
	private static final int WIDGETS = 12;
	private static final int ITEMS = 20;
	private static final int REMOVE = 18;
	private static final int CONFIRM = 26;

	private final ScheduleEditor editor;
	private final boolean condition;
	private IScheduleInput input;
	private final BiConsumer<IScheduleInput, IScheduleInput> replace;
	@Nullable
	private final Consumer<IScheduleInput> remove;
	private final Runnable back;

	public static ScheduleInputGui forInstruction(ScheduleEditor editor, ScheduleEntry entry) {
		return new ScheduleInputGui(editor, false, entry.instruction,
			(old, instruction) -> editor.replaceInstruction(entry, (ScheduleInstruction) instruction), null,
			editor::openList);
	}

	public static ScheduleInputGui forCondition(ScheduleEditor editor, ScheduleEntry entry,
		List<ScheduleWaitCondition> column, ScheduleWaitCondition condition) {
		boolean canRemove = column.size() > 1 || entry.conditions.size() > 1;
		return new ScheduleInputGui(editor, true, condition, (old, replacement) -> {
			column.set(column.indexOf(old), (ScheduleWaitCondition) replacement);
			editor.save();
		}, canRemove ? removed -> {
			column.remove(removed);
			if (column.isEmpty())
				entry.conditions.remove(column);
			editor.save();
		} : null, () -> editor.openConditions(entry));
	}

	private ScheduleInputGui(ScheduleEditor editor, boolean condition, IScheduleInput input,
		BiConsumer<IScheduleInput, IScheduleInput> replace, @Nullable Consumer<IScheduleInput> remove, Runnable back) {
		super(MenuType.GENERIC_9x3, editor.player, false);
		this.editor = editor;
		this.condition = condition;
		this.input = input;
		this.replace = replace;
		this.remove = remove;
		this.back = back;
		setLockPlayerInventory(true);
		setTitle(ScheduleLang.translate(condition ? "schedule.condition.editor" : "schedule.instruction.editor"));
		refresh();
	}

	@Override
	public boolean stillValid() {
		return editor.stillValid();
	}

	private String kind() {
		return condition ? "condition" : "instruction";
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private List<Pair<Identifier, Supplier<? extends IScheduleInput>>> types() {
		return (List) (condition ? Schedule.CONDITION_TYPES : Schedule.INSTRUCTION_TYPES);
	}

	private void refresh() {
		for (int i = 0; i < 27; i++)
			clearSlot(i);

		setSlot(SUMMARY, ScheduleEditor.describe(input, kind(), ScheduleEditor.iconOf(input)));

		// the type
		List<Pair<Identifier, Supplier<? extends IScheduleInput>>> types = types();
		List<? extends Component> names = Schedule.getTypeOptions(types());
		int current = 0;
		for (int i = 0; i < types.size(); i++)
			if (types.get(i)
				.getFirst()
				.equals(input.getId()))
				current = i;
		int selected = current;
		GuiElementBuilder type = new GuiElementBuilder(Items.COMPARATOR)
			.setName(ScheduleLang.translate(condition ? "schedule.condition_type" : "schedule.instruction_type")
				.withStyle(s -> s.withColor(0x5391E1)))
			.hideDefaultTooltip();
		for (int i = 0; i < names.size(); i++)
			type.addLoreLine(Component.literal(i == current ? "> " : "  ")
				.append(names.get(i))
				.withStyle(i == current ? ChatFormatting.WHITE : ChatFormatting.GRAY));
		type.setCallback(click -> {
			int delta = click.isLeft ? 1 : click.isRight ? -1 : 0;
			if (delta == 0)
				return;
			int next = Math.floorMod(selected + delta, types.size());
			IScheduleInput replacement = types.get(next)
				.getSecond()
				.get();
			replace.accept(input, replacement);
			input = replacement;
			refresh();
		});
		setSlot(TYPE, type);

		// the options
		ScheduleConfigBuilder builder = new ScheduleConfigBuilder();
		input.initConfigurationWidgets(builder);
		List<Widget> widgets = builder.getWidgets();
		for (int i = 0; i < widgets.size() && i < 6; i++)
			setSlot(WIDGETS + i, widgetButton(widgets.get(i)));

		// the items
		for (int slot = 0; slot < input.slotsTargeted() && slot < 2; slot++) {
			int target = slot;
			ItemStack item = input.getItem(slot);
			List<Component> tooltip = input.getSecondLineTooltip(slot);
			GuiElementBuilder button = new GuiElementBuilder(item.isEmpty() ? Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY) : item.getItem())
				.hideDefaultTooltip();
			if (tooltip != null && !tooltip.isEmpty()) {
				button.setName(tooltip.get(0)
					.copy());
				for (int i = 1; i < tooltip.size(); i++)
					button.addLoreLine(tooltip.get(i));
			} else
				button.setName(ScheduleLang.text("schedule.item_slot", "Reference Item"));
			if (!item.isEmpty()) {
				button.addLoreLine(Component.empty());
				button.addLoreLine(item.getHoverName()
					.copy()
					.withStyle(ChatFormatting.AQUA));
			}
			button.addLoreLine(Component.empty());
			button.addLoreLine(ScheduleLang.translate("gui.schedule.lmb_edit")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			button.setCallback(() -> new ScheduleItemGui(editor, input, this::reopen).open());
			setSlot(ITEMS + slot, button);
		}

		if (remove != null)
			setSlot(REMOVE, new GuiElementBuilder(Items.BARRIER)
				.setName(ScheduleLang.text("gui.schedule.remove_condition", "Remove Condition")
					.withStyle(ChatFormatting.RED))
				.hideDefaultTooltip()
				.setCallback(() -> {
					remove.accept(input);
					back.run();
				}));

		setSlot(CONFIRM, new GuiElementBuilder(Items.EMERALD_BLOCK)
			.setName(Component.translatable("create.gui.value_settings.confirm"))
			.hideDefaultTooltip()
			.setCallback(back));
	}

	private void reopen() {
		new ScheduleInputGui(editor, condition, input, replace, remove, back).open();
	}

	// ---- the options ----

	private GuiElementBuilder widgetButton(Widget widget) {
		var data = input.getData();
		if (widget instanceof Scroll scroll) {
			int value = Math.clamp(data.getIntOr(scroll.dataKey(), scroll.min()), scroll.min(), scroll.max() - 1);
			return new GuiElementBuilder(Items.REPEATER)
				.setName(scroll.title()
					.copy()
					.withStyle(s -> s.withColor(0x5391E1)))
				.addLoreLine(Component.literal(value + scroll.suffix())
					.withStyle(ChatFormatting.WHITE))
				.addLoreLine(ScheduleLang.text("schedule.scroll_hint", "Click: +%1$s, Right-click: -%1$s", scroll.step())
					.withStyle(ChatFormatting.DARK_GRAY))
				.addLoreLine(ScheduleLang.text("schedule.scroll_hint_shift", "Shift: %1$s", scroll.shiftStep())
					.withStyle(ChatFormatting.DARK_GRAY))
				.hideDefaultTooltip()
				.setCallback(click -> {
					int delta = click.isLeft ? 1 : click.isRight ? -1 : 0;
					if (delta == 0)
						return;
					int step = click.shift ? scroll.shiftStep() : scroll.step();
					data.putInt(scroll.dataKey(), Math.clamp(value + delta * step, scroll.min(), scroll.max() - 1));
					changed();
				});
		}

		if (widget instanceof Selection selection) {
			int current = Math.clamp(data.getIntOr(selection.dataKey(), 0), 0, selection.options()
				.size() - 1);
			GuiElementBuilder builder = new GuiElementBuilder(Items.REPEATER)
				.setName(selection.title()
					.copy()
					.withStyle(s -> s.withColor(0x5391E1)))
				.hideDefaultTooltip();
			for (int i = 0; i < selection.options()
				.size(); i++)
				builder.addLoreLine(Component.literal(i == current ? "> " : "  ")
					.append(selection.options()
						.get(i))
					.withStyle(i == current ? ChatFormatting.WHITE : ChatFormatting.GRAY));
			return builder.setCallback(click -> {
				int delta = click.isLeft ? 1 : click.isRight ? -1 : 0;
				if (delta == 0)
					return;
				data.putInt(selection.dataKey(), Math.floorMod(current + delta, selection.options()
					.size()));
				changed();
			});
		}

		String value = data.getStringOr(widget.dataKey(), "");
		GuiElementBuilder builder = new GuiElementBuilder(Items.NAME_TAG)
			.setName(widget.title()
				.copy()
				.withStyle(s -> s.withColor(0x5391E1)))
			.addLoreLine(value.isEmpty() ? Component.literal("-")
				.withStyle(ChatFormatting.DARK_GRAY)
				: Component.literal(value)
					.withStyle(ChatFormatting.WHITE))
			.hideDefaultTooltip();
		if (widget instanceof Text text)
			for (Component hint : text.hint())
				builder.addLoreLine(hint);
		builder.addLoreLine(ScheduleLang.translate("gui.schedule.lmb_edit")
			.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		return builder.setCallback(click -> {
			if (click.isLeft || click.isRight)
				editText(widget, value);
		});
	}

	/** A number or choice was changed: the schedule goes back into the item. */
	private void changed() {
		editor.save();
		refresh();
	}

	/** Create's text boxes: the text is typed in an anvil. */
	private void editText(Widget widget, String current) {
		boolean[] done = { false };
		Runnable back = () -> {
			if (done[0])
				return;
			done[0] = true;
			editor.player.level()
				.getServer()
				.execute(this::reopen);
		};
		AnvilInputGui anvil = new AnvilInputGui(editor.player, false) {
			@Override
			public void onPlayerClose(boolean success) {
				back.run();
			}

			@Override
			public void onManualClose() {
				back.run();
			}
		};
		anvil.setTitle(widget.title());
		anvil.setDefaultInputValue(current);
		anvil.setSlot(2, new GuiElementBuilder(Items.NAME_TAG)
			.setName(Component.translatable("create.gui.value_settings.confirm")
				.withStyle(ChatFormatting.GREEN))
			.hideDefaultTooltip()
			.setCallback(() -> {
				setText(widget, anvil.getInput());
				back.run();
			}));
		anvil.open();
	}

	/** Applies what was typed. */
	public void setText(Widget widget, String typed) {
		if (widget instanceof IntegerText) {
			try {
				typed = String.valueOf(Math.max(0, Integer.parseInt(typed.trim())));
			} catch (NumberFormatException e) {
				return;
			}
		} else if (widget instanceof Text text && text.maxWildcards() >= 0) {
			StringBuilder limited = new StringBuilder();
			int wildcards = 0;
			for (char c : typed.toCharArray()) {
				if (c == '*' && ++wildcards > text.maxWildcards())
					continue;
				limited.append(c);
			}
			typed = limited.toString();
		}
		input.getData()
			.putString(widget.dataKey(), typed);
		editor.save();
	}
}
