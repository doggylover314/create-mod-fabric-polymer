package dev.createpolymer.redstone.displayLink.source;

import static dev.createpolymer.trains.display.FlapDisplaySection.MONOSPACE;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import com.google.common.collect.ImmutableList;

import dev.createpolymer.api.behaviour.display.DisplaySource;
import dev.createpolymer.redstone.displayLink.DisplayLinkContext;
import dev.createpolymer.redstone.displayLink.ModularGuiLineBuilder;
import dev.createpolymer.redstone.displayLink.target.DisplayTargetStats;
import dev.createpolymer.trains.display.FlapDisplayBlockEntity;
import dev.createpolymer.trains.display.FlapDisplayLayout;
import dev.createpolymer.trains.display.FlapDisplaySection;
import dev.createpolymer.trains.display.GlobalTrainDisplayData;
import dev.createpolymer.trains.schedule.ScheduleLang;
import dev.createpolymer.trains.station.GlobalStation;
import dev.createpolymer.trains.station.StationBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;

/**
 * The departure board of a train station: the trains that are due at the stations whose name matches the filter, on a
 * display board. The display link has no scroll inputs, so the size of the train name column is chosen from steps of
 * 10% (the data key "NameColumnStep"); the percentage Create saves ("NameColumn") is used when there is no step.
 */
public class StationSummaryDisplaySource extends DisplaySource {

	protected static final MutableComponent UNPREDICTABLE;

	static {
		UNPREDICTABLE = Component.literal(" ~ ");
	}

	protected static final List<MutableComponent> EMPTY_ENTRY_4;

	static {
		EMPTY_ENTRY_4 = ImmutableList.of(WHITESPACE, Component.literal(" . "), WHITESPACE, WHITESPACE);
	}

	protected static final List<MutableComponent> EMPTY_ENTRY_5;

	static {
		EMPTY_ENTRY_5 = ImmutableList.of(WHITESPACE, Component.literal(" . "), WHITESPACE, WHITESPACE, WHITESPACE);
	}

	@Override
	public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
		return EMPTY;
	}

	@Override
	public List<List<MutableComponent>> provideFlapDisplayText(DisplayLinkContext context, DisplayTargetStats stats) {
		String filter = context.sourceConfig()
			.getStringOr("Filter", "");
		boolean hasPlatform = filter.contains("*");

		List<List<MutableComponent>> list = new ArrayList<>();
		GlobalTrainDisplayData.prepare(filter, stats.maxRows())
			.forEach(prediction -> {
				List<MutableComponent> lines = new ArrayList<>();

				if (prediction.ticks == -1 || prediction.ticks >= 12000 - 15 * 20) {
					lines.add(WHITESPACE);
					lines.add(UNPREDICTABLE);

				} else if (prediction.ticks < 200) {
					lines.add(WHITESPACE);
					lines.add(ScheduleLang.translate("display_source.station_summary.now"));

				} else {
					int min = prediction.ticks / 1200;
					int sec = (prediction.ticks / 20) % 60;
					sec = Mth.ceil(sec / 15f) * 15;
					if (sec == 60) {
						min++;
						sec = 0;
					}
					lines.add(min > 0 ? Component.literal(String.valueOf(min)) : WHITESPACE);
					lines.add(min > 0 ? ScheduleLang.translate("display_source.station_summary.minutes")
						: ScheduleLang.translate("display_source.station_summary.seconds", sec));
				}

				lines.add(prediction.train.name.copy());
				lines.add(prediction.scheduleTitle);

				if (!hasPlatform) {
					list.add(lines);
					return;
				}

				String platform = prediction.destination;
				for (String string : filter.split("\\*"))
					if (!string.isEmpty())
						platform = platform.replace(string, "");
				platform = platform.replace("*", "?");

				lines.add(Component.literal(platform.trim()));
				list.add(lines);
			});

		int toPad = stats.maxRows() - list.size();
		for (int padding = 0; padding < toPad; padding++)
			list.add(hasPlatform ? EMPTY_ENTRY_5 : EMPTY_ENTRY_4);

		return list;
	}

	private static int nameColumn(CompoundTag conf) {
		if (conf.contains("NameColumnStep"))
			return conf.getIntOr("NameColumnStep", 5) * 10;
		return conf.getIntOr("NameColumn", 50);
	}

	@Override
	public void loadFlapDisplayLayout(DisplayLinkContext context, FlapDisplayBlockEntity flapDisplay,
		FlapDisplayLayout layout) {
		CompoundTag conf = context.sourceConfig();
		int columnWidth = nameColumn(conf);
		int columnWidth2 = conf.getIntOr("PlatformColumn", 3);
		boolean hasPlatform = conf.getStringOr("Filter", "")
			.contains("*");

		String layoutName = "StationSummary" + columnWidth + hasPlatform + columnWidth2;

		if (layout.isLayout(layoutName))
			return;

		ArrayList<FlapDisplaySection> list = new ArrayList<>();

		int timeWidth = 20;
		float gapSize = 8f;
		float platformWidth = columnWidth2 * MONOSPACE;

		// populate
		FlapDisplaySection minutes = new FlapDisplaySection(MONOSPACE, "numeric", false, false);
		FlapDisplaySection time = new FlapDisplaySection(timeWidth, "arrival_time", true, true);

		float totalSize = flapDisplay.xSize * 32f - 4f - gapSize * 2;
		totalSize = totalSize - timeWidth - MONOSPACE;
		platformWidth = Math.min(platformWidth, totalSize - gapSize);
		platformWidth = (int) (platformWidth / MONOSPACE) * MONOSPACE;

		if (hasPlatform)
			totalSize = totalSize - gapSize - platformWidth;
		if (platformWidth == 0 && hasPlatform)
			totalSize += gapSize;

		int trainNameWidth = (int) ((columnWidth / 100f) * totalSize / MONOSPACE);
		int destinationWidth = (int) Math.round((1 - columnWidth / 100f) * totalSize / MONOSPACE);

		FlapDisplaySection trainName =
			new FlapDisplaySection(trainNameWidth * MONOSPACE, "alphabet", false, trainNameWidth > 0);
		FlapDisplaySection destination = new FlapDisplaySection(destinationWidth * MONOSPACE, "alphabet", false,
			hasPlatform && destinationWidth > 0 && platformWidth > 0);

		FlapDisplaySection platform = new FlapDisplaySection(platformWidth, "numeric", false, false).rightAligned();

		list.add(minutes);
		list.add(time);
		list.add(trainName);
		list.add(destination);

		if (hasPlatform)
			list.add(platform);

		layout.configure(layoutName, list);
	}

	@Override
	protected String getTranslationKey() {
		return "station_summary";
	}

	@Override
	public void populateData(DisplayLinkContext context) {
		CompoundTag conf = context.sourceConfig();

		if (!conf.contains("PlatformColumn"))
			conf.putInt("PlatformColumn", 3);
		if (!conf.contains("NameColumn"))
			conf.putInt("NameColumn", 50);
		if (!conf.contains("NameColumnStep"))
			conf.putInt("NameColumnStep", nameColumn(conf) / 10);

		if (conf.contains("Filter"))
			return;
		if (!(context.getSourceBlockEntity() instanceof StationBlockEntity stationBe))
			return;
		GlobalStation station = stationBe.getStation();
		if (station == null)
			return;
		conf.putString("Filter", station.name);
	}

	@Override
	public void initConfigurationWidgets(DisplayLinkContext context, ModularGuiLineBuilder builder,
		boolean isFirstLine) {
		if (isFirstLine) {
			builder.addTextInput("Filter", ScheduleLang.translate("display_source.station_summary.filter"));
			return;
		}

		builder.addSelectionScrollInput("NameColumnStep",
			ScheduleLang.translate("display_source.station_summary.train_name_column"), IntStream.rangeClosed(0, 7)
				.<Component>mapToObj(i -> Component.literal(i * 10 + "%"))
				.toList());
		builder.addSelectionScrollInput("PlatformColumn",
			ScheduleLang.translate("display_source.station_summary.platform_column"), IntStream.range(0, 16)
				.<Component>mapToObj(i -> Component.literal(String.valueOf(i)))
				.toList());
	}

}
