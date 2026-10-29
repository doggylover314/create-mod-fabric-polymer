package dev.createpolymer.redstone.thresholdSwitch;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.redstone.thresholdSwitch.ThresholdSwitchBlockEntity.ThresholdType;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Chest GUI that replaces Create's ThresholdSwitchScreen: the thresholds to switch on and off at, the unit for
 * items (single items or stacks), inverting the signal, and what the switch looks at right now. Every click is
 * applied straight away (Create sends the thresholds after a short pause or when the screen closes).
 */
public class ThresholdSwitchGui extends SimpleGui {
	private static final String PREFIX = "create.gui.threshold_switch.";

	private final ThresholdSwitchBlockEntity be;

	public static void open(ServerPlayer player, ThresholdSwitchBlockEntity be) {
		new ThresholdSwitchGui(player, be).open();
	}

	public ThresholdSwitchGui(ServerPlayer player, ThresholdSwitchBlockEntity be) {
		super(MenuType.GENERIC_9x4, player, false);
		this.be = be;
		setTitle(Component.translatable(PREFIX + "title"));
		setLockPlayerInventory(true);
		refresh();
	}

	@Override
	public boolean stillValid() {
		return !be.isRemoved() && be.canPlayerUse(player);
	}

	private int valueStep() {
		ThresholdType type = be.getTypeOfCurrentTarget();
		if (type == ThresholdType.FLUID)
			return 1000;
		if (type == ThresholdType.ITEM && be.inStacks)
			return 64;
		return 1;
	}

	private void refresh() {
		ThresholdType type = be.getTypeOfCurrentTarget();
		boolean supported = type != ThresholdType.UNSUPPORTED;
		int step = valueStep();
		boolean stacks = be.inStacks;

		// Row 0: what the switch looks at, and the unit
		ItemStack target = be.getDisplayItemForScreen();
		GuiElementBuilder targetInfo = new GuiElementBuilder(target.isEmpty() ? Items.BARRIER : target.getItem())
			.hideDefaultTooltip();
		if (target.isEmpty()) {
			targetInfo.setName(Component.translatable(PREFIX + "not_attached"));
		} else {
			targetInfo.setName(target.getHoverName());
			if (!supported) {
				targetInfo.addLoreLine(Component.translatable(PREFIX + "incompatible")
					.withStyle(ChatFormatting.GRAY));
			} else {
				targetInfo.addLoreLine(Component.translatable(PREFIX + "currently",
					be.format(be.currentLevel / step, stacks))
					.withStyle(ChatFormatting.DARK_AQUA));
				if (be.currentMinLevel / step == 0)
					targetInfo.addLoreLine(Component.translatable(PREFIX + "range_max",
						be.format(be.currentMaxLevel / step, stacks))
						.withStyle(ChatFormatting.GRAY));
				else
					targetInfo.addLoreLine(Component.translatable(PREFIX + "range", be.currentMinLevel / step,
						be.format(be.currentMaxLevel / step, stacks))
						.withStyle(ChatFormatting.GRAY));
			}
		}
		setSlot(1, targetInfo);

		if (type == ThresholdType.ITEM) {
			setSlot(7, new GuiElementBuilder(stacks ? Items.CHEST : Items.IRON_NUGGET)
				.setName(Component.translatable("create.schedule.condition.threshold.item_measure"))
				.addLoreLine(Component.translatable(stacks ? "create.schedule.condition.threshold.stacks"
					: "create.schedule.condition.threshold.items")
					.withStyle(ChatFormatting.AQUA))
				.hideDefaultTooltip()
				.setCallback((index, click, action, gui) -> {
					be.inStacks = !be.inStacks;
					clamp();
					changed();
				}));
		} else {
			clearSlot(7);
		}

		// Rows 1 and 2: upper and lower threshold
		boolean on = be.isInverted() ^ be.isPowered();
		thresholdRow(1, true, supported, step, stacks, on);
		thresholdRow(2, false, supported, step, stacks, !on);

		// Row 3: invert the signal
		setSlot(3 * 9 + 1, new GuiElementBuilder(be.isInverted() ? Items.REDSTONE_TORCH : Items.LEVER)
			.setName(Component.translatable(PREFIX + "invert_signal"))
			.glow(be.isInverted())
			.hideDefaultTooltip()
			.setCallback((index, click, action, gui) -> {
				be.setInverted(!be.isInverted());
				changed();
			}));

		setSlot(3 * 9 + 7, new GuiElementBuilder(Items.EMERALD)
			.setName(Component.translatable("create.gui.value_settings.confirm"))
			.hideDefaultTooltip()
			.setCallback(() -> close()));
	}

	private void thresholdRow(int row, boolean upper, boolean supported, int step, boolean stacks, boolean highlight) {
		int base = row * 9;
		boolean inverted = be.isInverted();
		boolean powerOn = upper ^ inverted;
		setSlot(base, new GuiElementBuilder(upper ? Items.REDSTONE_TORCH : Items.LEVER)
			.setName(Component.translatable(PREFIX + (powerOn ? "power_on_when" : "power_off_when")))
			.addLoreLine(Component.translatable(PREFIX + (upper ? "upper_threshold" : "lower_threshold"))
				.withStyle(ChatFormatting.GRAY))
			.glow(highlight)
			.hideDefaultTooltip());

		if (!supported) {
			for (int col = 2; col <= 6; col++)
				clearSlot(base + col);
			return;
		}

		int value = upper ? be.onWhenAbove : be.offWhenBelow;
		setSlot(base + 2, stepButton(upper, -10 * step, step));
		setSlot(base + 3, stepButton(upper, -step, step));
		Component shown = be.getTypeOfCurrentTarget() == ThresholdType.ITEM
			? Component.literal((upper ? "≥ " : "≤ ") + value / step)
				.append(" ")
				.append(Component.translatable(stacks ? "create.schedule.condition.threshold.stacks"
					: "create.schedule.condition.threshold.items"))
			: Component.literal(upper ? "≥ " : "≤ ")
				.append(be.format(value / step, stacks));
		setSlot(base + 4, new GuiElementBuilder(Items.PAPER)
			.setName(shown.copy()
				.withStyle(ChatFormatting.GOLD))
			.hideDefaultTooltip());
		setSlot(base + 5, stepButton(upper, step, step));
		setSlot(base + 6, stepButton(upper, 10 * step, step));
	}

	private GuiElementBuilder stepButton(boolean upper, int delta, int step) {
		boolean up = delta > 0;
		return new GuiElementBuilder(up ? Items.STAINED_GLASS_PANE.lime() : Items.STAINED_GLASS_PANE.red())
			.setName(Component.literal((up ? "+" : "") + (delta / step))
				.withStyle(up ? ChatFormatting.GREEN : ChatFormatting.RED))
			.hideDefaultTooltip()
			.setCallback((index, click, action, gui) -> {
				if (!click.isLeft && !click.isRight)
					return;
				if (upper)
					be.onWhenAbove += delta;
				else
					be.offWhenBelow += delta;
				clamp(upper);
				changed();
			});
	}

	private void clamp() {
		clamp(true);
	}

	/**
	 * Keeps both thresholds in the range of the target and a step apart, moving the other threshold along when the
	 * one that was edited runs into it (Create's screen does the same).
	 */
	private void clamp(boolean upperEdited) {
		int step = valueStep();
		int min = be.currentMinLevel;
		int max = be.currentMaxLevel;
		if (max < min)
			return;
		be.onWhenAbove = Mth.clamp(be.onWhenAbove / step * step, min + step, Math.max(min + step, max));
		be.offWhenBelow = Mth.clamp(be.offWhenBelow / step * step, min, Math.max(min, max - step));
		if (be.offWhenBelow >= be.onWhenAbove) {
			if (upperEdited)
				be.offWhenBelow = Math.max(min, be.onWhenAbove - step);
			else
				be.onWhenAbove = Math.min(Math.max(min + step, max), be.offWhenBelow + step);
		}
	}

	private void changed() {
		be.setChanged();
		be.sendData();
		be.updateCurrentLevel();
		player.level()
			.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.MASTER, .5f, 1f);
		refresh();
	}

	@Override
	public void onPlayerClose(boolean success) {
		if (!be.isRemoved())
			be.updateCurrentLevel();
	}
}
