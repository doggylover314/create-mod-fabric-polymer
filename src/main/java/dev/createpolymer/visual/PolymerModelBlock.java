package dev.createpolymer.visual;

import org.jspecify.annotations.Nullable;

import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
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
 * It is a {@link PolymerTexturedBlock} so that Polymer sends the invisible states that blocks like plates and slabs
 * request (see PolymerBlockResourceUtils#requestEmpty) as they are: without the marker Polymer swaps them for the
 * visible vanilla block they stand in for.
 * Add {@code implements PolymerModelBlock} to a ported block and implement {@code createModel}.
 */
public interface PolymerModelBlock extends PolymerTexturedBlock, BlockWithElementHolder {

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
