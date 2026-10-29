package dev.createpolymer.fluids.pipes;

import java.util.Map;

import dev.createpolymer.fluids.FluidPropagator;
import dev.createpolymer.fluids.FluidTransportBehaviour;
import java.util.Optional;

import dev.createpolymer.decoration.bracket.BracketedBlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.item.IWrenchableWithBracket;
import dev.createpolymer.registry.feature.PipesRegistry;
import dev.createpolymer.util.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.TickPriority;

public class AxisPipeBlock extends RotatedPillarBlock implements IWrenchableWithBracket, IAxisPipe {

	public AxisPipeBlock(Properties p_i48339_1_) {
		super(p_i48339_1_);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		EncasedPipeBlock encased = PipesRegistry.ENCASED_FLUID_PIPE;
		if (!encased.isCasing(stack))
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (level.isClientSide())
			return InteractionResult.SUCCESS;
		BlockState newState = encased.defaultBlockState();
		for (Direction d : Iterate.directionsInAxis(getAxis(state)))
			newState = newState.setValue(EncasedPipeBlock.FACING_TO_PROPERTY_MAP.get(d), true);
		FluidTransportBehaviour.cacheFlows(level, pos);
		level.setBlockAndUpdate(pos, newState);
		FluidTransportBehaviour.loadFlows(level, pos);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
		if (world.isClientSide())
			return;
		if (state != oldState)
			world.scheduleTick(pos, this, 1, TickPriority.HIGH);
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(PipesRegistry.FLUID_PIPE_ITEM);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
		Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		Direction changed = FluidPropagator.validateNeighbourChange(state, direction, neighbourState);
		if (changed != null && isOpenAt(state, changed))
			ticks.scheduleTick(pos, this, 1, TickPriority.HIGH);
		return state;
	}

	public static boolean isOpenAt(BlockState state, Direction d) {
		return d.getAxis() == state.getValue(AXIS);
	}

	@Override
	protected void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource r) {
		FluidPropagator.propagateChangedPipe(world, pos, state);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter p_220053_2_, BlockPos p_220053_3_,
		CollisionContext p_220053_4_) {
		return PipeShapes.EIGHT_VOXEL_POLE.get(state.getValue(AXIS));
	}

	/** Polymer would otherwise answer with the collision of the barrier players see. */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return getShape(state, level, pos, context);
	}

	public BlockState toRegularPipe(LevelAccessor world, BlockPos pos, BlockState state) {
		Direction side = Direction.get(AxisDirection.POSITIVE, state.getValue(AXIS));
		Map<Direction, BooleanProperty> facingToPropertyMap = FluidPipeBlock.PROPERTY_BY_DIRECTION;
		return PipesRegistry.FLUID_PIPE
			.updateBlockState(PipesRegistry.FLUID_PIPE.defaultBlockState()
				.setValue(facingToPropertyMap.get(side), true)
				.setValue(facingToPropertyMap.get(side.getOpposite()), true), side, null, world, pos);
	}

	@Override
	public Axis getAxis(BlockState state) {
		return state.getValue(AXIS);
	}

	@Override
	public Optional<ItemStack> removeBracket(BlockGetter world, BlockPos pos, boolean inOnReplacedContext) {
		BracketedBlockEntityBehaviour behaviour =
			BlockEntityBehaviour.get(world, pos, BracketedBlockEntityBehaviour.TYPE);
		if (behaviour == null)
			return Optional.empty();
		BlockState bracket = behaviour.removeBracket(inOnReplacedContext);
		if (bracket == null)
			return Optional.empty();
		return Optional.of(new ItemStack(bracket.getBlock()));
	}

}
