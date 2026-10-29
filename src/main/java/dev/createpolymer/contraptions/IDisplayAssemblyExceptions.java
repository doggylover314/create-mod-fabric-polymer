package dev.createpolymer.contraptions;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public interface IDisplayAssemblyExceptions {

	/** Goggle tooltip lines for the last assembly failure (server side: shown in chat or an action bar). */
	default boolean addExceptionToTooltip(List<Component> tooltip) {
		AssemblyException e = getLastAssemblyException();
		if (e == null)
			return false;

		if (!tooltip.isEmpty())
			tooltip.add(CommonComponents.EMPTY);

		tooltip.add(Component.translatable("create.gui.assembly.exception").withStyle(ChatFormatting.GOLD));
		for (String line : e.component.getString().split("\n"))
			tooltip.add(Component.literal(line).withStyle(ChatFormatting.GRAY));

		return true;
	}

	AssemblyException getLastAssemblyException();

}
