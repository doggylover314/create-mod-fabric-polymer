package dev.createpolymer.equipment.clipboard;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.equipment.ToolsSounds;
import dev.createpolymer.equipment.clipboard.ClipboardOverrides.ClipboardType;
import dev.createpolymer.registry.feature.ToolsRegistry;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Create's ClipboardScreen as an sgui chest. The screen is a checklist: pages of up to 12 entries, each with a
 * checkbox and a line of text (and an icon item for entries a clipboard was printed with).
 * <ul>
 * <li>click the dye to check or uncheck an entry</li>
 * <li>click the text to edit it in an anvil text box (right-click removes the entry); an empty row adds an entry.
 * The anvil limits an entry to 50 characters, which is the one thing the screen can do that this cannot</li>
 * <li>the arrows turn the pages (a new page is added after the last one, up to 50 pages), the sponge erases the
 * checked entries of the page</li>
 * <li>a read-only clipboard can be checked but not edited</li>
 * </ul>
 * Every change is saved at once. When the editor closes, blank entries and empty pages are dropped and a clipboard
 * with nothing left on it goes back to being an empty one.
 */
public class ClipboardGui extends SimpleGui {
	public static final int ENTRIES_PER_PAGE = 12;
	public static final int MAX_PAGE = 50;
	public static final int MAX_TEXT_LENGTH = 50;

	private static final String PREFIX = "create.gui.clipboard.";

	private static final int PREVIOUS_PAGE = 7;
	private static final int NEXT_PAGE = 8;
	private static final int PAGE_INDICATOR = 16;
	private static final int ADD_BUTTON = 25;
	private static final int ERASE_CHECKED = 34;
	private static final int CLOSE_BUTTON = 53;

	/** Where the clipboard's content lives: the held item or a placed clipboard block. */
	public interface Target {
		ClipboardContent get();

		/** Stores the content; null removes it (the clipboard is empty again). */
		void set(@Nullable ClipboardContent content);

		boolean stillValid(ServerPlayer player);

		/** Called after every change; a placed clipboard tells its model and its state. */
		default void onEdited(ServerPlayer editor) {
		}

		Level level();

		BlockPos pos();

		static Target item(ServerPlayer player, int slot) {
			return new Target() {
				private ItemStack stack() {
					return player.getInventory()
						.getItem(slot);
				}

				@Override
				public ClipboardContent get() {
					return stack().getOrDefault(ToolsRegistry.CLIPBOARD_CONTENT, ClipboardContent.EMPTY);
				}

				@Override
				public void set(@Nullable ClipboardContent content) {
					if (content == null)
						stack().remove(ToolsRegistry.CLIPBOARD_CONTENT);
					else
						stack().set(ToolsRegistry.CLIPBOARD_CONTENT, content);
				}

				@Override
				public boolean stillValid(ServerPlayer p) {
					return stack().getItem() instanceof ClipboardBlockItem;
				}

				@Override
				public Level level() {
					return player.level();
				}

				@Override
				public BlockPos pos() {
					return player.blockPosition();
				}
			};
		}

		static Target block(ClipboardBlockEntity cbe) {
			return new Target() {
				@Override
				public ClipboardContent get() {
					return cbe.components()
						.getOrDefault(ToolsRegistry.CLIPBOARD_CONTENT, ClipboardContent.EMPTY);
				}

				@Override
				public void set(@Nullable ClipboardContent content) {
					PatchedDataComponentMap map = new PatchedDataComponentMap(cbe.components());
					if (content == null)
						map.remove(ToolsRegistry.CLIPBOARD_CONTENT);
					else
						map.set(ToolsRegistry.CLIPBOARD_CONTENT, content);
					cbe.setComponents(map);
				}

				@Override
				public boolean stillValid(ServerPlayer p) {
					return !cbe.isRemoved() && cbe.canPlayerUse(p);
				}

				@Override
				public void onEdited(ServerPlayer editor) {
					cbe.onEditedBy(editor);
				}

				@Override
				public Level level() {
					return cbe.getLevel();
				}

				@Override
				public BlockPos pos() {
					return cbe.getBlockPos();
				}
			};
		}
	}

	/** The pages being edited, kept while the editor swaps places with the text box. */
	public static class Session {
		final Target target;
		final ClipboardContent content;
		final List<List<ClipboardEntry>> pages;
		final boolean readOnly;
		@Nullable ServerPlayer editor;
		int currentPage;
		boolean finished;

		Session(Target target) {
			this.target = target;
			this.content = target.get();
			this.pages = ClipboardEntry.readAll(content);
			if (pages.isEmpty())
				pages.add(new ArrayList<>());
			this.currentPage = Math.max(0, Math.min(content.previouslyOpenedPage(), pages.size() - 1));
			this.readOnly = content.readOnly();
		}

		public List<ClipboardEntry> entries() {
			return pages.get(currentPage);
		}

		public List<List<ClipboardEntry>> getPages() {
			return pages;
		}

		public int getCurrentPage() {
			return currentPage;
		}

		/** Keeps the edit on the clipboard, with the editor still open. */
		void save() {
			ClipboardContent toSave = content.setPages(copyPages())
				.setType(ClipboardType.EDITING)
				.setPreviouslyOpenedPage(currentPage);
			target.set(toSave);
			if (editor != null)
				target.onEdited(editor);
		}

		private List<List<ClipboardEntry>> copyPages() {
			List<List<ClipboardEntry>> copy = new ArrayList<>();
			for (List<ClipboardEntry> page : pages)
				copy.add(new ArrayList<>(page));
			return copy;
		}

		/** Create's ClipboardScreen.removed + send: drops blank entries and empty pages and stores the result. */
		void finish() {
			if (finished)
				return;
			finished = true;
			pages.forEach(list -> list.removeIf(ce -> ce.text.getString()
				.isBlank() && ce.icon.isEmpty()));
			List<ClipboardEntry> openedEntries = pages.get(currentPage);
			pages.removeIf(List::isEmpty);
			int opened = 0;
			for (int i = 0; i < pages.size(); i++)
				if (pages.get(i) == openedEntries)
					opened = i;

			ClipboardContent toSend = null;
			if (!pages.isEmpty())
				toSend = content.setPages(copyPages())
					.setType(ClipboardType.WRITTEN)
					.setPreviouslyOpenedPage(opened);
			target.set(toSend);
			if (editor != null)
				target.onEdited(editor);
		}
	}

	private final Session session;
	private boolean keepSession;

	/** Opens the editor on a clipboard and returns it. */
	public static ClipboardGui open(ServerPlayer player, Target target) {
		Session session = new Session(target);
		session.editor = player;
		if (session.pages.size() == 1 && session.pages.getFirst()
			.isEmpty())
			session.save();
		ClipboardGui gui = new ClipboardGui(player, session);
		gui.open();
		return gui;
	}

	private ClipboardGui(ServerPlayer player, Session session) {
		super(MenuType.GENERIC_9x6, player, false);
		this.session = session;
		setTitle(Component.translatable("block.create.clipboard"));
		refresh();
	}

	/** Keys Create has are looked up as usual; the ones only this port has carry an English fallback. */
	static MutableComponent text(String key, String fallback) {
		return Component.translatableWithFallback(key, fallback);
	}

	private static GuiElementBuilder filler() {
		return new GuiElementBuilder(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY)).setName(Component.empty())
			.hideTooltip();
	}

	private static int checkSlot(int entry) {
		int group = entry / 6;
		return entry % 6 * 9 + group * 3;
	}

	private void refresh() {
		for (int i = 0; i < 54; i++)
			setSlot(i, filler());

		List<ClipboardEntry> entries = session.entries();
		for (int i = 0; i < ENTRIES_PER_PAGE; i++) {
			int checkSlot = checkSlot(i);
			if (i < entries.size()) {
				ClipboardEntry entry = entries.get(i);
				setSlot(checkSlot, new GuiElementBuilder(Items.DYE.pick(entry.checked ? DyeColor.LIME : DyeColor.GRAY))
					.setName(Component.literal(entry.checked ? "☑" : "☐")
						.withStyle(entry.checked ? ChatFormatting.GREEN : ChatFormatting.GRAY))
					.hideDefaultTooltip()
					.setCallback(() -> toggle(entry)));
				setSlot(checkSlot + 1, entryButton(entry, i));
			} else if (!session.readOnly) {
				setSlot(checkSlot + 1, new GuiElementBuilder(Items.PAPER)
					.setName(ClipboardGui.text(PREFIX + "new_entry", "Add an entry")
						.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))
					.hideDefaultTooltip()
					.setCallback(() -> editEntry(-1)));
			}
		}

		boolean hasNext = session.currentPage < MAX_PAGE && (!session.readOnly || session.currentPage + 1 < session.pages.size());
		if (session.currentPage > 0)
			setSlot(PREVIOUS_PAGE, new GuiElementBuilder(Items.ARROW)
				.setName(Component.translatable("book.pageIndicator", session.currentPage, session.pages.size())
					.withStyle(ChatFormatting.GOLD))
				.hideDefaultTooltip()
				.setCallback(() -> changePage(false)));
		if (hasNext)
			setSlot(NEXT_PAGE, new GuiElementBuilder(Items.ARROW)
				.setName(Component.translatable("book.pageIndicator", session.currentPage + 2, Math.max(session.pages.size(), session.currentPage + 2))
					.withStyle(ChatFormatting.GOLD))
				.hideDefaultTooltip()
				.setCallback(() -> changePage(true)));
		setSlot(PAGE_INDICATOR, new GuiElementBuilder(Items.BOOK)
			.setName(Component.translatable("book.pageIndicator", session.currentPage + 1, session.pages.size())
				.withStyle(ChatFormatting.WHITE))
			.hideDefaultTooltip());

		if (!session.readOnly) {
			setSlot(ADD_BUTTON, new GuiElementBuilder(Items.WRITABLE_BOOK)
				.setName(ClipboardGui.text(PREFIX + "new_entry", "Add an entry")
					.withStyle(ChatFormatting.GREEN))
				.hideDefaultTooltip()
				.setCallback(() -> editEntry(-1)));
			setSlot(ERASE_CHECKED, new GuiElementBuilder(Items.SPONGE)
				.setName(Component.translatable("create.gui.clipboard.erase_checked")
					.withStyle(ChatFormatting.RED))
				.hideDefaultTooltip()
				.setCallback(this::eraseChecked));
		}
		setSlot(CLOSE_BUTTON, new GuiElementBuilder(Items.BARRIER)
			.setName(ClipboardGui.text(PREFIX + "close", "Close")
				.withStyle(ChatFormatting.RED))
			.hideDefaultTooltip()
			.setCallback(() -> close()));
	}

	private GuiElementBuilder entryButton(ClipboardEntry entry, int index) {
		GuiElementBuilder builder = entry.icon.isEmpty() ? new GuiElementBuilder(Items.PAPER)
			: GuiElementBuilder.from(entry.icon.copyWithCount(Math.max(1, Math.min(entry.itemAmount, 99))));
		MutableComponent name = Component.empty()
			.withStyle(Style.EMPTY.withItalic(false))
			.append(entry.checked ? entry.text.copy()
				.withStyle(ChatFormatting.STRIKETHROUGH, ChatFormatting.GRAY) : entry.text.copy());
		builder.setName(name)
			.hideDefaultTooltip();
		if (!session.readOnly) {
			builder.addLoreLine(ClipboardGui.text(PREFIX + "click_to_edit", "Click to edit")
				.withStyle(ChatFormatting.DARK_GRAY));
			builder.addLoreLine(ClipboardGui.text(PREFIX + "right_click_to_remove", "Right-click to remove")
				.withStyle(ChatFormatting.DARK_GRAY));
			builder.setCallback((ClickType type) -> {
				if (type.isRight)
					removeEntry(index);
				else
					editEntry(index);
			});
		}
		return builder;
	}

	private void toggle(ClipboardEntry entry) {
		entry.checked = !entry.checked;
		if (entry.checked)
			ToolsSounds.clipboardCheck(session.target.level(), session.target.pos());
		else
			ToolsSounds.clipboardErase(session.target.level(), session.target.pos());
		session.save();
		refresh();
	}

	private void removeEntry(int index) {
		List<ClipboardEntry> entries = session.entries();
		if (index < entries.size())
			entries.remove(index);
		session.save();
		refresh();
	}

	private void eraseChecked() {
		session.entries()
			.removeIf(ce -> ce.checked);
		ToolsSounds.clipboardErase(session.target.level(), session.target.pos());
		session.save();
		refresh();
	}

	/** Create's ClipboardScreen.changePage */
	private void changePage(boolean next) {
		int previously = session.currentPage;
		session.currentPage = Math.max(0, Math.min(session.currentPage + (next ? 1 : -1), MAX_PAGE));
		if (session.currentPage == previously)
			return;
		if (session.pages.size() <= session.currentPage) {
			if (session.readOnly) {
				session.currentPage = previously;
				return;
			}
			session.pages.add(new ArrayList<>());
		}
		session.save();
		refresh();
	}

	/** Create edits the text in place; here an anvil text box takes the input. index -1 adds an entry. */
	private void editEntry(int index) {
		if (session.readOnly)
			return;
		List<ClipboardEntry> entries = session.entries();
		if (index < 0 && entries.size() >= ENTRIES_PER_PAGE)
			return;
		String text = index >= 0 && index < entries.size() ? entries.get(index).text.getString() : "";
		keepSession = true;
		ServerPlayer player = this.player;
		Session session = this.session;
		boolean[] handled = {false};
		AnvilInputGui anvil = new AnvilInputGui(player, false) {
			@Override
			public void onRemoved() {
				super.onRemoved();
				if (!handled[0]) {
					handled[0] = true;
					session.finish();
				}
			}
		};
		anvil.setTitle(ClipboardGui.text(PREFIX + "edit_entry", "Edit entry"));
		anvil.setDefaultInputValue(text);
		anvil.setSlot(2, new GuiElementBuilder(Items.NAME_TAG)
			.setName(ClipboardGui.text(PREFIX + "confirm", "Confirm")
				.withStyle(ChatFormatting.GREEN))
			.setCallback(() -> {
				handled[0] = true;
				String input = anvil.getInput();
				if (input.length() > MAX_TEXT_LENGTH)
					input = input.substring(0, MAX_TEXT_LENGTH);
				List<ClipboardEntry> current = session.entries();
				if (index >= 0 && index < current.size()) {
					if (input.isBlank())
						current.remove(index);
					else
						current.get(index).text = Component.literal(input);
				} else if (!input.isBlank() && current.size() < ENTRIES_PER_PAGE) {
					current.add(new ClipboardEntry(false, Component.literal(input)));
				}
				session.save();
				new ClipboardGui(player, session).open();
			}));
		anvil.open();
	}

	@Override
	public boolean stillValid() {
		return session.target.stillValid(player);
	}

	@Override
	public void onRemoved() {
		if (keepSession)
			return;
		session.finish();
	}
}
