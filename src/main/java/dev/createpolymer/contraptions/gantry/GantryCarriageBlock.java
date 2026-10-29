package dev.createpolymer.contraptions.gantry;

import java.util.List;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.contraptions.render.ContraptionDisplay;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.kinetics.base.DirectionalAxisKineticBlock;
import dev.createpolymer.kinetics.gantry.GantryShaftBlock;
import dev.createpolymer.registry.feature.PulleyRegistry;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.BlockstateModels;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class GantryCarriageBlock extends DirectionalAxisKineticBlock
	implements IBE<GantryCarriageBlockEntity>, ContraptionDisplay {

	public GantryCarriageBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
		Direction direction = state.getValue(FACING);
		BlockState shaft = world.getBlockState(pos.relative(direction.getOpposite()));
		return shaft.getBlock() instanceof GantryShaftBlock && shaft.getValue(GantryShaftBlock.FACING)
			.getAxis() != direction.getAxis();
	}

	@Override
	protected void updateIndirectNeighbourShapes(BlockState stateIn, LevelAccessor worldIn, BlockPos pos, int flags,
		int count) {
		super.updateIndirectNeighbourShapes(stateIn, worldIn, pos, flags, count);
		withBlockEntityDo(worldIn, pos, GantryCarriageBlockEntity::checkValidGantryShaft);
	}

	@Override
	protected Direction getFacingForPlacement(BlockPlaceContext context) {
		return context.getClickedFace();
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (!player.mayBuild() || player.isShiftKeyDown())
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (stack.isEmpty()) {
			withBlockEntityDo(level, pos, be -> be.checkValidGantryShaft());
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState stateForPlacement = super.getStateForPlacement(context);
		Direction opposite = stateForPlacement.getValue(FACING)
			.getOpposite();
		return cycleAxisIfNecessary(stateForPlacement, opposite, context.getLevel()
			.getBlockState(context.getClickedPos()
				.relative(opposite)));
	}

	@Override
	protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block block,
		net.minecraft.world.level.redstone.@Nullable Orientation orientation, boolean isMoving) {
		// Create only looks at the update coming from the shaft behind the carriage; the orientation does not tell
		// where it came from, but whether the carriage survives only depends on that shaft
		if (!canSurvive(state, world, pos))
			world.destroyBlock(pos, true);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
		Direction direction, BlockPos neighbourPos, BlockState otherState, RandomSource random) {
		if (state.getValue(FACING) != direction.getOpposite())
			return state;
		return cycleAxisIfNecessary(state, direction, otherState);
	}

	protected BlockState cycleAxisIfNecessary(BlockState state, Direction direction, BlockState otherState) {
		if (!(otherState.getBlock() instanceof GantryShaftBlock))
			return state;
		if (otherState.getValue(GantryShaftBlock.FACING)
			.getAxis() == direction.getAxis())
			return state;
		if (isValidGantryShaftAxis(state, otherState))
			return state;
		return state.cycle(AXIS_ALONG_FIRST_COORDINATE);
	}

	public static boolean isValidGantryShaftAxis(BlockState pinionState, BlockState gantryState) {
		return getValidGantryShaftAxis(pinionState) == gantryState.getValue(GantryShaftBlock.FACING)
			.getAxis();
	}

	public static Axis getValidGantryShaftAxis(BlockState state) {
		if (!(state.getBlock() instanceof GantryCarriageBlock block))
			return Axis.Y;
		Axis rotationAxis = block.getRotationAxis(state);
		Axis facingAxis = state.getValue(FACING)
			.getAxis();
		for (Axis axis : Iterate.axes)
			if (axis != rotationAxis && axis != facingAxis)
				return axis;
		return Axis.Y;
	}

	public static Axis getValidGantryPinionAxis(BlockState state, Axis shaftAxis) {
		Axis facingAxis = state.getValue(FACING)
			.getAxis();
		for (Axis axis : Iterate.axes)
			if (axis != shaftAxis && axis != facingAxis)
				return axis;
		return Axis.Y;
	}

	@Override
	public Class<GantryCarriageBlockEntity> getBlockEntityClass() {
		return GantryCarriageBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends GantryCarriageBlockEntity> getBlockEntityType() {
		return PulleyRegistry.GANTRY_PINION_BLOCK_ENTITY;
	}

	// ---- Polymer ----

	@Override
	protected BlockState particleState() {
		return Blocks.OAK_PLANKS.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	@Override
	public List<Piece> getContraptionPieces(BlockState state) {
		List<Piece> pieces = new java.util.ArrayList<>();
		BlockstateModels.Variant variant = BlockstateModels.get("gantry_carriage", state);
		if (variant != null)
			pieces.add(new Piece(variant.stack(), variant.rotation()));
		pieces.add(new Piece(ModelItems.block("shaft"), Orientation.forAxis(getRotationAxis(state))));
		pieces.add(new Piece(ModelItems.block("gantry_carriage/wheels"), new Quaternionf().setFromUnnormalized(wheelBase(state))));
		return pieces;
	}

	/** The orientation Create's GantryCarriageVisual gives the cogs before they turn. */
	private static Matrix4f wheelBase(BlockState state) {
		Direction facing = state.getValue(FACING);
		boolean alongFirst = state.getValue(AXIS_ALONG_FIRST_COORDINATE);
		return new Matrix4f().rotateY(AngleHelper.rad(AngleHelper.horizontalAngle(facing)))
			.rotateX(AngleHelper.rad(facing == Direction.UP ? 0 : facing == Direction.DOWN ? 180 : 90))
			.rotateY(AngleHelper.rad(alongFirst ^ facing.getAxis() == Axis.X ? 0 : 90));
	}

	static float getRotationMultiplier(Axis gantryAxis, Direction facing) {
		float multiplier = 1;
		if (gantryAxis == Axis.X)
			if (facing == Direction.UP)
				multiplier *= -1;
		if (gantryAxis == Axis.Y)
			if (facing == Direction.NORTH || facing == Direction.EAST)
				multiplier *= -1;
		return multiplier;
	}

	/**
	 * Create's GantryCarriageRenderer: the casing, a shaft and the wheels that mesh with the gantry shaft. The wheels turn
	 * at half the carriage's speed, around a point a little beyond the block.
	 */
	public static class Model extends BaseModel {
		private final Part body;
		private final Part shaft;
		private final Part wheels;

		private Matrix4f wheelBase = new Matrix4f();
		private float rotationMultiplier = 1;
		private float lastWheelSpeed = Float.NaN;
		private long nextWheelUpdate = Long.MIN_VALUE;

		public Model(BlockState state) {
			super(state);
			body = addPart(ItemStack.EMPTY);
			shaft = addPart(ModelItems.block("shaft"));
			wheels = addPart(ModelItems.block("gantry_carriage/wheels"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			if (!(state.getBlock() instanceof GantryCarriageBlock block))
				return;
			showVariant(body, "gantry_carriage", state);
			Axis rotationAxis = block.getRotationAxis(state);
			shaft.orientation(Orientation.forAxis(rotationAxis))
				.spinAxis(rotationAxis)
				.apply(0);

			Direction facing = state.getValue(FACING);
			wheelBase = wheelBase(state);
			Axis gantryAxis = Axis.X;
			for (Axis axis : Iterate.axes)
				if (axis != rotationAxis && axis != facing.getAxis())
					gantryAxis = axis;
			rotationMultiplier = getRotationMultiplier(gantryAxis, facing);
			lastWheelSpeed = Float.NaN;
			setWheelAngle(0, 0);
		}

		private void setWheelAngle(float angle, int interpolation) {
			wheels.setMatrix(new Matrix4f(wheelBase).translate(0, -9 / 16f, 0)
				.rotateX(AngleHelper.rad(-angle))
				.translate(0, 9 / 16f, 0), interpolation);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof GantryCarriageBlockEntity carriage) || !(state.getBlock() instanceof GantryCarriageBlock block))
				return;
			Axis rotationAxis = block.getRotationAxis(state);
			Direction facing = state.getValue(FACING);
			float speed = carriage.getSpeed();
			shaft.spin(time, speed,
				Orientation.cogOffset(false, rotationAxis, pos.getX(), pos.getY(), pos.getZ()));

			// the wheels, like Part.spin: steps of at most 30 degrees, glided over by the client
			BlockPos visualPos = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? pos
				: pos.relative(facing.getOpposite());
			float offset = Orientation.cogOffset(false, rotationAxis, visualPos.getX(), visualPos.getY(), visualPos.getZ());
			if (speed == 0) {
				if (lastWheelSpeed != 0) {
					lastWheelSpeed = 0;
					setWheelAngle(offset % 360 * rotationMultiplier, 0);
				}
				return;
			}
			float degPerTick = Math.abs(speed) * 3 / 20f;
			int interval = Mth.clamp((int) (30f / degPerTick), 1, 10);
			if (speed != lastWheelSpeed || time >= nextWheelUpdate) {
				lastWheelSpeed = speed;
				nextWheelUpdate = time + interval;
				setWheelAngle(((time + interval) * speed * 3 / 20f + offset) % 360 * rotationMultiplier, interval);
			}
		}
	}
}
