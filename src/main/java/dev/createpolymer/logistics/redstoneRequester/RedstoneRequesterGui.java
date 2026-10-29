package dev.createpolymer.logistics.redstoneRequester;

import java.util.ArrayList;
import java.util.List;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.foundation.gui.GhostItemGui;
import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import dev.createpolymer.logistics.BigItemStack;
import dev.createpolymer.logistics.stockTicker.PackageOrder;
import dev.createpolymer.logistics.stockTicker.PackageOrderWithCrafts;
import dev.createpolymer.util.Lang;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Create's RedstoneRequesterMenu and RedstoneRequesterScreen as an sgui chest. The first row holds the nine ghost
 * slots of the items to request, the amounts of which are changed with the buttons below them (left click: 1 more,
 * right click: 1 less, shift: 10 at a time). The address and the partial-orders switches sit in the last row. Every
 * change is stored in the requester straight away, there is no confirm packet.
 */
public class RedstoneRequesterGui extends GhostItemGui {
	private static final String PREFIX = "create.gui.redstone_requester.";

	private static final int SLOTS = 9;
	private static final int AMOUNT_ROW = 9;
	private static final int ADDRESS_BUTTON = 19;
	private static final int ALLOW_PARTIAL_BUTTON = 21;
	private static final int FULL_BUTTON = 22;
	private static final int DONE_BUTTON = 26;

	private final RedstoneRequesterBlockEntity requester;
	private final List<Integer> amounts = new ArrayList<>();

	public RedstoneRequesterGui(ServerPlayer player, RedstoneRequesterBlockEntity requester) {
		super(MenuType.GENERIC_9x3, player, requester.getBlockState()
			.getBlock()
			.getName(), createGhostInventory(requester));
		this.requester = requester;

		for (int i = 0; i < SLOTS; i++)
			amounts.add(1);
		List<BigItemStack> stacks = requester.encodedRequest.stacks();
		for (int i = 0; i < stacks.size() && i < SLOTS; i++)
			amounts.set(i, Math.max(1, stacks.get(i).count));
		refreshButtons();
	}

	private static ItemStackHandler createGhostInventory(RedstoneRequesterBlockEntity requester) {
		ItemStackHandler inventory = new ItemStackHandler(SLOTS);
		List<BigItemStack> stacks = requester.encodedRequest.stacks();
		for (int i = 0; i < stacks.size() && i < SLOTS; i++)
			inventory.setStackInSlot(i, stacks.get(i).stack.copyWithCount(1));
		return inventory;
	}

	@Override
	protected boolean allowRepeats() {
		return true;
	}

	@Override
	protected void onGhostChanged() {
		for (int i = 0; i < SLOTS; i++)
			if (ghostInventory.getStackInSlot(i)
				.isEmpty())
				amounts.set(i, 1);
		saveData();
		refreshButtons();
	}

	/** Create's RedstoneRequesterMenu.saveData and RedstoneRequesterConfigurationPacket in one. */
	private void saveData() {
		ArrayList<BigItemStack> list = new ArrayList<>();
		for (int i = 0; i < SLOTS; i++) {
			ItemStack stackInSlot = ghostInventory.getStackInSlot(i);
			if (stackInSlot.isEmpty())
				continue;
			list.add(new BigItemStack(stackInSlot.copyWithCount(1), amounts.get(i)));
		}

		PackageOrderWithCrafts newRequest = new PackageOrderWithCrafts(new PackageOrder(list),
			requester.encodedRequest.orderedCrafts());
		if (!newRequest.orderedStacksMatchOrderedRecipes())
			newRequest = PackageOrderWithCrafts.simple(newRequest.stacks());
		requester.encodedRequest = newRequest;
		requester.notifyUpdate();
	}

	private void refreshButtons() {
		for (int i = 0; i < SLOTS; i++) {
			ItemStack ghost = ghostInventory.getStackInSlot(i);
			if (ghost.isEmpty()) {
				setSlot(AMOUNT_ROW + i, new GuiElementBuilder(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY))
					.setName(Component.empty())
					.hideTooltip());
				continue;
			}
			int slot = i;
			int amount = amounts.get(i);
			setSlot(AMOUNT_ROW + i, new GuiElementBuilder(ghost.getItem())
				.setCount(Mth.clamp(amount, 1, ghost.getMaxStackSize()))
				.setName(Lang.builder(CreatePolymer.MOD_ID)
					.translate("gui.factory_panel.send_item", ghost.getHoverName()
						.copy()
						.append(" x" + amount))
					.color(0xf1c40f)
					.component())
				.addLoreLine(Component.translatableWithFallback("create.gui.redstone_requester.change_amount",
					"Left click: +1, right click: -1. Hold shift for 10.")
					.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))
				.hideDefaultTooltip()
				.setCallback((ClickType type) -> changeAmount(slot, type)));
		}

		for (int i = 18; i < 27; i++)
			setSlot(i, new GuiElementBuilder(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY)).setName(Component.empty())
				.hideTooltip());

		String address = requester.encodedTargetAdress;
		boolean named = address != null && !address.isBlank();
		setSlot(ADDRESS_BUTTON, new GuiElementBuilder(Items.NAME_TAG)
			.setName(named ? Component.literal(address)
				.withStyle(ChatFormatting.WHITE)
				: Component.translatable("create.gui.factory_panel.restocker_address")
					.withStyle(ChatFormatting.GOLD))
			.addLoreLine(Component.translatable("create.gui.factory_panel.restocker_address_tip")
				.withStyle(ChatFormatting.GRAY))
			.addLoreLine(Component.translatable("create.gui.factory_panel.restocker_address_tip_1")
				.withStyle(ChatFormatting.GRAY))
			.addLoreLine(Component.translatableWithFallback("create.gui.package_port.click_to_edit",
				"Click to change the address")
				.withStyle(ChatFormatting.DARK_GRAY))
			.hideDefaultTooltip()
			.setCallback(this::editAddress));

		boolean partial = requester.allowPartialRequests;
		setSlot(ALLOW_PARTIAL_BUTTON, new GuiElementBuilder(Items.DYE.pick(partial ? DyeColor.LIME : DyeColor.GRAY))
			.setName(Component.translatable(PREFIX + "allow_partial")
				.withStyle(partial ? ChatFormatting.GREEN : ChatFormatting.GRAY))
			.hideDefaultTooltip()
			.setCallback(() -> setPartial(true)));
		setSlot(FULL_BUTTON, new GuiElementBuilder(Items.DYE.pick(partial ? DyeColor.GRAY : DyeColor.LIME))
			.setName(Component.translatable(PREFIX + "dont_allow_partial")
				.withStyle(partial ? ChatFormatting.GRAY : ChatFormatting.GREEN))
			.hideDefaultTooltip()
			.setCallback(() -> setPartial(false)));

		setSlot(DONE_BUTTON, new GuiElementBuilder(Items.DYE.pick(DyeColor.LIME))
			.setName(Component.translatableWithFallback("create.gui.confirm", "Confirm")
				.withStyle(ChatFormatting.GREEN))
			.hideDefaultTooltip()
			.setCallback(() -> close()));
	}

	private void changeAmount(int slot, ClickType type) {
		int step = type.shift ? 10 : 1;
		if (!type.isLeft && !type.isRight)
			return;
		int newAmount = Mth.clamp(amounts.get(slot) + (type.isLeft ? step : -step), 1, 256);
		amounts.set(slot, newAmount);
		saveData();
		refreshButtons();
	}

	private void setPartial(boolean partial) {
		requester.allowPartialRequests = partial;
		requester.notifyUpdate();
		refreshButtons();
	}

	/** Create edits the address in a text box on the screen; here an anvil takes its place. */
	private void editAddress() {
		RedstoneRequesterBlockEntity requester = this.requester;
		ServerPlayer player = this.player;
		AnvilInputGui anvil = new AnvilInputGui(player, false);
		anvil.setTitle(Component.translatable("create.gui.factory_panel.restocker_address"));
		anvil.setDefaultInputValue(requester.encodedTargetAdress);
		anvil.setSlot(2, new GuiElementBuilder(Items.NAME_TAG)
			.setName(Component.translatableWithFallback("create.gui.package_port.confirm", "Confirm")
				.withStyle(ChatFormatting.GREEN))
			.setCallback(() -> {
				String input = anvil.getInput();
				if (input.length() > 25)
					input = input.substring(0, 25);
				requester.encodedTargetAdress = input;
				requester.notifyUpdate();
				new RedstoneRequesterGui(player, requester).open();
			}));
		anvil.open();
	}

	@Override
	public boolean stillValid() {
		return !requester.isRemoved() && requester.canPlayerUse(player);
	}
}
