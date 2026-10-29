package dev.createpolymer.logistics.packagePort;

import dev.createpolymer.foundation.item.handler.IItemHandlerModifiable;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Create's PackagePortMenu and PackagePortScreen as an sgui chest. The first two rows are the port's 18 package
 * slots, the third row holds the controls: the address the port catches packages for (click to change it), the
 * send-and-receive and send-only switches (shown when the port has a target) and the icon of the target.
 */
public class PackagePortGui extends SimpleGui {
	private static final String PREFIX = "create.gui.package_port.";

	private static final int ADDRESS_BUTTON = 19;
	private static final int SEND_AND_RECEIVE_BUTTON = 21;
	private static final int SEND_ONLY_BUTTON = 22;
	private static final int TARGET_ICON = 25;

	private final PackagePortBlockEntity port;

	public PackagePortGui(ServerPlayer player, PackagePortBlockEntity port) {
		super(MenuType.GENERIC_9x3, player, false);
		this.port = port;
		setTitle(port.getBlockState()
			.getBlock()
			.getName());

		Container container = new HandlerContainer(port.inventory, port);
		for (int i = 0; i < port.inventory.getSlots(); i++)
			setSlot(i, new Slot(container, i, 0, 0) {
				// vanilla slots do not ask the container, so the package check has to be here
				@Override
				public boolean mayPlace(ItemStack stack) {
					return container.canPlaceItem(getContainerSlot(), stack);
				}
			});

		for (int i = 18; i < 27; i++)
			setSlot(i, new GuiElementBuilder(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY)).setName(Component.empty())
				.hideTooltip());
		refreshButtons();
	}

	private void refreshButtons() {
		String address = port.addressFilter;
		boolean named = address != null && !address.isBlank();
		GuiElementBuilder addressButton = new GuiElementBuilder(Items.NAME_TAG)
			.setName(named ? Component.literal(address)
				.withStyle(ChatFormatting.WHITE)
				: port.getBlockState()
					.getBlock()
					.getName()
					.copy()
					.withStyle(ChatFormatting.GRAY))
			.addLoreLine(Component.translatable(PREFIX + "catch_packages")
				.withStyle(ChatFormatting.GOLD))
			.addLoreLine(Component.translatable(PREFIX + "catch_packages_empty")
				.withStyle(ChatFormatting.GRAY))
			.addLoreLine(Component.translatable(PREFIX + "catch_packages_wildcard")
				.withStyle(ChatFormatting.GRAY))
			.addLoreLine(Component.translatableWithFallback(PREFIX + "click_to_edit", "Click to change the address")
				.withStyle(ChatFormatting.DARK_GRAY))
			.hideDefaultTooltip()
			.setCallback(this::editAddress);
		setSlot(ADDRESS_BUTTON, addressButton);

		if (port.target == null) {
			setSlot(SEND_AND_RECEIVE_BUTTON, new GuiElementBuilder(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY))
				.setName(Component.empty())
				.hideTooltip());
			setSlot(SEND_ONLY_BUTTON, new GuiElementBuilder(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY))
				.setName(Component.empty())
				.hideTooltip());
			setSlot(TARGET_ICON, new GuiElementBuilder(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY))
				.setName(Component.empty())
				.hideTooltip());
			return;
		}

		setSlot(SEND_AND_RECEIVE_BUTTON, new GuiElementBuilder(Items.DYE.pick(port.acceptsPackages ? DyeColor.LIME : DyeColor.GRAY))
			.setName(Component.translatable(PREFIX + "send_and_receive")
				.withStyle(port.acceptsPackages ? ChatFormatting.GREEN : ChatFormatting.GRAY))
			.hideDefaultTooltip()
			.setCallback(() -> setAccepting(true)));
		setSlot(SEND_ONLY_BUTTON, new GuiElementBuilder(Items.DYE.pick(port.acceptsPackages ? DyeColor.GRAY : DyeColor.LIME))
			.setName(Component.translatable(PREFIX + "send_only")
				.withStyle(port.acceptsPackages ? ChatFormatting.GRAY : ChatFormatting.GREEN))
			.hideDefaultTooltip()
			.setCallback(() -> setAccepting(false)));
		setSlot(TARGET_ICON, new GuiElementBuilder(port.target.getIcon())
			.hideDefaultTooltip()
			.setName(port.target.getIcon()
				.getHoverName()));
	}

	private void setAccepting(boolean accepting) {
		port.applySettings(port.addressFilter, accepting);
		refreshButtons();
	}

	/** Create edits the address in a text box on the screen; here an anvil takes its place. */
	private void editAddress() {
		PackagePortBlockEntity port = this.port;
		ServerPlayer player = this.player;
		AnvilInputGui anvil = new AnvilInputGui(player, false);
		anvil.setTitle(Component.translatable(PREFIX + "catch_packages"));
		anvil.setDefaultInputValue(port.addressFilter);
		anvil.setSlot(2, new GuiElementBuilder(Items.NAME_TAG)
			.setName(Component.translatableWithFallback(PREFIX + "confirm", "Confirm")
				.withStyle(ChatFormatting.GREEN))
			.setCallback(() -> {
				String input = anvil.getInput();
				if (input.length() > 25)
					input = input.substring(0, 25);
				port.applySettings(input, port.acceptsPackages);
				new PackagePortGui(player, port).open();
			}));
		anvil.open();
	}

	@Override
	public void onOpen() {
		port.getOpenTracker()
			.startOpen(player);
	}

	@Override
	public void onRemoved() {
		port.getOpenTracker()
			.stopOpen(player);
	}

	@Override
	public boolean stillValid() {
		return !port.isRemoved() && port.canPlayerUse(player);
	}

	/** The port's package slots as a vanilla container, so the GUI can use ordinary slots with shift-click and all. */
	private static class HandlerContainer implements Container {
		private final IItemHandlerModifiable handler;
		private final PackagePortBlockEntity port;

		HandlerContainer(IItemHandlerModifiable handler, PackagePortBlockEntity port) {
			this.handler = handler;
			this.port = port;
		}

		@Override
		public int getContainerSize() {
			return handler.getSlots();
		}

		@Override
		public boolean isEmpty() {
			for (int i = 0; i < handler.getSlots(); i++)
				if (!handler.getStackInSlot(i)
					.isEmpty())
					return false;
			return true;
		}

		@Override
		public ItemStack getItem(int slot) {
			return handler.getStackInSlot(slot);
		}

		@Override
		public ItemStack removeItem(int slot, int count) {
			return handler.extractItem(slot, count, false);
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			ItemStack stack = handler.getStackInSlot(slot);
			handler.setStackInSlot(slot, ItemStack.EMPTY);
			return stack;
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			handler.setStackInSlot(slot, stack);
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}

		@Override
		public void setChanged() {
			port.setChanged();
		}

		@Override
		public boolean stillValid(Player player) {
			return port.canPlayerUse(player);
		}

		@Override
		public boolean canPlaceItem(int slot, ItemStack stack) {
			return handler.isItemValid(slot, stack);
		}

		@Override
		public void clearContent() {
			for (int i = 0; i < handler.getSlots(); i++)
				handler.setStackInSlot(i, ItemStack.EMPTY);
		}
	}
}
