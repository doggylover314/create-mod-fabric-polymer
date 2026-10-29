package dev.createpolymer.decoration;

import java.util.IdentityHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.item.IWrenchable;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.MultiPolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Andesite, brass and copper bars. Players see them on Polymer's iron bars states, which take Create's bar parts as
 * a multipart model (one state for each of the 32 connection and waterlogging combinations); where Polymer has no
 * state left they see vanilla iron bars.
 */
public class MetalBarsBlock extends IronBarsBlock implements PolymerTexturedBlock, IWrenchable {

	private final Map<BlockState, BlockState> clientStates = new IdentityHashMap<>();

	/** {@code name} is "andesite", "brass" or "copper": the bar part models are called "andesite_post", ... */
	public MetalBarsBlock(Properties properties, String name) {
		super(properties);
		for (BlockState state : getStateDefinition().getPossibleStates()) {
			boolean north = state.getValue(NORTH), east = state.getValue(EAST), south = state.getValue(SOUTH),
				west = state.getValue(WEST);
			BlockModelType type = BlockModelType.getBars(state.getValue(WATERLOGGED), north, south, west, east);
			BlockState client = DecorationStates.request(type, model(name, north, east, south, west));
			if (client != null)
				clientStates.put(state, client);
		}
	}

	/** The same parts Create's multipart blockstate adds for these connections. */
	static MultiPolymerBlockModel model(String name, boolean north, boolean east, boolean south, boolean west) {
		MultiPolymerBlockModel model = MultiPolymerBlockModel.of();
		model.with(DecorationStates.model(name + "_post_ends"));
		if (!north && !east && !south && !west)
			model.with(DecorationStates.model(name + "_post"));
		if (north && !east && !south && !west)
			model.with(DecorationStates.model(name + "_cap"));
		if (!north && east && !south && !west)
			model.with(DecorationStates.model(name + "_cap", 0, 90));
		if (!north && !east && south && !west)
			model.with(DecorationStates.model(name + "_cap_alt"));
		if (!north && !east && !south && west)
			model.with(DecorationStates.model(name + "_cap_alt", 0, 90));
		if (north)
			model.with(DecorationStates.model(name + "_side"));
		if (east)
			model.with(DecorationStates.model(name + "_side", 0, 90));
		if (south)
			model.with(DecorationStates.model(name + "_side_alt"));
		if (west)
			model.with(DecorationStates.model(name + "_side_alt", 0, 90));
		return model;
	}

	public boolean isTextured() {
		return clientStates.size() == getStateDefinition().getPossibleStates().size();
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		BlockState client = clientStates.get(state);
		return client != null ? client : Blocks.IRON_BARS.withPropertiesOf(state);
	}

	@Override
	public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
		return Blocks.IRON_BARS.defaultBlockState();
	}

}
