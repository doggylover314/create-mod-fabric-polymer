package dev.createpolymer.foundation.gui;

import dev.createpolymer.logistics.box.PackageItem;
import dev.createpolymer.registry.feature.PackagesRegistry;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Create's PackageFilterMenu / PackageFilterScreen as an sgui chest: the address the filter lets packages through
 * for, edited in an anvil text box (Create has a text box on the screen) and written into the filter on every
 * change. The reset button clears the address.
 */
public class PackageFilterGui extends SimpleGui {
	private static final String PREFIX = "create.gui.package_filter.";

	private static final int ADDRESS_BUTTON = 13;
	private static final int RESET_BUTTON = 26;

	private final ItemStack filterItem;
	public String address;

	public PackageFilterGui(ServerPlayer player, ItemStack filterItem) {
		super(MenuType.GENERIC_9x3, player, false);
		this.filterItem = filterItem;
		setTitle(filterItem.getItemName());
		address = PackageItem.getAddress(filterItem);
		for (int i = 0; i < 27; i++)
			setSlot(i, new GuiElementBuilder(GuiText.glassPane(DyeColor.GRAY)).setName(Component.empty())
				.hideTooltip());
		refreshButtons();
	}

	public void setAddress(String address) {
		this.address = address;
		filterItem.set(PackagesRegistry.PACKAGE_ADDRESS, address);
		refreshButtons();
	}

	private void refreshButtons() {
		boolean named = !address.isBlank();
		setSlot(ADDRESS_BUTTON, new GuiElementBuilder(Items.NAME_TAG)
			.setName(named ? Component.literal(address)
				.withStyle(ChatFormatting.WHITE)
				: Component.translatable("create.gui.package_port.catch_packages_empty")
					.withStyle(ChatFormatting.GRAY))
			.addLoreLine(Component.translatable("create.gui.package_port.catch_packages")
				.withStyle(ChatFormatting.GOLD))
			.addLoreLine(Component.translatable("create.gui.package_port.catch_packages_wildcard")
				.withStyle(ChatFormatting.GRAY))
			.addLoreLine(GuiText.text(PREFIX + "click_to_edit", "Click to change the address")
				.withStyle(ChatFormatting.DARK_GRAY))
			.hideDefaultTooltip()
			.setCallback(this::editAddress));
		setSlot(RESET_BUTTON, new GuiElementBuilder(Items.BARRIER)
			.setName(GuiText.text("create.gui.filter_gui.reset", "Reset")
				.withStyle(ChatFormatting.RED))
			.hideDefaultTooltip()
			.setCallback(() -> setAddress("")));
	}

	private void editAddress() {
		ServerPlayer player = this.player;
		AnvilInputGui anvil = new AnvilInputGui(player, false);
		anvil.setTitle(Component.translatable("create.gui.package_port.catch_packages"));
		anvil.setDefaultInputValue(address);
		anvil.setSlot(2, new GuiElementBuilder(Items.NAME_TAG)
			.setName(GuiText.text("create.gui.package_port.confirm", "Confirm")
				.withStyle(ChatFormatting.GREEN))
			.setCallback(() -> {
				String input = anvil.getInput();
				if (input.length() > 25)
					input = input.substring(0, 25);
				setAddress(input);
				new PackageFilterGui(player, filterItem).open();
			}));
		anvil.open();
	}

	@Override
	public boolean stillValid() {
		return player.getInventory()
			.getSelectedItem() == filterItem;
	}
}
