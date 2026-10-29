package dev.createpolymer.trains.schedule.destination;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.ImmutableList;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.logistics.box.Glob;
import dev.createpolymer.registry.feature.TrainsRegistry;
import dev.createpolymer.trains.entity.Train;
import dev.createpolymer.trains.graph.DiscoveredPath;
import dev.createpolymer.trains.graph.EdgePointType;
import dev.createpolymer.trains.schedule.ScheduleLang;
import dev.createpolymer.trains.schedule.ScheduleRuntime;
import dev.createpolymer.trains.station.GlobalStation;
import dev.createpolymer.util.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class DestinationInstruction extends TextScheduleInstruction {

	@Override
	public Pair<ItemStack, Component> getSummary() {
		return Pair.of(new ItemStack(TrainsRegistry.TRACK_STATION_ITEM), Component.literal(getLabelText()));
	}

	@Override
	public boolean supportsConditions() {
		return true;
	}

	@Override
	public Identifier getId() {
		return CreatePolymer.id("destination");
	}

	@Override
	public ItemStack getSecondLineIcon() {
		return new ItemStack(TrainsRegistry.TRACK_STATION_ITEM);
	}

	public String getFilter() {
		return getLabelText();
	}

	public String getFilterForRegex() {
		return Glob.toRegexPattern(getFilter());
	}

	@Override
	public List<Component> getSecondLineTooltip(int slot) {
		return ImmutableList.of(ScheduleLang.translate("schedule.instruction.filter_edit_box"),
			ScheduleLang.translate("schedule.instruction.filter_edit_box_1")
				.withStyle(ChatFormatting.GRAY),
			ScheduleLang.translate("schedule.instruction.filter_edit_box_2")
				.withStyle(ChatFormatting.DARK_GRAY),
			ScheduleLang.translate("schedule.instruction.filter_edit_box_3")
				.withStyle(ChatFormatting.DARK_GRAY));
	}

	@Override
	protected Component textTitle() {
		return ScheduleLang.translate("schedule.instruction.filter_edit_box");
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
	@Nullable
	public DiscoveredPath start(ScheduleRuntime runtime, Level level) {
		String regex = getFilterForRegex();
		boolean anyMatch = false;
		ArrayList<GlobalStation> validStations = new ArrayList<>();
		Train train = runtime.train;

		if (!train.hasForwardConductor() && !train.hasBackwardConductor()) {
			train.status.missingConductor();
			runtime.startCooldown();
			return null;
		}

		for (GlobalStation globalStation : train.graph.getPoints(EdgePointType.STATION)) {
			if (!globalStation.name.matches(regex))
				continue;
			anyMatch = true;
			validStations.add(globalStation);
		}

		DiscoveredPath best = train.navigation.findPathTo(validStations, Double.MAX_VALUE);
		if (best == null) {
			if (anyMatch)
				train.status.failedNavigation();
			else
				train.status.failedNavigationNoTarget(getFilter());
			runtime.startCooldown();
			return null;
		}

		return best;
	}

}
