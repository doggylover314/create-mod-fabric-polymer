package dev.createpolymer.schematics.table;

import java.util.List;

import dev.createpolymer.schematics.HandlerSlot;
import dev.createpolymer.schematics.ServerSchematicLoader;
import dev.createpolymer.schematics.ServerSchematicLoader.AvailableSchematic;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;

/**
 * Create's SchematicTableScreen and SchematicTableMenu as an sgui chest. The top row is the table: the empty
 * schematic goes in on the left, the written one comes out on the right, with the progress of the writing in
 * between. The schematics the player can choose from (their own folder on the server, then the shared one) are
 * listed below, a page at a time; clicking one selects it and the emerald writes it.
 * <pre>
 *  input | progress x 7 | output
 *  previous | . | . | refresh | title | . | . | . | next
 *  27 schematics
 *  . | . | . | . | write | . | . | . | .
 * </pre>
 */
public class SchematicTableGui extends SimpleGui {

	public static final int INPUT = 0;
	public static final int PROGRESS = 1;
	public static final int OUTPUT = 8;
	public static final int PREVIOUS = 9;
	public static final int REFRESH = 12;
	public static final int TITLE = 13;
	public static final int NEXT = 17;
	public static final int LIST = 18;
	public static final int PAGE_SIZE = 27;
	public static final int WRITE = 49;

	private final SchematicTableBlockEntity table;
	private List<AvailableSchematic> available;
	private int page;
	private AvailableSchematic selected;
	private String shownState = "";

	public SchematicTableGui(ServerPlayer player, SchematicTableBlockEntity table) {
		super(MenuType.GENERIC_9x6, player, true);
		this.table = table;
		setTitle(table.getDisplayName());

		Runnable noop = () -> {};
		setSlot(INPUT, new HandlerSlot(table.inventory, 0, SchematicTableBlockEntity::isValidInput, noop));
		setSlot(OUTPUT, new HandlerSlot(table.inventory, 1, s -> false, noop));
		for (int i = 0; i < 54; i++)
			if (i != INPUT && i != OUTPUT)
				setSlot(i, filler());
		refreshList();
	}

	private static GuiElementBuilder filler() {
		return new GuiElementBuilder(Items.STAINED_GLASS_PANE.gray())
			.setName(Component.empty())
			.hideTooltip();
	}

	@Override
	public boolean stillValid() {
		return table.canPlayerUse(player);
	}

	@Override
	public void onTick() {
		String state = table.isUploading + ":" + (int) (table.uploadingProgress * 20) + ":"
			+ table.inventory.getStackInSlot(0)
				.isEmpty()
			+ ":" + table.inventory.getStackInSlot(1)
				.isEmpty();
		if (!state.equals(shownState))
			refresh();
	}

	/** Create's refresh button: reads the folders again */
	private void refreshList() {
		available = ServerSchematicLoader.getAvailableSchematics(ServerSchematicLoader.playerName(player));
		if (selected != null && !available.contains(selected))
			selected = null;
		page = Math.min(page, Math.max(0, pages() - 1));
		refresh();
	}

	private int pages() {
		return Math.max(1, (available.size() + PAGE_SIZE - 1) / PAGE_SIZE);
	}

	private void refresh() {
		shownState = table.isUploading + ":" + (int) (table.uploadingProgress * 20) + ":"
			+ table.inventory.getStackInSlot(0)
				.isEmpty()
			+ ":" + table.inventory.getStackInSlot(1)
				.isEmpty();
		refreshProgress();
		refreshNavigation();
		refreshEntries();
		refreshWrite();
	}

	private void refreshProgress() {
		boolean finished = !table.inventory.getStackInSlot(1)
			.isEmpty();
		float progress = finished ? 1 : table.isUploading ? table.uploadingProgress : 0;
		int filled = Math.round(progress * 7);
		Component label = table.isUploading
			? Component.translatable("create.gui.schematicTable.uploading")
				.append(Component.literal(" " + Math.round(progress * 100) + "%"))
			: finished ? Component.translatable("create.gui.schematicTable.finished") : Component.empty();
		for (int i = 0; i < 7; i++)
			setSlot(PROGRESS + i, new GuiElementBuilder(i < filled ? Items.STAINED_GLASS_PANE.lime()
				: Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY))
					.setName(label.copy()
						.withStyle(ChatFormatting.WHITE))
					.hideDefaultTooltip());
	}

	private void refreshNavigation() {
		setSlot(PREVIOUS, page > 0 ? new GuiElementBuilder(Items.ARROW)
			.setName(Component.translatableWithFallback("create.gui.schematicTable.previous", "Previous Page"))
			.hideDefaultTooltip()
			.setCallback(() -> {
				page--;
				refresh();
			}) : filler());
		setSlot(NEXT, page < pages() - 1 ? new GuiElementBuilder(Items.ARROW)
			.setName(Component.translatableWithFallback("create.gui.schematicTable.next", "Next Page"))
			.hideDefaultTooltip()
			.setCallback(() -> {
				page++;
				refresh();
			}) : filler());
		setSlot(REFRESH, new GuiElementBuilder(Items.COMPASS)
			.setName(Component.translatable("create.gui.schematicTable.refresh")
				.withStyle(ChatFormatting.WHITE))
			.hideDefaultTooltip()
			.setCallback(this::refreshList));

		Component title = table.isUploading ? Component.translatable("create.gui.schematicTable.uploading")
			: !table.inventory.getStackInSlot(1)
				.isEmpty() ? Component.translatable("create.gui.schematicTable.finished")
					: Component.translatable("create.gui.schematicTable.availableSchematics");
		setSlot(TITLE, new GuiElementBuilder(Items.BOOKSHELF)
			.setName(title.copy()
				.withStyle(ChatFormatting.WHITE))
			.addLoreLine(Component.literal((page + 1) + " / " + pages())
				.withStyle(ChatFormatting.GRAY))
			.hideDefaultTooltip());
	}

	private void refreshEntries() {
		for (int i = 0; i < PAGE_SIZE; i++) {
			int index = page * PAGE_SIZE + i;
			if (index >= available.size()) {
				setSlot(LIST + i, i == 0 && available.isEmpty() ? new GuiElementBuilder(Items.BARRIER)
					.setName(Component.translatable("create.gui.schematicTable.noSchematics")
						.withStyle(ChatFormatting.GRAY))
					.hideDefaultTooltip() : filler());
				continue;
			}
			AvailableSchematic schematic = available.get(index);
			boolean isSelected = schematic.equals(selected);
			setSlot(LIST + i, new GuiElementBuilder(schematic.uploaded() ? Items.FILLED_MAP : Items.PAPER)
				.setName(Component.literal(schematic.name())
					.withStyle(isSelected ? ChatFormatting.AQUA : ChatFormatting.WHITE))
				.addLoreLine(Component.translatableWithFallback(
					schematic.uploaded() ? "create.gui.schematicTable.uploaded" : "create.gui.schematicTable.shared",
					schematic.uploaded() ? "Your schematic" : "Server schematic")
					.withStyle(ChatFormatting.GRAY))
				.glow(isSelected)
				.hideDefaultTooltip()
				.setCallback(() -> {
					selected = schematic;
					refresh();
				}));
		}
	}

	private void refreshWrite() {
		boolean ready = table.canWrite() && selected != null;
		setSlot(WRITE, new GuiElementBuilder(ready ? Items.EMERALD : Items.STAINED_GLASS_PANE.gray())
			.setName(Component.translatableWithFallback("create.gui.schematicTable.write", "Write Schematic")
				.withStyle(ready ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY))
			.addLoreLine(selected == null ? Component.empty() : Component.literal(selected.name())
				.withStyle(ChatFormatting.GRAY))
			.hideDefaultTooltip()
			.setCallback(() -> {
				if (selected != null && table.startWrite(player, selected))
					refresh();
			}));
	}
}
