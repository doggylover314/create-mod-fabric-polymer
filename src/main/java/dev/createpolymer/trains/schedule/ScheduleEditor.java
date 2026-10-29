package dev.createpolymer.trains.schedule;

import java.util.ArrayList;
import java.util.List;

import dev.createpolymer.registry.feature.SchedulesRegistry;
import dev.createpolymer.trains.schedule.condition.ScheduleWaitCondition;
import dev.createpolymer.trains.schedule.condition.ScheduledDelay;
import dev.createpolymer.trains.schedule.destination.DestinationInstruction;
import dev.createpolymer.trains.schedule.destination.ScheduleInstruction;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What the schedule GUIs share while a player edits the schedule of an item: the schedule being edited and the item
 * it is written back to after every change (Create sends the schedule to the server when its screen closes).
 */
public class ScheduleEditor {

	public final ServerPlayer player;
	/** The schedule item in the main hand of the player. */
	public final ItemStack stack;
	public final Schedule schedule;
	public final HolderLookup.Provider registries;

	int page;

	public ScheduleEditor(ServerPlayer player, ItemStack stack) {
		this.player = player;
		this.stack = stack;
		this.registries = player.registryAccess();
		Schedule existing = ScheduleItem.getSchedule(registries, stack);
		this.schedule = existing != null ? existing : new Schedule();
	}

	/** The GUIs close as soon as the item leaves the hand of the player. */
	public boolean stillValid() {
		return player.getInventory()
			.getSelectedItem() == stack;
	}

	/** Create: ScheduleEditPacket#handle */
	public void save() {
		schedule.savedProgress =
			schedule.entries.isEmpty() ? 0 : Math.clamp(schedule.savedProgress, 0, schedule.entries.size() - 1);
		if (schedule.entries.isEmpty())
			stack.remove(SchedulesRegistry.TRAIN_SCHEDULE);
		else
			stack.set(SchedulesRegistry.TRAIN_SCHEDULE, schedule.write(registries));
	}

	public void openList() {
		new ScheduleGui(this).open();
	}

	public void openConditions(ScheduleEntry entry) {
		new ScheduleConditionsGui(this, entry).open();
	}

	/** Create: the entry the add button of the screen makes, a stop with a delay to wait there. */
	public ScheduleEntry newEntry() {
		ScheduleEntry entry = new ScheduleEntry();
		entry.instruction = new DestinationInstruction();
		List<ScheduleWaitCondition> initialConditions = new ArrayList<>();
		initialConditions.add(new ScheduledDelay());
		entry.conditions.add(initialConditions);
		return entry;
	}

	/** The new instruction of an entry; an entry that waits needs a condition to wait for. */
	public void replaceInstruction(ScheduleEntry entry, ScheduleInstruction instruction) {
		entry.instruction = instruction;
		if (instruction.supportsConditions() && entry.conditions.isEmpty()) {
			List<ScheduleWaitCondition> initialConditions = new ArrayList<>();
			initialConditions.add(new ScheduledDelay());
			entry.conditions.add(initialConditions);
		}
		save();
	}

	// ---- how things look ----

	/** The item that stands for an instruction or condition. */
	public static Item iconOf(IScheduleInput input) {
		ItemStack icon = input.getSummary()
			.getFirst();
		if (icon.isEmpty())
			icon = input.getSecondLineIcon();
		if (!icon.isEmpty())
			return icon.getItem();
		return switch (input.getId()
			.getPath()) {
		case "powered" -> Items.REDSTONE_TORCH;
		case "unloaded" -> Items.GRASS_BLOCK;
		default -> Items.PAPER;
		};
	}

	/** The title of an instruction or condition as the name of an item, and what it says more as lore. */
	public static GuiElementBuilder describe(IScheduleInput input, String type, Item icon) {
		List<Component> lines = input.getTitleAs(type);
		GuiElementBuilder builder = new GuiElementBuilder(icon).hideDefaultTooltip();
		if (lines.isEmpty())
			return builder;
		builder.setName(lines.get(0)
			.copy());
		for (int i = 1; i < lines.size(); i++)
			builder.addLoreLine(lines.get(i));
		return builder;
	}
}
