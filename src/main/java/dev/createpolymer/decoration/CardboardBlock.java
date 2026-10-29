package dev.createpolymer.decoration;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

/** Block of cardboard. The same Create model on both horizontal axes, turned a quarter the way Create's blockstate does it. */
public class CardboardBlock extends Block implements PolymerTexturedBlock {

	public static final Property<Axis> HORIZONTAL_AXIS = BlockStateProperties.HORIZONTAL_AXIS;

	private final Map<Axis, BlockState> clientStates = new EnumMap<>(Axis.class);

	public CardboardBlock(Properties pProperties, String model) {
		super(pProperties);
		// axis=x is the model turned by 90 degrees, axis=z the model as it is
		BlockState x = DecorationStates.fullBlock(DecorationStates.model(model, 0, 90));
		BlockState z = DecorationStates.fullBlock(DecorationStates.model(model));
		if (x != null)
			clientStates.put(Axis.X, x);
		if (z != null)
			clientStates.put(Axis.Z, z);
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		BlockState client = clientStates.get(state.getValue(HORIZONTAL_AXIS));
		return client != null ? client : Blocks.MUSHROOM_STEM.defaultBlockState();
	}

	@Override
	public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
		return Blocks.MUSHROOM_STEM.defaultBlockState();
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext pContext) {
		return this.defaultBlockState()
			.setValue(HORIZONTAL_AXIS, pContext.getHorizontalDirection()
				.getAxis());
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(HORIZONTAL_AXIS));
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rot) {
		return state.setValue(HORIZONTAL_AXIS,
			rot.rotate(Direction.get(AxisDirection.POSITIVE, state.getValue(HORIZONTAL_AXIS)))
				.getAxis());
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirrorIn) {
		return state;
	}

}
