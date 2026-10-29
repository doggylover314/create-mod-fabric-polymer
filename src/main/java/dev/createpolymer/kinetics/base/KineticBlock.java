package dev.createpolymer.kinetics.base;

import org.jspecify.annotations.Nullable;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Ported from Create's KineticBlock (MIT, The Create Team), plus the Polymer
 * glue: players see an invisible barrier with the right break particles, and
 * the visible model is a set of display entities built by {@link #createModel}.
 */
public abstract class KineticBlock extends Block implements IRotate, EntityBlock, PolymerBlock, BlockWithElementHolder {

	public KineticBlock(Properties properties) {
		super(properties);
	}

	public abstract BlockEntityType<? extends KineticBlockEntity> getBlockEntityType();

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return getBlockEntityType().create(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide() || type != getBlockEntityType())
			return null;
		return (l, p, s, be) -> ((SmartBlockEntity) be).tick();
	}

	@Override
	protected void onPlace(BlockState state, Level worldIn, BlockPos pos, BlockState oldState, boolean isMoving) {
		// onPlace is useless for init, as sometimes the BE gets re-instantiated.
		// However, if a block change does not change kinetic connections,
		// we can prevent a major re-propagation here.
		BlockEntity blockEntity = worldIn.getBlockEntity(pos);
		if (blockEntity instanceof KineticBlockEntity kineticBlockEntity) {
			kineticBlockEntity.preventSpeedUpdate = 0;

			if (oldState.getBlock() != state.getBlock())
				return;
			if (state.hasBlockEntity() != oldState.hasBlockEntity())
				return;
			if (!areStatesKineticallyEquivalent(oldState, state))
				return;

			kineticBlockEntity.preventSpeedUpdate = 2;
		}
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return false;
	}

	protected boolean areStatesKineticallyEquivalent(BlockState oldState, BlockState newState) {
		if (oldState.getBlock() != newState.getBlock())
			return false;
		return getRotationAxis(newState) == getRotationAxis(oldState);
	}

	@Override
	protected void updateIndirectNeighbourShapes(BlockState stateIn, LevelAccessor worldIn, BlockPos pos, int flags, int count) {
		if (worldIn.isClientSide())
			return;

		BlockEntity blockEntity = worldIn.getBlockEntity(pos);
		if (!(blockEntity instanceof KineticBlockEntity kbe))
			return;

		if (kbe.preventSpeedUpdate > 0)
			return;

		// Remove previous information when block is added
		kbe.warnOfMovement();
		kbe.clearKineticInformation();
		kbe.updateSpeed = true;
	}

	// ---- Polymer ----

	/** Block whose break particles/sounds players see. */
	protected BlockState particleState() {
		return Blocks.STONE.defaultBlockState();
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		boolean water = state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED);
		return Blocks.BARRIER.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, water);
	}

	@Override
	public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
		return particleState();
	}

	@Override
	public boolean tickElementHolder(ServerLevel world, BlockPos pos, BlockState initialBlockState) {
		return true;
	}

	@Override
	public @Nullable ElementHolder createElementHolder(ServerLevel world, BlockPos pos, BlockState initialBlockState) {
		return createModel(world, pos, initialBlockState);
	}

	protected abstract ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state);
}
