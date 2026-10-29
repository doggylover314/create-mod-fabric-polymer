package dev.createpolymer.trains.schedule;

import dev.createpolymer.foundation.gui.HeldItemGhostItemGui;
import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Create's ScheduleMenu: the ghost slots (one or two) of the item an instruction or condition asks for, e.g. the item
 * a train has to hold or the frequencies of a redstone link. Click one with an item to set it, click it with an empty
 * hand to clear it; the schedule item is written after every change.
 */
public class ScheduleItemGui extends HeldItemGhostItemGui {

	private static final int BACK = 8;

	private final ScheduleEditor editor;
	private final IScheduleInput input;

	public ScheduleItemGui(ScheduleEditor editor, IScheduleInput input, Runnable back) {
		super(MenuType.GENERIC_9x1, editor.player, input.getSecondLineTooltip(0) == null ? Component.empty()
			: input.getSecondLineTooltip(0)
				.get(0),
			editor.stack, createGhostInventory(input));
		this.editor = editor;
		this.input = input;
		setSlot(BACK, new GuiElementBuilder(Items.EMERALD_BLOCK)
			.setName(Component.translatable("create.gui.value_settings.confirm"))
			.hideDefaultTooltip()
			.setCallback(back));
	}

	private static ItemStackHandler createGhostInventory(IScheduleInput input) {
		ItemStackHandler handler = new ItemStackHandler(Math.max(1, input.slotsTargeted()));
		for (int i = 0; i < input.slotsTargeted(); i++)
			handler.setStackInSlot(i, input.getItem(i));
		return handler;
	}

	@Override
	protected boolean allowRepeats() {
		return true;
	}

	/** Create: ScheduleMenu#saveData is empty, the screen sends the items to the server when it closes. */
	@Override
	protected void saveData(ItemStack contentHolder) {
		for (int i = 0; i < input.slotsTargeted(); i++)
			input.setItem(i, ghostInventory.getStackInSlot(i)
				.copy());
		editor.save();
	}
}
