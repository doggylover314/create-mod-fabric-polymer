package dev.createpolymer.foundation.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import dev.createpolymer.logistics.filter.AttributeFilterWhitelistMode;
import dev.createpolymer.logistics.item.filter.attribute.ItemAttribute;
import dev.createpolymer.logistics.item.filter.attribute.ItemAttribute.ItemAttributeEntry;
import dev.createpolymer.registry.feature.ToolsRegistry;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Create's AttributeFilterMenu / AttributeFilterScreen as an sgui chest. Put a reference item into the ghost slot,
 * click the attribute button to cycle through the attributes that item has (right-click goes back), then add the
 * shown attribute or its opposite to the list. The three mode buttons pick how the list is used (any, all, none) and
 * the reset button empties the list. Every change is written into the filter item.
 */
public class AttributeFilterGui extends HeldItemGhostItemGui {
	private static final String PREFIX = "create.gui.attribute_filter.";

	private static final int REFERENCE_SLOT = 0;
	private static final int SELECTOR_BUTTON = 2;
	private static final int ADD_BUTTON = 3;
	private static final int ADD_INVERTED_BUTTON = 4;
	private static final int WHITELIST_DISJ_BUTTON = 10;
	private static final int WHITELIST_CONJ_BUTTON = 11;
	private static final int BLACKLIST_BUTTON = 12;
	private static final int SELECTED_DISPLAY = 14;
	private static final int RESET_BUTTON = 26;
	private static final int LORE_OPTIONS = 8;

	public AttributeFilterWhitelistMode whitelistMode;
	public final List<ItemAttributeEntry> selectedAttributes = new ArrayList<>();

	private ItemStack lastItemScanned = ItemStack.EMPTY;
	private final List<ItemAttribute> attributesOfItem = new ArrayList<>();
	private int selectedIndex;

	public AttributeFilterGui(ServerPlayer player, ItemStack filterItem) {
		super(MenuType.GENERIC_9x3, player, filterItem.getItemName(), filterItem, new ItemStackHandler(1));
		whitelistMode = filterItem.getOrDefault(ToolsRegistry.ATTRIBUTE_FILTER_WHITELIST_MODE,
			AttributeFilterWhitelistMode.WHITELIST_DISJ);
		selectedAttributes.addAll(filterItem.getOrDefault(ToolsRegistry.ATTRIBUTE_FILTER_MATCHED_ATTRIBUTES, List.of()));
		for (int i = 0; i < 27; i++)
			if (i != REFERENCE_SLOT)
				setSlot(i, filler());
		referenceItemChanged();
		refreshButtons();
	}

	private static GuiElementBuilder filler() {
		return new GuiElementBuilder(GuiText.glassPane(DyeColor.GRAY)).setName(Component.empty())
			.hideTooltip();
	}

	@Override
	protected boolean allowRepeats() {
		return false;
	}

	/** The reference item is a scratch slot, only the mode and the attribute list belong to the filter. */
	@Override
	protected void saveData(ItemStack filterItem) {
		filterItem.set(ToolsRegistry.ATTRIBUTE_FILTER_WHITELIST_MODE, whitelistMode);
		filterItem.set(ToolsRegistry.ATTRIBUTE_FILTER_MATCHED_ATTRIBUTES, List.copyOf(selectedAttributes));
	}

	@Override
	protected void onGhostChanged() {
		referenceItemChanged();
		refreshButtons();
	}

	@Override
	public ItemStack quickMove(int index) {
		if (index < getSize() || isProtectedSlot(index))
			return ItemStack.EMPTY;
		Slot slot = getSlotRedirectOrPlayer(index);
		if (slot == null || !slot.hasItem())
			return ItemStack.EMPTY;
		ItemStack copy = slot.getItem()
			.copy();
		copy.setCount(1);
		ghostInventory.setStackInSlot(REFERENCE_SLOT, copy);
		refreshGhostSlot(REFERENCE_SLOT);
		onGhostChanged();
		return ItemStack.EMPTY;
	}

	/** The attributes of the reference item, as Create's screen lists them in its selector. */
	private void referenceItemChanged() {
		ItemStack stack = ghostInventory.getStackInSlot(REFERENCE_SLOT);
		if (ItemStack.matches(stack, lastItemScanned))
			return;
		lastItemScanned = stack.copy();
		attributesOfItem.clear();
		selectedIndex = 0;
		if (stack.isEmpty())
			return;
		attributesOfItem.addAll(ItemAttribute.getAllAttributes(stack, player.level()));
	}

	public List<ItemAttribute> getAttributesOfItem() {
		return attributesOfItem;
	}

	public void selectAttribute(int index) {
		if (attributesOfItem.isEmpty())
			return;
		selectedIndex = Math.floorMod(index, attributesOfItem.size());
		refreshButtons();
	}

	public int getSelectedIndex() {
		return selectedIndex;
	}

	/** Create's AttributeFilterScreen.handleAddedAttibute. Returns whether an attribute was added. */
	public boolean addSelectedAttribute(boolean inverted) {
		if (selectedIndex >= attributesOfItem.size())
			return false;
		ItemAttribute itemAttribute = attributesOfItem.get(selectedIndex);
		if (isSelected(itemAttribute))
			return false;
		selectedAttributes.add(new ItemAttributeEntry(itemAttribute, inverted));
		onGhostChangedSaveOnly();
		return true;
	}

	public void setWhitelistMode(AttributeFilterWhitelistMode mode) {
		whitelistMode = mode;
		onGhostChangedSaveOnly();
	}

	public void clearSelectedAttributes() {
		selectedAttributes.clear();
		onGhostChangedSaveOnly();
	}

	private void onGhostChangedSaveOnly() {
		saveData(contentHolder);
		refreshButtons();
	}

	private boolean isSelected(ItemAttribute attribute) {
		Optional<Tag> encoded = ItemAttribute.CODEC.encodeStart(NbtOps.INSTANCE, attribute)
			.result();
		for (ItemAttributeEntry existing : selectedAttributes)
			if (existing.attribute()
				.equals(attribute)
				|| encoded.isPresent() && encoded.equals(ItemAttribute.CODEC.encodeStart(NbtOps.INSTANCE, existing.attribute())
					.result()))
				return true;
		return false;
	}

	private void refreshButtons() {
		boolean hasReference = !attributesOfItem.isEmpty();
		boolean addable = hasReference && selectedIndex < attributesOfItem.size()
			&& !isSelected(attributesOfItem.get(selectedIndex));

		// the attribute selector
		if (ghostInventory.getStackInSlot(REFERENCE_SLOT)
			.isEmpty()) {
			setSlot(SELECTOR_BUTTON, new GuiElementBuilder(Items.PAPER)
				.setName(Component.translatable(PREFIX + "add_reference_item")
					.withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY))
				.hideDefaultTooltip());
		} else if (!hasReference) {
			setSlot(SELECTOR_BUTTON, new GuiElementBuilder(Items.BARRIER)
				.setName(Component.translatable(PREFIX + "no_selected_attributes")
					.withStyle(ChatFormatting.GRAY))
				.hideDefaultTooltip());
		} else {
			GuiElementBuilder selector = new GuiElementBuilder(Items.NAME_TAG)
				.setName(attributesOfItem.get(selectedIndex)
					.format(false)
					.withStyle(ChatFormatting.WHITE))
				.hideDefaultTooltip();
			int first = Math.max(0, Math.min(selectedIndex - LORE_OPTIONS / 2, attributesOfItem.size() - LORE_OPTIONS));
			int last = Math.min(attributesOfItem.size(), first + LORE_OPTIONS);
			for (int i = first; i < last; i++)
				selector.addLoreLine(Component.literal(i == selectedIndex ? "> " : "  ")
					.append(attributesOfItem.get(i)
						.format(false))
					.withStyle(i == selectedIndex ? ChatFormatting.GOLD : ChatFormatting.GRAY));
			selector.addLoreLine(GuiText.text("create.gui.attribute_filter.click_to_cycle", "Click for the next, right-click for the previous")
				.withStyle(ChatFormatting.DARK_GRAY));
			selector.setCallback((ClickType type) -> selectAttribute(selectedIndex + (type.isRight ? -1 : 1)));
			setSlot(SELECTOR_BUTTON, selector);
		}

		if (addable) {
			setSlot(ADD_BUTTON, new GuiElementBuilder(GuiText.dye(DyeColor.LIME))
				.setName(Component.translatable(PREFIX + "add_attribute")
					.withStyle(ChatFormatting.GREEN))
				.hideDefaultTooltip()
				.setCallback(() -> addSelectedAttribute(false)));
			setSlot(ADD_INVERTED_BUTTON, new GuiElementBuilder(GuiText.dye(DyeColor.RED))
				.setName(Component.translatable(PREFIX + "add_inverted_attribute")
					.withStyle(ChatFormatting.RED))
				.hideDefaultTooltip()
				.setCallback(() -> addSelectedAttribute(true)));
		} else {
			setSlot(ADD_BUTTON, filler());
			setSlot(ADD_INVERTED_BUTTON, filler());
		}

		modeButton(WHITELIST_DISJ_BUTTON, AttributeFilterWhitelistMode.WHITELIST_DISJ, DyeColor.LIME,
			"allow_list_disjunctive");
		modeButton(WHITELIST_CONJ_BUTTON, AttributeFilterWhitelistMode.WHITELIST_CONJ, DyeColor.CYAN,
			"allow_list_conjunctive");
		modeButton(BLACKLIST_BUTTON, AttributeFilterWhitelistMode.BLACKLIST, DyeColor.RED, "deny_list");

		// the list of selected attributes
		GuiElementBuilder selected = new GuiElementBuilder(Items.NAME_TAG)
			.setName(Component.translatable(PREFIX + (selectedAttributes.isEmpty() ? "no_selected_attributes" : "selected_attributes"))
				.withStyle(ChatFormatting.YELLOW))
			.setCount(Math.max(1, Math.min(selectedAttributes.size(), 64)))
			.hideDefaultTooltip();
		for (ItemAttributeEntry entry : selectedAttributes)
			selected.addLoreLine(Component.literal("- ")
				.append(entry.attribute()
					.format(entry.inverted()))
				.withStyle(ChatFormatting.GRAY));
		setSlot(SELECTED_DISPLAY, selected);

		setSlot(RESET_BUTTON, new GuiElementBuilder(Items.BARRIER)
			.setName(GuiText.text("create.gui.filter_gui.reset", "Reset")
				.withStyle(ChatFormatting.RED))
			.hideDefaultTooltip()
			.setCallback(this::clearSelectedAttributes));
	}

	private void modeButton(int slot, AttributeFilterWhitelistMode mode, DyeColor color, String key) {
		boolean active = whitelistMode == mode;
		setSlot(slot, new GuiElementBuilder(GuiText.dye(color))
			.setName(Component.translatable(PREFIX + key)
				.withStyle(active ? ChatFormatting.GOLD : ChatFormatting.GRAY))
			.addLoreLine(Component.translatable(PREFIX + key + ".description")
				.withStyle(ChatFormatting.GRAY))
			.glow(active)
			.hideDefaultTooltip()
			.setCallback(() -> setWhitelistMode(mode)));
	}
}
