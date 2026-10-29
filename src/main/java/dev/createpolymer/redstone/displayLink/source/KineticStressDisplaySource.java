package dev.createpolymer.redstone.displayLink.source;

import dev.createpolymer.kinetics.gauge.GaugeBlockEntity;
import dev.createpolymer.redstone.displayLink.DisplayLinkContext;
import dev.createpolymer.redstone.displayLink.DisplayText;
import dev.createpolymer.redstone.displayLink.ModularGuiLineBuilder;
import dev.createpolymer.trains.display.FlapDisplayBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class KineticStressDisplaySource extends PercentOrProgressBarDisplaySource {

	@Override
	protected MutableComponent formatNumeric(DisplayLinkContext context, Float currentLevel) {
		int mode = getMode(context);
		if (mode == 1)
			return super.formatNumeric(context, currentLevel);
		String number = DisplayText.number(currentLevel);
		if (context.getTargetBlockEntity() instanceof FlapDisplayBlockEntity)
			number += " ";
		return Component.literal(number)
			.append(Component.translatable("create.generic.unit.stress"));
	}

	private int getMode(DisplayLinkContext context) {
		return context.sourceConfig()
			.getIntOr("Mode", 0);
	}

	@Override
	protected Float getProgress(DisplayLinkContext context) {
		if (!(context.getSourceBlockEntity() instanceof GaugeBlockEntity stressGauge) || !stressGauge.stressGauge)
			return null;

		float capacity = stressGauge.getNetworkCapacity();
		float stress = stressGauge.getNetworkStress();

		if (capacity == 0)
			return 0f;

		return switch (getMode(context)) {
		case 0, 1 -> stress / capacity;
		case 2 -> stress;
		case 3 -> capacity;
		case 4 -> capacity - stress;
		default -> 0f;
		};
	}

	@Override
	protected boolean allowsLabeling(DisplayLinkContext context) {
		return true;
	}

	@Override
	protected boolean progressBarActive(DisplayLinkContext context) {
		return getMode(context) == 0;
	}

	@Override
	public void initConfigurationWidgets(DisplayLinkContext context, ModularGuiLineBuilder builder,
		boolean isFirstLine) {
		super.initConfigurationWidgets(context, builder, isFirstLine);
		if (isFirstLine)
			return;
		builder.addSelectionScrollInput("Mode", Component.translatable("create.display_source.kinetic_stress.display"),
			DisplayText.translatedOptions("display_source.kinetic_stress", "progress_bar", "percent", "current", "max",
				"remaining"));
	}

	@Override
	protected String getTranslationKey() {
		return "kinetic_stress";
	}

}
