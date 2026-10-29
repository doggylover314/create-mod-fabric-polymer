package dev.createpolymer.decoration;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.MultiPolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hands out the spare vanilla block states Polymer re-skins through the resource pack (note blocks for cubes, leaves
 * for see-through cubes, ...). Polymer's pools are finite, so every request can come back {@code null}: callers keep a
 * vanilla block to show instead. Requests for the same model share one client state.
 */
public final class DecorationStates {
	private DecorationStates() {}

	private static final Map<String, BlockState> CACHE = new HashMap<>();

	public static PolymerBlockModel model(String name) {
		return model(name, 0, 0);
	}

	public static PolymerBlockModel model(String name, int x, int y) {
		return PolymerBlockModel.of(CreatePolymer.id("block/" + name), x, y);
	}

	/** An opaque cube. Several models are picked at random (by position) like Create's natural stone variants. */
	public static @Nullable BlockState fullBlock(PolymerBlockModel... models) {
		return request(BlockModelType.FULL_BLOCK, models);
	}

	/** A cube whose neighbours still draw their faces and that lets light through, for glass and windows. */
	public static @Nullable BlockState transparentBlock(PolymerBlockModel... models) {
		return request(BlockModelType.LEAVES, models);
	}

	public static @Nullable BlockState request(BlockModelType type, PolymerBlockModel... models) {
		String key = type + ":" + java.util.Arrays.toString(models);
		BlockState cached = CACHE.get(key);
		if (cached != null)
			return cached;
		BlockState state = PolymerBlockResourceUtils.requestBlock(type, models);
		if (state != null)
			CACHE.put(key, state);
		return state;
	}

	public static @Nullable BlockState request(BlockModelType type, MultiPolymerBlockModel model) {
		return PolymerBlockResourceUtils.requestBlock(type, model);
	}

	/** Models left in Polymer's pool for {@code type} (the last one is kept back for Polymer's own use). */
	public static int left(BlockModelType type) {
		return PolymerBlockResourceUtils.getBlocksLeft(type);
	}
}
