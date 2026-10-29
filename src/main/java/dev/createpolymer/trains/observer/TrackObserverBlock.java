package dev.createpolymer.trains.observer;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.registry.feature.SignalsRegistry;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class TrackObserverBlock extends Block implements IBE<TrackObserverBlockEntity>, IWrenchable, PolymerModelBlock {

	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

	public TrackObserverBlock(Properties p_49795_) {
		super(p_49795_);
		registerDefaultState(defaultBlockState().setValue(POWERED, false));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
		super.createBlockStateDefinition(pBuilder.add(POWERED));
	}

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	protected int getSignal(BlockState blockState, BlockGetter blockAccess, BlockPos pos, Direction side) {
		return blockState.getValue(POWERED) ? 15 : 0;
	}

	@Override
	public Class<TrackObserverBlockEntity> getBlockEntityClass() {
		return TrackObserverBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends TrackObserverBlockEntity> getBlockEntityType() {
		return SignalsRegistry.TRACK_OBSERVER_BLOCK_ENTITY;
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.POLISHED_ANDESITE.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new TrackObserverModel(state);
	}

}
