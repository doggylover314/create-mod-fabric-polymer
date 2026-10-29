package dev.createpolymer.processing.casing;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Andesite casing. A full cube, so it borrows one of the spare vanilla block
 * states Polymer re-skins through the resource pack and renders as a real block.
 * (Create's connected textures are not ported; it uses the plain casing texture.)
 */
public class CasingBlock extends Block implements PolymerTexturedBlock {
	private final BlockState clientState;

	public CasingBlock(Properties properties) {
		super(properties);
		this.clientState = PolymerBlockResourceUtils.requestBlock(BlockModelType.FULL_BLOCK,
			PolymerBlockModel.of(CreatePolymer.id("block/andesite_casing")));
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		return clientState != null ? clientState : Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState();
	}

	@Override
	public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
		return Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState();
	}
}
