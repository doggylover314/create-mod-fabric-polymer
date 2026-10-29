package dev.createpolymer.kinetics.transmission.sequencer;

import java.util.Vector;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Chest GUI that replaces Create's SequencedGearshiftScreen. Each of the five rows is one instruction:
 * type, value (- / value / +) and speed modifier. Click a type or speed button to cycle forwards
 * (right-click: backwards); shift-click the value buttons for the big step. Changes are applied when the
 * GUI is closed, like Create's configuration packet (nothing changes if you did not click anything).
 */
public class SequencedGearshiftGui extends SimpleGui {
	public static final int MAX_INSTRUCTIONS = 5;

	private final SequencedGearshiftBlockEntity be;
	private final Vector<Instruction> instructions;
	private boolean dirty = false;

	public static void open(ServerPlayer player, SequencedGearshiftBlockEntity be) {
		new SequencedGearshiftGui(player, be).open();
	}

	public SequencedGearshiftGui(ServerPlayer player, SequencedGearshiftBlockEntity be) {
		super(MenuType.GENERIC_9x6, player, false);
		this.be = be;
		this.instructions = Instruction.copyAll(be.getInstructions());
		setTitle(Component.translatable("create.gui.sequenced_gearshift.title"));
		refresh();
	}

	private static Item iconOf(SequencerInstructions instruction) {
		return switch (instruction) {
			case TURN_ANGLE -> Items.COMPASS;
			case TURN_DISTANCE -> Items.PISTON;
			case DELAY -> Items.CLOCK;
			case AWAIT -> Items.REDSTONE_TORCH;
			case END -> Items.BARRIER;
		};
	}

	private static Item iconOf(InstructionSpeedModifiers modifier) {
		return switch (modifier) {
			case FORWARD_FAST -> Items.STAINED_GLASS_PANE.lime();
			case FORWARD -> Items.STAINED_GLASS_PANE.green();
			case BACK -> Items.STAINED_GLASS_PANE.orange();
			case BACK_FAST -> Items.STAINED_GLASS_PANE.red();
		};
	}

	private void refresh() {
		for (int row = 0; row < MAX_INSTRUCTIONS; row++)
			refreshRow(row);
	}

	private void refreshRow(int row) {
		int base = row * 9;
		if (row >= instructions.size()) {
			for (int col = 0; col < 5; col++)
				setSlot(base + col, new GuiElementBuilder(Items.STAINED_GLASS_PANE.gray()).hideTooltip().build());
			return;
		}

		Instruction instruction = instructions.get(row);
		SequencerInstructions def = instruction.instruction;

		setSlot(base, new GuiElementBuilder(iconOf(def))
			.setName(def.getLabel())
			.addLoreLine(def.getDescription())
			.setCallback((index, type, action, gui) -> {
				if (!type.isLeft && !type.isRight)
					return;
				SequencerInstructions[] values = SequencerInstructions.values();
				int next = Math.floorMod(def.ordinal() + (type.isLeft ? 1 : -1), values.length);
				instructionUpdated(row, values[next]);
				click();
				refresh();
			})
			.build());

		if (def.hasValueParameter) {
			setSlot(base + 1, new GuiElementBuilder(Items.CONCRETE.red())
				.setName(Component.literal("-"))
				.addLoreLine(def.getParameterLabel())
				.setCallback((index, type, action, gui) -> step(row, false, type))
				.build());
			setSlot(base + 2, new GuiElementBuilder(Items.PAPER)
				.setName(def.formatValue(instruction.value))
				.addLoreLine(def.getParameterLabel())
				.build());
			setSlot(base + 3, new GuiElementBuilder(Items.CONCRETE.lime())
				.setName(Component.literal("+"))
				.addLoreLine(def.getParameterLabel())
				.setCallback((index, type, action, gui) -> step(row, true, type))
				.build());
		} else {
			for (int col = 1; col <= 3; col++)
				setSlot(base + col, new GuiElementBuilder(Items.STAINED_GLASS_PANE.gray()).hideTooltip().build());
		}

		if (def.hasSpeedParameter) {
			InstructionSpeedModifiers modifier = instruction.speedModifier;
			setSlot(base + 4, new GuiElementBuilder(iconOf(modifier))
				.setName(modifier.getLabel())
				.addLoreLine(modifier.getDescription())
				.setCallback((index, type, action, gui) -> {
					if (!type.isLeft && !type.isRight)
						return;
					InstructionSpeedModifiers[] values = InstructionSpeedModifiers.values();
					instruction.speedModifier = values[Math.floorMod(modifier.ordinal() + (type.isLeft ? 1 : -1), values.length)];
					click();
					refresh();
				})
				.build());
		} else {
			setSlot(base + 4, new GuiElementBuilder(Items.STAINED_GLASS_PANE.gray()).hideTooltip().build());
		}
	}

	/** Create's ScrollInput steps: 1 (shift: shiftStep); delays go 1, 2, ... 19, 20, 40, ... and 20/100 with shift. */
	private void step(int row, boolean forward, ClickType click) {
		if (!click.isLeft && !click.isRight)
			return;
		Instruction instruction = instructions.get(row);
		SequencerInstructions def = instruction.instruction;
		int value = instruction.value;
		int step;
		if (def == SequencerInstructions.DELAY) {
			int v = value;
			if (!forward)
				v--;
			if (v < 20)
				step = click.shift ? 20 : 1;
			else
				step = click.shift ? 100 : 20;
		} else
			step = click.shift ? def.shiftStep : 1;
		instruction.value = Mth.clamp(value + (forward ? step : -step), 1, def.maxValue);
		click();
		refresh();
	}

	private void instructionUpdated(int index, SequencerInstructions newValue) {
		Instruction instruction = instructions.get(index);
		instruction.instruction = newValue;
		instruction.value = newValue.defaultValue;
		if (newValue == SequencerInstructions.END) {
			for (int i = instructions.size() - 1; i > index; i--)
				instructions.remove(i);
		} else {
			if (index + 1 < MAX_INSTRUCTIONS && index + 1 == instructions.size())
				instructions.add(new Instruction(SequencerInstructions.END));
		}
	}

	private void click() {
		dirty = true;
		player.level().playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.MASTER, 0.5f, 1f);
	}

	private void apply() {
		if (!dirty)
			return;
		dirty = false;
		if (be.isRemoved())
			return;
		be.applyInstructions(Instruction.copyAll(instructions));
	}

	@Override
	public void onPlayerClose(boolean success) {
		apply();
	}

	@Override
	public void onManualClose() {
		apply();
	}

	@Override
	public void onRemoved() {
		apply();
	}

	@Override
	public boolean stillValid() {
		return !be.isRemoved() && be.canPlayerUse(player);
	}
}
