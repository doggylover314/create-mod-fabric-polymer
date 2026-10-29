package dev.createpolymer.redstone.displayLink.source;

import dev.createpolymer.kinetics.gauge.GaugeBlockEntity;
import dev.createpolymer.redstone.displayLink.DisplayLinkContext;
import dev.createpolymer.redstone.displayLink.DisplayText;
import dev.createpolymer.redstone.displayLink.ModularGuiLineBuilder;
import dev.createpolymer.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class KineticSpeedDisplaySource extends NumericSingleLineDisplaySource {

	@Override
	protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
		if (!(context.getSourceBlockEntity() instanceof GaugeBlockEntity speedGauge) || speedGauge.stressGauge)
			return ZERO.copy();

		boolean absoluteValue = context.sourceConfig()
			.getIntOr("Directional", 0) == 0;
		float speed = absoluteValue ? Math.abs(speedGauge.getSpeed()) : speedGauge.getSpeed();
		return Component.literal(DisplayText.number(speed) + " ")
			.append(Component.translatable("create.generic.unit.rpm"));
	}

	@Override
	protected String getTranslationKey() {
		return "kinetic_speed";
	}

	@Override
	public void initConfigurationWidgets(DisplayLinkContext context, ModularGuiLineBuilder builder,
		boolean isFirstLine) {
		super.initConfigurationWidgets(context, builder, isFirstLine);
		if (isFirstLine)
			return;

		builder.addSelectionScrollInput("Directional", Component.translatable("create.display_source.kinetic_speed"),
			DisplayText.translatedOptions("display_source.kinetic_speed", "absolute", "directional"));
	}

	@Override
	protected boolean allowsLabeling(DisplayLinkContext context) {
		return true;
	}
}
