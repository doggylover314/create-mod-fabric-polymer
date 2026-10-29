package dev.createpolymer.logistics.factoryBoard;

import dev.createpolymer.foundation.gui.GhostItemGui;
import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import dev.createpolymer.registry.feature.FactoryGaugeRegistry;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;

/**
 * Create's FactoryPanelSetItemMenu and FactoryPanelSetItemScreen (the screen that lets a player pick the monitored
 * item, which Create shows for JEI) as an sgui chest. Click the ghost slot with an item on the cursor, or shift-click
 * an item of the inventory; the item is set on the gauge straight away.
 */
public class FactoryPanelSetItemGui extends GhostItemGui {

	private final FactoryPanelBehaviour behaviour;

	public FactoryPanelSetItemGui(ServerPlayer player, FactoryPanelBehaviour behaviour) {
		super(MenuType.GENERIC_9x1, player, Component.translatable("create.gui.factory_panel.place_item_to_monitor"),
			new ItemStackHandler(1));
		this.behaviour = behaviour;
		ghostInventory.setStackInSlot(0, behaviour.getFilter()
			.copyWithCount(1));
		refreshGhostSlot(0);

		for (int i = 1; i < 9; i++)
			setSlot(i, new GuiElementBuilder(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY)).setName(Component.empty())
				.hideTooltip());
		setSlot(1, new GuiElementBuilder(FactoryGaugeRegistry.FACTORY_GAUGE_ITEM)
			.setName(Component.translatable("block.create.factory_gauge")
				.withStyle(ChatFormatting.GOLD))
			.hideDefaultTooltip());
		setSlot(8, new GuiElementBuilder(Items.EMERALD)
			.setName(Component.translatableWithFallback("create.gui.factory_panel.save_and_close", "Save and close")
				.withStyle(ChatFormatting.GREEN))
			.hideDefaultTooltip()
			.setCallback(() -> close()));
	}

	@Override
	protected boolean allowRepeats() {
		return true;
	}

	@Override
	protected void onGhostChanged() {
		if (!behaviour.setFilter(ghostInventory.getStackInSlot(0))) {
			player.sendOverlayMessage(Component.translatable("create.logistics.filter.invalid_item"));
			FactoryGaugeRegistry.deny(player.level(), player.blockPosition());
			ghostInventory.setStackInSlot(0, behaviour.getFilter()
				.copyWithCount(1));
			refreshGhostSlot(0);
			return;
		}
		player.level()
			.playSound(null, behaviour.getPos(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, .25f, .1f);
	}

	@Override
	public boolean stillValid() {
		return behaviour.isActive() && !behaviour.blockEntity.isRemoved() && behaviour.blockEntity.canPlayerUse(player);
	}
}
