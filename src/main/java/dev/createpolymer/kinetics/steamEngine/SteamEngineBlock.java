package dev.createpolymer.kinetics.steamEngine;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.fluids.tank.FluidTankBlock;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.kinetics.base.IRotate;
import dev.createpolymer.kinetics.simple.ShaftBlock;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.feature.SteamRegistry;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.Couple;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import dev.createpolymer.util.VoxelShaper;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import static dev.createpolymer.util.ShapeUtil.box;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED;


public class SteamEngineBlock extends FaceAttachedHorizontalDirectionalBlock
	implements SimpleWaterloggedBlock, IWrenchable, IBE<SteamEngineBlockEntity>, PolymerModelBlock {

	// Create's AllShapes.STEAM_ENGINE, STEAM_ENGINE_CEILING and STEAM_ENGINE_WALL
	private static final VoxelShaper STEAM_ENGINE = VoxelShaper.forHorizontalAxis(
		Shapes.or(box(1, 0, 1, 15, 3, 15), box(3, 0, 3, 13, 15, 13), box(1, 5, 4, 15, 13, 12)), Axis.Z);
	private static final VoxelShaper STEAM_ENGINE_CEILING = VoxelShaper.forHorizontalAxis(
		Shapes.or(box(1, 13, 1, 15, 16, 15), box(3, 1, 3, 13, 16, 13), box(1, 3, 4, 15, 11, 12)), Axis.Z);
	private static final VoxelShaper STEAM_ENGINE_WALL = VoxelShaper.forHorizontal(
		Shapes.or(box(1, 1, 0, 15, 15, 3), box(3, 3, 0, 13, 13, 15), box(1, 4, 5, 15, 12, 13)), Direction.SOUTH);

	public SteamEngineBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACE, AttachFace.FLOOR).setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
		super.createBlockStateDefinition(pBuilder.add(FACE, FACING, WATERLOGGED));
	}

	@Override
	protected boolean canSurvive(BlockState pState, LevelReader pLevel, BlockPos pPos) {
		return canAttach(pLevel, pPos, getConnectedDirection(pState).getOpposite());
	}

	public static boolean canAttach(LevelReader pReader, BlockPos pPos, Direction pDirection) {
		BlockPos blockpos = pPos.relative(pDirection);
		return pReader.getBlockState(blockpos)
			.getBlock() instanceof FluidTankBlock;
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState();
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		// Create's PlacementHelper: a shaft in hand is placed (as a powered shaft) where the engine reaches
		if (!stack.is(ModBlocks.SHAFT.asItem()) || player.isShiftKeyDown() || !player.mayBuild())
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		BlockPos shaftPos = SteamEngineBlock.getShaftPos(state, pos);
		BlockState shaft = ModBlocks.SHAFT.defaultBlockState();
		for (Direction direction : Direction.orderedByNearest(player)) {
			shaft = shaft.setValue(ShaftBlock.AXIS, direction.getAxis());
			if (isShaftValid(state, shaft))
				break;
		}

		BlockState newState = level.getBlockState(shaftPos);
		if (!newState.canBeReplaced())
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (level.isClientSide())
			return InteractionResult.SUCCESS;

		boolean water = level.getFluidState(shaftPos).getType() == Fluids.WATER;
		level.setBlock(shaftPos, PoweredShaftBlock.getEquivalent(shaft.setValue(WATERLOGGED, water)), Block.UPDATE_ALL);
		var sound = shaft.getSoundType();
		level.playSound(null, shaftPos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2, sound.getPitch() * .8f);
		if (!player.isCreative())
			stack.shrink(1);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
									 Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (state.getValue(WATERLOGGED))
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
	}

	@Override
	protected void onPlace(BlockState pState, Level pLevel, BlockPos pPos, BlockState pOldState, boolean pIsMoving) {
		FluidTankBlock.updateBoilerState(pState, pLevel, pPos.relative(getFacing(pState).getOpposite()));
		BlockPos shaftPos = getShaftPos(pState, pPos);
		BlockState shaftState = pLevel.getBlockState(shaftPos);
		if (isShaftValid(pState, shaftState))
			pLevel.setBlock(shaftPos, PoweredShaftBlock.getEquivalent(shaftState), Block.UPDATE_ALL);
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState pState, ServerLevel pLevel, BlockPos pPos, boolean pIsMoving) {
		super.affectNeighborsAfterRemoval(pState, pLevel, pPos, pIsMoving);
		FluidTankBlock.updateBoilerState(pState, pLevel, pPos.relative(getFacing(pState).getOpposite()));
		BlockPos shaftPos = getShaftPos(pState, pPos);
		BlockState shaftState = pLevel.getBlockState(shaftPos);
		if (shaftState.is(SteamRegistry.POWERED_SHAFT))
			pLevel.scheduleTick(shaftPos, shaftState.getBlock(), 1);
	}

	@Override
	protected VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		AttachFace face = pState.getValue(FACE);
		Direction direction = pState.getValue(FACING);
		return face == AttachFace.CEILING ? STEAM_ENGINE_CEILING.get(direction.getAxis())
			: face == AttachFace.FLOOR ? STEAM_ENGINE.get(direction.getAxis())
			: STEAM_ENGINE_WALL.get(direction);
	}

	/** Polymer would otherwise answer with the collision of the barrier players see. */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return getShape(state, level, pos, context);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		FluidState ifluidstate = level.getFluidState(pos);
		BlockState state = super.getStateForPlacement(context);
		if (state == null)
			return null;
		return state.setValue(WATERLOGGED, Boolean.valueOf(ifluidstate.getType() == Fluids.WATER));
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	public static Direction getFacing(BlockState sideState) {
		return getConnectedDirection(sideState);
	}

	public static BlockPos getShaftPos(BlockState sideState, BlockPos pos) {
		return pos.relative(getConnectedDirection(sideState), 2);
	}

	public static boolean isShaftValid(BlockState state, BlockState shaft) {
		return (shaft.is(ModBlocks.SHAFT) || shaft.is(SteamRegistry.POWERED_SHAFT))
			&& shaft.getValue(ShaftBlock.AXIS) != getFacing(state).getAxis();
	}

	@Override
	public Class<SteamEngineBlockEntity> getBlockEntityClass() {
		return SteamEngineBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends SteamEngineBlockEntity> getBlockEntityType() {
		return SteamRegistry.STEAM_ENGINE_BE;
	}

	public static Couple<Integer> getSpeedRange() {
		return Couple.create(16, 64);
	}

	public static Direction getConnectedDirection(BlockState state) {
		return FaceAttachedHorizontalDirectionalBlock.getConnectedDirection(state);
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.COPPER_BLOCK.weathering().unaffected().defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/**
	 * Create's SteamEngineVisual / SteamEngineRenderer: the block, plus the piston, linkage and shaft connector
	 * that move with the angle of the powered shaft.
	 */
	public static class Model extends BaseModel {
		private static final Matrix4f HIDDEN = new Matrix4f().scale(.0001f);

		private final Part block;
		private final Part piston;
		private final Part linkage;
		private final Part connector;

		private boolean shown;
		private float lastAngle = Float.NaN;
		private Axis lastAxis;
		private long nextUpdate = Long.MIN_VALUE;
		private float lastSpeed;

		public Model(BlockState state) {
			super(state);
			block = addPart(ModelItems.block("steam_engine/block"));
			piston = addPart(ModelItems.block("steam_engine/piston"));
			linkage = addPart(ModelItems.block("steam_engine/linkage"));
			connector = addPart(ModelItems.block("steam_engine/shaft_connector"));
			set(piston, HIDDEN, 0);
			set(linkage, HIDDEN, 0);
			set(connector, HIDDEN, 0);
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(block, "steam_engine", state);
			// force the moving parts to be placed again for the new facing
			lastAngle = Float.NaN;
			nextUpdate = Long.MIN_VALUE;
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof SteamEngineBlockEntity engine))
				return;

			PoweredShaftBlockEntity shaft = engine.getShaft();
			float speed = shaft == null ? 0 : shaft.getSpeed();
			Axis axis = Axis.Y;
			if (shaft != null && shaft.getBlockState().getBlock() instanceof IRotate rotate)
				axis = rotate.getRotationAxis(shaft.getBlockState());

			boolean moving = speed != 0;
			int interval = 1;
			if (moving) {
				float degPerTick = Math.abs(speed) * 0.3f;
				interval = Mth.clamp((int) (30f / degPerTick), 1, 5);
				if (speed == lastSpeed && time < nextUpdate)
					return;
			} else if (speed == lastSpeed && lastAxis == axis && !Float.isNaN(lastAngle))
				return;
			lastSpeed = speed;
			lastAxis = axis;
			nextUpdate = time + interval;

			Float angle = engine.getTargetAngle(time + (moving ? interval : 0));
			if (angle == null) {
				if (shown) {
					set(piston, HIDDEN, 0);
					set(linkage, HIDDEN, 0);
					set(connector, HIDDEN, 0);
					shown = false;
				}
				lastAngle = Float.NaN;
				return;
			}
			lastAngle = angle;
			int interpolation = moving ? interval : 0;
			shown = true;

			Direction facing = getFacing(state);
			Axis facingAxis = facing.getAxis();
			boolean roll90 = facingAxis.isHorizontal() && axis == Axis.Y || facingAxis.isVertical() && axis == Axis.Z;
			float pistonOffset = ((6 / 16f) * Mth.sin(angle) - Mth.sqrt(Mth.square(14 / 16f) - Mth.square(6 / 16f) * Mth.square(Mth.cos(angle))));
			float distance = Mth.sqrt(Mth.square(pistonOffset - 6 / 16f * Mth.sin(angle)));
			float angle2 = (float) Math.acos(distance / (14 / 16f)) * (Mth.cos(angle) >= 0 ? 1f : -1f);

			set(piston, finish(transformed(facing, roll90)
				.translate(0, pistonOffset + 20 / 16f, 0)), interpolation);

			set(linkage, finish(transformed(facing, roll90)
				.translate(.5f, .5f, .5f)
				.translate(0, 1, 0)
				.translate(-.5f, -.5f, -.5f)
				.translate(0, pistonOffset + 20 / 16f, 0)
				.translate(0, 4 / 16f, 8 / 16f)
				.rotateX(angle2)
				.translate(0, -4 / 16f, -8 / 16f)), interpolation);

			set(connector, finish(transformed(facing, roll90)
				.translate(0, 2, 0)
				.translate(.5f, .5f, .5f)
				.rotateX(-(angle + Mth.HALF_PI))
				.translate(-.5f, -.5f, -.5f)), interpolation);
		}

		/** Create's transformed(...) of the visual, in block space (0..1). */
		private static Matrix4f transformed(Direction facing, boolean roll90) {
			return new Matrix4f()
				.translate(.5f, .5f, .5f)
				.rotateY((float) Math.toRadians(AngleHelper.horizontalAngle(facing)))
				.rotateX((float) Math.toRadians(AngleHelper.verticalAngle(facing) + 90))
				.rotateY((float) Math.toRadians(roll90 ? -90 : 0))
				.translate(-.5f, -.5f, -.5f);
		}

		/** The matrices only rotate and move (or shrink to nothing), so they are split into parts here, not by an SVD. */
		private static void set(Part part, Matrix4f m, int interpolation) {
			Vector3f scale = m.getScale(new Vector3f());
			part.element.setTranslation(m.getTranslation(new Vector3f()));
			part.element.setLeftRotation(m.getNormalizedRotation(new Quaternionf()));
			part.element.setScale(scale);
			part.element.setRightRotation(new Quaternionf());
			part.element.setInterpolationDuration(interpolation);
			part.element.startInterpolation();
		}

		/** The display entity's origin is the block centre, a model's is the block corner. */
		private static Matrix4f finish(Matrix4f blockSpace) {
			return new Matrix4f().translate(-.5f, -.5f, -.5f).mul(blockSpace).translate(.5f, .5f, .5f);
		}
	}
}
