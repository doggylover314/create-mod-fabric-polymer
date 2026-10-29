package dev.createpolymer.trains.display;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class FlapDisplayLayout {

	List<FlapDisplaySection> sections;
	String layoutKey;

	public FlapDisplayLayout(int maxCharCount) {
		loadDefault(maxCharCount);
	}

	public void loadDefault(int maxCharCount) {
		configure("Default", Arrays
			.asList(new FlapDisplaySection(maxCharCount * FlapDisplaySection.MONOSPACE, "alphabet", false, false)));
	}

	public boolean isLayout(String key) {
		return layoutKey.equals(key);
	}

	public void configure(String layoutKey, List<FlapDisplaySection> sections) {
		this.layoutKey = layoutKey;
		this.sections = sections;
	}

	public void write(ValueOutput output) {
		output.putString("Key", layoutKey);
		ValueOutput.ValueOutputList list = output.childrenList("Sections");
		for (FlapDisplaySection section : sections)
			section.write(list.addChild());
	}

	public void read(ValueInput input) {
		String prevKey = layoutKey;
		layoutKey = input.getStringOr("Key", prevKey);
		List<ValueInput> sectionsTag = input.childrenListOrEmpty("Sections")
			.stream()
			.toList();
		if (sectionsTag.isEmpty()) {
			layoutKey = prevKey;
			return;
		}

		if (!prevKey.equals(layoutKey) || sectionsTag.size() != sections.size()) {
			List<FlapDisplaySection> loaded = new ArrayList<>();
			for (ValueInput tag : sectionsTag)
				loaded.add(FlapDisplaySection.load(tag));
			sections = loaded;
			return;
		}

		for (int i = 0; i < sectionsTag.size(); i++)
			sections.get(i)
				.update(sectionsTag.get(i));
	}

	public List<FlapDisplaySection> getSections() {
		return sections;
	}

	/** A key for the geometry of the line, to tell when it has to be laid out again. */
	public String shape() {
		StringBuilder builder = new StringBuilder(layoutKey);
		for (FlapDisplaySection section : sections)
			builder.append('|')
				.append(section.shape());
		return builder.toString();
	}

}
