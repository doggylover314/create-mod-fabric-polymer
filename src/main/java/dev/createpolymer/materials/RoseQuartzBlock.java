package dev.createpolymer.materials;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.decoration.DecorationStates;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Create's block of rose quartz: a pillar, drawn with Create's axis blockstate (the side model turned for the
 * horizontal axes).
 */
public class RoseQuartzBlock extends RotatedPillarBlock implements PolymerTexturedBlock {

	private final Map<Axis, BlockState> clientStates = new EnumMap<>(Axis.class);

	public RoseQuartzBlock(Properties properties) {
		super(properties);
		// Create's rose_quartz_block.json: axis=y as is, axis=x turned x 90 then y 90, axis=z turned x 90
		put(Axis.Y, DecorationStates.fullBlock(DecorationStates.model("rose_quartz_block")));
		put(Axis.X, DecorationStates.fullBlock(DecorationStates.model("rose_quartz_block_horizontal", 90, 90)));
		put(Axis.Z, DecorationStates.fullBlock(DecorationStates.model("rose_quartz_block_horizontal", 90, 0)));
	}

	private void put(Axis axis, @Nullable BlockState state) {
		if (state != null)
			clientStates.put(axis, state);
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		BlockState client = clientStates.get(state.getValue(BlockStateProperties.AXIS));
		return client != null ? client : Blocks.AMETHYST_BLOCK.defaultBlockState();
	}

	@Override
	public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
		return Blocks.AMETHYST_BLOCK.defaultBlockState();
	}
}
