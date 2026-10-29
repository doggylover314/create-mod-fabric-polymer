package dev.createpolymer.redstone;

import java.util.EnumMap;
import java.util.Map;

import dev.createpolymer.CreatePolymer;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Invisible vanilla block states with a thin collision and selection box, for the redstone blocks that are only a
 * few pixels thick. Players see (and bump into) the same flat plate that the display entities draw, instead of the
 * barrier's full cube. The client shape is always at least as big as the shape the server uses, so the client never
 * walks into a block the server thinks is empty space, and never stands inside one.
 * <p>
 * The states are Polymer's "empty" states of the trapdoor and slab block model types, which have to be requested
 * before the resource pack is built: call {@link #init()} while the mod initialises.
 */
public final class RedstoneClientStates {
	private static final Map<Direction, BlockState> PLATE = new EnumMap<>(Direction.class);
	private static final Map<Direction, BlockState> PLATE_WET = new EnumMap<>(Direction.class);
	private static BlockState slabBottom = barrier(false);
	private static BlockState slabBottomWet = barrier(true);
	private static BlockState slabTop = barrier(false);
	private static BlockState slabTopWet = barrier(true);
	private static boolean initialized;

	private RedstoneClientStates() {}

	private static BlockState barrier(boolean waterlogged) {
		return Blocks.BARRIER.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, waterlogged);
	}

	public static synchronized void init() {
		if (initialized)
			return;
		initialized = true;
		try {
			slabBottom = PolymerBlockResourceUtils.requestEmpty(BlockModelType.SLAB_BOTTOM);
			slabBottomWet = PolymerBlockResourceUtils.requestEmpty(BlockModelType.SLAB_BOTTOM_WATERLOGGED);
			slabTop = PolymerBlockResourceUtils.requestEmpty(BlockModelType.SLAB_TOP);
			slabTopWet = PolymerBlockResourceUtils.requestEmpty(BlockModelType.SLAB_TOP_WATERLOGGED);

			PLATE.put(Direction.UP, PolymerBlockResourceUtils.requestEmpty(BlockModelType.TRAPDOOR_TOP));
			PLATE.put(Direction.DOWN, PolymerBlockResourceUtils.requestEmpty(BlockModelType.TRAPDOOR_BOTTOM));
			PLATE_WET.put(Direction.UP, PolymerBlockResourceUtils.requestEmpty(BlockModelType.TRAPDOOR_TOP_WATERLOGGED));
			PLATE_WET.put(Direction.DOWN, PolymerBlockResourceUtils.requestEmpty(BlockModelType.TRAPDOOR_BOTTOM_WATERLOGGED));

			// A trapdoor model type is named after the facing of an open trapdoor; find the side that plate hugs
			// from the shape itself instead of trusting the name.
			for (BlockModelType type : new BlockModelType[] { BlockModelType.TRAPDOOR_NORTH, BlockModelType.TRAPDOOR_EAST,
				BlockModelType.TRAPDOOR_SOUTH, BlockModelType.TRAPDOOR_WEST }) {
				BlockState dry = PolymerBlockResourceUtils.requestEmpty(type);
				BlockState wet = PolymerBlockResourceUtils.requestEmpty(BlockModelType.valueOf(type.name() + "_WATERLOGGED"));
				Direction side = sideOf(dry);
				if (side != null) {
					PLATE.put(side, dry);
					PLATE_WET.put(side, wet);
				}
			}
		} catch (RuntimeException e) {
			CreatePolymer.LOGGER.error("Could not reserve thin client block states; redstone blocks fall back to barriers", e);
		}
	}

	/** The block face a flat plate state is pressed against, or null if the shape is not a plate against a face. */
	private static Direction sideOf(BlockState state) {
		VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		if (shape.isEmpty())
			return null;
		for (Axis axis : Axis.values()) {
			if (shape.max(axis) - shape.min(axis) > .5)
				continue;
			boolean positive = shape.min(axis) > .5;
			return Direction.get(positive ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE, axis);
		}
		return null;
	}

	/** A 3 pixel thick invisible plate against the given face of the block (the face the block is attached to). */
	public static BlockState plate(Direction side, boolean waterlogged) {
		BlockState state = (waterlogged ? PLATE_WET : PLATE).get(side);
		return state != null ? state : barrier(waterlogged);
	}

	/** An invisible half block, at the bottom or at the top of the block. */
	public static BlockState slab(boolean top, boolean waterlogged) {
		return top ? waterlogged ? slabTopWet : slabTop : waterlogged ? slabBottomWet : slabBottom;
	}
}
