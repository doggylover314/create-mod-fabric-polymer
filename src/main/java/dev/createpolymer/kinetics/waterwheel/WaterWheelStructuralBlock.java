package dev.createpolymer.kinetics.waterwheel;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.registry.ModBlocks;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Ported from Create's WaterWheelStructuralBlock (MIT, The Create Team): invisible filler
 * blocks around a large water wheel. Each points (FACING) towards the wheel's centre.
 */
public class WaterWheelStructuralBlock extends Block implements PolymerBlock, IWrenchable {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

	public WaterWheelStructuralBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(FACING));
	}

	public static BlockPos getMaster(BlockGetter level, BlockPos pos, BlockState state) {
		Direction direction = state.getValue(FACING);
		BlockPos targetedPos = pos.relative(direction);
		BlockState targetedState = level.getBlockState(targetedPos);
		if (targetedState.is(ModBlocks.WATER_WHEEL_STRUCTURAL))
			return getMaster(level, targetedPos, targetedState);
		return targetedPos;
	}

	public boolean stillValid(BlockGetter level, BlockPos pos, BlockState state, boolean directlyAdjacent) {
		if (!state.is(this))
			return false;
		Direction direction = state.getValue(FACING);
		BlockPos targetedPos = pos.relative(direction);
		BlockState targetedState = level.getBlockState(targetedPos);
		if (!directlyAdjacent && stillValid(level, targetedPos, targetedState, true))
			return true;
		return targetedState.getBlock() instanceof LargeWaterWheelBlock
			&& targetedState.getValue(LargeWaterWheelBlock.AXIS) != direction.getAxis();
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		return InteractionResult.PASS;
	}

	@Override
	public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		if (!stillValid(level, pos, state, false))
			return InteractionResult.PASS;
		BlockPos master = getMaster(level, pos, state);
		level.destroyBlock(master, context.getPlayer() == null || !context.getPlayer().isCreative());
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		if (stillValid(level, pos, state, false))
			level.destroyBlock(getMaster(level, pos, state), true);
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (stillValid(level, pos, state, false)) {
			BlockPos masterPos = getMaster(level, pos, state);
			if (!level.isClientSide() && player.isCreative())
				level.destroyBlock(masterPos, false);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
									 Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (stillValid(level, pos, state, false)) {
			BlockPos masterPos = getMaster(level, pos, state);
			ticks.scheduleTick(masterPos, ModBlocks.LARGE_WATER_WHEEL, 1);
			return state;
		}
		ticks.scheduleTick(pos, this, 1);
		return state;
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!stillValid(level, pos, state, false))
			level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		return Blocks.BARRIER.defaultBlockState();
	}

	@Override
	public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
		return Blocks.SPRUCE_PLANKS.defaultBlockState();
	}
}
