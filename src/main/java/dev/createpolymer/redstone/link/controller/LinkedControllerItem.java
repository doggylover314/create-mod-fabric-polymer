package dev.createpolymer.redstone.link.controller;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.foundation.item.ItemHelper;
import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import dev.createpolymer.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import dev.createpolymer.registry.feature.ToolsRegistry;
import dev.createpolymer.util.Couple;
import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

/**
 * Create's linked controller. Right-click toggles it: while it is active the movement keys (W, S, A, D, space and
 * sneak, in that order) are its six buttons and transmit the frequencies set for them. Sneak-right-click opens the
 * frequency screen, right-click on a redstone link receiver starts the bind mode and on an empty lectern puts it
 * there (see {@link LinkedControllerHandler}).
 */
public class LinkedControllerItem extends SimplePolymerItem {

	public LinkedControllerItem(Properties properties) {
		super(properties, Items.TRIAL_KEY, true);
	}

	@Override
	public InteractionResult use(Level world, Player player, InteractionHand hand) {
		ItemStack heldItem = player.getItemInHand(hand);

		if (player.isShiftKeyDown() && hand == InteractionHand.MAIN_HAND) {
			if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer && player.mayBuild())
				new LinkedControllerGui(serverPlayer, heldItem).open();
			return InteractionResult.SUCCESS;
		}

		if (!player.isShiftKeyDown()) {
			if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer)
				LinkedControllerHandler.toggle(serverPlayer);
			player.getCooldowns()
				.addCooldown(heldItem, 2);
		}

		return InteractionResult.PASS;
	}

	/** The item shows the lit antenna while its player is using it. */
	@Override
	public @Nullable Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
		ServerPlayer player = PolymerCommonUtils.getPlayer(context);
		if (player != null && LinkedControllerHandler.isActive(player, stack))
			return CreatePolymer.id("linked_controller_powered");
		return super.getPolymerItemModel(stack, context, lookup);
	}

	public static ItemStackHandler getFrequencyItems(ItemStack stack) {
		ItemStackHandler newInv = new ItemStackHandler(12);
		if (!(stack.getItem() instanceof LinkedControllerItem))
			throw new IllegalArgumentException("Cannot get frequency items from non-controller: " + stack);
		if (!stack.has(ToolsRegistry.LINKED_CONTROLLER_ITEMS))
			return newInv;
		ItemHelper.fillItemStackHandler(stack.getOrDefault(ToolsRegistry.LINKED_CONTROLLER_ITEMS, ItemContainerContents.EMPTY), newInv);
		return newInv;
	}

	public static Couple<Frequency> toFrequency(ItemStack controller, int slot) {
		ItemStackHandler frequencyItems = getFrequencyItems(controller);
		return Couple.create(Frequency.of(frequencyItems.getStackInSlot(slot * 2)),
			Frequency.of(frequencyItems.getStackInSlot(slot * 2 + 1)));
	}
}
