package dev.createpolymer.redstone.displayLink.source;

import java.util.List;

import com.google.common.collect.ImmutableList;

import dev.createpolymer.api.behaviour.display.DisplaySource;
import dev.createpolymer.redstone.displayLink.DisplayLinkContext;
import dev.createpolymer.redstone.displayLink.ModularGuiLineBuilder;
import dev.createpolymer.redstone.displayLink.target.DisplayTargetStats;
import dev.createpolymer.trains.display.FlapDisplayBlockEntity;
import dev.createpolymer.trains.display.FlapDisplayLayout;
import dev.createpolymer.trains.display.FlapDisplaySection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public abstract class SingleLineDisplaySource extends DisplaySource {

	protected abstract MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats);

	protected abstract boolean allowsLabeling(DisplayLinkContext context);

	@Override
	public void initConfigurationWidgets(DisplayLinkContext context, ModularGuiLineBuilder builder, boolean isFirstLine) {
		if (isFirstLine && allowsLabeling(context))
			addLabelingTextBox(builder);
	}

	protected void addLabelingTextBox(ModularGuiLineBuilder builder) {
		builder.addTextInput("Label", Component.translatable("create.display_source.label"));
	}

	@Override
	public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
		MutableComponent line = provideLine(context, stats);
		if (line == EMPTY_LINE)
			return EMPTY;

		if (allowsLabeling(context)) {
			String label = context.sourceConfig()
				.getStringOr("Label", "");
			if (!label.isEmpty()) {
				line = Component.literal(label + " ").append(line);
			}
		}

		return ImmutableList.of(line);
	}

	@Override
	public List<List<MutableComponent>> provideFlapDisplayText(DisplayLinkContext context, DisplayTargetStats stats) {

		if (allowsLabeling(context)) {
			String label = context.sourceConfig()
				.getStringOr("Label", "");
			if (!label.isEmpty()) {
				return ImmutableList.of(ImmutableList.of(Component.literal(label + " "), provideLine(context, stats)));
			}
		}

		return super.provideFlapDisplayText(context, stats);
	}

	@Override
	public void loadFlapDisplayLayout(DisplayLinkContext context, FlapDisplayBlockEntity flapDisplay,
		FlapDisplayLayout layout) {
		String layoutKey = getFlapDisplayLayoutName(context);

		if (!allowsLabeling(context)) {
			if (!layout.isLayout(layoutKey))
				layout.configure(layoutKey,
					ImmutableList.of(createSectionForValue(context, flapDisplay.getMaxCharCount())));
			return;
		}

		String label = context.sourceConfig()
			.getStringOr("Label", "");

		if (label.isEmpty()) {
			if (!layout.isLayout(layoutKey))
				layout.configure(layoutKey,
					ImmutableList.of(createSectionForValue(context, flapDisplay.getMaxCharCount())));
			return;
		}

		String layoutName = label.length() + "_Labeled_" + layoutKey;
		if (layout.isLayout(layoutName))
			return;

		int maxCharCount = flapDisplay.getMaxCharCount();
		FlapDisplaySection labelSection = new FlapDisplaySection(
			Math.min(maxCharCount, label.length() + 1) * FlapDisplaySection.MONOSPACE, "alphabet", false, false);

		if (label.length() + 1 < maxCharCount)
			layout.configure(layoutName,
				ImmutableList.of(labelSection, createSectionForValue(context, maxCharCount - label.length() - 1)));
		else
			layout.configure(layoutName, ImmutableList.of(labelSection));
	}

	protected String getFlapDisplayLayoutName(DisplayLinkContext context) {
		return "Default";
	}

	protected FlapDisplaySection createSectionForValue(DisplayLinkContext context, int size) {
		return new FlapDisplaySection(size * FlapDisplaySection.MONOSPACE, "alphabet", false, false);
	}

}
