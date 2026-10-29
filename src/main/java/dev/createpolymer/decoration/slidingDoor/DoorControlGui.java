package dev.createpolymer.decoration.slidingDoor;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/**
 * Create adds a scroll input for the {@link DoorControl} mode to its station and elevator contact screens; here it is
 * one button for chest GUIs that goes through the modes on each click and shows the one the player faces.
 */
public final class DoorControlGui {
	private DoorControlGui() {}

	public static GuiElementBuilder button(ServerPlayer player, DoorControlBehaviour doorControls, Runnable refresh) {
		DoorControl mode = doorControls.mode;
		return new GuiElementBuilder(Items.IRON_DOOR)
			.setName(Component.translatable("create.contraption.door_control")
				.withStyle(ChatFormatting.BLUE))
			.addLoreLine(mode.translate()
				.copy()
				.withStyle(ChatFormatting.GOLD))
			.addLoreLine(Component.translatable("create.contraption.door_control.player_facing",
				DoorControl.facing(player.getDirection())
					.translateShort())
				.withStyle(ChatFormatting.GRAY))
			.hideDefaultTooltip()
			.setCallback(() -> {
				doorControls.set(mode.next());
				refresh.run();
			});
	}
}
