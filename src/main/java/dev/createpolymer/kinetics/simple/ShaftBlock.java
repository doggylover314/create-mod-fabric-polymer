package dev.createpolymer.kinetics.simple;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.ModelItems;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Ported from Create's ShaftBlock (MIT, The Create Team). Encasing and girders are not ported yet. */
public class ShaftBlock extends AbstractShaftBlock implements PolymerTexturedBlock {
	public static final Map<Axis, VoxelShape> SHAPE = ShapeUtil.forAxis(ShapeUtil.box(5, 0, 5, 11, 16, 11));

	// Invisible lightning-rod states: same thin hitbox as a shaft, no visible model.
	private static final Map<Axis, BlockState> CLIENT_DRY = new java.util.EnumMap<>(Axis.class);
	private static final Map<Axis, BlockState> CLIENT_WET = new java.util.EnumMap<>(Axis.class);

	public ShaftBlock(Properties properties) {
		super(properties);
	}

	public static void requestClientStates() {
		for (Axis axis : Axis.values()) {
			CLIENT_DRY.put(axis, PolymerBlockResourceUtils.requestEmpty(BlockModelType.valueOf("LIGHTNING_ROD_" + axis.name())));
			CLIENT_WET.put(axis, PolymerBlockResourceUtils.requestEmpty(BlockModelType.valueOf("LIGHTNING_ROD_" + axis.name() + "_WATERLOGGED")));
		}
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		return SHAPE.get(state.getValue(AXIS));
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		Map<Axis, BlockState> map = state.getValue(BlockStateProperties.WATERLOGGED) ? CLIENT_WET : CLIENT_DRY;
		BlockState client = map.get(state.getValue(AXIS));
		return client != null ? client : super.getPolymerBlockState(state, context);
	}

	@Override
	protected BlockState particleState() {
		return Blocks.ANDESITE.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return model(state, ModelItems.block("shaft"), false);
	}
}
