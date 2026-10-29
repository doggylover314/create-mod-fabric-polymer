package dev.createpolymer.trains.schedule;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;

/**
 * Create's ModularGuiLineBuilder without the widgets: an instruction or condition describes the options it has, and
 * the editor ({@link ScheduleInputGui}) turns each of them into a button. Numbers and choices are stored in the
 * data of the input under the data key; texts are typed in an anvil.
 */
public class ScheduleConfigBuilder {

	public sealed interface Widget permits Scroll, Selection, Text, IntegerText {
		String dataKey();

		Component title();
	}

	/**
	 * Create's ScrollInput: a number from {@code min} up to but not including {@code max}, moved by {@code step},
	 * or by {@code shiftStep} while sneaking.
	 */
	public record Scroll(String dataKey, Component title, int min, int max, int step, int shiftStep, String suffix)
		implements Widget {}

	/** Create's SelectionScrollInput: the index of the chosen option is stored. */
	public record Selection(String dataKey, Component title, List<? extends Component> options) implements Widget {}

	/** Create's text input; {@code hint} lines tell what the text is for. */
	public record Text(String dataKey, Component title, List<Component> hint, int maxWildcards) implements Widget {}

	/** Create's integer text input: a number typed in, stored as text. */
	public record IntegerText(String dataKey, Component title) implements Widget {}

	private final List<Widget> widgets = new ArrayList<>();

	public List<Widget> getWidgets() {
		return widgets;
	}

	public ScheduleConfigBuilder addScrollInput(String dataKey, Component title, int min, int max, int step,
		int shiftStep, String suffix) {
		widgets.add(new Scroll(dataKey, title, min, max, step, shiftStep, suffix));
		return this;
	}

	public ScheduleConfigBuilder addSelectionScrollInput(String dataKey, Component title,
		List<? extends Component> options) {
		widgets.add(new Selection(dataKey, title, options));
		return this;
	}

	public ScheduleConfigBuilder addTextInput(String dataKey, Component title, List<Component> hint,
		int maxWildcards) {
		widgets.add(new Text(dataKey, title, hint, maxWildcards));
		return this;
	}

	public ScheduleConfigBuilder addIntegerTextInput(String dataKey, Component title) {
		widgets.add(new IntegerText(dataKey, title));
		return this;
	}
}
