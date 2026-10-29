package dev.createpolymer.redstone.displayLink;

import java.util.ArrayList;
import java.util.List;

import dev.createpolymer.api.behaviour.display.DisplaySource;
import dev.createpolymer.api.behaviour.display.DisplayTarget;
import dev.createpolymer.redstone.displayLink.ModularGuiLineBuilder.Selection;
import dev.createpolymer.redstone.displayLink.ModularGuiLineBuilder.TextInput;
import dev.createpolymer.redstone.displayLink.ModularGuiLineBuilder.Widget;
import dev.createpolymer.redstone.displayLink.source.SingleLineDisplaySource;
import dev.createpolymer.redstone.displayLink.target.DisplayTargetStats;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Chest GUI that replaces Create's DisplayLinkScreen: what the link reads from the block it is attached to (the
 * type of information, and that type's options, which a source describes with {@link ModularGuiLineBuilder}), and
 * where on the target it writes. Text options open an anvil to type in. Every change is applied straight away
 * (Create sends the settings when the screen closes).
 */
public class DisplayLinkGui extends SimpleGui {
	private static final ItemStack FALLBACK = new ItemStack(Items.BARRIER);
	private static final String PREFIX = "create.display_link.";

	private final DisplayLinkBlockEntity be;
	private List<DisplaySource> sources = List.of();
	private DisplayTarget target;
	private BlockState sourceState;
	private BlockState targetState;

	/** The id of the chosen source and the values of its options, as Create's screen sends them. */
	private CompoundTag configData = new CompoundTag();
	private int sourceIndex;
	private int targetLine;

	public static void open(ServerPlayer player, DisplayLinkBlockEntity be) {
		new DisplayLinkGui(player, be).open();
	}

	public DisplayLinkGui(ServerPlayer player, DisplayLinkBlockEntity be) {
		super(MenuType.GENERIC_9x4, player, false);
		this.be = be;
		setTitle(Component.translatable(PREFIX + "title"));
		setLockPlayerInventory(true);
		load();
		refresh();
	}

	@Override
	public boolean stillValid() {
		return !be.isRemoved() && be.canPlayerUse(player);
	}

	@Override
	public void onTick() {
		// Create's screen sets itself up again when the blocks on either side change
		Level level = be.getLevel();
		if (sourceState != null && sourceState.getBlock() != level.getBlockState(be.getSourcePosition())
			.getBlock()
			|| targetState != null && targetState.getBlock() != level.getBlockState(be.getTargetPosition())
				.getBlock()) {
			load();
			refresh();
		}
	}

	@Override
	public void onPlayerClose(boolean success) {
		apply();
	}

	@Override
	public void onManualClose() {
		apply();
	}

	private void load() {
		Level level = be.getLevel();
		sourceState = level.getBlockState(be.getSourcePosition());
		targetState = level.getBlockState(be.getTargetPosition());
		sources = DisplaySource.getAll(level, be.getSourcePosition());
		target = DisplayTarget.get(level, be.getTargetPosition());

		sourceIndex = be.activeSource == null ? 0 : Math.max(sources.indexOf(be.activeSource), 0);
		configData = new CompoundTag();
		if (!sources.isEmpty()) {
			DisplaySource source = sources.get(sourceIndex);
			source.populateData(new DisplayLinkContext(level, be));
			if (be.activeSource == source)
				configData = be.getSourceConfig()
					.copy();
			Identifier id = DisplaySource.getKey(source);
			if (id != null)
				configData.putString("Id", id.toString());
		}

		targetLine = 0;
		if (target != null) {
			DisplayTargetStats stats = target.provideStats(new DisplayLinkContext(level, be));
			targetLine = Math.min(be.targetLine, stats.maxRows() - 1);
		}
	}

	private static ItemStack iconOf(BlockState state) {
		Block block = state.getBlock();
		ItemStack stack = new ItemStack(block);
		return stack.isEmpty() ? FALLBACK : stack;
	}

	private void refresh() {
		for (int i = 0; i < getSize(); i++)
			clearSlot(i);

		// Row 0: the block that is read from and what it provides
		ItemStack sourceIcon = iconOf(sourceState);
		GuiElementBuilder sourceInfo = new GuiElementBuilder(sourceIcon.getItem())
			.setName(Component.translatable(PREFIX + "reading_from"))
			.addLoreLine(sourceState.getBlock()
				.getName()
				.withStyle(s -> s.withColor(sources.isEmpty() ? 0xF68989 : 0xF2C16D)))
			.addLoreLine(Component.translatable(PREFIX + "attached_side")
				.withStyle(ChatFormatting.GRAY))
			.hideDefaultTooltip();
		setSlot(1, sourceInfo);

		if (sources.isEmpty()) {
			setSlot(3, new GuiElementBuilder(Items.BARRIER)
				.setName(Component.translatable(PREFIX + "no_source")
					.withStyle(ChatFormatting.GRAY))
				.hideDefaultTooltip());
		} else {
			List<Component> names = sources.stream()
				.map(DisplaySource::getName)
				.toList();
			setSlot(3, selector(Items.COMPARATOR, Component.translatable(PREFIX + "information_type"), names,
				sourceIndex, delta -> {
					sourceIndex = Math.floorMod(sourceIndex + delta, sources.size());
					DisplaySource source = sources.get(sourceIndex);
					source.populateData(new DisplayLinkContext(be.getLevel(), be));
					configData = new CompoundTag();
					Identifier id = DisplaySource.getKey(source);
					if (id != null)
						configData.putString("Id", id.toString());
					changed();
				}));

			// Rows 1 and 2: the options of the chosen source, for the first line and for the following ones
			DisplayLinkContext context = new DisplayLinkContext(be.getLevel(), be);
			DisplaySource source = sources.get(sourceIndex);
			for (int line = 0; line < 2; line++) {
				List<Widget> widgets = new ArrayList<>();
				source.initConfigurationWidgets(context, new ModularGuiLineBuilder(widgets), line == 0);
				for (int i = 0; i < widgets.size() && i < 6; i++)
					setSlot((1 + line) * 9 + 3 + i, widgetButton(widgets.get(i)));
			}
		}

		// Row 3: the block that is written to and where on it
		ItemStack targetIcon = iconOf(targetState);
		setSlot(27 + 1, new GuiElementBuilder(targetIcon.getItem())
			.setName(Component.translatable(PREFIX + "writing_to"))
			.addLoreLine(targetState.getBlock()
				.getName()
				.withStyle(s -> s.withColor(target == null ? 0xF68989 : 0xF2C16D)))
			.addLoreLine(Component.translatable(PREFIX + "targeted_location")
				.withStyle(ChatFormatting.GRAY))
			.hideDefaultTooltip());

		if (target == null) {
			setSlot(27 + 3, new GuiElementBuilder(Items.BARRIER)
				.setName(Component.translatable(PREFIX + "no_target")
					.withStyle(ChatFormatting.GRAY))
				.hideDefaultTooltip());
		} else {
			DisplayTargetStats stats = target.provideStats(new DisplayLinkContext(be.getLevel(), be));
			int rows = stats.maxRows();
			if (rows > 1) {
				List<Component> lines = new ArrayList<>();
				for (int i = 0; i < rows; i++)
					lines.add(target.getLineOptionText(i));
				boolean multiline = sources.isEmpty() || !(sources.get(sourceIndex) instanceof SingleLineDisplaySource);
				setSlot(27 + 3, selector(Items.OAK_SIGN, Component.translatable(PREFIX + (multiline ? "display_on_multiline" : "display_on")),
					lines, targetLine, delta -> {
						targetLine = Math.floorMod(targetLine + delta, rows);
						changed();
					}));
			} else {
				setSlot(27 + 3, new GuiElementBuilder(Items.OAK_SIGN)
					.setName(target.getLineOptionText(0)
						.copy()
						.withStyle(ChatFormatting.GOLD))
					.hideDefaultTooltip());
			}
		}

		setSlot(27 + 7, new GuiElementBuilder(Items.EMERALD)
			.setName(Component.translatable("create.gui.value_settings.confirm"))
			.hideDefaultTooltip()
			.setCallback(() -> close()));
	}

	private GuiElementBuilder widgetButton(Widget widget) {
		if (widget instanceof Selection selection) {
			int current = Math.clamp(configData.getIntOr(selection.dataKey(), 0), 0, selection.options()
				.size() - 1);
			return selector(Items.REPEATER, selection.title(), selection.options(), current, delta -> {
				configData.putInt(selection.dataKey(), Math.floorMod(current + delta, selection.options()
					.size()));
				changed();
			});
		}

		TextInput input = (TextInput) widget;
		String value = configData.getStringOr(input.dataKey(), "");
		GuiElementBuilder builder = new GuiElementBuilder(Items.NAME_TAG)
			.setName(input.title()
				.copy()
				.withStyle(s -> s.withColor(0x5391E1)))
			.addLoreLine(value.isEmpty() ? Component.literal("-")
				.withStyle(ChatFormatting.DARK_GRAY)
				: Component.literal(value)
					.withStyle(ChatFormatting.WHITE))
			.addLoreLine(Component.translatable("create.gui.schedule.lmb_edit")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))
			.hideDefaultTooltip();
		return builder.setCallback((index, click, action, gui) -> {
			if (!click.isLeft && !click.isRight)
				return;
			editText(input, value);
		});
	}

	/** A button that goes through a list of options: left click for the next, right click for the previous one. */
	private GuiElementBuilder selector(net.minecraft.world.item.Item icon, Component title, List<Component> options, int current,
		java.util.function.IntConsumer onMove) {
		GuiElementBuilder builder = new GuiElementBuilder(icon)
			.setName(title.copy()
				.withStyle(s -> s.withColor(0x5391E1)))
			.hideDefaultTooltip();
		for (int i = 0; i < options.size(); i++) {
			boolean selected = i == current;
			builder.addLoreLine(Component.literal(selected ? "> " : "  ")
				.append(options.get(i))
				.withStyle(selected ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
		}
		if (options.size() > 1)
			builder.setCallback((index, click, action, gui) -> {
				if (click == ClickType.MOUSE_LEFT || click == ClickType.MOUSE_LEFT_SHIFT)
					onMove.accept(1);
				else if (click == ClickType.MOUSE_RIGHT || click == ClickType.MOUSE_RIGHT_SHIFT)
					onMove.accept(-1);
			});
		return builder;
	}

	private void editText(TextInput input, String current) {
		DisplayLinkBlockEntity be = this.be;
		ServerPlayer player = this.player;
		AnvilInputGui anvil = new AnvilInputGui(player, false) {
			@Override
			public void onPlayerClose(boolean success) {
				back();
			}

			@Override
			public void onManualClose() {
				back();
			}

			/** Back to the link. */
			private void back() {
				if (!be.isRemoved())
					player.level().getServer().execute(() -> new DisplayLinkGui(player, be).open());
			}
		};
		anvil.setTitle(input.title());
		anvil.setDefaultInputValue(current);
		anvil.setSlot(2, new GuiElementBuilder(Items.PAPER)
			.setName(Component.translatable("create.gui.value_settings.confirm"))
			.hideDefaultTooltip()
			.setCallback(() -> {
				configData.putString(input.dataKey(), anvil.getInput());
				apply();
				anvil.close();
			}));
		anvil.open();
	}

	private void changed() {
		apply();
		player.level()
			.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.MASTER, .5f, 1f);
		refresh();
	}

	/** Create's DisplayLinkScreen.onClose: send the chosen source, its options and the line. */
	private void apply() {
		if (be.isRemoved())
			return;
		be.applySettings(sources.isEmpty() ? new CompoundTag() : configData.copy(), targetLine);
	}

	public BlockPos getPos() {
		return be.getBlockPos();
	}
}
