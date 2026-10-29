package dev.createpolymer.decoration.palettes;

import org.jspecify.annotations.Nullable;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Glass panes and window panes. A pane has no spare vanilla block state Polymer could re-skin, so players see the
 * vanilla pane (or iron bars for the iron windows) with the same connections.
 */
public class GlassPaneBlock extends IronBarsBlock implements PolymerBlock {

	private final Block look;

	public GlassPaneBlock(Properties builder, Block look) {
		super(builder);
		this.look = look;
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		return look.withPropertiesOf(state);
	}

	@Override
	public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
		return look.defaultBlockState();
	}

}
