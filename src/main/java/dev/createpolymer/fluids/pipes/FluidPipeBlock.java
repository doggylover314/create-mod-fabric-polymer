package dev.createpolymer.fluids.pipes;

import java.util.Arrays;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.fluids.FluidPropagator;
import dev.createpolymer.fluids.FluidTransportBehaviour;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.registry.feature.PipesRegistry;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.TickPriority;

public class FluidPipeBlock extends PipeBlock implements SimpleWaterloggedBlock, IWrenchable,
	IBE<FluidPipeBlockEntity>, PolymerModelBlock {

	private static final VoxelShape OCCLUSION_BOX = ShapeUtil.box(4, 4, 4, 12, 12, 12);

	public FluidPipeBlock(Properties properties) {
		super(8f, properties);
		this.registerDefaultState(super.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false));
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		Level world = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Direction clickedFace = context.getClickedFace();

		Axis axis = getAxis(world, pos, state);
		if (axis == null) {
			Vec3 clickLocation = context.getClickLocation()
				.subtract(pos.getX(), pos.getY(), pos.getZ());
			double closest = Float.MAX_VALUE;
			Direction argClosest = Direction.UP;
			for (Direction direction : Iterate.directions) {
				if (clickedFace.getAxis() == direction.getAxis())
					continue;
				Vec3 centerOf = Vec3.atCenterOf(direction.getUnitVec3i());
				double distance = centerOf.distanceToSqr(clickLocation);
				if (distance < closest) {
					closest = distance;
					argClosest = direction;
				}
			}
			axis = argClosest.getAxis();
		}

		if (clickedFace.getAxis() == axis)
			return InteractionResult.PASS;
		if (!world.isClientSide()) {
			FluidTransportBehaviour.cacheFlows(world, pos);
			world.setBlockAndUpdate(pos, PipesRegistry.GLASS_FLUID_PIPE.defaultBlockState()
				.setValue(GlassFluidPipeBlock.AXIS, axis)
				.setValue(BlockStateProperties.WATERLOGGED, state.getValue(BlockStateProperties.WATERLOGGED)));
			FluidTransportBehaviour.loadFlows(world, pos);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		EncasedPipeBlock encased = PipesRegistry.ENCASED_FLUID_PIPE;
		if (!encased.isCasing(stack))
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (level.isClientSide())
			return InteractionResult.SUCCESS;
		encased.handleEncasing(state, level, pos);
		EncasedPipeBlock.playEncaseSound(level, pos);
		return InteractionResult.SUCCESS;
	}

	public BlockState getAxisState(Axis axis) {
		BlockState defaultState = defaultBlockState();
		for (Direction d : Iterate.directions)
			defaultState = defaultState.setValue(PROPERTY_BY_DIRECTION.get(d), d.getAxis() == axis);
		return defaultState;
	}

	@Nullable
	private Axis getAxis(BlockGetter world, BlockPos pos, BlockState state) {
		return FluidPropagator.getStraightPipeAxis(state);
	}

	@Override
	protected void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
		if (world.isClientSide())
			return;
		if (state != oldState)
			world.scheduleTick(pos, this, 1, TickPriority.HIGH);
	}

	@Override
	protected void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource r) {
		FluidPropagator.propagateChangedPipe(world, pos, state);
	}

	public static boolean isPipe(BlockState state) {
		return state.getBlock() instanceof FluidPipeBlock;
	}

	public static boolean canConnectTo(BlockGetter world, BlockPos neighbourPos, BlockState neighbour,
		Direction direction) {
		if (FluidPropagator.hasFluidCapability(world, neighbourPos, direction.getOpposite()))
			return true;
		if (VanillaFluidTargets.canProvideFluidWithoutCapability(neighbour))
			return true;
		FluidTransportBehaviour transport = BlockEntityBehaviour.get(world, neighbourPos, FluidTransportBehaviour.TYPE);
		if (isPipe(neighbour))
			return true;
		if (transport == null)
			return false;
		return transport.canHaveFlowToward(neighbour, direction.getOpposite());
	}

	public static boolean shouldDrawRim(BlockGetter world, BlockPos pos, BlockState state, Direction direction) {
		BlockPos offsetPos = pos.relative(direction);
		BlockState facingState = world.getBlockState(offsetPos);
		if (facingState.getBlock() instanceof EncasedPipeBlock)
			return true;
		if (!isPipe(facingState))
			return true;
		if (!canConnectTo(world, offsetPos, facingState, direction))
			return true;
		return false;
	}

	public static boolean isOpenAt(BlockState state, Direction direction) {
		return state.getValue(PROPERTY_BY_DIRECTION.get(direction));
	}

	public static boolean isCornerOrEndPipe(BlockGetter world, BlockPos pos, BlockState state) {
		return isPipe(state) && FluidPropagator.getStraightPipeAxis(state) == null
			&& !shouldDrawCasing(world, pos, state);
	}

	public static boolean shouldDrawCasing(BlockGetter world, BlockPos pos, BlockState state) {
		if (!isPipe(state))
			return false;
		for (Axis axis : Iterate.axes) {
			int connections = 0;
			for (Direction direction : Iterate.directions)
				if (direction.getAxis() != axis && isOpenAt(state, direction))
					connections++;
			if (connections > 2)
				return true;
		}
		return false;
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, BlockStateProperties.WATERLOGGED);
		super.createBlockStateDefinition(builder);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		FluidState FluidState = context.getLevel()
			.getFluidState(context.getClickedPos());
		return updateBlockState(defaultBlockState(), context.getNearestLookingDirection(), null, context.getLevel(),
			context.getClickedPos()).setValue(BlockStateProperties.WATERLOGGED,
				Boolean.valueOf(FluidState.getType() == Fluids.WATER));
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
		Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (state.getValue(BlockStateProperties.WATERLOGGED))
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		if (isOpenAt(state, direction) && neighbourState.hasProperty(BlockStateProperties.WATERLOGGED))
			ticks.scheduleTick(pos, this, 1, TickPriority.HIGH);
		Direction changed = FluidPropagator.validateNeighbourChange(state, direction, neighbourState);
		if (changed != null && isOpenAt(state, changed))
			ticks.scheduleTick(pos, this, 1, TickPriority.HIGH);
		return updateBlockState(state, direction, direction.getOpposite(), level, pos);
	}

	public BlockState updateBlockState(BlockState state, Direction preferredDirection, @Nullable Direction ignore,
		BlockGetter world, BlockPos pos) {

		BlockState prevState = state;
		int prevStateSides = (int) Arrays.stream(Iterate.directions)
			.map(PROPERTY_BY_DIRECTION::get)
			.filter(prevState::getValue)
			.count();

		// Update sides that are not ignored
		for (Direction d : Iterate.directions)
			if (d != ignore) {
				boolean shouldConnect = canConnectTo(world, pos.relative(d), world.getBlockState(pos.relative(d)), d);
				state = state.setValue(PROPERTY_BY_DIRECTION.get(d), shouldConnect);
			}

		// See if it has enough connections
		Direction connectedDirection = null;
		for (Direction d : Iterate.directions) {
			if (isOpenAt(state, d)) {
				if (connectedDirection != null)
					return state;
				connectedDirection = d;
			}
		}

		// Add opposite end if only one connection
		if (connectedDirection != null)
			return state.setValue(PROPERTY_BY_DIRECTION.get(connectedDirection.getOpposite()), true);

		// If we can't connect to anything and weren't connected before, do nothing
		if (prevStateSides == 2)
			return prevState;

		// Use preferred
		return state.setValue(PROPERTY_BY_DIRECTION.get(preferredDirection), true)
			.setValue(PROPERTY_BY_DIRECTION.get(preferredDirection.getOpposite()), true);
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false)
			: Fluids.EMPTY.defaultFluidState();
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	public Class<FluidPipeBlockEntity> getBlockEntityClass() {
		return FluidPipeBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends FluidPipeBlockEntity> getBlockEntityType() {
		return PipesRegistry.FLUID_PIPE_BLOCK_ENTITY;
	}

	@Override
	protected VoxelShape getOcclusionShape(BlockState pState) {
		return OCCLUSION_BOX;
	}

	/** Polymer would otherwise answer with the collision of the barrier players see. */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return getShape(state, level, pos, context);
	}

	@Override
	protected BlockState rotate(BlockState pState, Rotation pRotation) {
		return FluidPipeBlockRotation.rotate(pState, pRotation);
	}

	@Override
	protected BlockState mirror(BlockState pState, Mirror pMirror) {
		return FluidPipeBlockRotation.mirror(pState, pMirror);
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.COPPER_BLOCK.weathering().unaffected().defaultBlockState();
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(PipesRegistry.FLUID_PIPE_ITEM);
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/**
	 * Create's blockstate (core faces by connection) plus PipeAttachmentModel (arms, rims, drains), see
	 * {@link PipeAttachments}. The core is one merged model per connection state, with the casing if it has one.
	 */
	public static class Model extends BaseModel {
		private final Part core;
		private final PipeAttachments attachments;
		private boolean dirty = true;

		public Model(BlockState state) {
			super(state);
			core = addPart(ItemStack.EMPTY);
			attachments = new PipeAttachments(this);
			onStateChanged(state);
		}

		static String coreModel(BlockState state) {
			StringBuilder bits = new StringBuilder("fluid_pipe/polymer_core/");
			for (Direction d : new Direction[] { Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
				Direction.WEST, Direction.EAST })
				bits.append(isOpenAt(state, d) ? '1' : '0');
			return bits.toString();
		}

		@Override
		protected void onStateChanged(BlockState state) {
			core.setItem(state.getBlock() instanceof FluidPipeBlock && !coreModel(state).endsWith("000000")
				? ModelItems.block(coreModel(state)) : ItemStack.EMPTY);
			dirty = true;
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!dirty && time % 5 != 0)
				return;
			dirty = false;
			attachments.update(level, pos, state, BlockEntityBehaviour.get(be, FluidTransportBehaviour.TYPE));
		}
	}
}
