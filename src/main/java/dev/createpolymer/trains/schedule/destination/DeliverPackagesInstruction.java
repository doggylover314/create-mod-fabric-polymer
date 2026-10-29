package dev.createpolymer.trains.schedule.destination;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.ImmutableList;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.foundation.item.handler.IItemHandlerModifiable;
import dev.createpolymer.logistics.box.PackageItem;
import dev.createpolymer.registry.feature.PackagesRegistry;
import dev.createpolymer.trains.entity.Carriage;
import dev.createpolymer.trains.entity.Train;
import dev.createpolymer.trains.graph.DiscoveredPath;
import dev.createpolymer.trains.schedule.ScheduleLang;
import dev.createpolymer.trains.schedule.ScheduleRuntime;
import dev.createpolymer.trains.schedule.ScheduleRuntime.State;
import dev.createpolymer.trains.station.GlobalStation;
import dev.createpolymer.util.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class DeliverPackagesInstruction extends ScheduleInstruction {

	@Override
	public Pair<ItemStack, Component> getSummary() {
		return Pair.of(getSecondLineIcon(), ScheduleLang.translate("schedule.instruction.package_delivery"));
	}

	@Override
	public ItemStack getSecondLineIcon() {
		return new ItemStack(PackagesRegistry.PACKAGE_POSTBOX_ITEMS.get(DyeColor.WHITE));
	}

	@Override
	public List<Component> getTitleAs(String type) {
		return ImmutableList.of(ScheduleLang.translate("schedule.instruction.package_delivery.summary")
			.withStyle(ChatFormatting.GOLD),
			ScheduleLang.translate("schedule.instruction.package_delivery.summary_1")
				.withStyle(ChatFormatting.GRAY),
			ScheduleLang.translate("schedule.instruction.package_delivery.summary_2")
				.withStyle(ChatFormatting.GRAY));
	}

	@Override
	public Identifier getId() {
		return CreatePolymer.id("package_delivery");
	}

	@Override
	public boolean supportsConditions() {
		return true;
	}

	@Override
	@Nullable
	public DiscoveredPath start(ScheduleRuntime runtime, Level level) {
		boolean anyMatch = false;
		String firstPackage = null;
		ArrayList<GlobalStation> validStations = new ArrayList<>();
		Train train = runtime.train;

		if (!train.hasForwardConductor() && !train.hasBackwardConductor()) {
			train.status.missingConductor();
			runtime.startCooldown();
			return null;
		}

		for (Carriage carriage : train.carriages) {
			IItemHandlerModifiable carriageInventory = carriage.storage.getAllItems();
			if (carriageInventory == null)
				continue;

			// Export to station
			for (int slot = 0; slot < carriageInventory.getSlots(); slot++) {
				ItemStack stack = carriageInventory.getStackInSlot(slot);
				if (!PackageItem.isPackage(stack))
					continue;
				if (firstPackage == null)
					firstPackage = PackageItem.getAddress(stack);
				// Create looks for the stations whose connected package ports have a matching address here. Stations
				// do not know their package ports in this port, so no station is a destination.
			}
		}

		if (validStations.isEmpty()) {
			if (firstPackage != null) {
				train.status.failedPackageNoTarget(firstPackage);
				runtime.startCooldown();
			} else {
				runtime.state = State.PRE_TRANSIT;
				runtime.currentEntry++;
			}
			return null;
		}

		DiscoveredPath best = train.navigation.findPathTo(validStations, Double.MAX_VALUE);
		if (best == null) {
			if (anyMatch)
				train.status.failedNavigation();
			runtime.startCooldown();
			return null;
		}

		return best;
	}

}
