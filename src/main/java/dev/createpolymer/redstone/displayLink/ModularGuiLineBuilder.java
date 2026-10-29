package dev.createpolymer.redstone.displayLink;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;

/**
 * Create's ModularGuiLineBuilder without the widgets: a display source describes the options it has on one line of
 * its configuration, and {@link DisplayLinkGui} turns them into buttons. A line holds a selection (a number stored
 * under the data key, a click moves to the next option) or a text (stored as a string).
 */
public class ModularGuiLineBuilder {

	public sealed interface Widget permits Selection, TextInput {
		String dataKey();
	}

	/** Create's SelectionScrollInput: {@code options} are shown as a list, the index of the chosen one is stored. */
	public record Selection(String dataKey, Component title, List<Component> options) implements Widget {}

	/** Create's text input: {@code title} says what the text is for. */
	public record TextInput(String dataKey, Component title) implements Widget {}

	private final List<Widget> widgets;

	public ModularGuiLineBuilder(List<Widget> target) {
		this.widgets = target;
	}

	public ModularGuiLineBuilder() {
		this(new ArrayList<>());
	}

	public List<Widget> getWidgets() {
		return widgets;
	}

	public ModularGuiLineBuilder addSelectionScrollInput(String dataKey, Component title, List<Component> options) {
		widgets.add(new Selection(dataKey, title, options));
		return this;
	}

	public ModularGuiLineBuilder addTextInput(String dataKey, Component title) {
		widgets.add(new TextInput(dataKey, title));
		return this;
	}
}
