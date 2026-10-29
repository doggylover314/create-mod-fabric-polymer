package dev.createpolymer.schematics.cannon;

import java.util.Objects;

import dev.createpolymer.schematics.HandlerSlot;
import dev.createpolymer.schematics.cannon.ConfigureSchematicannon.Option;
import dev.createpolymer.schematics.cannon.SchematicannonBlockEntity.State;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Create's SchematicannonScreen and SchematicannonMenu as an sgui chest. The five slots of the cannon are real
 * slots (schematic, its output, book, checklist output, gunpowder); the progress bars, the status text, the play,
 * pause and stop buttons and the printer settings are items that are redrawn whenever the cannon's state changes.
 * <pre>
 *  gunpowder | fuel | . | checklist printer | status | missing item | . | book | checklist
 *  progress bar of the schematic
 *  schematic | . | play | pause | stop | . | skip missing | protect block entities | schematic output
 *  . | . | replace: don't | solid with solid | solid with any | with empty | . | . | .
 * </pre>
 */
public class SchematicannonGui extends SimpleGui {

	public static final int GUNPOWDER = 0;
	public static final int FUEL = 1;
	public static final int PRINTER = 3;
	public static final int STATUS = 4;
	public static final int MISSING = 5;
	public static final int BOOK = 7;
	public static final int CHECKLIST = 8;
	public static final int PROGRESS = 9;
	public static final int SCHEMATIC = 18;
	public static final int PLAY = 20;
	public static final int PAUSE = 21;
	public static final int STOP = 22;
	public static final int SKIP_MISSING = 24;
	public static final int PROTECT = 25;
	public static final int SCHEMATIC_OUT = 26;
	public static final int REPLACE = 29;

	private final SchematicannonBlockEntity be;
	private record Snapshot(State state, String status, ItemStack missing, int fuel, int gunpowder, boolean creative,
		int progress, int printing, int replaceMode, boolean skipMissing, boolean replaceBlockEntities, int placed,
		int toPlace, boolean schematic) {
		@Override
		public boolean equals(Object o) {
			return o instanceof Snapshot s && state == s.state && status.equals(s.status)
				&& ItemStack.matches(missing, s.missing) && fuel == s.fuel && gunpowder == s.gunpowder
				&& creative == s.creative && progress == s.progress && printing == s.printing
				&& replaceMode == s.replaceMode && skipMissing == s.skipMissing
				&& replaceBlockEntities == s.replaceBlockEntities && placed == s.placed && toPlace == s.toPlace
				&& schematic == s.schematic;
		}

		@Override
		public int hashCode() {
			return Objects.hash(state, status, fuel, gunpowder, progress, printing, replaceMode);
		}
	}

	private Snapshot last;

	public SchematicannonGui(ServerPlayer player, SchematicannonBlockEntity be) {
		super(MenuType.GENERIC_9x5, player, true);
		this.be = be;
		setTitle(be.getDisplayName());

		Runnable noop = () -> {};
		SchematicannonInventory inv = be.inventory;
		setSlot(GUNPOWDER, new HandlerSlot(inv, 4, s -> inv.isItemValid(4, s), noop));
		setSlot(BOOK, new HandlerSlot(inv, 2, s -> inv.isItemValid(2, s), noop));
		setSlot(CHECKLIST, new HandlerSlot(inv, 3, s -> false, noop));
		setSlot(SCHEMATIC, new HandlerSlot(inv, 0, s -> inv.isItemValid(0, s), noop));
		setSlot(SCHEMATIC_OUT, new HandlerSlot(inv, 1, s -> false, noop));

		for (int i = 0; i < 45; i++)
			if (i != GUNPOWDER && i != BOOK && i != CHECKLIST && i != SCHEMATIC && i != SCHEMATIC_OUT)
				setSlot(i, filler());
		refresh();
	}

	private static GuiElementBuilder filler() {
		return new GuiElementBuilder(Items.STAINED_GLASS_PANE.gray())
			.setName(Component.empty())
			.hideTooltip();
	}

	private Snapshot snapshot() {
		return new Snapshot(be.state, be.statusMsg, be.missingItem == null ? ItemStack.EMPTY : be.missingItem.copy(),
			be.remainingFuel, be.inventory.getStackInSlot(4)
				.getCount(),
			be.hasCreativeCrate, Math.round(Math.min(be.schematicProgress, 1) * 100),
			Math.round(be.bookPrintingProgress * 20), be.replaceMode, be.skipMissing, be.replaceBlockEntities,
			be.blocksPlaced, be.blocksToPlace, !be.inventory.getStackInSlot(0)
				.isEmpty());
	}

	@Override
	public void onTick() {
		Snapshot now = snapshot();
		if (!now.equals(last))
			refresh();
	}

	@Override
	public boolean stillValid() {
		return be.canPlayerUse(player);
	}

	private void refresh() {
		last = snapshot();
		refreshStatus();
		refreshFuel();
		refreshProgress();
		refreshControls();
		refreshSettings();
	}

	private void refreshStatus() {
		MutableComponent msg = Component.translatable("create.schematicannon.status." + be.statusMsg);
		ChatFormatting color = switch (be.state) {
			case RUNNING -> ChatFormatting.GREEN;
			case PAUSED -> ChatFormatting.YELLOW;
			case STOPPED -> ChatFormatting.RED;
		};
		GuiElementBuilder status = new GuiElementBuilder(Items.PAPER)
			.setName(msg.withStyle(color))
			.hideDefaultTooltip();
		if ("schematicErrored".equals(be.statusMsg))
			status.addLoreLine(Component.translatable("create.schematicannon.status.schematicErroredCheckLogs")
				.withStyle(ChatFormatting.GRAY));
		if (be.blocksToPlace > 0)
			status.addLoreLine(Component.literal(be.blocksPlaced + " / " + be.blocksToPlace)
				.withStyle(ChatFormatting.GRAY));
		setSlot(STATUS, status);

		if (be.missingItem != null && !be.missingItem.isEmpty())
			setSlot(MISSING, GuiElementBuilder.from(be.missingItem.copy())
				.setCount(1)
				.addLoreLine(Component.translatable("create.schematicannon.status.missingBlock")
					.withStyle(ChatFormatting.RED)));
		else
			setSlot(MISSING, filler());

		// the checklist printer
		int printing = Math.round(be.bookPrintingProgress * 100);
		setSlot(PRINTER, new GuiElementBuilder(Items.WRITABLE_BOOK)
			.setName(Component.translatable("create.gui.schematicannon.listPrinter")
				.withStyle(ChatFormatting.BLUE))
			.addLoreLine(Component.literal(printing + "%")
				.withStyle(ChatFormatting.GRAY))
			.addLoreLine(Component.translatable("create.gui.schematicannon.slot.listPrinter")
				.withStyle(ChatFormatting.DARK_GRAY))
			.hideDefaultTooltip());
	}

	private void refreshFuel() {
		int shotsPerGunpowder = be.getShotsPerGunpowder();
		GuiElementBuilder fuel = new GuiElementBuilder(Items.FIRE_CHARGE)
			.hideDefaultTooltip();
		if (be.hasCreativeCrate) {
			fuel.setName(Component.translatable("create.gui.schematicannon.gunpowderLevel", "100")
				.withStyle(ChatFormatting.WHITE));
			fuel.addLoreLine(Component.literal("(")
				.append(dev.createpolymer.registry.feature.MiscRegistry.CREATIVE_CRATE.getName())
				.append(")")
				.withStyle(ChatFormatting.DARK_PURPLE));
		} else {
			int shotsLeft = be.remainingFuel;
			int shotsLeftWithItems = shotsLeft + be.inventory.getStackInSlot(4)
				.getCount() * shotsPerGunpowder;
			int fillPercent = shotsPerGunpowder == 0 ? 0 : (int) ((be.remainingFuel / (float) shotsPerGunpowder) * 100);
			fuel.setName(Component.translatable("create.gui.schematicannon.gunpowderLevel", fillPercent)
				.withStyle(ChatFormatting.WHITE));
			fuel.addLoreLine(Component.translatable("create.gui.schematicannon.shotsRemaining",
				Component.literal(Integer.toString(shotsLeft))
					.withStyle(ChatFormatting.BLUE))
				.withStyle(ChatFormatting.GRAY));
			if (shotsLeftWithItems != shotsLeft)
				fuel.addLoreLine(Component.translatable("create.gui.schematicannon.shotsRemainingWithBackup",
					Component.literal(Integer.toString(shotsLeftWithItems))
						.withStyle(ChatFormatting.BLUE))
					.withStyle(ChatFormatting.GRAY));
		}
		fuel.addLoreLine(Component.translatable("create.gui.schematicannon.slot.gunpowder")
			.withStyle(ChatFormatting.DARK_GRAY));
		setSlot(FUEL, fuel);
	}

	private void refreshProgress() {
		float progress = Math.min(be.schematicProgress, 1);
		int percent = Math.round(progress * 100);
		int filled = Math.round(progress * 9);
		for (int i = 0; i < 9; i++)
			setSlot(PROGRESS + i, new GuiElementBuilder(i < filled ? Items.STAINED_GLASS_PANE.lime()
				: Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY))
					.setName(Component.literal(percent + "%")
						.withStyle(ChatFormatting.WHITE))
					.hideDefaultTooltip());
	}

	private void refreshControls() {
		setSlot(PLAY, control(Items.DYE.pick(DyeColor.LIME), "create.schematicannon.status.running", be.state == State.RUNNING,
			Option.PLAY, be.state != State.RUNNING));
		setSlot(PAUSE, control(Items.DYE.pick(DyeColor.YELLOW), "create.schematicannon.status.paused", be.state == State.PAUSED,
			Option.PAUSE, be.state == State.RUNNING));
		setSlot(STOP, control(Items.DYE.pick(DyeColor.RED), "create.schematicannon.status.stopped", be.state == State.STOPPED,
			Option.STOP, be.state != State.STOPPED));
	}

	private GuiElementBuilder control(Item icon, String key, boolean current, Option option, boolean enabled) {
		GuiElementBuilder button = new GuiElementBuilder(icon)
			.setName(Component.translatable(key)
				.withStyle(current ? ChatFormatting.WHITE : ChatFormatting.GRAY))
			.glow(current)
			.hideDefaultTooltip();
		if (enabled)
			button.setCallback(() -> {
				ConfigureSchematicannon.apply(be, option, true);
				refresh();
			});
		return button;
	}

	private void refreshSettings() {
		setSlot(SKIP_MISSING, toggle(Items.ENDER_EYE, "create.gui.schematicannon.option.skipMissing",
			"skipMissing", be.skipMissing, Option.SKIP_MISSING));
		setSlot(PROTECT, toggle(Items.CHEST, "create.gui.schematicannon.option.skipBlockEntities",
			"skipBlockEntities", !be.replaceBlockEntities, Option.SKIP_BLOCK_ENTITIES));

		Item[] icons = { Items.BARRIER, Items.STONE, Items.TNT, Items.STRUCTURE_VOID };
		String[] names = { "dontReplaceSolid", "replaceWithSolid", "replaceWithAny", "replaceWithEmpty" };
		for (int i = 0; i < 4; i++) {
			int mode = i;
			boolean selected = be.replaceMode == mode;
			GuiElementBuilder button = new GuiElementBuilder(icons[i])
				.setName(Component.translatable("create.gui.schematicannon.option." + names[i])
					.withStyle(selected ? ChatFormatting.WHITE : ChatFormatting.GRAY))
				.addLoreLine(Component.translatable("create.gui.schematicannon.option." + names[i] + ".description")
					.withStyle(ChatFormatting.DARK_GRAY))
				.glow(selected)
				.hideDefaultTooltip()
				.setCallback(() -> {
					if (be.replaceMode != mode)
						ConfigureSchematicannon.apply(be, Option.values()[mode], true);
					refresh();
				});
			setSlot(REPLACE + i, button);
		}
	}

	private GuiElementBuilder toggle(Item icon, String key, String description, boolean enabled, Option option) {
		return new GuiElementBuilder(icon)
			.setName(Component.translatable(key)
				.withStyle(enabled ? ChatFormatting.WHITE : ChatFormatting.GRAY))
			.addLoreLine(Component.translatable(enabled ? "create.gui.schematicannon.optionEnabled"
				: "create.gui.schematicannon.optionDisabled")
				.withStyle(enabled ? ChatFormatting.DARK_GREEN : ChatFormatting.RED))
			.addLoreLine(Component.translatable("create.gui.schematicannon.option." + description + ".description")
				.withStyle(ChatFormatting.DARK_GRAY))
			.glow(enabled)
			.hideDefaultTooltip()
			.setCallback(() -> {
				boolean now = option == Option.SKIP_MISSING ? !be.skipMissing : !be.replaceBlockEntities;
				ConfigureSchematicannon.apply(be, option, now);
				refresh();
			});
	}
}
