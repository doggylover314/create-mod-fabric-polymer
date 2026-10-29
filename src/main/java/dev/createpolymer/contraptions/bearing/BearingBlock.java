package dev.createpolymer.contraptions.bearing;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.contraptions.render.ContraptionDisplay;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ported from Create's BearingBlock (MIT, The Create Team). Players see the bearing as display entities: the block,
 * a shaft half that turns with the kinetic speed and the top plate that turns with the contraption.
 */
public abstract class BearingBlock extends DirectionalKineticBlock implements IWrenchable, ContraptionDisplay {

	public BearingBlock(Properties properties) {
		super(properties);
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face == state.getValue(FACING).getOpposite();
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return state.getValue(FACING).getAxis();
	}

	/**
	 * Create disassembles after the wrench turned the bearing; doing it first is the same, as a bearing that is not
	 * running ignores it, and the structure then comes back in the orientation it was moved in.
	 */
	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		if (!context.getLevel().isClientSide()) {
			BlockEntity be = context.getLevel().getBlockEntity(context.getClickedPos());
			if (be instanceof MechanicalBearingBlockEntity mbe)
				mbe.disassemble();
		}
		return null;
	}

	/** Create's model of the bearing block: {@code mechanical_bearing}, {@code windmill_bearing}, ... */
	protected abstract String bodyModel();

	/** The top plate: {@code bearing/top} or {@code bearing/top_wooden}. */
	protected String topModel() {
		return "bearing/top";
	}

	@Override
	protected BlockState particleState() {
		return Blocks.ANDESITE.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state, bodyModel(), topModel());
	}

	@Override
	public List<Piece> getContraptionPieces(BlockState state) {
		Direction facing = state.getValue(FACING);
		return List.of(new Piece(ModelItems.block(bodyModel()), Orientation.forFacing(facing)),
			new Piece(ModelItems.block("shaft_half"), Orientation.fromSouth(facing.getOpposite())),
			new Piece(ModelItems.block(topModel()), Orientation.forFacing(facing)));
	}

	public static class Model extends BaseModel {
		private final Part body;
		private final Part shaft;
		private final Part top;

		public Model(BlockState state, String bodyModel, String topModel) {
			super(state);
			body = addPart(ModelItems.block(bodyModel));
			shaft = addPart(ModelItems.block("shaft_half"));
			top = addPart(ModelItems.block(topModel));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			Direction facing = state.getValue(FACING);
			body.orientation(Orientation.forFacing(facing)).apply(0);
			shaft.orientation(Orientation.fromSouth(facing.getOpposite())).spinAxis(facing.getAxis()).apply(0);
			top.orientation(Orientation.forFacing(facing)).spinAxis(facing.getAxis()).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof KineticBlockEntity kinetic))
				return;
			Axis axis = state.getValue(FACING).getAxis();
			shaft.spin(time, kinetic.getSpeed(), Orientation.cogOffset(false, axis, pos.getX(), pos.getY(), pos.getZ()));
			if (be instanceof IBearingBlockEntity bearing)
				top.angle(bearing.getInterpolatedAngle(1)).apply(1);
		}
	}
}
