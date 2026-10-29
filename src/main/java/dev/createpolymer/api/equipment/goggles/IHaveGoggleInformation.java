package dev.createpolymer.api.equipment.goggles;

import java.util.List;

import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.IFluidHandler;
import dev.createpolymer.util.Lang;
import dev.createpolymer.util.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Implement this interface on the {@link BlockEntity} that wants to add info to the goggle overlay. There is no
 * overlay for vanilla clients: {@link dev.createpolymer.item.GogglesInfo} shows the lines in the action bar.
 */
public interface IHaveGoggleInformation {
	/**
	 * This method will be called when looking at a {@link BlockEntity} that implements this interface
	 *
	 * @return {@code true} if the tooltip creation was successful and should be
	 * displayed, or {@code false} if the overlay should not be displayed
	 */
	default boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		return false;
	}

	default boolean containedFluidTooltip(List<Component> tooltip, boolean isPlayerSneaking,
										  IFluidHandler handler) {
		if (handler == null)
			return false;

		if (handler.getTanks() == 0)
			return false;

		LangBuilder mb = Lang.builder("create").translate("generic.unit.millibuckets");
		Lang.builder("create").translate("gui.goggles.fluid_container")
			.forGoggles(tooltip);

		boolean isEmpty = true;
		for (int i = 0; i < handler.getTanks(); i++) {
			FluidStack fluidStack = handler.getFluidInTank(i);
			if (fluidStack.isEmpty())
				continue;

			Lang.builder("create").add(fluidStack.getHoverName().copy())
				.style(ChatFormatting.GRAY)
				.forGoggles(tooltip, 1);

			Lang.builder("create")
				.add(Lang.builder("create").text(Lang.formatNumber(fluidStack.getAmount()))
					.add(mb)
					.style(ChatFormatting.GOLD))
				.text(ChatFormatting.GRAY, " / ")
				.add(Lang.builder("create").text(Lang.formatNumber(handler.getTankCapacity(i)))
					.add(mb)
					.style(ChatFormatting.DARK_GRAY))
				.forGoggles(tooltip, 1);

			isEmpty = false;
		}

		if (handler.getTanks() > 1) {
			if (isEmpty)
				tooltip.remove(tooltip.size() - 1);
			return true;
		}

		if (!isEmpty)
			return true;

		Lang.builder("create").translate("gui.goggles.fluid_container.capacity")
			.add(Lang.builder("create").text(Lang.formatNumber(handler.getTankCapacity(0)))
				.add(mb)
				.style(ChatFormatting.GOLD))
			.style(ChatFormatting.GRAY)
			.forGoggles(tooltip, 1);

		return true;
	}

}
