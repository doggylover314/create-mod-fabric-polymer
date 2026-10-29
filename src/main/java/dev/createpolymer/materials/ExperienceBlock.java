package dev.createpolymer.materials;

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
 * Create's block of experience: a glowing full cube. Its animateTick, the client-side particles, is not ported.
 * It drops itself, through its loot table.
 */
public class ExperienceBlock extends Block implements PolymerTexturedBlock {
	private final BlockState clientState;

	public ExperienceBlock(Properties properties) {
		super(properties);
		this.clientState = PolymerBlockResourceUtils.requestBlock(BlockModelType.FULL_BLOCK,
			PolymerBlockModel.of(CreatePolymer.id("block/experience_block")));
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		return clientState != null ? clientState : Blocks.GLOWSTONE.defaultBlockState();
	}

	@Override
	public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
		return Blocks.GLOWSTONE.defaultBlockState();
	}
}
