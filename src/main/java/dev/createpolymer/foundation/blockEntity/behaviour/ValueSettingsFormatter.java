package dev.createpolymer.foundation.blockEntity.behaviour;

import java.util.function.Function;

import dev.createpolymer.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import dev.createpolymer.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;

public class ValueSettingsFormatter {
	private final Function<ValueSettings, MutableComponent> formatter;

	public ValueSettingsFormatter(Function<ValueSettings, MutableComponent> formatter) {
		this.formatter = formatter;
	}

	public MutableComponent format(ValueSettings valueSettings) {
		return formatter.apply(valueSettings);
	}

	public static class ScrollOptionSettingsFormatter extends ValueSettingsFormatter {

		private final INamedIconOptions[] options;

		public ScrollOptionSettingsFormatter(INamedIconOptions[] options) {
			super(v -> Component.translatable(options[v.value()].getTranslationKey()));
			this.options = options;
		}

		/** The item that stands in for the option's icon in the value settings GUI. */
		public Item getIcon(ValueSettings valueSettings) {
			return options[valueSettings.value()].getIconItem();
		}

	}
}
