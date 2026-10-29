package dev.createpolymer.kinetics.gearbox;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.base.RotatedPillarKineticBlock;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ported from Create's GearboxBlock (MIT, The Create Team).
 * Placement follows the clicked face here, so one item covers both the
 * vertical and horizontal gearbox.
 */
public class GearboxBlock extends RotatedPillarKineticBlock {

	public GearboxBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
		return ModBlockEntities.GEARBOX;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis());
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face.getAxis() != state.getValue(AXIS);
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return state.getValue(AXIS);
	}

	@Override
	protected BlockState particleState() {
		return Blocks.SPRUCE_PLANKS.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	public static class Model extends BaseModel {
		private final Part casing;
		private final Map<Direction, Part> halves = new EnumMap<>(Direction.class);

		public Model(BlockState state) {
			super(state);
			casing = addPart(ModelItems.block("gearbox/block"));
			for (Direction d : Direction.values())
				halves.put(d, addPart(ModelItems.block("shaft_half")));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			Axis boxAxis = state.getValue(AXIS);
			casing.orientation(Orientation.forAxis(boxAxis)).apply(0);
			for (var e : halves.entrySet()) {
				Direction d = e.getKey();
				Part p = e.getValue();
				p.orientation(Orientation.fromSouth(d)).spinAxis(d.getAxis())
					.scale(d.getAxis() == boxAxis ? 0 : 1).apply(0);
			}
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof GearboxBlockEntity gearbox))
				return;
			Axis boxAxis = state.getValue(AXIS);
			Direction sourceFacing = gearbox.hasSource() ? gearbox.getSourceFacing() : null;
			for (var e : halves.entrySet()) {
				Direction d = e.getKey();
				if (d.getAxis() == boxAxis)
					continue;
				float speed = gearbox.getSpeed();
				if (speed != 0 && sourceFacing != null) {
					if (sourceFacing.getAxis() == d.getAxis())
						speed *= sourceFacing == d ? 1 : -1;
					else if (sourceFacing.getAxisDirection() == d.getAxisDirection())
						speed *= -1;
				}
				e.getValue().spin(time, speed, Orientation.cogOffset(false, d.getAxis(), pos.getX(), pos.getY(), pos.getZ()));
			}
		}
	}
}
