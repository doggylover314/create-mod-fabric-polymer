package dev.createpolymer.decoration.girder;

import java.util.HashMap;
import java.util.Map;

import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The multipart of a girder: named display pieces that are added, re-modelled or removed to match the wanted set.
 * Also holds the connection brackets Create's ConnectedGirderModel adds on every horizontal side that
 * {@link GirderBlock#isConnected} finds something to hold on to.
 */
final class GirderModelParts {
	private final ElementHolder holder;
	private final Map<String, Part> parts = new HashMap<>();
	private final Map<String, String> shown = new HashMap<>();

	GirderModelParts(ElementHolder holder) {
		this.holder = holder;
	}

	/** Shows {@code modelPath} (under models/block) as piece {@code key}; null takes the piece away. */
	void set(String key, @Nullable String modelPath, Quaternionf orientation) {
		String current = shown.get(key);
		if (modelPath == null) {
			if (current == null)
				return;
			shown.remove(key);
			Part part = parts.remove(key);
			if (part != null)
				holder.removeElement(part.element);
			return;
		}
		if (modelPath.equals(current))
			return;
		Part part = parts.get(key);
		if (part == null) {
			part = new Part(ModelItems.block(modelPath));
			parts.put(key, part);
			holder.addElement(part.element);
		} else
			part.setItem(ModelItems.block(modelPath));
		shown.put(key, modelPath);
		part.orientation(orientation)
			.apply(0);
	}

	void updateBrackets(BlockGetter level, BlockPos pos, BlockState state) {
		for (Direction d : dev.createpolymer.util.Iterate.horizontalDirections)
			set("bracket_" + d.getName(),
				GirderBlock.isConnected(level, pos, state, d) ? "metal_girder/bracket_" + d.getName() : null,
				new Quaternionf());
	}
}
