package dev.createpolymer.contraptions.elevator;

import java.util.ArrayList;
import java.util.List;

import dev.createpolymer.decoration.slidingDoor.DoorControlGui;
import dev.createpolymer.util.Couple;
import dev.createpolymer.util.IntAttached;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Chest GUI that replaces Create's elevator contact screen and the floor selection of the contraption controls: it
 * lists the floors (contacts) of the column this contact belongs to, calls the elevator to the one that is clicked,
 * and names the floor of this contact (Create's two text boxes become two anvil prompts).
 */
public class ElevatorContactGui extends SimpleGui {
	private static final String PREFIX = "create.elevator_contact.";
	private static final int FLOORS_PER_PAGE = 27;
	private static final int MAX_SHORT_NAME = 4;
	private static final int MAX_LONG_NAME = 30;

	private final ElevatorContactBlockEntity be;
	private int page;

	public static void open(ServerPlayer player, ElevatorContactBlockEntity be) {
		new ElevatorContactGui(player, be).open();
	}

	public ElevatorContactGui(ServerPlayer player, ElevatorContactBlockEntity be) {
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

	@Override
	public void onTick() {
		if (player.tickCount % 10 == 0)
			refresh();
	}

	private ElevatorColumn column() {
		return be.columnCoords == null ? null : ElevatorColumn.get(be.getLevel(), be.columnCoords);
	}

	static String floorName(Couple<String> names, int y) {
		String shortName = names.getFirst();
		String longName = names.getSecond();
		StringBuilder name = new StringBuilder();
		if (!shortName.isBlank())
			name.append('[')
				.append(shortName)
				.append("] ");
		if (!longName.isBlank())
			name.append(longName);
		else if (shortName.isBlank())
			name.append("y = ")
				.append(y);
		return name.toString()
			.trim();
	}

	private void refresh() {
		Level level = be.getLevel();
		ElevatorColumn column = column();
		List<IntAttached<Couple<String>>> floors =
			column == null ? List.of() : new ArrayList<>(column.compileNamesList());
		// highest floor first
		java.util.Collections.reverse(floors);

		int pages = Math.max(1, (floors.size() + FLOORS_PER_PAGE - 1) / FLOORS_PER_PAGE);
		page = Math.min(page, pages - 1);

		for (int slot = 0; slot < FLOORS_PER_PAGE; slot++) {
			int index = page * FLOORS_PER_PAGE + slot;
			if (index >= floors.size()) {
				clearSlot(slot);
				continue;
			}
			setSlot(slot, floorButton(level, column, floors.get(index)));
		}

		Component title = Component.literal(floorName(be.getNames(), be.getBlockPos()
			.getY()))
			.withStyle(ChatFormatting.GOLD);
		setSlot(27, new GuiElementBuilder(Items.NAME_TAG)
			.setName(Component.translatable(PREFIX + "floor_identifier")
				.withStyle(ChatFormatting.BLUE))
			.addLoreLine(title)
			.addLoreLine(Component.translatableWithFallback(PREFIX + "click_to_edit", "Click to rename this floor")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))
			.hideDefaultTooltip()
			.setCallback(this::editNames));

		setSlot(28, DoorControlGui.button(player, be.doorControls, this::refresh));

		boolean active = column != null && column.isActive();
		setSlot(31, new GuiElementBuilder(active ? Items.DYE.pick(DyeColor.LIME) : Items.DYE.pick(DyeColor.GRAY))
			.setName(Component.translatableWithFallback(active ? PREFIX + "active" : PREFIX + "inactive",
				active ? "An elevator serves this column" : "No elevator serves this column")
				.withStyle(active ? ChatFormatting.GREEN : ChatFormatting.GRAY))
			.hideDefaultTooltip());

		if (page > 0)
			setSlot(29, new GuiElementBuilder(Items.ARROW)
				.setName(Component.translatableWithFallback(PREFIX + "previous_page", "Higher floors"))
				.hideDefaultTooltip()
				.setCallback(() -> {
					page--;
					refresh();
				}));
		else
			clearSlot(29);
		if (page < pages - 1)
			setSlot(33, new GuiElementBuilder(Items.ARROW)
				.setName(Component.translatableWithFallback(PREFIX + "next_page", "Lower floors"))
				.hideDefaultTooltip()
				.setCallback(() -> {
					page++;
					refresh();
				}));
		else
			clearSlot(33);

		setSlot(35, new GuiElementBuilder(Items.EMERALD)
			.setName(Component.translatable("create.gui.value_settings.confirm"))
			.hideDefaultTooltip()
			.setCallback(() -> close()));
	}

	private GuiElementBuilder floorButton(Level level, ElevatorColumn column, IntAttached<Couple<String>> floor) {
		int y = floor.getFirst();
		BlockPos contactPos = column.contactAt(y);
		BlockState state = level.getBlockState(contactPos);
		boolean here = state.getBlock() instanceof ElevatorContactBlock && state.getValue(ElevatorContactBlock.POWERING);
		boolean target = column.isTargetAvailable() && column.getTargetedYLevel() == y;
		boolean own = contactPos.equals(be.getBlockPos());

		Item icon = here ? Items.DYE.pick(DyeColor.LIME) : target ? Items.DYE.pick(DyeColor.YELLOW) : Items.DYE.pick(DyeColor.GRAY);
		MutableComponent name = Component.literal(floorName(floor.getSecond(), y));
		name.withStyle(here ? ChatFormatting.GREEN : target ? ChatFormatting.YELLOW : ChatFormatting.WHITE);

		GuiElementBuilder builder = new GuiElementBuilder(icon)
			.setName(name)
			.addLoreLine(Component.literal("y = " + y)
				.withStyle(ChatFormatting.GRAY))
			.glow(own)
			.hideDefaultTooltip();
		if (here)
			builder.addLoreLine(Component.translatableWithFallback(PREFIX + "elevator_here", "The elevator is on this floor")
				.withStyle(ChatFormatting.GREEN));
		else if (column.isActive())
			builder.addLoreLine(Component.translatableWithFallback(PREFIX + "call", "Click to call the elevator")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		return builder.setCallback(() -> {
			call(y);
			refresh();
		});
	}

	/** What Create's ElevatorTargetFloorPacket does when the controls select a floor. */
	private void call(int y) {
		Level level = be.getLevel();
		ElevatorColumn column = column();
		if (column == null || !column.hasContactAt(y))
			return;
		BlockPos pos = column.contactAt(y);
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof ElevatorContactBlock ecb))
			return;
		ecb.callToContactAndUpdate(column, state, level, pos, false);
	}

	private void editNames() {
		ServerPlayer player = this.player;
		ElevatorContactBlockEntity be = this.be;

		AnvilInputGui shortGui = new AnvilInputGui(player, false);
		shortGui.setTitle(Component.translatable(PREFIX + "floor_identifier"));
		shortGui.setDefaultInputValue(be.shortName);
		shortGui.setSlot(2, new GuiElementBuilder(Items.NAME_TAG)
			.setName(Component.translatableWithFallback(PREFIX + "next", "Next")
				.withStyle(ChatFormatting.GREEN))
			.hideDefaultTooltip()
			.setCallback(() -> {
				String shortName = limit(shortGui.getInput(), MAX_SHORT_NAME);

				AnvilInputGui longGui = new AnvilInputGui(player, false);
				longGui.setTitle(Component.translatable(PREFIX + "floor_description"));
				longGui.setDefaultInputValue(be.longName);
				longGui.setSlot(2, new GuiElementBuilder(Items.NAME_TAG)
					.setName(Component.translatableWithFallback(PREFIX + "confirm", "Confirm")
						.withStyle(ChatFormatting.GREEN))
					.hideDefaultTooltip()
					.setCallback(() -> {
						be.updateName(shortName, limit(longGui.getInput(), MAX_LONG_NAME));
						new ElevatorContactGui(player, be).open();
					}));
				longGui.open();
			}));
		shortGui.open();
	}

	private static String limit(String input, int length) {
		return input.length() > length ? input.substring(0, length) : input;
	}
}
