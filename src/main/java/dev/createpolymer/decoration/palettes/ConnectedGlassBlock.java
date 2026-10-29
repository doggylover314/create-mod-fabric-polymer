package dev.createpolymer.decoration.palettes;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.decoration.DecorationStates;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Framed glass, tiled glass and windows. Players see a Create cube model on a spare leaves state (which, unlike a
 * full block state, lets light through and does not hide the faces of its neighbours); plain glass when Polymer has
 * no state left. Create's connected textures are not ported.
 */
public class ConnectedGlassBlock extends TransparentBlock implements PolymerTexturedBlock {

	private final BlockState clientState;

	public ConnectedGlassBlock(Properties p_i48392_1_, String... models) {
		super(p_i48392_1_);
		PolymerBlockModel[] polymerModels = new PolymerBlockModel[models.length];
		for (int i = 0; i < models.length; i++)
			polymerModels[i] = DecorationStates.model(models[i]);
		this.clientState = DecorationStates.transparentBlock(polymerModels);
	}

	public @Nullable BlockState getClientState() {
		return clientState;
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		return clientState != null ? clientState : Blocks.GLASS.defaultBlockState();
	}

	@Override
	public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
		return Blocks.GLASS.defaultBlockState();
	}
}
