package dev.createpolymer.visual;

import org.jspecify.annotations.Nullable;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Polymer glue for any Create block: players see an invisible barrier (waterlogged if the block is) with the break
 * particles of {@link #particleState()}, and the visible model is the display-entity holder from {@link #createModel}.
 * Add {@code implements PolymerModelBlock} to a ported block and implement {@code createModel}.
 */
public interface PolymerModelBlock extends PolymerBlock, BlockWithElementHolder {

	/** Block whose break particles and sounds players see. */
	default BlockState particleState() {
		return Blocks.STONE.defaultBlockState();
	}

	@Override
	default BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		boolean water = state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED);
		return Blocks.BARRIER.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, water);
	}

	@Override
	default BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
		return particleState();
	}

	@Override
	default boolean tickElementHolder(ServerLevel world, BlockPos pos, BlockState initialBlockState) {
		return true;
	}

	@Override
	default @Nullable ElementHolder createElementHolder(ServerLevel world, BlockPos pos, BlockState initialBlockState) {
		return createModel(world, pos, initialBlockState);
	}

	ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state);
}
