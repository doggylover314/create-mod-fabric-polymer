package dev.createpolymer.redstone.displayLink.source;

import dev.createpolymer.redstone.displayLink.DisplayLinkContext;
import dev.createpolymer.redstone.displayLink.DisplayText;
import dev.createpolymer.redstone.displayLink.ModularGuiLineBuilder;
import dev.createpolymer.redstone.thresholdSwitch.ThresholdSwitchBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

public class FillLevelDisplaySource extends PercentOrProgressBarDisplaySource {

	@Override
	protected Float getProgress(DisplayLinkContext context) {
		BlockEntity be = context.getSourceBlockEntity();
		if (!(be instanceof ThresholdSwitchBlockEntity tsbe))
			return null;
		return Math.max(0,
			(float) (tsbe.currentLevel - tsbe.currentMinLevel) / (tsbe.currentMaxLevel - tsbe.currentMinLevel));
	}

	@Override
	protected boolean progressBarActive(DisplayLinkContext context) {
		return context.sourceConfig()
			.getIntOr("Mode", 0) != 0;
	}

	@Override
	protected String getTranslationKey() {
		return "fill_level";
	}

	@Override
	public void initConfigurationWidgets(DisplayLinkContext context, ModularGuiLineBuilder builder, boolean isFirstLine) {
		super.initConfigurationWidgets(context, builder, isFirstLine);
		if (isFirstLine)
			return;
		builder.addSelectionScrollInput("Mode", Component.translatable("create.display_source.fill_level.display"),
			DisplayText.translatedOptions("display_source.fill_level", "percent", "progress_bar"));
	}

	@Override
	protected boolean allowsLabeling(DisplayLinkContext context) {
		return true;
	}

}
