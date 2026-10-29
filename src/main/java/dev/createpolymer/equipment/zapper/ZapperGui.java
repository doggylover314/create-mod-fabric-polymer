package dev.createpolymer.equipment.zapper;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.createpolymer.registry.feature.WandsRegistry;

/**
 * Server side stand-in for Create's ZapperScreen: a chest GUI for the item in the player's hand. The pattern buttons
 * and the done button live here; subclasses add their own settings. The settings are written to the item whenever
 * something changes and when the GUI closes (Create's screen writes them when it is removed).
 */
public abstract class ZapperGui extends SimpleGui {

	protected final ItemStack zapper;
	protected final InteractionHand hand;

	protected PlacementPatterns currentPattern;

	protected ZapperGui(MenuType<?> type, ServerPlayer player, ItemStack zapper, InteractionHand hand) {
		super(type, player, false);
		this.zapper = zapper;
		this.hand = hand;
		setLockPlayerInventory(true);
		setTitle(zapper.getHoverName());

		currentPattern = zapper.getOrDefault(WandsRegistry.PLACEMENT_PATTERN, PlacementPatterns.Solid);
	}

	@Override
	public boolean stillValid() {
		return player.getItemInHand(hand) == zapper;
	}

	/** Writes the settings of the GUI to the item (Create's ConfigureZapperPacket.configureZapper) */
	protected abstract void save();

	@Override
	public void onRemoved() {
		if (stillValid())
			save();
	}

	/** The pattern buttons, one slot each from {@code firstSlot} */
	protected void patternButtons(int firstSlot) {
		for (PlacementPatterns pattern : PlacementPatterns.values()) {
			boolean selected = pattern == currentPattern;
			setSlot(firstSlot + pattern.ordinal(), button(pattern.icon, pattern.iconCount,
				Component.translatable("create.gui.terrainzapper.patternSection")
					.append(": ")
					.append(Component.translatable("create.gui.terrainzapper.pattern." + pattern.translationKey)),
				selected, () -> {
					currentPattern = pattern;
					save();
					refresh();
				}));
		}
	}

	protected GuiElementBuilder doneButton() {
		return new GuiElementBuilder(Items.EMERALD)
			.setName(Component.translatable("gui.done")
				.withStyle(ChatFormatting.GREEN))
			.hideDefaultTooltip()
			.setCallback(() -> close());
	}

	protected abstract void refresh();

	/** A button of the GUI: the selected one glows and has a gold name */
	public static GuiElementBuilder button(Item icon, int count, Component name, boolean selected, Runnable onClick) {
		return new GuiElementBuilder(icon).setCount(count)
			.setName(name.copy()
				.withStyle(selected ? ChatFormatting.GOLD : ChatFormatting.GRAY))
			.glow(selected)
			.hideDefaultTooltip()
			.setCallback(onClick);
	}

	/** A heading of a row of the GUI */
	public static GuiElementBuilder label(Component name) {
		return new GuiElementBuilder(Items.PAPER).setName(name.copy()
			.withStyle(ChatFormatting.WHITE))
			.hideDefaultTooltip();
	}

	public static GuiElementBuilder stepButton(boolean plus, int delta, Runnable onClick) {
		return new GuiElementBuilder(plus ? Items.STAINED_GLASS_PANE.pick(DyeColor.LIME) : Items.STAINED_GLASS_PANE.pick(DyeColor.RED))
			.setName(Component.literal((delta > 0 ? "+" : "") + delta)
				.withStyle(plus ? ChatFormatting.GREEN : ChatFormatting.RED))
			.hideDefaultTooltip()
			.setCallback(onClick);
	}

	/** An on/off switch */
	public static GuiElementBuilder toggle(Component name, boolean on, Runnable onClick) {
		return new GuiElementBuilder(Items.DYE.pick(on ? DyeColor.LIME : DyeColor.GRAY))
			.setName(name.copy()
				.withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY))
			.glow(on)
			.hideDefaultTooltip()
			.setCallback(onClick);
	}
}
