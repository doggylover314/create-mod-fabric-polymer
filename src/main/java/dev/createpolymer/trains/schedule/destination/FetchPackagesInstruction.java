package dev.createpolymer.trains.schedule.destination;

import java.util.ArrayList;
import java.util.List;


import com.google.common.collect.ImmutableList;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.logistics.box.Glob;
import dev.createpolymer.logistics.box.PackageStyles;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class FetchPackagesInstruction extends TextScheduleInstruction {

	@Override
	public Pair<ItemStack, Component> getSummary() {
		return Pair.of(getSecondLineIcon(), ScheduleLang.translate("schedule.instruction.package_retrieval"));
	}

	@Override
	public List<Component> getTitleAs(String type) {
		return ImmutableList.of(ScheduleLang.translate("schedule.instruction.package_retrieval.summary")
			.withStyle(ChatFormatting.GOLD), ScheduleLang.translate("generic.in_quotes", Component.literal(getLabelText())),
			ScheduleLang.translate("schedule.instruction.package_retrieval.summary_1")
				.withStyle(ChatFormatting.GRAY),
			ScheduleLang.translate("schedule.instruction.package_retrieval.summary_2")
				.withStyle(ChatFormatting.GRAY));
	}

	@Override
	public ItemStack getSecondLineIcon() {
		return PackageStyles.getDefaultBox();
	}

	public String getFilter() {
		return getLabelText();
	}

	public String getFilterForRegex() {
		if (getFilter().isBlank())
			return Glob.toRegexPattern("*");
		return Glob.toRegexPattern(getFilter());
	}

	@Override
	public List<Component> getSecondLineTooltip(int slot) {
		return ImmutableList.of(ScheduleLang.translate("schedule.instruction.address_filter_edit_box"),
			ScheduleLang.translate("schedule.instruction.address_filter_edit_box_1")
				.withStyle(ChatFormatting.GRAY),
			ScheduleLang.translate("schedule.instruction.address_filter_edit_box_2")
				.withStyle(ChatFormatting.DARK_GRAY),
			ScheduleLang.translate("schedule.instruction.address_filter_edit_box_3")
				.withStyle(ChatFormatting.DARK_GRAY));
	}

	@Override
	protected Component textTitle() {
		return ScheduleLang.translate("schedule.instruction.address_filter_edit_box");
	}

	@Override
	protected List<Component> textHint() {
		return getSecondLineTooltip(0).subList(1, 4);
	}

	@Override
	protected int maxWildcards() {
		return 3;
	}

	@Override
	public Identifier getId() {
		return CreatePolymer.id("package_retrieval");
	}

	@Override
	public boolean supportsConditions() {
		return true;
	}

	@Override
	public DiscoveredPath start(ScheduleRuntime runtime, Level level) {
		MinecraftServer server = level.getServer();
		if (server == null)
			return null;

		boolean anyMatch = false;
		ArrayList<GlobalStation> validStations = new ArrayList<>();
		Train train = runtime.train;

		if (!train.hasForwardConductor() && !train.hasBackwardConductor()) {
			train.status.missingConductor();
			runtime.startCooldown();
			return null;
		}

		// Create looks through the postboxes connected to every station for a package with an address that matches
		// the filter. Stations do not know their package ports in this port, so no station is a destination.

		if (validStations.isEmpty()) {
			runtime.startCooldown();
			runtime.state = State.PRE_TRANSIT;
			runtime.currentEntry++;
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
