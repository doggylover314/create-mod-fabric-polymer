package dev.createpolymer.decoration.palettes;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.decoration.DecorationStates;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A column block. Players see Create's vertical model for the Y axis and its horizontal model, turned the way
 * Create's blockstate does it, for X and Z; Create's connected textures are not ported.
 */
public class LayeredBlock extends RotatedPillarBlock implements PolymerTexturedBlock {

	private final Map<Axis, BlockState> clientStates = new EnumMap<>(Axis.class);
	private final Block fallback;

	public LayeredBlock(Properties p_55926_, Block fallback, String verticalModel, String horizontalModel) {
		super(p_55926_);
		this.fallback = fallback;
		put(Axis.Y, DecorationStates.fullBlock(DecorationStates.model(verticalModel)));
		put(Axis.X, DecorationStates.fullBlock(DecorationStates.model(horizontalModel, 90, 90)));
		put(Axis.Z, DecorationStates.fullBlock(DecorationStates.model(horizontalModel, 90, 0)));
	}

	private void put(Axis axis, @Nullable BlockState state) {
		if (state != null)
			clientStates.put(axis, state);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext pContext) {
		BlockState stateForPlacement = super.getStateForPlacement(pContext);
		BlockState placedOn = pContext.getLevel()
			.getBlockState(pContext.getClickedPos()
				.relative(pContext.getClickedFace()
					.getOpposite()));
		if (placedOn.getBlock() == this && (pContext.getPlayer() == null || !pContext.getPlayer()
			.isShiftKeyDown()))
			stateForPlacement = stateForPlacement.setValue(AXIS, placedOn.getValue(AXIS));
		return stateForPlacement;
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		BlockState client = clientStates.get(state.getValue(AXIS));
		return client != null ? client : fallback.defaultBlockState();
	}

	@Override
	public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
		return fallback.defaultBlockState();
	}
}
