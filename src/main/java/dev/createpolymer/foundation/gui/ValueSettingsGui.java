package dev.createpolymer.foundation.gui;

import java.util.List;

import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueSettingsBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueSettingsBoard;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueSettingsFormatter.ScrollOptionSettingsFormatter;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Server-side stand-in for Create's value settings screen (the click-and-drag board). Every row of the
 * {@link ValueSettingsBoard} becomes a chest row with its label and the buttons -10, -1, +1, +10 for the shared
 * value; the confirm button applies the chosen row and value, a filtering behaviour also gets a clear button.
 */
public class ValueSettingsGui extends SimpleGui {
	private static final String PREFIX = "create.gui.value_settings.";
	private static final int MAX_ROWS = 5;

	private static final int LABEL_COLUMN = 0;
	private static final int MINUS_10_COLUMN = 2;
	private static final int MINUS_1_COLUMN = 3;
	private static final int VALUE_COLUMN = 4;
	private static final int PLUS_1_COLUMN = 5;
	private static final int PLUS_10_COLUMN = 6;

	private final BlockEntityBehaviour origin;
	private final ValueSettingsBehaviour behaviour;
	private final ValueSettingsBoard board;
	private final BlockHitResult hitResult;
	private final int boardRows;
	private final int maxValue;

	private int row;
	private int value;

	/** Opens the GUI for {@code behaviour}, which has to be a {@link ValueSettingsBehaviour}. */
	public static void open(ServerPlayer player, BlockEntityBehaviour behaviour, BlockHitResult hitResult) {
		if (!(behaviour instanceof ValueSettingsBehaviour valueSettingsBehaviour))
			return;
		ValueSettingsBoard board = valueSettingsBehaviour.createBoard(player, hitResult);
		new ValueSettingsGui(player, behaviour, valueSettingsBehaviour, board, hitResult).open();
	}

	protected ValueSettingsGui(ServerPlayer player, BlockEntityBehaviour origin, ValueSettingsBehaviour behaviour,
		ValueSettingsBoard board, BlockHitResult hitResult) {
		super(menuTypeFor(Math.min(MAX_ROWS, Math.max(1, board.rows().size())) + 1), player, false);
		this.origin = origin;
		this.behaviour = behaviour;
		this.board = board;
		this.hitResult = hitResult;
		this.boardRows = Math.min(MAX_ROWS, Math.max(1, board.rows().size()));
		this.maxValue = Math.max(0, board.maxValue());
		setTitle(board.title());
		setLockPlayerInventory(true);

		ValueSettings current = behaviour.getValueSettings();
		this.row = Mth.clamp(current.row(), 0, boardRows - 1);
		this.value = Mth.clamp(current.value(), 0, maxValue);
		refresh();
	}

	private static MenuType<?> menuTypeFor(int chestRows) {
		return switch (chestRows) {
			case 2 -> MenuType.GENERIC_9x2;
			case 3 -> MenuType.GENERIC_9x3;
			case 4 -> MenuType.GENERIC_9x4;
			case 5 -> MenuType.GENERIC_9x5;
			default -> MenuType.GENERIC_9x6;
		};
	}

	@Override
	public boolean stillValid() {
		return !origin.blockEntity.isRemoved() && origin.blockEntity.canPlayerUse(player);
	}

	private void select(int newRow, int delta) {
		row = newRow;
		value = Mth.clamp(value + delta, 0, maxValue);
		refresh();
	}

	private void refresh() {
		List<Component> labels = board.rows();
		for (int r = 0; r < boardRows; r++) {
			int rowIndex = r;
			boolean selected = r == row;
			int base = r * 9;
			ValueSettings settings = new ValueSettings(r, value);

			setSlot(base + LABEL_COLUMN, new GuiElementBuilder(selected ? GuiText.dye(DyeColor.LIME) : GuiText.dye(DyeColor.GRAY))
				.setName(labels.get(r)
					.copy()
					.withStyle(selected ? ChatFormatting.GOLD : ChatFormatting.GRAY))
				.hideDefaultTooltip()
				.glow(selected)
				.setCallback(() -> select(rowIndex, 0)));

			Item icon = Items.PAPER;
			if (board.formatter() instanceof ScrollOptionSettingsFormatter optionFormatter)
				icon = optionFormatter.getIcon(settings);
			setSlot(base + VALUE_COLUMN, new GuiElementBuilder(icon)
				.setName(board.formatter()
					.format(settings)
					.withStyle(selected ? ChatFormatting.GOLD : ChatFormatting.WHITE))
				.setCount(Mth.clamp(value, 1, 64))
				.hideDefaultTooltip()
				.glow(selected)
				.setCallback(() -> select(rowIndex, 0)));

			if (maxValue > 0) {
				setSlot(base + MINUS_1_COLUMN, step(r, GuiText.glassPane(DyeColor.RED), -1));
				setSlot(base + PLUS_1_COLUMN, step(r, GuiText.glassPane(DyeColor.LIME), 1));
			}
			if (maxValue > 10) {
				setSlot(base + MINUS_10_COLUMN, step(r, GuiText.glassPane(DyeColor.RED), -10));
				setSlot(base + PLUS_10_COLUMN, step(r, GuiText.glassPane(DyeColor.LIME), 10));
			}
		}

		int controls = boardRows * 9;
		setSlot(controls + 3, new GuiElementBuilder(Items.EMERALD)
			.setName(GuiText.text(PREFIX + "confirm", "Confirm")
				.withStyle(ChatFormatting.GREEN))
			.addLoreLine(board.formatter()
				.format(new ValueSettings(row, value))
				.withStyle(ChatFormatting.GRAY))
			.hideDefaultTooltip()
			.setCallback(this::confirm));

		if (behaviour instanceof FilteringBehaviour filtering)
			setSlot(controls + 5, new GuiElementBuilder(Items.BARRIER)
				.setName(GuiText.text(PREFIX + "clear_filter", "Clear filter")
					.withStyle(ChatFormatting.RED))
				.hideDefaultTooltip()
				.setCallback(() -> clearFilter(filtering)));
	}

	private GuiElementBuilder step(int targetRow, Item item, int delta) {
		return new GuiElementBuilder(item)
			.setName(Component.literal((delta > 0 ? "+" : "") + delta)
				.withStyle(delta > 0 ? ChatFormatting.GREEN : ChatFormatting.RED))
			.hideDefaultTooltip()
			.setCallback(() -> select(targetRow, delta));
	}

	private void confirm() {
		if (stillValid())
			behaviour.setValueSettings(player, new ValueSettings(row, value), false);
		close();
	}

	private void clearFilter(FilteringBehaviour filtering) {
		if (stillValid() && player.getMainHandItem().isEmpty())
			filtering.onShortInteract(player, InteractionHand.MAIN_HAND, hitResult.getDirection(), hitResult);
		close();
	}
}
