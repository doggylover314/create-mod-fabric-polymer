package dev.createpolymer.decoration;

import org.jspecify.annotations.Nullable;

import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A plain full cube shown with one of Create's cube models (or several, picked at random like Create's natural
 * stone). Players get a spare vanilla block state that Polymer re-skins through the resource pack; when Polymer has
 * none left they see {@code fallback}.
 */
public class TexturedCubeBlock extends Block implements PolymerTexturedBlock {
	private final BlockState clientState;
	private final Block fallback;

	public TexturedCubeBlock(Properties properties, Block fallback, String... models) {
		super(properties);
		this.fallback = fallback;
		PolymerBlockModel[] polymerModels = new PolymerBlockModel[models.length];
		for (int i = 0; i < models.length; i++)
			polymerModels[i] = DecorationStates.model(models[i]);
		this.clientState = DecorationStates.fullBlock(polymerModels);
	}

	public Block getFallback() {
		return fallback;
	}

	/** The state players see, or null when Polymer had none left. */
	public @Nullable BlockState getClientState() {
		return clientState;
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		return clientState != null ? clientState : fallback.defaultBlockState();
	}

	@Override
	public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
		return fallback.defaultBlockState();
	}
}
